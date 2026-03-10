package com.game.hub.controller;

import com.game.hub.service.AccountService;
import com.game.hub.service.AvatarStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountControllerTest {

    @Test
    void uploadAvatarShouldRequireLogin() {
        AccountService accountService = mock(AccountService.class);
        AvatarStorageService avatarStorageService = mock(AvatarStorageService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        AccountController controller = new AccountController(accountService, avatarStorageService);
        Object result = controller.uploadAvatar(
            new MockMultipartFile("avatar", "avatar.png", "image/png", new byte[] {1, 2, 3}),
            request
        );

        assertTrue(result instanceof Map<?, ?>);
        assertFalse((Boolean) ((Map<?, ?>) result).get("success"));
        assertEquals("Login required", ((Map<?, ?>) result).get("error"));
    }

    @Test
    void uploadAvatarShouldReturnStorageErrorWhenFileRejected() {
        AccountService accountService = mock(AccountService.class);
        AvatarStorageService avatarStorageService = mock(AvatarStorageService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");
        when(avatarStorageService.store(org.mockito.ArgumentMatchers.any()))
            .thenReturn(AvatarStorageService.StoreResult.error("Chi ho tro JPG, PNG, GIF, WEBP"));

        AccountController controller = new AccountController(accountService, avatarStorageService);
        Object result = controller.uploadAvatar(
            new MockMultipartFile("avatar", "avatar.txt", "text/plain", new byte[] {1}),
            request
        );

        assertTrue(result instanceof Map<?, ?>);
        assertFalse((Boolean) ((Map<?, ?>) result).get("success"));
        assertEquals("Chi ho tro JPG, PNG, GIF, WEBP", ((Map<?, ?>) result).get("error"));
    }

    @Test
    void uploadAvatarShouldUpdateAvatarOnAuthenticatedUser() {
        AccountService accountService = mock(AccountService.class);
        AvatarStorageService avatarStorageService = mock(AvatarStorageService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_USER_ID")).thenReturn("u1");
        when(avatarStorageService.store(org.mockito.ArgumentMatchers.any()))
            .thenReturn(AvatarStorageService.StoreResult.ok("/uploads/avatars/u1.png"));
        when(accountService.updateAvatar("u1", "/uploads/avatars/u1.png"))
            .thenReturn(AccountService.ServiceResult.ok(Map.of(
                "userId", "u1",
                "displayName", "Alice",
                "email", "alice@example.com",
                "role", "User",
                "avatarPath", "/uploads/avatars/u1.png"
            )));

        AccountController controller = new AccountController(accountService, avatarStorageService);
        Object result = controller.uploadAvatar(
            new MockMultipartFile("avatar", "avatar.png", "image/png", new byte[] {1, 2, 3}),
            request
        );

        assertTrue(result instanceof Map<?, ?>);
        Map<?, ?> payload = (Map<?, ?>) result;
        assertTrue((Boolean) payload.get("success"));
        assertTrue(payload.get("data") instanceof Map<?, ?>);
        assertEquals("/uploads/avatars/u1.png", ((Map<?, ?>) payload.get("data")).get("avatarPath"));
        verify(accountService).updateAvatar("u1", "/uploads/avatars/u1.png");
    }
}
