package com.game.hub.games.monopoly.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

final class MonopolyGameEngine {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int JAIL_BAIL = 50;
    private static final int MAX_LOG_ITEMS = 28;
    private static final List<String> PLAYER_COLORS = List.of("#f97316", "#22c55e", "#38bdf8", "#f43f5e");
    private static final List<String> BOARD_DEFS = List.of(
        "0|go|go|GO|corner|Di qua nhan 200$.|||||",
        "1|pho-co|property|Pho Co|brown||60|30|50||2,10,30,90,160,250",
        "2|community-1|community|Community Chest|event|Rut 1 the cong dong.|||||",
        "3|ben-nghe|property|Ben Nghe|brown||60|30|50||4,20,60,180,320,450",
        "4|income-tax|tax|Income Tax|tax|Dong 120$ vao quy free parking.||||120|",
        "5|ga-sai-gon|railroad|Ga Sai Gon|railroad||200|100|||",
        "6|ho-tay|property|Ho Tay|lightBlue||100|50|50||6,30,90,270,400,550",
        "7|chance-1|chance|Chance|event|Rut 1 the co hoi.|||||",
        "8|phu-nhuan|property|Phu Nhuan|lightBlue||100|50|50||6,30,90,270,400,550",
        "9|cau-giay|property|Cau Giay|lightBlue||120|60|50||8,40,100,300,450,600",
        "10|jail|jail|Jail / Visit|corner|Vao tham hoac cho ra tu.|||||",
        "11|hai-chau|property|Hai Chau|pink||140|70|100||10,50,150,450,625,750",
        "12|dien-luc|utility|Dien luc|utility||150|75|||",
        "13|ninh-kieu|property|Ninh Kieu|pink||140|70|100||10,50,150,450,625,750",
        "14|da-kao|property|Da Kao|pink||160|80|100||12,60,180,500,700,900",
        "15|ga-ha-noi|railroad|Ga Ha Noi|railroad||200|100|||",
        "16|tay-ho|property|Tay Ho|orange||180|90|100||14,70,200,550,750,950",
        "17|community-2|community|Community Chest|event|Rut 1 the cong dong.|||||",
        "18|district-1|property|District 1|orange||180|90|100||14,70,200,550,750,950",
        "19|my-khe|property|My Khe|orange||200|100|100||16,80,220,600,800,1000",
        "20|free-parking|freeParking|Free Parking|corner|Nhan toan bo quy jackpot.|||||",
        "21|vung-tau|property|Vung Tau|red||220|110|150||18,90,250,700,875,1050",
        "22|chance-2|chance|Chance|event|Rut 1 the co hoi.|||||",
        "23|thu-duc|property|Thu Duc|red||220|110|150||18,90,250,700,875,1050",
        "24|hai-phong|property|Hai Phong Port|red||240|120|150||20,100,300,750,925,1100",
        "25|ga-da-nang|railroad|Ga Da Nang|railroad||200|100|||",
        "26|sala|property|Sala|yellow||260|130|150||22,110,330,800,975,1150",
        "27|tay-ho-tay|property|Tay Ho Tay|yellow||260|130|150||22,110,330,800,975,1150",
        "28|cap-nuoc|utility|Cap nuoc|utility||150|75|||",
        "29|phu-my-hung|property|Phu My Hung|yellow||280|140|150||24,120,360,850,1025,1200",
        "30|go-to-jail|goToJail|Go To Jail|corner|Di thang vao tu.|||||",
        "31|cau-rong|property|Cau Rong|green||300|150|200||26,130,390,900,1100,1275",
        "32|van-mieu|property|Van Mieu|green||300|150|200||26,130,390,900,1100,1275",
        "33|community-3|community|Community Chest|event|Rut 1 the cong dong.|||||",
        "34|landmark-81|property|Landmark 81|green||320|160|200||28,150,450,1000,1200,1400",
        "35|ga-nha-trang|railroad|Ga Nha Trang|railroad||200|100|||",
        "36|chance-3|chance|Chance|event|Rut 1 the co hoi.|||||",
        "37|ba-na-hills|property|Ba Na Hills|darkBlue||350|175|200||35,175,500,1100,1300,1500",
        "38|luxury-tax|tax|Luxury Tax|tax|Dong 100$ vao quy free parking.||||100|",
        "39|west-lake-view|property|West Lake View|darkBlue||400|200|200||50,200,600,1400,1700,2000"
    );
    private static final List<CardDef> CHANCE_CARDS = List.of(
        CardDef.move(0, true, "Tien toi GO va nhan 200$."),
        CardDef.move(24, true, "Tien toi Hai Phong Port."),
        CardDef.kind("nearestRailroad", "Tien toi nha ga gan nhat. Neu da co chu, tra gap doi tien thue."),
        CardDef.kind("nearestUtility", "Tien toi cong ty gan nhat. Neu da co chu, tra 10x tong xuc xac."),
        CardDef.moveBack(3, "Lui 3 o."),
        CardDef.cash(50, false, "Co tuc ngan hang: nhan 50$."),
        CardDef.cash(-15, true, "Vi pham toc do: nop 15$."),
        CardDef.repairs(25, 100, "Sua chua tai san: 25$/nha, 100$/hotel."),
        CardDef.kind("gotoJail", "Di thang vao tu."),
        CardDef.kind("escape", "Giu the nay de ra tu mien phi.")
    );
    private static final List<CardDef> CHEST_CARDS = List.of(
        CardDef.move(0, true, "Von quy phat thuong: tien toi GO."),
        CardDef.cash(200, false, "Loi he thong ngan hang: nhan 200$."),
        CardDef.cash(100, false, "Hoan tra phuc loi: nhan 100$."),
        CardDef.cash(45, false, "Ban tai san nho: nhan 45$."),
        CardDef.cash(-50, true, "Vien phi va giay to: nop 50$."),
        CardDef.cash(-100, true, "Sua he thong nha cua: nop 100$."),
        CardDef.cash(25, false, "Phi tu van thanh cong: nhan 25$."),
        CardDef.repairs(40, 115, "Bao tri nha dat: 40$/nha, 115$/hotel."),
        CardDef.kind("gotoJail", "Bi trieu tap: vao tu."),
        CardDef.kind("escape", "Giu the nay de ra tu mien phi.")
    );

