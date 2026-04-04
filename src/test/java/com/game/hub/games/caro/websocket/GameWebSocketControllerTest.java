package com.game.hub.games.caro.websocket;

import com.game.hub.service.AchievementService;
import com.game.hub.service.ChatModerationService;
import com.game.hub.service.CommunicationGuardService;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.games.caro.service.GameRoomService;
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
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "u1"));
        when(gameRoomService.joinRoom("room-1", "u1"))
            .thenReturn(new GameRoomService.JoinResult(true, "X", null, "u1", 1, 0));
        when(gameRoomService.getBoardSnapshot("room-1")).thenReturn(new String[10][10]);
        when(gameRoomService.availableRooms()).thenReturn(List.of());
        when(userAccountRepository.findById("u1")).thenReturn(Optional.of(user("u1", "ServerName", "/server.png")));

        GameWebSocketController controller = new GameWebSocketController(gameRoomService, messagingTemplate, userAccountRepository, achievementService, communicationGuardService(userAccountRepository));
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
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "u1"));
        when(userAccountRepository.findById("u1")).thenReturn(Optional.of(user("u1", "Alice", "/alice.jpg")));

        GameWebSocketController controller = new GameWebSocketController(gameRoomService, messagingTemplate, userAccountRepository, achievementService, communicationGuardService(userAccountRepository));
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
    void chatShouldMaskProfanityAndWarnCurrentUser() {
        GameRoomService gameRoomService = mock(GameRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "u1"));

        when(userAccountRepository.findById("u1")).thenReturn(Optional.of(user("u1", "Alice", "/alice.jpg")));

        GameWebSocketController controller = new GameWebSocketController(gameRoomService, messagingTemplate, userAccountRepository, achievementService, communicationGuardService(userAccountRepository));
        ChatMessage message = new ChatMessage();
        message.setRoomId("room-2");
        message.setUserId("u1");
        message.setContent("v.c.l");

        controller.chat(message, headers);

        verify(messagingTemplate).convertAndSendToUser(eq("u1"), eq("/queue/errors"), argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            return "Tin nhan chua ngon tu tho tuc va da bi chan. Noi dung da duoc an thanh ******. Canh cao 1/3.".equals(map.get("error"));
        }));
        verify(messagingTemplate).convertAndSend(eq("/topic/room.room-2"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            return "CHAT".equals(map.get("type"))
                && "u1".equals(map.get("userId"))
                && "******".equals(map.get("message"));
        }));
    }

    @Test
    void joinShouldAllowGuestSessionUser() {
        GameRoomService gameRoomService = mock(GameRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("GUEST_USER_ID", "guest-abcd1234"));
        when(gameRoomService.joinRoom("room-g", "guest-abcd1234"))
            .thenReturn(new GameRoomService.JoinResult(true, "X", null, "guest-abcd1234", 1, 0));
        when(gameRoomService.getBoardSnapshot("room-g")).thenReturn(new String[10][10]);
        when(gameRoomService.availableRooms()).thenReturn(List.of("room-g"));
        when(userAccountRepository.findById("guest-abcd1234")).thenReturn(Optional.empty());

        GameWebSocketController controller = new GameWebSocketController(gameRoomService, messagingTemplate, userAccountRepository, achievementService, communicationGuardService(userAccountRepository));
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

    @Test
    void moveShouldPublishGameOverWithWinLineAndResetDelay() {
        GameRoomService gameRoomService = mock(GameRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "u1"));
        when(gameRoomService.makeMove("room-win", "u1", 4, 4)).thenReturn(
            GameRoomService.MoveResult.win(
                "X", 4, 4, "u1", "u2", null, null,
                List.of(
                    new GameRoomService.BoardPoint(4, 0),
                    new GameRoomService.BoardPoint(4, 1),
                    new GameRoomService.BoardPoint(4, 2),
                    new GameRoomService.BoardPoint(4, 3),
                    new GameRoomService.BoardPoint(4, 4)
                )
            )
        );
        when(gameRoomService.getBoardSnapshot("room-win")).thenReturn(new String[10][10]);
        when(gameRoomService.getCurrentTurnUserId("room-win")).thenReturn("u1");

        GameWebSocketController controller = new GameWebSocketController(gameRoomService, messagingTemplate, userAccountRepository, achievementService, communicationGuardService(userAccountRepository));
        MoveMessage message = new MoveMessage();
        message.setRoomId("room-win");
        message.setUserId("u1");
        message.setX(4);
        message.setY(4);

        controller.move(message, headers);

        verify(messagingTemplate).convertAndSend(eq("/topic/room.room-win"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            if (!"MOVE".equals(map.get("type"))) return false;
            return Integer.valueOf(4).equals(map.get("x"))
                && Integer.valueOf(4).equals(map.get("y"))
                && "X".equals(map.get("symbol"));
        }));

        verify(messagingTemplate).convertAndSend(eq("/topic/room.room-win"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            if (!"GAME_OVER".equals(map.get("type"))) return false;
            if (!"u1".equals(map.get("winnerUserId"))) return false;
            Object resetDelayMs = map.get("resetDelayMs");
            if (!(resetDelayMs instanceof Number n) || n.longValue() <= 0L) return false;
            Object winLine = map.get("winLine");
            return winLine instanceof List<?> line && line.size() >= 5;
        }));
    }

    @Test
    void surrenderShouldPublishGameOverWithReasonAndResetDelay() {
        GameRoomService gameRoomService = mock(GameRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "u1"));
        when(gameRoomService.surrender("room-s", "u1"))
            .thenReturn(GameRoomService.SurrenderResult.ok("u2", "u1", null, null));

        GameWebSocketController controller = new GameWebSocketController(gameRoomService, messagingTemplate, userAccountRepository, achievementService, communicationGuardService(userAccountRepository));
        LeaveGameMessage message = new LeaveGameMessage();
        message.setRoomId("room-s");
        message.setUserId("u1");

        controller.surrender(message, headers);

        verify(messagingTemplate).convertAndSend(eq("/topic/room.room-s"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            if (!"GAME_OVER".equals(map.get("type"))) return false;
            if (!"SURRENDER".equals(map.get("reason"))) return false;
            if (!"u2".equals(map.get("winnerUserId"))) return false;
            if (!"u1".equals(map.get("loserUserId"))) return false;
            if (!"u1".equals(map.get("surrenderUserId"))) return false;
            Object resetDelayMs = map.get("resetDelayMs");
            return resetDelayMs instanceof Number n && n.longValue() > 0L;
        }));
    }

    @Test
    void skillShouldBroadcastRemovedOpponentPieceAndRemainingCharges() {
        GameRoomService gameRoomService = mock(GameRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "u1"));
        when(gameRoomService.useSkill("room-skill", "u1"))
            .thenReturn(GameRoomService.SkillResult.ok(5, 5, "u2", 0, "u2", Map.of("u1", 0, "u2", 0)));

        GameWebSocketController controller = new GameWebSocketController(gameRoomService, messagingTemplate, userAccountRepository, achievementService, communicationGuardService(userAccountRepository));
        LeaveGameMessage message = new LeaveGameMessage();
        message.setRoomId("room-skill");
        message.setUserId("u1");

        controller.useSkill(message, headers);

        verify(messagingTemplate).convertAndSend(eq("/topic/room.room-skill"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            return "SKILL_USED".equals(map.get("type"))
                && "u1".equals(map.get("userId"))
                && "u2".equals(map.get("affectedUserId"))
                && Integer.valueOf(5).equals(map.get("removedX"))
                && Integer.valueOf(5).equals(map.get("removedY"))
                && Integer.valueOf(0).equals(map.get("remainingCharges"));
        }));
    }

    @Test
    void skillShouldSendErrorWhenNormalModeDisablesSkills() {
        GameRoomService gameRoomService = mock(GameRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "u1"));
        when(gameRoomService.useSkill("Normal_ROOM", "u1"))
            .thenReturn(GameRoomService.SkillResult.error("Skill chi duoc dung trong che do nang cao"));

        GameWebSocketController controller = new GameWebSocketController(gameRoomService, messagingTemplate, userAccountRepository, achievementService, communicationGuardService(userAccountRepository));
        LeaveGameMessage message = new LeaveGameMessage();
        message.setRoomId("Normal_ROOM");
        message.setUserId("u1");

        controller.useSkill(message, headers);

        verify(messagingTemplate).convertAndSendToUser("u1", "/queue/errors", Map.of("error", "Skill chi duoc dung trong che do nang cao"));
        verify(messagingTemplate).convertAndSend("/topic/room.Normal_ROOM", Map.of(
            "type", "ERROR",
            "userId", "u1",
            "error", "Skill chi duoc dung trong che do nang cao"
        ));
    }

    @Test
    void advancedMoveShouldBroadcastSkillAwardWithoutGameOver() {
        GameRoomService gameRoomService = mock(GameRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "u1"));
        when(gameRoomService.makeMove("Advanced_ROOM", "u1", 4, 4)).thenReturn(
            GameRoomService.MoveResult.skillAwarded(
                "X",
                4,
                4,
                "u2",
                "u1",
                "EXTRA_TURN",
                List.of(
                    new GameRoomService.BoardPoint(4, 0),
                    new GameRoomService.BoardPoint(4, 1),
                    new GameRoomService.BoardPoint(4, 2),
                    new GameRoomService.BoardPoint(4, 3),
                    new GameRoomService.BoardPoint(4, 4)
                ),
                Map.of("u1", 1, "u2", 0),
                Map.of("u1", List.of("EXTRA_TURN"), "u2", List.of())
            )
        );
        when(gameRoomService.isAdvancedModeRoom("Advanced_ROOM")).thenReturn(true);

        GameWebSocketController controller = new GameWebSocketController(gameRoomService, messagingTemplate, userAccountRepository, achievementService, communicationGuardService(userAccountRepository));
        MoveMessage message = new MoveMessage();
        message.setRoomId("Advanced_ROOM");
        message.setUserId("u1");
        message.setX(4);
        message.setY(4);

        controller.move(message, headers);

        verify(messagingTemplate).convertAndSend(eq("/topic/room.Advanced_ROOM"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            return "MOVE".equals(map.get("type"))
                && Boolean.TRUE.equals(map.get("skillAwarded"))
                && "u1".equals(map.get("awardedUserId"))
                && "EXTRA_TURN".equals(map.get("awardedSkillType"))
                && Boolean.TRUE.equals(map.get("advancedMode"));
        }));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/room.Advanced_ROOM"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            return "GAME_OVER".equals(map.get("type"));
        }));
    }

    private static UserAccount user(String id, String displayName, String avatarPath) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setDisplayName(displayName);
        user.setAvatarPath(avatarPath);
        return user;
    }

    private CommunicationGuardService communicationGuardService(UserAccountRepository userAccountRepository) {
        return new CommunicationGuardService(userAccountRepository, new ChatModerationService());
    }
}
