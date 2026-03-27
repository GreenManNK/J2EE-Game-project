package com.game.hub.controller;

import com.game.hub.service.BotMatchHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/history/api")
public class BotMatchHistoryController {
    private final BotMatchHistoryService botMatchHistoryService;

    public BotMatchHistoryController(BotMatchHistoryService botMatchHistoryService) {
        this.botMatchHistoryService = botMatchHistoryService;
    }

    @PostMapping("/bot-match")
    public Map<String, Object> recordBotMatch(@RequestBody(required = false) BotMatchHistoryService.BotMatchRecordRequest request,
                                              HttpServletRequest httpRequest) {
        BotMatchHistoryService.ServiceResult result = botMatchHistoryService.record(request, httpRequest);
        if (!result.success()) {
            return Map.of(
                "success", false,
                "error", result.error()
            );
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recorded", result.recorded());
        data.put("userId", result.playerUserId());
        data.put("matchCode", result.matchCode());
        return Map.of(
            "success", true,
            "data", data
        );
    }
}