    Map<String, Object> createInitialState(List<PlayerSeed> players, int startingCash, int passGoAmount, Random random) {
        EngineState state = new EngineState();
        state.players = new ArrayList<>();
        state.board = cloneBoard();
        state.chanceDeck = createDeck(CHANCE_CARDS, random);
        state.chestDeck = createDeck(CHEST_CARDS, random);
        state.currentPlayerIndex = 0;
        state.selectedTileIndex = 0;
        state.phase = "await_roll";
        state.log = new ArrayList<>();
        state.round = 1;
        state.turnNumber = 1;
        state.settings = new EngineSettings();
        state.settings.passGoAmount = passGoAmount;

        for (int index = 0; index < players.size(); index += 1) {
            PlayerSeed seed = players.get(index);
            EnginePlayer player = new EnginePlayer();
            player.id = seed.playerId();
            player.name = seed.name();
            player.color = PLAYER_COLORS.get(index % PLAYER_COLORS.size());
            player.token = seed.token();
            player.turnOrder = seed.turnOrder();
            player.money = startingCash;
            state.players.add(player);
        }

        if (!state.players.isEmpty()) {
            logAction(state, "Van moi", "Ban choi da san sang. " + state.players.get(0).name + " di truoc.");
        }
        return toMap(state);
    }

    ActionResult applyAction(Map<String, Object> rawState, String actorId, String action, Random random) {
        EngineState state = toState(rawState);
        EnginePlayer current = currentPlayer(state);
        if (current == null) {
            return ActionResult.error("Khong tim thay nguoi choi hien tai");
        }
        if (!Objects.equals(normalize(actorId), normalize(current.id))) {
            return ActionResult.error("Chua den luot cua ban");
        }

        String normalizedAction = normalize(action);
        if (normalizedAction == null) {
            return ActionResult.error("Action khong hop le");
        }

        return switch (normalizedAction) {
            case "roll" -> run(state, () -> handleRoll(state, random));
            case "buy" -> run(state, () -> handleBuy(state));
            case "skippurchase" -> run(state, () -> handleSkipPurchase(state));
            case "endturn" -> run(state, () -> handleEndTurn(state));
            case "paybail" -> run(state, () -> handlePayBail(state));
            case "useescapecard" -> run(state, () -> handleUseEscapeCard(state));
            case "declarebankruptcy" -> run(state, () -> handleDeclareBankruptcy(state));
            case "finishgame" -> run(state, () -> finishGame(state, "Van dau duoc chot bang tay."));
            default -> ActionResult.error("Action chua duoc ho tro o room mode");
        };
    }

    private ActionResult run(EngineState state, Mutation mutation) {
        String error = mutation.run();
        return error == null ? ActionResult.ok(toMap(state)) : ActionResult.error(error);
    }

