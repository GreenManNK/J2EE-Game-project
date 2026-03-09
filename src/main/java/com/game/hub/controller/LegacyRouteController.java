package com.game.hub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
        StringBuilder redirect = new StringBuilder("redirect:/online-hub?game=").append(selectedGame);
        if (roomId != null && !roomId.isBlank()) {
            redirect.append("&roomId=").append(roomId);
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
}
