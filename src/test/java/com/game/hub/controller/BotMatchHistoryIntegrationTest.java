package com.game.hub.controller;

import com.game.hub.config.RoleGuardInterceptor;
import com.game.hub.entity.GameHistory;
import com.game.hub.repository.GameHistoryRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BotMatchHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GameHistoryRepository gameHistoryRepository;

    @BeforeEach
    void setUp() {
        gameHistoryRepository.deleteAll();
    }

    @Test
    void shouldPersistBotMatchForAuthenticatedUserAndSkipDuplicates() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RoleGuardInterceptor.AUTH_USER_ID, "bot-player-1");
        session.setAttribute(RoleGuardInterceptor.AUTH_ROLE, "User");

        String body = """
            {
              "gameCode": "chess",
              "difficulty": "hard",
              "outcome": "loss",
              "totalMoves": 32,
              "firstPlayerRole": "player",
              "matchCode": "BOT-CHESS-CASE-1"
            }
            """;

        mockMvc.perform(post("/history/api/bot-match")
                .session(session)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.recorded").value(true))
            .andExpect(jsonPath("$.data.matchCode").value("BOT-CHESS-CASE-1"));

        mockMvc.perform(post("/history/api/bot-match")
                .session(session)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.recorded").value(false))
            .andExpect(jsonPath("$.data.matchCode").value("BOT-CHESS-CASE-1"));

        assertEquals(1, gameHistoryRepository.findAll().size());
        GameHistory history = gameHistoryRepository.findAll().get(0);
        assertEquals("chess-bot", history.getGameCode());
        assertEquals("BOT-CHESS-CASE-1", history.getMatchCode());
        assertEquals("bot-player-1", history.getPlayer1Id());
        assertEquals("bot-chess-hard", history.getPlayer2Id());
        assertEquals("bot-chess-hard", history.getWinnerId());
        assertEquals("bot-player-1", history.getFirstPlayerId());
        assertEquals("/chess/bot?difficulty=hard", history.getLocationPath());
        assertEquals("Bot Co vua Hard", history.getLocationLabel());
        assertNull(history.getRoomId());
    }

    @Test
    void shouldCreateGuestSessionAndPersistGuestBotMatchWhenAnonymous() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/history/api/bot-match")
                .session(session)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "gameCode": "caro",
                      "difficulty": "easy",
                      "outcome": "win",
                      "totalMoves": 11,
                      "firstPlayerRole": "player",
                      "matchCode": "BOT-CARO-CASE-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.recorded").value(true))
            .andExpect(jsonPath("$.data.userId").value(org.hamcrest.Matchers.startsWith("guest-")));

        HttpSession httpSession = session;
        Object guestUserId = httpSession.getAttribute("GUEST_USER_ID");
        assertNotNull(guestUserId);
        assertTrue(String.valueOf(guestUserId).startsWith("guest-"));

        GameHistory history = gameHistoryRepository.findAll().get(0);
        assertEquals(String.valueOf(guestUserId), history.getPlayer1Id());
        assertEquals("bot-caro-easy", history.getPlayer2Id());
        assertEquals(String.valueOf(guestUserId), history.getWinnerId());
        assertEquals("caro-bot", history.getGameCode());
    }

    @Test
    void shouldPersistMonopolyBotMatchWithNativeRouteMetadata() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RoleGuardInterceptor.AUTH_USER_ID, "mono-player-1");
        session.setAttribute(RoleGuardInterceptor.AUTH_ROLE, "User");

        mockMvc.perform(post("/history/api/bot-match")
                .session(session)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "gameCode": "monopoly",
                      "difficulty": "hard",
                      "outcome": "win",
                      "totalMoves": 27,
                      "firstPlayerRole": "player",
                      "matchCode": "BOT-MONOPOLY-CASE-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.recorded").value(true))
            .andExpect(jsonPath("$.data.matchCode").value("BOT-MONOPOLY-CASE-1"));

        GameHistory history = gameHistoryRepository.findAll().get(0);
        assertEquals("monopoly-bot", history.getGameCode());
        assertEquals("BOT-MONOPOLY-CASE-1", history.getMatchCode());
        assertEquals("mono-player-1", history.getPlayer1Id());
        assertEquals("bot-monopoly-hard", history.getPlayer2Id());
        assertEquals("mono-player-1", history.getWinnerId());
        assertEquals("/games/monopoly/bot?difficulty=hard", history.getLocationPath());
        assertEquals("Ban Monopoly voi bot Hard", history.getLocationLabel());
    }

    @Test
    void shouldPersistQuizAndTypingBotMatchesWithPracticeRouteMetadata() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RoleGuardInterceptor.AUTH_USER_ID, "practice-player-1");
        session.setAttribute(RoleGuardInterceptor.AUTH_ROLE, "User");

        mockMvc.perform(post("/history/api/bot-match")
                .session(session)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "gameCode": "quiz",
                      "difficulty": "hard",
                      "outcome": "win",
                      "totalMoves": 8,
                      "firstPlayerRole": "player",
                      "matchCode": "BOT-QUIZ-CASE-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.recorded").value(true));

        mockMvc.perform(post("/history/api/bot-match")
                .session(session)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "gameCode": "typing",
                      "difficulty": "easy",
                      "outcome": "loss",
                      "totalMoves": 120,
                      "firstPlayerRole": "player",
                      "matchCode": "BOT-TYPING-CASE-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.recorded").value(true));

        assertEquals(2, gameHistoryRepository.findAll().size());
        GameHistory quizHistory = gameHistoryRepository.findAll().stream()
            .filter(item -> "quiz-bot".equals(item.getGameCode()))
            .findFirst()
            .orElseThrow();
        assertEquals("/games/quiz/bot?difficulty=hard", quizHistory.getLocationPath());
        assertEquals("Quiz voi bot Hard", quizHistory.getLocationLabel());

        GameHistory typingHistory = gameHistoryRepository.findAll().stream()
            .filter(item -> "typing-bot".equals(item.getGameCode()))
            .findFirst()
            .orElseThrow();
        assertEquals("/games/typing/bot?difficulty=easy", typingHistory.getLocationPath());
        assertEquals("Typing Battle voi bot Easy", typingHistory.getLocationLabel());
    }
}
