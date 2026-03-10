package com.game.hub.games.xiangqi.websocket;

import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.AchievementService;
import com.game.hub.games.xiangqi.service.XiangqiOnlineRoomService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XiangqiWebSocketControllerTest {

    @Test
    void moveShouldBroadcastRoomStateWhenMatchEndsByMove() {
        XiangqiOnlineRoomService roomService = mock(XiangqiOnlineRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "blackUser"));

        XiangqiOnlineRoomService.RoomSnapshot room = new XiangqiOnlineRoomService.RoomSnapshot(
            "XQ-FINAL",
            2,
            2,
            0,
            "GAME_OVER",
            "Den da an Tuong va chien thang!",
            null,
            "b",
            new String[10][9],
            List.of("Den Tuong an 5-10 -> 5-1"),
            null,
            List.of(
                new XiangqiOnlineRoomService.PlayerSnapshot("redUser", "Red", "", "r"),
                new XiangqiOnlineRoomService.PlayerSnapshot("blackUser", "Black", "", "b")
            )
        );
        when(roomService.move("XQ-FINAL", "blackUser", 0, 4, 9, 4, null))
            .thenReturn(XiangqiOnlineRoomService.ActionResult.ok("MOVE", room));

        XiangqiWebSocketController controller = new XiangqiWebSocketController(roomService, messagingTemplate, userAccountRepository, achievementService);
        XiangqiMoveMessage message = new XiangqiMoveMessage();
        message.setRoomId("XQ-FINAL");
        message.setUserId("blackUser");
        message.setFromRow(0);
        message.setFromCol(4);
        message.setToRow(9);
        message.setToCol(4);

        controller.move(message, headers);

        verify(messagingTemplate).convertAndSend(eq("/topic/xiangqi.room.XQ-FINAL"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            if (!"MOVE".equals(map.get("type"))) return false;
            if (!"Den da an Tuong va chien thang!".equals(map.get("message"))) return false;
            Object roomObj = map.get("room");
            if (!(roomObj instanceof XiangqiOnlineRoomService.RoomSnapshot snapshot)) return false;
            return "GAME_OVER".equals(snapshot.status()) && snapshot.currentTurnUserId() == null;
        }));
        verify(achievementService).checkAndAward("blackUser", "Xiangqi", true);
    }

    @Test
    void spectateShouldBroadcastRoomStateWithViewerMessage() {
        XiangqiOnlineRoomService roomService = mock(XiangqiOnlineRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "viewerUser"));
        when(headers.getSessionId()).thenReturn("sess-1");

        XiangqiOnlineRoomService.RoomSnapshot room = new XiangqiOnlineRoomService.RoomSnapshot(
            "XQ-WATCH",
            2,
            2,
            1,
            "PLAYING",
            "Dang choi",
            "redUser",
            "r",
            new String[10][9],
            List.of(),
            null,
            List.of(
                new XiangqiOnlineRoomService.PlayerSnapshot("redUser", "Red", "", "r"),
                new XiangqiOnlineRoomService.PlayerSnapshot("blackUser", "Black", "", "b")
            )
        );
        when(roomService.joinAsSpectator("XQ-WATCH", "viewerUser"))
            .thenReturn(new XiangqiOnlineRoomService.JoinResult(true, "spectator", room, null));

        XiangqiWebSocketController controller = new XiangqiWebSocketController(roomService, messagingTemplate, userAccountRepository, achievementService);
        XiangqiJoinMessage join = new XiangqiJoinMessage();
        join.setRoomId("XQ-WATCH");
        join.setUserId("viewerUser");

        controller.spectate(join, headers);

        verify(messagingTemplate).convertAndSend(eq("/topic/xiangqi.room.XQ-WATCH"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            return "ROOM_STATE".equals(map.get("type"))
                && "Da vao xem".equals(map.get("message"))
                && room.equals(map.get("room"));
        }));
    }
}
