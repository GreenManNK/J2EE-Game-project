package com.game.hub.service;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class CommunicationGuardService {
    private static final int MUTE_THRESHOLD = 3;
    private static final int MUTE_DURATION_MINUTES = 15;
    private static final String MASKED_CHAT_CONTENT = "******";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserAccountRepository userAccountRepository;
    private final ChatModerationService chatModerationService;

    public CommunicationGuardService(UserAccountRepository userAccountRepository,
                                     ChatModerationService chatModerationService) {
        this.userAccountRepository = userAccountRepository;
        this.chatModerationService = chatModerationService;
    }

    public ValidationResult validate(String userId, String content) {
        UserAccount user = loadTrackedUser(userId);
        if (user != null && user.isCommunicationRestricted()) {
            return ValidationResult.blocked(restrictedMessage(user.getCommunicationRestrictedUntil()), user.getAbusiveContentViolationCount(), user.getCommunicationRestrictedUntil());
        }

        ChatModerationService.ModerationResult moderation = chatModerationService.moderate(content);
        if (moderation.allowed()) {
            return ValidationResult.pass();
        }

        if (user == null) {
            return ValidationResult.blocked(moderation.error(), null, null);
        }

        int nextViolationCount = user.getAbusiveContentViolationCount() + 1;
        user.setAbusiveContentViolationCount(nextViolationCount);

        LocalDateTime restrictedUntil = user.getCommunicationRestrictedUntil();
        if (nextViolationCount >= MUTE_THRESHOLD) {
            restrictedUntil = LocalDateTime.now().plusMinutes(MUTE_DURATION_MINUTES);
            user.setCommunicationRestrictedUntil(restrictedUntil);
        }
        userAccountRepository.save(user);

        if (restrictedUntil != null && restrictedUntil.isAfter(LocalDateTime.now())) {
            return ValidationResult.blocked(
                moderation.error() + " Ban bi tam khoa giao tiep den " + formatDateTime(restrictedUntil) + ".",
                nextViolationCount,
                restrictedUntil
            );
        }
        return ValidationResult.blocked(
            moderation.error() + " Canh cao " + Math.min(nextViolationCount, MUTE_THRESHOLD) + "/" + MUTE_THRESHOLD + ".",
            nextViolationCount,
            null
        );
    }

    public ChatMessageDecision inspectChatMessage(String userId, String content) {
        UserAccount user = loadTrackedUser(userId);
        if (user != null && user.isCommunicationRestricted()) {
            return ChatMessageDecision.blocked(
                restrictedMessage(user.getCommunicationRestrictedUntil()),
                user.getAbusiveContentViolationCount(),
                user.getCommunicationRestrictedUntil()
            );
        }

        ChatModerationService.ModerationResult moderation = chatModerationService.moderate(content);
        if (moderation.allowed()) {
            return ChatMessageDecision.pass(content);
        }

        if (user == null) {
            return ChatMessageDecision.masked(
                MASKED_CHAT_CONTENT,
                moderation.error() + " Noi dung da duoc an thanh " + MASKED_CHAT_CONTENT + ".",
                null,
                null
            );
        }

        int nextViolationCount = user.getAbusiveContentViolationCount() + 1;
        user.setAbusiveContentViolationCount(nextViolationCount);

        LocalDateTime restrictedUntil = null;
        if (nextViolationCount >= MUTE_THRESHOLD) {
            restrictedUntil = LocalDateTime.now().plusMinutes(MUTE_DURATION_MINUTES);
            user.setCommunicationRestrictedUntil(restrictedUntil);
        }
        userAccountRepository.save(user);

        String notice;
        if (restrictedUntil != null && restrictedUntil.isAfter(LocalDateTime.now())) {
            notice = moderation.error()
                + " Noi dung da duoc an thanh " + MASKED_CHAT_CONTENT + ". Ban bi tam khoa giao tiep den "
                + formatDateTime(restrictedUntil) + ".";
        } else {
            notice = moderation.error()
                + " Noi dung da duoc an thanh " + MASKED_CHAT_CONTENT + ". Canh cao "
                + Math.min(nextViolationCount, MUTE_THRESHOLD) + "/" + MUTE_THRESHOLD + ".";
        }

        return ChatMessageDecision.masked(MASKED_CHAT_CONTENT, notice, nextViolationCount, restrictedUntil);
    }

    private UserAccount loadTrackedUser(String userId) {
        if (userId == null || userId.isBlank() || isGuestUserId(userId)) {
            return null;
        }
        return userAccountRepository.findById(userId).orElse(null);
    }

    private boolean isGuestUserId(String userId) {
        return userId != null && userId.trim().toLowerCase().startsWith("guest-");
    }

    private String restrictedMessage(LocalDateTime restrictedUntil) {
        return "Ban dang bi tam khoa giao tiep den " + formatDateTime(restrictedUntil) + ".";
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "khong xac dinh";
        }
        return DATE_TIME_FORMATTER.format(value);
    }

    public record ValidationResult(boolean allowed,
                                   String error,
                                   Integer violationCount,
                                   LocalDateTime restrictedUntil) {
        public static ValidationResult pass() {
            return new ValidationResult(true, null, null, null);
        }

        public static ValidationResult blocked(String error, Integer violationCount, LocalDateTime restrictedUntil) {
            return new ValidationResult(false, error, violationCount, restrictedUntil);
        }
    }

    public record ChatMessageDecision(boolean allowed,
                                      String deliveryContent,
                                      String notice,
                                      boolean masked,
                                      Integer violationCount,
                                      LocalDateTime restrictedUntil) {
        public static ChatMessageDecision pass(String deliveryContent) {
            return new ChatMessageDecision(true, deliveryContent, null, false, null, null);
        }

        public static ChatMessageDecision masked(String deliveryContent,
                                                 String notice,
                                                 Integer violationCount,
                                                 LocalDateTime restrictedUntil) {
            return new ChatMessageDecision(true, deliveryContent, notice, true, violationCount, restrictedUntil);
        }

        public static ChatMessageDecision blocked(String notice,
                                                  Integer violationCount,
                                                  LocalDateTime restrictedUntil) {
            return new ChatMessageDecision(false, null, notice, false, violationCount, restrictedUntil);
        }
    }
}
