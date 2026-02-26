package com.game.hub.games.cards.tienlen.controller;

import com.game.hub.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TienLenControllerTest {

    @Test
    void shouldRenderTienLenPageAndCreateGuestSessionWhenAnonymous() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(null);
        when(request.getSession(true)).thenReturn(session);
        when(session.getAttribute("GUEST_USER_ID")).thenReturn(null);

        TienLenController controller = new TienLenController(userRepo);
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.tienLen("room-a", request, model);

        assertEquals("cards/tien-len", view);
        assertEquals("room-a", model.getAttribute("defaultRoomId"));
        assertTrue(String.valueOf(model.getAttribute("sessionUserId")).startsWith("guest-"));
        assertTrue(String.valueOf(model.getAttribute("sessionDisplayName")).startsWith("Guest "));
        verify(session).setAttribute(anyString(), org.mockito.ArgumentMatchers.any(String.class));
    }

    @Test
    void shouldRenderTienLenBotPageWithDifficulty() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(null);
        when(request.getSession(true)).thenReturn(session);
        when(session.getAttribute("GUEST_USER_ID")).thenReturn("guest-abcd");

        TienLenController controller = new TienLenController(userRepo);
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.tienLenBot("hard", request, model);

        assertEquals("cards/tien-len-bot", view);
        assertEquals("hard", model.getAttribute("botDifficulty"));
        assertEquals("guest-abcd", model.getAttribute("sessionUserId"));
        assertTrue(String.valueOf(model.getAttribute("pageHeading")).contains("HARD"));
    }
}
