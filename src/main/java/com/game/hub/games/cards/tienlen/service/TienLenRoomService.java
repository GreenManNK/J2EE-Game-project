package com.game.hub.games.cards.tienlen.service;

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
    private static final String BOT_USER_ID_PREFIX = "bot-tienlen-";
    private static final int PENALTY_TWO_BLACK = 5;
    private static final int PENALTY_TWO_RED = 10;
    private static final int PENALTY_CONG_BONUS = 13;
    private static final int PENALTY_LEFTOVER_FOUR_KIND = 8;
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
            if (!room.started) {
                updateWaitingStatus(room);
            }
            return JoinResult.ok(snapshotOf(room));
        }

        if (room.started) {
            return JoinResult.error("Room is in progress", snapshotOf(room));
        }
        if (room.players.size() >= PLAYER_LIMIT) {
            return JoinResult.error("Room is full", snapshotOf(room));
        }

        int seatIndex = room.players.size();
        room.players.put(uid, new PlayerState(uid, normalizeDisplayName(displayName, uid), normalizeAvatarPath(avatarPath), seatIndex, false));
        if (!room.started) {
            updateWaitingStatus(room);
        }
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

        if (!containsHumanPlayer(room)) {
            rooms.remove(room.roomId);
            return new LeaveResult(true, null, null, true);
        }

        if (!wasStarted || wasGameOver) {
            removeBotPlayers(room);
            if (room.players.isEmpty()) {
                rooms.remove(room.roomId);
                return new LeaveResult(true, null, null, true);
            }
            reseat(room);
            if (room.players.size() < PLAYER_LIMIT) {
                resetToWaiting(room, "Phong dang cho them nguoi choi de bat dau van moi");
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
                room.statusMessage = "Nguoi choi da roi phong. Bo qua luot cua nguoi nay va tiep tuc van.";
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

        resetRoundTrackingForNewGame(room);
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

        InstantWinWinner instantWin = detectInstantWinWinner(room, seatOrder);
        if (instantWin != null) {
            room.gameOver = true;
            room.winnerUserId = instantWin.userId();
            room.currentTurnUserId = null;
            room.controlUserId = null;
            applyRoundSettlement(room, instantWin.userId(), "toi trang (" + instantWin.ruleLabel() + ")");
            return ActionResult.ok(snapshotOf(room), "GAME_OVER");
        }

        room.statusMessage = "Van da bat dau. Nguoi giu 3S danh truoc.";

        return ActionResult.ok(snapshotOf(room), "GAME_STARTED");
    }

    public synchronized AutoFillStartResult autoFillBotsAndStart(String roomId) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        if (room == null || room.started || room.players.isEmpty()) {
            return AutoFillStartResult.noChange(snapshotOf(room));
        }
        if (!containsHumanPlayer(room)) {
            rooms.remove(room.roomId);
            return AutoFillStartResult.noChange(null);
        }
        if (room.players.size() >= PLAYER_LIMIT) {
            return AutoFillStartResult.noChange(snapshotOf(room));
        }

        int addedBotCount = 0;
        while (room.players.size() < PLAYER_LIMIT) {
            String botId = nextBotUserId(room);
            String botName = "Bot " + (countBots(room) + 1);
            room.players.put(botId, new PlayerState(botId, botName, "/uploads/avatars/default-opponent.jpg", room.players.size(), true));
            addedBotCount++;
        }
        reseat(room);

        String starterUserId = room.players.keySet().stream().findFirst().orElse(null);
        if (starterUserId == null) {
            return AutoFillStartResult.noChange(snapshotOf(room));
        }

        ActionResult start = startGame(roomId, starterUserId);
        if (!start.ok()) {
            return AutoFillStartResult.error(start.error(), start.room());
        }

        if (!room.gameOver) {
            room.statusMessage = "Cho qua lau, da them " + addedBotCount + " bot de bat dau van.";
        }
        return AutoFillStartResult.started(addedBotCount, ActionResult.ok(snapshotOf(room), start.eventType()));
    }

    public synchronized ActionResult botTakeTurn(String roomId) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        if (room == null) {
            return ActionResult.error("Room not found", null);
        }
        if (!room.started || room.gameOver) {
            return ActionResult.error("Game is not active", snapshotOf(room));
        }
        String currentUserId = room.currentTurnUserId;
        if (!isBotPlayer(room, currentUserId)) {
            return ActionResult.error("Current turn is not a bot", snapshotOf(room));
        }

        List<TienLenCard> hand = room.hands.get(currentUserId);
        if (hand == null || hand.isEmpty()) {
            return ActionResult.error("Bot khong co bai hop le", snapshotOf(room));
        }

        List<String> move = chooseBotMove(room, currentUserId);
        if (move != null && !move.isEmpty()) {
            return playCards(roomId, currentUserId, move);
        }

        if (room.currentCombination != null && !currentUserId.equals(room.controlUserId)) {
            return passTurn(roomId, currentUserId);
        }

        String fallbackCode = hand.stream().sorted(TienLenCard.NATURAL_ORDER).findFirst().map(TienLenCard::code).orElse(null);
        if (fallbackCode == null) {
            return ActionResult.error("Bot khong tim thay nuoc di", snapshotOf(room));
        }
        return playCards(roomId, currentUserId, List.of(fallbackCode));
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
            return ActionResult.error("Nuoc dau tien phai chua la 3S", snapshotOf(room));
        }

        if (room.currentCombination != null) {
            if (!combo.canBeat(room.currentCombination)) {
                return ActionResult.error("Bo bai khong de hon bo bai hien tai tren ban", snapshotOf(room));
            }
        }
        SpecialBeatPenalty specialBeatPenalty = detectSpecialBeatPenalty(combo, room.currentCombination, room.currentTrickCards);
        String choppedUserId = room.currentTrickOwnerUserId;

        removeCardsFromHand(hand, selected);
        hand.sort(TienLenCard.NATURAL_ORDER);
        PlayerState player = room.players.get(uid);
        if (player != null) {
            player.roundPlayedCardCount += selected.size();
        }

        room.currentCombination = combo;
        room.currentTrickCards = List.copyOf(selected.stream().sorted(TienLenCard.NATURAL_ORDER).toList());
        room.currentTrickOwnerUserId = uid;
        room.controlUserId = uid;
        room.passedUsers.clear();
        room.playCount++;

        String specialBeatMessage = recordSpecialBeatPenalty(room, uid, choppedUserId, specialBeatPenalty);

        if (hand.isEmpty()) {
            room.gameOver = true;
            room.winnerUserId = uid;
            room.currentTurnUserId = null;
            applyRoundSettlement(room, uid, "het bai");
            return ActionResult.ok(snapshotOf(room), "GAME_OVER");
        }

        room.currentTurnUserId = nextPlayerId(room, uid);
        room.statusMessage = room.players.get(uid).displayName + " da danh " + combo.label;
        if (specialBeatMessage != null && !specialBeatMessage.isBlank()) {
            room.statusMessage = room.statusMessage + ". " + specialBeatMessage;
        }
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
        removeBotPlayers(room);
        if (room.players.isEmpty()) {
            rooms.remove(roomId);
            return null;
        }
        reseat(room);
        resetToWaiting(room, "Van dau da ket thuc. Dang cho nguoi choi moi gia nhap.");
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
                p.bot,
                room.hands.getOrDefault(p.userId, List.of()).size(),
                p.score,
                p.lastRoundDelta,
                p.lastRoundChopDelta,
                p.lastRoundPenalty,
                p.lastRoundCong,
                p.lastRoundTwos,
                p.lastRoundSpecialPenalty
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
            trick,
            room.lastRoundSummary,
            room.roundNumber
        );
    }

    private boolean containsHumanPlayer(RoomState room) {
        if (room == null) {
            return false;
        }
        return room.players.values().stream().anyMatch(player -> !player.bot);
    }

    private int countBots(RoomState room) {
        if (room == null) {
            return 0;
        }
        return (int) room.players.values().stream().filter(player -> player.bot).count();
    }

    private void removeBotPlayers(RoomState room) {
        if (room == null || room.players.isEmpty()) {
            return;
        }
        boolean clearCurrentTurn = isBotPlayer(room, room.currentTurnUserId);
        boolean clearControl = isBotPlayer(room, room.controlUserId);
        boolean clearTrickOwner = isBotPlayer(room, room.currentTrickOwnerUserId);
        List<String> botIds = room.players.entrySet().stream()
            .filter(entry -> entry.getValue().bot)
            .map(Map.Entry::getKey)
            .toList();
        if (botIds.isEmpty()) {
            return;
        }
        for (String botId : botIds) {
            room.players.remove(botId);
            room.hands.remove(botId);
            room.passedUsers.remove(botId);
        }
        if (clearCurrentTurn) {
            room.currentTurnUserId = null;
        }
        if (clearControl) {
            room.controlUserId = null;
        }
        if (clearTrickOwner) {
            room.currentTrickOwnerUserId = null;
            room.currentCombination = null;
            room.currentTrickCards = List.of();
        }
    }

    private void updateWaitingStatus(RoomState room) {
        if (room == null || room.started) {
            return;
        }
        int currentPlayers = room.players.size();
        if (currentPlayers <= 0) {
            room.statusMessage = "Cho du 4 nguoi de bat dau";
            return;
        }
        if (currentPlayers >= PLAYER_LIMIT) {
            room.statusMessage = "Da du 4 nguoi. San sang bat dau.";
            return;
        }
        int missing = PLAYER_LIMIT - currentPlayers;
        room.statusMessage = "Dang co " + currentPlayers + "/" + PLAYER_LIMIT + " nguoi. Con thieu " + missing + " nguoi de bat dau.";
    }

    private String nextBotUserId(RoomState room) {
        int index = 1;
        while (true) {
            String candidate = BOT_USER_ID_PREFIX + index;
            if (room == null || !room.players.containsKey(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    private boolean isBotPlayer(RoomState room, String userId) {
        if (room == null || userId == null) {
            return false;
        }
        PlayerState player = room.players.get(userId);
        return player != null && player.bot;
    }

    private List<String> chooseBotMove(RoomState room, String userId) {
        if (room == null || userId == null) {
            return null;
        }
        List<TienLenCard> hand = room.hands.get(userId);
        if (hand == null || hand.isEmpty()) {
            return null;
        }

        List<Combination> candidates = allBotCandidates(hand);
        if (candidates.isEmpty()) {
            return null;
        }

        if (room.currentCombination == null) {
            if (room.playCount == 0) {
                candidates = candidates.stream()
                    .filter(combo -> combo.cards().stream().anyMatch(card -> "3S".equals(card.code())))
                    .toList();
                if (candidates.isEmpty()) {
                    return null;
                }
            }
            Combination opening = firstOf(candidates);
            return opening.cards().stream().map(TienLenCard::code).toList();
        }

        Combination current = room.currentCombination;
        List<Combination> beaters = candidates.stream()
            .filter(combo -> combo.canBeat(current))
            .toList();
        if (beaters.isEmpty()) {
            return null;
        }

        Comparator<Combination> beatingComparator = Comparator
            .comparing((Combination combo) -> combo.type() != current.type() || combo.length() != current.length())
            .thenComparing(this::botComboSortKey);
        Combination choice = beaters.stream().sorted(beatingComparator).findFirst().orElse(firstOf(beaters));
        return choice.cards().stream().map(TienLenCard::code).toList();
    }

    private List<Combination> allBotCandidates(List<TienLenCard> hand) {
        List<TienLenCard> sortedHand = hand.stream().sorted(TienLenCard.NATURAL_ORDER).toList();
        int n = sortedHand.size();
        if (n == 0) {
            return List.of();
        }
        List<Combination> candidates = new ArrayList<>();
        int subsetCount = 1 << n;
        for (int mask = 1; mask < subsetCount; mask++) {
            List<TienLenCard> subset = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(sortedHand.get(i));
                }
            }
            Combination combo = parseCombination(subset);
            if (combo != null) {
                candidates.add(combo);
            }
        }
        candidates.sort(Comparator.comparing(this::botComboSortKey));
        return candidates;
    }

    private String botComboSortKey(Combination combo) {
        if (combo == null) {
            return "9";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(botTypeOrder(combo.type())).append('|');
        sb.append(String.format(Locale.ROOT, "%02d", combo.length())).append('|');
        sb.append(String.format(Locale.ROOT, "%02d", combo.highestRank())).append('|');
        sb.append(String.format(Locale.ROOT, "%02d", combo.highestSuit())).append('|');
        for (TienLenCard card : combo.cards()) {
            sb.append(String.format(Locale.ROOT, "%02d%02d", card.rankValue(), card.suitOrder())).append(',');
        }
        return sb.toString();
    }

    private int botTypeOrder(CombinationType type) {
        if (type == null) {
            return 9;
        }
        return switch (type) {
            case SINGLE -> 0;
            case PAIR -> 1;
            case TRIPLE -> 2;
            case STRAIGHT -> 3;
            case FOUR_KIND -> 4;
            case DOUBLE_STRAIGHT -> 5;
        };
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
        clearRoundTransientTracking(room);
        updateWaitingStatus(room);
        if (message != null && !message.isBlank()) {
            room.statusMessage = message;
        }
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
            return firstOf(order);
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
            TienLenCard c = firstOf(sorted);
            return new Combination(CombinationType.SINGLE, 1, c.rankValue(), c.suitOrder(), sorted, "don " + c.label());
        }

        boolean sameRank = sorted.stream().map(TienLenCard::rankValue).distinct().count() == 1;
        if (sameRank && size == 2) {
            TienLenCard hi = lastOf(sorted);
            return new Combination(CombinationType.PAIR, 2, hi.rankValue(), hi.suitOrder(), sorted, "doi " + rankLabel(hi.rankValue()));
        }
        if (sameRank && size == 3) {
            TienLenCard hi = lastOf(sorted);
            return new Combination(CombinationType.TRIPLE, 3, hi.rankValue(), hi.suitOrder(), sorted, "sam " + rankLabel(hi.rankValue()));
        }
        if (sameRank && size == 4) {
            TienLenCard hi = lastOf(sorted);
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
                    TienLenCard hi = lastOf(sorted);
                    int pairCount = size / 2;
                    return new Combination(
                        CombinationType.DOUBLE_STRAIGHT,
                        size,
                        hi.rankValue(),
                        hi.suitOrder(),
                        sorted,
                        "doi thong " + pairCount + " doi (" + firstOf(sorted).label() + " - " + hi.label() + ")"
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
            TienLenCard hi = lastOf(sorted);
            return new Combination(CombinationType.STRAIGHT, size, hi.rankValue(), hi.suitOrder(), sorted,
                "sanh " + size + " la (" + firstOf(sorted).label() + " - " + hi.label() + ")");
        }

        return null;
    }

    private InstantWinWinner detectInstantWinWinner(RoomState room, List<String> seatOrder) {
        if (room == null || seatOrder == null || seatOrder.isEmpty()) {
            return null;
        }
        for (String userId : seatOrder) {
            List<TienLenCard> hand = room.hands.get(userId);
            InstantWinRule rule = detectInstantWinRule(hand);
            if (rule != null) {
                return new InstantWinWinner(userId, rule.label());
            }
        }
        return null;
    }

    private InstantWinRule detectInstantWinRule(List<TienLenCard> hand) {
        if (hand == null || hand.size() != 13) {
            return null;
        }
        int[] rankCounts = rankCounts(hand);

        if (hasDragonStraightThreeToAce(rankCounts)) {
            return InstantWinRule.DRAGON_STRAIGHT;
        }
        if (hasConsecutivePairs(rankCounts, 5)) {
            return InstantWinRule.FIVE_CONSECUTIVE_PAIRS;
        }
        if (pairCount(rankCounts) >= 6) {
            return InstantWinRule.SIX_PAIRS;
        }
        if (rankCounts[15] == 4) {
            return InstantWinRule.FOUR_TWOS;
        }
        return null;
    }

    private boolean hasDragonStraightThreeToAce(int[] rankCounts) {
        for (int rank = 3; rank <= 14; rank++) {
            if (rankCounts[rank] <= 0) {
                return false;
            }
        }
        return true;
    }

    private boolean hasConsecutivePairs(int[] rankCounts, int requiredPairs) {
        if (rankCounts == null || requiredPairs <= 0) {
            return false;
        }
        int streak = 0;
        for (int rank = 3; rank <= 14; rank++) {
            if (rankCounts[rank] >= 2) {
                streak++;
                if (streak >= requiredPairs) {
                    return true;
                }
            } else {
                streak = 0;
            }
        }
        return false;
    }

    private int pairCount(int[] rankCounts) {
        if (rankCounts == null) {
            return 0;
        }
        int pairs = 0;
        for (int rank = 3; rank <= 15; rank++) {
            pairs += rankCounts[rank] / 2;
        }
        return pairs;
    }

    private int[] rankCounts(List<TienLenCard> hand) {
        int[] counts = new int[16];
        if (hand == null) {
            return counts;
        }
        for (TienLenCard card : hand) {
            if (card == null) {
                continue;
            }
            int rank = card.rankValue();
            if (rank >= 0 && rank < counts.length) {
                counts[rank]++;
            }
        }
        return counts;
    }

    private void resetRoundTrackingForNewGame(RoomState room) {
        if (room == null) {
            return;
        }
        room.lastRoundSummary = null;
        room.roundPenaltyEvents.clear();
        room.roundNumber++;
        for (PlayerState player : room.players.values()) {
            player.lastRoundDelta = 0;
            player.lastRoundChopDelta = 0;
            player.lastRoundPenalty = 0;
            player.lastRoundCong = false;
            player.lastRoundTwos = 0;
            player.lastRoundSpecialPenalty = 0;
            player.roundPlayedCardCount = 0;
            player.roundSideBetDelta = 0;
        }
    }

    private void clearRoundTransientTracking(RoomState room) {
        if (room == null) {
            return;
        }
        for (PlayerState player : room.players.values()) {
            player.roundPlayedCardCount = 0;
        }
    }

    private void applyRoundSettlement(RoomState room, String winnerUserId, String finishReason) {
        if (room == null || winnerUserId == null) {
            return;
        }
        PlayerState winner = room.players.get(winnerUserId);
        if (winner == null) {
            return;
        }

        int winnerGain = 0;
        List<String> loserSummaries = new ArrayList<>();
        List<String> chopEvents = List.copyOf(room.roundPenaltyEvents);
        for (PlayerState player : room.players.values()) {
            player.lastRoundDelta = player.roundSideBetDelta;
            player.lastRoundChopDelta = player.roundSideBetDelta;
            player.lastRoundPenalty = 0;
            player.lastRoundCong = false;
            player.lastRoundTwos = 0;
            player.lastRoundSpecialPenalty = 0;
        }

        for (PlayerState player : room.players.values()) {
            if (winnerUserId.equals(player.userId)) {
                continue;
            }
            List<TienLenCard> remainingHand = room.hands.getOrDefault(player.userId, List.of());
            PenaltyBreakdown penalty = calculateLoserPenalty(remainingHand, player.roundPlayedCardCount <= 0);
            int totalPenalty = penalty.total();

            player.lastRoundPenalty = totalPenalty;
            player.lastRoundCong = penalty.cong();
            player.lastRoundTwos = penalty.twoCount();
            player.lastRoundSpecialPenalty = penalty.specialPenalty();
            player.lastRoundDelta -= totalPenalty;
            player.score += player.lastRoundDelta;

            winnerGain += totalPenalty;
            loserSummaries.add(buildLoserSettlementSummary(player, penalty, remainingHand.size()));
        }

        winner.lastRoundDelta += winnerGain;
        winner.lastRoundChopDelta = winner.roundSideBetDelta;
        winner.lastRoundPenalty = 0;
        winner.lastRoundCong = false;
        winner.lastRoundTwos = 0;
        winner.lastRoundSpecialPenalty = 0;
        winner.score += winner.lastRoundDelta;

        clearRoundTransientTracking(room);

        String winnerName = displayNameOf(room, winnerUserId);
        String reason = (finishReason == null || finishReason.isBlank()) ? "chien thang" : finishReason;
        room.lastRoundSummary = buildRoundSummary(winnerName, winnerGain, chopEvents, loserSummaries);
        room.statusMessage = winnerName + " " + reason + ". " + room.lastRoundSummary;
    }

    private String buildRoundSummary(String winnerName, int winnerGain, List<String> chopEvents, List<String> loserSummaries) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tinh diem van: ").append(winnerName).append(" +").append(winnerGain);
        if (chopEvents != null && !chopEvents.isEmpty()) {
            sb.append(" | Chat: ").append(String.join(" ; ", chopEvents));
        }
        if (loserSummaries != null && !loserSummaries.isEmpty()) {
            sb.append(" | ");
            sb.append(String.join(" ; ", loserSummaries));
        }
        return sb.toString();
    }

    private String buildLoserSettlementSummary(PlayerState player, PenaltyBreakdown penalty, int remainingCards) {
        String name = player == null ? "Unknown" : normalizeDisplayName(player.displayName, player.userId);
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" -").append(penalty.total());
        sb.append(" (con ").append(Math.max(0, remainingCards)).append(" la");
        if (penalty.cong()) {
            sb.append(", cong x2 +13");
        }
        if (penalty.twoCount() > 0) {
            sb.append(", thoi ").append(penalty.twoCount()).append(" heo");
        }
        if (penalty.specialPenalty() > 0) {
            sb.append(", thoi hang +").append(penalty.specialPenalty());
        }
        sb.append(")");
        return sb.toString();
    }

    private PenaltyBreakdown calculateLoserPenalty(List<TienLenCard> hand, boolean cong) {
        List<TienLenCard> safeHand = hand == null ? List.of() : hand;
        int base = safeHand.size();
        int baseAfterCongMultiplier = cong ? (base * 2) : base;
        int twoCount = countRank(safeHand, 15);
        int twoPenalty = twoPenaltyPoints(safeHand);
        int specialPenalty = countLeftoverSpecialPenalty(safeHand);
        int congPenalty = cong ? PENALTY_CONG_BONUS : 0;
        int total = baseAfterCongMultiplier + congPenalty + twoPenalty + specialPenalty;
        return new PenaltyBreakdown(total, cong, twoCount, specialPenalty);
    }

    private int countLeftoverSpecialPenalty(List<TienLenCard> hand) {
        if (hand == null || hand.isEmpty()) {
            return 0;
        }
        int[] counts = rankCounts(hand);
        int penalty = 0;
        for (int rank = 3; rank <= 15; rank++) {
            if (counts[rank] >= 4) {
                penalty += PENALTY_LEFTOVER_FOUR_KIND;
            }
        }

        int streak = 0;
        for (int rank = 3; rank <= 14; rank++) {
            if (counts[rank] >= 2) {
                streak++;
            } else {
                if (streak >= 3) {
                    penalty += doubleStraightLeftoverPenaltyPoints(streak);
                }
                streak = 0;
            }
        }
        if (streak >= 3) {
            penalty += doubleStraightLeftoverPenaltyPoints(streak);
        }
        return penalty;
    }

    private int doubleStraightLeftoverPenaltyPoints(int pairCount) {
        if (pairCount <= 0) {
            return 0;
        }
        if (pairCount >= 5) {
            return 15;
        }
        if (pairCount == 4) {
            return 10;
        }
        if (pairCount == 3) {
            return 6;
        }
        return 0;
    }

    private int countRank(List<TienLenCard> hand, int rankValue) {
        if (hand == null || hand.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (TienLenCard card : hand) {
            if (card != null && card.rankValue() == rankValue) {
                count++;
            }
        }
        return count;
    }

    private int twoPenaltyPoints(List<TienLenCard> hand) {
        if (hand == null || hand.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (TienLenCard card : hand) {
            total += twoCardPenaltyPoints(card);
        }
        return total;
    }

    private SpecialBeatPenalty detectSpecialBeatPenalty(Combination challenger, Combination current, List<TienLenCard> currentCards) {
        if (challenger == null || current == null) {
            return null;
        }
        if (current.type == CombinationType.SINGLE && current.highestRank == 15) {
            int points = singleTwoPenaltyPoints(currentCards);
            return points <= 0 ? null : new SpecialBeatPenalty(points, "chat 1 heo");
        }
        if (current.type == CombinationType.PAIR && current.highestRank == 15) {
            int points = pairTwoPenaltyPoints(currentCards);
            return points <= 0 ? null : new SpecialBeatPenalty(points, "chat doi heo");
        }
        if (current.type == CombinationType.FOUR_KIND
            && challenger.type == CombinationType.DOUBLE_STRAIGHT
            && challenger.length >= 8) {
            int pairCount = challenger.length / 2;
            int points = pairCount >= 5 ? 20 : 16;
            return new SpecialBeatPenalty(points, "chat tu quy");
        }
        if (current.type == CombinationType.FOUR_KIND
            && challenger.type == CombinationType.FOUR_KIND) {
            return new SpecialBeatPenalty(PENALTY_LEFTOVER_FOUR_KIND, "chat tu quy");
        }
        if (current.type == CombinationType.DOUBLE_STRAIGHT
            && challenger.type == CombinationType.DOUBLE_STRAIGHT
            && challenger.length > current.length) {
            int pairCount = Math.max(3, current.length / 2);
            return new SpecialBeatPenalty(pairCount * 3, "chat doi thong " + pairCount + " doi");
        }
        return null;
    }

    private int singleTwoPenaltyPoints(List<TienLenCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return 0;
        }
        return twoCardPenaltyPoints(firstOf(cards));
    }

    private int pairTwoPenaltyPoints(List<TienLenCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (TienLenCard card : cards) {
            if (card != null && card.rankValue() == 15) {
                sum += twoCardPenaltyPoints(card);
            }
        }
        return sum;
    }

    private int twoCardPenaltyPoints(TienLenCard card) {
        if (card == null || card.rankValue() != 15) {
            return 0;
        }
        return card.suitOrder() >= 2 ? PENALTY_TWO_RED : PENALTY_TWO_BLACK;
    }

    private <T> T firstOf(List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("List is empty");
        }
        return list.get(0);
    }

    private <T> T lastOf(List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("List is empty");
        }
        return list.get(list.size() - 1);
    }

    private String recordSpecialBeatPenalty(RoomState room,
                                            String chopperUserId,
                                            String victimUserId,
                                            SpecialBeatPenalty penalty) {
        if (room == null || penalty == null || penalty.points() <= 0) {
            return null;
        }
        if (chopperUserId == null || victimUserId == null || Objects.equals(chopperUserId, victimUserId)) {
            return null;
        }
        PlayerState chopper = room.players.get(chopperUserId);
        PlayerState victim = room.players.get(victimUserId);
        if (chopper == null || victim == null) {
            return null;
        }
        chopper.roundSideBetDelta += penalty.points();
        victim.roundSideBetDelta -= penalty.points();
        String event = displayNameOf(room, chopperUserId) + " " + penalty.label()
            + " cua " + displayNameOf(room, victimUserId)
            + " (+" + penalty.points() + ")";
        room.roundPenaltyEvents.add(event);
        return event;
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

    private enum InstantWinRule {
        DRAGON_STRAIGHT("sanh rong 3-A"),
        FIVE_CONSECUTIVE_PAIRS("5 doi thong"),
        SIX_PAIRS("6 doi"),
        FOUR_TWOS("tu quy 2");

        private final String label;

        InstantWinRule(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
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
        private final boolean bot;
        private String displayName;
        private String avatarPath;
        private int seatIndex;
        private int score;
        private int lastRoundDelta;
        private int lastRoundChopDelta;
        private int lastRoundPenalty;
        private boolean lastRoundCong;
        private int lastRoundTwos;
        private int lastRoundSpecialPenalty;
        private int roundPlayedCardCount;
        private int roundSideBetDelta;

        private PlayerState(String userId, String displayName, String avatarPath, int seatIndex, boolean bot) {
            this.userId = userId;
            this.bot = bot;
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
        private int roundNumber;
        private String lastRoundSummary;
        private final List<String> roundPenaltyEvents = new ArrayList<>();
        private String statusMessage = "Cho du 4 nguoi de bat dau";

        private RoomState(String roomId) {
            this.roomId = roomId;
        }
    }

    private record InstantWinWinner(String userId, String ruleLabel) {
    }

    private record PenaltyBreakdown(
        int total,
        boolean cong,
        int twoCount,
        int specialPenalty
    ) {
    }

    private record SpecialBeatPenalty(int points, String label) {
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

    public record AutoFillStartResult(
        boolean changed,
        boolean started,
        int addedBotCount,
        String error,
        RoomSnapshot room
    ) {
        public static AutoFillStartResult noChange(RoomSnapshot room) {
            return new AutoFillStartResult(false, false, 0, null, room);
        }

        public static AutoFillStartResult error(String error, RoomSnapshot room) {
            return new AutoFillStartResult(false, false, 0, error, room);
        }

        public static AutoFillStartResult started(int addedBotCount, ActionResult startResult) {
            RoomSnapshot room = startResult == null ? null : startResult.room();
            String error = startResult == null ? null : startResult.error();
            return new AutoFillStartResult(true, true, Math.max(0, addedBotCount), error, room);
        }
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
        TrickSnapshot currentTrick,
        String lastRoundSummary,
        int roundNumber
    ) {
    }

    public record PlayerSnapshot(
        String userId,
        String displayName,
        String avatarPath,
        int seatIndex,
        boolean bot,
        int handCount,
        int score,
        int lastRoundDelta,
        int lastRoundChopDelta,
        int lastRoundPenalty,
        boolean lastRoundCong,
        int lastRoundTwos,
        int lastRoundSpecialPenalty
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
