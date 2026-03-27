package com.game.hub.support;

import com.game.hub.entity.GameHistory;
import com.game.hub.service.GameCatalogItem;
import com.game.hub.service.GameCatalogService;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class GameHistoryPresentationSupport {
    private final Map<String, GameCatalogItem> catalogByCode;

    public GameHistoryPresentationSupport(GameCatalogService gameCatalogService) {
        GameCatalogService safeCatalogService = gameCatalogService == null ? new GameCatalogService() : gameCatalogService;
        this.catalogByCode = new LinkedHashMap<>();
        for (GameCatalogItem item : safeCatalogService.findAll()) {
            this.catalogByCode.put(normalize(item.code()), item);
        }
    }

    public ViewMetadata describe(GameHistory history) {
        String storedGameCodeRaw = trimToNull(history == null ? null : history.getGameCode());
        String storedGameCode = normalize(storedGameCodeRaw);
        String canonicalGameCode = resolveCanonicalGameCode(storedGameCode);
        String roomId = trimToNull(history == null ? null : history.getRoomId());
        String matchCode = resolveMatchCode(history, storedGameCodeRaw, storedGameCode, roomId);
        String contextLabel = resolveContextLabel(storedGameCode, roomId);
        String gameName = resolveGameName(canonicalGameCode, storedGameCode);
        String gameIconClass = resolveGameIconClass(canonicalGameCode);
        String gameHref = resolveGameHref(canonicalGameCode);
        String locationLabel = resolveLocationLabel(history, canonicalGameCode, storedGameCode, roomId);
        String locationHref = resolveLocationHref(history, canonicalGameCode, roomId, contextLabel);
        return new ViewMetadata(
            canonicalGameCode,
            gameName,
            gameIconClass,
            gameHref,
            matchCode,
            locationLabel,
            locationHref,
            contextLabel
        );
    }

    private String resolveCanonicalGameCode(String storedGameCode) {
        if (storedGameCode.isBlank()) {
            return "unknown";
        }
        if (isLegacyCaroMatchCode(storedGameCode)) {
            return "caro";
        }
        return switch (storedGameCode) {
            case "caro" -> "caro";
            case "cards", "tienlen", "tien-len" -> "cards";
            case "blackjack" -> "blackjack";
            case "chess", "chess-offline", "chess_offline", "chessoffline" -> "chess";
            case "xiangqi", "xiangqi-offline", "xiangqi_offline", "xiangqioffline" -> "xiangqi";
            case "typing" -> "typing";
            case "quiz" -> "quiz";
            case "minesweeper" -> "minesweeper";
            case "monopoly" -> "monopoly";
            case "sudoku", "jigsaw", "sliding", "word", "puzzle" -> "puzzle";
            default -> storedGameCode;
        };
    }

    private String resolveMatchCode(GameHistory history,
                                    String storedGameCodeRaw,
                                    String storedGameCode,
                                    String roomId) {
        String explicitMatchCode = trimToNull(history == null ? null : history.getMatchCode());
        if (explicitMatchCode != null) {
            return explicitMatchCode;
        }
        if (roomId != null) {
            return roomId;
        }
        if (isLegacyCaroMatchCode(storedGameCode)) {
            return storedGameCodeRaw == null ? storedGameCode : storedGameCodeRaw;
        }
        Long historyId = history == null ? null : history.getId();
        if (historyId != null) {
            return "TRAN-" + historyId;
        }
        return "TRAN-NA";
    }

    private String resolveContextLabel(String storedGameCode, String roomId) {
        if (roomId != null || isLegacyCaroMatchCode(storedGameCode)) {
            return "Online";
        }
        if (storedGameCode.endsWith("-offline") || storedGameCode.endsWith("_offline") || storedGameCode.endsWith("offline")) {
            return "Offline";
        }
        if ("minesweeper".equals(storedGameCode) || "monopoly".equals(storedGameCode)) {
            return "Local";
        }
        return "Lich su";
    }

    private String resolveGameName(String canonicalGameCode, String storedGameCode) {
        GameCatalogItem item = catalogByCode.get(canonicalGameCode);
        if (item != null) {
            if ("cards".equals(canonicalGameCode)) {
                return "Tien Len";
            }
            return item.displayName();
        }
        return switch (canonicalGameCode) {
            case "cards" -> "Tien Len";
            case "puzzle" -> switch (storedGameCode) {
                case "sudoku" -> "Puzzle Sudoku";
                case "jigsaw" -> "Puzzle Jigsaw";
                case "sliding" -> "Puzzle Sliding";
                case "word" -> "Puzzle Word";
                default -> "Puzzle";
            };
            case "unknown" -> "Tran dau";
            default -> canonicalGameCode.toUpperCase(Locale.ROOT);
        };
    }

    private String resolveGameIconClass(String canonicalGameCode) {
        GameCatalogItem item = catalogByCode.get(canonicalGameCode);
        if (item != null) {
            return item.iconClass();
        }
        return switch (canonicalGameCode) {
            case "cards" -> "bi-suit-spade-fill";
            case "puzzle" -> "bi-puzzle-fill";
            default -> "bi-controller";
        };
    }

    private String resolveGameHref(String canonicalGameCode) {
        GameCatalogItem item = catalogByCode.get(canonicalGameCode);
        String catalogHref = item == null ? null : trimToNull(item.primaryActionUrl());
        if (catalogHref != null) {
            return catalogHref;
        }
        return switch (canonicalGameCode) {
            case "caro" -> "/games/caro";
            case "cards" -> "/cards/tien-len/rooms";
            case "blackjack" -> "/games/cards/blackjack";
            case "chess" -> "/games/chess";
            case "xiangqi" -> "/games/xiangqi";
            case "typing" -> "/games/typing";
            case "quiz" -> "/games/quiz";
            case "minesweeper" -> "/games/minesweeper";
            case "monopoly" -> "/games/monopoly";
            case "puzzle" -> "/games/puzzle";
            default -> "/history";
        };
    }

    private String resolveLocationLabel(GameHistory history,
                                        String canonicalGameCode,
                                        String storedGameCode,
                                        String roomId) {
        String explicitLabel = trimToNull(history == null ? null : history.getLocationLabel());
        if (explicitLabel != null) {
            return explicitLabel;
        }
        if (roomId != null) {
            return roomLabelFor(canonicalGameCode, roomId);
        }
        if (isLegacyCaroMatchCode(storedGameCode)) {
            return roomLabelFor(canonicalGameCode, storedGameCode);
        }
        if (storedGameCode.endsWith("-offline") || storedGameCode.endsWith("_offline") || storedGameCode.endsWith("offline")) {
            return "Che do offline";
        }
        return switch (canonicalGameCode) {
            case "minesweeper", "monopoly", "puzzle" -> "Ban local";
            case "cards", "blackjack", "chess", "xiangqi", "typing", "quiz", "caro" -> "Trang game";
            default -> "Lich su dong bo";
        };
    }

    private String resolveLocationHref(GameHistory history,
                                       String canonicalGameCode,
                                       String roomId,
                                       String contextLabel) {
        String explicitPath = trimToNull(history == null ? null : history.getLocationPath());
        if (explicitPath != null) {
            return explicitPath;
        }
        if (roomId != null && "Online".equalsIgnoreCase(contextLabel)) {
            return roomPathFor(canonicalGameCode, roomId);
        }
        return resolveGameHref(canonicalGameCode);
    }

    private String roomLabelFor(String canonicalGameCode, String roomIdOrLegacyCode) {
        String normalizedRoomId = normalize(roomIdOrLegacyCode);
        if ("caro".equals(canonicalGameCode)) {
            if (normalizedRoomId.startsWith("ranked_")) {
                return "Phong xep hang Caro";
            }
            if (normalizedRoomId.startsWith("normal_")) {
                return "Phong thuong Caro";
            }
            if (normalizedRoomId.startsWith("challenge_")) {
                return "Phong thach dau Caro";
            }
            return "Phong online Caro";
        }
        return switch (canonicalGameCode) {
            case "cards" -> "Phong Tien Len";
            case "blackjack" -> "Ban blackjack online";
            case "chess" -> "Phong co vua online";
            case "xiangqi" -> "Phong co tuong online";
            case "typing" -> "Phong Typing Battle";
            case "quiz" -> "Phong Quiz";
            default -> "Phong online";
        };
    }

    private String roomPathFor(String canonicalGameCode, String roomId) {
        String encodedRoomId = UriUtils.encodePathSegment(roomId, StandardCharsets.UTF_8);
        return switch (canonicalGameCode) {
            case "caro" -> "/game/room/" + encodedRoomId;
            case "cards" -> "/cards/tien-len/room/" + encodedRoomId;
            case "blackjack" -> "/games/cards/blackjack/room/" + encodedRoomId;
            case "chess" -> "/chess/online/room/" + encodedRoomId;
            case "xiangqi" -> "/xiangqi/online/room/" + encodedRoomId;
            case "typing" -> "/games/typing/room/" + encodedRoomId;
            case "quiz" -> "/games/quiz/room/" + encodedRoomId;
            default -> resolveGameHref(canonicalGameCode);
        };
    }

    private boolean isLegacyCaroMatchCode(String storedGameCode) {
        return storedGameCode.startsWith("ranked_")
            || storedGameCode.startsWith("normal_")
            || storedGameCode.startsWith("challenge_")
            || storedGameCode.startsWith("game_");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record ViewMetadata(String gameCode,
                               String gameName,
                               String gameIconClass,
                               String gameHref,
                               String matchCode,
                               String locationLabel,
                               String locationHref,
                               String contextLabel) {
    }
}