    private String handleRoll(EngineState state, Random random) {
        EnginePlayer player = currentPlayer(state);
        if (player == null || player.bankrupt || state.debt != null || !List.of("await_roll", "jail").contains(state.phase)) {
            return "Khong the tung xuc xac luc nay";
        }

        DiceResult dice = rollDice(random);
        state.lastDice = dice;
        state.lastCard = null;

        if (player.inJail && "jail".equals(state.phase)) {
            return handleJailRoll(state, player, dice);
        }

        player.consecutiveDoubles = dice.doubles ? player.consecutiveDoubles + 1 : 0;
        state.pendingExtraRoll = dice.doubles;
        if (player.consecutiveDoubles >= 3) {
            logAction(state, "3 lan double", player.name + " bi dua vao tu vi double 3 lan lien tuc.");
            sendToJail(state, player, "Double 3 lan lien tuc.");
            return null;
        }

        moveBy(state, player, dice.total, "Tung " + dice.a + " va " + dice.b + ".", random);
        return null;
    }

    private String handleJailRoll(EngineState state, EnginePlayer player, DiceResult dice) {
        state.pendingExtraRoll = false;
        if (dice.doubles) {
            player.inJail = false;
            player.jailTurns = 0;
            player.consecutiveDoubles = 0;
            logAction(state, "Ra tu", player.name + " tung double va duoc ra tu.");
            moveBy(state, player, dice.total, "Ra tu bang double.", null);
            return null;
        }

        player.jailTurns += 1;
        if (player.jailTurns >= 3) {
            player.inJail = false;
            player.jailTurns = 0;
            charge(state, state.currentPlayerIndex, JAIL_BAIL, "Tien bao lanh sau 3 luot trong tu.", null, true);
            logAction(state, "Ra tu", player.name + " het 3 luot trong tu va phai nop 50$ de tiep tuc.");
            if (state.debt == null) {
                moveBy(state, player, dice.total, "Ra tu sau 3 luot.", null);
            }
            return null;
        }

        state.phase = "await_end_turn";
        logAction(state, "Trong tu", player.name + " chua ra tu va phai cho luot sau.");
        return null;
    }

    private String handleBuy(EngineState state) {
        EnginePlayer player = currentPlayer(state);
        if (player == null || state.pendingPurchase == null) {
            return "Khong co tai san dang cho mua";
        }
        EngineSpace tile = board(state, state.pendingPurchase.tileIndex);
        if (tile == null || tile.price == null || player.money < tile.price) {
            return "Khong du tien de mua tai san nay";
        }
        player.money -= tile.price;
        tile.ownerId = player.id;
        tile.houses = 0;
        tile.mortgaged = false;
        logAction(state, "Mua tai san", player.name + " mua " + tile.name + " voi gia " + money(tile.price) + ".");
        state.pendingPurchase = null;
        finishStep(state);
        return null;
    }

    private String handleSkipPurchase(EngineState state) {
        if (state.pendingPurchase == null) {
            return "Khong co lua chon mua nao dang mo";
        }
        EnginePlayer player = currentPlayer(state);
        EngineSpace tile = board(state, state.pendingPurchase.tileIndex);
        logAction(state, "Bo qua", player.name + " bo qua quyen mua " + (tile == null ? "tai san" : tile.name) + ".");
        state.pendingPurchase = null;
        finishStep(state);
        return null;
    }

    private String handleEndTurn(EngineState state) {
        EnginePlayer player = currentPlayer(state);
        if (player == null || !"await_end_turn".equals(state.phase)) {
            return "Chua the ket thuc luot luc nay";
        }
        player.consecutiveDoubles = 0;
        state.pendingExtraRoll = false;
        nextTurn(state);
        return null;
    }

    private String handlePayBail(EngineState state) {
        EnginePlayer player = currentPlayer(state);
        if (player == null || !player.inJail || !"jail".equals(state.phase) || player.money < JAIL_BAIL) {
            return "Khong the nop bao lanh luc nay";
        }
        player.money -= JAIL_BAIL;
        state.freeParkingPot += JAIL_BAIL;
        player.inJail = false;
        player.jailTurns = 0;
        state.phase = "await_roll";
        logAction(state, "Bao lanh", player.name + " nop 50$ de ra tu.");
        return null;
    }

    private String handleUseEscapeCard(EngineState state) {
        EnginePlayer player = currentPlayer(state);
        if (player == null || !player.inJail || !"jail".equals(state.phase) || player.escapeCards <= 0) {
            return "Khong the dung the ra tu luc nay";
        }
        player.escapeCards -= 1;
        player.inJail = false;
        player.jailTurns = 0;
        state.phase = "await_roll";
        logAction(state, "The ra tu", player.name + " dung the ra tu mien phi.");
        return null;
    }

