package com.game.hub.games.xiangqi.websocket;

import com.game.hub.games.xiangqi.service.XiangqiOnlineRoomService;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class XiangqiWebSocketController {
    private static final String AUTH_USER_ID = "AUTH_USER_ID";
    private static final String GUEST_USER_ID = "GUEST_USER_ID";
    private static final String DEFAULT_AVATAR_PATH = "/uploads/avatars/default-avatar.jpg";

    private final XiangqiOnlineRoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserAccountRepository userAccountRepository;
    private final Map<String, RoomPresence> sessionRoomPresence = new ConcurrentHashMap<>();

    public XiangqiWebSocketController(XiangqiOnlineRoomService roomService,
                                    SimpMessagingTemplate messagingTemplate,
                                    UserAccountRepository userAccountRepository) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
        this.userAccountRepository = userAccountRepository;
    }

    @MessageMapping("/xiangqi.join")
    public void join(XiangqiJoinMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String roomId = safeTrim(message.getRoomId());
        String userId = requireConnectionUser(roomId, message.getUserId(), headers);
        if (roomId == null || userId == null) {
            return;
        }

        PlayerMeta meta = playerMeta(userId);
        XiangqiOnlineRoomService.JoinResult result = roomService.joinRoom(roomId, userId, meta.displayName(), meta.avatarPath());
        if (!result.ok()) {
            sendUserError(roomId, userId, result.error());
            if (result.room() != null) {
                broadcastRoomState(roomId, "ROOM_STATE", result.room(), result.error());
            }
            return;
        }

        rememberRoomPresence(headers, roomId, userId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ROOM_STATE");
        payload.put("room", result.room());
        payload.put("assignedColor", result.assignedColor());
        payload.put("message", "Da vao phong");
        messagingTemplate.convertAndSend("/topic/xiangqi.room." + roomId, payload);
        broadcastRoomList();
    }

    @MessageMapping("/xiangqi.spectate")
    public void spectate(XiangqiJoinMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String roomId = safeTrim(message.getRoomId());
        String userId = requireConnectionUser(roomId, message.getUserId(), headers);
        if (roomId == null || userId == null) {
            return;
        }

        XiangqiOnlineRoomService.JoinResult result = roomService.joinAsSpectator(roomId, userId);
        if (!result.ok()) {
            sendUserError(roomId, userId, result.error());
            return;
        }

        rememberRoomPresence(headers, roomId, userId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ROOM_STATE");
        payload.put("room", result.room());
        payload.put("message", "Da vao xem");
        messagingTemplate.convertAndSend("/topic/xiangqi.room." + roomId, payload);
    }

    @MessageMapping("/xiangqi.move")
    public void move(XiangqiMoveMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String roomId = safeTrim(message.getRoomId());
        String userId = requireConnectionUser(roomId, message.getUserId(), headers);
        if (roomId == null || userId == null) {
            return;
        }
        if (message.getFromRow() == null || message.getFromCol() == null || message.getToRow() == null || message.getToCol() == null) {
            sendUserError(roomId, userId, "Move payload is incomplete");
            return;
        }
        XiangqiOnlineRoomService.ActionResult result = roomService.move(
            roomId,
            userId,
            message.getFromRow(),
            message.getFromCol(),
            message.getToRow(),
            message.getToCol(),
            message.getPromotion()
        );
        if (!result.ok()) {
            sendUserError(roomId, userId, result.error());
            XiangqiOnlineRoomService.RoomSnapshot room = roomService.roomSnapshot(roomId);
            if (room != null) {
                broadcastRoomState(roomId, "ROOM_STATE", room, result.error());
            }
            return;
        }
        broadcastRoomState(roomId, "MOVE", result.room(), result.room() == null ? null : result.room().statusMessage());
    }

    @MessageMapping("/xiangqi.reset")
    public void reset(XiangqiRoomActionMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String roomId = safeTrim(message.getRoomId());
        String userId = requireConnectionUser(roomId, message.getUserId(), headers);
        if (roomId == null || userId == null) {
            return;
        }
        XiangqiOnlineRoomService.ActionResult result = roomService.resetGame(roomId, userId);
        if (!result.ok()) {
            sendUserError(roomId, userId, result.error());
            XiangqiOnlineRoomService.RoomSnapshot room = roomService.roomSnapshot(roomId);
            if (room != null) {
                broadcastRoomState(roomId, "ROOM_STATE", room, result.error());
            }
            return;
        }
        broadcastRoomState(roomId, "RESET", result.room(), "Van moi da san sang");
    }

    @MessageMapping("/xiangqi.surrender")
    public void surrender(XiangqiRoomActionMessage message, SimpMessageHeaderAccessor headers) {
        if (message == null) {
            return;
        }
        String roomId = safeTrim(message.getRoomId());
        String userId = requireConnectionUser(roomId, message.getUserId(), headers);
        if (roomId == null || userId == null) {
            return;
        }
        XiangqiOnlineRoomService.ActionResult result = roomService.surrenderGame(roomId, userId);
        if (!result.ok()) {
            sendUserError(roomId, userId, result.error());
            XiangqiOnlineRoomService.RoomSnapshot room = roomService.roomSnapshot(roomId);
            if (room != null) {
                broadcastRoomState(roomId, "ROOM_STATE", room, result.error());
            }
            return;
        }
        broadcastRoomState(roomId, "SURRENDER", result.room(), result.room() == null ? "Nguoi choi da dau hang" : result.room().statusMessage());
    }

    @MessageMapping("/xiangqi.leave")
    public void leave(XiangqiRoomActionMessage message, SimpMessageHeaderAccessor headers) {
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
        XiangqiOnlineRoomService.RoomSnapshot room = roomService.roomSnapshot(roomId);
        if (room == null) {
            messagingTemplate.convertAndSend("/topic/xiangqi.room." + roomId, Map.of(
                "type", "ROOM_CLOSED",
                "roomId", roomId,
                "message", "Phong da dong"
            ));
        } else {
            broadcastRoomState(roomId, "ROOM_STATE", room, "Nguoi choi da roi phong");
        }
        broadcastRoomList();
    }

    @MessageMapping("/xiangqi.roomList")
    public void roomListRequest() {
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
        XiangqiOnlineRoomService.RoomSnapshot room = roomService.roomSnapshot(presence.roomId());
        if (room == null) {
            messagingTemplate.convertAndSend("/topic/xiangqi.room." + presence.roomId(), Map.of(
                "type", "ROOM_CLOSED",
                "roomId", presence.roomId(),
                "message", "Phong da dong"
            ));
        } else {
            broadcastRoomState(presence.roomId(), "ROOM_STATE", room, "Nguoi choi mat ket noi");
        }
        broadcastRoomList();
    }

    private void broadcastRoomList() {
        messagingTemplate.convertAndSend("/topic/xiangqi.rooms", Map.of(
            "type", "ROOM_LIST",
            "rooms", roomService.availableRooms()
        ));
    }

    private void broadcastRoomState(String roomId, String type, XiangqiOnlineRoomService.RoomSnapshot room, String message) {
        if (roomId == null || roomId.isBlank()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type == null ? "ROOM_STATE" : type);
        payload.put("room", room);
        if (message != null && !message.isBlank()) {
            payload.put("message", message);
        }
        messagingTemplate.convertAndSend("/topic/xiangqi.room." + roomId, payload);
    }

    private void sendUserError(String roomId, String userId, String error) {
        if (userId != null && !userId.isBlank()) {
            messagingTemplate.convertAndSendToUser(userId, "/queue/errors", Map.of(
                "error", error,
                "scope", "xiangqi"
            ));
        }
        if (roomId != null && !roomId.isBlank() && error != null && !error.isBlank()) {
            messagingTemplate.convertAndSend("/topic/xiangqi.room." + roomId, Map.of(
                "type", "ERROR",
                "userId", userId,
                "error", error
            ));
        }
    }

    private void rememberRoomPresence(SimpMessageHeaderAccessor headers, String roomId, String userId) {
        String sessionId = headers == null ? null : headers.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        RoomPresence previous = sessionRoomPresence.put(sessionId, new RoomPresence(roomId, userId));
        if (previous == null || (Objects.equals(previous.roomId(), roomId) && Objects.equals(previous.userId(), userId))) {
            return;
        }
        roomService.leaveRoom(previous.roomId(), previous.userId());
        XiangqiOnlineRoomService.RoomSnapshot room = roomService.roomSnapshot(previous.roomId());
        if (room != null) {
            broadcastRoomState(previous.roomId(), "ROOM_STATE", room, "Nguoi choi da chuyen phong");
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
            if (auth != null) {
                return auth;
            }
            String guest = safeTrim(headers.getSessionAttributes().get(GUEST_USER_ID));
            if (guest != null) {
                return guest;
            }
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

