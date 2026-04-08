package com.game.hub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuestOnlineAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousUserCanOpenLobby() throws Exception {
        mockMvc.perform(get("/lobby"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/games/caro/rooms"));

        mockMvc.perform(get("/online-hub").param("game", "caro"))
            .andExpect(status().isOk());
    }

    @Test
    void anonymousUserCanOpenGameAndReceiveGuestSessionIdentity() throws Exception {
        mockMvc.perform(get("/game").param("roomId", "guest-room"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/game/room/guest-room"))
            .andReturn();

        MvcResult roomPageResult = mockMvc.perform(get("/game/room/guest-room"))
            .andExpect(status().isOk())
            .andExpect(view().name("game/index"))
            .andReturn();

        MockHttpSession session = (MockHttpSession) roomPageResult.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session);

        Object guestUserId = session.getAttribute("GUEST_USER_ID");
        org.junit.jupiter.api.Assertions.assertNotNull(guestUserId);
        org.junit.jupiter.api.Assertions.assertTrue(String.valueOf(guestUserId).startsWith("guest-"));

        mockMvc.perform(get("/game/api").param("roomId", "guest-room").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionUserId", startsWith("guest-")))
            .andExpect(jsonPath("$.sessionDisplayName", startsWith("Guest ")));
    }
}
