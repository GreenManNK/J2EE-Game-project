package com.game.hub.controller;

import com.game.hub.games.cards.blackjack.logic.BlackjackRoom;
import com.game.hub.games.cards.blackjack.service.BlackjackService;
import com.game.hub.games.cards.tienlen.service.TienLenRoomService;
import com.game.hub.games.chess.service.ChessOnlineRoomService;
import com.game.hub.games.quiz.logic.QuizRoom;
import com.game.hub.games.quiz.service.QuizService;
import com.game.hub.games.typing.logic.TypingRoom;
import com.game.hub.games.typing.service.TypingService;
import com.game.hub.games.xiangqi.service.XiangqiOnlineRoomService;
import com.game.hub.service.GameCatalogItem;
import com.game.hub.service.GameCatalogService;
import com.game.hub.games.caro.service.GameRoomService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Controller
@RequestMapping("/online-hub")
public class OnlineHubController {
    private static final String DEFAULT_GAME_CODE = "caro";

    private final GameCatalogService gameCatalogService;
    private final GameRoomService gameRoomService;
    private final TienLenRoomService tienLenRoomService;
    private final BlackjackService blackjackService;
    private final ChessOnlineRoomService chessOnlineRoomService;
    private final XiangqiOnlineRoomService xiangqiOnlineRoomService;
    private final TypingService typingService;
    private final QuizService quizService;

    public OnlineHubController(GameCatalogService gameCatalogService,
                               GameRoomService gameRoomService,
                               TienLenRoomService tienLenRoomService,
                               BlackjackService blackjackService,
                               ChessOnlineRoomService chessOnlineRoomService,
                               XiangqiOnlineRoomService xiangqiOnlineRoomService,
                               TypingService typingService,
                               QuizService quizService) {
        this.gameCatalogService = gameCatalogService;
        this.gameRoomService = gameRoomService;
        this.tienLenRoomService = tienLenRoomService;
        this.blackjackService = blackjackService;
        this.chessOnlineRoomService = chessOnlineRoomService;
        this.xiangqiOnlineRoomService = xiangqiOnlineRoomService;
        this.typingService = typingService;
        this.quizService = quizService;
    }

    @GetMapping
    public String index(@RequestParam(required = false) String game,
                        @RequestParam(required = false) String roomId,
                        Model model) {
        GameCatalogItem item = resolveGameItem(game);

        model.addAttribute("selectedGame", item);
        model.addAttribute("selectedGameCode", item.code());
        model.addAttribute("selectedGameName", item.displayName());
        model.addAttribute("selectedRoomId", roomId == null ? "" : roomId.trim());
        model.addAttribute("onlineSupportedNow", onlineGameplayImplemented(item.code()));
        model.addAttribute("supportsSpectateNow", supportsSpectateNow(item.code()));
        model.addAttribute("roomRows", listRooms(item.code()));
        model.addAttribute("playUrlBase", playUrlBase(item.code()));
        model.addAttribute("playRoomParam", playRoomParam(item.code()));
        model.addAttribute("playUrlTemplate", playUrlTemplate(item.code()));
        model.addAttribute("spectateUrlTemplate", spectateUrlTemplate(item.code()));
        model.addAttribute("spectateParamName", spectateParamName(item.code()));
        model.addAttribute("spectateParamValue", spectateParamValue(item.code()));
        model.addAttribute("inviteUrlPathTemplate", invitePathTemplate(item.code()));
        return "online-hub/index";
    }

    @ResponseBody
    @GetMapping("/api/rooms")
    public Map<String, Object> rooms(@RequestParam(required = false) String game) {
        GameCatalogItem item = resolveGameItem(game);
        return Map.of(
            "game", item.code(),
            "onlineSupportedNow", onlineGameplayImplemented(item.code()),
            "supportsSpectateNow", supportsSpectateNow(item.code()),
            "rooms", listRooms(item.code())
        );
    }

