package com.game.hub.controller;

import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.GameCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoryControllerTest {

    @Test
    void pageShouldRenderStructuredHistoryDetails() {
        GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        GameHistory history = new GameHistory();
        history.setId(91L);
        history.setGameCode("caro");
        history.setMatchCode("Normal_ABC123-1743041234567");
        history.setRoomId("Normal_ABC123");
        history.setLocationLabel("Phong thuong Caro");
        history.setLocationPath("/game/room/Normal_ABC123");
        history.setPlayer1Id("u1");
        history.setPlayer2Id("u2");
        history.setFirstPlayerId("u1");
        history.setWinnerId("u1");
        history.setTotalMoves(18);
        history.setPlayedAt(LocalDateTime.of(2026, 3, 27, 10, 30));

        UserAccount playerOne = new UserAccount();
        playerOne.setId("u1");
        playerOne.setDisplayName("Player One");

        UserAccount playerTwo = new UserAccount();
        playerTwo.setId("u2");
        playerTwo.setDisplayName("Player Two");

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");
        when(session.getAttribute("AUTH_ROLE")).thenReturn("User");
        when(gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc("u1", "u1")).thenReturn(List.of(history));
        when(userAccountRepository.findById("u1")).thenReturn(java.util.Optional.of(playerOne));
        when(userAccountRepository.findAllById(any())).thenReturn(List.of(playerOne, playerTwo));

        HistoryController controller = new HistoryController(
            gameHistoryRepository,
            userAccountRepository,
            new GameCatalogService()
        );

        ConcurrentModel model = new ConcurrentModel();
        String viewName = controller.page("u1", null, null, null, null, 0, 20, request, model);

        assertEquals("history/index", viewName);
        @SuppressWarnings("unchecked")
        List<HistoryController.GameHistoryView> histories =
            (List<HistoryController.GameHistoryView>) model.getAttribute("histories");
        assertFalse(histories.isEmpty());

        HistoryController.GameHistoryView row = histories.get(0);
        assertEquals("caro", row.gameCode());
        assertEquals("Caro", row.gameName());
        assertEquals("Normal_ABC123-1743041234567", row.matchCode());
        assertEquals("Phong thuong Caro", row.locationLabel());
        assertEquals("/game/room/Normal_ABC123", row.locationHref());
        assertEquals("Player One", row.player1Name());
        assertEquals("Player Two", row.player2Name());
        assertEquals("Thang", row.result());
        assertEquals("Player One", model.getAttribute("historyOwnerName"));
        assertEquals(Boolean.FALSE, model.getAttribute("canExportHistory"));
        assertEquals(Boolean.FALSE, model.getAttribute("canSwitchHistoryUser"));

        HistoryController.HistorySummaryView summary =
            (HistoryController.HistorySummaryView) model.getAttribute("historySummary");
        assertEquals(1, summary.totalMatches());
        assertEquals(1, summary.wins());
        assertEquals(0, summary.losses());
        assertEquals(18, summary.totalMoves());
    }

    @Test
    void adminViewerShouldBeAbleToExportAndSwitchTargetUser() {
        GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        UserAccount target = new UserAccount();
        target.setId("u2");
        target.setDisplayName("Target User");

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("admin-1");
        when(session.getAttribute("AUTH_ROLE")).thenReturn("Admin");
        when(gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc("u2", "u2")).thenReturn(List.of());
        when(userAccountRepository.findById("u2")).thenReturn(java.util.Optional.of(target));
        when(userAccountRepository.findAllById(any())).thenReturn(List.of());

        HistoryController controller = new HistoryController(
            gameHistoryRepository,
            userAccountRepository,
            new GameCatalogService()
        );

        ConcurrentModel model = new ConcurrentModel();
        String viewName = controller.page("u2", null, null, null, null, 0, 20, request, model);

        assertEquals("history/index", viewName);
        assertEquals(Boolean.TRUE, model.getAttribute("canExportHistory"));
        assertEquals(Boolean.TRUE, model.getAttribute("canSwitchHistoryUser"));
        assertEquals("Target User", model.getAttribute("historyOwnerName"));

        @SuppressWarnings("unchecked")
        List<HistoryController.GameBreakdownView> breakdown =
            (List<HistoryController.GameBreakdownView>) model.getAttribute("historyGameBreakdown");
        assertTrue(breakdown.isEmpty());
    }
}
