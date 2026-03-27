package com.game.hub.config;

import com.game.hub.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2LoginSuccessHandlerTest {

    @Test
    void socialLinkCallbackShouldPersistProviderAndRedirectToSettings() throws Exception {
        AccountService accountService = mock(AccountService.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(accountService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/Game");
        request.getSession(true).setAttribute(OAuth2LoginSuccessHandler.SOCIAL_LINK_USER_ID, "u1");
        request.getSession().setAttribute(OAuth2LoginSuccessHandler.SOCIAL_LINK_PROVIDER, "google");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(accountService.linkOAuth2Provider(eq("u1"), argThat(req ->
            req != null
                && "google".equals(req.provider())
                && "google-123".equals(req.providerUserId())
                && "alice@example.com".equals(req.email())
                && "Alice".equals(req.displayName())
        ))).thenReturn(AccountService.ServiceResult.ok(Map.of("message", "Linked")));
        when(accountService.getSessionUserSummary("u1"))
            .thenReturn(AccountService.ServiceResult.ok(Map.of("userId", "u1", "role", "User")));

        handler.onAuthenticationSuccess(request, response, oauthToken());

        verify(accountService).linkOAuth2Provider(eq("u1"), argThat(req ->
            req != null
                && "google".equals(req.provider())
                && "google-123".equals(req.providerUserId())
        ));
        assertEquals("u1", request.getSession(false).getAttribute(RoleGuardInterceptor.AUTH_USER_ID));
        assertEquals("User", request.getSession(false).getAttribute(RoleGuardInterceptor.AUTH_ROLE));
        assertEquals("/Game/settings?socialLinked=google", response.getRedirectedUrl());
    }

    private OAuth2AuthenticationToken oauthToken() {
        OAuth2User principal = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of(
                "sub", "google-123",
                "email", "alice@example.com",
                "name", "Alice"
            ),
            "sub"
        );
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
