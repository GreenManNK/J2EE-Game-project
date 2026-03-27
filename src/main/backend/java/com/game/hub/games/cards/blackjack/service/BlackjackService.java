package com.game.hub.games.cards.blackjack.service;

import com.game.hub.games.cards.blackjack.logic.BlackjackRoom;
import com.game.hub.games.cards.blackjack.model.Deck;
import com.game.hub.games.cards.blackjack.model.FixedBlackjackDeck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class BlackjackService {
    private final Map<String, BlackjackRoom> rooms = new ConcurrentHashMap<>();
    private final Supplier<Deck> deckSupplier;

    public BlackjackService() {
        this(Deck::new);
    }

    @Autowired
    public BlackjackService(@Value("${app.blackjack.fixed-deck-enabled:false}") boolean fixedDeckEnabled) {
        this(fixedDeckEnabled ? FixedBlackjackDeck::new : Deck::new);
    }

    BlackjackService(Supplier<Deck> deckSupplier) {
        this.deckSupplier = deckSupplier == null ? Deck::new : deckSupplier;
    }

    public BlackjackRoom createRoom() {
        String roomId = UUID.randomUUID().toString();
        BlackjackRoom room = new BlackjackRoom(roomId, deckSupplier.get());
        rooms.put(roomId, room);
        return room;
    }

    public BlackjackRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public void removeRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return;
        }
        rooms.remove(roomId);
    }

    public List<BlackjackRoom> getAvailableRooms() {
        return new ArrayList<>(rooms.values());
    }
}
