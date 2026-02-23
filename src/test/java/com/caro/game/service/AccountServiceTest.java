package com.caro.game.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class AccountServiceTest {

    @Autowired
    private AccountService accountService;
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
}
