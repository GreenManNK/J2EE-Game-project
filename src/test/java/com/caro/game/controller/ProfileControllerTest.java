package com.caro.game.controller;

import com.caro.game.entity.UserAccount;
import com.caro.game.repository.UserAccountRepository;
import com.caro.game.service.ProfileStatsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileControllerTest {

    @Test
    void indexApiShouldReturnErrorWhenUserNotFound() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        when(userAccountRepository.findById("not-found")).thenReturn(Optional.empty());

        ProfileController controller = new ProfileController(userAccountRepository, profileStatsService);
        Map<String, Object> response = controller.indexApi("not-found", null);

        assertFalse((Boolean) response.get("success"));
        assertTrue(((String) response.get("error")).contains("User not found"));
    }

    @Test
    void editShouldRejectWhenSessionUserDoesNotMatchRequestUser() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u-session");

        ProfileController controller = new ProfileController(userAccountRepository, profileStatsService);
        Object result = controller.edit(new ProfileController.UpdateProfileRequest("u-other", "Name", null, null), request);

        assertTrue(result instanceof Map<?, ?>);
        assertFalse((Boolean) ((Map<?, ?>) result).get("success"));
        assertTrue(String.valueOf(((Map<?, ?>) result).get("error")).contains("mismatch"));
    }

    @Test
    void pageShouldForceReadOnlyWhenViewingAnotherUsersProfile() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        UserAccount target = new UserAccount();
        target.setId("u-target");

        when(userAccountRepository.findById("u-target")).thenReturn(Optional.of(target));
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u-viewer");

        Map<String, Object> spoofedProfile = new HashMap<>();
        spoofedProfile.put("user", target);
        spoofedProfile.put("isOwner", true);
        when(profileStatsService.buildProfileStats("u-target", "u-viewer")).thenReturn(spoofedProfile);

        ProfileController controller = new ProfileController(userAccountRepository, profileStatsService);
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.page("u-target", "u-target", request, model);

        assertEquals("profile/index", view);
        assertEquals(Boolean.FALSE, model.getAttribute("isOwner"));
        verify(profileStatsService).buildProfileStats("u-target", "u-viewer");
    }

    @Test
    void indexApiShouldForceReadOnlyWhenNoSessionEvenIfClientSpoofsViewerId() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);

        UserAccount target = new UserAccount();
        target.setId("u-target");

        when(userAccountRepository.findById("u-target")).thenReturn(Optional.of(target));

        Map<String, Object> spoofedProfile = new HashMap<>();
        spoofedProfile.put("user", target);
        spoofedProfile.put("isOwner", true);
        when(profileStatsService.buildProfileStats("u-target", "")).thenReturn(spoofedProfile);

        ProfileController controller = new ProfileController(userAccountRepository, profileStatsService);
        Map<String, Object> response = controller.indexApi("u-target", "u-target");

        assertEquals(Boolean.FALSE, response.get("isOwner"));
        verify(profileStatsService).buildProfileStats("u-target", "");
    }
}
