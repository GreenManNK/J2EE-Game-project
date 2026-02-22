package com.caro.game.service;

import com.caro.game.entity.EmailVerificationToken;
import com.caro.game.entity.PasswordResetToken;
import com.caro.game.entity.UserAccount;
import com.caro.game.repository.EmailVerificationTokenRepository;
import com.caro.game.repository.PasswordResetTokenRepository;
import com.caro.game.repository.UserAccountRepository;
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
    private final UserAccountRepository userAccountRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final Random random = new Random();

    public AccountService(UserAccountRepository userAccountRepository,
                          EmailVerificationTokenRepository emailVerificationTokenRepository,
                          PasswordResetTokenRepository passwordResetTokenRepository,
                          PasswordEncoder passwordEncoder,
                          EmailService emailService) {
        this.userAccountRepository = userAccountRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    public ServiceResult register(RegisterRequest request) {
        if (userAccountRepository.findByEmail(request.email()).isPresent()) {
            return ServiceResult.error("Email already exists");
        }
        if (PendingRegisterStore.get(request.email()) != null) {
            return ServiceResult.error("Email is waiting for verification");
        }

        String code = String.valueOf(100000 + random.nextInt(900000));
        EmailVerificationToken token = new EmailVerificationToken();
        token.setEmail(request.email());
        token.setToken(code);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpireAt(LocalDateTime.now().plusMinutes(5));
        emailVerificationTokenRepository.save(token);

        PendingRegisterStore.put(request.email(), request);
        emailService.sendEmail(request.email(), "Caro Verify Email", "Your verification code is: " + code);

        return ServiceResult.ok(Map.of("email", request.email(), "message", "Verification code sent"));
    }

    public ServiceResult verifyEmail(String email, String code) {
        Optional<EmailVerificationToken> tokenOpt = emailVerificationTokenRepository
            .findTopByEmailAndTokenOrderByCreatedAtDesc(email, code);

        if (tokenOpt.isEmpty() || tokenOpt.get().getExpireAt().isBefore(LocalDateTime.now())) {
            return ServiceResult.error("Invalid or expired verification code");
        }

        RegisterRequest pending = PendingRegisterStore.get(email);
        if (pending == null) {
            return ServiceResult.error("No pending registration for this email");
        }

        UserAccount user = new UserAccount();
        user.setEmail(pending.email());
        user.setUsername(pending.email());
        user.setDisplayName(pending.displayName());
        user.setAvatarPath(pending.avatarPath() == null || pending.avatarPath().isBlank()
            ? "/uploads/avatars/default-avatar.jpg" : pending.avatarPath());
        user.setPassword(passwordEncoder.encode(pending.password()));
        user.setEmailConfirmed(true);
        user.setRole("User");
        userAccountRepository.save(user);

        PendingRegisterStore.remove(email);
        return ServiceResult.ok(Map.of("userId", user.getId(), "message", "Account created"));
    }

    public ServiceResult login(String email, String password) {
        UserAccount user = userAccountRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ServiceResult.error("Account not found");
        }

        if (user.isBanned()) {
            return ServiceResult.error("Account banned until " + user.getBannedUntil());
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ServiceResult.error("Invalid credentials");
        }

        user.setOnline(true);
        userAccountRepository.save(user);
        return ServiceResult.ok(Map.of(
            "userId", user.getId(),
            "email", user.getEmail(),
            "displayName", user.getDisplayName(),
            "role", user.getRole()
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

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ServiceResult.error("Current password incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userAccountRepository.save(user);
        return ServiceResult.ok(Map.of("message", "Password changed"));
    }

    public ServiceResult sendResetCode(String email) {
        UserAccount user = userAccountRepository.findByEmail(email).orElse(null);
        if (user == null) return ServiceResult.error("Email not found");

        String code = String.valueOf(100000 + random.nextInt(900000));
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setToken(code);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpireAt(LocalDateTime.now().plusMinutes(5));
        passwordResetTokenRepository.save(token);

        emailService.sendEmail(email, "Caro Reset Password", "Your reset code is: " + code);
        return ServiceResult.ok(Map.of("userId", user.getId(), "message", "Reset code sent"));
    }

    public ServiceResult verifyResetCode(String userId, String code) {
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository
            .findTopByUserIdAndTokenOrderByCreatedAtDesc(userId, code);

        if (tokenOpt.isEmpty() || tokenOpt.get().getExpireAt().isBefore(LocalDateTime.now())) {
            return ServiceResult.error("Invalid or expired reset code");
        }
        return ServiceResult.ok(Map.of("message", "Code verified"));
    }

    public ServiceResult resetPassword(String userId, String code, String newPassword, String confirmPassword) {
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

    public static final class PendingRegisterStore {
        private static final Map<String, RegisterRequest> STORE = new java.util.concurrent.ConcurrentHashMap<>();

        private PendingRegisterStore() {
        }

        public static void put(String email, RegisterRequest request) {
            STORE.put(email, request);
        }

        public static RegisterRequest get(String email) {
            return STORE.get(email);
        }

        public static void remove(String email) {
            STORE.remove(email);
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
}