    private String handleDeclareBankruptcy(EngineState state) {
        if (state.debt == null || !Objects.equals(state.debt.playerId, currentPlayer(state).id)) {
            return "Khong co khoan no nao de tuyen bo pha san";
        }
        bankrupt(state, "Khong the can bang no.");
        return null;
    }

    private String finishGame(EngineState state, String reason) {
        List<EnginePlayer> ranked = new ArrayList<>(state.players);
        ranked.sort((left, right) -> Integer.compare(netWorth(state, right), netWorth(state, left)));
        state.phase = "ended";
        state.pendingPurchase = null;
        state.pendingExtraRoll = false;
        state.debt = null;
        state.winnerId = ranked.isEmpty() ? null : ranked.get(0).id;
        if (!ranked.isEmpty()) {
            logAction(state, "Ket van", reason + " Dan dau: " + ranked.get(0).name + " voi gia tri rong " + money(netWorth(state, ranked.get(0))) + ".");
        } else {
            logAction(state, "Ket van", reason);
        }
        return null;
    }

    private void moveBy(EngineState state, EnginePlayer player, int steps, String reason, Random random) {
        int origin = player.position;
        int destination = (origin + steps) % state.board.size();
        boolean passedGo = origin + steps >= state.board.size();
        int passGo = Math.max(50, state.settings == null || state.settings.passGoAmount == null ? MonopolyRoomService.DEFAULT_PASS_GO_AMOUNT : state.settings.passGoAmount);
        if (passedGo) {
            player.money += passGo;
            logAction(state, "Qua GO", player.name + " nhan " + money(passGo) + " khi di qua GO.");
        }
        player.position = destination;
        state.selectedTileIndex = destination;
        logAction(state, "Di chuyen", player.name + " den " + board(state, destination).name + ". " + reason);
        resolveLanding(state, player, board(state, destination), random);
    }

    private void moveTo(EngineState state, EnginePlayer player, int destination, String reason, boolean collectGo, Random random) {
        boolean passedGo = collectGo || destination < player.position;
        int passGo = Math.max(50, state.settings == null || state.settings.passGoAmount == null ? MonopolyRoomService.DEFAULT_PASS_GO_AMOUNT : state.settings.passGoAmount);
        if (passedGo) {
            player.money += passGo;
            logAction(state, "Qua GO", player.name + " nhan " + money(passGo) + " khi di qua GO.");
        }
        player.position = destination;
        state.selectedTileIndex = destination;
        logAction(state, "The su kien", player.name + " di toi " + board(state, destination).name + ". " + reason);
        resolveLanding(state, player, board(state, destination), random);
    }

    private void resolveLanding(EngineState state, EnginePlayer player, EngineSpace tile, Random random) {
        if (tile == null || player.bankrupt) {
            return;
        }
        state.selectedTileIndex = tile.index;
        switch (tile.type) {
            case "go", "jail" -> finishStep(state);
            case "freeParking" -> {
                if (state.freeParkingPot > 0) {
                    player.money += state.freeParkingPot;
                    logAction(state, "Free parking", player.name + " nhat quy jackpot " + money(state.freeParkingPot) + ".");
                    state.freeParkingPot = 0;
                }
                finishStep(state);
            }
            case "goToJail" -> sendToJail(state, player, "Dung o Go To Jail.");
            case "tax" -> charge(state, state.currentPlayerIndex, tile.amount == null ? 0 : tile.amount, tile.name, null, true);
            case "chance" -> drawCard(state, true, random);
            case "community" -> drawCard(state, false, random);
            case "property", "railroad", "utility" -> resolveAsset(state, player, tile);
            default -> finishStep(state);
        }
    }

    private void resolveAsset(EngineState state, EnginePlayer player, EngineSpace tile) {
        EnginePlayer owner = owner(state, tile);
        if (owner == null) {
            PendingPurchase pending = new PendingPurchase();
            pending.tileIndex = tile.index;
            state.pendingPurchase = pending;
            state.phase = "await_purchase";
            return;
        }
        if (Objects.equals(owner.id, player.id)) {
            finishStep(state);
            return;
        }
        if (Boolean.TRUE.equals(tile.mortgaged)) {
            logAction(state, "Khong thu tien", tile.name + " dang duoc the chap nen khong phat sinh tien thue.");
            finishStep(state);
            return;
        }

        int rent = rent(state, tile);
        if ("utility".equals(tile.type)) {
            int utilityCount = (int) state.board.stream().filter(space -> "utility".equals(space.type) && Objects.equals(space.ownerId, owner.id)).count();
            rent = (utilityCount >= 2 ? 10 : 4) * (state.lastDice == null ? 7 : state.lastDice.total);
        }
        if (state.specialRent != null && Objects.equals(state.specialRent.tileIndex, tile.index)) {
            if ("railroad".equals(state.specialRent.kind)) {
                rent *= 2;
            } else if ("utility".equals(state.specialRent.kind)) {
                rent = 10 * (state.lastDice == null ? 7 : state.lastDice.total);
            }
        }
        state.specialRent = null;
        charge(state, state.currentPlayerIndex, rent, "Tien thue " + tile.name, owner.id, false);
    }

