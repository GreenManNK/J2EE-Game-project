package com.game.hub.controller;

import com.game.hub.config.RoleGuardInterceptor;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataExportAdminOnlyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    void setUp() {
        saveUser("export-admin-1", "export-admin-1@test.com", "Export Admin", "Admin", 999);
        saveUser("export-manager-1", "export-manager-1@test.com", "Export Manager", "Manager", 500);
        saveUser("export-user-1", "export-user-1@test.com", "Export User", "User", 320);
    }

    @Test
    void leaderboardExportShouldBeAdminOnly() throws Exception {
        mockMvc.perform(get("/leaderboard/export-csv"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/account/login-page"));

        mockMvc.perform(get("/leaderboard/export-csv").session(session("export-user-1", "User")))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/leaderboard/export-csv").session(session("export-admin-1", "Admin")))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=leaderboard-all.csv"))
            .andExpect(content().string(containsString("Export User")));
    }

    @Test
    void managerUserExportShouldBeAdminOnly() throws Exception {
        mockMvc.perform(get("/manager/export-users-csv").session(session("export-manager-1", "Manager")))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/manager/export-users-excel").session(session("export-admin-1", "Admin")))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=manager-users-page-1.xlsx"));
    }

    @Test
    void leaderboardAndManagerPagesShouldHideExportActionsForNonAdmin() throws Exception {
        mockMvc.perform(get("/leaderboard").session(session("export-user-1", "User")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chi admin moi duoc xuat du lieu bang xep hang.")))
            .andExpect(content().string(not(containsString("Xuat CSV trang nay"))));

        mockMvc.perform(get("/leaderboard").session(session("export-admin-1", "Admin")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Xuat CSV trang nay")));

        mockMvc.perform(get("/manager/users").session(session("export-manager-1", "Manager")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chi admin moi duoc xuat du lieu tai khoan.")))
            .andExpect(content().string(not(containsString("Xuat Excel tat ca da loc"))));

        mockMvc.perform(get("/manager/users").session(session("export-admin-1", "Admin")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Xuat Excel tat ca da loc")));
    }

    @Test
    void settingsPageShouldHideExportShortcutsForNonAdmin() throws Exception {
        mockMvc.perform(get("/settings").session(session("export-user-1", "User")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chi admin moi duoc tai cac file bao cao va export du lieu.")))
            .andExpect(content().string(not(containsString("Lich su CSV"))))
            .andExpect(content().string(not(containsString("BXH CSV"))));

        mockMvc.perform(get("/settings").session(session("export-admin-1", "Admin")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Lich su CSV")))
            .andExpect(content().string(containsString("BXH CSV")))
            .andExpect(content().string(containsString("Excel tai khoan")));
    }

    private void saveUser(String id, String email, String displayName, String role, int score) {
        if (userAccountRepository.findById(id).isPresent()) {
            return;
        }
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(email);
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setScore(score);
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
