package com.caro.game.config;

import com.caro.game.entity.UserAccount;
import com.caro.game.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DefaultAdminRoleInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DefaultAdminRoleInitializer.class);

    private final UserAccountRepository userAccountRepository;
    private final String defaultAdminEmail;

    public DefaultAdminRoleInitializer(UserAccountRepository userAccountRepository,
                                       @Value("${app.admin.default-email:luckhaikiet@gmail.com}") String defaultAdminEmail) {
        this.userAccountRepository = userAccountRepository;
        this.defaultAdminEmail = normalizeEmail(defaultAdminEmail);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (defaultAdminEmail == null) {
            return;
        }
        userAccountRepository.findByEmail(defaultAdminEmail).ifPresent(this::ensureAdminRole);
    }

    private void ensureAdminRole(UserAccount user) {
        boolean updated = false;
        if (!"Admin".equalsIgnoreCase(user.getRole())) {
            user.setRole("Admin");
            updated = true;
        }
        if (!user.isEmailConfirmed()) {
            user.setEmailConfirmed(true);
            updated = true;
        }
        if (updated) {
            userAccountRepository.save(user);
            log.info("Enforced default admin account role for {}", defaultAdminEmail);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }
}