    private void drawCard(EngineState state, boolean chance, Random random) {
        List<EngineCard> deck = chance ? state.chanceDeck : state.chestDeck;
        if (deck == null || deck.isEmpty()) {
            deck = createDeck(chance ? CHANCE_CARDS : CHEST_CARDS, random);
            if (chance) {
                state.chanceDeck = deck;
            } else {
                state.chestDeck = deck;
            }
        }
        EngineCard card = deck.remove(0);
        LastCard lastCard = new LastCard();
        lastCard.title = chance ? "Chance" : "Community Chest";
        lastCard.label = card.label;
        state.lastCard = lastCard;
        applyCard(state, card, random);
    }

    private void applyCard(EngineState state, EngineCard card, Random random) {
        EnginePlayer player = currentPlayer(state);
        if (player == null) {
            return;
        }
        switch (card.kind) {
            case "move" -> moveTo(state, player, card.target, card.label, Boolean.TRUE.equals(card.collectGo), random);
            case "moveBack" -> {
                int destination = (player.position - card.steps + state.board.size()) % state.board.size();
                player.position = destination;
                state.selectedTileIndex = destination;
                logAction(state, "The su kien", player.name + " lui " + card.steps + " o.");
                resolveLanding(state, player, board(state, destination), random);
            }
            case "nearestRailroad" -> {
                SpecialRent special = new SpecialRent();
                special.tileIndex = nearest(state, player.position, "railroad");
                special.kind = "railroad";
                state.specialRent = special;
                moveTo(state, player, special.tileIndex, card.label, special.tileIndex < player.position, random);
            }
            case "nearestUtility" -> {
                SpecialRent special = new SpecialRent();
                special.tileIndex = nearest(state, player.position, "utility");
                special.kind = "utility";
                state.specialRent = special;
                moveTo(state, player, special.tileIndex, card.label, special.tileIndex < player.position, random);
            }
            case "cash" -> {
                if (card.amount >= 0) {
                    player.money += card.amount;
                    logAction(state, "The su kien", player.name + " nhan " + money(card.amount) + ".");
                    finishStep(state);
                } else {
                    charge(state, state.currentPlayerIndex, Math.abs(card.amount), card.label, null, Boolean.TRUE.equals(card.toPot));
                }
            }
            case "repairs" -> {
                int repairAmount = owned(state, player.id).stream()
                    .filter(space -> "property".equals(space.type))
                    .mapToInt(space -> space.houses >= 5 ? card.hotelFee : space.houses * card.houseFee)
                    .sum();
                if (repairAmount > 0) {
                    charge(state, state.currentPlayerIndex, repairAmount, card.label, null, true);
                } else {
                    logAction(state, "The su kien", player.name + " khong co nha/hotel de sua chua.");
                    finishStep(state);
                }
            }
            case "gotoJail" -> sendToJail(state, player, card.label);
            case "escape" -> {
                player.escapeCards += 1;
                logAction(state, "The su kien", player.name + " nhan 1 the ra tu mien phi.");
                finishStep(state);
            }
            default -> finishStep(state);
        }
    }

    private void charge(EngineState state, int playerIndex, int amount, String reason, String creditorId, boolean toPot) {
        EnginePlayer player = playerIndex >= 0 && playerIndex < state.players.size() ? state.players.get(playerIndex) : null;
        if (player == null || amount <= 0 || player.bankrupt) {
            finishStep(state);
            return;
        }
        if (player.money >= amount) {
            player.money -= amount;
            if (creditorId != null) {
                EnginePlayer creditor = player(state, creditorId);
                if (creditor != null) {
                    creditor.money += amount;
                }
            }
            if (toPot) {
                state.freeParkingPot += amount;
            }
            logAction(state, "Thanh toan", player.name + " tra " + money(amount) + " cho " + (creditorId == null ? "ngan hang" : player(state, creditorId).name) + ".");
            finishStep(state);
            return;
        }
        DebtState debt = new DebtState();
        debt.playerId = player.id;
        debt.creditorId = creditorId;
        debt.amount = amount;
        debt.reason = reason;
        debt.toPot = toPot;
        state.debt = debt;
        state.phase = "debt";
        logAction(state, "Can xoay von", player.name + " can " + money(amount) + " de thanh toan: " + reason);
    }

