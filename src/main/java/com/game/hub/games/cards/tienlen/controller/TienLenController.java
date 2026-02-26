package com.game.hub.games.cards.tienlen.controller;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
@RequestMapping("/cards")
public class TienLenController {
    private static final String AUTH_USER_ID = "AUTH_USER_ID";
    private static final String GUEST_USER_ID = "GUEST_USER_ID";
    private static final String DEFAULT_AVATAR_PATH = "/uploads/avatars/default-avatar.jpg";

    private final UserAccountRepository userAccountRepository;

    public TienLenController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping("/tien-len")
    public String tienLen(@RequestParam(required = false) String roomId,
                          HttpServletRequest request,
                          Model model) {
        SessionPlayer player = resolveSessionPlayer(request);
        model.addAttribute("sessionUserId", player.userId());
        model.addAttribute("sessionDisplayName", player.displayName());
        model.addAttribute("sessionAvatarPath", player.avatarPath());
        model.addAttribute("defaultRoomId", roomId == null ? "" : roomId.trim());
        return "cards/tien-len";
    }

    @GetMapping("/tien-len/bot")
    public String tienLenBot(@RequestParam(defaultValue = "easy") String difficulty,
                             HttpServletRequest request,
                             Model model) {
        SessionPlayer player = resolveSessionPlayer(request);
        model.addAttribute("sessionUserId", player.userId());
        model.addAttribute("sessionDisplayName", player.displayName());
        model.addAttribute("sessionAvatarPath", player.avatarPath());

        String botDifficulty = normalizeDifficulty(difficulty);
        boolean hard = "hard".equals(botDifficulty);
        model.addAttribute("botDifficulty", botDifficulty);
        model.addAttribute("pageTitle", "Tien len Bot");
        model.addAttribute("pageHeading", "TIEN LEN VOI BOT (" + (hard ? "HARD" : "EASY") + ") - MVP");
        model.addAttribute("pageSubtitle", "1 nguoi choi + 3 bot tren cung thiet bi. Khong can dang nhap.");
        model.addAttribute("pageNote", hard
            ? "Bot hard uu tien danh bo bai hop le co gia tri tot hon va giu the chu dong. Da ho tro doi thong va chat 2 co ban (tu quy / doi thong); cac luat nang cao khac dang bo sung."
            : "Bot easy danh ngau nhien trong cac nuoc hop le (uu tien bo bai nho/de danh). Da ho tro doi thong va chat 2 co ban (tu quy / doi thong); cac luat nang cao khac dang bo sung.");
        return "cards/tien-len-bot";
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

    private String normalizeDifficulty(String difficulty) {
        return "hard".equalsIgnoreCase(difficulty) ? "hard" : "easy";
    }

    private record SessionPlayer(String userId, String displayName, String avatarPath) {
    }
}
