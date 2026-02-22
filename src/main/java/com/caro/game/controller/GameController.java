package com.caro.game.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/game")
public class GameController {

    @GetMapping
    public String index(@RequestParam(required = false) String roomId,
                        @RequestParam(required = false) String symbol,
                        Model model) {
        model.addAttribute("roomId", roomId == null ? "" : roomId);
        model.addAttribute("symbol", symbol == null ? "" : symbol);
        return "game/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> indexApi(@RequestParam(required = false) String roomId,
                                        @RequestParam(required = false) String symbol) {
        return Map.of("roomId", roomId == null ? "" : roomId, "symbol", symbol == null ? "" : symbol);
    }

    @GetMapping("/offline")
    public String offline() {
        return "game/offline";
    }

    @GetMapping("/waiting")
    public String waiting(@RequestParam String requestId, Model model) {
        model.addAttribute("requestId", requestId);
        return "game/waiting";
    }
}
