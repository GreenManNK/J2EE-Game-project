package com.game.hub.controller;

import com.game.hub.config.RoleGuardInterceptor;
import com.game.hub.entity.DataExportAuditLog;
import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.DataExportAuditLogRepository;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataExportAuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private GameHistoryRepository gameHistoryRepository;

    @Autowired
    private DataExportAuditLogRepository dataExportAuditLogRepository;

    @BeforeEach
    void setUp() {
        dataExportAuditLogRepository.deleteAll();
        gameHistoryRepository.deleteAll();

        saveUser("audit-admin-1", "audit-admin-1@test.com", "Audit Admin", "Admin");
        saveUser("audit-manager-1", "audit-manager-1@test.com", "Audit Manager", "Manager");
        saveUser("audit-player-1", "audit-player-1@test.com", "Audit Player", "User");
        saveUser("audit-player-2", "audit-player-2@test.com", "Audit Opponent", "User");

        GameHistory history = new GameHistory();
        history.setGameCode("caro");
        history.setMatchCode("Normal_AUDIT-20260327153000");
        history.setRoomId("Normal_AUDIT");
        history.setLocationLabel("Phong audit Caro");
        history.setLocationPath("/game/room/Normal_AUDIT");
        history.setPlayer1Id("audit-player-1");
        history.setPlayer2Id("audit-player-2");
        history.setFirstPlayerId("audit-player-1");
        history.setWinnerId("audit-player-1");
        history.setTotalMoves(24);
        history.setPlayedAt(LocalDateTime.of(2026, 3, 27, 15, 30));
        gameHistoryRepository.save(history);
    }

    @Test
    void historyExportShouldWriteAuditLog() throws Exception {
        mockMvc.perform(get("/history/export-csv")
                .param("userId", "audit-player-1")
                .session(session("audit-admin-1", "Admin")))
            .andExpect(status().isOk());

        List<DataExportAuditLog> logs = dataExportAuditLogRepository.findAllByOrderByExportedAtDesc();
        assertEquals(1, logs.size());

        DataExportAuditLog log = logs.get(0);
        assertEquals("history", log.getReportCode());
        assertEquals("Lich su dau", log.getReportLabel());
        assertEquals("csv", log.getFileFormat());
        assertEquals("history-all.csv", log.getFilename());
        assertEquals("all", log.getScope());
        assertEquals(Integer.valueOf(1), log.getRowCount());
        assertEquals("audit-player-1", log.getTargetUserId());
        assertEquals("audit-admin-1", log.getActorUserId());
        assertEquals("Admin", log.getActorUserRole());
        assertEquals("/history/export-csv", log.getRequestPath());
        assertFalse(log.getClientIp() == null || log.getClientIp().isBlank());
    }

    @Test
    void moduleRegistryExportShouldWriteAuditLog() throws Exception {
        mockMvc.perform(get("/admin/game-modules/api/export")
                .session(session("audit-admin-1", "Admin")))
            .andExpect(status().isOk());

        List<DataExportAuditLog> logs = dataExportAuditLogRepository.findAllByOrderByExportedAtDesc();
        assertEquals(1, logs.size());
        assertEquals("module-registry", logs.get(0).getReportCode());
        assertEquals("json", logs.get(0).getFileFormat());
        assertEquals("external-game-modules-backup.json", logs.get(0).getFilename());
    }

    @Test
    void reportCenterShouldBeAdminOnlyAndRenderRecentAuditRows() throws Exception {
        DataExportAuditLog log = new DataExportAuditLog();
        log.setExportedAt(LocalDateTime.of(2026, 3, 27, 18, 45));
        log.setReportCode("history");
        log.setReportLabel("Lich su dau");
        log.setFileFormat("csv");
        log.setFilename("history-all.csv");
        log.setScope("all");
        log.setRowCount(1);
        log.setTargetUserId("audit-player-1");
        log.setActorUserId("audit-admin-1");
        log.setActorUserRole("Admin");
        log.setClientIp("127.0.0.1");
        log.setRequestPath("/history/export-csv");
        dataExportAuditLogRepository.save(log);

        mockMvc.perform(get("/admin/report-center"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/account/login-page"));

        mockMvc.perform(get("/admin/report-center").session(session("audit-manager-1", "Manager")))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/report-center").session(session("audit-admin-1", "Admin")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Trung tam export va audit du lieu")))
            .andExpect(content().string(containsString("history-all.csv")))
            .andExpect(content().string(containsString("Lich su dau")))
            .andExpect(content().string(containsString("Audit Admin")));
    }

    private void saveUser(String id, String email, String displayName, String role) {
        if (userAccountRepository.findById(id).isPresent()) {
            return;
        }
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(email);
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setEmailConfirmed(true);
        userAccountRepository.save(user);
    }

    private MockHttpSession session(String userId, String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RoleGuardInterceptor.AUTH_USER_ID, userId);
        session.setAttribute(RoleGuardInterceptor.AUTH_ROLE, role);
        return session;
    }
}
