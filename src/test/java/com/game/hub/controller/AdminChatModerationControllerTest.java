package com.game.hub.controller;

import com.game.hub.entity.ChatModerationTerm;
import com.game.hub.service.ChatModerationTermService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminChatModerationControllerTest {

    @Test
    void listTermsShouldReturnDefaultAndDatabaseTerms() {
        ChatModerationTermService service = mock(ChatModerationTermService.class);
        when(service.listAdminTerms()).thenReturn(List.of(
            new ChatModerationTermService.ModerationTermView(null, "vcl", "default", false),
            new ChatModerationTermService.ModerationTermView(10L, "lag switch", "database", true)
        ));

        AdminChatModerationController controller = new AdminChatModerationController(service);
        Object result = controller.listTerms();

        assertInstanceOf(Map.class, result);
        Map<?, ?> payload = (Map<?, ?>) result;
        assertTrue((Boolean) payload.get("success"));
        assertInstanceOf(List.class, payload.get("terms"));
        assertEquals(2, ((List<?>) payload.get("terms")).size());
    }

    @Test
    void createTermShouldReturnValidationErrorFromService() {
        ChatModerationTermService service = mock(ChatModerationTermService.class);
        when(service.addTerm("")).thenThrow(new IllegalArgumentException("Cum tu khong hop le"));

        AdminChatModerationController controller = new AdminChatModerationController(service);
        Object result = controller.createTerm(new AdminChatModerationController.CreateTermRequest(""));

        assertInstanceOf(Map.class, result);
        Map<?, ?> payload = (Map<?, ?>) result;
        assertFalse((Boolean) payload.get("success"));
        assertEquals("Cum tu khong hop le", payload.get("error"));
    }

    @Test
    void createTermShouldReturnCreatedDatabaseTerm() {
        ChatModerationTermService service = mock(ChatModerationTermService.class);
        ChatModerationTerm created = new ChatModerationTerm();
        created.setId(3L);
        created.setTerm("lag switch");
        when(service.addTerm("lag switch")).thenReturn(created);

        AdminChatModerationController controller = new AdminChatModerationController(service);
        Object result = controller.createTerm(new AdminChatModerationController.CreateTermRequest("lag switch"));

        assertInstanceOf(Map.class, result);
        Map<?, ?> payload = (Map<?, ?>) result;
        assertTrue((Boolean) payload.get("success"));
        assertInstanceOf(Map.class, payload.get("term"));
    }
}
