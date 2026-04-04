package com.game.hub.service;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunicationGuardServiceTest {

    @Test
    void validateShouldWarnOnFirstViolationAndPersistCount() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        UserAccount user = new UserAccount();
        user.setId("u1");
        user.setAbusiveContentViolationCount(0);
        when(repository.findById("u1")).thenReturn(Optional.of(user));
        when(repository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommunicationGuardService service = new CommunicationGuardService(repository, new ChatModerationService());
        CommunicationGuardService.ValidationResult result = service.validate("u1", "v.c.l");

        assertFalse(result.allowed());
        assertEquals("Tin nhan chua ngon tu tho tuc va da bi chan. Canh cao 1/3.", result.error());
        assertEquals(1, user.getAbusiveContentViolationCount());
        verify(repository).save(user);
    }

    @Test
    void inspectChatMessageShouldMaskProfanityAndKeepDelivery() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        UserAccount user = new UserAccount();
        user.setId("u1");
        user.setAbusiveContentViolationCount(0);
        when(repository.findById("u1")).thenReturn(Optional.of(user));
        when(repository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommunicationGuardService service = new CommunicationGuardService(repository, new ChatModerationService());
        CommunicationGuardService.ChatMessageDecision decision = service.inspectChatMessage("u1", "v.c.l");

        assertTrue(decision.allowed());
        assertTrue(decision.masked());
        assertEquals("******", decision.deliveryContent());
        assertEquals(1, user.getAbusiveContentViolationCount());
        assertTrue(decision.notice().contains("Canh cao 1/3"));
        verify(repository).save(user);
    }

    @Test
    void inspectChatMessageShouldMaskThenRestrictAtThreshold() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        UserAccount user = new UserAccount();
        user.setId("u1");
        user.setAbusiveContentViolationCount(2);
        when(repository.findById("u1")).thenReturn(Optional.of(user));
        when(repository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommunicationGuardService service = new CommunicationGuardService(repository, new ChatModerationService());
        CommunicationGuardService.ChatMessageDecision decision = service.inspectChatMessage("u1", "d!t m3 may");

        assertTrue(decision.allowed());
        assertTrue(decision.masked());
        assertEquals("******", decision.deliveryContent());
        assertNotNull(decision.restrictedUntil());
        assertTrue(decision.notice().contains("Ban bi tam khoa giao tiep den "));
    }

    @Test
    void validateShouldRestrictCommunicationAfterThreshold() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        UserAccount user = new UserAccount();
        user.setId("u1");
        user.setAbusiveContentViolationCount(2);
        when(repository.findById("u1")).thenReturn(Optional.of(user));
        when(repository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommunicationGuardService service = new CommunicationGuardService(repository, new ChatModerationService());
        CommunicationGuardService.ValidationResult result = service.validate("u1", "d!t m3 may");

        assertFalse(result.allowed());
        assertNotNull(result.restrictedUntil());
        assertTrue(result.error().contains("Ban bi tam khoa giao tiep den "));
        assertTrue(user.isCommunicationRestricted());
    }

    @Test
    void validateShouldBlockCleanMessageWhenUserIsAlreadyRestricted() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        UserAccount user = new UserAccount();
        user.setId("u1");
        user.setAbusiveContentViolationCount(3);
        user.setCommunicationRestrictedUntil(LocalDateTime.now().plusMinutes(5));
        when(repository.findById("u1")).thenReturn(Optional.of(user));

        CommunicationGuardService service = new CommunicationGuardService(repository, new ChatModerationService());
        CommunicationGuardService.ValidationResult result = service.validate("u1", "Xin loi, toi se noi chuyen lich su hon.");

        assertFalse(result.allowed());
        assertTrue(result.error().contains("Ban dang bi tam khoa giao tiep den "));
    }
}
