package com.game.hub.games.puzzle.controller;

import com.game.hub.games.puzzle.model.Word;
import com.game.hub.games.puzzle.service.WordPuzzleService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminPuzzleWordControllerTest {

    @Test
    void listWordsShouldReturnDatabaseEntries() {
        WordPuzzleService service = mock(WordPuzzleService.class);
        Word word = new Word("SPRING");
        word.setId(1L);
        when(service.listWords()).thenReturn(List.of(word));

        AdminPuzzleWordController controller = new AdminPuzzleWordController(service);
        Object result = controller.listWords();

        assertInstanceOf(Map.class, result);
        Map<?, ?> payload = (Map<?, ?>) result;
        assertTrue((Boolean) payload.get("success"));
        assertInstanceOf(List.class, payload.get("words"));
        List<?> words = (List<?>) payload.get("words");
        assertEquals(1, words.size());
    }

    @Test
    void createWordShouldReturnValidationErrorFromService() {
        WordPuzzleService service = mock(WordPuzzleService.class);
        when(service.addWord("")).thenThrow(new IllegalArgumentException("Tu khong hop le"));

        AdminPuzzleWordController controller = new AdminPuzzleWordController(service);
        Object result = controller.createWord(new AdminPuzzleWordController.CreateWordRequest(""));

        assertInstanceOf(Map.class, result);
        Map<?, ?> payload = (Map<?, ?>) result;
        assertFalse((Boolean) payload.get("success"));
        assertEquals("Tu khong hop le", payload.get("error"));
    }
}
