package com.caro.game.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

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
}
