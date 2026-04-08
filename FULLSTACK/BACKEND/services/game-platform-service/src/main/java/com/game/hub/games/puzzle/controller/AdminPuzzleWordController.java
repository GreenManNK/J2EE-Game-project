package com.game.hub.games.puzzle.controller;

import com.game.hub.games.puzzle.model.Word;
import com.game.hub.games.puzzle.service.WordPuzzleService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/api/puzzle-words")
public class AdminPuzzleWordController {
    private final WordPuzzleService wordPuzzleService;

    public AdminPuzzleWordController(WordPuzzleService wordPuzzleService) {
        this.wordPuzzleService = wordPuzzleService;
    }

    @ResponseBody
    @GetMapping
    public Object listWords() {
        List<Map<String, Object>> words = wordPuzzleService.listWords().stream()
            .map(this::toPayload)
            .toList();
        return Map.of(
            "success", true,
            "words", words
        );
    }

    @ResponseBody
    @PostMapping
    public Object createWord(@RequestBody CreateWordRequest request) {
        try {
            Word created = wordPuzzleService.addWord(request == null ? null : request.word());
            return Map.of(
                "success", true,
                "word", toPayload(created)
            );
        } catch (IllegalArgumentException ex) {
            return Map.of(
                "success", false,
                "error", ex.getMessage()
            );
        }
    }

    private Map<String, Object> toPayload(Word word) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", word.getId());
        payload.put("word", word.getWord());
        payload.put("length", word.getWord() == null ? 0 : word.getWord().codePointCount(0, word.getWord().length()));
        return payload;
    }

    public record CreateWordRequest(String word) {
    }
}
