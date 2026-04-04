package com.game.hub.controller;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountGuestMigrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void sessionUserShouldReturnCurrentAccountSummary() throws Exception {
        UserAccount user = new UserAccount();
        user.setEmail("session-user@test.com");
        user.setUsername("session-user@test.com");
        user.setDisplayName("Session User");
        user.setAvatarPath("/uploads/avatars/session-user.png");
        user.setPassword(passwordEncoder.encode("Pass@123"));
        user.setEmailConfirmed(true);
        user.setRole("Admin");
        user.setScore(88);
        user = userAccountRepository.save(user);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("AUTH_USER_ID", user.getId());
        session.setAttribute("AUTH_ROLE", "Admin");

        mockMvc.perform(get("/account/session-user").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").value(user.getId()))
            .andExpect(jsonPath("$.data.displayName").value("Session User"))
            .andExpect(jsonPath("$.data.email").value("session-user@test.com"))
            .andExpect(jsonPath("$.data.role").value("Admin"))
            .andExpect(jsonPath("$.data.score").value(88))
            .andExpect(jsonPath("$.data.avatarPath").value("/uploads/avatars/session-user.png"));
    }

    @Test
    void migrateGuestDataShouldPersistPreferencesAndStatsForLoggedInUser() throws Exception {
        UserAccount user = new UserAccount();
        user.setEmail("guest-migrate@test.com");
        user.setUsername("guest-migrate@test.com");
        user.setDisplayName("Guest Migrate");
        user.setPassword(passwordEncoder.encode("Pass@123"));
        user.setEmailConfirmed(true);
        user = userAccountRepository.save(user);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("AUTH_USER_ID", user.getId());
        session.setAttribute("AUTH_ROLE", "User");

        String payload = """
            {
              "preferences": {
                "themeMode": "dark",
                "language": "en",
                "sidebarDesktopVisibleByDefault": true,
                "sidebarMobileAutoClose": false,
                "homeMusicEnabled": false,
                "toastNotificationsEnabled": true,
                "showOfflineFriendsInSidebar": false,
                "autoRefreshFriendList": true,
                "friendListRefreshMs": 10000
              },
              "gameStats": {
                "chess-offline": {
                  "whiteWins": 3,
                  "blackWins": 1,
                  "draws": 2
                },
                "minesweeper": {
                  "totalGames": 4,
                  "wins": 3,
                  "losses": 1,
                  "bestTimes": {
                    "easy": 44
                  }
                }
              },
              "gamesBrowserState": {
                "favorites": ["caro", "chess"],
                "recentGames": [
                  {
                    "code": "chess",
                    "name": "Chess",
                    "at": 1762291200000
                  },
                  {
                    "code": "caro",
                    "name": "Caro",
                    "at": 1762291000000
                  }
                ],
                "merge": true
              },
              "puzzleCatalogState": {
                "favorites": ["sudoku", "word"],
                "ratings": {
                  "sudoku": 5,
                  "word": 4
                },
                "recentCodes": ["word", "sudoku"],
                "merge": true
              }
            }
            """;

        mockMvc.perform(post("/account/migrate-guest-data")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.migratedPreferences").value(true))
            .andExpect(jsonPath("$.data.migratedGameStatsCount").value(2))
            .andExpect(jsonPath("$.data.migratedGamesBrowserState").value(true))
            .andExpect(jsonPath("$.data.migratedPuzzleCatalogState").value(true))
            .andExpect(jsonPath("$.data.preferences.themeMode").value("dark"))
            .andExpect(jsonPath("$.data.preferences.language").value("en"))
            .andExpect(jsonPath("$.data.gamesBrowserState.favorites[0]").value("caro"))
            .andExpect(jsonPath("$.data.gamesBrowserState.recentGames[0].code").value("chess"))
            .andExpect(jsonPath("$.data.puzzleCatalogState.favorites[0]").value("sudoku"))
            .andExpect(jsonPath("$.data.puzzleCatalogState.ratings.sudoku").value(5))
            .andExpect(jsonPath("$.data.puzzleCatalogState.recentCodes[0]").value("word"));

        UserAccount updated = userAccountRepository.findById(user.getId()).orElseThrow();
        assertEquals("dark", updated.getThemeMode());
        assertEquals("en", updated.getLanguage());
        assertTrue(updated.isSidebarDesktopVisibleByDefault());
        assertFalse(updated.isSidebarMobileAutoClose());
        assertFalse(updated.isHomeMusicEnabled());
        assertFalse(updated.isShowOfflineFriendsInSidebar());
        assertEquals(10000, updated.getFriendListRefreshMs());
        assertTrue(updated.getChessOfflineStatsJson().contains("\"whiteWins\":3"));
        assertTrue(updated.getMinesweeperStatsJson().contains("\"easy\":44"));
        assertTrue(updated.getGamesBrowserFavoritesJson().contains("\"caro\""));
        assertTrue(updated.getGamesBrowserRecentJson().contains("\"code\":\"chess\""));
        assertTrue(updated.getPuzzleCatalogFavoritesJson().contains("\"sudoku\""));
        assertTrue(updated.getPuzzleCatalogRatingsJson().contains("\"sudoku\":5"));
        assertTrue(updated.getPuzzleCatalogRecentJson().contains("\"word\""));
    }
}
