package com.game.hub.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AvatarStorageServiceTest {

    @Test
    void storeShouldRejectAvatarLargerThanConfiguredLimit() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(AvatarStorageService.MAX_AVATAR_BYTES + 1L);

        AvatarStorageService.StoreResult result = new AvatarStorageService().store(file);

        assertFalse(result.success());
        assertEquals("Avatar vuot qua gioi han 406MB", result.error());
    }
}
