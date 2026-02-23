package com.caro.game.controller;

import com.caro.game.service.PrivateChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    @Test
    void privateChatApiShouldRequireLoginSession() {
        PrivateChatService chatService = mock(PrivateChatService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        ChatController controller = new ChatController(chatService);
        Map<String, Object> response = controller.privateChat("u1", "u2", request);

        assertFalse((Boolean) response.get("success"));
        assertEquals("Login required", response.get("error"));
    }

    @Test
    void privateChatApiShouldRejectSessionMismatch() {
        PrivateChatService chatService = mock(PrivateChatService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u99");

        ChatController controller = new ChatController(chatService);
        Map<String, Object> response = controller.privateChat("u1", "u2", request);

        assertFalse((Boolean) response.get("success"));
        assertTrue(String.valueOf(response.get("error")).contains("mismatch"));
    }

    @Test
    void privateChatApiShouldDelegateWhenSessionMatches() {
        PrivateChatService chatService = mock(PrivateChatService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");
        when(chatService.buildChatBootstrap("u1", "u2")).thenReturn(
            PrivateChatService.ChatBootstrapResult.success("u1", "Alice", "u2", "Bob", "u1__u2", List.of())
        );

        ChatController controller = new ChatController(chatService);
        Map<String, Object> response = controller.privateChat("u1", "u2", request);

        assertTrue((Boolean) response.get("success"));
        assertEquals("u1__u2", response.get("roomKey"));
    }
}
