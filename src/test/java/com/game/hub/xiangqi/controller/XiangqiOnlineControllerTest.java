package com.game.hub.xiangqi.controller;

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

class XiangqiOnlineControllerTest {

    @Test
    void onlineShouldRenderTemplateAndCreateGuestSessionPlayer() {
        UserAccountRepository repo = mock(UserAccountRepository.class);
        when(repo.findById(anyString())).thenReturn(Optional.empty());
        XiangqiOnlineController controller = new XiangqiOnlineController(repo);
        MockHttpServletRequest request = new MockHttpServletRequest();
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.online("XQ-ROOM-1", request, model);

        assertEquals("xiangqi/online", view);
        assertEquals("XQ-ROOM-1", model.getAttribute("defaultRoomId"));
        String sessionUserId = String.valueOf(model.getAttribute("sessionUserId"));
        assertTrue(sessionUserId.startsWith("guest-"));
        assertTrue(String.valueOf(model.getAttribute("sessionDisplayName")).startsWith("Guest"));
    }
}
