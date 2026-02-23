package com.caro.game.controller;

import com.caro.game.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameControllerTest {

    @Test
    void indexShouldRedirectToLoginWhenNoSessionUser() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        GameController controller = new GameController(userAccountRepository);
        Model model = new ConcurrentModel();

        String view = controller.index("room1", null, request, model);

        assertEquals("redirect:/account/login-page", view);
    }
}
