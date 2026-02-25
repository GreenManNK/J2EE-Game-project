package com.caro.game.controller;

import com.caro.game.entity.Friendship;
import com.caro.game.entity.UserAccount;
import com.caro.game.repository.AchievementNotificationRepository;
import com.caro.game.repository.SystemNotificationRepository;
import com.caro.game.repository.UserAccountRepository;
import com.caro.game.service.FriendshipService;
import com.caro.game.service.ProfileStatsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FriendshipControllerTest {

    @Test
    void notificationsShouldFallbackWhenCurrentUserDoesNotExist() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);

        when(userAccountRepository.findById("missing-user")).thenReturn(Optional.empty());
        when(userAccountRepository.findAll()).thenReturn(List.of());
        when(systemNotificationRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        Map<String, Object> response = controller.notifications("missing-user");

        assertEquals(List.of(), response.get("friendRequests"));
        assertEquals(List.of(), response.get("achievementNotifications"));
        assertEquals(List.of(), response.get("systemNotifications"));
    }

    @Test
    void userDetailShouldReturnErrorWhenTargetUserNotFound() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);

        when(userAccountRepository.findById("missing-id")).thenReturn(Optional.empty());

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        Map<String, Object> response = controller.userDetail("missing-id", "any-user");

        assertFalse((Boolean) response.get("success"));
        assertTrue(((String) response.get("error")).contains("User not found"));
    }

    @Test
    void sendRequestShouldRequireLoginSession() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        Map<String, Object> response = controller.sendRequest(
            new FriendshipController.SendByEmailRequest("u1", "a@b.com"),
            request
        );

        assertFalse((Boolean) response.get("success"));
        assertTrue(String.valueOf(response.get("error")).contains("Login"));
    }

    @Test
    void acceptShouldReturnAcceptedFriendViewWhenSuccess() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");

        Friendship pending = new Friendship();
        pending.setId(77L);
        pending.setRequesterId("u2");
        pending.setAddresseeId("u1");
        pending.setAccepted(false);

        when(friendshipService.getPendingRequests("u1")).thenReturn(List.of(pending));
        when(friendshipService.acceptRequest(77L, "u1")).thenReturn(true);

        UserAccount requester = user("u2", "Requester Name", "u2@example.com", "/uploads/avatars/u2.jpg", 222, true);
        when(userAccountRepository.findById("u2")).thenReturn(Optional.of(requester));

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        Map<String, Object> response = controller.accept(new FriendshipController.FriendshipActionRequest(77L), request);

        assertTrue((Boolean) response.get("success"));
        Object acceptedFriend = response.get("acceptedFriend");
        assertTrue(acceptedFriend instanceof FriendshipController.FriendView);
        FriendshipController.FriendView friendView = (FriendshipController.FriendView) acceptedFriend;
        assertEquals("u2", friendView.userId());
        assertEquals("Requester Name", friendView.displayName());
        assertEquals(222, friendView.score());
        assertTrue(friendView.online());
    }

    @Test
    @SuppressWarnings("unchecked")
    void indexShouldIncludeEnrichedViewsForFriendshipLists() {
        FriendshipService friendshipService = mock(FriendshipService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementNotificationRepository achievementNotificationRepository = mock(AchievementNotificationRepository.class);
        SystemNotificationRepository systemNotificationRepository = mock(SystemNotificationRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getSession(false)).thenReturn(null);

        UserAccount current = user("u1", "Current User", "u1@example.com", "/uploads/avatars/u1.jpg", 10, true);
        UserAccount friend = user("u2", "Friend User", "u2@example.com", "/uploads/avatars/u2.jpg", 120, false);
        UserAccount requester = user("u3", "Pending User", "u3@example.com", "/uploads/avatars/u3.jpg", 90, true);
        UserAccount addressee = user("u4", "Sent User", "u4@example.com", "", 70, false);

        Friendship pending = new Friendship();
        pending.setId(11L);
        pending.setRequesterId("u3");
        pending.setAddresseeId("u1");

        Friendship sent = new Friendship();
        sent.setId(12L);
        sent.setRequesterId("u1");
        sent.setAddresseeId("u4");

        when(userAccountRepository.findById("u1")).thenReturn(Optional.of(current));
        when(friendshipService.getFriends("u1")).thenReturn(List.of(friend));
        when(friendshipService.getPendingRequests("u1")).thenReturn(List.of(pending));
        when(friendshipService.getSentRequests("u1")).thenReturn(List.of(sent));

        Map<String, UserAccount> usersById = new HashMap<>();
        usersById.put("u3", requester);
        usersById.put("u4", addressee);
        when(userAccountRepository.findAllById(ArgumentMatchers.any())).thenAnswer(invocation -> {
            Iterable<String> ids = invocation.getArgument(0);
            List<UserAccount> found = new ArrayList<>();
            for (String id : ids) {
                UserAccount u = usersById.get(id);
                if (u != null) {
                    found.add(u);
                }
            }
            return found;
        });

        FriendshipController controller = new FriendshipController(
            friendshipService,
            userAccountRepository,
            achievementNotificationRepository,
            systemNotificationRepository,
            profileStatsService
        );

        Map<String, Object> response = controller.index("u1", request);

        List<FriendshipController.FriendView> friendViews =
            (List<FriendshipController.FriendView>) response.get("friendViews");
        List<FriendshipController.FriendRequestView> pendingRequestViews =
            (List<FriendshipController.FriendRequestView>) response.get("pendingRequestViews");
        List<FriendshipController.SentRequestView> sentRequestViews =
            (List<FriendshipController.SentRequestView>) response.get("sentRequestViews");

        assertEquals(1, friendViews.size());
        assertEquals("u2", friendViews.get(0).userId());
        assertEquals("Friend User", friendViews.get(0).displayName());

        assertEquals(1, pendingRequestViews.size());
        assertEquals(11L, pendingRequestViews.get(0).friendshipId());
        assertEquals("Pending User", pendingRequestViews.get(0).requesterName());

        assertEquals(1, sentRequestViews.size());
        assertEquals(12L, sentRequestViews.get(0).friendshipId());
        assertEquals("u4", sentRequestViews.get(0).addresseeId());
        assertEquals("Sent User", sentRequestViews.get(0).addresseeName());

        assertTrue(response.containsKey("friends"));
        assertTrue(response.containsKey("pendingRequests"));
        assertTrue(response.containsKey("sentRequests"));
    }

    private UserAccount user(String id, String displayName, String email, String avatarPath, int score, boolean online) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setAvatarPath(avatarPath);
        user.setScore(score);
        user.setOnline(online);
        return user;
    }
}
