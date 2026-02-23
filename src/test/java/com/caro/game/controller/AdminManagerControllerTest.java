package com.caro.game.controller;

import com.caro.game.repository.FriendshipRepository;
import com.caro.game.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminManagerControllerTest {

    @Test
    void adminEndpointsShouldHandleMissingUserGracefully() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        FriendshipRepository friendshipRepo = mock(FriendshipRepository.class);
        when(userRepo.findById("missing")).thenReturn(Optional.empty());

        AdminController controller = new AdminController(userRepo, friendshipRepo, mock(org.springframework.security.crypto.password.PasswordEncoder.class));

        Model model = new ConcurrentModel();
        String view = controller.userDetailPage("missing", model);
        assertEquals("redirect:/admin/users", view);

        Object details = controller.details("missing");
        assertTrue(details instanceof Map<?, ?>);
        assertFalse((Boolean) ((Map<?, ?>) details).get("success"));

        Object edit = controller.edit("missing", new AdminController.EditUserRequest("A", 1, "User", null));
        assertTrue(edit instanceof Map<?, ?>);
        assertFalse((Boolean) ((Map<?, ?>) edit).get("success"));
    }

    @Test
    void managerEndpointsShouldHandleMissingUserGracefully() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findById("missing")).thenReturn(Optional.empty());

        ManagerController controller = new ManagerController(userRepo, mock(org.springframework.security.crypto.password.PasswordEncoder.class));

        Object details = controller.details("missing");
        assertTrue(details instanceof Map<?, ?>);
        assertFalse((Boolean) ((Map<?, ?>) details).get("success"));

        Object edit = controller.edit("missing", new ManagerController.EditUserRequest("A", 1, "User", null));
        assertTrue(edit instanceof Map<?, ?>);
        assertFalse((Boolean) ((Map<?, ?>) edit).get("success"));
    }
}
