package com.game.hub.controller;

import com.game.hub.entity.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.DataExportAuditService;
import com.game.hub.support.TabularExportSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/leaderboard")
public class LeaderboardController {
    private final UserAccountRepository userAccountRepository;
    private final DataExportAuditService dataExportAuditService;

    public LeaderboardController(UserAccountRepository userAccountRepository,
                                 DataExportAuditService dataExportAuditService) {
        this.userAccountRepository = userAccountRepository;
        this.dataExportAuditService = dataExportAuditService;
    }

    @GetMapping
    public String page(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Integer minScore,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       HttpServletRequest request,
                       Model model) {
        List<LeaderboardItem> filtered = applyFilters(buildItems(), q, minScore);
        PageSlice<LeaderboardItem> slice = paginate(filtered, page, size);
        model.addAttribute("players", slice.items());
        model.addAttribute("q", safe(q));
        model.addAttribute("minScore", minScore == null ? "" : minScore);
        model.addAttribute("page", slice.page());
        model.addAttribute("size", slice.size());
        model.addAttribute("totalPages", slice.totalPages());
        model.addAttribute("totalItems", filtered.size());
        model.addAttribute("viewerRole", safe(sessionRole(request)));
        model.addAttribute("canExportLeaderboard", isAdminRole(sessionRole(request)));
        return "leaderboard/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public List<LeaderboardItem> indexApi(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) Integer minScore) {
        return applyFilters(buildItems(), q, minScore);
    }

    @GetMapping("/export-csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) Integer minScore,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(defaultValue = "all") String scope,
                                            HttpServletRequest request) {
        ResponseEntity<byte[]> denied = ensureAdminExportAccess(request);
        if (denied != null) {
            return denied;
        }
        List<LeaderboardItem> filtered = applyFilters(buildItems(), q, minScore);
        boolean pageScope = "page".equalsIgnoreCase(scope);
        List<LeaderboardItem> rows = pageScope ? paginate(filtered, page, size).items() : filtered;
        int rankOffset = pageScope ? Math.max(0, page) * Math.max(1, Math.min(size, 100)) : 0;

        byte[] body = TabularExportSupport.toCsv(
            new String[]{"Rank", "User ID", "Name", "Score"},
            toRows(rows, rankOffset)
        );
        String filename = "leaderboard-" + exportSuffix(scope, page) + ".csv";
        dataExportAuditService.recordExport(request, "leaderboard", "Bang xep hang", "csv", filename, scope, rows.size(), null);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(body);
    }

    @GetMapping("/export-excel")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) Integer minScore,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(defaultValue = "all") String scope,
                                              HttpServletRequest request) {
        ResponseEntity<byte[]> denied = ensureAdminExportAccess(request);
        if (denied != null) {
            return denied;
        }
        List<LeaderboardItem> filtered = applyFilters(buildItems(), q, minScore);
        boolean pageScope = "page".equalsIgnoreCase(scope);
        List<LeaderboardItem> rows = pageScope ? paginate(filtered, page, size).items() : filtered;
        int rankOffset = pageScope ? Math.max(0, page) * Math.max(1, Math.min(size, 100)) : 0;

        byte[] body = TabularExportSupport.toExcel(
            "Leaderboard",
            new String[]{"Rank", "User ID", "Name", "Score"},
            toRows(rows, rankOffset)
        );
        String filename = "leaderboard-" + exportSuffix(scope, page) + ".xlsx";
        dataExportAuditService.recordExport(request, "leaderboard", "Bang xep hang", "excel", filename, scope, rows.size(), null);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body);
    }

    private ResponseEntity<byte[]> ensureAdminExportAccess(HttpServletRequest request) {
        String role = sessionRole(request);
        if (role == null) {
            return ResponseEntity.status(401).build();
        }
        if (!isAdminRole(role)) {
            return ResponseEntity.status(403).build();
        }
        return null;
    }

    private List<LeaderboardItem> buildItems() {
        return userAccountRepository.findAllByOrderByScoreDesc().stream()
            .map(u -> new LeaderboardItem(u.getId(), u.getDisplayName(), u.getScore(), u.getAvatarPath()))
            .toList();
    }

    private List<LeaderboardItem> applyFilters(List<LeaderboardItem> source,
                                               String q,
                                               Integer minScore) {
        String query = trimToNull(q);
        int scoreFloor = minScore == null ? Integer.MIN_VALUE : minScore;
        return source.stream().filter(item -> {
            if (item.score() < scoreFloor) {
                return false;
            }
            if (query == null) {
                return true;
            }
            String queryLower = query.toLowerCase();
            String haystack = (nullSafe(item.userId()) + " " + nullSafe(item.displayName())).toLowerCase();
            return haystack.contains(queryLower);
        }).toList();
    }

    private List<List<String>> toRows(List<LeaderboardItem> items, int rankOffset) {
        return java.util.stream.IntStream.range(0, items.size())
            .mapToObj(index -> {
                LeaderboardItem item = items.get(index);
                return List.of(
                    String.valueOf(rankOffset + index + 1),
                    nullSafe(item.userId()),
                    nullSafe(item.displayName()),
                    String.valueOf(item.score())
                );
            })
            .toList();
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

    private String exportSuffix(String scope, int page) {
        if ("page".equalsIgnoreCase(scope)) {
            return "page-" + (Math.max(page, 0) + 1);
        }
        return "all";
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

    private boolean isAdminRole(String role) {
        return "Admin".equalsIgnoreCase(role);
    }

    public record LeaderboardItem(String userId, String displayName, int score, String avatarPath) {
    }
}
