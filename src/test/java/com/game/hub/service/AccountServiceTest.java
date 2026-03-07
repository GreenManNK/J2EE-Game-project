package com.game.hub.service;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class AccountServiceTest {

    @Autowired
    private AccountService accountService;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockBean
    private EmailService emailService;

    @Test
    void shouldBlockDuplicatePendingRegistration() {
        String email = "dup-pending@test.com";

        AccountService.ServiceResult first = accountService.register(
            new AccountService.RegisterRequest(email, "User A", "Pass@123", "")
        );
        AccountService.ServiceResult second = accountService.register(
            new AccountService.RegisterRequest(email, "User B", "Pass@123", "")
        );

        assertTrue(first.success());
        assertFalse(second.success());
    }

    @Test
    void shouldRollbackPendingRegistrationWhenVerifyEmailSendFails() {
        String email = "mail-fail@test.com";
        doThrow(new IllegalStateException("smtp down")).when(emailService)
            .sendEmail(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        AccountService.ServiceResult first = accountService.register(
            new AccountService.RegisterRequest(email, "Mail Fail", "Pass@123", "")
        );
        AccountService.ServiceResult second = accountService.register(
            new AccountService.RegisterRequest(email, "Mail Retry", "Pass@123", "")
        );

        assertFalse(first.success());
        assertFalse(second.success());
        assertEquals("Cannot send verification email right now. Please try again.", first.error());
        assertEquals("Cannot send verification email right now. Please try again.", second.error());
    }

    @Test
    void shouldSupportResendVerificationCodeWithCooldown() {
        String email = "resend-code@test.com";

        AccountService.ServiceResult register = accountService.register(
            new AccountService.RegisterRequest(email, "Resend User", "Pass@123", "")
        );
        AccountService.ServiceResult resendImmediately = accountService.resendVerificationCode(email);

        assertTrue(register.success());
        assertFalse(resendImmediately.success());
        assertTrue(resendImmediately.error().contains("Please wait"));
        verify(emailService).sendEmail(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void loginShouldUseGenericErrorForUnknownAccount() {
        AccountService.ServiceResult result = accountService.login("unknown-login@test.com", "Pass@123");

        assertFalse(result.success());
        assertEquals("Invalid email or password", result.error());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendResetCodeShouldNotRevealWhetherEmailExists() {
        String existingEmail = "reset-known@test.com";
        UserAccount user = new UserAccount();
        user.setEmail(existingEmail);
        user.setUsername(existingEmail);
        user.setDisplayName("Reset Known");
        user.setPassword(passwordEncoder.encode("Pass@123"));
        user.setEmailConfirmed(true);
        userAccountRepository.save(user);

        AccountService.ServiceResult missing = accountService.sendResetCode("reset-missing@test.com");
        AccountService.ServiceResult existing = accountService.sendResetCode(existingEmail);

        assertTrue(missing.success());
        assertTrue(existing.success());
        assertNotNull(missing.data());
        assertNotNull(existing.data());

        Map<String, Object> missingData = (Map<String, Object>) missing.data();
        Map<String, Object> existingData = (Map<String, Object>) existing.data();

        assertEquals("If the email exists, a reset code has been sent", missingData.get("message"));
        assertEquals("If the email exists, a reset code has been sent", existingData.get("message"));
        assertNull(missingData.get("userId"));
        assertNull(existingData.get("userId"));
    }

    @Test
    void changePasswordShouldRejectBlankNewPassword() {
        UserAccount user = new UserAccount();
        user.setEmail("change-pass@test.com");
        user.setUsername("change-pass@test.com");
        user.setDisplayName("Change Pass");
        user.setPassword(passwordEncoder.encode("OldPass@123"));
        user.setEmailConfirmed(true);
        userAccountRepository.save(user);

        AccountService.ServiceResult result = accountService.changePassword(user.getId(), "OldPass@123", "   ");

        assertFalse(result.success());
        assertEquals("Current password and new password are required", result.error());
    }

    @Test
    void resetPasswordShouldRejectMissingNewPasswordWithoutThrowing() {
        AccountService.ServiceResult result = accountService.resetPassword("user-id", "123456", null, "new-pass");

        assertFalse(result.success());
        assertEquals("New password and confirmation are required", result.error());
    }

    @Test
    void updatePreferencesShouldPersistToDatabase() {
        UserAccount user = new UserAccount();
        user.setEmail("prefs@test.com");
        user.setUsername("prefs@test.com");
        user.setDisplayName("Prefs User");
        user.setPassword(passwordEncoder.encode("Pass@123"));
        user.setEmailConfirmed(true);
        userAccountRepository.save(user);

        AccountService.ServiceResult result = accountService.updatePreferences(
            user.getId(),
            new AccountService.PreferencesRequest(
                "dark",
                "en",
                true,
                false,
                false,
                false,
                false,
                false,
                30000
            )
        );

        assertTrue(result.success());
        UserAccount updated = userAccountRepository.findById(user.getId()).orElseThrow();
        assertEquals("dark", updated.getThemeMode());
        assertEquals("en", updated.getLanguage());
        assertTrue(updated.isSidebarDesktopVisibleByDefault());
        assertFalse(updated.isSidebarMobileAutoClose());
        assertFalse(updated.isHomeMusicEnabled());
        assertFalse(updated.isToastNotificationsEnabled());
        assertFalse(updated.isShowOfflineFriendsInSidebar());
        assertFalse(updated.isAutoRefreshFriendList());
        assertEquals(30000, updated.getFriendListRefreshMs());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateGameStatsShouldMergeGuestProgressIntoDatabase() {
        UserAccount user = new UserAccount();
        user.setEmail("stats-merge@test.com");
        user.setUsername("stats-merge@test.com");
        user.setDisplayName("Stats Merge");
        user.setPassword(passwordEncoder.encode("Pass@123"));
        user.setEmailConfirmed(true);
        userAccountRepository.save(user);

        AccountService.ServiceResult firstUpdate = accountService.updateGameStats(
            user.getId(),
            "chess-offline",
            Map.of("whiteWins", 2, "blackWins", 1, "draws", 0),
            true
        );
        AccountService.ServiceResult secondUpdate = accountService.updateGameStats(
            user.getId(),
            "chess-offline",
            Map.of("whiteWins", 1, "blackWins", 4, "draws", 3),
            true
        );

        assertTrue(firstUpdate.success());
        assertTrue(secondUpdate.success());

        AccountService.ServiceResult readResult = accountService.getGameStats(user.getId(), "chess-offline");
        assertTrue(readResult.success());
        assertNotNull(readResult.data());

        Map<String, Object> payload = (Map<String, Object>) readResult.data();
        Map<String, Object> stats = (Map<String, Object>) payload.get("stats");
        assertEquals(2, ((Number) stats.get("whiteWins")).intValue());
        assertEquals(4, ((Number) stats.get("blackWins")).intValue());
        assertEquals(3, ((Number) stats.get("draws")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updatePuzzleCatalogStateShouldSupportMergeAndReplaceModes() {
        UserAccount user = new UserAccount();
        user.setEmail("puzzle-state@test.com");
        user.setUsername("puzzle-state@test.com");
        user.setDisplayName("Puzzle State");
        user.setPassword(passwordEncoder.encode("Pass@123"));
        user.setEmailConfirmed(true);
        userAccountRepository.save(user);

        AccountService.ServiceResult firstUpdate = accountService.updatePuzzleCatalogState(
            user.getId(),
            new AccountService.PuzzleCatalogStateRequest(
                java.util.List.of("sudoku", "word"),
                Map.of("sudoku", 5),
                java.util.List.of("word"),
                false
            )
        );
        AccountService.ServiceResult mergeUpdate = accountService.updatePuzzleCatalogState(
            user.getId(),
            new AccountService.PuzzleCatalogStateRequest(
                java.util.List.of("jigsaw"),
                Map.of("word", 4),
                java.util.List.of("jigsaw"),
                true
            )
        );
        AccountService.ServiceResult replaceUpdate = accountService.updatePuzzleCatalogState(
            user.getId(),
            new AccountService.PuzzleCatalogStateRequest(
                java.util.List.of("word"),
                Map.of("word", 2),
                java.util.List.of("word"),
                false
            )
        );

        assertTrue(firstUpdate.success());
        assertTrue(mergeUpdate.success());
        assertTrue(replaceUpdate.success());

        AccountService.ServiceResult readResult = accountService.getPuzzleCatalogState(user.getId());
        assertTrue(readResult.success());

        Map<String, Object> payload = (Map<String, Object>) readResult.data();
        assertEquals(java.util.List.of("word"), payload.get("favorites"));
        assertEquals(java.util.List.of("word"), payload.get("recentCodes"));
        assertEquals(Map.of("word", 2), payload.get("ratings"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateGamesBrowserStateShouldSupportMergeAndReplaceModes() {
        UserAccount user = new UserAccount();
        user.setEmail("games-browser-state@test.com");
        user.setUsername("games-browser-state@test.com");
        user.setDisplayName("Games Browser State");
        user.setPassword(passwordEncoder.encode("Pass@123"));
        user.setEmailConfirmed(true);
        userAccountRepository.save(user);

        AccountService.ServiceResult firstUpdate = accountService.updateGamesBrowserState(
            user.getId(),
            new AccountService.GamesBrowserStateRequest(
                java.util.List.of("caro", "chess"),
                java.util.List.of(
                    Map.of("code", "chess", "name", "Chess", "at", 1762291200000L)
                ),
                false
            )
        );
        AccountService.ServiceResult mergeUpdate = accountService.updateGamesBrowserState(
            user.getId(),
            new AccountService.GamesBrowserStateRequest(
                java.util.List.of("xiangqi"),
                java.util.List.of(
                    Map.of("code", "xiangqi", "name", "Xiangqi", "at", 1762291300000L)
                ),
                true
            )
        );
        AccountService.ServiceResult replaceUpdate = accountService.updateGamesBrowserState(
            user.getId(),
            new AccountService.GamesBrowserStateRequest(
                java.util.List.of("caro"),
                java.util.List.of(
                    Map.of("code", "caro", "name", "Caro", "at", 1762291400000L)
                ),
                false
            )
        );

        assertTrue(firstUpdate.success());
        assertTrue(mergeUpdate.success());
        assertTrue(replaceUpdate.success());

        AccountService.ServiceResult readResult = accountService.getGamesBrowserState(user.getId());
        assertTrue(readResult.success());

        Map<String, Object> payload = (Map<String, Object>) readResult.data();
        assertEquals(java.util.List.of("caro"), payload.get("favorites"));
        java.util.List<Map<String, Object>> recentGames = (java.util.List<Map<String, Object>>) payload.get("recentGames");
        assertEquals(1, recentGames.size());
        assertEquals("caro", recentGames.get(0).get("code"));
    }
}
