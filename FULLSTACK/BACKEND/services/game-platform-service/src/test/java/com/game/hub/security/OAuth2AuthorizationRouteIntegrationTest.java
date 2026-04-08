package com.game.hub.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-google-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
    "spring.security.oauth2.client.registration.facebook.client-id=__disabled__",
    "spring.security.oauth2.client.registration.facebook.client-secret=__disabled__"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuth2AuthorizationRouteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void disabledProviderShouldNotExposeAuthorizationRedirectRoute() throws Exception {
        mockMvc.perform(get("/account/login-page"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Dang nhap voi Google")))
            .andExpect(content().string(containsString("Facebook chua cau hinh")));

        mockMvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("accounts.google.com/o/oauth2/v2/auth")))
            .andExpect(header().string("Location", containsString("client_id=test-google-client")));

        mockMvc.perform(get("/oauth2/authorization/facebook"))
            .andExpect(status().isNotFound());
    }
}