    private void settleDebt(EngineState state) {
        if (state.debt == null) {
            return;
        }
        EnginePlayer debtor = player(state, state.debt.playerId);
        if (debtor == null || debtor.money < state.debt.amount) {
            return;
        }
        debtor.money -= state.debt.amount;
        if (state.debt.creditorId != null) {
            EnginePlayer creditor = player(state, state.debt.creditorId);
            if (creditor != null) {
                creditor.money += state.debt.amount;
            }
        }
        if (state.debt.toPot) {
            state.freeParkingPot += state.debt.amount;
        }
        logAction(state, "Tra no", debtor.name + " vua can bang khoan no " + money(state.debt.amount) + ".");
        state.debt = null;
        finishStep(state);
    }

    private void sendToJail(EngineState state, EnginePlayer player, String reason) {
        player.position = 10;
        player.inJail = true;
        player.jailTurns = 0;
        player.consecutiveDoubles = 0;
        state.pendingExtraRoll = false;
        state.pendingPurchase = null;
        state.phase = "await_end_turn";
        state.selectedTileIndex = 10;
        logAction(state, "Vao tu", player.name + " bi dua vao tu. " + reason);
    }

    private void bankrupt(EngineState state, String reason) {
        EnginePlayer debtor = currentPlayer(state);
        EnginePlayer creditor = state.debt != null && state.debt.creditorId != null ? player(state, state.debt.creditorId) : null;
        for (EngineSpace tile : owned(state, debtor.id)) {
            if (creditor != null) {
                tile.ownerId = creditor.id;
            } else {
                tile.ownerId = null;
                tile.houses = 0;
                tile.mortgaged = false;
            }
        }
        if (creditor != null) {
            creditor.money += debtor.money;
        } else if (state.debt != null && state.debt.toPot) {
            state.freeParkingPot += debtor.money;
        }
        debtor.money = 0;
        debtor.bankrupt = true;
        debtor.inJail = false;
        debtor.escapeCards = 0;
        state.debt = null;
        state.pendingPurchase = null;
        state.pendingExtraRoll = false;
        logAction(state, "Pha san", debtor.name + " pha san. " + reason);
        if (active(state) <= 1) {
            finishGame(state, "Chi con 1 nguoi choi chua pha san.");
        } else {
            nextTurn(state);
        }
    }

    private void finishStep(EngineState state) {
        if ("ended".equals(state.phase)) {
            return;
        }
        if (state.debt != null) {
            state.phase = "debt";
            return;
        }
        if (active(state) <= 1) {
            finishGame(state, "Chi con 1 nguoi choi chua pha san.");
            return;
        }
        state.phase = state.pendingExtraRoll ? "await_roll" : "await_end_turn";
    }

    private void nextTurn(EngineState state) {
        int next = state.currentPlayerIndex;
        do {
            next = (next + 1) % state.players.size();
            if (next == 0) {
                state.round += 1;
            }
        } while (state.players.get(next).bankrupt);
        state.currentPlayerIndex = next;
        state.turnNumber += 1;
        state.lastDice = null;
        state.lastCard = null;
        state.pendingPurchase = null;
        state.specialRent = null;
        state.selectedTileIndex = state.players.get(next).position;
        state.phase = state.players.get(next).inJail ? "jail" : "await_roll";
        logAction(state, "Chuyen luot", state.players.get(next).name + " den luot.");
    }

    private int nearest(EngineState state, int start, String type) {
        for (int offset = 1; offset <= state.board.size(); offset += 1) {
            int candidate = (start + offset) % state.board.size();
            if (Objects.equals(state.board.get(candidate).type, type)) {
                return candidate;
            }
        }
        return start;
    }

