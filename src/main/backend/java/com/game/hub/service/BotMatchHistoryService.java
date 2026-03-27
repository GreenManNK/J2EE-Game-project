package com.game.hub.service;

import com.game.hub.config.RoleGuardInterceptor;
import com.game.hub.entity.GameHistory;
import com.game.hub.repository.GameHistoryRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class BotMatchHistoryService {
    private static final String GUEST_USER_ID = "GUEST_USER_ID";

    private final GameHistoryRepository gameHistoryRepository;

    public BotMatchHistoryService(GameHistoryRepository gameHistoryRepository) {
        this.gameHistoryRepository = gameHistoryRepository;
    }

    public ServiceResult record(BotMatchRecordRequest request, HttpServletRequest httpRequest) {
        if (request == null) {
            return ServiceResult.error("Invalid request");
        }

        String normalizedGameCode = normalizeGameCode(request.gameCode());
        if (normalizedGameCode == null) {
            return ServiceResult.error("Unsupported game");
        }

        String outcome = normalizeOutcome(request.outcome());
        if (outcome == null) {
            return ServiceResult.error("Unsupported outcome");
        }

        String playerUserId = resolveOrCreateSessionUserId(httpRequest);
        if (playerUserId == null || playerUserId.isBlank()) {
            return ServiceResult.error("Unable to resolve session player");
        }

        BotProfile botProfile = buildBotProfile(normalizedGameCode, request.difficulty());
        String matchCode = trimToNull(request.matchCode());
        if (matchCode == null) {
            matchCode = defaultMatchCode(normalizedGameCode);
        }

        String firstPlayerRole = normalizeFirstPlayerRole(request.firstPlayerRole());
        if (gameHistoryRepository.existsByGameCodeAndMatchCodeAndPlayer1Id(botProfile.storedGameCode(), matchCode, playerUserId)) {
            return ServiceResult.success(false, playerUserId, matchCode);
        }

        GameHistory history = new GameHistory();
        history.setGameCode(botProfile.storedGameCode());
        history.setMatchCode(matchCode);
        history.setRoomId(null);
        history.setLocationLabel(botProfile.locationLabel());
        history.setLocationPath(botProfile.locationPath());
        history.setPlayer1Id(playerUserId);
        history.setPlayer2Id(botProfile.opponentId());
        history.setFirstPlayerId("bot".equals(firstPlayerRole) ? botProfile.opponentId() : playerUserId);
        history.setTotalMoves(Math.max(0, request.totalMoves() == null ? 0 : request.totalMoves()));
        history.setWinnerId(resolveWinnerId(outcome, playerUserId, botProfile.opponentId()));
        gameHistoryRepository.save(history);
        return ServiceResult.success(true, playerUserId, matchCode);
    }

    private String resolveWinnerId(String outcome, String playerUserId, String botUserId) {
        return switch (outcome) {
            case "win" -> playerUserId;
            case "loss" -> botUserId;
            default -> null;
        };
    }

    private BotProfile buildBotProfile(String normalizedGameCode, String rawDifficulty) {
        String difficulty = normalizeDifficulty(rawDifficulty);
        return switch (normalizedGameCode) {
            case "caro" -> new BotProfile(
                "caro-bot",
                "bot-caro-" + difficulty,
                "Bot Caro " + titleCase(difficulty),
                "/bot/" + difficulty
            );
            case "chess" -> new BotProfile(
                "chess-bot",
                "bot-chess-" + difficulty,
                "Bot Co vua " + titleCase(difficulty),
                "/chess/bot?difficulty=" + difficulty
            );
            case "xiangqi" -> new BotProfile(
                "xiangqi-bot",
                "bot-xiangqi-" + difficulty,
                "Bot Co tuong " + titleCase(difficulty),
                "/xiangqi/bot?difficulty=" + difficulty
            );
            case "cards" -> new BotProfile(
                "cards-bot",
                "bot-tienlen-" + difficulty,
                "Ban 3 bot Tien Len " + titleCase(difficulty),
                "/cards/tien-len/bot?difficulty=" + difficulty
            );
            case "blackjack" -> new BotProfile(
                "blackjack-bot",
                "bot-blackjack-dealer",
                "Ban blackjack voi dealer",
                "/games/cards/blackjack/bot"
            );
            case "quiz" -> new BotProfile(
                "quiz-bot",
                "bot-quiz-" + difficulty,
                "Quiz voi bot " + titleCase(difficulty),
                "/games/quiz/bot?difficulty=" + difficulty
            );
            case "typing" -> new BotProfile(
                "typing-bot",
                "bot-typing-" + difficulty,
                "Typing Battle voi bot " + titleCase(difficulty),
                "/games/typing/bot?difficulty=" + difficulty
            );
            case "monopoly" -> new BotProfile(
                "monopoly-bot",
                "bot-monopoly-" + difficulty,
                "Ban Monopoly voi bot " + titleCase(difficulty),
                "/games/monopoly/bot?difficulty=" + difficulty
            );
            default -> throw new IllegalArgumentException("Unsupported game code: " + normalizedGameCode);
        };
    }

    private String resolveOrCreateSessionUserId(HttpServletRequest httpRequest) {
        if (httpRequest == null) {
            return null;
        }
        HttpSession session = httpRequest.getSession(true);
        String authUserId = trimToNull(sessionValue(session, RoleGuardInterceptor.AUTH_USER_ID));
        if (authUserId != null) {
            return authUserId;
        }
        String guestUserId = trimToNull(sessionValue(session, GUEST_USER_ID));
        if (guestUserId != null) {
            return guestUserId;
        }
        String generatedGuestUserId = "guest-" + UUID.randomUUID().toString().replace("-", "");
        session.setAttribute(GUEST_USER_ID, generatedGuestUserId);
        return generatedGuestUserId;
    }

    private String sessionValue(HttpSession session, String key) {
        if (session == null || key == null) {
            return null;
        }
        Object value = session.getAttribute(key);
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeGameCode(String rawGameCode) {
        String normalized = trimToNull(rawGameCode);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "caro" -> "caro";
            case "chess" -> "chess";
            case "xiangqi" -> "xiangqi";
            case "cards", "tienlen", "tien-len" -> "cards";
            case "blackjack" -> "blackjack";
            case "quiz" -> "quiz";
            case "typing" -> "typing";
            case "monopoly" -> "monopoly";
            default -> null;
        };
    }

    private String normalizeOutcome(String rawOutcome) {
        String normalized = trimToNull(rawOutcome);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "win" -> "win";
            case "loss", "lose" -> "loss";
            case "draw", "tie", "push" -> "draw";
            default -> null;
        };
    }

    private String normalizeFirstPlayerRole(String rawRole) {
        String normalized = trimToNull(rawRole);
        if (normalized == null) {
            return "player";
        }
        return "bot".equalsIgnoreCase(normalized) ? "bot" : "player";
    }

    private String normalizeDifficulty(String rawDifficulty) {
        return "hard".equalsIgnoreCase(rawDifficulty) ? "hard" : "easy";
    }

    private String defaultMatchCode(String gameCode) {
        return "BOT-" + gameCode.toUpperCase(Locale.ROOT) + "-" + Instant.now().toEpochMilli();
    }

    private String titleCase(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record BotMatchRecordRequest(String gameCode,
                                        String difficulty,
                                        String outcome,
                                        Integer totalMoves,
                                        String firstPlayerRole,
                                        String matchCode) {
    }

    private record BotProfile(String storedGameCode,
                              String opponentId,
                              String locationLabel,
                              String locationPath) {
    }

    public record ServiceResult(boolean success,
                                String error,
                                boolean recorded,
                                String playerUserId,
                                String matchCode) {
        private static ServiceResult success(boolean recorded, String playerUserId, String matchCode) {
            return new ServiceResult(true, null, recorded, playerUserId, matchCode);
        }

        private static ServiceResult error(String error) {
            return new ServiceResult(false, error, false, null, null);
        }
    }
}