    @ResponseBody
    @PostMapping("/api/create-room")
    public Map<String, Object> createRoom(@RequestParam(required = false) String game) {
        GameCatalogItem item = resolveGameItem(game);
        String gameCode = item.code();

        RoomCreation creation = createRoomForGame(gameCode);

        return Map.of(
            "game", gameCode,
            "roomId", creation.roomId(),
            "serverCreated", creation.serverCreated(),
            "onlineSupportedNow", onlineGameplayImplemented(gameCode),
            "playUrlBase", playUrlBase(gameCode),
            "playRoomParam", playRoomParam(gameCode),
            "playUrlTemplate", playUrlTemplate(gameCode),
            "spectateUrlTemplate", spectateUrlTemplate(gameCode),
            "inviteUrlPathTemplate", invitePathTemplate(gameCode)
        );
    }

    @ResponseBody
    @PostMapping("/api/quick-random")
    public Map<String, Object> quickRandom(@RequestParam(required = false) String game) {
        GameCatalogItem item = resolveGameItem(game);
        String gameCode = item.code();

        List<RoomRow> rows = listRooms(gameCode);
        List<RoomRow> joinableRooms = rows.stream()
            .filter(this::isJoinableRoom)
            .toList();

        String roomId;
        boolean createdNew;
        boolean serverCreated;
        if (!joinableRooms.isEmpty()) {
            RoomRow picked = joinableRooms.get(ThreadLocalRandom.current().nextInt(joinableRooms.size()));
            roomId = picked.roomId();
            createdNew = false;
            serverCreated = false;
        } else {
            RoomCreation creation = createRoomForGame(gameCode);
            roomId = creation.roomId();
            createdNew = true;
            serverCreated = creation.serverCreated();
        }

        return Map.of(
            "game", gameCode,
            "roomId", roomId,
            "createdNew", createdNew,
            "serverCreated", serverCreated,
            "playUrlBase", playUrlBase(gameCode),
            "playRoomParam", playRoomParam(gameCode),
            "playUrlTemplate", playUrlTemplate(gameCode),
            "spectateUrlTemplate", spectateUrlTemplate(gameCode),
            "inviteUrlPathTemplate", invitePathTemplate(gameCode)
        );
    }

    private boolean onlineGameplayImplemented(String gameCode) {
        return "caro".equalsIgnoreCase(gameCode)
            || "cards".equalsIgnoreCase(gameCode)
            || "blackjack".equalsIgnoreCase(gameCode)
            || "chess".equalsIgnoreCase(gameCode)
            || "xiangqi".equalsIgnoreCase(gameCode)
            || "typing".equalsIgnoreCase(gameCode)
            || "quiz".equalsIgnoreCase(gameCode);
    }

    private boolean supportsSpectateNow(String gameCode) {
        return "chess".equalsIgnoreCase(gameCode)
            || "xiangqi".equalsIgnoreCase(gameCode)
            || "blackjack".equalsIgnoreCase(gameCode)
            || "quiz".equalsIgnoreCase(gameCode);
    }

    private String playUrlBase(String gameCode) {
        if ("caro".equalsIgnoreCase(gameCode)) {
            return "/game";
        }
        if ("cards".equalsIgnoreCase(gameCode)) {
            return "/cards/tien-len";
        }
        if ("blackjack".equalsIgnoreCase(gameCode)) {
            return "/games/cards/blackjack";
        }
        if ("chess".equalsIgnoreCase(gameCode)) {
            return "/chess/online";
        }
        if ("xiangqi".equalsIgnoreCase(gameCode)) {
            return "/xiangqi/online";
        }
        if ("typing".equalsIgnoreCase(gameCode)) {
            return "/games/typing";
        }
        if ("quiz".equalsIgnoreCase(gameCode)) {
            return "/games/quiz";
        }
        return "";
    }

    private String playRoomParam(String gameCode) {
        if ("typing".equalsIgnoreCase(gameCode)
            || "quiz".equalsIgnoreCase(gameCode)
            || "blackjack".equalsIgnoreCase(gameCode)) {
            return "room";
        }
        return "roomId";
    }

