package com.game.hub.controller;

import com.game.hub.caro.service.GameRoomService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/lobby")
public class LobbyController {
    private final GameRoomService gameRoomService;

    public LobbyController(GameRoomService gameRoomService) {
        this.gameRoomService = gameRoomService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("availableRooms", gameRoomService.availableRooms());
        return "lobby/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> api() {
        return Map.of("availableRooms", gameRoomService.availableRooms());
    }
}
