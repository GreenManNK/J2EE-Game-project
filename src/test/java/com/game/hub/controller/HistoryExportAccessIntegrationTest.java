package com.game.hub.controller;

import com.game.hub.config.RoleGuardInterceptor;
import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.GameHistoryRepository;
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

import java.time.LocalDateTime;

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
class HistoryExportAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private GameHistoryRepository gameHistoryRepository;

    @BeforeEach
    void setUp() {
        gameHistoryRepository.deleteAll();

        UserAccount player = new UserAccount();
        player.setId("player-history-1");
        player.setEmail("player-history-1@test.com");
        player.setUsername("player-history-1@test.com");
        player.setDisplayName("Player History");
        player.setRole("User");

        UserAccount opponent = new UserAccount();
        opponent.setId("player-history-2");
        opponent.setEmail("player-history-2@test.com");
        opponent.setUsername("player-history-2@test.com");
        opponent.setDisplayName("Opponent History");
        opponent.setRole("User");

        userAccountRepository.save(player);
        userAccountRepository.save(opponent);

        GameHistory history = new GameHistory();
        history.setGameCode("caro");
        history.setMatchCode("Normal_ROOM-20260327121500");
        history.setRoomId("Normal_ROOM");
        history.setLocationLabel("Phong thuong Caro");
        history.setLocationPath("/game/room/Normal_ROOM");
        history.setPlayer1Id("player-history-1");
        history.setPlayer2Id("player-history-2");
        history.setFirstPlayerId("player-history-1");
        history.setWinnerId("player-history-1");
        history.setTotalMoves(22);
        history.setPlayedAt(LocalDateTime.of(2026, 3, 27, 12, 15));
        gameHistoryRepository.save(history);
    }

    @Test
    void anonymousUserCannotExportHistory() throws Exception {
        mockMvc.perform(get("/history/export-csv").param("userId", "player-history-1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/account/login-page"));
    }

    @Test
    void nonAdminAccountsCannotExportHistory() throws Exception {
        mockMvc.perform(get("/history/export-csv")
                .param("userId", "player-history-1")
                .session(session("player-history-1", "User")))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/history/export-excel")
                .param("userId", "player-history-1")
                .session(session("manager-history-1", "Manager")))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanExportHistoryFiles() throws Exception {
        MockHttpSession adminSession = session("admin-history-1", "Admin");

        mockMvc.perform(get("/history/export-csv")
                .param("userId", "player-history-1")
                .session(adminSession))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=history-all.csv"))
            .andExpect(content().string(containsString("Normal_ROOM-20260327121500")))
            .andExpect(content().string(containsString("Phong thuong Caro")));

        mockMvc.perform(get("/history/export-excel")
                .param("userId", "player-history-1")
                .session(adminSession))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=history-all.xlsx"));
    }

    @Test
    void historyPageShouldHideExportActionsForRegularUserAndShowThemForAdmin() throws Exception {
        mockMvc.perform(get("/history").session(session("player-history-1", "User")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chi admin moi duoc xuat du lieu lich su.")))
            .andExpect(content().string(not(containsString("Xuat CSV trang hien tai"))));

        mockMvc.perform(get("/history")
                .param("userId", "player-history-1")
                .session(session("admin-history-1", "Admin")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Mo report center voi bo loc nay")))
            .andExpect(content().string(containsString("Ban do tran dau cua nguoi choi")));
    }

    private MockHttpSession session(String userId, String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RoleGuardInterceptor.AUTH_USER_ID, userId);
        session.setAttribute(RoleGuardInterceptor.AUTH_ROLE, role);
        return session;
    }
}
