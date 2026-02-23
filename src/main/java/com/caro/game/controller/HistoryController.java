package com.caro.game.controller;

import com.caro.game.entity.GameHistory;
import com.caro.game.entity.UserAccount;
import com.caro.game.repository.GameHistoryRepository;
import com.caro.game.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/history")
public class HistoryController {
    private final GameHistoryRepository gameHistoryRepository;
    private final UserAccountRepository userAccountRepository;

    public HistoryController(GameHistoryRepository gameHistoryRepository, UserAccountRepository userAccountRepository) {
        this.gameHistoryRepository = gameHistoryRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public String page(@RequestParam(required = false) String userId,
                       HttpServletRequest request,
                       Model model) {
        String effectiveUserId = effectiveUserId(userId, request);
        if (effectiveUserId == null || effectiveUserId.isBlank()) {
            model.addAttribute("userId", "");
            model.addAttribute("histories", List.of());
            return "history/index";
        }
        model.addAttribute("userId", effectiveUserId);
        model.addAttribute("histories", buildHistories(effectiveUserId));
        return "history/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public List<GameHistoryView> indexApi(@RequestParam String userId, HttpServletRequest request) {
        String effectiveUserId = effectiveUserId(userId, request);
        if (effectiveUserId == null || effectiveUserId.isBlank()) {
            return List.of();
        }
        return buildHistories(effectiveUserId);
    }

    private String effectiveUserId(String requestedUserId, HttpServletRequest request) {
        String sessionUserId = sessionUserId(request);
        if (sessionUserId != null) {
            return sessionUserId;
        }
        return "";
    }

    private String sessionUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute("AUTH_USER_ID");
        if (value == null) {
            return null;
        }
        String userId = String.valueOf(value).trim();
        return userId.isEmpty() ? null : userId;
    }

    private List<GameHistoryView> buildHistories(String userId) {
        List<GameHistory> histories = gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc(userId, userId);
        Set<String> userIds = histories.stream()
            .flatMap(h -> java.util.stream.Stream.of(h.getPlayer1Id(), h.getPlayer2Id(), h.getFirstPlayerId(), h.getWinnerId()))
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toSet());

        Map<String, String> userNameMap = new HashMap<>();
        for (UserAccount u : userAccountRepository.findAllById(userIds)) {
            userNameMap.put(u.getId(), u.getDisplayName());
        }

        return histories.stream().map(h -> new GameHistoryView(
            h.getGameCode(),
            userNameMap.getOrDefault(h.getPlayer1Id(), h.getPlayer1Id()),
            userNameMap.getOrDefault(h.getPlayer2Id(), h.getPlayer2Id()),
            userNameMap.getOrDefault(h.getFirstPlayerId(), h.getFirstPlayerId()),
            h.getTotalMoves(),
            userNameMap.getOrDefault(h.getWinnerId(), h.getWinnerId()),
            h.getPlayedAt(),
            userId.equals(h.getWinnerId()) ? "Thang" : "Thua"
        )).toList();
    }

    public record GameHistoryView(String gameCode,
                                  String player1Name,
                                  String player2Name,
                                  String firstPlayerName,
                                  int totalMoves,
                                  String winnerName,
                                  LocalDateTime playedAt,
                                  String result) {
    }
}
