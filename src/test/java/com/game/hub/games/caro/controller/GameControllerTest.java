package com.game.hub.games.caro.controller;

import com.game.hub.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameControllerTest {

    @Test
    void indexShouldAllowGuestWhenNoSessionUser() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(null);
        when(request.getSession(true)).thenReturn(session);
        when(session.getAttribute("GUEST_USER_ID")).thenReturn(null);

        GameController controller = new GameController(userAccountRepository);
        Model model = new ConcurrentModel();

        String view = controller.index("room1", null, request, model);

        assertEquals("redirect:/game/room/room1", view);
    }

    @Test
    void roomPageShouldAllowGuestWhenNoSessionUser() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(null);
        when(request.getSession(true)).thenReturn(session);
        when(session.getAttribute("GUEST_USER_ID")).thenReturn(null);

        GameController controller = new GameController(userAccountRepository);
        Model model = new ConcurrentModel();

        String view = controller.roomPage("room1", null, request, model);

        assertEquals("game/index", view);
        assertEquals("room1", model.getAttribute("roomId"));
        assertTrue(String.valueOf(model.getAttribute("sessionUserId")).startsWith("guest-"));
        assertTrue(String.valueOf(model.getAttribute("sessionDisplayName")).startsWith("Guest "));
        assertEquals("/uploads/avatars/default-avatar.jpg", model.getAttribute("sessionAvatarPath"));
        verify(session).setAttribute(org.mockito.ArgumentMatchers.eq("GUEST_USER_ID"), org.mockito.ArgumentMatchers.any(String.class));
    }
}
