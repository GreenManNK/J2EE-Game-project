package com.game.hub.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GameCatalogService {
    private final List<GameCatalogItem> items;
    private final Map<String, GameCatalogItem> byCode;

    public GameCatalogService() {
        this.items = List.of(
            new GameCatalogItem(
                "caro",
                "Caro",
                "Caro",
                "Caro da ho tro online room, offline 2 nguoi, bot Easy/Hard va guest.",
                "bi-grid-3x3-gap-fill",
                true,
                true,
                true,
                true,
                "Vao Caro",
                "/multiplayer",
                List.of(
                    "Lobby + room invite + ws realtime.",
                    "Bot Easy/Hard va lich su tran dau.",
                    "Guest mode va profile dang nhap."
                )
            ),
            new GameCatalogItem(
                "chess",
                "Co vua",
                "Chess",
                "Co vua da co online room 1v1, bot Easy/Hard va offline 2 nguoi cung may.",
                "bi-activity",
                true,
                true,
                true,
                true,
                "Co vua online",
                "/online-hub?game=chess",
                List.of(
                    "Ban co 8x8 va setup quan co day du.",
                    "Bot Easy/Hard va che do offline local.",
                    "Phong online va dong bo nuoc di realtime."
                )
            ),
            new GameCatalogItem(
                "xiangqi",
                "Co tuong",
                "Xiangqi",
                "Co tuong da co online room, bot Easy/Hard va offline 2 nguoi cung may.",
                "bi-diagram-3-fill",
                true,
                true,
                true,
                true,
                "Co tuong online",
                "/online-hub?game=xiangqi",
                List.of(
                    "Bot Easy/Hard da san sang.",
                    "Offline 2 nguoi cung may.",
                    "Online room va dong bo nuoc di realtime."
                )
            ),
            new GameCatalogItem(
                "minesweeper",
                "Do min",
                "Minesweeper",
                "Minesweeper offline voi beginner/intermediate/expert, co cam co va first-click safe.",
                "bi-asterisk",
                true,
                false,
                true,
                true,
                "Mo Minesweeper",
                "/minesweeper",
                List.of(
                    "3 muc do beginner/intermediate/expert.",
                    "Cam co va first-click safe.",
                    "Cap nhat achievement khi thang."
                )
            ),
            new GameCatalogItem(
                "cards",
                "Danh bai",
                "Cards",
                "Module Cards da co Tien Len online 4 nguoi, bot Easy/Hard va them mode Blackjack.",
                "bi-suit-spade-fill",
                true,
                true,
                true,
                true,
                "Mo Cards hub",
                "/games/cards",
                List.of(
                    "Tien Len online 4 nguoi + room realtime.",
                    "Tien Len bot Easy/Hard va bo luat mo rong.",
                    "Blackjack voi bot dealer da co route rieng."
                )
            ),
            new GameCatalogItem(
                "quiz",
                "Quiz",
                "Quiz",
                "Quiz da ho tro tao room, join room, spectate va bang diem cao.",
                "bi-patch-question-fill",
                true,
                true,
                false,
                true,
                "Mo Quiz",
                "/games/quiz",
                List.of(
                    "Tao/join/spectate room quiz.",
                    "Nhieu dang cau hoi: single, multiple, typed.",
                    "Theo doi high score."
                )
            ),
            new GameCatalogItem(
                "typing",
                "Typing Battle",
                "Typing",
                "Typing Battle realtime voi tao/join room, theo doi progress va accuracy.",
                "bi-keyboard-fill",
                true,
                true,
                false,
                true,
                "Mo Typing Battle",
                "/games/typing",
                List.of(
                    "Tao/join room typing realtime.",
                    "Theo doi progress + accuracy cua tung nguoi choi.",
                    "Thong bao winner khi ket thuc."
                )
            ),
            new GameCatalogItem(
                "puzzle",
                "Puzzles",
                "Puzzle",
                "Puzzle pack gom Jigsaw, Sliding, Word Puzzle va Sudoku.",
                "bi-puzzle-fill",
                true,
                false,
                true,
                true,
                "Mo Puzzle Pack",
                "/games/puzzle",
                List.of(
                    "Jigsaw puzzle.",
                    "Sliding puzzle.",
                    "Word puzzle va Sudoku."
                )
            )
        );
        this.byCode = this.items.stream()
            .collect(Collectors.toUnmodifiableMap(
                item -> normalize(item.code()),
                Function.identity()
            ));
    }

    public List<GameCatalogItem> findAll() {
        return items;
    }

    public Optional<GameCatalogItem> findByCode(String code) {
        return Optional.ofNullable(byCode.get(normalize(code)));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
