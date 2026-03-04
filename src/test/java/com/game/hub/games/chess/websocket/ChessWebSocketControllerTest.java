package com.game.hub.games.chess.websocket;

import com.game.hub.service.AchievementService;
import com.game.hub.games.chess.service.ChessOnlineRoomService;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChessWebSocketControllerTest {

    @Test
    void moveShouldBroadcastRoomStateWhenMatchEndsByMove() {
        ChessOnlineRoomService roomService = mock(ChessOnlineRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "blackUser"));

        ChessOnlineRoomService.RoomSnapshot room = new ChessOnlineRoomService.RoomSnapshot(
            "CHESS-FINAL",
            2,
            2,
            0,
            "GAME_OVER",
            "Chieu het. Den thang.",
            null,
            "b",
            new String[8][8],
            List.of("Trang Tot di f2 -> f3", "Den Hau di d8 -> h4"),
            null,
            List.of(
                new ChessOnlineRoomService.PlayerSnapshot("whiteUser", "White", "", "w"),
                new ChessOnlineRoomService.PlayerSnapshot("blackUser", "Black", "", "b")
            )
        );
        when(roomService.move("CHESS-FINAL", "blackUser", 0, 3, 4, 7, null))
            .thenReturn(ChessOnlineRoomService.ActionResult.ok("MOVE", room));

        ChessWebSocketController controller = new ChessWebSocketController(roomService, messagingTemplate, userAccountRepository, achievementService);
        ChessMoveMessage message = new ChessMoveMessage();
        message.setRoomId("CHESS-FINAL");
        message.setUserId("blackUser");
        message.setFromRow(0);
        message.setFromCol(3);
        message.setToRow(4);
        message.setToCol(7);

        controller.move(message, headers);

        verify(messagingTemplate).convertAndSend(eq("/topic/chess.room.CHESS-FINAL"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            if (!"MOVE".equals(map.get("type"))) return false;
            if (!"Chieu het. Den thang.".equals(map.get("message"))) return false;
            Object roomObj = map.get("room");
            if (!(roomObj instanceof ChessOnlineRoomService.RoomSnapshot snapshot)) return false;
            return "GAME_OVER".equals(snapshot.status()) && snapshot.currentTurnUserId() == null;
        }));
    }

    @Test
    void spectateShouldBroadcastRoomStateWithViewerMessage() {
        ChessOnlineRoomService roomService = mock(ChessOnlineRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "viewerUser"));
        when(headers.getSessionId()).thenReturn("sess-chess-watch");

        ChessOnlineRoomService.RoomSnapshot room = new ChessOnlineRoomService.RoomSnapshot(
            "CHESS-WATCH",
            2,
            2,
            1,
            "PLAYING",
            "Dang choi",
            "whiteUser",
            "w",
            new String[8][8],
            List.of(),
            null,
            List.of(
                new ChessOnlineRoomService.PlayerSnapshot("whiteUser", "White", "", "w"),
                new ChessOnlineRoomService.PlayerSnapshot("blackUser", "Black", "", "b")
            )
        );
        when(roomService.joinAsSpectator("CHESS-WATCH", "viewerUser"))
            .thenReturn(new ChessOnlineRoomService.JoinResult(true, "spectator", room, null));

        ChessWebSocketController controller = new ChessWebSocketController(roomService, messagingTemplate, userAccountRepository, achievementService);
        ChessJoinMessage join = new ChessJoinMessage();
        join.setRoomId("CHESS-WATCH");
        join.setUserId("viewerUser");

        controller.spectate(join, headers);

        verify(messagingTemplate).convertAndSend(eq("/topic/chess.room.CHESS-WATCH"), org.mockito.ArgumentMatchers.<Object>argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) return false;
            return "ROOM_STATE".equals(map.get("type"))
                && "Da vao xem".equals(map.get("message"))
                && room.equals(map.get("room"));
        }));
    }
}
