package com.game.hub.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/connectivity")
public class ConnectivityController {

    @GetMapping("/ping")
    public Map<String, Object> ping(HttpServletRequest request) {
        return Map.of(
            "ok", true,
            "serverTime", Instant.now().toString(),
            "clientIp", request.getRemoteAddr()
        );
    }
}
