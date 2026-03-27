package com.game.hub.controller;

import com.game.hub.repository.UserAccountRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class AccountPageControllerTest {

    @Test
    void startSocialLinkShouldRequireAuthenticatedUser() {
        AccountPageController controller = new AccountPageController(
            mock(UserAccountRepository.class),
            "google-client",
            "facebook-client"
        );

        String viewName = controller.startSocialLink("google", new MockHttpServletRequest());

        assertEquals("redirect:/account/login-page?socialError=Login+required", viewName);
    }

    @Test
    void startSocialLinkShouldStoreLinkStateAndRedirectToOAuthProvider() {
        AccountPageController controller = new AccountPageController(
            mock(UserAccountRepository.class),
            "google-client",
            "facebook-client"
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        session.setAttribute("AUTH_USER_ID", "u1");

        String viewName = controller.startSocialLink("google", request);

        assertEquals("redirect:/oauth2/authorization/google", viewName);
        assertEquals("u1", session.getAttribute("SOCIAL_LINK_USER_ID"));
        assertEquals("google", session.getAttribute("SOCIAL_LINK_PROVIDER"));
    }

    @Test
    void startSocialLinkShouldRejectUnsupportedProvider() {
        AccountPageController controller = new AccountPageController(
            mock(UserAccountRepository.class),
            "google-client",
            "facebook-client"
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("AUTH_USER_ID", "u1");

        String viewName = controller.startSocialLink("github", request);

        assertEquals("redirect:/settings?socialError=Unsupported+social+provider", viewName);
        assertNull(request.getSession(false).getAttribute("SOCIAL_LINK_USER_ID"));
    }
}
