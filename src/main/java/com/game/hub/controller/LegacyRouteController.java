package com.game.hub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Controller
public class LegacyRouteController {

    @GetMapping({"/Home", "/Home/Index"})
    public String homeIndex() {
        return "redirect:/";
    }

    @GetMapping("/Home/Multiplayer")
    public String homeMultiplayer() {
        return "redirect:/games/caro";
    }

    @GetMapping("/Home/Singleplayer")
    public String homeSinglePlayer() {
        return "redirect:/game-mode/bot?game=caro";
    }

    @GetMapping("/Home/Games")
    public String homeGames() {
        return "redirect:/games";
    }

    @GetMapping("/Lobby")
    public String lobby() {
        return "redirect:/lobby";
    }

    @GetMapping("/Friendship/FriendList")
    public String friendListAlias(@RequestParam(required = false) String currentUserId) {
        if (currentUserId == null || currentUserId.isBlank()) {
            return "redirect:/friendship/friend-list";
        }
        return "redirect:/friendship/friend-list?currentUserId=" + currentUserId;
    }

    @GetMapping("/Game/Index")
    public String gameIndex(@RequestParam(required = false) String roomId,
                            @RequestParam(required = false) String symbol) {
        if (roomId != null && !roomId.isBlank()) {
            StringBuilder redirect = new StringBuilder("redirect:/game/room/").append(roomId);
            if (symbol != null && !symbol.isBlank()) {
                redirect.append("?symbol=").append(symbol);
            }
            return redirect.toString();
        } else if (symbol != null && !symbol.isBlank()) {
            return "redirect:/game?symbol=" + symbol;
        }
        return "redirect:/game";
    }

    @GetMapping("/Game/Offline")
    public String gameOffline() {
        return "redirect:/game/offline";
    }

    @GetMapping("/Chess/Offline")
    public String chessOffline() {
        return "redirect:/chess/offline";
    }

    @GetMapping("/Chess/Bot")
    public String chessBot() {
        return "redirect:/chess/bot";
    }

    @GetMapping("/Chess/Online")
    public String chessOnline(@RequestParam(required = false) String roomId,
                              @RequestParam(required = false) Boolean spectate) {
        if (roomId == null || roomId.isBlank()) {
            return "redirect:/chess/online";
        }
        if (Boolean.TRUE.equals(spectate)) {
            return "redirect:/chess/online/room/" + roomId + "/spectate";
        }
        return "redirect:/chess/online/room/" + roomId;
    }

    @GetMapping("/Xiangqi/Offline")
    public String xiangqiOffline() {
        return "redirect:/xiangqi/offline";
    }

    @GetMapping("/Xiangqi/Bot")
    public String xiangqiBot() {
        return "redirect:/xiangqi/bot";
    }

    @GetMapping("/Xiangqi/Online")
    public String xiangqiOnline(@RequestParam(required = false) String roomId,
                                @RequestParam(required = false) Boolean spectate) {
        if (roomId == null || roomId.isBlank()) {
            return "redirect:/xiangqi/online";
        }
        if (Boolean.TRUE.equals(spectate)) {
            return "redirect:/xiangqi/online/room/" + roomId + "/spectate";
        }
        return "redirect:/xiangqi/online/room/" + roomId;
    }

    @GetMapping("/Cards/TienLen")
    public String cardsTienLen(@RequestParam(required = false) String roomId) {
        if (roomId != null && !roomId.isBlank()) {
            return "redirect:/cards/tien-len/room/" + roomId;
        }
        return "redirect:/cards/tien-len";
    }

    @GetMapping("/Cards/TienLenBot")
    public String cardsTienLenBot() {
        return "redirect:/cards/tien-len/bot";
    }

    @GetMapping("/Online/Hub")
    public String onlineHub(@RequestParam(required = false) String game,
                            @RequestParam(required = false) String roomId) {
        String selectedGame = (game == null || game.isBlank()) ? "caro" : game;
        String basePath = onlineBasePath(selectedGame);
        StringBuilder redirect = new StringBuilder("redirect:").append(basePath);
        if (roomId != null && !roomId.isBlank()) {
            redirect.append("?")
                .append(onlineRoomParam(selectedGame))
                .append("=")
                .append(UriUtils.encodeQueryParam(roomId.trim(), StandardCharsets.UTF_8));
        }
        return redirect.toString();
    }

    @GetMapping("/Game/Waiting")
    public String gameWaiting(@RequestParam String requestId) {
        return "redirect:/game/waiting?requestId=" + requestId;
    }

    @GetMapping("/Account/Login")
    public String accountLogin() {
        return "redirect:/account/login-page";
    }

    @GetMapping("/Account/Register")
    public String accountRegister() {
        return "redirect:/account/register-page";
    }

    private String onlineBasePath(String gameCode) {
        String normalized = gameCode == null ? "" : gameCode.trim().toLowerCase();
        return switch (normalized) {
            case "caro" -> "/game";
            case "cards" -> "/cards/tien-len";
            case "blackjack" -> "/games/cards/blackjack";
            case "chess" -> "/chess/online";
            case "xiangqi" -> "/xiangqi/online";
            case "typing" -> "/games/typing";
            case "quiz" -> "/games/quiz";
            default -> "/games/" + normalized;
        };
    }

    private String onlineRoomParam(String gameCode) {
        String normalized = gameCode == null ? "" : gameCode.trim().toLowerCase();
        if ("typing".equals(normalized) || "quiz".equals(normalized) || "blackjack".equals(normalized)) {
            return "room";
        }
        return "roomId";
    }
}
