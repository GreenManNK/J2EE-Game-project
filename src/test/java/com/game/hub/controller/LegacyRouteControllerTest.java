package com.game.hub.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyRouteControllerTest {

    private final LegacyRouteController controller = new LegacyRouteController();

    @Test
    void homeMultiplayerShouldRedirectToCanonicalCaroModePage() {
        assertEquals("redirect:/games/caro", controller.homeMultiplayer());
    }

    @Test
    void homeSinglePlayerShouldRedirectToCanonicalCaroBotPicker() {
        assertEquals("redirect:/game-mode/bot?game=caro", controller.homeSinglePlayer());
    }

    @Test
    void lobbyShouldRedirectToDedicatedCaroRoomPage() {
        assertEquals("redirect:/games/caro/rooms", controller.lobby());
    }

    @Test
    void gameIndexShouldRedirectRoomRequestsToDedicatedRoomPage() {
        assertEquals("redirect:/game/room/ROOM-123?symbol=X", controller.gameIndex("ROOM-123", "X"));
    }

    @Test
    void chessOnlineShouldRedirectSpectatorsToDedicatedSpectatePage() {
        assertEquals("redirect:/chess/online/room/CHESS-9/spectate", controller.chessOnline("CHESS-9", true));
    }

    @Test
    void xiangqiOnlineShouldRedirectPlayersToDedicatedRoomPage() {
        assertEquals("redirect:/xiangqi/online/room/XQ-9", controller.xiangqiOnline("XQ-9", false));
    }

    @Test
    void cardsTienLenShouldRedirectRoomRequestsToDedicatedRoomPage() {
        assertEquals("redirect:/cards/tien-len/room/TL-ROOM", controller.cardsTienLen("TL-ROOM"));
    }

    @Test
    void onlineHubAliasShouldRedirectToDedicatedGameRoomPage() {
        assertEquals("redirect:/games/chess/rooms?roomId=CHESS-9", controller.onlineHub("chess", "CHESS-9"));
    }
}
