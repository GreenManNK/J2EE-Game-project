package com.caro.game.controller;

import com.caro.game.cards.tienlen.service.TienLenRoomService;
import com.caro.game.service.GameCatalogItem;
import com.caro.game.service.GameCatalogService;
import com.caro.game.service.GameRoomService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/online-hub")
public class OnlineHubController {
    private final GameCatalogService gameCatalogService;
    private final GameRoomService gameRoomService;
    private final TienLenRoomService tienLenRoomService;

    public OnlineHubController(GameCatalogService gameCatalogService,
                               GameRoomService gameRoomService,
                               TienLenRoomService tienLenRoomService) {
        this.gameCatalogService = gameCatalogService;
        this.gameRoomService = gameRoomService;
        this.tienLenRoomService = tienLenRoomService;
    }

    @GetMapping
    public String index(@RequestParam String game,
                        @RequestParam(required = false) String roomId,
                        Model model) {
        GameCatalogItem item = gameCatalogService.findByCode(game)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        model.addAttribute("selectedGame", item);
        model.addAttribute("selectedGameCode", item.code());
        model.addAttribute("selectedGameName", item.displayName());
        model.addAttribute("selectedRoomId", roomId == null ? "" : roomId.trim());
        model.addAttribute("onlineSupportedNow", onlineGameplayImplemented(item.code()));
        model.addAttribute("roomRows", listRooms(item.code()));
        model.addAttribute("playUrlBase", playUrlBase(item.code()));
        model.addAttribute("inviteUrlPathTemplate", invitePathTemplate(item.code()));
        return "online-hub/index";
    }

    @ResponseBody
    @GetMapping("/api/rooms")
    public Map<String, Object> rooms(@RequestParam String game) {
        GameCatalogItem item = gameCatalogService.findByCode(game)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        return Map.of(
            "game", item.code(),
            "onlineSupportedNow", onlineGameplayImplemented(item.code()),
            "rooms", listRooms(item.code())
        );
    }

    private boolean onlineGameplayImplemented(String gameCode) {
        return "caro".equalsIgnoreCase(gameCode) || "cards".equalsIgnoreCase(gameCode);
    }

    private String playUrlBase(String gameCode) {
        if ("caro".equalsIgnoreCase(gameCode)) {
            return "/game";
        }
        if ("cards".equalsIgnoreCase(gameCode)) {
            return "/cards/tien-len";
        }
        return "";
    }

    private String invitePathTemplate(String gameCode) {
        if ("caro".equalsIgnoreCase(gameCode)) {
            return "/online-hub?game=caro&roomId={roomId}";
        }
        if ("cards".equalsIgnoreCase(gameCode)) {
            return "/online-hub?game=cards&roomId={roomId}";
        }
        if ("chess".equalsIgnoreCase(gameCode)) {
            return "/online-hub?game=chess&roomId={roomId}";
        }
        if ("xiangqi".equalsIgnoreCase(gameCode)) {
            return "/online-hub?game=xiangqi&roomId={roomId}";
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
        return List.of();
    }

    public record RoomRow(String roomId, int playerCount, int playerLimit, String note) {
    }
}
