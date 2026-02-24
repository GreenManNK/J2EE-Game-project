package com.caro.game.controller;

import com.caro.game.cards.tienlen.service.TienLenRoomService;
import com.caro.game.chess.service.ChessOnlineRoomService;
import com.caro.game.xiangqi.service.XiangqiOnlineRoomService;
import com.caro.game.service.GameCatalogService;
import com.caro.game.service.GameRoomService;
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
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService()
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
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService()
        );

        Map<String, Object> result = controller.rooms("caro");

        assertEquals("caro", result.get("game"));
        @SuppressWarnings("unchecked")
        List<OnlineHubController.RoomRow> rooms = (List<OnlineHubController.RoomRow>) result.get("rooms");
        assertEquals(1, rooms.size());
        assertEquals("Normal_123", rooms.getFirst().roomId());
        assertEquals(2, rooms.getFirst().playerLimit());
    }

    @Test
    void shouldThrow404ForUnknownGame() {
        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            mock(GameRoomService.class),
            new TienLenRoomService(),
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService()
        );

        assertThrows(ResponseStatusException.class, () -> controller.index("unknown", null, new ConcurrentModel()));
    }

    @Test
    void chessShouldBeMarkedAsOnlineImplementedAndUseChessPlayUrl() {
        OnlineHubController controller = new OnlineHubController(
            new GameCatalogService(),
            mock(GameRoomService.class),
            new TienLenRoomService(),
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService()
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
            new ChessOnlineRoomService(),
            new XiangqiOnlineRoomService()
        );
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.index("xiangqi", "XQ-1", model);

        assertEquals("online-hub/index", view);
        assertEquals(true, model.getAttribute("onlineSupportedNow"));
        assertEquals("/xiangqi/online", model.getAttribute("playUrlBase"));
    }
}
