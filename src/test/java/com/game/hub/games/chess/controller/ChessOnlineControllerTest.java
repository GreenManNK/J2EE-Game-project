package com.game.hub.games.chess.controller;

import com.game.hub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ConcurrentModel;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChessOnlineControllerTest {

    @Test
    void onlineShouldRenderTemplateAndCreateGuestSessionPlayer() {
        UserAccountRepository repo = mock(UserAccountRepository.class);
        when(repo.findById(anyString())).thenReturn(Optional.empty());
        ChessOnlineController controller = new ChessOnlineController(repo);
        MockHttpServletRequest request = new MockHttpServletRequest();
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.online("CHESS-ROOM-1", request, model);

        assertEquals("chess/online", view);
        assertEquals("CHESS-ROOM-1", model.getAttribute("defaultRoomId"));
        String sessionUserId = String.valueOf(model.getAttribute("sessionUserId"));
        assertTrue(sessionUserId.startsWith("guest-"));
        assertTrue(String.valueOf(model.getAttribute("sessionDisplayName")).startsWith("Guest"));
    }
}