    private int rent(EngineState state, EngineSpace tile) {
        if (Boolean.TRUE.equals(tile.mortgaged)) {
            return 0;
        }
        if ("property".equals(tile.type)) {
            if (tile.houses >= 5) {
                return tile.rent.get(5);
            }
            if (tile.houses > 0) {
                return tile.rent.get(tile.houses);
            }
            EnginePlayer owner = owner(state, tile);
            int base = tile.rent.get(0);
            if (owner != null && ownsFullSet(state, owner.id, tile.group)) {
                return base * 2;
            }
            return base;
        }
        if ("railroad".equals(tile.type)) {
            int count = (int) state.board.stream().filter(space -> "railroad".equals(space.type) && Objects.equals(space.ownerId, tile.ownerId)).count();
            return switch (count) {
                case 1 -> 25;
                case 2 -> 50;
                case 3 -> 100;
                case 4 -> 200;
                default -> 0;
            };
        }
        return 0;
    }

    private boolean ownsFullSet(EngineState state, String ownerId, String group) {
        List<EngineSpace> groupTiles = state.board.stream().filter(space -> Objects.equals(space.group, group) && "property".equals(space.type)).toList();
        return !groupTiles.isEmpty() && groupTiles.stream().allMatch(space -> Objects.equals(space.ownerId, ownerId));
    }

    private int netWorth(EngineState state, EnginePlayer player) {
        int assets = owned(state, player.id).stream().mapToInt(tile -> {
            int base = tile.price == null ? 0 : tile.price;
            int houses = tile.houseCost == null ? 0 : tile.houseCost * tile.houses;
            return Boolean.TRUE.equals(tile.mortgaged) ? (int) Math.floor((base + houses) * 0.55d) : base + houses;
        }).sum();
        return player.money + assets;
    }

    private int active(EngineState state) {
        return (int) state.players.stream().filter(player -> !player.bankrupt).count();
    }

    private EnginePlayer currentPlayer(EngineState state) {
        return state.players == null || state.players.isEmpty() ? null : state.players.get(Math.max(0, Math.min(state.currentPlayerIndex, state.players.size() - 1)));
    }

    private EnginePlayer player(EngineState state, String playerId) {
        return state.players.stream().filter(item -> Objects.equals(item.id, playerId)).findFirst().orElse(null);
    }

    private EnginePlayer owner(EngineState state, EngineSpace tile) {
        return tile == null || tile.ownerId == null ? null : player(state, tile.ownerId);
    }

    private List<EngineSpace> owned(EngineState state, String playerId) {
        return state.board.stream().filter(tile -> Objects.equals(tile.ownerId, playerId)).toList();
    }

    private EngineSpace board(EngineState state, Integer index) {
        return index == null || index < 0 || index >= state.board.size() ? null : state.board.get(index);
    }

    private DiceResult rollDice(Random random) {
        DiceResult dice = new DiceResult();
        dice.a = 1 + random.nextInt(6);
        dice.b = 1 + random.nextInt(6);
        dice.total = dice.a + dice.b;
        dice.doubles = dice.a == dice.b;
        return dice;
    }

    private void logAction(EngineState state, String title, String message) {
        if (state.log == null) {
            state.log = new ArrayList<>();
        }
        LogEntry entry = new LogEntry();
        entry.title = title;
        entry.message = message;
        entry.stamp = "Luot " + state.turnNumber;
        state.log.add(0, entry);
        if (state.log.size() > MAX_LOG_ITEMS) {
            state.log = new ArrayList<>(state.log.subList(0, MAX_LOG_ITEMS));
        }
    }

