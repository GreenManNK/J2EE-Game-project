package com.game.hub.games.monopoly.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MonopolyRoomService {
    static final int MIN_PLAYERS = 2;
    static final int MAX_PLAYERS = 4;
    static final int DEFAULT_STARTING_CASH = 1500;
    static final int DEFAULT_PASS_GO_AMOUNT = 200;
    static final List<String> TOKEN_POOL = List.of("dog", "car", "hat", "ship", "cat", "boot");
    private static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final Map<String, RoomState> rooms = new ConcurrentHashMap<>();
    private final Random random;
    private final MonopolyGameEngine gameEngine;

    public MonopolyRoomService() {
        this(new SecureRandom(), new MonopolyGameEngine());
    }

    MonopolyRoomService(Random random) {
        this(random, new MonopolyGameEngine());
    }

    MonopolyRoomService(Random random, MonopolyGameEngine gameEngine) {
        this.random = Objects.requireNonNull(random);
        this.gameEngine = Objects.requireNonNull(gameEngine);
    }

    public synchronized RoomActionResult createRoom(CreateRoomCommand command) {
        String playerName = normalizeDisplayName(command.playerName(), "Chu phong");
        String playerId = normalizePlayerId(command.playerId());
        if (playerId == null) {
            playerId = nextPlayerId();
        }

        String roomId = nextRoomId();
        RoomState room = new RoomState(
            roomId,
            normalizeRoomName(command.roomName(), playerName),
            clampPlayerLimit(command.maxPlayers()),
            normalizePositive(command.startingCash(), DEFAULT_STARTING_CASH),
            normalizePositive(command.passGoAmount(), DEFAULT_PASS_GO_AMOUNT)
        );

        room.hostPlayerId = playerId;
        room.players.put(playerId, new PlayerState(playerId, playerName, 0, true));
        touch(room);
        rooms.put(roomId, room);
        return RoomActionResult.ok(snapshotOf(room), playerId);
    }

    public synchronized RoomActionResult joinRoom(String roomId, JoinRoomCommand command) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        if (room == null) {
            return RoomActionResult.error("Khong tim thay phong", null);
        }

        String playerId = normalizePlayerId(command.playerId());
        String playerName = normalizeDisplayName(command.playerName(), "Nguoi choi");
        if (playerId != null) {
            PlayerState existing = room.players.get(playerId);
            if (existing != null) {
                existing.name = playerName;
                touch(room);
                return RoomActionResult.ok(snapshotOf(room), playerId);
            }
        }

        if (room.status != RoomStatus.WAITING) {
            return RoomActionResult.error("Phong da bat dau, khong the tham gia them", snapshotOf(room));
        }
        if (room.players.size() >= room.maxPlayers) {
            return RoomActionResult.error("Phong da day", snapshotOf(room));
        }

        String resolvedPlayerId = playerId == null ? nextPlayerId() : playerId;
        room.players.put(resolvedPlayerId, new PlayerState(resolvedPlayerId, playerName, room.players.size(), false));
        touch(room);
        return RoomActionResult.ok(snapshotOf(room), resolvedPlayerId);
    }

    public synchronized RoomActionResult selectToken(String roomId, TokenSelectionCommand command) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        if (room == null) {
            return RoomActionResult.error("Khong tim thay phong", null);
        }
        if (room.status != RoomStatus.WAITING) {
            return RoomActionResult.error("Phong da bat dau, khong doi token nua", snapshotOf(room));
        }

        String playerId = normalizePlayerId(command.playerId());
        PlayerState player = room.players.get(playerId);
        if (player == null) {
            return RoomActionResult.error("Nguoi choi khong nam trong phong", snapshotOf(room));
        }

        String token = normalizeToken(command.token());
        if (token == null) {
            return RoomActionResult.error("Token khong hop le", snapshotOf(room));
        }

        for (PlayerState candidate : room.players.values()) {
            if (!candidate.playerId.equals(player.playerId) && token.equals(candidate.token)) {
                return RoomActionResult.error("Token nay da co nguoi chon", snapshotOf(room));
            }
        }

        player.token = token;
        touch(room);
        return RoomActionResult.ok(snapshotOf(room), playerId);
    }

    public synchronized RoomActionResult startRoom(String roomId, StartRoomCommand command) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        if (room == null) {
            return RoomActionResult.error("Khong tim thay phong", null);
        }

        String playerId = normalizePlayerId(command.playerId());
        if (!Objects.equals(room.hostPlayerId, playerId)) {
            return RoomActionResult.error("Chi host moi duoc bat dau game", snapshotOf(room));
        }
        if (room.status != RoomStatus.WAITING) {
            return RoomActionResult.error("Phong khong o trang thai cho", snapshotOf(room));
        }
        if (room.players.size() < MIN_PLAYERS) {
            return RoomActionResult.error("Can it nhat 2 nguoi choi de bat dau", snapshotOf(room));
        }
        boolean missingToken = room.players.values().stream()
            .anyMatch(player -> player.token == null || player.token.isBlank());
        if (missingToken) {
            return RoomActionResult.error("Tat ca nguoi choi phai chon token truoc khi bat dau", snapshotOf(room));
        }

        room.status = RoomStatus.PLAYING;
        room.version = 1;
        room.gameState = gameEngine.createInitialState(
            room.players.values().stream()
                .sorted(Comparator.comparingInt(player -> player.turnOrder))
                .map(player -> new MonopolyGameEngine.PlayerSeed(player.playerId, player.name, player.token, player.turnOrder))
                .toList(),
            room.startingCash,
            room.passGoAmount,
            random
        );
        touch(room);
        return RoomActionResult.ok(snapshotOf(room), playerId);
    }

    public synchronized RoomActionResult leaveRoom(String roomId, LeaveRoomCommand command) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        if (room == null) {
            return RoomActionResult.error("Khong tim thay phong", null);
        }

        String playerId = normalizePlayerId(command.playerId());
        if (playerId == null || !room.players.containsKey(playerId)) {
            return RoomActionResult.error("Nguoi choi khong nam trong phong", snapshotOf(room));
        }
        if (room.status == RoomStatus.PLAYING) {
            return RoomActionResult.error("Van dang dien ra, hien chua ho tro roi phong giua tran", snapshotOf(room));
        }

        room.players.remove(playerId);
        if (room.players.isEmpty()) {
            rooms.remove(room.roomId);
            return RoomActionResult.ok(null, playerId);
        }

        if (Objects.equals(room.hostPlayerId, playerId)) {
            PlayerState nextHost = room.players.values().stream().findFirst().orElse(null);
            room.hostPlayerId = nextHost == null ? null : nextHost.playerId;
        }

        int turnOrder = 0;
        for (PlayerState player : room.players.values()) {
            player.turnOrder = turnOrder;
            player.host = Objects.equals(player.playerId, room.hostPlayerId);
            turnOrder += 1;
        }

        touch(room);
        return RoomActionResult.ok(snapshotOf(room), playerId);
    }

    public synchronized RoomActionResult syncGameState(String roomId, SyncGameStateCommand command) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        if (room == null) {
            return RoomActionResult.error("Khong tim thay phong", null);
        }
        if (room.status != RoomStatus.PLAYING && room.status != RoomStatus.FINISHED) {
            return RoomActionResult.error("Phong chua o trang thai choi", snapshotOf(room));
        }

        String actorPlayerId = normalizePlayerId(command.playerId());
        if (actorPlayerId == null || !room.players.containsKey(actorPlayerId)) {
            return RoomActionResult.error("Nguoi choi khong hop le", snapshotOf(room));
        }
        if (command.baseVersion() != room.version) {
            return RoomActionResult.error("State phong da thay doi. Hay dong bo lai truoc.", snapshotOf(room));
        }
        if (command.gameState() == null || command.gameState().isEmpty()) {
            return RoomActionResult.error("Game state trong", snapshotOf(room));
        }

        if (room.gameState == null) {
            if (!Objects.equals(room.hostPlayerId, actorPlayerId)) {
                return RoomActionResult.error("Chi host moi duoc khoi tao state van dau", snapshotOf(room));
            }
        } else {
            String currentTurnPlayerId = extractCurrentTurnPlayerId(room.gameState);
            if (currentTurnPlayerId != null && !Objects.equals(currentTurnPlayerId, actorPlayerId)) {
                return RoomActionResult.error("Chua den luot cua ban", snapshotOf(room));
            }
        }

        room.gameState = castMap(deepCopy(command.gameState()));
        room.version += 1;
        if (isFinishedState(room.gameState)) {
            room.status = RoomStatus.FINISHED;
        } else if (room.status == RoomStatus.FINISHED) {
            room.status = RoomStatus.PLAYING;
        }
        touch(room);
        return RoomActionResult.ok(snapshotOf(room), actorPlayerId);
    }

    public synchronized RoomActionResult performAction(String roomId, RoomGameActionCommand command) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        if (room == null) {
            return RoomActionResult.error("Khong tim thay phong", null);
        }
        if (room.status != RoomStatus.PLAYING) {
            return RoomActionResult.error("Phong khong o trong tran dang choi", snapshotOf(room));
        }
        String actorPlayerId = normalizePlayerId(command.playerId());
        if (actorPlayerId == null || !room.players.containsKey(actorPlayerId)) {
            return RoomActionResult.error("Nguoi choi khong hop le", snapshotOf(room));
        }
        if (room.gameState == null || room.gameState.isEmpty()) {
            return RoomActionResult.error("Room chua co game state", snapshotOf(room));
        }

        MonopolyGameEngine.ActionResult result = gameEngine.applyAction(room.gameState, actorPlayerId, command.action(), random);
        if (!result.success()) {
            return RoomActionResult.error(result.error(), snapshotOf(room));
        }

        room.gameState = result.gameState();
        room.version += 1;
        if (isFinishedState(room.gameState)) {
            room.status = RoomStatus.FINISHED;
        }
        touch(room);
        return RoomActionResult.ok(snapshotOf(room), actorPlayerId);
    }

    public synchronized RoomSnapshot roomSnapshot(String roomId) {
        RoomState room = rooms.get(normalizeRoomId(roomId));
        return room == null ? null : snapshotOf(room);
    }

    public synchronized List<RoomListItem> availableRooms() {
        List<RoomListItem> rows = new ArrayList<>();
        for (RoomState room : rooms.values()) {
            if (room.status != RoomStatus.WAITING) {
                continue;
            }
            String hostName = room.players.containsKey(room.hostPlayerId)
                ? room.players.get(room.hostPlayerId).name
                : "Host";
            rows.add(new RoomListItem(
                room.roomId,
                room.roomName,
                room.players.size(),
                room.maxPlayers,
                room.status,
                hostName
            ));
        }
        rows.sort(Comparator.comparing(RoomListItem::roomId));
        return rows;
    }

    private RoomSnapshot snapshotOf(RoomState room) {
        List<PlayerSnapshot> players = room.players.values().stream()
            .map(player -> new PlayerSnapshot(
                player.playerId,
                player.name,
                player.token,
                player.turnOrder,
                player.host
            ))
            .toList();
        return new RoomSnapshot(
            room.roomId,
            room.roomName,
            room.status,
            room.maxPlayers,
            MIN_PLAYERS,
            room.startingCash,
            room.passGoAmount,
            room.hostPlayerId,
            room.version,
            room.gameState != null,
            room.updatedAt,
            players,
            room.gameState == null ? null : castMap(deepCopy(room.gameState))
        );
    }

    private String extractCurrentTurnPlayerId(Map<String, Object> gameState) {
        if (gameState == null) {
            return null;
        }
        Object currentIndexValue = gameState.get("currentPlayerIndex");
        Object playersValue = gameState.get("players");
        if (!(currentIndexValue instanceof Number currentIndexNumber) || !(playersValue instanceof List<?> players)) {
            return null;
        }
        int currentIndex = currentIndexNumber.intValue();
        if (currentIndex < 0 || currentIndex >= players.size()) {
            return null;
        }
        Object selectedPlayer = players.get(currentIndex);
        if (!(selectedPlayer instanceof Map<?, ?> playerMap)) {
            return null;
        }
        Object playerIdValue = playerMap.get("id");
        if (playerIdValue == null) {
            playerIdValue = playerMap.get("playerId");
        }
        return normalizePlayerId(playerIdValue == null ? null : String.valueOf(playerIdValue));
    }

    private boolean isFinishedState(Map<String, Object> gameState) {
        if (gameState == null) {
            return false;
        }
        String phase = normalizeText(stringValue(gameState.get("phase")));
        if ("ended".equals(phase) || "finished".equals(phase)) {
            return true;
        }
        return normalizePlayerId(stringValue(gameState.get("winnerId"))) != null;
    }

    private Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                copy.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> listValue) {
            List<Object> copy = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private void touch(RoomState room) {
        room.updatedAt = Instant.now();
    }

    private int clampPlayerLimit(Integer value) {
        if (value == null) {
            return MAX_PLAYERS;
        }
        return Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, value));
    }

    private int normalizePositive(Integer value, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }

    private String normalizeRoomId(String roomId) {
        String value = normalizeText(roomId);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String normalizePlayerId(String playerId) {
        String value = normalizeText(playerId);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String name, String fallback) {
        String value = normalizeText(name);
        if (value == null) {
            return fallback;
        }
        if (value.length() > 24) {
            return value.substring(0, 24).trim();
        }
        return value;
    }

    private String normalizeRoomName(String roomName, String hostName) {
        String value = normalizeText(roomName);
        if (value == null) {
            return "Phong " + hostName;
        }
        if (value.length() > 36) {
            return value.substring(0, 36).trim();
        }
        return value;
    }

    private String normalizeToken(String token) {
        String value = normalizeText(token);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return TOKEN_POOL.contains(normalized) ? normalized : null;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nextRoomId() {
        String candidate;
        do {
            StringBuilder builder = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                builder.append(ROOM_CODE_ALPHABET.charAt(random.nextInt(ROOM_CODE_ALPHABET.length())));
            }
            candidate = builder.toString();
        } while (rooms.containsKey(candidate));
        return candidate;
    }

    private String nextPlayerId() {
        return "mp-" + Long.toString(Math.abs(random.nextLong()), 36);
    }

    static final class RoomState {
        private final String roomId;
        private final String roomName;
        private final int maxPlayers;
        private final int startingCash;
        private final int passGoAmount;
        private final Map<String, PlayerState> players = new LinkedHashMap<>();
        private String hostPlayerId;
        private RoomStatus status = RoomStatus.WAITING;
        private int version = 0;
        private Instant updatedAt = Instant.now();
        private Map<String, Object> gameState;

        private RoomState(String roomId, String roomName, int maxPlayers, int startingCash, int passGoAmount) {
            this.roomId = roomId;
            this.roomName = roomName;
            this.maxPlayers = maxPlayers;
            this.startingCash = startingCash;
            this.passGoAmount = passGoAmount;
        }
    }

    static final class PlayerState {
        private final String playerId;
        private String name;
        private String token;
        private int turnOrder;
        private boolean host;

        private PlayerState(String playerId, String name, int turnOrder, boolean host) {
            this.playerId = playerId;
            this.name = name;
            this.turnOrder = turnOrder;
            this.host = host;
        }
    }

    public record CreateRoomCommand(
        String playerId,
        String playerName,
        String roomName,
        Integer maxPlayers,
        Integer startingCash,
        Integer passGoAmount
    ) {
    }

    public record JoinRoomCommand(String playerId, String playerName) {
    }

    public record TokenSelectionCommand(String playerId, String token) {
    }

    public record StartRoomCommand(String playerId) {
    }

    public record LeaveRoomCommand(String playerId) {
    }

    public record SyncGameStateCommand(String playerId, int baseVersion, Map<String, Object> gameState) {
    }

    public record RoomGameActionCommand(String playerId, String action) {
    }

    public record RoomActionResult(boolean success, String error, RoomSnapshot room, String playerId) {
        static RoomActionResult ok(RoomSnapshot room, String playerId) {
            return new RoomActionResult(true, null, room, playerId);
        }

        static RoomActionResult error(String error, RoomSnapshot room) {
            return new RoomActionResult(false, error, room, null);
        }
    }

    public record RoomListItem(
        String roomId,
        String roomName,
        int playerCount,
        int maxPlayers,
        RoomStatus status,
        String hostName
    ) {
    }

    public record RoomSnapshot(
        String roomId,
        String roomName,
        RoomStatus status,
        int maxPlayers,
        int minPlayersToStart,
        int startingCash,
        int passGoAmount,
        String hostPlayerId,
        int version,
        boolean hasGameState,
        Instant updatedAt,
        List<PlayerSnapshot> players,
        Map<String, Object> gameState
    ) {
    }

    public record PlayerSnapshot(
        String playerId,
        String name,
        String token,
        int turnOrder,
        boolean host
    ) {
    }

    public enum RoomStatus {
        WAITING,
        PLAYING,
        FINISHED
    }
}