    private String playUrlTemplate(String gameCode) {
        if ("caro".equalsIgnoreCase(gameCode)) {
            return "/game/room/{roomId}";
        }
        if ("cards".equalsIgnoreCase(gameCode)) {
            return "/cards/tien-len/room/{roomId}";
        }
        if ("blackjack".equalsIgnoreCase(gameCode)) {
            return "/games/cards/blackjack/room/{roomId}";
        }
        if ("chess".equalsIgnoreCase(gameCode)) {
            return "/chess/online/room/{roomId}";
        }
        if ("xiangqi".equalsIgnoreCase(gameCode)) {
            return "/xiangqi/online/room/{roomId}";
        }
        if ("typing".equalsIgnoreCase(gameCode)) {
            return "/games/typing/room/{roomId}";
        }
        if ("quiz".equalsIgnoreCase(gameCode)) {
            return "/games/quiz/room/{roomId}";
        }
        return "";
    }

    private String spectateUrlTemplate(String gameCode) {
        if ("blackjack".equalsIgnoreCase(gameCode)) {
            return "/games/cards/blackjack/room/{roomId}/spectate";
        }
        if ("chess".equalsIgnoreCase(gameCode)) {
            return "/chess/online/room/{roomId}/spectate";
        }
        if ("xiangqi".equalsIgnoreCase(gameCode)) {
            return "/xiangqi/online/room/{roomId}/spectate";
        }
        if ("quiz".equalsIgnoreCase(gameCode)) {
            return "/games/quiz/room/{roomId}/spectate";
        }
        return "";
    }

    private String spectateParamName(String gameCode) {
        if ("quiz".equalsIgnoreCase(gameCode) || "blackjack".equalsIgnoreCase(gameCode)) {
            return "mode";
        }
        if ("chess".equalsIgnoreCase(gameCode) || "xiangqi".equalsIgnoreCase(gameCode)) {
            return "spectate";
        }
        return "";
    }

    private String spectateParamValue(String gameCode) {
        if ("quiz".equalsIgnoreCase(gameCode) || "blackjack".equalsIgnoreCase(gameCode)) {
            return "spectate";
        }
        if ("chess".equalsIgnoreCase(gameCode) || "xiangqi".equalsIgnoreCase(gameCode)) {
            return "true";
        }
        return "";
    }

    private String invitePathTemplate(String gameCode) {
        String playUrlTemplate = playUrlTemplate(gameCode);
        if (!playUrlTemplate.isBlank()) {
            return playUrlTemplate;
        }
        return "/online-hub?game=" + gameCode + "&roomId={roomId}";
    }

    private List<RoomRow> listRooms(String gameCode) {
        if ("caro".equalsIgnoreCase(gameCode)) {
            return gameRoomService.availableRooms().stream()
                .map(roomId -> new RoomRow(roomId, 1, 2, "Dang cho doi thu"))
                .toList();
        }
        if ("cards".equalsIgnoreCase(gameCode)) {
            return tienLenRoomService.availableRooms().stream()
                .map(room -> new RoomRow(
                    room.roomId(),
                    room.playerCount(),
                    room.playerLimit(),
                    "Dang cho du 4 nguoi"
                ))
                .toList();
        }
        if ("blackjack".equalsIgnoreCase(gameCode)) {
            return blackjackService.getAvailableRooms().stream()
                .filter(room -> !room.getPlayers().isEmpty() || !room.getSpectators().isEmpty())
                .map(this::blackjackRoomRow)
                .toList();
        }
        if ("chess".equalsIgnoreCase(gameCode)) {
            return chessOnlineRoomService.availableRooms().stream()
                .map(room -> new RoomRow(
                    room.roomId(),
                    room.playerCount(),
                    room.playerLimit(),
                    room.note()
                ))
                .toList();
        }
        if ("xiangqi".equalsIgnoreCase(gameCode)) {
            return xiangqiOnlineRoomService.availableRooms().stream()
                .map(room -> new RoomRow(
                    room.roomId(),
                    room.playerCount(),
                    room.playerLimit(),
                    room.note()
                ))
                .toList();
        }
        if ("typing".equalsIgnoreCase(gameCode)) {
            return typingService.getAvailableRooms().stream()
                .filter(room -> room.getPlayerCount() > 0)
                .map(this::typingRoomRow)
                .toList();
        }
        if ("quiz".equalsIgnoreCase(gameCode)) {
            return quizService.getAvailableRooms().stream()
                .filter(room -> !room.getPlayers().isEmpty() || !room.getSpectators().isEmpty())
                .map(this::quizRoomRow)
                .toList();
        }
        return List.of();
    }