    private String money(int value) {
        return "$" + NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    private List<EngineSpace> cloneBoard() {
        List<EngineSpace> board = new ArrayList<>();
        for (String raw : BOARD_DEFS) {
            String[] parts = raw.split("\\|", -1);
            EngineSpace tile = new EngineSpace();
            tile.index = Integer.parseInt(parts[0]);
            tile.key = parts[1];
            tile.type = parts[2];
            tile.name = parts[3];
            tile.group = parts[4];
            tile.text = emptyToNull(parts[5]);
            tile.price = parseInt(parts[6]);
            tile.mortgage = parseInt(parts[7]);
            tile.houseCost = parseInt(parts[8]);
            tile.amount = parseInt(parts[9]);
            tile.rent = parseRent(parts[10]);
            tile.houses = 0;
            tile.mortgaged = false;
            board.add(tile);
        }
        return board;
    }

    private List<EngineCard> createDeck(List<CardDef> defs, Random random) {
        List<EngineCard> deck = new ArrayList<>();
        for (int index = 0; index < defs.size(); index += 1) {
            CardDef def = defs.get(index);
            EngineCard card = new EngineCard();
            card.id = "card-" + index + "-" + Integer.toString(random.nextInt(1_000_000), 36);
            card.kind = def.kind;
            card.target = def.target;
            card.collectGo = def.collectGo;
            card.label = def.label;
            card.steps = def.steps;
            card.amount = def.amount;
            card.toPot = def.toPot;
            card.houseFee = def.houseFee;
            card.hotelFee = def.hotelFee;
            deck.add(card);
        }
        Collections.shuffle(deck, random);
        return deck;
    }

    private EngineState toState(Map<String, Object> raw) {
        EngineState state = MAPPER.convertValue(raw, EngineState.class);
        if (state.players == null) {
            state.players = new ArrayList<>();
        }
        if (state.board == null || state.board.isEmpty()) {
            state.board = cloneBoard();
        }
        if (state.chanceDeck == null) {
            state.chanceDeck = new ArrayList<>();
        }
        if (state.chestDeck == null) {
            state.chestDeck = new ArrayList<>();
        }
        if (state.log == null) {
            state.log = new ArrayList<>();
        }
        if (state.settings == null) {
            state.settings = new EngineSettings();
        }
        if (state.settings.passGoAmount == null) {
            state.settings.passGoAmount = MonopolyRoomService.DEFAULT_PASS_GO_AMOUNT;
        }
        return state;
    }

    private Map<String, Object> toMap(EngineState state) {
        return MAPPER.convertValue(state, MAP_TYPE);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.replace("-", "").replace("_", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private Integer parseInt(String value) {
        return value == null || value.isBlank() ? null : Integer.parseInt(value);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<Integer> parseRent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<Integer> rents = new ArrayList<>();
        for (String item : raw.split(",")) {
            rents.add(Integer.parseInt(item));
        }
        return rents;
    }

    record PlayerSeed(String playerId, String name, String token, int turnOrder) {
    }

    record ActionResult(boolean success, String error, Map<String, Object> gameState) {
        static ActionResult ok(Map<String, Object> gameState) {
            return new ActionResult(true, null, gameState);
        }

        static ActionResult error(String error) {
            return new ActionResult(false, error, null);
        }
    }

    @FunctionalInterface
    private interface Mutation {
        String run();
    }

    private record CardDef(String kind, Integer target, Boolean collectGo, String label, Integer steps, Integer amount, Boolean toPot, Integer houseFee, Integer hotelFee) {
        static CardDef move(int target, boolean collectGo, String label) {
            return new CardDef("move", target, collectGo, label, null, null, null, null, null);
        }

        static CardDef moveBack(int steps, String label) {
            return new CardDef("moveBack", null, null, label, steps, null, null, null, null);
        }

        static CardDef cash(int amount, boolean toPot, String label) {
            return new CardDef("cash", null, null, label, null, amount, toPot, null, null);
        }

        static CardDef repairs(int houseFee, int hotelFee, String label) {
            return new CardDef("repairs", null, null, label, null, null, null, houseFee, hotelFee);
        }

        static CardDef kind(String kind, String label) {
            return new CardDef(kind, null, null, label, null, null, null, null, null);
        }
    }

    public static final class EngineState { public List<EnginePlayer> players; public List<EngineSpace> board; public List<EngineCard> chanceDeck; public List<EngineCard> chestDeck; public int currentPlayerIndex; public int selectedTileIndex; public PendingPurchase pendingPurchase; public DebtState debt; public String phase; public List<LogEntry> log; public DiceResult lastDice; public LastCard lastCard; public boolean pendingExtraRoll; public SpecialRent specialRent; public int freeParkingPot; public int round; public int turnNumber; public String winnerId; public EngineSettings settings; }
    public static final class EnginePlayer { public String id; public String name; public String color; public String token; public int turnOrder; public int money; public int position; public boolean bankrupt; public boolean inJail; public int jailTurns; public int escapeCards; public int consecutiveDoubles; }
    public static final class EngineSpace { public int index; public String key; public String type; public String name; public String group; public String text; public Integer price; public Integer mortgage; public Integer houseCost; public Integer amount; public List<Integer> rent; public String ownerId; public int houses; public Boolean mortgaged; }
    public static final class EngineCard { public String id; public String kind; public Integer target; public Boolean collectGo; public String label; public Integer steps; public Integer amount; public Boolean toPot; public Integer houseFee; public Integer hotelFee; }
    public static final class PendingPurchase { public Integer tileIndex; }
    public static final class DebtState { public String playerId; public String creditorId; public int amount; public String reason; public boolean toPot; }
    public static final class LogEntry { public String title; public String message; public String stamp; }
    public static final class DiceResult { public int a; public int b; public int total; public boolean doubles; }
    public static final class LastCard { public String title; public String label; }
    public static final class SpecialRent { public Integer tileIndex; public String kind; }
    public static final class EngineSettings { public Integer passGoAmount; }
}
