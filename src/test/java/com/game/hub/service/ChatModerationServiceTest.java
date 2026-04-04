package com.game.hub.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatModerationServiceTest {

    private final ChatModerationService service = new ChatModerationService();

    @Test
    void moderateShouldRejectProfanityEvenWhenObfuscated() {
        ChatModerationService.ModerationResult result = service.moderate("D!t   m3 may");

        assertFalse(result.allowed());
    }

    @Test
    void moderateShouldAllowNormalMessage() {
        ChatModerationService.ModerationResult result = service.moderate("Chuc ban choi vui ve va gap may man.");

        assertTrue(result.allowed());
    }

    @Test
    void moderateShouldRejectDatabaseCustomTerm() {
        ChatModerationTermService termService = mock(ChatModerationTermService.class);
        when(termService.listDatabaseTermStrings()).thenReturn(List.of("lag switch"));
        service.setChatModerationTermService(termService);

        ChatModerationService.ModerationResult result = service.moderate("thang kia dung lag-switch di");

        assertFalse(result.allowed());
    }
}
