package com.caro.game.controller;

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
        return "redirect:/multiplayer";
    }

    @GetMapping("/Home/Singleplayer")
    public String homeSinglePlayer() {
        return "redirect:/single-player";
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
        StringBuilder redirect = new StringBuilder("redirect:/game");
        if (roomId != null && !roomId.isBlank()) {
            redirect.append("?roomId=").append(roomId);
            if (symbol != null && !symbol.isBlank()) {
                redirect.append("&symbol=").append(symbol);
            }
        } else if (symbol != null && !symbol.isBlank()) {
            redirect.append("?symbol=").append(symbol);
        }
        return redirect.toString();
    }

    @GetMapping("/Game/Offline")
    public String gameOffline() {
        return "redirect:/game/offline";
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
