package com.game.hub.controller;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.ProfileStatsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileControllerTest {

    @Test
    void pageShouldReturnProfileView() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ProfileStatsService profileStatsService = mock(ProfileStatsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        UserAccount user = new UserAccount();
        user.setId("test-user");

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("test-user");
        when(userAccountRepository.findById("test-user")).thenReturn(Optional.of(user));
        when(profileStatsService.buildProfileStats("test-user", "test-user")).thenReturn(new HashMap<>());

        ProfileController controller = new ProfileController(userAccountRepository, profileStatsService);
        ConcurrentModel model = new ConcurrentModel();
        String viewName = controller.page("test-user", model, request);

        assertEquals("profile/index", viewName);
        verify(profileStatsService).buildProfileStats("test-user", "test-user");
    }
}
