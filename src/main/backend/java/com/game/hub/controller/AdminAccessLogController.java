package com.game.hub.controller;

import com.game.hub.entity.PageAccessLog;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.PageAccessLogRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.DataExportAuditService;
import com.game.hub.support.TabularExportSupport;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/admin/access-logs")
public class AdminAccessLogController {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PageAccessLogRepository pageAccessLogRepository;
    private final UserAccountRepository userAccountRepository;
    private final DataExportAuditService dataExportAuditService;

    public AdminAccessLogController(PageAccessLogRepository pageAccessLogRepository,
                                    UserAccountRepository userAccountRepository,
                                    DataExportAuditService dataExportAuditService) {
        this.pageAccessLogRepository = pageAccessLogRepository;
        this.userAccountRepository = userAccountRepository;
        this.dataExportAuditService = dataExportAuditService;
    }

    @GetMapping
    public String page(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String method,
                       @RequestParam(required = false) String fromDate,
                       @RequestParam(required = false) String toDate,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "50") int size,
                       Model model) {
        return "redirect:/admin";
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> api(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) String method,
                                   @RequestParam(required = false) String fromDate,
                                   @RequestParam(required = false) String toDate,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        List<PageAccessLogView> filtered = applyFilters(loadViews(), q, method, fromDate, toDate);
        PageSlice<PageAccessLogView> slice = paginate(filtered, page, size);
        return Map.of(
            "items", slice.items(),
            "page", slice.page(),
            "size", slice.size(),
            "totalPages", slice.totalPages(),
            "totalItems", filtered.size(),
            "summary", buildSummary(filtered)
        );
    }

    @GetMapping("/export-csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) String method,
                                            @RequestParam(required = false) String fromDate,
                                            @RequestParam(required = false) String toDate,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size,
                                            @RequestParam(defaultValue = "all") String scope,
                                            HttpServletRequest request) {
        List<PageAccessLogView> rows = resolveRowsForExport(q, method, fromDate, toDate, page, size, scope);
        byte[] body = TabularExportSupport.toCsv(
            new String[]{"VisitedAt", "Method", "Path", "Query", "UserId", "DisplayName", "Role", "ClientIP", "UserAgent", "Referer"},
            toRows(rows)
        );
        String filename = "access-logs-" + exportSuffix(scope, page) + ".csv";
        dataExportAuditService.recordExport(request, "access-logs", "Access logs", "csv", filename, scope, rows.size(), null);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(body);
    }

    @GetMapping("/export-excel")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) String method,
                                              @RequestParam(required = false) String fromDate,
                                              @RequestParam(required = false) String toDate,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size,
                                              @RequestParam(defaultValue = "all") String scope,
                                              HttpServletRequest request) {
        List<PageAccessLogView> rows = resolveRowsForExport(q, method, fromDate, toDate, page, size, scope);
        byte[] body = TabularExportSupport.toExcel(
            "AccessLogs",
            new String[]{"VisitedAt", "Method", "Path", "Query", "UserId", "DisplayName", "Role", "ClientIP", "UserAgent", "Referer"},
            toRows(rows)
        );
        String filename = "access-logs-" + exportSuffix(scope, page) + ".xlsx";
        dataExportAuditService.recordExport(request, "access-logs", "Access logs", "excel", filename, scope, rows.size(), null);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body);
    }

    private List<PageAccessLogView> resolveRowsForExport(String q,
                                                         String method,
                                                         String fromDate,
                                                         String toDate,
                                                         int page,
                                                         int size,
                                                         String scope) {
        List<PageAccessLogView> filtered = applyFilters(loadViews(), q, method, fromDate, toDate);
        if ("page".equalsIgnoreCase(scope)) {
            return paginate(filtered, page, size).items();
        }
        return filtered;
    }

    private List<List<String>> toRows(List<PageAccessLogView> rows) {
        return rows.stream().map(item -> List.of(
            formatDateTime(item.visitedAt()),
            nullSafe(item.httpMethod()),
            nullSafe(item.requestPath()),
            nullSafe(item.queryString()),
            nullSafe(item.userId()),
            nullSafe(item.userDisplayName()),
            nullSafe(item.userRole()),
            nullSafe(item.clientIp()),
            nullSafe(item.userAgent()),
            nullSafe(item.referer())
        )).toList();
    }

    private String exportSuffix(String scope, int page) {
        if ("page".equalsIgnoreCase(scope)) {
            return "page-" + (Math.max(page, 0) + 1);
        }
        return "all";
    }

    private List<PageAccessLogView> loadViews() {
        List<PageAccessLog> logs = pageAccessLogRepository.findAllByOrderByVisitedAtDesc();
        Set<String> userIds = logs.stream()
            .map(PageAccessLog::getUserId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toSet());

        Map<String, String> displayNameById = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (UserAccount user : userAccountRepository.findAllById(userIds)) {
                displayNameById.put(user.getId(), trimToNull(user.getDisplayName()));
            }
        }

        return logs.stream().map(log -> {
            String userId = trimToNull(log.getUserId());
            String displayName = userId == null ? null : displayNameById.get(userId);
            return new PageAccessLogView(
                log.getVisitedAt(),
                trimToNull(log.getHttpMethod()),
                trimToNull(log.getRequestPath()),
                trimToNull(log.getQueryString()),
                userId,
                displayName == null ? userId : displayName,
                trimToNull(log.getUserRole()),
                trimToNull(log.getClientIp()),
                trimToNull(log.getUserAgent()),
                trimToNull(log.getReferer())
            );
        }).toList();
    }

    private List<PageAccessLogView> applyFilters(List<PageAccessLogView> source,
                                                 String q,
                                                 String method,
                                                 String fromDate,
                                                 String toDate) {
        String normalizedQ = trimToNull(q);
        String normalizedMethod = trimToNull(method);
        LocalDateTime from = parseDateStart(fromDate);
        LocalDateTime to = parseDateEnd(toDate);

        return source.stream().filter(item -> {
            if (normalizedMethod != null && !"all".equalsIgnoreCase(normalizedMethod)) {
                String currentMethod = String.valueOf(item.httpMethod());
                if (!normalizedMethod.equalsIgnoreCase(currentMethod)) {
                    return false;
                }
            }

            if (normalizedQ != null) {
                String haystack = (
                    nullSafe(item.requestPath()) + " " +
                    nullSafe(item.queryString()) + " " +
                    nullSafe(item.userId()) + " " +
                    nullSafe(item.userDisplayName()) + " " +
                    nullSafe(item.clientIp()) + " " +
                    nullSafe(item.userAgent())
                ).toLowerCase();
                if (!haystack.contains(normalizedQ.toLowerCase())) {
                    return false;
                }
            }

            if (from != null) {
                LocalDateTime visitedAt = item.visitedAt();
                if (visitedAt == null || visitedAt.isBefore(from)) {
                    return false;
                }
            }
            if (to != null) {
                LocalDateTime visitedAt = item.visitedAt();
                if (visitedAt == null || visitedAt.isAfter(to)) {
                    return false;
                }
            }
            return true;
        }).toList();
    }

    private <T> PageSlice<T> paginate(List<T> source, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 200));
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

    public record PageAccessLogView(LocalDateTime visitedAt,
                                    String httpMethod,
                                    String requestPath,
                                    String queryString,
                                    String userId,
                                    String userDisplayName,
                                    String userRole,
                                    String clientIp,
                                    String userAgent,
                                    String referer) {
    }

    private Map<String, Object> buildSummary(List<PageAccessLogView> rows) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCount = rows.stream()
            .map(PageAccessLogView::visitedAt)
            .filter(v -> v != null && !v.isBefore(todayStart))
            .count();
        long guestCount = rows.stream()
            .filter(v -> trimToNull(v.userId()) == null)
            .count();
        long uniqueUsers = rows.stream()
            .map(PageAccessLogView::userId)
            .map(this::trimToNull)
            .filter(v -> v != null)
            .distinct()
            .count();
        long uniqueIps = rows.stream()
            .map(PageAccessLogView::clientIp)
            .map(this::trimToNull)
            .filter(v -> v != null)
            .distinct()
            .count();
        long postCount = rows.stream()
            .filter(v -> "POST".equalsIgnoreCase(v.httpMethod()))
            .count();
        return Map.of(
            "todayCount", todayCount,
            "guestCount", guestCount,
            "uniqueUsers", uniqueUsers,
            "uniqueIps", uniqueIps,
            "postCount", postCount
        );
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
