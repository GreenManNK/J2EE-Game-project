package com.game.hub.games.typing.service;

import com.game.hub.games.typing.logic.TypingRoom;
import com.game.hub.games.typing.model.TypingText;
import com.game.hub.games.typing.repository.TypingTextRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TypingService {
    private static final List<String> PRACTICE_FALLBACK_TEXTS = List.of(
        "The quick brown fox jumps over the lazy dog.",
        "Typing practice rewards accuracy before raw speed.",
        "A steady rhythm usually beats frantic corrections in the final stretch.",
        "Keep your eyes on the source text and let your fingers learn the pattern."
    );

    private final Map<String, TypingRoom> rooms = new ConcurrentHashMap<>();
    private final TypingTextRepository textRepository;

    @Autowired
    public TypingService(TypingTextRepository textRepository) {
        this.textRepository = textRepository;
    }

    public TypingRoom createRoom() {
        String roomId = UUID.randomUUID().toString();
        TypingRoom room = new TypingRoom(roomId, pickRandomText());
        rooms.put(roomId, room);
        return room;
    }

    public TypingRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public void removeRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return;
        }
        rooms.remove(roomId);
    }

    public List<TypingRoom> getAvailableRooms() {
        return new ArrayList<>(rooms.values());
    }

    public TypingRoom resetRoomRace(String roomId) {
        TypingRoom room = rooms.get(roomId);
        if (room == null) {
            return null;
        }
        room.resetRace(pickRandomText());
        return room;
    }

    public List<String> getPracticeTexts() {
        LinkedHashSet<String> texts = new LinkedHashSet<>();
        String randomText = trimToNull(pickRandomText());
        if (randomText != null) {
            texts.add(randomText);
        }
        for (String fallbackText : PRACTICE_FALLBACK_TEXTS) {
            String normalized = trimToNull(fallbackText);
            if (normalized != null) {
                texts.add(normalized);
            }
        }
        return List.copyOf(texts);
    }

    private String pickRandomText() {
        TypingText randomText = textRepository.findRandomText();
        if (randomText == null || randomText.getContent() == null || randomText.getContent().isBlank()) {
            return "The quick brown fox jumps over the lazy dog.";
        }
        return randomText.getContent();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
