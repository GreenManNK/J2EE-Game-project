package com.game.hub.games.chess.controller;

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

import java.util.UUID;

@Controller
@RequestMapping("/chess")
public class ChessOnlineController {
    private static final String AUTH_USER_ID = "AUTH_USER_ID";
    private static final String GUEST_USER_ID = "GUEST_USER_ID";
    private static final String DEFAULT_AVATAR_PATH = "/uploads/avatars/default-avatar.jpg";

    private final UserAccountRepository userAccountRepository;

    public ChessOnlineController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping("/online")
    public String online(@RequestParam(required = false) String roomId,
                         HttpServletRequest request,
                         Model model) {
        SessionPlayer player = resolveSessionPlayer(request);
        model.addAttribute("sessionUserId", player.userId());
        model.addAttribute("sessionDisplayName", player.displayName());
        model.addAttribute("sessionAvatarPath", player.avatarPath());
        model.addAttribute("defaultRoomId", roomId == null ? "" : roomId.trim());
        return "chess/online";
    }

    @GetMapping("/online/room/{roomId}")
    public String onlineRoom(@PathVariable String roomId,
                             HttpServletRequest request,
                             Model model) {
        return online(roomId, request, model);
    }

    @GetMapping("/online/room/{roomId}/spectate")
    public String spectateRoom(@PathVariable String roomId,
                               HttpServletRequest request,
                               Model model) {
        return online(roomId, request, model);
    }

    private SessionPlayer resolveSessionPlayer(HttpServletRequest request) {
        UserAccount user = sessionUser(request);
        if (user != null) {
            String displayName = (user.getDisplayName() == null || user.getDisplayName().isBlank()) ? user.getId() : user.getDisplayName();
            String avatarPath = (user.getAvatarPath() == null || user.getAvatarPath().isBlank()) ? DEFAULT_AVATAR_PATH : user.getAvatarPath();
            return new SessionPlayer(user.getId(), displayName, avatarPath);
        }
        String guestUserId = ensureGuestUserId(request);
        return new SessionPlayer(guestUserId, guestDisplayName(guestUserId), DEFAULT_AVATAR_PATH);
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

    private String ensureGuestUserId(HttpServletRequest request) {
        if (request == null) {
            return "guest-" + UUID.randomUUID().toString().replace("-", "");
        }
        HttpSession session = request.getSession(true);
        Object existing = session.getAttribute(GUEST_USER_ID);
        if (existing != null) {
            String value = String.valueOf(existing).trim();
            if (!value.isEmpty()) {
                return value;
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
        String suffix = normalized.length() <= 4 ? normalized : normalized.substring(normalized.length() - 4);
        return "Guest " + suffix.toUpperCase();
    }

    private record SessionPlayer(String userId, String displayName, String avatarPath) {
    }
}
