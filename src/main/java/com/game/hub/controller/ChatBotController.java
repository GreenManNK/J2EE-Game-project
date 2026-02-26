package com.game.hub.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/chat-bot")
public class ChatBotController {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${huggingface.apiKey:}")
    private String apiKey;

    @Value("${huggingface.model:}")
    private String model;

    @GetMapping
    public String page() {
        return "chat-bot/index";
    }

    @ResponseBody
    @PostMapping("/send")
    public Map<String, Object> sendMessage(@RequestBody ChatRequest request) {
        String message = request.message() == null ? "" : request.message().trim();
        String normalized = message.toLowerCase();

        if (normalized.contains("dau rank") || normalized.contains("dau xep hang")) {
            return Map.of("success", true, "reply",
                "Huong dan dau xep hang: vao Home -> Multiplayer -> Online -> Tim tran.");
        }

        if (normalized.contains("bang xep hang")) {
            return Map.of("success", true, "reply", "Mo muc Bang xep hang de xem top nguoi choi theo diem.");
        }

        if (normalized.contains("doi mat khau")) {
            return Map.of("success", true, "reply", "Vao trang Profile/Account va chon doi mat khau.");
        }

        if (apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) {
            return Map.of("success", true, "reply", "Chatbot AI chua duoc cau hinh API key/model.");
        }

        try {
            String url = "https://api-inference.huggingface.co/models/" + model;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("inputs", message), headers);
            ResponseEntity<List> response = restTemplate.postForEntity(url, entity, List.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isEmpty()) {
                return Map.of("success", false, "message", "Loi goi HuggingFace API");
            }

            Object first = response.getBody().get(0);
            if (first instanceof Map<?, ?> map) {
                Object generated = map.get("generated_text");
                if (generated != null) {
                    return Map.of("success", true, "reply", generated.toString());
                }
            }

            return Map.of("success", false, "message", "Khong phan tich duoc ket qua AI");
        } catch (Exception ex) {
            return Map.of("success", false, "message", "Loi chatbot: " + ex.getMessage());
        }
    }

    public record ChatRequest(String message) {
    }
}