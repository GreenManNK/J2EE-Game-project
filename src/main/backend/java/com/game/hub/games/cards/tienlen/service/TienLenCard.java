package com.game.hub.games.cards.tienlen.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record TienLenCard(
    String code,
    String label,
    int rankValue,
    int suitOrder
) {
    public static final Comparator<TienLenCard> NATURAL_ORDER =
        Comparator.comparingInt(TienLenCard::rankValue)
            .thenComparingInt(TienLenCard::suitOrder);

    private static final List<String> SUIT_CODES = List.of("S", "C", "D", "H");
    private static final List<String> SUIT_SYMBOLS = List.of("\u2660", "\u2663", "\u2666", "\u2665");
    private static final Map<Integer, String> RANK_LABELS = Map.ofEntries(
        Map.entry(3, "3"),
        Map.entry(4, "4"),
        Map.entry(5, "5"),
        Map.entry(6, "6"),
        Map.entry(7, "7"),
        Map.entry(8, "8"),
        Map.entry(9, "9"),
        Map.entry(10, "10"),
        Map.entry(11, "J"),
        Map.entry(12, "Q"),
        Map.entry(13, "K"),
        Map.entry(14, "A"),
        Map.entry(15, "2")
    );

    public static List<TienLenCard> standardDeck() {
        List<TienLenCard> deck = new ArrayList<>(52);
        for (int rank = 3; rank <= 15; rank++) {
            for (int suit = 0; suit < SUIT_CODES.size(); suit++) {
                deck.add(of(rank, suit));
            }
        }
        return deck;
    }

    public static TienLenCard of(int rankValue, int suitOrder) {
        if (!RANK_LABELS.containsKey(rankValue)) {
            throw new IllegalArgumentException("Unsupported rank: " + rankValue);
        }
        if (suitOrder < 0 || suitOrder >= SUIT_CODES.size()) {
            throw new IllegalArgumentException("Unsupported suit: " + suitOrder);
        }
        String rankLabel = RANK_LABELS.get(rankValue);
        String suitCode = SUIT_CODES.get(suitOrder);
        String suitSymbol = SUIT_SYMBOLS.get(suitOrder);
        return new TienLenCard(
            rankLabel + suitCode,
            rankLabel + suitSymbol,
            rankValue,
            suitOrder
        );
    }

    public static TienLenCard parseCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Card code is blank");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() < 2) {
            throw new IllegalArgumentException("Invalid card code: " + code);
        }
        String suitCode = normalized.substring(normalized.length() - 1);
        String rankText = normalized.substring(0, normalized.length() - 1);

        int suitOrder = SUIT_CODES.indexOf(suitCode);
        if (suitOrder < 0) {
            throw new IllegalArgumentException("Invalid suit: " + code);
        }

        int rankValue = switch (rankText) {
            case "3" -> 3;
            case "4" -> 4;
            case "5" -> 5;
            case "6" -> 6;
            case "7" -> 7;
            case "8" -> 8;
            case "9" -> 9;
            case "10" -> 10;
            case "J" -> 11;
            case "Q" -> 12;
            case "K" -> 13;
            case "A" -> 14;
            case "2" -> 15;
            default -> throw new IllegalArgumentException("Invalid rank: " + code);
        };
        return of(rankValue, suitOrder);
    }
}
