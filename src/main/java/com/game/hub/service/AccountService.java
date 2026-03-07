package com.game.hub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.entity.EmailVerificationToken;
import com.game.hub.entity.PasswordResetToken;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.EmailVerificationTokenRepository;
import com.game.hub.repository.PasswordResetTokenRepository;
import com.game.hub.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountService {
    private static final int VERIFICATION_CODE_TTL_MINUTES = 5;
    private static final long VERIFICATION_RESEND_COOLDOWN_SECONDS = 30;
    private static final Set<String> ALLOWED_THEME_MODES = Set.of("system", "light", "dark");
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("vi", "en");
    private static final Set<Integer> ALLOWED_FRIEND_REFRESH_MS = Set.of(5000, 10000, 15000, 20000, 30000, 60000);
    private static final String GAME_CODE_CHESS_OFFLINE = "chess-offline";
    private static final String GAME_CODE_XIANGQI_OFFLINE = "xiangqi-offline";
    private static final String GAME_CODE_MINESWEEPER = "minesweeper";

    private final UserAccountRepository userAccountRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final String defaultAdminEmail;
    private final String adminActivationCode;
    private final Random random = new Random();

    public AccountService(UserAccountRepository userAccountRepository,
                          EmailVerificationTokenRepository emailVerificationTokenRepository,
                          PasswordResetTokenRepository passwordResetTokenRepository,
                          PasswordEncoder passwordEncoder,
                          EmailService emailService,
                          ObjectMapper objectMapper,
                          @Value("${app.admin.default-email:luckhaikiet@gmail.com}") String defaultAdminEmail,
                          @Value("${app.admin.activation-code:j2ee20262027}") String adminActivationCode) {
        this.userAccountRepository = userAccountRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.defaultAdminEmail = normalizeEmail(defaultAdminEmail);
        this.adminActivationCode = trimToNull(adminActivationCode);
    }

    public ServiceResult register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request == null ? null : request.email());
        String displayName = trimToNull(request == null ? null : request.displayName());
        String password = request == null ? null : request.password();
        String avatarPath = trimToNull(request == null ? null : request.avatarPath());

        if (normalizedEmail == null) {
            return ServiceResult.error("Email is required");
        }
        if (displayName == null) {
            return ServiceResult.error("Display name is required");
        }
        if (password == null || password.isBlank()) {
            return ServiceResult.error("Password is required");
        }

        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            return ServiceResult.error("Email already exists");
        }
        if (PendingRegisterStore.get(normalizedEmail) != null) {
            return ServiceResult.error("Email is waiting for verification");
        }

        RegisterRequest normalizedRequest = new RegisterRequest(normalizedEmail, displayName, password, avatarPath);
        PendingRegisterStore.put(normalizedEmail, normalizedRequest, LocalDateTime.now().plusMinutes(VERIFICATION_CODE_TTL_MINUTES));
        ServiceResult issueResult = issueVerificationCode(normalizedEmail, false);
        if (!issueResult.success()) {
            PendingRegisterStore.remove(normalizedEmail);
            emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByEmail(normalizedEmail));
            return issueResult;
        }
        return issueResult;
    }

    public ServiceResult resendVerificationCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return ServiceResult.error("Email is required");
        }
        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            return ServiceResult.error("Email already registered");
        }

        RegisterRequest pending = PendingRegisterStore.get(normalizedEmail);
        if (pending == null) {
            emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByEmail(normalizedEmail));
            return ServiceResult.error("No pending registration for this email");
        }

        ServiceResult cooldown = validateResendCooldown(normalizedEmail);
        if (!cooldown.success()) {
            return cooldown;
        }

        return issueVerificationCode(normalizedEmail, true);
    }

    public ServiceResult verifyEmail(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = trimToNull(code);
        if (normalizedEmail == null || normalizedCode == null) {
            return ServiceResult.error("Email and code are required");
        }
        Optional<EmailVerificationToken> tokenOpt = emailVerificationTokenRepository
            .findTopByEmailAndTokenOrderByCreatedAtDesc(normalizedEmail, normalizedCode);

        if (tokenOpt.isEmpty() || tokenOpt.get().getExpireAt().isBefore(LocalDateTime.now())) {
            return ServiceResult.error("Invalid or expired verification code");
        }

        RegisterRequest pending = PendingRegisterStore.get(normalizedEmail);
        if (pending == null) {
            emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByEmail(normalizedEmail));
            return ServiceResult.error("No pending registration for this email");
        }
        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            PendingRegisterStore.remove(normalizedEmail);
            emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByEmail(normalizedEmail));
            return ServiceResult.error("Email already registered");
        }

        UserAccount user = new UserAccount();
        user.setEmail(pending.email());
        user.setUsername(pending.email());
        user.setDisplayName(pending.displayName());
        user.setAvatarPath(pending.avatarPath() == null || pending.avatarPath().isBlank()
            ? "/uploads/avatars/default-avatar.jpg" : pending.avatarPath());
        user.setPassword(passwordEncoder.encode(pending.password()));
        user.setEmailConfirmed(true);
        user.setRole(isDefaultAdminEmail(pending.email()) ? "Admin" : "User");
        userAccountRepository.save(user);

        PendingRegisterStore.remove(normalizedEmail);
        emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByEmail(normalizedEmail));
        Map<String, Object> payload = new HashMap<>(toLoginPayload(user));
        payload.put("message", "Account created");
        return ServiceResult.ok(payload);
    }

    public ServiceResult login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || password == null) {
            return ServiceResult.error("Email and password are required");
        }
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return ServiceResult.error("Invalid email or password");
        }

        if (user.isBanned()) {
            return ServiceResult.error("Account banned until " + user.getBannedUntil());
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ServiceResult.error("Invalid email or password");
        }

        applyPostLoginState(user);
        return ServiceResult.ok(toLoginPayload(user));
    }

    public ServiceResult loginWithOAuth2(OAuth2LoginRequest request) {
        String provider = trimToNull(request == null ? null : request.provider());
        String providerUserId = trimToNull(request == null ? null : request.providerUserId());
        String normalizedEmail = normalizeEmail(request == null ? null : request.email());
        String displayName = trimToNull(request == null ? null : request.displayName());

        if (provider == null) {
            provider = "social";
        }
        if (providerUserId == null) {
            return ServiceResult.error("Cannot read social account id");
        }

        if (normalizedEmail == null) {
            normalizedEmail = syntheticOauthEmail(provider, providerUserId);
        }
        if (displayName == null) {
            displayName = defaultDisplayNameFromEmail(normalizedEmail);
        }

        UserAccount user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            user = new UserAccount();
            user.setEmail(normalizedEmail);
            user.setUsername(normalizedEmail);
            user.setDisplayName(displayName);
            user.setAvatarPath("/uploads/avatars/default-avatar.jpg");
            user.setPassword(passwordEncoder.encode(UUID.randomUUID() + ":" + provider + ":" + providerUserId));
            user.setEmailConfirmed(true);
            user.setRole(isDefaultAdminEmail(normalizedEmail) ? "Admin" : "User");
        } else {
            if (trimToNull(user.getDisplayName()) == null && displayName != null) {
                user.setDisplayName(displayName);
            }
            if (trimToNull(user.getUsername()) == null) {
                user.setUsername(normalizedEmail);
            }
            if (trimToNull(user.getAvatarPath()) == null) {
                user.setAvatarPath("/uploads/avatars/default-avatar.jpg");
            }
            if (user.getPassword() == null || user.getPassword().isBlank()) {
                user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            }
        }

        if (user.isBanned()) {
            return ServiceResult.error("Account banned until " + user.getBannedUntil());
        }

        applyPostLoginState(user);
        return ServiceResult.ok(toLoginPayload(user));
    }

    public ServiceResult logout(String userId) {
        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        user.setOnline(false);
        userAccountRepository.save(user);
        return ServiceResult.ok(Map.of("message", "Logged out"));
    }

    public ServiceResult changePassword(String userId, String currentPassword, String newPassword) {
        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) return ServiceResult.error("User not found");
        if (currentPassword == null || currentPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            return ServiceResult.error("Current password and new password are required");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ServiceResult.error("Current password incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userAccountRepository.save(user);
        return ServiceResult.ok(Map.of("message", "Password changed"));
    }

    public ServiceResult updateProfile(String userId, String displayName, String email, String avatarPath) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        String normalizedDisplayName = trimToNull(displayName);
        if (normalizedDisplayName == null) {
            return ServiceResult.error("Display name is required");
        }

        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return ServiceResult.error("Email is required");
        }

        UserAccount existingByEmail = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (existingByEmail != null && !normalizedUserId.equals(existingByEmail.getId())) {
            return ServiceResult.error("Email already exists");
        }

        String normalizedAvatarPath = trimToNull(avatarPath);
        user.setDisplayName(normalizedDisplayName);
        user.setEmail(normalizedEmail);
        user.setUsername(normalizedEmail);
        user.setAvatarPath(normalizedAvatarPath == null ? "/uploads/avatars/default-avatar.jpg" : normalizedAvatarPath);
        userAccountRepository.save(user);

        return ServiceResult.ok(Map.of(
            "userId", user.getId(),
            "displayName", user.getDisplayName() == null ? "Player" : user.getDisplayName(),
            "email", user.getEmail() == null ? "" : user.getEmail(),
            "role", user.getRole() == null ? "User" : user.getRole(),
            "avatarPath", user.getAvatarPath() == null ? "/uploads/avatars/default-avatar.jpg" : user.getAvatarPath(),
            "message", "Profile updated"
        ));
    }

    public ServiceResult updateAvatar(String userId, String avatarPath) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        String normalizedAvatarPath = trimToNull(avatarPath);
        if (normalizedAvatarPath == null) {
            return ServiceResult.error("Avatar path is required");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        user.setAvatarPath(normalizedAvatarPath);
        userAccountRepository.save(user);
        return ServiceResult.ok(Map.of(
            "userId", user.getId(),
            "email", user.getEmail() == null ? "" : user.getEmail(),
            "displayName", user.getDisplayName() == null ? "Player" : user.getDisplayName(),
            "role", user.getRole() == null ? "User" : user.getRole(),
            "avatarPath", user.getAvatarPath() == null ? "/uploads/avatars/default-avatar.jpg" : user.getAvatarPath()
        ));
    }

    public ServiceResult getPreferences(String userId) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }
        return ServiceResult.ok(toPreferencesPayload(user));
    }

    public ServiceResult updatePreferences(String userId, PreferencesRequest request) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        if (request == null) {
            return ServiceResult.error("Invalid request");
        }
        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        user.setThemeMode(normalizeThemeMode(request.themeMode()));
        user.setLanguage(normalizeLanguage(request.language()));
        user.setSidebarDesktopVisibleByDefault(valueOrDefault(request.sidebarDesktopVisibleByDefault(), user.isSidebarDesktopVisibleByDefault()));
        user.setSidebarMobileAutoClose(valueOrDefault(request.sidebarMobileAutoClose(), user.isSidebarMobileAutoClose()));
        user.setHomeMusicEnabled(valueOrDefault(request.homeMusicEnabled(), user.isHomeMusicEnabled()));
        user.setToastNotificationsEnabled(valueOrDefault(request.toastNotificationsEnabled(), user.isToastNotificationsEnabled()));
        user.setShowOfflineFriendsInSidebar(valueOrDefault(request.showOfflineFriendsInSidebar(), user.isShowOfflineFriendsInSidebar()));
        user.setAutoRefreshFriendList(valueOrDefault(request.autoRefreshFriendList(), user.isAutoRefreshFriendList()));
        user.setFriendListRefreshMs(normalizeFriendRefreshMs(request.friendListRefreshMs(), user.getFriendListRefreshMs()));
        userAccountRepository.save(user);

        return ServiceResult.ok(toPreferencesPayload(user));
    }

    public ServiceResult getGameStats(String userId, String gameCode) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        String normalizedGameCode = normalizeGameCode(gameCode);
        if (normalizedGameCode == null) {
            return ServiceResult.error("Unsupported gameCode");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        Map<String, Object> stats = normalizeStatsByGameCode(
            normalizedGameCode,
            readRawStatsForGameCode(user, normalizedGameCode)
        );
        return ServiceResult.ok(Map.of(
            "gameCode", normalizedGameCode,
            "stats", stats
        ));
    }

    public ServiceResult updateGameStats(String userId, String gameCode, Object statsPayload, boolean mergeExisting) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        String normalizedGameCode = normalizeGameCode(gameCode);
        if (normalizedGameCode == null) {
            return ServiceResult.error("Unsupported gameCode");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }
        if (!(statsPayload instanceof Map<?, ?>)) {
            return ServiceResult.error("Stats payload is required");
        }

        Map<String, Object> incoming = normalizeStatsByGameCode(normalizedGameCode, statsPayload);
        Map<String, Object> normalizedToPersist = incoming;
        if (mergeExisting) {
            Map<String, Object> existing = normalizeStatsByGameCode(
                normalizedGameCode,
                readRawStatsForGameCode(user, normalizedGameCode)
            );
            normalizedToPersist = mergeStatsByGameCode(normalizedGameCode, existing, incoming);
        }

        ServiceResult writeResult = writeStatsForGameCode(user, normalizedGameCode, normalizedToPersist);
        if (!writeResult.success()) {
            return writeResult;
        }

        return ServiceResult.ok(Map.of(
            "gameCode", normalizedGameCode,
            "stats", normalizedToPersist
        ));
    }

    public ServiceResult sendResetCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) return ServiceResult.error("Email is required");
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return ServiceResult.ok(Map.of("message", "If the email exists, a reset code has been sent"));
        }

        String code = String.valueOf(100000 + random.nextInt(900000));
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setToken(code);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpireAt(LocalDateTime.now().plusMinutes(5));
        passwordResetTokenRepository.save(token);

        try {
            emailService.sendEmail(normalizedEmail, "Caro Reset Password", "Your reset code is: " + code);
        } catch (RuntimeException ex) {
            passwordResetTokenRepository.delete(token);
            return ServiceResult.error("Cannot send reset code right now. Please try again.");
        }
        return ServiceResult.ok(Map.of("message", "If the email exists, a reset code has been sent"));
    }

    public ServiceResult verifyResetCode(String userId, String code) {
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository
            .findTopByUserIdAndTokenOrderByCreatedAtDesc(userId, code);

        if (tokenOpt.isEmpty() || tokenOpt.get().getExpireAt().isBefore(LocalDateTime.now())) {
            return ServiceResult.error("Invalid or expired reset code");
        }
        return ServiceResult.ok(Map.of("message", "Code verified"));
    }

    public ServiceResult verifyResetCodeByEmail(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = trimToNull(code);
        if (normalizedEmail == null || normalizedCode == null) {
            return ServiceResult.error("Email and code are required");
        }
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return ServiceResult.error("Invalid or expired reset code");
        }
        return verifyResetCode(user.getId(), normalizedCode);
    }

    public ServiceResult resetPassword(String userId, String code, String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.isBlank() || confirmPassword == null || confirmPassword.isBlank()) {
            return ServiceResult.error("New password and confirmation are required");
        }
        if (!newPassword.equals(confirmPassword)) {
            return ServiceResult.error("Password confirmation does not match");
        }

        ServiceResult verify = verifyResetCode(userId, code);
        if (!verify.success()) {
            return verify;
        }

        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userAccountRepository.save(user);

        List<PasswordResetToken> tokens = passwordResetTokenRepository.findByUserId(userId);
        passwordResetTokenRepository.deleteAll(tokens);
        return ServiceResult.ok(Map.of("message", "Password reset success"));
    }

    public ServiceResult resetPasswordByEmail(String email, String code, String newPassword, String confirmPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return ServiceResult.error("Email is required");
        }
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return ServiceResult.error("Invalid or expired reset code");
        }
        return resetPassword(user.getId(), code, newPassword, confirmPassword);
    }

    public ServiceResult listUsers(String searchTerm, String banFilter) {
        List<UserAccount> users = userAccountRepository.findAll();

        if (searchTerm != null && !searchTerm.isBlank()) {
            String lower = searchTerm.toLowerCase();
            users = users.stream().filter(u ->
                (u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(lower))
                    || (u.getEmail() != null && u.getEmail().toLowerCase().contains(lower))
            ).toList();
        }

        if ("banned".equalsIgnoreCase(banFilter)) {
            users = users.stream().filter(UserAccount::isBanned).toList();
        } else if ("active".equalsIgnoreCase(banFilter)) {
            users = users.stream().filter(u -> !u.isBanned()).toList();
        }

        users = users.stream().sorted(Comparator.comparing(UserAccount::getDisplayName, Comparator.nullsLast(String::compareToIgnoreCase))).toList();
        return ServiceResult.ok(Map.of("users", users));
    }

    public ServiceResult activateAdminRole(String userId, String activationCode) {
        String normalizedUserId = trimToNull(userId);
        String normalizedCode = trimToNull(activationCode);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        if (normalizedCode == null) {
            return ServiceResult.error("Activation code is required");
        }
        if (adminActivationCode == null || !adminActivationCode.equals(normalizedCode)) {
            return ServiceResult.error("Activation code is invalid");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }
        if (user.isBanned()) {
            return ServiceResult.error("Account is banned");
        }

        boolean roleChanged = !"Admin".equalsIgnoreCase(user.getRole());
        if (roleChanged) {
            user.setRole("Admin");
            userAccountRepository.save(user);
        }

        return ServiceResult.ok(Map.of(
            "userId", user.getId(),
            "role", "Admin",
            "displayName", user.getDisplayName() == null ? "Player" : user.getDisplayName(),
            "email", user.getEmail() == null ? "" : user.getEmail(),
            "avatarPath", user.getAvatarPath() == null ? "/uploads/avatars/default-avatar.jpg" : user.getAvatarPath(),
            "message", roleChanged ? "Admin role activated" : "Account is already Admin"
        ));
    }

    public static final class PendingRegisterStore {
        private static final Map<String, PendingRegisterEntry> STORE = new java.util.concurrent.ConcurrentHashMap<>();

        private PendingRegisterStore() {
        }

        public static void put(String email, RegisterRequest request, LocalDateTime expireAt) {
            STORE.put(email, new PendingRegisterEntry(request, expireAt));
        }

        public static RegisterRequest get(String email) {
            PendingRegisterEntry entry = STORE.get(email);
            if (entry == null) {
                return null;
            }
            if (entry.expireAt() != null && entry.expireAt().isBefore(LocalDateTime.now())) {
                STORE.remove(email);
                return null;
            }
            return entry.request();
        }

        public static void remove(String email) {
            STORE.remove(email);
        }

        private record PendingRegisterEntry(RegisterRequest request, LocalDateTime expireAt) {
        }
    }

    public record RegisterRequest(String email, String displayName, String password, String avatarPath) {
    }

    public record OAuth2LoginRequest(String provider, String providerUserId, String email, String displayName) {
    }

    public record PreferencesRequest(String themeMode,
                                     String language,
                                     Boolean sidebarDesktopVisibleByDefault,
                                     Boolean sidebarMobileAutoClose,
                                     Boolean homeMusicEnabled,
                                     Boolean toastNotificationsEnabled,
                                     Boolean showOfflineFriendsInSidebar,
                                     Boolean autoRefreshFriendList,
                                     Integer friendListRefreshMs) {
    }

    public record ServiceResult(boolean success, String error, Object data) {
        public static ServiceResult ok(Object data) {
            return new ServiceResult(true, null, data);
        }

        public static ServiceResult error(String error) {
            return new ServiceResult(false, error, null);
        }
    }

    private String normalizeGameCode(String gameCode) {
        String normalized = trimToNull(gameCode);
        if (normalized == null) {
            return null;
        }
        String lowered = normalized.toLowerCase();
        if ("chess-offline".equals(lowered) || "chess_offline".equals(lowered)
            || "chessoffline".equals(lowered) || "chess".equals(lowered)) {
            return GAME_CODE_CHESS_OFFLINE;
        }
        if ("xiangqi-offline".equals(lowered) || "xiangqi_offline".equals(lowered)
            || "xiangqioffline".equals(lowered) || "xiangqi".equals(lowered)) {
            return GAME_CODE_XIANGQI_OFFLINE;
        }
        if ("minesweeper".equals(lowered)) {
            return GAME_CODE_MINESWEEPER;
        }
        return null;
    }

    private Map<String, Object> readRawStatsForGameCode(UserAccount user, String gameCode) {
        if (user == null || gameCode == null) {
            return Map.of();
        }
        String rawJson;
        if (GAME_CODE_CHESS_OFFLINE.equals(gameCode)) {
            rawJson = user.getChessOfflineStatsJson();
        } else if (GAME_CODE_XIANGQI_OFFLINE.equals(gameCode)) {
            rawJson = user.getXiangqiOfflineStatsJson();
        } else if (GAME_CODE_MINESWEEPER.equals(gameCode)) {
            rawJson = user.getMinesweeperStatsJson();
        } else {
            return Map.of();
        }
        return readJsonMap(rawJson);
    }

    private ServiceResult writeStatsForGameCode(UserAccount user, String gameCode, Map<String, Object> normalizedStats) {
        if (user == null || gameCode == null) {
            return ServiceResult.error("Invalid user or gameCode");
        }
        String json = writeJson(normalizedStats);
        if (json == null) {
            return ServiceResult.error("Cannot serialize stats");
        }

        if (GAME_CODE_CHESS_OFFLINE.equals(gameCode)) {
            user.setChessOfflineStatsJson(json);
        } else if (GAME_CODE_XIANGQI_OFFLINE.equals(gameCode)) {
            user.setXiangqiOfflineStatsJson(json);
        } else if (GAME_CODE_MINESWEEPER.equals(gameCode)) {
            user.setMinesweeperStatsJson(json);
        } else {
            return ServiceResult.error("Unsupported gameCode");
        }
        userAccountRepository.save(user);
        return ServiceResult.ok(Map.of());
    }

    private Map<String, Object> normalizeStatsByGameCode(String gameCode, Object rawStats) {
        Map<String, Object> source = asStringObjectMap(rawStats);
        if (GAME_CODE_CHESS_OFFLINE.equals(gameCode)) {
            return normalizeChessStats(source);
        }
        if (GAME_CODE_XIANGQI_OFFLINE.equals(gameCode)) {
            return normalizeXiangqiStats(source);
        }
        if (GAME_CODE_MINESWEEPER.equals(gameCode)) {
            return normalizeMinesweeperStats(source);
        }
        return Map.of();
    }

    private Map<String, Object> mergeStatsByGameCode(String gameCode,
                                                     Map<String, Object> existing,
                                                     Map<String, Object> incoming) {
        if (GAME_CODE_CHESS_OFFLINE.equals(gameCode)) {
            return mergeChessStats(existing, incoming);
        }
        if (GAME_CODE_XIANGQI_OFFLINE.equals(gameCode)) {
            return mergeXiangqiStats(existing, incoming);
        }
        if (GAME_CODE_MINESWEEPER.equals(gameCode)) {
            return mergeMinesweeperStats(existing, incoming);
        }
        return Map.of();
    }

    private Map<String, Object> normalizeChessStats(Map<String, Object> source) {
        int whiteWins = Math.max(0, toInt(source.get("whiteWins"), 0));
        int blackWins = Math.max(0, toInt(source.get("blackWins"), 0));
        int draws = Math.max(0, toInt(source.get("draws"), 0));
        return Map.of(
            "whiteWins", whiteWins,
            "blackWins", blackWins,
            "draws", draws
        );
    }

    private Map<String, Object> normalizeXiangqiStats(Map<String, Object> source) {
        int redWins = Math.max(0, toInt(source.get("redWins"), 0));
        int blackWins = Math.max(0, toInt(source.get("blackWins"), 0));
        int draws = Math.max(0, toInt(source.get("draws"), 0));
        return Map.of(
            "redWins", redWins,
            "blackWins", blackWins,
            "draws", draws
        );
    }

    private Map<String, Object> normalizeMinesweeperStats(Map<String, Object> source) {
        int totalGames = Math.max(0, toInt(source.get("totalGames"), 0));
        int wins = Math.max(0, toInt(source.get("wins"), 0));
        int losses = Math.max(0, toInt(source.get("losses"), Math.max(0, totalGames - wins)));
        totalGames = Math.max(totalGames, wins + losses);

        Map<String, Integer> bestTimes = normalizeBestTimesMap(source.get("bestTimes"));

        Map<String, Object> result = new HashMap<>();
        result.put("totalGames", totalGames);
        result.put("wins", wins);
        result.put("losses", losses);
        result.put("bestTimes", bestTimes);
        return result;
    }

    private Map<String, Object> mergeChessStats(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> safeExisting = normalizeChessStats(asStringObjectMap(existing));
        Map<String, Object> safeIncoming = normalizeChessStats(asStringObjectMap(incoming));
        int whiteWins = Math.max(toInt(safeExisting.get("whiteWins"), 0), toInt(safeIncoming.get("whiteWins"), 0));
        int blackWins = Math.max(toInt(safeExisting.get("blackWins"), 0), toInt(safeIncoming.get("blackWins"), 0));
        int draws = Math.max(toInt(safeExisting.get("draws"), 0), toInt(safeIncoming.get("draws"), 0));
        return Map.of(
            "whiteWins", whiteWins,
            "blackWins", blackWins,
            "draws", draws
        );
    }

    private Map<String, Object> mergeXiangqiStats(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> safeExisting = normalizeXiangqiStats(asStringObjectMap(existing));
        Map<String, Object> safeIncoming = normalizeXiangqiStats(asStringObjectMap(incoming));
        int redWins = Math.max(toInt(safeExisting.get("redWins"), 0), toInt(safeIncoming.get("redWins"), 0));
        int blackWins = Math.max(toInt(safeExisting.get("blackWins"), 0), toInt(safeIncoming.get("blackWins"), 0));
        int draws = Math.max(toInt(safeExisting.get("draws"), 0), toInt(safeIncoming.get("draws"), 0));
        return Map.of(
            "redWins", redWins,
            "blackWins", blackWins,
            "draws", draws
        );
    }

    private Map<String, Object> mergeMinesweeperStats(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> safeExisting = normalizeMinesweeperStats(asStringObjectMap(existing));
        Map<String, Object> safeIncoming = normalizeMinesweeperStats(asStringObjectMap(incoming));

        int wins = Math.max(toInt(safeExisting.get("wins"), 0), toInt(safeIncoming.get("wins"), 0));
        int losses = Math.max(toInt(safeExisting.get("losses"), 0), toInt(safeIncoming.get("losses"), 0));
        int totalGames = Math.max(toInt(safeExisting.get("totalGames"), 0), toInt(safeIncoming.get("totalGames"), 0));
        totalGames = Math.max(totalGames, wins + losses);

        Map<String, Integer> mergedBestTimes = normalizeBestTimesMap(safeExisting.get("bestTimes"));
        Map<String, Integer> incomingBestTimes = normalizeBestTimesMap(safeIncoming.get("bestTimes"));
        for (Map.Entry<String, Integer> entry : incomingBestTimes.entrySet()) {
            String key = entry.getKey();
            Integer sec = entry.getValue();
            Integer prev = mergedBestTimes.get(key);
            if (prev == null || sec < prev) {
                mergedBestTimes.put(key, sec);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalGames", totalGames);
        result.put("wins", wins);
        result.put("losses", losses);
        result.put("bestTimes", mergedBestTimes);
        return result;
    }

    private Map<String, Integer> normalizeBestTimesMap(Object raw) {
        Map<String, Integer> result = new HashMap<>();
        if (!(raw instanceof Map<?, ?> source)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey()).trim();
            if (key.isEmpty()) {
                continue;
            }
            int sec = toInt(entry.getValue(), -1);
            if (sec >= 0) {
                result.put(key, sec);
            }
        }
        return result;
    }

    private Map<String, Object> asStringObjectMap(Object raw) {
        Map<String, Object> result = new HashMap<>();
        if (!(raw instanceof Map<?, ?> source)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        String raw = trimToNull(json);
        if (raw == null) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeEmail(String email) {
        String normalized = trimToNull(email);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private boolean isDefaultAdminEmail(String email) {
        String normalized = normalizeEmail(email);
        return normalized != null && normalized.equals(defaultAdminEmail);
    }

    private void applyPostLoginState(UserAccount user) {
        boolean updated = false;
        if (isDefaultAdminEmail(user.getEmail()) && !"Admin".equalsIgnoreCase(user.getRole())) {
            user.setRole("Admin");
            updated = true;
        }

        if (!user.isEmailConfirmed()) {
            user.setEmailConfirmed(true);
            updated = true;
        }

        if (!user.isOnline()) {
            user.setOnline(true);
            updated = true;
        }

        if (updated || user.getId() == null || user.getId().isBlank()) {
            userAccountRepository.save(user);
        }
    }

    private Map<String, Object> toLoginPayload(UserAccount user) {
        return Map.of(
            "userId", user.getId(),
            "email", user.getEmail() == null ? "" : user.getEmail(),
            "displayName", user.getDisplayName() == null ? "Player" : user.getDisplayName(),
            "role", user.getRole() == null ? "User" : user.getRole(),
            "avatarPath", user.getAvatarPath() == null ? "/uploads/avatars/default-avatar.jpg" : user.getAvatarPath()
        );
    }

    private String syntheticOauthEmail(String provider, String providerUserId) {
        String providerPart = provider == null ? "social" : provider.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String idPart = providerUserId.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        return providerPart + "-" + idPart + "@oauth.local";
    }

    private String defaultDisplayNameFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return "Player";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "Player";
        }
        String prefix = email.substring(0, at).trim();
        return prefix.isEmpty() ? "Player" : prefix;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeThemeMode(String mode) {
        String normalized = trimToNull(mode);
        if (normalized == null) {
            return "system";
        }
        String lowered = normalized.toLowerCase();
        return ALLOWED_THEME_MODES.contains(lowered) ? lowered : "system";
    }

    private String normalizeLanguage(String language) {
        String normalized = trimToNull(language);
        if (normalized == null) {
            return "vi";
        }
        String lowered = normalized.toLowerCase();
        return ALLOWED_LANGUAGES.contains(lowered) ? lowered : "vi";
    }

    private boolean valueOrDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private int normalizeFriendRefreshMs(Integer requested, int fallback) {
        int value = requested == null ? fallback : requested;
        if (ALLOWED_FRIEND_REFRESH_MS.contains(value)) {
            return value;
        }
        return 5000;
    }

    private Map<String, Object> toPreferencesPayload(UserAccount user) {
        String themeMode = normalizeThemeMode(user.getThemeMode());
        String language = normalizeLanguage(user.getLanguage());
        int refreshMs = normalizeFriendRefreshMs(user.getFriendListRefreshMs(), 5000);
        return Map.of(
            "themeMode", themeMode,
            "language", language,
            "sidebarDesktopVisibleByDefault", user.isSidebarDesktopVisibleByDefault(),
            "sidebarMobileAutoClose", user.isSidebarMobileAutoClose(),
            "homeMusicEnabled", user.isHomeMusicEnabled(),
            "toastNotificationsEnabled", user.isToastNotificationsEnabled(),
            "showOfflineFriendsInSidebar", user.isShowOfflineFriendsInSidebar(),
            "autoRefreshFriendList", user.isAutoRefreshFriendList(),
            "friendListRefreshMs", refreshMs
        );
    }

    private ServiceResult issueVerificationCode(String normalizedEmail, boolean keepPendingOnSendFailure) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.plusMinutes(VERIFICATION_CODE_TTL_MINUTES);
        String code = String.valueOf(100000 + random.nextInt(900000));

        EmailVerificationToken token = new EmailVerificationToken();
        token.setEmail(normalizedEmail);
        token.setToken(code);
        token.setCreatedAt(now);
        token.setExpireAt(expireAt);
        emailVerificationTokenRepository.save(token);

        try {
            emailService.sendEmail(normalizedEmail, "Caro Verify Email", "Your verification code is: " + code);
        } catch (RuntimeException ex) {
            emailVerificationTokenRepository.delete(token);
            if (!keepPendingOnSendFailure) {
                PendingRegisterStore.remove(normalizedEmail);
            }
            return ServiceResult.error("Cannot send verification email right now. Please try again.");
        }

        RegisterRequest pending = PendingRegisterStore.get(normalizedEmail);
        if (pending != null) {
            PendingRegisterStore.put(normalizedEmail, pending, expireAt);
        }
        cleanupOldVerificationTokens(normalizedEmail, token.getId());
        return ServiceResult.ok(Map.of(
            "email", normalizedEmail,
            "message", "Verification code sent",
            "expireInSeconds", VERIFICATION_CODE_TTL_MINUTES * 60
        ));
    }

    private void cleanupOldVerificationTokens(String email, Long keepTokenId) {
        List<EmailVerificationToken> tokens = emailVerificationTokenRepository.findByEmail(email);
        List<EmailVerificationToken> toDelete = tokens.stream()
            .filter(t -> keepTokenId == null || t.getId() == null || !keepTokenId.equals(t.getId()))
            .toList();
        if (!toDelete.isEmpty()) {
            emailVerificationTokenRepository.deleteAll(toDelete);
        }
    }

    private ServiceResult validateResendCooldown(String normalizedEmail) {
        LocalDateTime now = LocalDateTime.now();
        Optional<LocalDateTime> latestCreatedAt = emailVerificationTokenRepository.findByEmail(normalizedEmail).stream()
            .map(EmailVerificationToken::getCreatedAt)
            .filter(java.util.Objects::nonNull)
            .max(LocalDateTime::compareTo);

        if (latestCreatedAt.isEmpty()) {
            return ServiceResult.ok(Map.of());
        }

        LocalDateTime nextAllowed = latestCreatedAt.get().plusSeconds(VERIFICATION_RESEND_COOLDOWN_SECONDS);
        if (nextAllowed.isAfter(now)) {
            long waitSeconds = java.time.Duration.between(now, nextAllowed).toSeconds();
            if (waitSeconds <= 0) {
                waitSeconds = 1;
            }
            return ServiceResult.error("Please wait " + waitSeconds + " seconds before requesting a new code");
        }
        return ServiceResult.ok(Map.of());
    }
}
