package com.caro.game.cards.tienlen.websocket;

import com.caro.game.cards.tienlen.service.TienLenRoomService;
import com.caro.game.entity.UserAccount;
import com.caro.game.repository.UserAccountRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Controller
public class TienLenWebSocketController {
    private static final Logger log = LoggerFactory.getLogger(TienLenWebSocketController.class);
    private static final String AUTH_USER_ID = "AUTH_USER_ID";
    private static final String GUEST_USER_ID = "GUEST_USER_ID";
    private static final String DEFAULT_AVATAR_PATH = "/uploads/avatars/default-avatar.jpg";
    private static final long DEFAULT_AUTO_FILL_WAIT_MS = 20_000L;
    private static final long DEFAULT_BOT_TURN_DELAY_MS = 1_000L;

    private final TienLenRoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserAccountRepository userAccountRepository;
    private final Map<String, RoomPresence> sessionRoomPresence = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> autoFillTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> botTurnTasks = new ConcurrentHashMap<>();
    @Value("${app.tienlen.automation.auto-fill-wait-ms:20000}")
    private long autoFillWaitMs;
    @Value("${app.tienlen.automation.bot-turn-delay-ms:1000}")
    private long botTurnDelayMs;
    private final ScheduledExecutorService automationExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "tienlen-room-automation");
        thread.setDaemon(true);
        return thread;
    });

    public TienLenWebSocketController(TienLenRoomService roomService,
                                      SimpMessagingTemplate messagingTemplate,
                                      UserAccountRepository userAccountRepository) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
        this.userAccountRepository = userAccountRepository;
    }

    @PreDestroy
    public void shutdownAutomation() {
        autoFillTasks.values().forEach(task -> task.cancel(false));
        botTurnTasks.values().forEach(task -> task.cancel(false));
        autoFillTasks.clear();
        botTurnTasks.clear();
        automationExecutor.shutdownNow();
    }

    @MessageMapping("/tienlen.join")
    public void join(TienLenJoinMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String roomId = safeTrim(message.getRoomId());
        String userId = requireConnectionUser(roomId, message.getUserId(), headers);
        if (roomId == null || userId == null) {
            return;
        }

        PlayerMeta meta = playerMeta(userId);
        TienLenRoomService.JoinResult result = roomService.joinRoom(roomId, userId, meta.displayName(), meta.avatarPath());
        if (!result.ok()) {
            sendUserError(roomId, userId, result.error());
            if (result.room() != null) {
                broadcastRoomState(roomId, "ROOM_STATE", result.room(), result.error());
            }
            return;
        }

        rememberRoomPresence(headers, roomId, userId);
        broadcastRoomState(roomId, "ROOM_STATE", result.room(), "Da vao phong");
        sendPrivateState(roomId, userId);
        broadcastRoomList();
        refreshAutomationForRoom(roomId, result.room());
    }

    @MessageMapping("/tienlen.start")
    public void start(TienLenRoomActionMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String roomId = safeTrim(message.getRoomId());
        String userId = requireConnectionUser(roomId, message.getUserId(), headers);
        if (roomId == null || userId == null) {
            return;
        }
        TienLenRoomService.ActionResult result = roomService.startGame(roomId, userId);
        if (!result.ok()) {
            sendUserError(roomId, userId, result.error());
            if (result.room() != null) {
                broadcastRoomState(roomId, "ROOM_STATE", result.room(), result.error());
            }
            return;
        }
        broadcastRoomState(roomId, result.eventType(), result.room(), "Bat dau van moi");
        sendPrivateStates(roomId);
        broadcastRoomList();
        refreshAutomationForRoom(roomId, result.room());
    }

    @MessageMapping("/tienlen.play")
    public void play(TienLenPlayMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String roomId = safeTrim(message.getRoomId());
        String userId = requireConnectionUser(roomId, message.getUserId(), headers);
        if (roomId == null || userId == null) {
            return;
        }
        TienLenRoomService.ActionResult result = roomService.playCards(roomId, userId, message.getCardCodes());
        if (!result.ok()) {
            sendUserError(roomId, userId, result.error());
            if (result.room() != null) {
                broadcastRoomState(roomId, "ROOM_STATE", result.room(), result.error());
            }
            return;
        }
        broadcastRoomState(roomId, result.eventType(), result.room(), result.room() == null ? null : result.room().statusMessage());
        sendPrivateState(roomId, userId);
        if ("GAME_OVER".equals(result.eventType())) {
            TienLenRoomService.RoomSnapshot waitingRoom = roomService.resetToWaitingAfterGame(roomId);
            if (waitingRoom != null) {
                broadcastRoomState(roomId, "ROOM_STATE", waitingRoom, waitingRoom.statusMessage());
                sendPrivateStates(roomId);
            }
            broadcastRoomList();
            refreshAutomationForRoom(roomId, waitingRoom);
            return;
        }
        refreshAutomationForRoom(roomId, result.room());
    }

    @MessageMapping("/tienlen.pass")
    public void pass(TienLenRoomActionMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String roomId = safeTrim(message.getRoomId());
        String userId = requireConnectionUser(roomId, message.getUserId(), headers);
        if (roomId == null || userId == null) {
            return;
        }
        TienLenRoomService.ActionResult result = roomService.passTurn(roomId, userId);
        if (!result.ok()) {
            sendUserError(roomId, userId, result.error());
            if (result.room() != null) {
                broadcastRoomState(roomId, "ROOM_STATE", result.room(), result.error());
            }
            return;
        }
        broadcastRoomState(roomId, result.eventType(), result.room(), result.room() == null ? null : result.room().statusMessage());
        refreshAutomationForRoom(roomId, result.room());
    }

    @MessageMapping("/tienlen.surrender")
    public void surrender(TienLenRoomActionMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String roomId = safeTrim(message.getRoomId());
        String userId = requireConnectionUser(roomId, message.getUserId(), headers);
        if (roomId == null || userId == null) {
            return;
        }
        TienLenRoomService.LeaveResult result = roomService.leaveRoom(roomId, userId);
        if (!result.ok()) {
            sendUserError(roomId, userId, result.error());
            if (result.room() != null) {
                broadcastRoomState(roomId, "ROOM_STATE", result.room(), result.error());
            }
            return;
        }
        forgetRoomPresence(headers, roomId, userId);
        if (result.roomClosed()) {
            messagingTemplate.convertAndSend("/topic/tienlen.room." + roomId, Map.of(
                "type", "ROOM_CLOSED",
                "roomId", roomId,
                "message", "Phong da dong"
            ));
            refreshAutomationForRoom(roomId, null);
        } else {
            TienLenRoomService.RoomSnapshot room = result.room() != null ? result.room() : roomService.roomSnapshot(roomId);
            if (room != null) {
                broadcastRoomState(roomId, "ROOM_STATE", room, "Nguoi choi da dau hang va roi phong");
                sendPrivateStates(roomId);
            }
            refreshAutomationForRoom(roomId, room);
        }
        broadcastRoomList();
    }

    @MessageMapping("/tienlen.leave")
    public void leave(TienLenRoomActionMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String roomId = safeTrim(message.getRoomId());
        String userId = requireConnectionUser(roomId, message.getUserId(), headers);
        if (roomId == null || userId == null) {
            return;
        }
        roomService.leaveRoom(roomId, userId);
        forgetRoomPresence(headers, roomId, userId);
        TienLenRoomService.RoomSnapshot room = roomService.roomSnapshot(roomId);
        if (room == null) {
            messagingTemplate.convertAndSend("/topic/tienlen.room." + roomId, Map.of(
                "type", "ROOM_CLOSED",
                "roomId", roomId,
                "message", "Phong da dong"
            ));
            refreshAutomationForRoom(roomId, null);
        } else {
            broadcastRoomState(roomId, "ROOM_STATE", room, "Nguoi choi da roi phong");
            sendPrivateStates(roomId);
            refreshAutomationForRoom(roomId, room);
        }
        broadcastRoomList();
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        if (event == null || event.getMessage() == null) {
            return;
        }
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        RoomPresence presence = sessionRoomPresence.remove(sessionId);
        if (presence == null) {
            return;
        }
        roomService.leaveRoom(presence.roomId(), presence.userId());
        TienLenRoomService.RoomSnapshot room = roomService.roomSnapshot(presence.roomId());
        if (room == null) {
            messagingTemplate.convertAndSend("/topic/tienlen.room." + presence.roomId(), Map.of(
                "type", "ROOM_CLOSED",
                "roomId", presence.roomId(),
                "message", "Phong da dong"
            ));
            refreshAutomationForRoom(presence.roomId(), null);
        } else {
            broadcastRoomState(presence.roomId(), "ROOM_STATE", room, "Nguoi choi mat ket noi");
            sendPrivateStates(presence.roomId());
            refreshAutomationForRoom(presence.roomId(), room);
        }
        broadcastRoomList();
    }

    @MessageMapping("/tienlen.roomList")
    public void roomListRequest() {
        broadcastRoomList();
    }

    private void broadcastRoomList() {
        messagingTemplate.convertAndSend("/topic/tienlen.rooms", Map.of(
            "type", "ROOM_LIST",
            "rooms", roomService.availableRooms()
        ));
    }

    private void broadcastRoomState(String roomId, String type, TienLenRoomService.RoomSnapshot room, String message) {
        if (roomId == null || roomId.isBlank()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type == null ? "ROOM_STATE" : type);
        payload.put("room", room);
        if (message != null && !message.isBlank()) {
            payload.put("message", message);
        }
        messagingTemplate.convertAndSend("/topic/tienlen.room." + roomId, payload);
    }

    private void sendPrivateStates(String roomId) {
        roomService.privateStates(roomId).forEach(this::sendPrivateStatePayload);
    }

    private void sendPrivateState(String roomId, String userId) {
        TienLenRoomService.PrivateState privateState = roomService.privateState(roomId, userId);
        if (privateState != null) {
            sendPrivateStatePayload(userId, privateState);
        }
    }

    private void sendPrivateStatePayload(String userId, TienLenRoomService.PrivateState privateState) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/tienlen.private", Map.of(
            "type", "PRIVATE_STATE",
            "state", privateState
        ));
    }

    private void sendUserError(String roomId, String userId, String error) {
        if (userId != null && !userId.isBlank()) {
            messagingTemplate.convertAndSendToUser(userId, "/queue/errors", Map.of(
                "error", error,
                "scope", "tienlen"
            ));
        }
        if (roomId != null && !roomId.isBlank() && error != null && !error.isBlank()) {
            messagingTemplate.convertAndSend("/topic/tienlen.room." + roomId, Map.of(
                "type", "ERROR",
                "userId", userId,
                "error", error
            ));
        }
    }

    private void refreshAutomationForRoom(String roomId) {
        refreshAutomationForRoom(roomId, roomService.roomSnapshot(roomId));
    }

    private void refreshAutomationForRoom(String roomId, TienLenRoomService.RoomSnapshot room) {
        String rid = safeTrim(roomId);
        if (rid == null) {
            return;
        }
        if (room == null) {
            cancelAutoFillTask(rid);
            cancelBotTurnTask(rid);
            return;
        }

        if (room.started() && !room.gameOver()) {
            cancelAutoFillTask(rid);
            if (isBotTurn(room)) {
                scheduleBotTurnIfNeeded(rid);
            } else {
                cancelBotTurnTask(rid);
            }
            return;
        }

        cancelBotTurnTask(rid);
        if (!room.started() && !room.gameOver() && shouldAutoFillWaitingRoom(room)) {
            scheduleAutoFillIfNeeded(rid);
        } else {
            cancelAutoFillTask(rid);
        }
    }

    private boolean shouldAutoFillWaitingRoom(TienLenRoomService.RoomSnapshot room) {
        if (room == null) {
            return false;
        }
        if (room.playerCount() <= 0 || room.playerCount() >= room.playerLimit()) {
            return false;
        }
        List<TienLenRoomService.PlayerSnapshot> players = room.players();
        if (players == null || players.isEmpty()) {
            return false;
        }
        return players.stream().anyMatch(player -> player != null && !player.bot());
    }

    private void scheduleAutoFillIfNeeded(String roomId) {
        autoFillTasks.compute(roomId, (rid, existing) -> {
            if (existing != null && !existing.isDone() && !existing.isCancelled()) {
                return existing;
            }
            return automationExecutor.schedule(() -> runAutoFillTask(rid), sanitizedDelay(autoFillWaitMs, DEFAULT_AUTO_FILL_WAIT_MS), TimeUnit.MILLISECONDS);
        });
    }

    private void scheduleBotTurnIfNeeded(String roomId) {
        botTurnTasks.compute(roomId, (rid, existing) -> {
            if (existing != null && !existing.isDone() && !existing.isCancelled()) {
                return existing;
            }
            return automationExecutor.schedule(() -> runBotTurnTask(rid), sanitizedDelay(botTurnDelayMs, DEFAULT_BOT_TURN_DELAY_MS), TimeUnit.MILLISECONDS);
        });
    }

    private void runAutoFillTask(String roomId) {
        try {
            autoFillTasks.remove(roomId);

            TienLenRoomService.AutoFillStartResult result = roomService.autoFillBotsAndStart(roomId);
            if (result == null) {
                refreshAutomationForRoom(roomId, null);
                return;
            }

            if (result.error() != null && !result.error().isBlank()) {
                log.warn("TienLen auto-fill failed for room {}: {}", roomId, result.error());
                if (result.room() != null) {
                    broadcastRoomState(roomId, "ROOM_STATE", result.room(), result.error());
                }
                refreshAutomationForRoom(roomId, result.room());
                return;
            }

            if (result.changed() && result.started() && result.room() != null) {
                log.info("TienLen auto-filled {} bot(s) and started room {}", result.addedBotCount(), roomId);
                broadcastRoomState(roomId, "GAME_STARTED", result.room(), result.room().statusMessage());
                sendPrivateStates(roomId);
                broadcastRoomList();
            }

            refreshAutomationForRoom(roomId, result.room());
        } catch (RuntimeException ex) {
            log.error("Unexpected error in TienLen auto-fill task for room {}", roomId, ex);
            refreshAutomationForRoom(roomId);
        }
    }

    private void runBotTurnTask(String roomId) {
        try {
            botTurnTasks.remove(roomId);

            TienLenRoomService.ActionResult result = roomService.botTakeTurn(roomId);
            if (result == null || !result.ok()) {
                if (result != null && result.error() != null && !result.error().isBlank()) {
                    log.debug("TienLen bot turn skipped for room {}: {}", roomId, result.error());
                }
                refreshAutomationForRoom(roomId, result == null ? null : result.room());
                return;
            }

            broadcastRoomState(roomId, result.eventType(), result.room(), result.room() == null ? null : result.room().statusMessage());
            sendPrivateStates(roomId);

            if ("GAME_OVER".equals(result.eventType())) {
                TienLenRoomService.RoomSnapshot waitingRoom = roomService.resetToWaitingAfterGame(roomId);
                if (waitingRoom != null) {
                    broadcastRoomState(roomId, "ROOM_STATE", waitingRoom, waitingRoom.statusMessage());
                    sendPrivateStates(roomId);
                }
                broadcastRoomList();
                refreshAutomationForRoom(roomId, waitingRoom);
                return;
            }

            refreshAutomationForRoom(roomId, result.room());
        } catch (RuntimeException ex) {
            log.error("Unexpected error in TienLen bot turn task for room {}", roomId, ex);
            refreshAutomationForRoom(roomId);
        }
    }

    private void cancelAutoFillTask(String roomId) {
        cancelTask(autoFillTasks, roomId);
    }

    private void cancelBotTurnTask(String roomId) {
        cancelTask(botTurnTasks, roomId);
    }

    private void cancelTask(Map<String, ScheduledFuture<?>> tasks, String roomId) {
        if (tasks == null || roomId == null || roomId.isBlank()) {
            return;
        }
        ScheduledFuture<?> future = tasks.remove(roomId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private boolean isBotTurn(TienLenRoomService.RoomSnapshot room) {
        if (room == null || room.currentTurnUserId() == null || room.players() == null) {
            return false;
        }
        return room.players().stream()
            .filter(Objects::nonNull)
            .anyMatch(player -> Objects.equals(player.userId(), room.currentTurnUserId()) && player.bot());
    }

    private long sanitizedDelay(long configuredDelayMs, long defaultDelayMs) {
        long fallback = Math.max(0L, defaultDelayMs);
        if (configuredDelayMs < 0) {
            return fallback;
        }
        return configuredDelayMs;
    }

    private void rememberRoomPresence(SimpMessageHeaderAccessor headers, String roomId, String userId) {
        String sessionId = headers == null ? null : headers.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        RoomPresence previous = sessionRoomPresence.put(sessionId, new RoomPresence(roomId, userId));
        if (previous == null || (previous.roomId().equals(roomId) && previous.userId().equals(userId))) {
            return;
        }
        roomService.leaveRoom(previous.roomId(), previous.userId());
        TienLenRoomService.RoomSnapshot room = roomService.roomSnapshot(previous.roomId());
        if (room != null) {
            broadcastRoomState(previous.roomId(), "ROOM_STATE", room, "Nguoi choi da chuyen phong");
            sendPrivateStates(previous.roomId());
            refreshAutomationForRoom(previous.roomId(), room);
        } else {
            refreshAutomationForRoom(previous.roomId(), null);
        }
        broadcastRoomList();
    }

    private void forgetRoomPresence(SimpMessageHeaderAccessor headers, String roomId, String userId) {
        String sessionId = headers == null ? null : headers.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        RoomPresence current = sessionRoomPresence.get(sessionId);
        if (current == null) {
            return;
        }
        if (!Objects.equals(current.roomId(), roomId) || !Objects.equals(current.userId(), userId)) {
            return;
        }
        sessionRoomPresence.remove(sessionId, current);
    }

    private String requireConnectionUser(String roomId, String claimedUserId, SimpMessageHeaderAccessor headers) {
        String connectionUserId = connectionUserId(headers);
        if (connectionUserId == null) {
            sendUserError(roomId, claimedUserId, "Session user not found");
            return null;
        }
        String claimed = safeTrim(claimedUserId);
        if (claimed == null) {
            sendUserError(roomId, connectionUserId, "UserId is required");
            return null;
        }
        if (!connectionUserId.equals(claimed)) {
            sendUserError(roomId, connectionUserId, "Session user mismatch");
            return null;
        }
        return connectionUserId;
    }

    private String connectionUserId(SimpMessageHeaderAccessor headers) {
        if (headers != null && headers.getSessionAttributes() != null) {
            String auth = safeTrim(headers.getSessionAttributes().get(AUTH_USER_ID));
            if (auth != null) return auth;
            String guest = safeTrim(headers.getSessionAttributes().get(GUEST_USER_ID));
            if (guest != null) return guest;
        }
        if (headers != null && headers.getUser() != null) {
            return safeTrim(headers.getUser().getName());
        }
        return null;
    }

    private PlayerMeta playerMeta(String userId) {
        if (userId == null || userId.isBlank()) {
            return new PlayerMeta("Guest", DEFAULT_AVATAR_PATH);
        }
        Optional<UserAccount> user = userAccountRepository.findById(userId);
        if (user.isPresent()) {
            UserAccount acc = user.get();
            String name = (acc.getDisplayName() == null || acc.getDisplayName().isBlank()) ? userId : acc.getDisplayName();
            String avatar = (acc.getAvatarPath() == null || acc.getAvatarPath().isBlank()) ? DEFAULT_AVATAR_PATH : acc.getAvatarPath();
            return new PlayerMeta(name, avatar);
        }
        if (userId.toLowerCase().startsWith("guest-")) {
            String suffix = userId.length() <= 4 ? userId : userId.substring(userId.length() - 4);
            return new PlayerMeta("Guest " + suffix.toUpperCase(), DEFAULT_AVATAR_PATH);
        }
        return new PlayerMeta(userId, DEFAULT_AVATAR_PATH);
    }

    private String safeTrim(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private record PlayerMeta(String displayName, String avatarPath) {
    }

    private record RoomPresence(String roomId, String userId) {
    }
}
