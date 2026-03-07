package com.game.hub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.entity.Friendship;
import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAchievement;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.FriendshipRepository;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAchievementRepository;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSyncApiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private GameHistoryRepository gameHistoryRepository;
    @Autowired
    private UserAchievementRepository userAchievementRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void syncApiShouldRejectMissingApiKey() throws Exception {
        mockMvc.perform(post("/api/account-sync/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"blocked@test.com\",\"password\":\"Pass@123\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Invalid API key"));
    }

    @Test
    void syncApiShouldAllowPostmanStyleUpsertWithoutCsrfAndReturnSnapshot() throws Exception {
        UserAccount friend = new UserAccount();
        friend.setEmail("friend-sync@test.com");
        friend.setUsername("friend-sync@test.com");
        friend.setDisplayName("Friend Sync");
        friend.setPassword(passwordEncoder.encode("Pass@123"));
        friend.setEmailConfirmed(true);
        friend = userAccountRepository.save(friend);

        String createSeedPayload = """
            {
              "userId": "player-sync-seed",
              "email": "player-sync@test.com",
              "displayName": "Player Sync",
              "password": "Pass@123",
              "replaceRelatedData": true
            }
            """;

        String seedResponse = mockMvc.perform(post("/api/account-sync/accounts")
                .header("X-API-Key", "test-sync-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSeedPayload))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode seedJson = objectMapper.readTree(seedResponse);
        String userId = seedJson.path("data").path("account").path("userId").asText();

        String payload = """
            {
              "email": "player-sync@test.com",
              "displayName": "Player Sync",
              "password": "Pass@123",
              "emailConfirmed": true,
              "role": "User",
              "score": 120,
              "highestScore": 220,
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
                "chess": {
                  "whiteWins": 5,
                  "blackWins": 2,
                  "draws": 1
                },
                "xiangqi": {
                  "redWins": 3,
                  "blackWins": 1,
                  "draws": 0
                },
                "minesweeper": {
                  "totalGames": 10,
                  "wins": 7,
                  "losses": 3,
                  "bestTimes": {
                    "easy": 40
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
                ]
              },
              "puzzleCatalogState": {
                "favorites": ["sudoku", "jigsaw"],
                "ratings": {
                  "sudoku": 5,
                  "jigsaw": 3
                },
                "recentCodes": ["jigsaw", "sudoku"]
              },
              "achievements": [
                {
                  "achievementName": "Bac",
                  "achievedAt": "2026-03-08T09:00:00"
                }
              ],
              "gameHistory": [
                {
                  "gameCode": "caro",
                  "player1Id": "%s",
                  "player2Id": "%s",
                  "firstPlayerId": "%s",
                  "totalMoves": 18,
                  "winnerId": "%s",
                  "playedAt": "2026-03-08T10:00:00"
                }
              ],
              "friendships": [
                {
                  "requesterId": "%s",
                  "addresseeId": "%s",
                  "accepted": true
                }
              ],
              "replaceRelatedData": true
            }
            """.formatted(userId, friend.getId(), userId, userId, userId, friend.getId());

        String responseContent = mockMvc.perform(post("/api/account-sync/accounts")
                .header("X-API-Key", "test-sync-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.created").value(false))
            .andExpect(jsonPath("$.data.account.email").value("player-sync@test.com"))
            .andExpect(jsonPath("$.data.account.preferences.themeMode").value("dark"))
            .andExpect(jsonPath("$.data.account.gameStats['chess-offline'].whiteWins").value(5))
            .andExpect(jsonPath("$.data.account.gamesBrowserState.favorites[0]").value("caro"))
            .andExpect(jsonPath("$.data.account.gamesBrowserState.recentGames[0].code").value("chess"))
            .andExpect(jsonPath("$.data.account.puzzleCatalogState.favorites[0]").value("sudoku"))
            .andExpect(jsonPath("$.data.account.puzzleCatalogState.ratings.sudoku").value(5))
            .andExpect(jsonPath("$.data.account.friendships[0].addresseeId").value(friend.getId()))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(responseContent);
        String syncedUserId = json.path("data").path("account").path("userId").asText();

        UserAccount syncedUser = userAccountRepository.findById(syncedUserId).orElseThrow();
        assertEquals("player-sync@test.com", syncedUser.getEmail());
        assertTrue(passwordEncoder.matches("Pass@123", syncedUser.getPassword()));
        assertEquals("dark", syncedUser.getThemeMode());
        assertEquals("en", syncedUser.getLanguage());
        assertTrue(syncedUser.isSidebarDesktopVisibleByDefault());
        assertFalse(syncedUser.isSidebarMobileAutoClose());
        assertFalse(syncedUser.isHomeMusicEnabled());
        assertFalse(syncedUser.isShowOfflineFriendsInSidebar());
        assertEquals(10000, syncedUser.getFriendListRefreshMs());
        assertTrue(syncedUser.getChessOfflineStatsJson().contains("\"whiteWins\":5"));
        assertTrue(syncedUser.getGamesBrowserFavoritesJson().contains("\"caro\""));
        assertTrue(syncedUser.getGamesBrowserRecentJson().contains("\"code\":\"chess\""));
        assertTrue(syncedUser.getPuzzleCatalogFavoritesJson().contains("\"sudoku\""));
        assertTrue(syncedUser.getPuzzleCatalogRatingsJson().contains("\"sudoku\":5"));
        assertTrue(syncedUser.getPuzzleCatalogRecentJson().contains("\"jigsaw\""));

        UserAchievement achievement = userAchievementRepository.findByUserId(syncedUserId).stream().findFirst().orElseThrow();
        assertEquals("Bac", achievement.getAchievementName());

        GameHistory history = gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc(syncedUserId, syncedUserId)
            .stream().findFirst().orElseThrow();
        assertEquals("caro", history.getGameCode());
        assertEquals(friend.getId(), history.getPlayer2Id());

        Friendship friendship = friendshipRepository.findByRequesterIdOrAddresseeId(syncedUserId, syncedUserId)
            .stream().findFirst().orElseThrow();
        assertEquals(friend.getId(), friendship.getAddresseeId());
        assertTrue(friendship.isAccepted());

        mockMvc.perform(get("/api/account-sync/accounts/{userId}", syncedUserId)
                .header("X-API-Key", "test-sync-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(syncedUserId))
            .andExpect(jsonPath("$.data.gameStats['minesweeper'].bestTimes.easy").value(40))
            .andExpect(jsonPath("$.data.gamesBrowserState.recentGames[0].code").value("chess"))
            .andExpect(jsonPath("$.data.puzzleCatalogState.recentCodes[0]").value("jigsaw"));

        mockMvc.perform(get("/api/account-sync/accounts/by-email")
                .header("X-API-Key", "test-sync-key")
                .param("email", "player-sync@test.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("player-sync@test.com"))
            .andExpect(jsonPath("$.data.friendships[0].accepted").value(true));
    }

    @Test
    void syncApiShouldBulkImportAndExportAccounts() throws Exception {
        String payload = """
            {
              "continueOnError": false,
              "accounts": [
                {
                  "userId": "bulk-a",
                  "email": "bulk-a@test.com",
                  "displayName": "Bulk A",
                  "password": "Pass@123",
                  "gamesBrowserState": {
                    "favorites": ["caro"],
                    "recentGames": [
                      {
                        "code": "caro",
                        "name": "Caro",
                        "at": 1762290000000
                      }
                    ]
                  },
                  "puzzleCatalogState": {
                    "favorites": ["word"],
                    "ratings": {
                      "word": 4
                    },
                    "recentCodes": ["word"]
                  },
                  "gameStats": {
                    "chess": {
                      "whiteWins": 2,
                      "blackWins": 1,
                      "draws": 0
                    }
                  },
                  "replaceRelatedData": true
                },
                {
                  "userId": "bulk-b",
                  "email": "bulk-b@test.com",
                  "displayName": "Bulk B",
                  "password": "Pass@123",
                  "friendships": [
                    {
                      "requesterId": "bulk-b",
                      "addresseeId": "bulk-a",
                      "accepted": true
                    }
                  ],
                  "gameHistory": [
                    {
                      "gameCode": "caro",
                      "player1Id": "bulk-b",
                      "player2Id": "bulk-a",
                      "firstPlayerId": "bulk-b",
                      "totalMoves": 14,
                      "winnerId": "bulk-b",
                      "playedAt": "2026-03-08T12:00:00"
                    }
                  ],
                  "replaceRelatedData": true
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/account-sync/accounts/bulk")
                .header("X-API-Key", "test-sync-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.successCount").value(2))
            .andExpect(jsonPath("$.data.errorCount").value(0))
            .andExpect(jsonPath("$.data.results[0].account.userId").value("bulk-a"))
            .andExpect(jsonPath("$.data.results[0].account.gamesBrowserState.favorites[0]").value("caro"))
            .andExpect(jsonPath("$.data.results[0].account.puzzleCatalogState.favorites[0]").value("word"))
            .andExpect(jsonPath("$.data.results[1].account.friendships[0].addresseeId").value("bulk-a"));

        UserAccount bulkA = userAccountRepository.findById("bulk-a").orElseThrow();
        UserAccount bulkB = userAccountRepository.findById("bulk-b").orElseThrow();
        assertTrue(passwordEncoder.matches("Pass@123", bulkA.getPassword()));
        assertTrue(passwordEncoder.matches("Pass@123", bulkB.getPassword()));
        assertTrue(bulkA.getGamesBrowserFavoritesJson().contains("\"caro\""));
        assertTrue(bulkA.getPuzzleCatalogFavoritesJson().contains("\"word\""));

        Friendship friendship = friendshipRepository.findByRequesterIdOrAddresseeId("bulk-b", "bulk-b")
            .stream()
            .filter(link -> "bulk-a".equals(link.getAddresseeId()))
            .findFirst()
            .orElseThrow();
        assertTrue(friendship.isAccepted());

        GameHistory history = gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc("bulk-b", "bulk-b")
            .stream()
            .filter(link -> "bulk-a".equals(link.getPlayer2Id()))
            .findFirst()
            .orElseThrow();
        assertEquals("caro", history.getGameCode());

        mockMvc.perform(get("/api/account-sync/accounts/export")
                .header("X-API-Key", "test-sync-key")
                .param("userId", "bulk-a", "bulk-b"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.count").value(2))
            .andExpect(jsonPath("$.data.accounts[0].userId").value("bulk-a"))
            .andExpect(jsonPath("$.data.accounts[0].gamesBrowserState.favorites[0]").value("caro"))
            .andExpect(jsonPath("$.data.accounts[0].puzzleCatalogState.favorites[0]").value("word"))
            .andExpect(jsonPath("$.data.accounts[1].userId").value("bulk-b"));
    }

    @Test
    void syncApiShouldReplaceGamesBrowserStateByDefault() throws Exception {
        String firstPayload = """
            {
              "userId": "games-browser-default-replace",
              "email": "games-browser-default-replace@test.com",
              "displayName": "Games Browser Default Replace",
              "password": "Pass@123",
              "gamesBrowserState": {
                "favorites": ["caro"],
                "recentGames": [
                  {
                    "code": "caro",
                    "name": "Caro",
                    "at": 1762291200000
                  }
                ]
              }
            }
            """;

        mockMvc.perform(post("/api/account-sync/accounts")
                .header("X-API-Key", "test-sync-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        String secondPayload = """
            {
              "userId": "games-browser-default-replace",
              "email": "games-browser-default-replace@test.com",
              "displayName": "Games Browser Default Replace",
              "gamesBrowserState": {
                "favorites": ["chess"],
                "recentGames": [
                  {
                    "code": "chess",
                    "name": "Chess",
                    "at": 1762291300000
                  }
                ]
              }
            }
            """;

        String responseContent = mockMvc.perform(post("/api/account-sync/accounts")
                .header("X-API-Key", "test-sync-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(responseContent);
        JsonNode favorites = json.path("data").path("account").path("gamesBrowserState").path("favorites");
        JsonNode recentGames = json.path("data").path("account").path("gamesBrowserState").path("recentGames");

        assertEquals(1, favorites.size());
        assertEquals("chess", favorites.path(0).asText());
        assertEquals(1, recentGames.size());
        assertEquals("chess", recentGames.path(0).path("code").asText());

        UserAccount updated = userAccountRepository.findById("games-browser-default-replace").orElseThrow();
        assertFalse(updated.getGamesBrowserFavoritesJson().contains("caro"));
        assertFalse(updated.getGamesBrowserRecentJson().contains("\"code\":\"caro\""));
    }

    @Test
    void syncApiShouldReplacePuzzleCatalogStateByDefault() throws Exception {
        String firstPayload = """
            {
              "userId": "puzzle-default-replace",
              "email": "puzzle-default-replace@test.com",
              "displayName": "Puzzle Default Replace",
              "password": "Pass@123",
              "puzzleCatalogState": {
                "favorites": ["sudoku"],
                "ratings": {
                  "sudoku": 5
                },
                "recentCodes": ["sudoku"]
              }
            }
            """;

        mockMvc.perform(post("/api/account-sync/accounts")
                .header("X-API-Key", "test-sync-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        String secondPayload = """
            {
              "userId": "puzzle-default-replace",
              "email": "puzzle-default-replace@test.com",
              "displayName": "Puzzle Default Replace",
              "puzzleCatalogState": {
                "favorites": ["word"],
                "ratings": {
                  "word": 4
                },
                "recentCodes": ["word"]
              }
            }
            """;

        String responseContent = mockMvc.perform(post("/api/account-sync/accounts")
                .header("X-API-Key", "test-sync-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(responseContent);
        JsonNode favorites = json.path("data").path("account").path("puzzleCatalogState").path("favorites");
        JsonNode ratings = json.path("data").path("account").path("puzzleCatalogState").path("ratings");
        JsonNode recentCodes = json.path("data").path("account").path("puzzleCatalogState").path("recentCodes");

        assertEquals(1, favorites.size());
        assertEquals("word", favorites.path(0).asText());
        assertEquals(4, ratings.path("word").asInt());
        assertEquals(1, recentCodes.size());
        assertEquals("word", recentCodes.path(0).asText());

        UserAccount updated = userAccountRepository.findById("puzzle-default-replace").orElseThrow();
        assertFalse(updated.getPuzzleCatalogFavoritesJson().contains("sudoku"));
        assertFalse(updated.getPuzzleCatalogRatingsJson().contains("sudoku"));
        assertFalse(updated.getPuzzleCatalogRecentJson().contains("sudoku"));
    }
}
