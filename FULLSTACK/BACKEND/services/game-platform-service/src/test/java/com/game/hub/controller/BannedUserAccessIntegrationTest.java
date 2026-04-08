package com.game.hub.controller;

import com.game.hub.config.RoleGuardInterceptor;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BannedUserAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Test
    void bannedAuthenticatedUserShouldBeRedirectedToBanNotificationPage() throws Exception {
        userAccountRepository.save(bannedUser("banned-page-user", "page-ban@test.com"));
        MockHttpSession session = session("banned-page-user", "User");

        mockMvc.perform(get("/settings").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/account/ban-notification-page?message=*"));
    }

    @Test
    void bannedAuthenticatedUserShouldReceiveForbiddenJsonForApiRequests() throws Exception {
        userAccountRepository.save(bannedUser("banned-api-user", "api-ban@test.com"));
        MockHttpSession session = session("banned-api-user", "User");

        mockMvc.perform(get("/account/session-user")
                .session(session)
                .accept(APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value(containsString("Account banned until")));
    }

    @Test
    void banNotificationPageShouldStillRenderNormally() throws Exception {
        userAccountRepository.save(bannedUser("banned-notice-user", "notice-ban@test.com"));
        MockHttpSession session = session("banned-notice-user", "User");

        mockMvc.perform(get("/account/ban-notification-page").session(session))
            .andExpect(status().isOk())
            .andExpect(view().name("account/ban-notification"))
            .andExpect(content().string(containsString("Thong bao khoa tai khoan")));
    }

    private UserAccount bannedUser(String id, String email) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(email);
        user.setDisplayName("Banned");
        user.setRole("User");
        user.setBannedUntil(LocalDateTime.now().plusHours(2));
        return user;
    }

    private MockHttpSession session(String userId, String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RoleGuardInterceptor.AUTH_USER_ID, userId);
        session.setAttribute(RoleGuardInterceptor.AUTH_ROLE, role);
        return session;
    }
}
