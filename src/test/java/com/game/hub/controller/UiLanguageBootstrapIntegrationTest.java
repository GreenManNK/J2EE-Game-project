package com.game.hub.controller;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UiLanguageBootstrapIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Test
    void authenticatedUserPreferenceShouldBeExposedToAppHead() throws Exception {
        UserAccount user = new UserAccount();
        user.setId("lang-user");
        user.setEmail("lang@test.com");
        user.setUsername("lang@test.com");
        user.setDisplayName("Language User");
        user.setLanguage("en");
        userAccountRepository.save(user);

        mockMvc.perform(get("/settings").sessionAttr("AUTH_USER_ID", "lang-user"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"app-language\" content=\"en\"")))
            .andExpect(content().string(containsString("name=\"app-language-pinned\" content=\"true\"")));
    }

    @Test
    void acceptLanguageHeaderShouldBootstrapGuestLanguageWhenNoUserPreferenceExists() throws Exception {
        mockMvc.perform(get("/").header("Accept-Language", "en-US,en;q=0.9"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"app-language\" content=\"en\"")))
            .andExpect(content().string(containsString("name=\"app-language-pinned\" content=\"false\"")));
    }
}
