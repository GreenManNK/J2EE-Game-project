package com.game.hub.controller;

import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.GameCatalogService;
import com.game.hub.support.TabularExportSupport;
import com.game.hub.support.GameHistoryPresentationSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/history")
public class HistoryController {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GameHistoryRepository gameHistoryRepository;
    private final UserAccountRepository userAccountRepository;
    private final GameHistoryPresentationSupport gameHistoryPresentationSupport;

    public HistoryController(GameHistoryRepository gameHistoryRepository,
                             UserAccountRepository userAccountRepository,
                             GameCatalogService gameCatalogService) {
        this.gameHistoryRepository = gameHistoryRepository;
        this.userAccountRepository = userAccountRepository;
        this.gameHistoryPresentationSupport = new GameHistoryPresentationSupport(gameCatalogService);
    }

    @GetMapping
    public String page(@RequestParam(required = false) String userId,
                       @RequestParam(required = false) String gameCode,
                       @RequestParam(required = false) String result,
                       @RequestParam(required = false) String fromDate,
                       @RequestParam(required = false) String toDate,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       HttpServletRequest request,
                       Model model) {
        String effectiveUserId = effectiveUserId(userId, request);
        if (effectiveUserId == null || effectiveUserId.isBlank()) {
            return "redirect:/account/login-page";
        }

        List<GameHistoryView> filtered = applyFilters(buildHistories(effectiveUserId), gameCode, result, fromDate, toDate);
        PageSlice<GameHistoryView> slice = paginate(filtered, page, size);

        model.addAttribute("userId", effectiveUserId);
        model.addAttribute("gameCode", safe(gameCode));
        model.addAttribute("result", safe(result));
        model.addAttribute("fromDate", safe(fromDate));
        model.addAttribute("toDate", safe(toDate));
        model.addAttribute("page", slice.page());
        model.addAttribute("size", slice.size());
        model.addAttribute("totalPages", slice.totalPages());
        model.addAttribute("totalItems", filtered.size());
        model.addAttribute("histories", slice.items());
        return "history/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public List<GameHistoryView> indexApi(@RequestParam(required = false) String userId,
                                          @RequestParam(required = false) String gameCode,
                                          @RequestParam(required = false) String result,
                                          @RequestParam(required = false) String fromDate,
                                          @RequestParam(required = false) String toDate,
                                          HttpServletRequest request) {
        String effectiveUserId = effectiveUserId(userId, request);
        if (effectiveUserId == null || effectiveUserId.isBlank()) {
            return List.of();
        }
        return applyFilters(buildHistories(effectiveUserId), gameCode, result, fromDate, toDate);
    }

    @GetMapping("/export-csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String userId,
                                            @RequestParam(required = false) String gameCode,
                                            @RequestParam(required = false) String result,
                                            @RequestParam(required = false) String fromDate,
                                            @RequestParam(required = false) String toDate,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(defaultValue = "all") String scope,
                                            HttpServletRequest request) {
        List<GameHistoryView> rows = resolveRowsForExport(userId, gameCode, result, fromDate, toDate, page, size, scope, request);
        byte[] body = TabularExportSupport.toCsv(
            new String[]{"Game", "GameCode", "Match", "Location", "At", "P1", "P2", "First", "Moves", "Winner", "Result", "OpenUrl"},
            toRows(rows)
        );
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=history-" + exportSuffix(scope, page) + ".csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(body);
    }

