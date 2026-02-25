package com.caro.game.controller;

import com.caro.game.entity.UserAccount;
import com.caro.game.repository.FriendshipRepository;
import com.caro.game.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void managerEditShouldNotChangeUserRole() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        UserAccount user = new UserAccount();
        user.setId("u1");
        user.setRole("User");
        user.setDisplayName("Old");
        user.setScore(10);

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ManagerController controller = new ManagerController(userRepo, passwordEncoder);
        Object result = controller.edit("u1", new ManagerController.EditUserRequest("New", 99, "Admin", "/a.png"));

        assertTrue(result instanceof Map<?, ?>);
        assertTrue((Boolean) ((Map<?, ?>) result).get("success"));
        assertEquals("User", user.getRole());
        assertEquals("New", user.getDisplayName());
        assertEquals(99, user.getScore());
        assertEquals("/a.png", user.getAvatarPath());
    }

    @Test
    void managerCreateShouldForceUserRole() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(userRepo.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pw")).thenReturn("encoded");
        when(userRepo.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ManagerController controller = new ManagerController(userRepo, passwordEncoder);
        Object result = controller.create(new ManagerController.CreateUserRequest(
            "new@example.com", "New User", "pw", 0, "Admin", null
        ));

        assertTrue(result instanceof Map<?, ?>);
        assertTrue((Boolean) ((Map<?, ?>) result).get("success"));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepo).save(captor.capture());
        assertEquals("User", captor.getValue().getRole());
    }
}
