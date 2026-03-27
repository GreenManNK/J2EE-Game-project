package com.game.hub.controller;

import com.game.hub.games.caro.service.GameRoomService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
@RequestMapping("/lobby")
public class LobbyController {
    private final GameRoomService gameRoomService;

    public LobbyController(GameRoomService gameRoomService) {
        this.gameRoomService = gameRoomService;
    }

    @GetMapping
    public String index(@RequestParam(required = false) String roomId) {
        String redirect = "redirect:/games/caro/rooms";
        if (roomId == null || roomId.isBlank()) {
            return redirect;
        }
        return redirect + "?roomId=" + UriUtils.encodeQueryParam(roomId.trim(), StandardCharsets.UTF_8);
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> api() {
        return Map.of("availableRooms", gameRoomService.availableRooms());
    }
}