    @GetMapping("/export-excel")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String userId,
                                              @RequestParam(required = false) String gameCode,
                                              @RequestParam(required = false) String result,
                                              @RequestParam(required = false) String fromDate,
                                              @RequestParam(required = false) String toDate,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(defaultValue = "all") String scope,
                                              HttpServletRequest request) {
        List<GameHistoryView> rows = resolveRowsForExport(userId, gameCode, result, fromDate, toDate, page, size, scope, request);
        byte[] body = TabularExportSupport.toExcel(
            "History",
            new String[]{"Game", "GameCode", "Match", "Location", "At", "P1", "P2", "First", "Moves", "Winner", "Result", "OpenUrl"},
            toRows(rows)
        );
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=history-" + exportSuffix(scope, page) + ".xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body);
    }

    private List<GameHistoryView> resolveRowsForExport(String userId,
                                                       String gameCode,
                                                       String result,
                                                       String fromDate,
                                                       String toDate,
                                                       int page,
                                                       int size,
                                                       String scope,
                                                       HttpServletRequest request) {
        String effectiveUserId = effectiveUserId(userId, request);
        if (effectiveUserId == null || effectiveUserId.isBlank()) {
            return List.of();
        }
        List<GameHistoryView> filtered = applyFilters(buildHistories(effectiveUserId), gameCode, result, fromDate, toDate);
        if ("page".equalsIgnoreCase(scope)) {
            return paginate(filtered, page, size).items();
        }
        return filtered;
    }

    private String exportSuffix(String scope, int page) {
        if ("page".equalsIgnoreCase(scope)) {
            return "page-" + (Math.max(page, 0) + 1);
        }
        return "all";
    }

    private List<List<String>> toRows(List<GameHistoryView> rows) {
        return rows.stream().map(item -> List.of(
            nullSafe(item.gameName()),
            nullSafe(item.gameCode()),
            nullSafe(item.matchCode()),
            nullSafe(item.locationLabel()),
            formatDateTime(item.playedAt()),
            nullSafe(item.player1Name()),
            nullSafe(item.player2Name()),
            nullSafe(item.firstPlayerName()),
            String.valueOf(item.totalMoves()),
            nullSafe(item.winnerName()),
            nullSafe(item.result()),
            nullSafe(item.locationHref())
        )).toList();
    }

    private List<GameHistoryView> applyFilters(List<GameHistoryView> source,
                                               String gameCode,
                                               String result,
                                               String fromDate,
                                               String toDate) {
        String normalizedGame = trimToNull(gameCode);
        String normalizedResult = trimToNull(result);
        LocalDateTime from = parseDateStart(fromDate);
        LocalDateTime to = parseDateEnd(toDate);

        return source.stream().filter(item -> {
            if (normalizedGame != null) {
                String currentGame = String.join(" ",
                    nullSafe(item.gameCode()),
                    nullSafe(item.gameName())
                ).toLowerCase();
                if (!currentGame.contains(normalizedGame.toLowerCase())) {
                    return false;
                }
            }
            if (normalizedResult != null && !"all".equalsIgnoreCase(normalizedResult)) {
                if (!normalizedResult.equalsIgnoreCase(item.result())) {
                    return false;
                }
            }
            if (from != null) {
                LocalDateTime playedAt = item.playedAt();
                if (playedAt == null || playedAt.isBefore(from)) {
                    return false;
                }
            }
            if (to != null) {
                LocalDateTime playedAt = item.playedAt();
                if (playedAt == null || playedAt.isAfter(to)) {
                    return false;
                }
            }
            return true;
        }).toList();
    }

    private String effectiveUserId(String requestedUserId, HttpServletRequest request) {
        String sessionUserId = sessionUserId(request);
        if (sessionUserId == null) {
            return null;
        }
        String normalizedRequested = trimToNull(requestedUserId);
        if (normalizedRequested == null) {
            return sessionUserId;
        }
        if (sessionUserId.equals(normalizedRequested)) {
            return sessionUserId;
        }
        String sessionRole = sessionRole(request);
        if (sessionRole != null
            && ("Admin".equalsIgnoreCase(sessionRole) || "Manager".equalsIgnoreCase(sessionRole))) {
            return normalizedRequested;
        }
        return sessionUserId;
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

    private String sessionRole(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute("AUTH_ROLE");
        String role = value == null ? null : String.valueOf(value).trim();
        return role == null || role.isEmpty() ? null : role;
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

        return histories.stream().map(h -> {
            GameHistoryPresentationSupport.ViewMetadata metadata = gameHistoryPresentationSupport.describe(h);
            return new GameHistoryView(
                h.getId(),
                metadata.gameCode(),
                metadata.gameName(),
                metadata.gameIconClass(),
                metadata.gameHref(),
                metadata.matchCode(),
                metadata.locationLabel(),
                metadata.locationHref(),
                metadata.contextLabel(),
                displayNameFor(userNameMap, h.getPlayer1Id()),
                displayNameFor(userNameMap, h.getPlayer2Id()),
                displayNameFor(userNameMap, h.getFirstPlayerId()),
                h.getTotalMoves(),
                winnerNameFor(userNameMap, h.getWinnerId()),
                h.getPlayedAt(),
                resultLabelFor(userId, h.getWinnerId())
            );
        }).toList();
    }

    private <T> PageSlice<T> paginate(List<T> source, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int totalItems = source.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) safeSize));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = safePage * safeSize;
        int to = Math.min(from + safeSize, totalItems);
        List<T> items = from >= to ? List.of() : source.subList(from, to);
        return new PageSlice<>(items, safePage, safeSize, totalPages);
    }

    private record PageSlice<T>(List<T> items, int page, int size, int totalPages) {
    }

    public record GameHistoryView(Long id,
                                  String gameCode,
                                  String gameName,
                                  String gameIconClass,
                                  String gameHref,
                                  String matchCode,
                                  String locationLabel,
                                  String locationHref,
                                  String contextLabel,
                                  String player1Name,
                                  String player2Name,
                                  String firstPlayerName,
                                  int totalMoves,
                                  String winnerName,
                                  LocalDateTime playedAt,
                                  String result) {
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String displayNameFor(Map<String, String> userNameMap, String userId) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        String displayName = userNameMap.get(userId);
        if (displayName == null || displayName.isBlank()) {
            return userId;
        }
        return displayName;
    }

    private String winnerNameFor(Map<String, String> userNameMap, String winnerId) {
        if (winnerId == null || winnerId.isBlank()) {
            return "Hoa";
        }
        return displayNameFor(userNameMap, winnerId);
    }

    private String resultLabelFor(String userId, String winnerId) {
        if (winnerId == null || winnerId.isBlank()) {
            return "Hoa";
        }
        return userId.equals(winnerId) ? "Thang" : "Thua";
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }

    private LocalDateTime parseDateStart(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private LocalDateTime parseDateEnd(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value).atTime(23, 59, 59);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
