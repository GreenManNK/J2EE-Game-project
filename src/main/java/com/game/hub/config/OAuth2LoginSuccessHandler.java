package com.game.hub.config;

import com.game.hub.service.AccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    public static final String SOCIAL_LINK_USER_ID = "SOCIAL_LINK_USER_ID";
    public static final String SOCIAL_LINK_PROVIDER = "SOCIAL_LINK_PROVIDER";

    private final AccountService accountService;

    public OAuth2LoginSuccessHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            redirectToLoginWithError(request, response, "Unsupported social authentication");
            return;
        }

        AccountService.OAuth2LoginRequest oauthRequest = extractRequest(oauthToken);
        HttpSession session = request.getSession(true);
        String socialLinkUserId = asString(session.getAttribute(SOCIAL_LINK_USER_ID));
        String socialLinkProvider = asString(session.getAttribute(SOCIAL_LINK_PROVIDER));
        clearSocialLinkState(session);

        if (socialLinkUserId != null) {
            if (socialLinkProvider != null && oauthRequest.provider() != null
                && !socialLinkProvider.equalsIgnoreCase(oauthRequest.provider())) {
                redirectToSettingsWithError(request, response, "OAuth provider mismatch for social linking");
                return;
            }

            AccountService.ServiceResult linkResult = accountService.linkOAuth2Provider(socialLinkUserId, oauthRequest);
            if (!linkResult.success()) {
                redirectToSettingsWithError(request, response, linkResult.error());
                return;
            }

            AccountService.ServiceResult summaryResult = accountService.getSessionUserSummary(socialLinkUserId);
            if (!summaryResult.success() || !(summaryResult.data() instanceof Map<?, ?> summary)) {
                redirectToSettingsWithError(request, response, summaryResult.error());
                return;
            }

            String role = summary.get("role") == null ? "User" : String.valueOf(summary.get("role"));
            session.setAttribute(RoleGuardInterceptor.AUTH_USER_ID, socialLinkUserId);
            session.setAttribute(RoleGuardInterceptor.AUTH_ROLE, role);
            putRoleAuthentication(socialLinkUserId, role, request, session);

            String provider = oauthRequest.provider() == null ? "social" : oauthRequest.provider().toLowerCase(Locale.ROOT);
            response.sendRedirect(settingsPageUrl(request) + "?socialLinked=" + URLEncoder.encode(provider, StandardCharsets.UTF_8));
            return;
        }

        AccountService.ServiceResult result = accountService.loginWithOAuth2(oauthRequest);
        if (!result.success() || !(result.data() instanceof Map<?, ?> data)) {
            redirectToLoginWithError(request, response, result.error());
            return;
        }

        Object userId = data.get("userId");
        if (!(userId instanceof String uid) || uid.isBlank()) {
            redirectToLoginWithError(request, response, "Cannot resolve user account");
            return;
        }

        String role = data.get("role") == null ? "User" : String.valueOf(data.get("role"));
        session.setAttribute(RoleGuardInterceptor.AUTH_USER_ID, uid);
        session.setAttribute(RoleGuardInterceptor.AUTH_ROLE, role);
        putRoleAuthentication(uid, role, request, session);

        response.sendRedirect(oauth2SuccessPageUrl(request));
    }

    private AccountService.OAuth2LoginRequest extractRequest(OAuth2AuthenticationToken token) {
        String provider = trimToNull(token.getAuthorizedClientRegistrationId());
        Map<String, Object> attributes = token.getPrincipal() == null ? Map.of() : token.getPrincipal().getAttributes();

        String providerUserId = firstNonBlank(
            asString(attributes.get("sub")),
            asString(attributes.get("id")),
            trimToNull(token.getName())
        );
        String email = asString(attributes.get("email"));
        String displayName = firstNonBlank(
            asString(attributes.get("name")),
            asString(attributes.get("given_name"))
        );

        return new AccountService.OAuth2LoginRequest(provider, providerUserId, email, displayName);
    }

    private void redirectToLoginWithError(HttpServletRequest request,
                                          HttpServletResponse response,
                                          String message) throws IOException {
        String encoded = URLEncoder.encode(
            trimToNull(message) == null ? "Social login failed. Please try again." : message.trim(),
            StandardCharsets.UTF_8
        );
        response.sendRedirect(loginPageUrl(request) + "?socialError=" + encoded);
    }

    private String oauth2SuccessPageUrl(HttpServletRequest request) {
        String contextPath = request == null ? null : request.getContextPath();
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "/account/oauth2-success";
        }
        return contextPath + "/account/oauth2-success";
    }

    private void redirectToSettingsWithError(HttpServletRequest request,
                                             HttpServletResponse response,
                                             String message) throws IOException {
        String encoded = URLEncoder.encode(
            trimToNull(message) == null ? "Social link failed. Please try again." : message.trim(),
            StandardCharsets.UTF_8
        );
        response.sendRedirect(settingsPageUrl(request) + "?socialError=" + encoded);
    }

    private String loginPageUrl(HttpServletRequest request) {
        String contextPath = request == null ? null : request.getContextPath();
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "/account/login-page";
        }
        return contextPath + "/account/login-page";
    }

    private String settingsPageUrl(HttpServletRequest request) {
        String contextPath = request == null ? null : request.getContextPath();
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "/settings";
        }
        return contextPath + "/settings";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String asString(Object value) {
        String text = value == null ? null : String.valueOf(value);
        return trimToNull(text);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void clearSocialLinkState(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute(SOCIAL_LINK_USER_ID);
        session.removeAttribute(SOCIAL_LINK_PROVIDER);
    }

    private void putRoleAuthentication(String userId, String role, HttpServletRequest request, HttpSession session) {
        String authority = "ROLE_" + (role == null ? "USER" : role.trim().toUpperCase(Locale.ROOT));
        UsernamePasswordAuthenticationToken appAuth = new UsernamePasswordAuthenticationToken(
            userId,
            null,
            List.of(new SimpleGrantedAuthority(authority))
        );
        appAuth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(appAuth);
        SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
