package com.caro.game.controller;

import com.caro.game.repository.AchievementNotificationRepository;
import com.caro.game.repository.SystemNotificationRepository;
import com.caro.game.repository.UserAccountRepository;
import com.caro.game.service.FriendshipService;
import com.caro.game.service.ProfileStatsService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

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
}
