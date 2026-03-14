package com.game.hub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomePageRenderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homePageShouldRenderWithDiscoveryModel() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("home/index"))
            .andExpect(model().attributeExists(
                "games",
                "recommendedGames",
                "onlineGames",
                "quickPlayGames",
                "strategyGames",
                "freshGames",
                "posts"
            ));
    }

    @Test
    void gamesCatalogShouldStillRender() throws Exception {
        mockMvc.perform(get("/games"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/index"))
            .andExpect(model().attributeExists("games"));
    }

    @Test
    void caroDetailPageShouldRenderWithSharedCatalogModel() throws Exception {
        mockMvc.perform(get("/games/caro"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/caro"))
            .andExpect(model().attributeExists("game", "allGames"));
    }

    @Test
    void quizDetailPageShouldRenderWithSharedCatalogModel() throws Exception {
        mockMvc.perform(get("/games/quiz"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/quiz"))
            .andExpect(model().attributeExists("game", "allGames"));
    }

    @Test
    void typingDetailPageShouldRenderWithSharedCatalogModel() throws Exception {
        mockMvc.perform(get("/games/typing"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/typing"))
            .andExpect(model().attributeExists("game", "allGames"));
    }

    @Test
    void puzzleHubPageShouldRenderWithSharedCatalogModel() throws Exception {
        mockMvc.perform(get("/games/puzzle"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/puzzle/index"))
            .andExpect(model().attributeExists("game", "allGames"));
    }

    @Test
    void monopolyDetailPageShouldRenderWithSharedCatalogModel() throws Exception {
        mockMvc.perform(get("/games/monopoly"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/monopoly"))
            .andExpect(model().attributeExists("game", "allGames"));
    }

    @Test
    void onlineHubShouldRenderWithSelectedGameModel() throws Exception {
        mockMvc.perform(get("/online-hub"))
            .andExpect(status().isOk())
            .andExpect(view().name("online-hub/index"))
            .andExpect(model().attributeExists(
                "selectedGame",
                "selectedGameCode",
                "selectedGameName",
                "roomRows",
                "onlineSupportedNow"
            ));
    }

    @Test
    void dedicatedCaroRoomPageShouldRenderViaSharedRoomHub() throws Exception {
        mockMvc.perform(get("/games/caro/rooms"))
            .andExpect(status().isOk())
            .andExpect(view().name("forward:/online-hub?game=caro"))
            .andExpect(forwardedUrl("/online-hub?game=caro"));
    }

    @Test
    void dedicatedQuizRoomPageShouldRedirectToQuizLobbyPage() throws Exception {
        mockMvc.perform(get("/games/quiz/rooms"))
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:/games/quiz"));
    }

    @Test
    void dedicatedTypingRoomPageShouldRedirectToTypingLobbyPage() throws Exception {
        mockMvc.perform(get("/games/typing/rooms"))
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:/games/typing"));
    }

    @Test
    void dedicatedBlackjackRoomPageShouldRedirectToBlackjackLobbyPage() throws Exception {
        mockMvc.perform(get("/games/blackjack/rooms"))
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:/games/cards/blackjack"));
    }

    @Test
    void leaderboardPageShouldRender() throws Exception {
        mockMvc.perform(get("/leaderboard"))
            .andExpect(status().isOk())
            .andExpect(view().name("leaderboard/index"))
            .andExpect(model().attributeExists("players", "totalItems"));
    }

    @Test
    void chatBotPageShouldRender() throws Exception {
        mockMvc.perform(get("/chat-bot"))
            .andExpect(status().isOk())
            .andExpect(view().name("chat-bot/index"));
    }
}
