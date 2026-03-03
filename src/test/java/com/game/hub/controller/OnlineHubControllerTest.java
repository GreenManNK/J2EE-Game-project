package com.game.hub.controller;

import com.game.hub.games.cards.blackjack.logic.BlackjackRoom;
import com.game.hub.games.cards.blackjack.service.BlackjackService;
import com.game.hub.games.cards.tienlen.service.TienLenRoomService;
import com.game.hub.games.chess.service.ChessOnlineRoomService;
import com.game.hub.games.quiz.logic.QuizRoom;
import com.game.hub.games.quiz.service.QuizService;
import com.game.hub.games.typing.logic.TypingRoom;
import com.game.hub.games.typing.service.TypingService;
import com.game.hub.games.xiangqi.service.XiangqiOnlineRoomService;
import com.game.hub.service.GameCatalogService;
import com.game.hub.games.caro.service.GameRoomService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnlineHubControllerTest {

    @Test
    void indexShouldRenderOnlineHubForCaro() {
        GameRoomService gameRoomService = mock(GameRoomService.class);
        when(gameRoomService.availableRooms()).thenReturn(List.of("room-a"));

        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            gameRoomService,
            new TienLenRoomService(),
            mock(BlackjackService.class),
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService(),
            mock(TypingService.class),
            mock(QuizService.class)
        );
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.index("caro", "room-a", model);

        assertEquals("online-hub/index", view);
        assertEquals("caro", model.getAttribute("selectedGameCode"));
        assertEquals("room-a", model.getAttribute("selectedRoomId"));
        assertEquals(true, model.getAttribute("onlineSupportedNow"));
        assertNotNull(model.getAttribute("roomRows"));
    }

    @Test
    void roomsApiShouldNormalizeCaroRooms() {
        GameRoomService gameRoomService = mock(GameRoomService.class);
        when(gameRoomService.availableRooms()).thenReturn(List.of("Normal_123"));

        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            gameRoomService,
            new TienLenRoomService(),
            mock(BlackjackService.class),
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService(),
            mock(TypingService.class),
            mock(QuizService.class)
        );

        Map<String, Object> result = controller.rooms("caro");

        assertEquals("caro", result.get("game"));
        @SuppressWarnings("unchecked")
        List<OnlineHubController.RoomRow> rooms = (List<OnlineHubController.RoomRow>) result.get("rooms");
        assertEquals(1, rooms.size());
        assertEquals("Normal_123", rooms.get(0).roomId());
        assertEquals(2, rooms.get(0).playerLimit());
    }

    @Test
    void shouldThrow404ForUnknownGame() {
        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            mock(GameRoomService.class),
            new TienLenRoomService(),
            mock(BlackjackService.class),
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService(),
            mock(TypingService.class),
            mock(QuizService.class)
        );

        assertThrows(ResponseStatusException.class, () -> controller.index("unknown", null, new ConcurrentModel()));
    }

    @Test
    void chessShouldBeMarkedAsOnlineImplementedAndUseChessPlayUrl() {
        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            mock(GameRoomService.class),
            new TienLenRoomService(),
            mock(BlackjackService.class),
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService(),
            mock(TypingService.class),
            mock(QuizService.class)
        );
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.index("chess", "CHESS-1", model);

        assertEquals("online-hub/index", view);
        assertEquals(true, model.getAttribute("onlineSupportedNow"));
        assertEquals("/chess/online", model.getAttribute("playUrlBase"));
    }

    @Test
    void xiangqiShouldBeMarkedAsOnlineImplementedAndUseXiangqiPlayUrl() {
        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            mock(GameRoomService.class),
            new TienLenRoomService(),
            mock(BlackjackService.class),
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService(),
            mock(TypingService.class),
            mock(QuizService.class)
        );
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.index("xiangqi", "XQ-1", model);

        assertEquals("online-hub/index", view);
        assertEquals(true, model.getAttribute("onlineSupportedNow"));
        assertEquals("/xiangqi/online", model.getAttribute("playUrlBase"));
    }

    @Test
    void typingShouldUseRoomQueryParamAndTypingPlayUrl() {
        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            mock(GameRoomService.class),
            new TienLenRoomService(),
            mock(BlackjackService.class),
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService(),
            mock(TypingService.class),
            mock(QuizService.class)
        );
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.index("typing", "ROOM-1", model);

        assertEquals("online-hub/index", view);
        assertEquals(true, model.getAttribute("onlineSupportedNow"));
        assertEquals("/games/typing", model.getAttribute("playUrlBase"));
        assertEquals("room", model.getAttribute("playRoomParam"));
        assertEquals("/games/typing?room={roomId}", model.getAttribute("inviteUrlPathTemplate"));
    }

    @Test
    void quizShouldExposeSpectateModeParameter() {
        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            mock(GameRoomService.class),
            new TienLenRoomService(),
            mock(BlackjackService.class),
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService(),
            mock(TypingService.class),
            mock(QuizService.class)
        );
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.index("quiz", "QUIZ-1", model);

        assertEquals("online-hub/index", view);
        assertEquals(true, model.getAttribute("onlineSupportedNow"));
        assertEquals(true, model.getAttribute("supportsSpectateNow"));
        assertEquals("/games/quiz", model.getAttribute("playUrlBase"));
        assertEquals("room", model.getAttribute("playRoomParam"));
        assertEquals("mode", model.getAttribute("spectateParamName"));
        assertEquals("spectate", model.getAttribute("spectateParamValue"));
    }

    @Test
    void createRoomApiShouldCreateTypingRoomFromService() {
        BlackjackService blackjackService = mock(BlackjackService.class);
        TypingService typingService = mock(TypingService.class);
        QuizService quizService = mock(QuizService.class);
        TypingRoom room = mock(TypingRoom.class);
        when(room.getId()).thenReturn("TYPING-ROOM-001");
        when(typingService.createRoom()).thenReturn(room);

        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            mock(GameRoomService.class),
            new TienLenRoomService(),
            blackjackService,
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService(),
            typingService,
            quizService
        );

        Map<String, Object> result = controller.createRoom("typing");

        assertEquals("typing", result.get("game"));
        assertEquals("TYPING-ROOM-001", result.get("roomId"));
        assertEquals(true, result.get("serverCreated"));
        assertEquals("room", result.get("playRoomParam"));
    }

    @Test
    void createRoomApiShouldCreateQuizRoomFromService() {
        BlackjackService blackjackService = mock(BlackjackService.class);
        TypingService typingService = mock(TypingService.class);
        QuizService quizService = mock(QuizService.class);
        QuizRoom room = mock(QuizRoom.class);
        when(room.getRoomId()).thenReturn("QUIZ-ROOM-001");
        when(quizService.createRoom()).thenReturn(room);

        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            mock(GameRoomService.class),
            new TienLenRoomService(),
            blackjackService,
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService(),
            typingService,
            quizService
        );

        Map<String, Object> result = controller.createRoom("quiz");

        assertEquals("quiz", result.get("game"));
        assertEquals("QUIZ-ROOM-001", result.get("roomId"));
        assertEquals(true, result.get("serverCreated"));
        assertEquals("room", result.get("playRoomParam"));
        assertEquals("/games/quiz?room={roomId}", result.get("inviteUrlPathTemplate"));
    }

    @Test
    void indexShouldFallbackToCaroWhenGameMissing() {
        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            mock(GameRoomService.class),
            new TienLenRoomService(),
            mock(BlackjackService.class),
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService(),
            mock(TypingService.class),
            mock(QuizService.class)
        );
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.index(null, null, model);

        assertEquals("online-hub/index", view);
        assertEquals("caro", model.getAttribute("selectedGameCode"));
    }

    @Test
    void blackjackShouldExposeRoomParamAndSpectateMode() {
        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            mock(GameRoomService.class),
            new TienLenRoomService(),
            mock(BlackjackService.class),
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService(),
            mock(TypingService.class),
            mock(QuizService.class)
        );
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.index("blackjack", "BJ-ROOM-1", model);

        assertEquals("online-hub/index", view);
        assertEquals(true, model.getAttribute("onlineSupportedNow"));
        assertEquals(true, model.getAttribute("supportsSpectateNow"));
        assertEquals("/games/cards/blackjack", model.getAttribute("playUrlBase"));
        assertEquals("room", model.getAttribute("playRoomParam"));
        assertEquals("mode", model.getAttribute("spectateParamName"));
        assertEquals("spectate", model.getAttribute("spectateParamValue"));
    }

    @Test
    void createRoomApiShouldCreateBlackjackRoomFromService() {
        BlackjackService blackjackService = mock(BlackjackService.class);
        TypingService typingService = mock(TypingService.class);
        QuizService quizService = mock(QuizService.class);
        BlackjackRoom room = mock(BlackjackRoom.class);
        when(room.getId()).thenReturn("BJ-ROOM-001");
        when(blackjackService.createRoom()).thenReturn(room);

        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            mock(GameRoomService.class),
            new TienLenRoomService(),
            blackjackService,
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService(),
            typingService,
            quizService
        );

        Map<String, Object> result = controller.createRoom("blackjack");

        assertEquals("blackjack", result.get("game"));
        assertEquals("BJ-ROOM-001", result.get("roomId"));
        assertEquals(true, result.get("serverCreated"));
        assertEquals("room", result.get("playRoomParam"));
        assertEquals("/games/cards/blackjack?room={roomId}", result.get("inviteUrlPathTemplate"));
    }
}
