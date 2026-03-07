package com.game.hub.games.caro.controller;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.UUID;

@Controller("caroGameController")
@RequestMapping("/game")
public class GameController {
    private static final String AUTH_USER_ID = "AUTH_USER_ID";
    private static final String GUEST_USER_ID = "GUEST_USER_ID";
    private static final String DEFAULT_AVATAR_PATH = "/uploads/avatars/default-avatar.jpg";

    private final UserAccountRepository userAccountRepository;

    public GameController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public String index(@RequestParam(required = false) String roomId,
                        @RequestParam(required = false) String symbol,
                        HttpServletRequest request,
                        Model model) {
        model.addAttribute("roomId", roomId == null ? "" : roomId);
        model.addAttribute("symbol", symbol == null ? "" : symbol);
        addSessionPlayer(model, request);
        return "game/index";
    }

    @GetMapping("/room/{roomId}")
    public String roomPage(@PathVariable String roomId,
                           @RequestParam(required = false) String symbol,
                           HttpServletRequest request,
                           Model model) {
        return index(roomId, symbol, request, model);
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> indexApi(@RequestParam(required = false) String roomId,
                                        @RequestParam(required = false) String symbol,
                                        HttpServletRequest request) {
        SessionPlayer player = resolveSessionPlayer(request);
        return Map.of(
            "roomId", roomId == null ? "" : roomId,
            "symbol", symbol == null ? "" : symbol,
            "sessionUserId", player.userId(),
            "sessionDisplayName", player.displayName(),
            "sessionAvatarPath", player.avatarPath()
        );
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

    private void addSessionPlayer(Model model, HttpServletRequest request) {
        SessionPlayer player = resolveSessionPlayer(request);
        model.addAttribute("sessionUserId", player.userId());
        model.addAttribute("sessionDisplayName", player.displayName());
        model.addAttribute("sessionAvatarPath", player.avatarPath());
    }

    private SessionPlayer resolveSessionPlayer(HttpServletRequest request) {
        UserAccount user = sessionUser(request);
        if (user != null) {
            return new SessionPlayer(user.getId(), displayNameOf(user), avatarPathOf(user));
        }
        String guestUserId = ensureGuestUserId(request);
        return new SessionPlayer(guestUserId, guestDisplayName(guestUserId), DEFAULT_AVATAR_PATH);
    }

    private String ensureGuestUserId(HttpServletRequest request) {
        if (request == null) {
            return "guest-" + UUID.randomUUID().toString().replace("-", "");
        }
        HttpSession session = request.getSession(true);
        Object existing = session.getAttribute(GUEST_USER_ID);
        if (existing != null) {
            String userId = String.valueOf(existing).trim();
            if (!userId.isEmpty()) {
                return userId;
            }
        }
        String guestUserId = "guest-" + UUID.randomUUID().toString().replace("-", "");
        session.setAttribute(GUEST_USER_ID, guestUserId);
        return guestUserId;
    }

    private String guestDisplayName(String guestUserId) {
        if (guestUserId == null || guestUserId.isBlank()) {
            return "Guest";
        }
        String normalized = guestUserId.trim();
        String suffix = normalized.length() <= 4
            ? normalized
            : normalized.substring(normalized.length() - 4);
        return "Guest " + suffix.toUpperCase();
    }

    private UserAccount sessionUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object authUserId = session.getAttribute(AUTH_USER_ID);
        if (authUserId == null) {
            return null;
        }
        String userId = String.valueOf(authUserId).trim();
        if (userId.isEmpty()) {
            return null;
        }
        return userAccountRepository.findById(userId).orElse(null);
    }

    private String displayNameOf(UserAccount user) {
        String displayName = user.getDisplayName();
        return (displayName == null || displayName.isBlank()) ? user.getId() : displayName;
    }

    private String avatarPathOf(UserAccount user) {
        return user.getAvatarPath() == null ? "" : user.getAvatarPath();
    }

    private record SessionPlayer(String userId, String displayName, String avatarPath) {
    }
}
