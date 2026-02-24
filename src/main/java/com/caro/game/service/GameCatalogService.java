package com.caro.game.service;

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
                "Game hien tai da ho tro choi online/offline, guest va lobby phong.",
                "bi-grid-3x3-gap-fill",
                true,
                true,
                true,
                true,
                "Vao che do choi",
                "/multiplayer",
                List.of(
                    "Giữ vai tro game mac dinh hien tai.",
                    "Mo rong lich su va thong ke theo tung game."
                )
            ),
            new GameCatalogItem(
                "chess",
                "Co vua",
                "Chess",
                "Da co che do choi offline 2 nguoi cung may (MVP). Online va cac luat nang cao se duoc trien khai tiep.",
                "bi-activity",
                true,
                false,
                true,
                true,
                "Choi offline (MVP)",
                "/chess/offline",
                List.of(
                    "Ban co 8x8, setup quan co day du.",
                    "Luat nuoc di hop le va chieu/chieu het.",
                    "Phong online va dong bo nuoc di."
                )
            ),
            new GameCatalogItem(
                "xiangqi",
                "Co tuong",
                "Xiangqi",
                "Da co che do bot Easy/Hard (MVP) va che do offline 2 nguoi. Online room dang tiep tuc hoan thien.",
                "bi-diagram-3-fill",
                true,
                false,
                true,
                true,
                "Choi voi bot (chon do kho)",
                "/game-mode/bot?game=xiangqi",
                List.of(
                    "Bot Easy/Hard MVP da san sang.",
                    "Hoan thien them luat/edge-case Co tuong neu can.",
                    "Online room va xu ly ket thuc van co."
                )
            ),
            new GameCatalogItem(
                "cards",
                "Danh bai",
                "Cards",
                "Da co Tien len online 4 nguoi (MVP) va che do choi voi bot Easy/Hard (MVP). Se tiep tuc hoan thien luat nang cao o buoc sau.",
                "bi-suit-spade-fill",
                true,
                true,
                true,
                true,
                "Tien len online 4 nguoi (MVP)",
                "/cards/tien-len",
                List.of(
                    "MVP hien tai: online 4 nguoi + bot Easy/Hard, ho tro don, doi, sam, tu quy, sanh.",
                    "Bo sung luat nang cao Tien len (chat 2, doi thong, bao toi/trang neu can).",
                    "Them cac game bai khac sau khi on dinh module Tien len."
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
