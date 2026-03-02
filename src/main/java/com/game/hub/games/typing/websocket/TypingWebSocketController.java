package com.game.hub.games.typing.websocket;

import com.game.hub.games.typing.logic.TypingRoom;
import com.game.hub.games.typing.service.TypingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class TypingWebSocketController {

    @Autowired
    private TypingService typingService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/typing.create")
    public void createRoom(SimpMessageHeaderAccessor headerAccessor) {
        String playerId = headerAccessor.getUser().getName();
        TypingRoom room = typingService.createRoom();
        room.addPlayer(playerId);
        messagingTemplate.convertAndSend("/topic/typing." + room.getId(), room);
    }

    @MessageMapping("/typing.join")
    public void joinRoom(Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String roomId = payload.get("roomId");
        String playerId = headerAccessor.getUser().getName();
        TypingRoom room = typingService.getRoom(roomId);
        if (room != null && room.addPlayer(playerId)) {
            messagingTemplate.convertAndSend("/topic/typing." + roomId, room);
        }
    }

    @MessageMapping("/typing.progress")
    public void updateProgress(Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String roomId = payload.get("roomId");
        String typedText = payload.get("typed");
        String playerId = headerAccessor.getUser().getName();
        TypingRoom room = typingService.getRoom(roomId);
        if (room != null) {
            room.updateProgress(playerId, typedText);
            messagingTemplate.convertAndSend("/topic/typing." + roomId, room);
        }
    }
}
