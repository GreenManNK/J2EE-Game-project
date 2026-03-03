package com.game.hub.service;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class AccountService {
    private static final int VERIFICATION_CODE_TTL_MINUTES = 5;
    private static final long VERIFICATION_RESEND_COOLDOWN_SECONDS = 30;

    private final UserAccountRepository userAccountRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String defaultAdminEmail;
    private final String adminActivationCode;
    private final Random random = new Random();

    public AccountService(UserAccountRepository userAccountRepository,
                          EmailVerificationTokenRepository emailVerificationTokenRepository,
                          PasswordResetTokenRepository passwordResetTokenRepository,
                          PasswordEncoder passwordEncoder,
                          EmailService emailService,
                          @Value("${app.admin.default-email:luckhaikiet@gmail.com}") String defaultAdminEmail,
                          @Value("${app.admin.activation-code:j2ee20262027}") String adminActivationCode) {
        this.userAccountRepository = userAccountRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
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
        return ServiceResult.ok(Map.of("userId", user.getId(), "message", "Account created"));
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

        boolean updated = false;
        if (isDefaultAdminEmail(user.getEmail()) && !"Admin".equalsIgnoreCase(user.getRole())) {
            user.setRole("Admin");
            updated = true;
        }

        user.setOnline(true);
        updated = true;
        if (updated) {
            userAccountRepository.save(user);
        }
        return ServiceResult.ok(Map.of(
            "userId", user.getId(),
            "email", user.getEmail(),
            "displayName", user.getDisplayName(),
            "role", user.getRole(),
            "avatarPath", user.getAvatarPath() == null ? "/uploads/avatars/default-avatar.jpg" : user.getAvatarPath()
        ));
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

    public record ServiceResult(boolean success, String error, Object data) {
        public static ServiceResult ok(Object data) {
            return new ServiceResult(true, null, data);
        }

        public static ServiceResult error(String error) {
            return new ServiceResult(false, error, null);
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

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
