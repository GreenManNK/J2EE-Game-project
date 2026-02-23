package com.caro.game.websocket;

import com.caro.game.entity.UserAccount;
import com.caro.game.repository.UserAccountRepository;
import com.caro.game.service.GameRoomService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameWebSocketControllerTest {

    @Test
    void joinShouldUseServerSidePlayerMetadataInsteadOfClientPayload() {
        GameRoomService gameRoomService = mock(GameRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "u1"));
        when(gameRoomService.joinRoom("room-1", "u1"))
            .thenReturn(new GameRoomService.JoinResult(true, "X", null, "u1", 1));
        when(gameRoomService.getBoardSnapshot("room-1")).thenReturn(new String[10][10]);
        when(gameRoomService.availableRooms()).thenReturn(List.of());
        when(userAccountRepository.findById("u1")).thenReturn(Optional.of(user("u1", "ServerName", "/server.png")));

        GameWebSocketController controller = new GameWebSocketController(gameRoomService, messagingTemplate, userAccountRepository);
        JoinGameMessage message = new JoinGameMessage();
        message.setRoomId("room-1");
        message.setUserId("u1");
        message.setDisplayName("SpoofedName");
        message.setAvatarPath("/spoof.png");

        controller.join(message, headers);

        verify(messagingTemplate).convertAndSend(eq("/topic/room.room-1"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            return "PLAYER_JOINED".equals(map.get("type"))
                && "ServerName".equals(map.get("displayName"))
                && "/server.png".equals(map.get("avatarPath"));
        }));
    }

    @Test
    void chatShouldUseServerSidePlayerMetadataInsteadOfClientPayload() {
        GameRoomService gameRoomService = mock(GameRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "u1"));
        when(userAccountRepository.findById("u1")).thenReturn(Optional.of(user("u1", "Alice", "/alice.jpg")));

        GameWebSocketController controller = new GameWebSocketController(gameRoomService, messagingTemplate, userAccountRepository);
        ChatMessage message = new ChatMessage();
        message.setRoomId("room-2");
        message.setUserId("u1");
        message.setDisplayName("Spoofed");
        message.setAvatarPath("/spoof.jpg");
        message.setContent(" hello ");

        controller.chat(message, headers);

        verify(messagingTemplate).convertAndSend(eq("/topic/room.room-2"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            return "CHAT".equals(map.get("type"))
                && "u1".equals(map.get("userId"))
                && "Alice".equals(map.get("displayName"))
                && "/alice.jpg".equals(map.get("avatarPath"))
                && "hello".equals(map.get("message"));
        }));
    }

    @Test
    void joinShouldAllowGuestSessionUser() {
        GameRoomService gameRoomService = mock(GameRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("GUEST_USER_ID", "guest-abcd1234"));
        when(gameRoomService.joinRoom("room-g", "guest-abcd1234"))
            .thenReturn(new GameRoomService.JoinResult(true, "X", null, "guest-abcd1234", 1));
        when(gameRoomService.getBoardSnapshot("room-g")).thenReturn(new String[10][10]);
        when(gameRoomService.availableRooms()).thenReturn(List.of("room-g"));
        when(userAccountRepository.findById("guest-abcd1234")).thenReturn(Optional.empty());

        GameWebSocketController controller = new GameWebSocketController(gameRoomService, messagingTemplate, userAccountRepository);
        JoinGameMessage message = new JoinGameMessage();
        message.setRoomId("room-g");
        message.setUserId("guest-abcd1234");

        controller.join(message, headers);

        verify(gameRoomService).joinRoom("room-g", "guest-abcd1234");
        verify(messagingTemplate).convertAndSend(eq("/topic/room.room-g"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            return "PLAYER_JOINED".equals(map.get("type"))
                && "guest-abcd1234".equals(map.get("userId"))
                && "Guest 1234".equals(map.get("displayName"))
                && "/uploads/avatars/default-avatar.jpg".equals(map.get("avatarPath"));
        }));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("guest-abcd1234"), eq("/queue/errors"), any());
    }

    private static UserAccount user(String id, String displayName, String avatarPath) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setDisplayName(displayName);
        user.setAvatarPath(avatarPath);
        return user;
    }
}
