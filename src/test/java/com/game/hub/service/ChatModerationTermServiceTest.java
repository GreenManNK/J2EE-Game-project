package com.game.hub.service;

import com.game.hub.entity.ChatModerationTerm;
import com.game.hub.repository.ChatModerationTermRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatModerationTermServiceTest {

    @Test
    void addTermShouldRejectBuiltInDefaultTerm() {
        ChatModerationTermRepository repository = mock(ChatModerationTermRepository.class);
        ChatModerationTermService service = new ChatModerationTermService(repository);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.addTerm("vcl"));

        assertEquals("Cum tu nay da nam trong bo loc mac dinh.", error.getMessage());
        verify(repository, never()).save(any(ChatModerationTerm.class));
    }

    @Test
    void addTermShouldNormalizeAndPersistCustomTerm() {
        ChatModerationTermRepository repository = mock(ChatModerationTermRepository.class);
        ChatModerationTerm saved = new ChatModerationTerm();
        saved.setId(5L);
        saved.setTerm("lag switch");
        when(repository.existsByTermIgnoreCase("lag switch")).thenReturn(false);
        when(repository.save(any(ChatModerationTerm.class))).thenReturn(saved);
        when(repository.findAllByOrderByTermAsc()).thenReturn(List.of(saved));

        ChatModerationTermService service = new ChatModerationTermService(repository);
        ChatModerationTerm result = service.addTerm("  Lag-Switch  ");

        assertEquals(5L, result.getId());
        assertEquals("lag switch", result.getTerm());
        assertTrue(service.listDatabaseTermStrings().contains("lag switch"));
    }

    @Test
    void deleteTermShouldReturnFalseWhenMissing() {
        ChatModerationTermRepository repository = mock(ChatModerationTermRepository.class);
        when(repository.existsById(99L)).thenReturn(false);

        ChatModerationTermService service = new ChatModerationTermService(repository);

        assertFalse(service.deleteTerm(99L));
    }
}
