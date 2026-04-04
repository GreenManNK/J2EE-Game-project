package com.game.hub.controller;

import com.game.hub.service.PrivateChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    @Test
    void privateChatApiShouldRequireLoginSession() {
        PrivateChatService chatService = mock(PrivateChatService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        ChatController controller = new ChatController(chatService, messagingTemplate);
        Map<String, Object> response = controller.privateChat("u1", "u2", request);

        assertFalse((Boolean) response.get("success"));
        assertEquals("Login required", response.get("error"));
    }

    @Test
    void privateChatApiShouldUseSessionUserWhenRequestParamMismatch() {
        PrivateChatService chatService = mock(PrivateChatService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u99");
        when(chatService.buildChatBootstrap("u99", "u2")).thenReturn(
            PrivateChatService.ChatBootstrapResult.success("u99", "Carol", "u2", "Bob", "u2__u99", List.of())
        );

        ChatController controller = new ChatController(chatService, messagingTemplate);
        Map<String, Object> response = controller.privateChat("u1", "u2", request);

        assertTrue((Boolean) response.get("success"));
        assertEquals("u99", response.get("currentUserId"));
        assertEquals("u2__u99", response.get("roomKey"));
    }

    @Test
    void privateChatApiShouldDelegateWhenSessionMatches() {
        PrivateChatService chatService = mock(PrivateChatService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");
        when(chatService.buildChatBootstrap("u1", "u2")).thenReturn(
            PrivateChatService.ChatBootstrapResult.success("u1", "Alice", "u2", "Bob", "u1__u2", List.of())
        );

        ChatController controller = new ChatController(chatService, messagingTemplate);
        Map<String, Object> response = controller.privateChat("u1", "u2", request);

        assertTrue((Boolean) response.get("success"));
        assertEquals("u1__u2", response.get("roomKey"));
    }

    @Test
    void sendPrivateMessageShouldPersistAndBroadcastToTopicAndQueues() {
        PrivateChatService chatService = mock(PrivateChatService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");

        Map<String, Object> payload = Map.of(
            "messageId", 42L,
            "clientMessageId", "cid-42",
            "type", "PRIVATE_CHAT",
            "roomKey", "u1__u2",
            "fromUserId", "u1",
            "toUserId", "u2",
            "senderName", "Alice",
            "message", "hello"
        );
        when(chatService.saveMessage("u1", "u2", "hello", "cid-42")).thenReturn(
            PrivateChatService.SendResult.success("u1__u2", "u1", payload)
        );

        ChatController controller = new ChatController(chatService, messagingTemplate);
        Map<String, Object> response = controller.sendPrivateMessage(
            new ChatController.SendPrivateChatRequest("u2", "hello", "cid-42"),
            request
        );

        assertTrue((Boolean) response.get("success"));
        assertEquals(payload, response.get("payload"));
        verify(messagingTemplate).convertAndSend("/topic/private.u1__u2", payload);
        verify(messagingTemplate).convertAndSendToUser("u1", "/queue/private-chat", payload);
        verify(messagingTemplate).convertAndSendToUser("u2", "/queue/private-chat", payload);
    }

    @Test
    void sendPrivateMessageShouldReturnWarningWhenMessageIsMasked() {
        PrivateChatService chatService = mock(PrivateChatService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");

        Map<String, Object> payload = Map.of(
            "messageId", 52L,
            "clientMessageId", "cid-52",
            "type", "PRIVATE_CHAT",
            "roomKey", "u1__u2",
            "fromUserId", "u1",
            "toUserId", "u2",
            "senderName", "Alice",
            "message", "******"
        );
        when(chatService.saveMessage("u1", "u2", "v.c.l", "cid-52")).thenReturn(
            PrivateChatService.SendResult.success(
                "u1__u2",
                "u1",
                payload,
                "Tin nhan chua ngon tu tho tuc va da bi chan. Noi dung da duoc an thanh ******. Canh cao 1/3."
            )
        );

        ChatController controller = new ChatController(chatService, messagingTemplate);
        Map<String, Object> response = controller.sendPrivateMessage(
            new ChatController.SendPrivateChatRequest("u2", "v.c.l", "cid-52"),
            request
        );

        assertTrue((Boolean) response.get("success"));
        assertEquals(payload, response.get("payload"));
        assertEquals("Tin nhan chua ngon tu tho tuc va da bi chan. Noi dung da duoc an thanh ******. Canh cao 1/3.", response.get("warning"));
    }
}
