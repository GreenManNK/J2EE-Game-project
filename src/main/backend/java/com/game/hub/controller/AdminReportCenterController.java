package com.game.hub.controller;

import com.game.hub.entity.DataExportAuditLog;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.DataExportAuditLogRepository;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.PageAccessLogRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.ExternalGameModuleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/report-center")
public class AdminReportCenterController {
    private final DataExportAuditLogRepository dataExportAuditLogRepository;
    private final UserAccountRepository userAccountRepository;
    private final GameHistoryRepository gameHistoryRepository;
    private final PageAccessLogRepository pageAccessLogRepository;
    private final ExternalGameModuleService externalGameModuleService;

    public AdminReportCenterController(DataExportAuditLogRepository dataExportAuditLogRepository,
                                       UserAccountRepository userAccountRepository,
                                       GameHistoryRepository gameHistoryRepository,
                                       PageAccessLogRepository pageAccessLogRepository,
                                       ExternalGameModuleService externalGameModuleService) {
        this.dataExportAuditLogRepository = dataExportAuditLogRepository;
        this.userAccountRepository = userAccountRepository;
        this.gameHistoryRepository = gameHistoryRepository;
        this.pageAccessLogRepository = pageAccessLogRepository;
        this.externalGameModuleService = externalGameModuleService;
    }

    @GetMapping
    public String page(@RequestParam(required = false) String report,
                       @RequestParam(required = false) String historyUserId,
                       @RequestParam(required = false) String historyGameCode,
                       @RequestParam(required = false) String historyResult,
                       @RequestParam(required = false) String historyFromDate,
                       @RequestParam(required = false) String historyToDate,
                       @RequestParam(defaultValue = "0") int historyPage,
                       @RequestParam(defaultValue = "20") int historySize,
                       @RequestParam(defaultValue = "all") String historyScope,
                       @RequestParam(required = false) String leaderboardQ,
                       @RequestParam(required = false) Integer leaderboardMinScore,
                       @RequestParam(defaultValue = "0") int leaderboardPage,
                       @RequestParam(defaultValue = "20") int leaderboardSize,
                       @RequestParam(defaultValue = "all") String leaderboardScope,
                       @RequestParam(required = false) String managedSearchTerm,
                       @RequestParam(required = false) String managedBanFilter,
                       @RequestParam(defaultValue = "0") int managedPage,
                       @RequestParam(defaultValue = "10") int managedSize,
                       @RequestParam(defaultValue = "page") String managedScope,
                       @RequestParam(required = false) String adminSearchTerm,
                       @RequestParam(required = false) String adminBanFilter,
                       @RequestParam(defaultValue = "0") int adminPage,
                       @RequestParam(defaultValue = "10") int adminSize,
                       @RequestParam(defaultValue = "page") String adminScope,
                       @RequestParam(required = false) String logsQ,
                       @RequestParam(required = false) String logsMethod,
                       @RequestParam(required = false) String logsFromDate,
                       @RequestParam(required = false) String logsToDate,
                       @RequestParam(defaultValue = "0") int logsPage,
                       @RequestParam(defaultValue = "50") int logsSize,
                       @RequestParam(defaultValue = "all") String logsScope,
                       HttpServletRequest request,
                       Model model) {
        List<DataExportAuditLog> allAuditLogs = dataExportAuditLogRepository.findAllByOrderByExportedAtDesc();
        List<ExportAuditView> auditViews = toAuditViews(allAuditLogs.stream().limit(20).toList());

        model.addAttribute("focusReport", safe(report));
        model.addAttribute("historyUserId", defaultHistoryUserId(historyUserId, request));
        model.addAttribute("historyGameCode", safe(historyGameCode));
        model.addAttribute("historyResult", safe(historyResult));
        model.addAttribute("historyFromDate", safe(historyFromDate));
        model.addAttribute("historyToDate", safe(historyToDate));
        model.addAttribute("historyPage", Math.max(historyPage, 0));
        model.addAttribute("historySize", clamp(historySize, 1, 100));
        model.addAttribute("historyScope", normalizeScope(historyScope, "all"));

        model.addAttribute("leaderboardQ", safe(leaderboardQ));
        model.addAttribute("leaderboardMinScore", leaderboardMinScore == null ? "" : leaderboardMinScore);
        model.addAttribute("leaderboardPage", Math.max(leaderboardPage, 0));
        model.addAttribute("leaderboardSize", clamp(leaderboardSize, 1, 100));
        model.addAttribute("leaderboardScope", normalizeScope(leaderboardScope, "all"));

        model.addAttribute("managedSearchTerm", safe(managedSearchTerm));
        model.addAttribute("managedBanFilter", safe(managedBanFilter));
        model.addAttribute("managedPage", Math.max(managedPage, 0));
        model.addAttribute("managedSize", clamp(managedSize, 1, 100));
        model.addAttribute("managedScope", normalizeScope(managedScope, "page"));

        model.addAttribute("adminSearchTerm", safe(adminSearchTerm));
        model.addAttribute("adminBanFilter", safe(adminBanFilter));
        model.addAttribute("adminPage", Math.max(adminPage, 0));
        model.addAttribute("adminSize", clamp(adminSize, 1, 100));
        model.addAttribute("adminScope", normalizeScope(adminScope, "page"));

        model.addAttribute("logsQ", safe(logsQ));
        model.addAttribute("logsMethod", safe(logsMethod));
        model.addAttribute("logsFromDate", safe(logsFromDate));
        model.addAttribute("logsToDate", safe(logsToDate));
        model.addAttribute("logsPage", Math.max(logsPage, 0));
        model.addAttribute("logsSize", clamp(logsSize, 1, 200));
        model.addAttribute("logsScope", normalizeScope(logsScope, "all"));

        model.addAttribute("reportSummary", buildSummary(allAuditLogs));
        model.addAttribute("reportDatasets", buildDatasets());
        model.addAttribute("recentExportAudits", auditViews);
        return "admin/report-center";
    }