    private RoomRow typingRoomRow(TypingRoom room) {
        String note = switch (room.getGameState()) {
            case WAITING -> "Dang cho them nguoi choi";
            case PLAYING -> "Dang dua";
            case FINISHED -> "Tran vua ket thuc";
        };
        return new RoomRow(room.getId(), room.getPlayerCount(), room.getPlayerLimit(), note);
    }

    private RoomRow quizRoomRow(QuizRoom room) {
        int playerCount = room.getPlayers().size();
        int spectatorCount = room.getSpectators().size();
        int total = room.getTotalQuestions();
        int current = Math.min(room.getCurrentQuestionIndex() + 1, Math.max(1, total));
        String state = room.isGameOver() ? "FINISHED" : (room.isStarted() ? "PLAYING" : "WAITING");
        String note = "Quiz " + state + " | Q" + current + "/" + total
            + " | Da tra loi " + room.getAnsweredCount() + "/" + playerCount
            + " | Spectators " + spectatorCount;
        return new RoomRow(room.getRoomId(), playerCount, 0, note);
    }

    private RoomRow blackjackRoomRow(BlackjackRoom room) {
        int playerCount = room.getPlayers().size();
        int spectatorCount = room.getSpectators().size();
        String note = "Blackjack " + room.getGameState().name() + " | Spectators " + spectatorCount;
        return new RoomRow(room.getId(), playerCount, 5, note);
    }

    public record RoomRow(String roomId, int playerCount, int playerLimit, String note) {
    }

    private record RoomCreation(String roomId, boolean serverCreated) {
    }

    private RoomCreation createRoomForGame(String gameCode) {
        if ("blackjack".equalsIgnoreCase(gameCode)) {
            return new RoomCreation(blackjackService.createRoom().getId(), true);
        }
        if ("typing".equalsIgnoreCase(gameCode)) {
            return new RoomCreation(typingService.createRoom().getId(), true);
        }
        if ("quiz".equalsIgnoreCase(gameCode)) {
            return new RoomCreation(quizService.createRoom().getRoomId(), true);
        }
        return new RoomCreation(generatedRoomId(gameCode), false);
    }

    private boolean isJoinableRoom(RoomRow row) {
        if (row == null || row.roomId() == null || row.roomId().isBlank()) {
            return false;
        }
        if (row.playerLimit() <= 0) {
            return true;
        }
        return row.playerCount() < row.playerLimit();
    }

    private String generatedRoomId(String gameCode) {
        String prefix = switch (gameCode == null ? "" : gameCode.trim().toLowerCase()) {
            case "caro" -> "CARO";
            case "cards" -> "TL";
            case "blackjack" -> "BJ";
            case "chess" -> "CHESS";
            case "xiangqi" -> "XQ";
            case "typing" -> "TYP";
            case "quiz" -> "QUIZ";
            default -> "ROOM";
        };
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return prefix + "-" + token;
    }

    private GameCatalogItem resolveGameItem(String game) {
        String normalizedGame = game == null ? "" : game.trim();
        String requestedGame = normalizedGame.isEmpty() ? DEFAULT_GAME_CODE : normalizedGame;
        GameCatalogItem item = gameCatalogService.findByCode(requestedGame)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        if (item.isExternalSource()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "External game module uses its own module page or external API gateway"
            );
        }
        return item;
    }
}
