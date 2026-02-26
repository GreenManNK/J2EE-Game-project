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
                "Game hien tai da ho tro choi online/offline, guest va lobby phong.",
                "bi-grid-3x3-gap-fill",
                true,
                true,
                true,
                true,
                "Vao che do choi",
                "/multiplayer",
                List.of(
                    "Giá»¯ vai tro game mac dinh hien tai.",
                    "Mo rong lich su va thong ke theo tung game."
                )
            ),
            new GameCatalogItem(
                "chess",
                "Co vua",
                "Chess",
                "Da co che do bot Easy/Hard, offline 2 nguoi cung may va online room (MVP). Se tiep tuc hoan thien luat/online nang cao.",
                "bi-activity",
                true,
                true,
                true,
                true,
                "Co vua online (MVP)",
                "/online-hub?game=chess",
                List.of(
                    "Ban co 8x8, setup quan co day du.",
                    "Bot Easy/Hard va che do offline 2 nguoi.",
                    "Phong online va dong bo nuoc di (MVP)."
                )
            ),
            new GameCatalogItem(
                "xiangqi",
                "Co tuong",
                "Xiangqi",
                "Da co che do bot Easy/Hard (MVP), offline 2 nguoi va online room (MVP). Se tiep tuc hoan thien luat/online nang cao.",
                "bi-diagram-3-fill",
                true,
                true,
                true,
                true,
                "Co tuong online (MVP)",
                "/online-hub?game=xiangqi",
                List.of(
                    "Bot Easy/Hard MVP da san sang.",
                    "Offline 2 nguoi cung may.",
                    "Online room va dong bo nuoc di (MVP)."
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
                    "MVP hien tai: online 4 nguoi + bot Easy/Hard, ho tro don, doi, sam, tu quy, sanh, doi thong va chat 2 co ban (tu quy / doi thong).",
                    "Bo sung them luat nang cao Tien len (doi thong nang cao, bao toi/trang neu can).",
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
