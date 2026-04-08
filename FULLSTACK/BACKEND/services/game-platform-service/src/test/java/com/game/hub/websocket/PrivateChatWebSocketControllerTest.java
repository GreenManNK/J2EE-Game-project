package com.game.hub.websocket;

import com.game.hub.service.PrivateChatService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrivateChatWebSocketControllerTest {

    @Test
    void sendShouldUseSessionUserAndBroadcastToRoomAndUsers() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PrivateChatService privateChatService = mock(PrivateChatService.class);
        PrivateChatWebSocketController controller = new PrivateChatWebSocketController(messagingTemplate, privateChatService);

        PrivateChatMessage message = new PrivateChatMessage();
        message.setCurrentUserId("spoofed-user");
        message.setFriendId("u2");
        message.setContent("hello");
        message.setClientMessageId("cid-5");

        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("AUTH_USER_ID", "u1");
        headers.setSessionAttributes(sessionAttributes);

        Map<String, Object> payload = Map.of(
            "messageId", 5L,
            "clientMessageId", "cid-5",
            "type", "PRIVATE_CHAT",
            "roomKey", "u1__u2",
            "fromUserId", "u1",
            "toUserId", "u2",
            "senderName", "Alice",
            "message", "hello"
        );
        when(privateChatService.saveMessage("u1", "u2", "hello", "cid-5")).thenReturn(
            PrivateChatService.SendResult.success("u1__u2", "u1", payload)
        );

        controller.send(message, headers);

        verify(privateChatService).saveMessage("u1", "u2", "hello", "cid-5");
        verify(messagingTemplate).convertAndSend("/topic/private.u1__u2", payload);
        verify(messagingTemplate).convertAndSendToUser("u1", "/queue/private-chat", payload);
        verify(messagingTemplate).convertAndSendToUser("u2", "/queue/private-chat", payload);
    }

    @Test
    void sendShouldReturnErrorToCurrentUserWhenSessionMissing() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PrivateChatService privateChatService = mock(PrivateChatService.class);
        PrivateChatWebSocketController controller = new PrivateChatWebSocketController(messagingTemplate, privateChatService);

        PrivateChatMessage message = new PrivateChatMessage();
        message.setCurrentUserId("u1");
        message.setFriendId("u2");
        message.setContent("hello");
        message.setClientMessageId("cid-missing-session");

        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
        when(privateChatService.roomKey("u1", "u2")).thenReturn("u1__u2");

        controller.send(message, headers);

        verify(privateChatService, never()).saveMessage("u1", "u2", "hello", "cid-missing-session");
        ArgumentCaptor<Map<String, Object>> queuePayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(eq("u1"), eq("/queue/private-chat"), queuePayloadCaptor.capture());

        assertErrorPayload(queuePayloadCaptor.getValue(), "u1__u2", "u1", "Login required");
    }

    @Test
    void sendShouldBroadcastMaskedMessageAndWarningToSender() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PrivateChatService privateChatService = mock(PrivateChatService.class);
        PrivateChatWebSocketController controller = new PrivateChatWebSocketController(messagingTemplate, privateChatService);

        PrivateChatMessage message = new PrivateChatMessage();
        message.setCurrentUserId("u1");
        message.setFriendId("u2");
        message.setContent("v.c.l");
        message.setClientMessageId("cid-mask");

        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("AUTH_USER_ID", "u1");
        headers.setSessionAttributes(sessionAttributes);

        Map<String, Object> payload = Map.of(
            "messageId", 6L,
            "clientMessageId", "cid-mask",
            "type", "PRIVATE_CHAT",
            "roomKey", "u1__u2",
            "fromUserId", "u1",
            "toUserId", "u2",
            "senderName", "Alice",
            "message", "******"
        );
        when(privateChatService.saveMessage("u1", "u2", "v.c.l", "cid-mask")).thenReturn(
            PrivateChatService.SendResult.success(
                "u1__u2",
                "u1",
                payload,
                "Tin nhan chua ngon tu tho tuc va da bi chan. Noi dung da duoc an thanh ******. Canh cao 1/3."
            )
        );

        controller.send(message, headers);

        verify(messagingTemplate).convertAndSend("/topic/private.u1__u2", payload);
        verify(messagingTemplate).convertAndSendToUser("u2", "/queue/private-chat", payload);
        verify(messagingTemplate).convertAndSendToUser(eq("u1"), eq("/queue/private-chat"), argThat(data -> {
            if (!(data instanceof Map<?, ?> map)) {
                return false;
            }
            return "WARNING".equals(map.get("type"))
                && "u1".equals(map.get("userId"))
                && String.valueOf(map.get("message")).contains("Noi dung da duoc an thanh ******.");
        }));
    }

    private static void assertErrorPayload(Map<String, Object> payload,
                                           String roomKey,
                                           String userId,
                                           String error) {
        assertNotNull(payload);
        assertEquals("ERROR", payload.get("type"));
        assertEquals(roomKey, payload.get("roomKey"));
        assertEquals(userId, payload.get("userId"));
        assertEquals(error, payload.get("error"));
    }
}
