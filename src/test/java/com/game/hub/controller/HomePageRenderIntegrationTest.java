package com.game.hub.controller;

import com.game.hub.entity.Friendship;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.FriendshipRepository;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

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
            ))
            .andExpect(content().string(containsString("Trò chơi &amp; lịch sử")))
            .andExpect(content().string(containsString("Cài đặt tài khoản")))
            .andExpect(content().string(containsString("Hồ sơ & tài khoản")));
    }

    @Test
    void gamesCatalogShouldStillRender() throws Exception {
        mockMvc.perform(get("/games"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/index"))
            .andExpect(model().attributeExists("games"))
            .andExpect(content().string(containsString("/games/monopoly")))
            .andExpect(content().string(containsString("/games/cards/blackjack")))
            .andExpect(content().string(containsString("/images/games/home/quiz.")));
    }

    @Test
    void gamesCatalogShouldRenderForDiscoveryViews() throws Exception {
        mockMvc.perform(get("/games").param("view", "favorite"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/index"))
            .andExpect(model().attributeExists("games"));
    }

    @Test
    void loginPageShouldRenderSplitAuthSurface() throws Exception {
        mockMvc.perform(get("/account/login-page"))
            .andExpect(status().isOk())
            .andExpect(view().name("account/login"))
            .andExpect(content().string(containsString("Game Hub account")))
            .andExpect(content().string(containsString("Dang nhap de tiep tuc choi")))
            .andExpect(content().string(containsString("id=\"loginForm\"")));
    }

    @Test
    void registerPageShouldRenderSplitOnboardingSurface() throws Exception {
        mockMvc.perform(get("/account/register-page"))
            .andExpect(status().isOk())
            .andExpect(view().name("account/register"))
            .andExpect(content().string(containsString("Quy trinh 3 buoc")))
            .andExpect(content().string(containsString("Email va mat khau")))
            .andExpect(content().string(containsString("id=\"regForm\"")));
    }

    @Test
    void caroDetailPageShouldRenderWithSharedCatalogModel() throws Exception {
        mockMvc.perform(get("/games/caro"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/caro"))
            .andExpect(model().attributeExists("game", "allGames"))
            .andExpect(content().string(containsString("Gameplay rail /")))
            .andExpect(content().string(containsString("Lich su choi")));
    }

    @Test
    void chessDetailPageShouldRenderWithGameplayRail() throws Exception {
        mockMvc.perform(get("/games/chess"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/chess"))
            .andExpect(model().attributeExists("game", "allGames"))
            .andExpect(content().string(containsString("Gameplay rail /")));
    }

    @Test
    void xiangqiDetailPageShouldRenderWithGameplayRail() throws Exception {
        mockMvc.perform(get("/games/xiangqi"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/xiangqi"))
            .andExpect(model().attributeExists("game", "allGames"))
            .andExpect(content().string(containsString("Gameplay rail /")));
    }

    @Test
    void cardsDetailPageShouldRenderWithGameplayRail() throws Exception {
        mockMvc.perform(get("/games/cards"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/cards"))
            .andExpect(model().attributeExists("game", "allGames"))
            .andExpect(content().string(containsString("Gameplay rail /")));
    }

    @Test
    void minesweeperDetailPageShouldRenderWithGameplayRail() throws Exception {
        mockMvc.perform(get("/games/minesweeper"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/minesweeper"))
            .andExpect(model().attributeExists("game", "allGames"))
            .andExpect(content().string(containsString("Gameplay rail /")));
    }

    @Test
    void quizDetailPageShouldRenderWithSharedCatalogModel() throws Exception {
        mockMvc.perform(get("/games/quiz"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/quiz"))
            .andExpect(model().attributeExists("game", "allGames"))
            .andExpect(content().string(containsString("Gameplay rail /")))
            .andExpect(content().string(containsString("/images/games/home/quiz.")));
    }

    @Test
    void quizLocalAndBotPagesShouldRenderDedicatedPracticeModes() throws Exception {
        mockMvc.perform(get("/games/quiz/local"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/quiz-practice"))
            .andExpect(model().attribute("localPage", true))
            .andExpect(model().attribute("botPage", false));

        mockMvc.perform(get("/games/quiz/bot").param("difficulty", "hard"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/quiz-practice"))
            .andExpect(model().attribute("localPage", false))
            .andExpect(model().attribute("botPage", true))
            .andExpect(model().attribute("botDifficulty", "hard"));
    }

    @Test
    void typingDetailPageShouldRenderWithSharedCatalogModel() throws Exception {
        mockMvc.perform(get("/games/typing"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/typing"))
            .andExpect(model().attributeExists("game", "allGames"))
            .andExpect(content().string(containsString("Gameplay rail /")));
    }

    @Test
    void typingPracticeAndBotPagesShouldRenderDedicatedPracticeModes() throws Exception {
        mockMvc.perform(get("/games/typing/practice"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/typing-practice"))
            .andExpect(model().attribute("practicePage", true))
            .andExpect(model().attribute("botPage", false));

        mockMvc.perform(get("/games/typing/bot").param("difficulty", "hard"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/typing-practice"))
            .andExpect(model().attribute("botPage", true))
            .andExpect(model().attribute("botDifficulty", "hard"));
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
            .andExpect(model().attributeExists("game", "allGames"))
            .andExpect(content().string(containsString("Gameplay rail /")))
            .andExpect(content().string(containsString("/games/monopoly/bot?difficulty=easy")));
    }

    @Test
    void blackjackDetailPageShouldRenderWithGameplayRail() throws Exception {
        mockMvc.perform(get("/games/cards/blackjack"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/cards/blackjack"))
            .andExpect(content().string(containsString("Gameplay rail /")))
            .andExpect(content().string(containsString("Bang xep hang")));
    }

    @Test
    void blackjackLocalPageShouldRenderDedicatedLocalMode() throws Exception {
        mockMvc.perform(get("/games/cards/blackjack/local"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/cards/blackjack-local"))
            .andExpect(model().attribute("localPage", true));
    }

    @Test
    void monopolyLocalPageShouldRenderDedicatedLocalMode() throws Exception {
        mockMvc.perform(get("/games/monopoly/local"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/monopoly-local"))
            .andExpect(model().attribute("localPage", true))
            .andExpect(model().attribute("roomPage", false))
            .andExpect(model().attribute("botPage", false));
    }

    @Test
    void monopolyBotPageShouldRenderDedicatedBotMode() throws Exception {
        mockMvc.perform(get("/games/monopoly/bot").param("difficulty", "hard"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/monopoly-bot"))
            .andExpect(model().attribute("roomPage", false))
            .andExpect(model().attribute("localPage", false))
            .andExpect(model().attribute("botPage", true))
            .andExpect(model().attribute("botArenaPage", false))
            .andExpect(model().attribute("botDifficulty", "hard"))
            .andExpect(content().string(containsString("Sanh bot rieng")))
            .andExpect(content().string(not(containsString("Phong bot da mo"))));
    }

    @Test
    void monopolyBotArenaPageShouldRenderDedicatedArenaTemplate() throws Exception {
        mockMvc.perform(get("/games/monopoly/bot/arena")
                .param("difficulty", "hard")
                .param("playerName", "Tester")
                .param("playerCount", "3")
                .param("startingCash", "2000"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/monopoly-bot-arena"))
            .andExpect(model().attribute("roomPage", false))
            .andExpect(model().attribute("localPage", false))
            .andExpect(model().attribute("botPage", true))
            .andExpect(model().attribute("botArenaPage", true))
            .andExpect(model().attribute("botDifficulty", "hard"))
            .andExpect(content().string(containsString("Phong bot da mo")))
            .andExpect(content().string(not(containsString("Setup ban voi bot"))));
    }

    @Test
    void monopolyRoomPageShouldRenderDedicatedRoomMode() throws Exception {
        mockMvc.perform(get("/games/monopoly/room/MONO-1"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/monopoly-room"))
            .andExpect(model().attribute("defaultRoomId", "MONO-1"))
            .andExpect(model().attribute("roomPage", true))
            .andExpect(model().attribute("localPage", false))
            .andExpect(model().attribute("botPage", false))
            .andExpect(content().string(containsString("Phong Co ty phu online")))
            .andExpect(content().string(not(containsString("Sanh phong Co ty phu"))));
    }

    @Test
    void tienLenRoomPageShouldRenderDedicatedRoomTemplate() throws Exception {
        mockMvc.perform(get("/cards/tien-len/room/TL-1"))
            .andExpect(status().isOk())
            .andExpect(view().name("cards/tien-len"))
            .andExpect(model().attribute("defaultRoomId", "TL-1"))
            .andExpect(model().attribute("roomPage", true))
            .andExpect(content().string(containsString("Phong hien tai:")))
            .andExpect(content().string(not(containsString("Danh sach phong dang cho"))));
    }

    @Test
    void quizRoomPageShouldRenderDedicatedRoomTemplate() throws Exception {
        mockMvc.perform(get("/games/quiz/room/QUIZ-1"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/quiz-room"))
            .andExpect(model().attribute("defaultRoomId", "QUIZ-1"))
            .andExpect(model().attribute("roomPage", true))
            .andExpect(content().string(containsString("Quiz / Phong choi")))
            .andExpect(content().string(not(containsString("Quiz / Lobby"))));
    }

    @Test
    void typingRoomPageShouldRenderDedicatedRoomTemplate() throws Exception {
        mockMvc.perform(get("/games/typing/room/TYP-1"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/typing-room"))
            .andExpect(model().attribute("defaultRoomId", "TYP-1"))
            .andExpect(model().attribute("roomPage", true))
            .andExpect(content().string(containsString("Typing / Phong choi")))
            .andExpect(content().string(not(containsString("Typing / Lobby"))));
    }

    @Test
    void blackjackRoomPageShouldRenderDedicatedRoomTemplate() throws Exception {
        mockMvc.perform(get("/games/cards/blackjack/room/BJ-1"))
            .andExpect(status().isOk())
            .andExpect(view().name("games/cards/blackjack-room"))
            .andExpect(model().attribute("defaultRoomId", "BJ-1"))
            .andExpect(model().attribute("roomPage", true))
            .andExpect(content().string(containsString("Blackjack / Phong choi")))
            .andExpect(content().string(not(containsString("Blackjack / Lobby"))));
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
    void dedicatedCaroRoomPageShouldRedirectDirectlyToNativeRoomWhenRoomIdProvided() throws Exception {
        mockMvc.perform(get("/games/caro/rooms").param("roomId", "CARO-1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:/game/room/CARO-1"));
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
    void dedicatedMonopolyRoomPageShouldRedirectToMonopolyLobbyPage() throws Exception {
        mockMvc.perform(get("/games/monopoly/rooms"))
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:/games/monopoly"));
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

    @Test
    void profilePageShouldRenderWithUpdatedSurface() throws Exception {
        userAccountRepository.save(user("profile-user", "Profile User", "profile@example.com"));

        mockMvc.perform(get("/profile").param("userId", "profile-user").sessionAttr("AUTH_USER_ID", "profile-user"))
            .andExpect(status().isOk())
            .andExpect(view().name("profile/index"))
            .andExpect(model().attributeExists("user", "favoriteGameCount", "recentActivity", "memberDays", "practiceStatCards", "practiceProgressCount"));
    }

    @Test
    void friendshipPagesShouldRenderWithUpdatedSurface() throws Exception {
        userAccountRepository.save(user("social-user", "Social User", "social@example.com"));
        userAccountRepository.save(user("social-target", "Social Target", "target@example.com"));

        mockMvc.perform(get("/friendship").sessionAttr("AUTH_USER_ID", "social-user"))
            .andExpect(status().isOk())
            .andExpect(view().name("friendship/index"))
            .andExpect(model().attributeExists("friendViews", "pendingRequestViews", "sentRequestViews"));

        mockMvc.perform(get("/friendship/search").param("query", "Social").sessionAttr("AUTH_USER_ID", "social-user"))
            .andExpect(status().isOk())
            .andExpect(view().name("friendship/search"))
            .andExpect(model().attributeExists("query", "exactMatches", "similarMatches"));

        mockMvc.perform(get("/friendship/user-detail/social-target").sessionAttr("AUTH_USER_ID", "social-user"))
            .andExpect(status().isOk())
            .andExpect(view().name("friendship/user-detail"))
            .andExpect(model().attributeExists("user", "favoriteGameCount", "recentActivity", "rank"));

        mockMvc.perform(get("/friendship/notifications").sessionAttr("AUTH_USER_ID", "social-user"))
            .andExpect(status().isOk())
            .andExpect(view().name("friendship/notifications"))
            .andExpect(model().attributeExists("friendRequestViews", "achievementNotifications", "systemNotifications"));
    }

    @Test
    void settingsAndPrivateChatPagesShouldRenderWithUpdatedSurface() throws Exception {
        userAccountRepository.save(user("settings-user", "Settings User", "settings@example.com"));
        userAccountRepository.save(user("chat-friend", "Chat Friend", "chat-friend@example.com"));

        Friendship friendship = new Friendship();
        friendship.setRequesterId("settings-user");
        friendship.setAddresseeId("chat-friend");
        friendship.setAccepted(true);
        friendshipRepository.save(friendship);

        mockMvc.perform(get("/settings").sessionAttr("AUTH_USER_ID", "settings-user"))
            .andExpect(status().isOk())
            .andExpect(view().name("settings/index"))
            .andExpect(model().attributeExists("isAuthenticated", "settingsUser", "googleLinked", "facebookLinked"));

        mockMvc.perform(get("/chat/private")
                .param("currentUserId", "settings-user")
                .param("friendId", "chat-friend")
                .sessionAttr("AUTH_USER_ID", "settings-user"))
            .andExpect(status().isOk())
            .andExpect(view().name("chat/private"))
            .andExpect(model().attributeExists("success", "currentUserName", "friendName", "roomKey", "messages"))
            .andExpect(content().string(containsString("/vendor/sockjs.min.js")))
            .andExpect(content().string(containsString("/vendor/stomp.umd.min.js")));
    }

    private UserAccount user(String id, String displayName, String email) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setDisplayName(displayName);
        user.setEmail(email);
        return user;
    }
}
