package com.game.hub.games.typing.service;

import com.game.hub.games.typing.logic.TypingRoom;
import com.game.hub.games.typing.model.TypingText;
import com.game.hub.games.typing.repository.TypingTextRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TypingService {
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

    private String pickRandomText() {
        TypingText randomText = textRepository.findRandomText();
        if (randomText == null || randomText.getContent() == null || randomText.getContent().isBlank()) {
            return "The quick brown fox jumps over the lazy dog.";
        }
        return randomText.getContent();
    }
}
