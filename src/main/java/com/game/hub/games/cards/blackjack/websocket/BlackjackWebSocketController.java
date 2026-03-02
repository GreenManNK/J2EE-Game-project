package com.game.hub.games.cards.blackjack.websocket;

import com.game.hub.games.cards.blackjack.logic.BlackjackRoom;
import com.game.hub.games.cards.blackjack.service.BlackjackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
public class BlackjackWebSocketController {

    @Autowired
    private BlackjackService blackjackService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/blackjack.create")
    public void createRoom(Principal principal) {
        BlackjackRoom room = blackjackService.createRoom();
        room.addPlayer(principal.getName());
        messagingTemplate.convertAndSend("/topic/blackjack." + room.getId(), room);
    }

    @MessageMapping("/blackjack.join")
    public void joinRoom(Map<String, String> payload, Principal principal) {
        String roomId = payload.get("roomId");
        BlackjackRoom room = blackjackService.getRoom(roomId);
        if (room != null) {
            room.addPlayer(principal.getName());
            messagingTemplate.convertAndSend("/topic/blackjack." + roomId, room);
        }
    }
    
    @MessageMapping("/blackjack.spectate")
    public void spectate(Map<String, String> payload, Principal principal) {
        String roomId = payload.get("roomId");
        BlackjackRoom room = blackjackService.getRoom(roomId);
        if (room != null) {
            room.addSpectator(principal.getName());
            messagingTemplate.convertAndSend("/topic/blackjack." + roomId, room);
        }
    }

    @MessageMapping("/blackjack.bet")
    public void placeBet(Map<String, String> payload, Principal principal) {
        String roomId = payload.get("roomId");
        int betAmount = Integer.parseInt(payload.get("amount"));
        BlackjackRoom room = blackjackService.getRoom(roomId);
        if (room != null) {
            room.getPlayers().get(principal.getName()).placeBet(betAmount);
            messagingTemplate.convertAndSend("/topic/blackjack." + roomId, room);
        }
    }

    @MessageMapping("/blackjack.hit")
    public void hit(Map<String, String> payload, Principal principal) {
        String roomId = payload.get("roomId");
        BlackjackRoom room = blackjackService.getRoom(roomId);
        if (room != null) {
            room.playerHit(principal.getName());
            messagingTemplate.convertAndSend("/topic/blackjack." + roomId, room);
        }
    }

    @MessageMapping("/blackjack.stand")
    public void stand(Map<String, String> payload, Principal principal) {
        String roomId = payload.get("roomId");
        BlackjackRoom room = blackjackService.getRoom(roomId);
        if (room != null) {
            room.playerStand(principal.getName());
            messagingTemplate.convertAndSend("/topic/blackjack." + roomId, room);
        }
    }
}
