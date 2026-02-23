package com.caro.game.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityCsrfIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postWithoutCsrfTokenShouldBeRejected() throws Exception {
        mockMvc.perform(post("/account/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"u1\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void postWithCsrfTokenShouldPassCsrfFilter() throws Exception {
        mockMvc.perform(post("/account/logout")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"u1\"}"))
            .andExpect(status().isOk());
    }
}
