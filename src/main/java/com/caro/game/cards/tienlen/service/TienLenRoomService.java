package com.caro.game.cards.tienlen.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TienLenRoomService {
    private static final int PLAYER_LIMIT = 4;
    private final Map<String, RoomState> rooms = new ConcurrentHashMap<>();
    private final Random random;

    public TienLenRoomService() {
        this(new SecureRandom());
    }

    TienLenRoomService(Random random) {
        this.random = Objects.requireNonNull(random);
    }

    public synchronized JoinResult joinRoom(String roomId, String userId, String displayName, String avatarPath) {
        String rid = normalizeRoomId(roomId);
        String uid = normalizeUserId(userId);
        if (rid == null || uid == null) {
            return JoinResult.error("Invalid room or user", null);
        }

        RoomState room = rooms.computeIfAbsent(rid, RoomState::new);
        PlayerState existing = room.players.get(uid);
        if (existing != null) {
            existing.displayName = normalizeDisplayName(displayName, uid);
            existing.avatarPath = normalizeAvatarPath(avatarPath);
            return JoinResult.ok(snapshotOf(room));
        }

        if (room.started) {
            return JoinResult.error("Room is in progress", snapshotOf(room));
        }
        if (room.players.size() >= PLAYER_LIMIT) {
            return JoinResult.error("Room is full", snapshotOf(room));
        }

        int seatIndex = room.players.size();
        room.players.put(uid, new PlayerState(uid, normalizeDisplayName(displayName, uid), normalizeAvatarPath(avatarPath), seatIndex));
        return JoinResult.ok(snapshotOf(room));
    }

    public synchronized LeaveResult leaveRoom(String roomId, String userId) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        String uid = normalizeUserId(userId);
        if (room == null || uid == null || !room.players.containsKey(uid)) {
            return new LeaveResult(false, "Player not in room", null, false);
        }

        List<String> orderBeforeLeave = new ArrayList<>(room.players.keySet());
        boolean wasStarted = room.started;
        boolean wasGameOver = room.gameOver;
        boolean leavingCurrentTurn = uid.equals(room.currentTurnUserId);
        boolean leavingControl = uid.equals(room.controlUserId);
        boolean leavingTrickOwner = uid.equals(room.currentTrickOwnerUserId);

        room.players.remove(uid);
        room.hands.remove(uid);
        room.passedUsers.remove(uid);

        if (room.players.isEmpty()) {
            rooms.remove(room.roomId);
            return new LeaveResult(true, null, null, true);
        }

        reseat(room);

        if (!wasStarted || wasGameOver) {
            if (room.players.size() < PLAYER_LIMIT) {
                resetToWaiting(room, "Phong dang cho them nguoi choi de bat dau ván moi");
            } else {
                room.statusMessage = "Da roi phong. San sang bat dau lai.";
            }
            return new LeaveResult(true, null, snapshotOf(room), false);
        }

        if (room.players.size() <= 1) {
            resetToWaiting(room, "Khong du nguoi choi de tiep tuc. Phong dang cho nguoi moi.");
            return new LeaveResult(true, null, snapshotOf(room), false);
        }

        String nextAfterLeaver = nextPlayerIdFromHistoricalOrder(orderBeforeLeave, uid, room.players.keySet());

        if (leavingControl || leavingTrickOwner) {
            clearCurrentTrick(room);
            room.controlUserId = null;
        }

        if (leavingCurrentTurn) {
            room.currentTurnUserId = nextAfterLeaver;
        } else if (room.currentTurnUserId == null || !room.players.containsKey(room.currentTurnUserId)) {
            room.currentTurnUserId = nextAfterLeaver;
        }

        if (room.currentTurnUserId == null) {
            room.currentTurnUserId = room.players.keySet().stream().findFirst().orElse(null);
        }

        if (room.currentCombination == null) {
            room.currentTrickOwnerUserId = null;
            if (room.currentTurnUserId != null) {
                room.controlUserId = room.currentTurnUserId;
            }
            room.statusMessage = "Nguoi choi da roi phong. Van dau tiep tuc, mo vong moi.";
        } else {
            if (room.controlUserId == null || !room.players.containsKey(room.controlUserId)) {
                room.controlUserId = room.currentTurnUserId;
            }
            if (room.currentTurnUserId != null && room.currentTurnUserId.equals(room.controlUserId)) {
                clearCurrentTrick(room);
                room.statusMessage = "Nguoi choi da roi phong. Tat ca doi thu da pass/roi, mo vong moi.";
            } else {
                room.statusMessage = "Nguoi choi da roi phong. Bo qua luot cua nguoi nay va tiep tuc ván.";
            }
        }

        return new LeaveResult(true, null, snapshotOf(room), false);
    }

    public synchronized ActionResult startGame(String roomId, String userId) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        String uid = normalizeUserId(userId);
        if (room == null || uid == null || !room.players.containsKey(uid)) {
            return ActionResult.error("Player not in room", null);
        }
        if (room.started) {
            return ActionResult.error("Game already started", snapshotOf(room));
        }
        if (room.players.size() != PLAYER_LIMIT) {
            return ActionResult.error("Can du 4 nguoi moi bat dau duoc", snapshotOf(room));
        }

        List<TienLenCard> deck = TienLenCard.standardDeck();
        Collections.shuffle(deck, random);

        room.hands.clear();
        List<String> seatOrder = new ArrayList<>(room.players.keySet());
        for (String pid : seatOrder) {
            room.hands.put(pid, new ArrayList<>());
        }
        for (int i = 0; i < deck.size(); i++) {
            String pid = seatOrder.get(i % seatOrder.size());
            room.hands.get(pid).add(deck.get(i));
        }
        room.hands.values().forEach(hand -> hand.sort(TienLenCard.NATURAL_ORDER));

        String firstTurn = findPlayerHolding(room, "3S");
        room.started = true;
        room.gameOver = false;
        room.winnerUserId = null;
        room.currentTurnUserId = firstTurn;
        room.controlUserId = firstTurn;
        room.currentCombination = null;
        room.currentTrickCards = List.of();
        room.currentTrickOwnerUserId = null;
        room.passedUsers.clear();
        room.playCount = 0;
        room.statusMessage = "Van da bat dau. Nguoi giu 3♠ danh truoc.";

        return ActionResult.ok(snapshotOf(room), "GAME_STARTED");
    }

    public synchronized ActionResult playCards(String roomId, String userId, List<String> cardCodes) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        String uid = normalizeUserId(userId);
        if (room == null || uid == null || !room.players.containsKey(uid)) {
            return ActionResult.error("Player not in room", null);
        }
        if (!room.started || room.gameOver) {
            return ActionResult.error("Game is not active", snapshotOf(room));
        }
        if (!uid.equals(room.currentTurnUserId)) {
            return ActionResult.error("Chua den luot cua ban", snapshotOf(room));
        }
        if (cardCodes == null || cardCodes.isEmpty()) {
            return ActionResult.error("Ban phai chon it nhat 1 la bai", snapshotOf(room));
        }

        List<TienLenCard> hand = room.hands.get(uid);
        if (hand == null) {
            return ActionResult.error("Khong tim thay bai nguoi choi", snapshotOf(room));
        }
        List<TienLenCard> selected = resolveSelection(hand, cardCodes);
        if (selected == null) {
            return ActionResult.error("La bai khong hop le hoac khong thuoc tay ban", snapshotOf(room));
        }

        Combination combo = parseCombination(selected);
        if (combo == null) {
            return ActionResult.error("Bo bai khong hop le (ho tro: don, doi, sam, tu quy, sanh, doi thong)", snapshotOf(room));
        }

        if (room.playCount == 0 && selected.stream().noneMatch(card -> "3S".equals(card.code()))) {
            return ActionResult.error("Nuoc dau tien phai chua la 3♠", snapshotOf(room));
        }

        if (room.currentCombination != null) {
            if (!combo.canBeat(room.currentCombination)) {
                return ActionResult.error("Bo bai khong de hon bo bai hien tai tren ban", snapshotOf(room));
            }
        }

        removeCardsFromHand(hand, selected);
        hand.sort(TienLenCard.NATURAL_ORDER);

        room.currentCombination = combo;
        room.currentTrickCards = List.copyOf(selected.stream().sorted(TienLenCard.NATURAL_ORDER).toList());
        room.currentTrickOwnerUserId = uid;
        room.controlUserId = uid;
        room.passedUsers.clear();
        room.playCount++;

        if (hand.isEmpty()) {
            room.gameOver = true;
            room.winnerUserId = uid;
            room.currentTurnUserId = null;
            room.statusMessage = room.players.get(uid).displayName + " da het bai va chien thang!";
            return ActionResult.ok(snapshotOf(room), "GAME_OVER");
        }

        room.currentTurnUserId = nextPlayerId(room, uid);
        room.statusMessage = room.players.get(uid).displayName + " da danh " + combo.label;
        return ActionResult.ok(snapshotOf(room), "PLAYED");
    }

    public synchronized ActionResult passTurn(String roomId, String userId) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        String uid = normalizeUserId(userId);
        if (room == null || uid == null || !room.players.containsKey(uid)) {
            return ActionResult.error("Player not in room", null);
        }
        if (!room.started || room.gameOver) {
            return ActionResult.error("Game is not active", snapshotOf(room));
        }
        if (!uid.equals(room.currentTurnUserId)) {
            return ActionResult.error("Chua den luot cua ban", snapshotOf(room));
        }
        if (room.currentCombination == null) {
            return ActionResult.error("Khong the bo luot khi ban dang mo vong moi", snapshotOf(room));
        }
        if (uid.equals(room.controlUserId)) {
            return ActionResult.error("Nguoi dang nam vong khong the bo luot", snapshotOf(room));
        }

        room.passedUsers.add(uid);
        String next = nextPlayerId(room, uid);
        if (next == null) {
            room.currentTurnUserId = room.controlUserId;
            room.statusMessage = "Tat ca da bo luot. Mo vong moi.";
            clearCurrentTrick(room);
            return ActionResult.ok(snapshotOf(room), "ROUND_RESET");
        }

        if (Objects.equals(next, room.controlUserId) || allOthersPassed(room)) {
            room.currentTurnUserId = room.controlUserId;
            room.statusMessage = "Tat ca da bo luot. " + displayNameOf(room, room.controlUserId) + " mo vong moi.";
            clearCurrentTrick(room);
            return ActionResult.ok(snapshotOf(room), "ROUND_RESET");
        }

        room.currentTurnUserId = next;
        room.statusMessage = displayNameOf(room, uid) + " bo luot.";
        return ActionResult.ok(snapshotOf(room), "PASSED");
    }

    public synchronized RoomSnapshot roomSnapshot(String roomId) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        return snapshotOf(room);
    }

    public synchronized Map<String, PrivateState> privateStates(String roomId) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        if (room == null) {
            return Map.of();
        }
        Map<String, PrivateState> result = new LinkedHashMap<>();
        for (String userId : room.players.keySet()) {
            result.put(userId, privateStateInternal(room, userId));
        }
        return result;
    }

    public synchronized PrivateState privateState(String roomId, String userId) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        if (room == null) {
            return null;
        }
        return privateStateInternal(room, normalizeUserId(userId));
    }

    public synchronized List<RoomListItem> availableRooms() {
        List<RoomListItem> list = new ArrayList<>();
        for (RoomState room : rooms.values()) {
            if (!room.started && room.players.size() < PLAYER_LIMIT) {
                list.add(new RoomListItem(room.roomId, room.players.size(), PLAYER_LIMIT));
            }
        }
        list.sort(Comparator.comparing(RoomListItem::roomId));
        return List.copyOf(list);
    }

    public synchronized RoomSnapshot resetToWaitingAfterGame(String roomId) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        if (room == null) {
            return null;
        }
        if (room.players.isEmpty()) {
            rooms.remove(roomId);
            return null;
        }
        resetToWaiting(room, "Ván dau da ket thuc. Dang cho nguoi choi moi gia nhap.");
        return snapshotOf(room);
    }

    public synchronized List<String> roomPlayerIds(String roomId) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        if (room == null) {
            return List.of();
        }
        return List.copyOf(room.players.keySet());
    }

    private PrivateState privateStateInternal(RoomState room, String userId) {
        if (room == null || userId == null || !room.players.containsKey(userId)) {
            return null;
        }
        List<TienLenCard> hand = room.hands.getOrDefault(userId, List.of());
        List<TienLenCard> cards = hand.stream().sorted(TienLenCard.NATURAL_ORDER).toList();
        return new PrivateState(room.roomId, userId, cards);
    }

    private RoomSnapshot snapshotOf(RoomState room) {
        if (room == null) {
            return null;
        }
        List<PlayerSnapshot> players = room.players.values().stream()
            .sorted(Comparator.comparingInt(p -> p.seatIndex))
            .map(p -> new PlayerSnapshot(
                p.userId,
                p.displayName,
                p.avatarPath,
                p.seatIndex,
                room.hands.getOrDefault(p.userId, List.of()).size()
            ))
            .toList();

        TrickSnapshot trick = null;
        if (room.currentCombination != null && room.currentTrickOwnerUserId != null) {
            trick = new TrickSnapshot(
                room.currentTrickOwnerUserId,
                displayNameOf(room, room.currentTrickOwnerUserId),
                room.currentCombination.type.name(),
                room.currentCombination.length,
                room.currentCombination.label,
                List.copyOf(room.currentTrickCards)
            );
        }

        return new RoomSnapshot(
            room.roomId,
            room.started,
            room.gameOver,
            room.winnerUserId,
            room.currentTurnUserId,
            room.controlUserId,
            room.playCount,
            room.players.size(),
            PLAYER_LIMIT,
            room.players.size() == PLAYER_LIMIT && !room.started,
            room.statusMessage,
            players,
            List.copyOf(room.passedUsers.stream().sorted().toList()),
            trick
        );
    }

    private void clearCurrentTrick(RoomState room) {
        room.currentCombination = null;
        room.currentTrickCards = List.of();
        room.currentTrickOwnerUserId = null;
        room.passedUsers.clear();
    }

    private void resetToWaiting(RoomState room, String message) {
        room.started = false;
        room.gameOver = false;
        room.winnerUserId = null;
        room.currentTurnUserId = null;
        room.controlUserId = null;
        room.currentCombination = null;
        room.currentTrickCards = List.of();
        room.currentTrickOwnerUserId = null;
        room.passedUsers.clear();
        room.playCount = 0;
        room.hands.clear();
        room.statusMessage = message;
    }

    private void reseat(RoomState room) {
        int seat = 0;
        for (PlayerState player : room.players.values()) {
            player.seatIndex = seat++;
        }
    }

    private boolean allOthersPassed(RoomState room) {
        if (room.controlUserId == null) {
            return false;
        }
        for (String uid : room.players.keySet()) {
            if (uid.equals(room.controlUserId)) {
                continue;
            }
            if (!room.passedUsers.contains(uid)) {
                return false;
            }
        }
        return true;
    }

    private String nextPlayerId(RoomState room, String fromUserId) {
        List<String> order = new ArrayList<>(room.players.keySet());
        if (order.isEmpty()) {
            return null;
        }
        int index = order.indexOf(fromUserId);
        if (index < 0) {
            return order.getFirst();
        }
        for (int step = 1; step <= order.size(); step++) {
            String candidate = order.get((index + step) % order.size());
            if (candidate.equals(fromUserId)) {
                continue;
            }
            if (room.players.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String nextPlayerIdFromHistoricalOrder(List<String> historicalOrder,
                                                   String removedUserId,
                                                   Set<String> activePlayers) {
        if (activePlayers == null || activePlayers.isEmpty()) {
            return null;
        }
        if (historicalOrder == null || historicalOrder.isEmpty()) {
            return activePlayers.iterator().next();
        }
        int index = historicalOrder.indexOf(removedUserId);
        if (index < 0) {
            for (String userId : historicalOrder) {
                if (activePlayers.contains(userId)) {
                    return userId;
                }
            }
            return activePlayers.iterator().next();
        }
        for (int step = 1; step <= historicalOrder.size(); step++) {
            String candidate = historicalOrder.get((index + step) % historicalOrder.size());
            if (activePlayers.contains(candidate)) {
                return candidate;
            }
        }
        return activePlayers.iterator().next();
    }

    private String findPlayerHolding(RoomState room, String cardCode) {
        for (Map.Entry<String, List<TienLenCard>> entry : room.hands.entrySet()) {
            boolean found = entry.getValue().stream().anyMatch(c -> cardCode.equals(c.code()));
            if (found) {
                return entry.getKey();
            }
        }
        return room.players.keySet().stream().findFirst().orElse(null);
    }

    private void removeCardsFromHand(List<TienLenCard> hand, List<TienLenCard> selected) {
        Map<String, Integer> counts = new HashMap<>();
        for (TienLenCard card : selected) {
            counts.merge(card.code(), 1, Integer::sum);
        }
        hand.removeIf(card -> {
            Integer remaining = counts.get(card.code());
            if (remaining == null || remaining <= 0) {
                return false;
            }
            if (remaining == 1) {
                counts.remove(card.code());
            } else {
                counts.put(card.code(), remaining - 1);
            }
            return true;
        });
    }

    private List<TienLenCard> resolveSelection(List<TienLenCard> hand, List<String> cardCodes) {
        if (cardCodes == null || cardCodes.isEmpty()) {
            return null;
        }
        List<String> normalizedCodes = new ArrayList<>();
        Set<String> uniqueCheck = new HashSet<>();
        for (String code : cardCodes) {
            if (code == null || code.isBlank()) {
                return null;
            }
            String normalized = code.trim().toUpperCase(Locale.ROOT);
            if (!uniqueCheck.add(normalized)) {
                return null;
            }
            normalizedCodes.add(normalized);
        }
        Map<String, TienLenCard> handByCode = new HashMap<>();
        for (TienLenCard card : hand) {
            handByCode.put(card.code().toUpperCase(Locale.ROOT), card);
        }
        List<TienLenCard> selected = new ArrayList<>();
        for (String code : normalizedCodes) {
            TienLenCard card = handByCode.get(code);
            if (card == null) {
                return null;
            }
            selected.add(card);
        }
        selected.sort(TienLenCard.NATURAL_ORDER);
        return selected;
    }

    private Combination parseCombination(List<TienLenCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        List<TienLenCard> sorted = new ArrayList<>(cards);
        sorted.sort(TienLenCard.NATURAL_ORDER);
        int size = sorted.size();

        if (size == 1) {
            TienLenCard c = sorted.getFirst();
            return new Combination(CombinationType.SINGLE, 1, c.rankValue(), c.suitOrder(), sorted, "don " + c.label());
        }

        boolean sameRank = sorted.stream().map(TienLenCard::rankValue).distinct().count() == 1;
        if (sameRank && size == 2) {
            TienLenCard hi = sorted.getLast();
            return new Combination(CombinationType.PAIR, 2, hi.rankValue(), hi.suitOrder(), sorted, "doi " + rankLabel(hi.rankValue()));
        }
        if (sameRank && size == 3) {
            TienLenCard hi = sorted.getLast();
            return new Combination(CombinationType.TRIPLE, 3, hi.rankValue(), hi.suitOrder(), sorted, "sam " + rankLabel(hi.rankValue()));
        }
        if (sameRank && size == 4) {
            TienLenCard hi = sorted.getLast();
            return new Combination(CombinationType.FOUR_KIND, 4, hi.rankValue(), hi.suitOrder(), sorted, "tu quy " + rankLabel(hi.rankValue()));
        }

        if (size >= 6 && size % 2 == 0) {
            boolean hasTwo = sorted.stream().anyMatch(card -> card.rankValue() == 15);
            if (!hasTwo) {
                boolean validDoubleStraight = true;
                int prevRank = -1;
                for (int i = 0; i < size; i += 2) {
                    int currentRank = sorted.get(i).rankValue();
                    if (sorted.get(i + 1).rankValue() != currentRank) {
                        validDoubleStraight = false;
                        break;
                    }
                    if (prevRank >= 0 && currentRank != prevRank + 1) {
                        validDoubleStraight = false;
                        break;
                    }
                    prevRank = currentRank;
                }
                if (validDoubleStraight) {
                    TienLenCard hi = sorted.getLast();
                    int pairCount = size / 2;
                    return new Combination(
                        CombinationType.DOUBLE_STRAIGHT,
                        size,
                        hi.rankValue(),
                        hi.suitOrder(),
                        sorted,
                        "doi thong " + pairCount + " doi (" + sorted.getFirst().label() + " - " + hi.label() + ")"
                    );
                }
            }
        }

        if (size >= 3) {
            for (TienLenCard card : sorted) {
                if (card.rankValue() == 15) {
                    return null;
                }
            }
            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i).rankValue() != sorted.get(i - 1).rankValue() + 1) {
                    return null;
                }
            }
            TienLenCard hi = sorted.getLast();
            return new Combination(CombinationType.STRAIGHT, size, hi.rankValue(), hi.suitOrder(), sorted,
                "sanh " + size + " la (" + sorted.getFirst().label() + " - " + hi.label() + ")");
        }

        return null;
    }

    private String rankLabel(int rankValue) {
        return switch (rankValue) {
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "A";
            case 15 -> "2";
            default -> String.valueOf(rankValue);
        };
    }

    private String displayNameOf(RoomState room, String userId) {
        if (room == null || userId == null) {
            return "Unknown";
        }
        PlayerState player = room.players.get(userId);
        return player == null ? userId : player.displayName;
    }

    private String normalizeRoomId(String roomId) {
        if (roomId == null) return null;
        String value = roomId.trim();
        if (value.isEmpty()) return null;
        return value;
    }

    private String normalizeUserId(String userId) {
        if (userId == null) return null;
        String value = userId.trim();
        if (value.isEmpty()) return null;
        return value;
    }

    private String normalizeDisplayName(String displayName, String fallbackUserId) {
        if (displayName == null || displayName.isBlank()) {
            return fallbackUserId;
        }
        return displayName.trim();
    }

    private String normalizeAvatarPath(String avatarPath) {
        if (avatarPath == null || avatarPath.isBlank()) {
            return "/uploads/avatars/default-avatar.jpg";
        }
        return avatarPath.trim();
    }

    private enum CombinationType {
        SINGLE,
        PAIR,
        TRIPLE,
        FOUR_KIND,
        STRAIGHT,
        DOUBLE_STRAIGHT
    }

    private record Combination(
        CombinationType type,
        int length,
        int highestRank,
        int highestSuit,
        List<TienLenCard> cards,
        String label
    ) {
        boolean canBeat(Combination current) {
            if (current == null) {
                return true;
            }
            if (canBeatSpecial(current)) {
                return true;
            }
            if (this.type != current.type) {
                return false;
            }
            if (this.length != current.length) {
                return false;
            }
            if (this.highestRank != current.highestRank) {
                return this.highestRank > current.highestRank;
            }
            return this.highestSuit > current.highestSuit;
        }

        private boolean canBeatSpecial(Combination current) {
            if (current == null) {
                return false;
            }
            if (current.type == CombinationType.SINGLE && current.highestRank == 15) {
                if (this.type == CombinationType.FOUR_KIND) {
                    return true;
                }
                return this.type == CombinationType.DOUBLE_STRAIGHT && this.length >= 6;
            }
            if (current.type == CombinationType.PAIR && current.highestRank == 15) {
                if (this.type == CombinationType.FOUR_KIND) {
                    return true;
                }
                return this.type == CombinationType.DOUBLE_STRAIGHT && this.length >= 8;
            }
            if (current.type == CombinationType.FOUR_KIND) {
                return this.type == CombinationType.DOUBLE_STRAIGHT && this.length >= 8;
            }
            if (current.type == CombinationType.DOUBLE_STRAIGHT && this.type == CombinationType.DOUBLE_STRAIGHT) {
                return this.length > current.length;
            }
            return false;
        }
    }

    private static final class PlayerState {
        private final String userId;
        private String displayName;
        private String avatarPath;
        private int seatIndex;

        private PlayerState(String userId, String displayName, String avatarPath, int seatIndex) {
            this.userId = userId;
            this.displayName = displayName;
            this.avatarPath = avatarPath;
            this.seatIndex = seatIndex;
        }
    }

    private static final class RoomState {
        private final String roomId;
        private final LinkedHashMap<String, PlayerState> players = new LinkedHashMap<>();
        private final Map<String, List<TienLenCard>> hands = new HashMap<>();
        private final Set<String> passedUsers = new HashSet<>();
        private boolean started;
        private boolean gameOver;
        private String winnerUserId;
        private String currentTurnUserId;
        private String controlUserId;
        private Combination currentCombination;
        private List<TienLenCard> currentTrickCards = List.of();
        private String currentTrickOwnerUserId;
        private int playCount;
        private String statusMessage = "Cho du 4 nguoi de bat dau";

        private RoomState(String roomId) {
            this.roomId = roomId;
        }
    }

    public record JoinResult(boolean ok, String error, RoomSnapshot room) {
        public static JoinResult ok(RoomSnapshot room) {
            return new JoinResult(true, null, room);
        }

        public static JoinResult error(String error, RoomSnapshot room) {
            return new JoinResult(false, error, room);
        }
    }

    public record LeaveResult(boolean ok, String error, RoomSnapshot room, boolean roomClosed) {
    }

    public record ActionResult(boolean ok, String error, String eventType, RoomSnapshot room) {
        public static ActionResult ok(RoomSnapshot room, String eventType) {
            return new ActionResult(true, null, eventType, room);
        }

        public static ActionResult error(String error, RoomSnapshot room) {
            return new ActionResult(false, error, "ERROR", room);
        }
    }

    public record RoomSnapshot(
        String roomId,
        boolean started,
        boolean gameOver,
        String winnerUserId,
        String currentTurnUserId,
        String controlUserId,
        int playCount,
        int playerCount,
        int playerLimit,
        boolean canStart,
        String statusMessage,
        List<PlayerSnapshot> players,
        List<String> passedUserIds,
        TrickSnapshot currentTrick
    ) {
    }

    public record PlayerSnapshot(
        String userId,
        String displayName,
        String avatarPath,
        int seatIndex,
        int handCount
    ) {
    }

    public record TrickSnapshot(
        String playedByUserId,
        String playedByDisplayName,
        String combinationType,
        int cardCount,
        String combinationLabel,
        List<TienLenCard> cards
    ) {
    }

    public record PrivateState(
        String roomId,
        String userId,
        List<TienLenCard> hand
    ) {
    }

    public record RoomListItem(
        String roomId,
        int playerCount,
        int playerLimit
    ) {
    }
}
