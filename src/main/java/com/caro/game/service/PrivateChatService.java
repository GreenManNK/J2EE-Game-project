package com.caro.game.service;

import com.caro.game.entity.PrivateChatRecord;
import com.caro.game.entity.UserAccount;
import com.caro.game.repository.PrivateChatRecordRepository;
import com.caro.game.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PrivateChatService {
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final UserAccountRepository userAccountRepository;
    private final FriendshipService friendshipService;
    private final PrivateChatRecordRepository privateChatRecordRepository;

    public PrivateChatService(UserAccountRepository userAccountRepository,
                              FriendshipService friendshipService,
                              PrivateChatRecordRepository privateChatRecordRepository) {
        this.userAccountRepository = userAccountRepository;
        this.friendshipService = friendshipService;
        this.privateChatRecordRepository = privateChatRecordRepository;
    }

    public ChatBootstrapResult buildChatBootstrap(String currentUserId, String friendId) {
        String normalizedCurrentUserId = trimToNull(currentUserId);
        String normalizedFriendId = trimToNull(friendId);

        if (normalizedCurrentUserId == null || normalizedFriendId == null) {
            return ChatBootstrapResult.error("Missing user id");
        }
        if (normalizedCurrentUserId.equals(normalizedFriendId)) {
            return ChatBootstrapResult.error("Cannot chat with yourself");
        }

        UserAccount currentUser = userAccountRepository.findById(normalizedCurrentUserId).orElse(null);
        UserAccount friend = userAccountRepository.findById(normalizedFriendId).orElse(null);
        if (currentUser == null || friend == null) {
            return ChatBootstrapResult.error("User not found");
        }
        if (!friendshipService.areFriends(normalizedCurrentUserId, normalizedFriendId)) {
            return ChatBootstrapResult.error("Only friends can use private chat");
        }

        String roomKey = roomKey(normalizedCurrentUserId, normalizedFriendId);
        return ChatBootstrapResult.success(
            currentUser.getId(),
            displayNameOf(currentUser),
            friend.getId(),
            displayNameOf(friend),
            roomKey,
            loadRecentMessages(roomKey)
        );
    }

    public SendResult saveMessage(String currentUserId, String friendId, String content) {
        String normalizedCurrentUserId = trimToNull(currentUserId);
        String normalizedFriendId = trimToNull(friendId);
        String normalizedContent = trimToNull(content);

        if (normalizedCurrentUserId == null || normalizedFriendId == null) {
            return SendResult.error(null, normalizedCurrentUserId, "Missing user id");
        }
        if (normalizedCurrentUserId.equals(normalizedFriendId)) {
            return SendResult.error(roomKey(normalizedCurrentUserId, normalizedFriendId), normalizedCurrentUserId, "Cannot chat with yourself");
        }
        if (normalizedContent == null) {
            return SendResult.error(roomKey(normalizedCurrentUserId, normalizedFriendId), normalizedCurrentUserId, "Empty message");
        }
        if (normalizedContent.length() > MAX_MESSAGE_LENGTH) {
            normalizedContent = normalizedContent.substring(0, MAX_MESSAGE_LENGTH);
        }

        UserAccount currentUser = userAccountRepository.findById(normalizedCurrentUserId).orElse(null);
        UserAccount friend = userAccountRepository.findById(normalizedFriendId).orElse(null);
        if (currentUser == null || friend == null) {
            return SendResult.error(roomKey(normalizedCurrentUserId, normalizedFriendId), normalizedCurrentUserId, "User not found");
        }
        if (!friendshipService.areFriends(normalizedCurrentUserId, normalizedFriendId)) {
            return SendResult.error(roomKey(normalizedCurrentUserId, normalizedFriendId), normalizedCurrentUserId, "Only friends can chat");
        }

        String roomKey = roomKey(normalizedCurrentUserId, normalizedFriendId);

        PrivateChatRecord record = new PrivateChatRecord();
        record.setRoomKey(roomKey);
        record.setFromUserId(currentUser.getId());
        record.setToUserId(friend.getId());
        record.setSenderName(displayNameOf(currentUser));
        record.setContent(normalizedContent);
        PrivateChatRecord saved = privateChatRecordRepository.save(record);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "PRIVATE_CHAT");
        payload.put("roomKey", roomKey);
        payload.put("fromUserId", saved.getFromUserId());
        payload.put("toUserId", saved.getToUserId());
        payload.put("senderName", saved.getSenderName());
        payload.put("message", saved.getContent());
        payload.put("sentAt", saved.getSentAt() == null ? null : saved.getSentAt().toString());

        return SendResult.success(roomKey, saved.getFromUserId(), payload);
    }

    public String roomKey(String userIdA, String userIdB) {
        if (userIdA == null || userIdB == null) {
            return null;
        }
        return userIdA.compareTo(userIdB) <= 0
            ? (userIdA + "__" + userIdB)
            : (userIdB + "__" + userIdA);
    }

    private List<Map<String, Object>> loadRecentMessages(String roomKey) {
        List<PrivateChatRecord> records = privateChatRecordRepository.findTop100ByRoomKeyOrderByIdDesc(roomKey);
        List<Map<String, Object>> messages = new ArrayList<>(records.size());
        for (int i = records.size() - 1; i >= 0; i--) {
            PrivateChatRecord record = records.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fromUserId", record.getFromUserId());
            row.put("toUserId", record.getToUserId());
            row.put("senderName", record.getSenderName());
            row.put("message", record.getContent());
            row.put("sentAt", record.getSentAt() == null ? null : record.getSentAt().toString());
            messages.add(row);
        }
        return messages;
    }

    private String displayNameOf(UserAccount user) {
        String displayName = trimToNull(user.getDisplayName());
        return displayName == null ? user.getId() : displayName;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ChatBootstrapResult(boolean ok,
                                      String error,
                                      String currentUserId,
                                      String currentUserName,
                                      String friendId,
                                      String friendName,
                                      String roomKey,
                                      List<Map<String, Object>> messages) {
        public static ChatBootstrapResult error(String error) {
            return new ChatBootstrapResult(false, error, null, null, null, null, null, List.of());
        }

        public static ChatBootstrapResult success(String currentUserId,
                                                  String currentUserName,
                                                  String friendId,
                                                  String friendName,
                                                  String roomKey,
                                                  List<Map<String, Object>> messages) {
            return new ChatBootstrapResult(true, null, currentUserId, currentUserName, friendId, friendName, roomKey, messages);
        }

        public Map<String, Object> toMap() {
            if (!ok) {
                return Map.of("success", false, "error", error == null ? "Unknown error" : error);
            }
            return Map.of(
                "success", true,
                "currentUserId", currentUserId,
                "currentUserName", currentUserName,
                "friendId", friendId,
                "friendName", friendName,
                "roomKey", roomKey,
                "messages", messages
            );
        }
    }

    public record SendResult(boolean ok,
                             String error,
                             String roomKey,
                             String userId,
                             Map<String, Object> payload) {
        public static SendResult success(String roomKey, String userId, Map<String, Object> payload) {
            return new SendResult(true, null, roomKey, userId, payload);
        }

        public static SendResult error(String roomKey, String userId, String error) {
            return new SendResult(false, error, roomKey, userId, null);
        }
    }
}