    private String defaultHistoryUserId(String historyUserId, HttpServletRequest request) {
        String requested = trimToNull(historyUserId);
        if (requested != null) {
            return requested;
        }
        HttpSession session = request == null ? null : request.getSession(false);
        return session == null ? "" : safe(trimToNull(session.getAttribute("AUTH_USER_ID")));
    }

    private List<ExportAuditView> toAuditViews(List<DataExportAuditLog> logs) {
        Set<String> actorIds = logs.stream()
            .map(DataExportAuditLog::getActorUserId)
            .map(this::trimToNull)
            .filter(value -> value != null)
            .collect(Collectors.toSet());

        Map<String, String> displayNames = new HashMap<>();
        if (!actorIds.isEmpty()) {
            for (UserAccount user : userAccountRepository.findAllById(actorIds)) {
                displayNames.put(user.getId(), trimToNull(user.getDisplayName()));
            }
        }

        return logs.stream().map(log -> {
            String actorId = trimToNull(log.getActorUserId());
            String displayName = actorId == null ? null : displayNames.get(actorId);
            return new ExportAuditView(
                log.getExportedAt(),
                safe(log.getReportCode()),
                safe(log.getReportLabel()),
                safe(log.getFileFormat()),
                safe(log.getFilename()),
                safe(log.getScope()),
                log.getRowCount() == null ? 0 : Math.max(log.getRowCount(), 0),
                safe(log.getTargetUserId()),
                safe(actorId),
                safe(displayName == null ? actorId : displayName),
                safe(log.getActorUserRole()),
                safe(log.getClientIp()),
                safe(log.getRequestPath()),
                safe(log.getQueryString())
            );
        }).toList();
    }

    private ReportSummaryView buildSummary(List<DataExportAuditLog> rows) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCount = rows.stream()
            .map(DataExportAuditLog::getExportedAt)
            .filter(value -> value != null && !value.isBefore(todayStart))
            .count();
        long uniqueActors = rows.stream()
            .map(DataExportAuditLog::getActorUserId)
            .map(this::trimToNull)
            .filter(value -> value != null)
            .distinct()
            .count();
        String hottestReport = rows.stream()
            .collect(Collectors.groupingBy(log -> safe(trimToNull(log.getReportLabel())), Collectors.counting()))
            .entrySet().stream()
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .map(Map.Entry::getKey)
            .map(this::safe)
            .findFirst()
            .orElse("Chua co export");
        return new ReportSummaryView(rows.size(), todayCount, uniqueActors, hottestReport);
    }

    private List<ReportDatasetView> buildDatasets() {
        return List.of(
            new ReportDatasetView("history", "Lich su dau", "Theo doi tran dau theo nguoi choi, game, ket qua va moc thoi gian.", gameHistoryRepository.count()),
            new ReportDatasetView("leaderboard", "Bang xep hang", "Tai du lieu diem, thu hang va tap nguoi choi da loc.", userAccountRepository.count()),
            new ReportDatasetView("managed-users", "Tai khoan manager", "Export du lieu tai khoan tu bo loc manager.", userAccountRepository.count()),
            new ReportDatasetView("admin-users", "Tai khoan admin", "Export dataset tai khoan day du tu admin center.", userAccountRepository.count()),
            new ReportDatasetView("access-logs", "Access logs", "Xuat request log, ip, role va user-agent cho audit.", pageAccessLogRepository.count()),
            new ReportDatasetView("module-registry", "Module registry", "Tai backup JSON cua registry module game ngoai.", externalGameModuleService.listConfigurations().size())
        );
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private String normalizeScope(String scope, String fallback) {
        String normalized = trimToNull(scope);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        return raw.isEmpty() ? null : raw;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record ReportSummaryView(int totalExports,
                                    long exportsToday,
                                    long uniqueActors,
                                    String hottestReport) {
    }

    public record ReportDatasetView(String code,
                                    String title,
                                    String description,
                                    long rowCount) {
    }

    public record ExportAuditView(LocalDateTime exportedAt,
                                  String reportCode,
                                  String reportLabel,
                                  String fileFormat,
                                  String filename,
                                  String scope,
                                  int rowCount,
                                  String targetUserId,
                                  String actorUserId,
                                  String actorDisplayName,
                                  String actorRole,
                                  String clientIp,
                                  String requestPath,
                                  String queryString) {
    }
}
