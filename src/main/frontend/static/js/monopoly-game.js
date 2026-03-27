document.addEventListener("DOMContentLoaded", () => {
  const boot = window.MonopolyBoot || {};
  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === "function")
    ? window.CaroUrl.path
    : (value) => value;
  const root = document.getElementById("monopolyPage") || document.getElementById("monopolyApp");
  if (!root) {
    return;
  }

  const refs = {
    playerCount: document.getElementById("monopolyPlayerCount"),
    startingCash: document.getElementById("monopolyStartingCash"),
    playerInputs: [
      document.getElementById("monopolyPlayer1"),
      document.getElementById("monopolyPlayer2"),
      document.getElementById("monopolyPlayer3"),
      document.getElementById("monopolyPlayer4")
    ],
    playerSlots: Array.from(document.querySelectorAll("[data-player-slot]")),
    localSetupCard: document.getElementById("monopolyLocalSetupCard"),
    startBtn: document.getElementById("monopolyStartBtn"),
    restartBtn: document.getElementById("monopolyRestartBtn"),
    roomPlayerName: document.getElementById("monopolyRoomPlayerName"),
    roomName: document.getElementById("monopolyRoomName"),
    roomMaxPlayers: document.getElementById("monopolyRoomMaxPlayers"),
    roomCodeInput: document.getElementById("monopolyRoomCodeInput"),
    roomStatus: document.getElementById("monopolyRoomStatus"),
    createRoomBtn: document.getElementById("monopolyCreateRoomBtn"),
    joinRoomBtn: document.getElementById("monopolyJoinRoomBtn"),
    refreshRoomsBtn: document.getElementById("monopolyRefreshRoomsBtn"),
    copyRoomBtn: document.getElementById("monopolyCopyRoomBtn"),
    currentRoom: document.getElementById("monopolyCurrentRoom"),
    roomPlayers: document.getElementById("monopolyRoomPlayers"),
    tokenPicker: document.getElementById("monopolyTokenPicker"),
    roomStartBtn: document.getElementById("monopolyRoomStartBtn"),
    leaveRoomBtn: document.getElementById("monopolyLeaveRoomBtn"),
    openRooms: document.getElementById("monopolyOpenRooms"),
    rollBtn: document.getElementById("monopolyRollBtn"),
    buyBtn: document.getElementById("monopolyBuyBtn"),
    skipBuyBtn: document.getElementById("monopolySkipBuyBtn"),
    endTurnBtn: document.getElementById("monopolyEndTurnBtn"),
    payBailBtn: document.getElementById("monopolyPayBailBtn"),
    useCardBtn: document.getElementById("monopolyUseCardBtn"),
    bankruptcyBtn: document.getElementById("monopolyDeclareBankruptcyBtn"),
    finishBtn: document.getElementById("monopolyFinishBtn"),
    board: document.getElementById("monopolyBoard"),
    turnSummary: document.getElementById("monopolyTurnSummary"),
    instruction: document.getElementById("monopolyInstruction"),
    debtNotice: document.getElementById("monopolyDebtNotice"),
    lastCard: document.getElementById("monopolyLastCard"),
    auctionPanel: document.getElementById("monopolyAuctionPanel"),
    auctionSummary: document.getElementById("monopolyAuctionSummary"),
    auctionBidInput: document.getElementById("monopolyAuctionBidInput"),
    auctionBidBtn: document.getElementById("monopolyAuctionBidBtn"),
    auctionPassBtn: document.getElementById("monopolyAuctionPassBtn"),
    tradePanel: document.getElementById("monopolyTradePanel"),
    selectedTile: document.getElementById("monopolySelectedTile"),
    standings: document.getElementById("monopolyStandings"),
    players: document.getElementById("monopolyPlayers"),
    portfolio: document.getElementById("monopolyPortfolio"),
    log: document.getElementById("monopolyLog")
  };

  const PASS_GO_AMOUNT = 200;
  const JAIL_BAIL = 50;
  const MAX_LOG_ITEMS = 28;
  const ROOM_POLL_MS = 2600;
  const ROOM_STATE_DEBOUNCE_MS = 140;
  const ROOM_STORAGE_KEY = "monopolyRoomProfile.v2";
  const PLAYER_COLORS = ["#f97316", "#22c55e", "#38bdf8", "#f43f5e"];
  const TOKEN_CATALOG = [
    { id: "dog", label: "Cho", icon: "bi-emoji-smile" },
    { id: "car", label: "Xe hoi", icon: "bi-car-front-fill" },
    { id: "hat", label: "Mu", icon: "bi-person-badge-fill" },
    { id: "ship", label: "Tau", icon: "bi-water" },
    { id: "cat", label: "Meo", icon: "bi-emoji-heart-eyes" },
    { id: "boot", label: "Giay", icon: "bi-lightning-charge-fill" }
  ];
  const GROUP_META = {
    brown: { label: "Nau", accent: "#8b5e3c" },
    lightBlue: { label: "Xanh nhat", accent: "#38bdf8" },
    pink: { label: "Hong", accent: "#ec4899" },
    orange: { label: "Cam", accent: "#f97316" },
    red: { label: "Do", accent: "#ef4444" },
    yellow: { label: "Vang", accent: "#facc15" },
    green: { label: "La", accent: "#22c55e" },
    darkBlue: { label: "Xanh dam", accent: "#2563eb" },
    railroad: { label: "Nha ga", accent: "#64748b" },
    utility: { label: "Cong ty", accent: "#14b8a6" },
    corner: { label: "Corner", accent: "#c084fc" },
    event: { label: "Su kien", accent: "#f59e0b" },
    tax: { label: "Tax", accent: "#fb7185" }
  };

  const BOARD_SPACES = [
    { index: 0, key: "go", type: "go", name: "GO", text: "Di qua nhan 200$.", group: "corner" },
    { index: 1, key: "pho-co", type: "property", name: "Pho Co", group: "brown", price: 60, mortgage: 30, houseCost: 50, rent: [2, 10, 30, 90, 160, 250] },
    { index: 2, key: "community-1", type: "community", name: "Community Chest", text: "Rut 1 the cong dong.", group: "event" },
    { index: 3, key: "ben-nghe", type: "property", name: "Ben Nghe", group: "brown", price: 60, mortgage: 30, houseCost: 50, rent: [4, 20, 60, 180, 320, 450] },
    { index: 4, key: "income-tax", type: "tax", name: "Income Tax", amount: 120, text: "Dong 120$ vao quy free parking.", group: "tax" },
    { index: 5, key: "ga-sai-gon", type: "railroad", name: "Ga Sai Gon", group: "railroad", price: 200, mortgage: 100 },
    { index: 6, key: "ho-tay", type: "property", name: "Ho Tay", group: "lightBlue", price: 100, mortgage: 50, houseCost: 50, rent: [6, 30, 90, 270, 400, 550] },
    { index: 7, key: "chance-1", type: "chance", name: "Chance", text: "Rut 1 the co hoi.", group: "event" },
    { index: 8, key: "phu-nhuan", type: "property", name: "Phu Nhuan", group: "lightBlue", price: 100, mortgage: 50, houseCost: 50, rent: [6, 30, 90, 270, 400, 550] },
    { index: 9, key: "cau-giay", type: "property", name: "Cau Giay", group: "lightBlue", price: 120, mortgage: 60, houseCost: 50, rent: [8, 40, 100, 300, 450, 600] },
    { index: 10, key: "jail", type: "jail", name: "Jail / Visit", text: "Vao tham hoac cho ra tu.", group: "corner" },
    { index: 11, key: "hai-chau", type: "property", name: "Hai Chau", group: "pink", price: 140, mortgage: 70, houseCost: 100, rent: [10, 50, 150, 450, 625, 750] },
    { index: 12, key: "dien-luc", type: "utility", name: "Dien luc", group: "utility", price: 150, mortgage: 75 },
    { index: 13, key: "ninh-kieu", type: "property", name: "Ninh Kieu", group: "pink", price: 140, mortgage: 70, houseCost: 100, rent: [10, 50, 150, 450, 625, 750] },
    { index: 14, key: "da-kao", type: "property", name: "Da Kao", group: "pink", price: 160, mortgage: 80, houseCost: 100, rent: [12, 60, 180, 500, 700, 900] },
    { index: 15, key: "ga-ha-noi", type: "railroad", name: "Ga Ha Noi", group: "railroad", price: 200, mortgage: 100 },
    { index: 16, key: "tay-ho", type: "property", name: "Tay Ho", group: "orange", price: 180, mortgage: 90, houseCost: 100, rent: [14, 70, 200, 550, 750, 950] },
    { index: 17, key: "community-2", type: "community", name: "Community Chest", text: "Rut 1 the cong dong.", group: "event" },
    { index: 18, key: "district-1", type: "property", name: "District 1", group: "orange", price: 180, mortgage: 90, houseCost: 100, rent: [14, 70, 200, 550, 750, 950] },
    { index: 19, key: "my-khe", type: "property", name: "My Khe", group: "orange", price: 200, mortgage: 100, houseCost: 100, rent: [16, 80, 220, 600, 800, 1000] },
    { index: 20, key: "free-parking", type: "freeParking", name: "Free Parking", text: "Nhan toan bo quy jackpot.", group: "corner" },
    { index: 21, key: "vung-tau", type: "property", name: "Vung Tau", group: "red", price: 220, mortgage: 110, houseCost: 150, rent: [18, 90, 250, 700, 875, 1050] },
    { index: 22, key: "chance-2", type: "chance", name: "Chance", text: "Rut 1 the co hoi.", group: "event" },
    { index: 23, key: "thu-duc", type: "property", name: "Thu Duc", group: "red", price: 220, mortgage: 110, houseCost: 150, rent: [18, 90, 250, 700, 875, 1050] },
    { index: 24, key: "hai-phong", type: "property", name: "Hai Phong Port", group: "red", price: 240, mortgage: 120, houseCost: 150, rent: [20, 100, 300, 750, 925, 1100] },
    { index: 25, key: "ga-da-nang", type: "railroad", name: "Ga Da Nang", group: "railroad", price: 200, mortgage: 100 },
    { index: 26, key: "sala", type: "property", name: "Sala", group: "yellow", price: 260, mortgage: 130, houseCost: 150, rent: [22, 110, 330, 800, 975, 1150] },
    { index: 27, key: "tay-ho-tay", type: "property", name: "Tay Ho Tay", group: "yellow", price: 260, mortgage: 130, houseCost: 150, rent: [22, 110, 330, 800, 975, 1150] },
    { index: 28, key: "cap-nuoc", type: "utility", name: "Cap nuoc", group: "utility", price: 150, mortgage: 75 },
    { index: 29, key: "phu-my-hung", type: "property", name: "Phu My Hung", group: "yellow", price: 280, mortgage: 140, houseCost: 150, rent: [24, 120, 360, 850, 1025, 1200] },
    { index: 30, key: "go-to-jail", type: "goToJail", name: "Go To Jail", text: "Di thang vao tu.", group: "corner" },
    { index: 31, key: "cau-rong", type: "property", name: "Cau Rong", group: "green", price: 300, mortgage: 150, houseCost: 200, rent: [26, 130, 390, 900, 1100, 1275] },
    { index: 32, key: "van-mieu", type: "property", name: "Van Mieu", group: "green", price: 300, mortgage: 150, houseCost: 200, rent: [26, 130, 390, 900, 1100, 1275] },
    { index: 33, key: "community-3", type: "community", name: "Community Chest", text: "Rut 1 the cong dong.", group: "event" },
    { index: 34, key: "landmark-81", type: "property", name: "Landmark 81", group: "green", price: 320, mortgage: 160, houseCost: 200, rent: [28, 150, 450, 1000, 1200, 1400] },
    { index: 35, key: "ga-nha-trang", type: "railroad", name: "Ga Nha Trang", group: "railroad", price: 200, mortgage: 100 },
    { index: 36, key: "chance-3", type: "chance", name: "Chance", text: "Rut 1 the co hoi.", group: "event" },
    { index: 37, key: "ba-na-hills", type: "property", name: "Ba Na Hills", group: "darkBlue", price: 350, mortgage: 175, houseCost: 200, rent: [35, 175, 500, 1100, 1300, 1500] },
    { index: 38, key: "luxury-tax", type: "tax", name: "Luxury Tax", amount: 100, text: "Dong 100$ vao quy free parking.", group: "tax" },
    { index: 39, key: "west-lake-view", type: "property", name: "West Lake View", group: "darkBlue", price: 400, mortgage: 200, houseCost: 200, rent: [50, 200, 600, 1400, 1700, 2000] }
  ];

  const CHANCE_CARDS = [
    { kind: "move", target: 0, collectGo: true, label: "Tien toi GO va nhan 200$." },
    { kind: "move", target: 24, collectGo: true, label: "Tien toi Hai Phong Port." },
    { kind: "nearestRailroad", label: "Tien toi nha ga gan nhat. Neu da co chu, tra gap doi tien thue." },
    { kind: "nearestUtility", label: "Tien toi cong ty gan nhat. Neu da co chu, tra 10x tong xuc xac." },
    { kind: "moveBack", steps: 3, label: "Lui 3 o." },
    { kind: "cash", amount: 50, label: "Co tuc ngan hang: nhan 50$." },
    { kind: "cash", amount: -15, toPot: true, label: "Vi pham toc do: nop 15$." },
    { kind: "repairs", houseFee: 25, hotelFee: 100, label: "Sua chua tai san: 25$/nha, 100$/hotel." },
    { kind: "gotoJail", label: "Di thang vao tu." },
    { kind: "escape", label: "Giu the nay de ra tu mien phi." }
  ];

  const CHEST_CARDS = [
    { kind: "move", target: 0, collectGo: true, label: "Von quy phat thuong: tien toi GO." },
    { kind: "cash", amount: 200, label: "Loi he thong ngan hang: nhan 200$." },
    { kind: "cash", amount: 100, label: "Hoan tra phuc loi: nhan 100$." },
    { kind: "cash", amount: 45, label: "Ban tai san nho: nhan 45$." },
    { kind: "cash", amount: -50, toPot: true, label: "Vien phi va giay to: nop 50$." },
    { kind: "cash", amount: -100, toPot: true, label: "Sua he thong nha cua: nop 100$." },
    { kind: "cash", amount: 25, label: "Phi tu van thanh cong: nhan 25$." },
    { kind: "repairs", houseFee: 40, hotelFee: 115, label: "Bao tri nha dat: 40$/nha, 115$/hotel." },
    { kind: "gotoJail", label: "Bi trieu tap: vao tu." },
    { kind: "escape", label: "Giu the nay de ra tu mien phi." }
  ];

  let state = createEmptyState();
  const roomSession = createRoomSession();
  const tradeDraft = {
    targetPlayerId: "",
    offeredCash: 0,
    requestedCash: 0,
    offeredTileIndices: [],
    requestedTileIndices: []
  };

  bindStaticEvents();
  bindRoomEvents();
  hydrateStoredPlayerProfile();
  syncSetupFields();
  if (shouldBootLocalBoard()) {
    startNewGame(false);
  }
  renderAll();
  renderRoomPanels();
  if (hasRoomUi()) {
    loadRoomList(true);
  }
  if (roomSession.roomId) {
    if (refs.roomCodeInput) {
      refs.roomCodeInput.value = roomSession.roomId;
    }
    if (isDedicatedRoomPage()) {
      joinRoom(roomSession.roomId, true);
    } else if (!isLocalPage()) {
      navigateToRoomPage(roomSession.roomId);
      return;
    }
  }

  function createEmptyState() {
    return {
      players: [],
      board: cloneBoardSpaces(),
      chanceDeck: createDeck(CHANCE_CARDS),
      chestDeck: createDeck(CHEST_CARDS),
      currentPlayerIndex: 0,
      selectedTileIndex: 0,
      pendingPurchase: null,
      auction: null,
      tradeOffer: null,
      debt: null,
      phase: "setup",
      log: [],
      lastDice: null,
      lastCard: null,
      pendingExtraRoll: false,
      specialRent: null,
      freeParkingPot: 0,
      round: 1,
      turnNumber: 1,
      winnerId: null,
      settings: {
        passGoAmount: PASS_GO_AMOUNT
      }
    };
  }

  function cloneBoardSpaces() {
    return BOARD_SPACES.map((space) => ({
      ...space,
      ownerId: null,
      houses: 0,
      mortgaged: false
    }));
  }

  function bindStaticEvents() {
    refs.playerCount?.addEventListener("change", syncSetupFields);
    refs.startBtn?.addEventListener("click", () => startNewGame(true));
    refs.restartBtn?.addEventListener("click", () => startNewGame(true));
    refs.rollBtn?.addEventListener("click", handleRoll);
    refs.buyBtn?.addEventListener("click", handleBuyProperty);
    refs.skipBuyBtn?.addEventListener("click", handleSkipPurchase);
    refs.endTurnBtn?.addEventListener("click", handleEndTurn);
    refs.payBailBtn?.addEventListener("click", handlePayBail);
    refs.useCardBtn?.addEventListener("click", handleUseEscapeCard);
    refs.bankruptcyBtn?.addEventListener("click", handleDeclareBankruptcy);
    refs.finishBtn?.addEventListener("click", () => {
      if (isRoomAttached()) {
        void runRoomAction("finish_game");
        return;
      }
      finishGame("Van dau duoc chot bang tay.");
    });
    refs.auctionBidBtn?.addEventListener("click", () => {
      const amount = Number(refs.auctionBidInput?.value || 0);
      if (!Number.isFinite(amount) || amount <= 0) {
        setRoomStatus("Nhap muc gia hop le truoc khi dat gia.", true);
        return;
      }
      void runRoomAction("auction_bid", null, amount);
    });
    refs.auctionPassBtn?.addEventListener("click", () => {
      void runRoomAction("auction_pass");
    });
    refs.tradePanel?.addEventListener("change", (event) => {
      const target = event.target;
      if (!(target instanceof HTMLElement)) {
        return;
      }
      if (target.matches("[data-trade-target]")) {
        tradeDraft.targetPlayerId = target.value || "";
        tradeDraft.requestedTileIndices = [];
        renderTradePanel();
        return;
      }
      if (target.matches("[data-trade-offered-cash]")) {
        tradeDraft.offeredCash = Math.max(0, Number(target.value || 0));
        return;
      }
      if (target.matches("[data-trade-requested-cash]")) {
        tradeDraft.requestedCash = Math.max(0, Number(target.value || 0));
        return;
      }
      if (target.matches("[data-trade-offered-tile]")) {
        toggleDraftTile("offeredTileIndices", Number(target.getAttribute("data-trade-offered-tile")), target.checked);
        return;
      }
      if (target.matches("[data-trade-requested-tile]")) {
        toggleDraftTile("requestedTileIndices", Number(target.getAttribute("data-trade-requested-tile")), target.checked);
      }
    });
    refs.tradePanel?.addEventListener("input", (event) => {
      const target = event.target;
      if (!(target instanceof HTMLElement)) {
        return;
      }
      if (target.matches("[data-trade-offered-cash]")) {
        tradeDraft.offeredCash = Math.max(0, Number(target.value || 0));
      } else if (target.matches("[data-trade-requested-cash]")) {
        tradeDraft.requestedCash = Math.max(0, Number(target.value || 0));
      }
    });
    refs.tradePanel?.addEventListener("click", (event) => {
      const button = event.target.closest("[data-trade-action]");
      if (!button) {
        return;
      }
      const action = button.getAttribute("data-trade-action");
      if (action === "offer") {
        sendTradeOffer();
      } else if (action === "accept") {
        void runRoomAction("trade_accept");
      } else if (action === "reject") {
        void runRoomAction("trade_reject");
      }
    });

    refs.board?.addEventListener("click", (event) => {
      const button = event.target.closest("[data-space-index]");
      if (!button) {
        return;
      }
      state.selectedTileIndex = Number(button.getAttribute("data-space-index"));
      renderAll();
    });

    refs.selectedTile?.addEventListener("click", (event) => {
      const actionButton = event.target.closest("[data-monopoly-action]");
      if (!actionButton) {
        return;
      }
      const action = actionButton.getAttribute("data-monopoly-action");
      const tileIndex = Number(actionButton.getAttribute("data-space-index"));
      if (isRoomAttached()) {
        void runRoomAction(action, tileIndex);
        return;
      }
      if (action === "build") {
        buildHouse(tileIndex);
      } else if (action === "sell-house") {
        sellHouse(tileIndex);
      } else if (action === "mortgage") {
        mortgageProperty(tileIndex);
      } else if (action === "unmortgage") {
        unmortgageProperty(tileIndex);
      }
    });

    refs.portfolio?.addEventListener("click", (event) => {
      const asset = event.target.closest("[data-space-select]");
      if (!asset) {
        return;
      }
      state.selectedTileIndex = Number(asset.getAttribute("data-space-select"));
      renderAll();
    });
  }

  function bindRoomEvents() {
    refs.createRoomBtn?.addEventListener("click", createRoom);
    refs.joinRoomBtn?.addEventListener("click", () => joinRoomByInput());
    refs.refreshRoomsBtn?.addEventListener("click", () => loadRoomList(false));
    refs.copyRoomBtn?.addEventListener("click", copyRoomInvite);
    refs.roomStartBtn?.addEventListener("click", startRoomSession);
    refs.leaveRoomBtn?.addEventListener("click", leaveRoomSession);
    refs.roomCodeInput?.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        joinRoomByInput();
      }
    });

    refs.openRooms?.addEventListener("click", (event) => {
      const joinButton = event.target.closest("[data-room-join]");
      if (!joinButton) {
        return;
      }
      joinRoom(joinButton.getAttribute("data-room-join"), false);
    });

    refs.tokenPicker?.addEventListener("click", (event) => {
      const button = event.target.closest("[data-token-id]");
      if (!button) {
        return;
      }
      selectToken(button.getAttribute("data-token-id"));
    });
    window.addEventListener("pagehide", notifyRoomDisconnect);
  }

  function syncSetupFields() {
    if (!refs.playerSlots.length) {
      return;
    }
    const activeCount = Number(refs.playerCount?.value || 4);
    refs.playerSlots.forEach((slot) => {
      const index = Number(slot.getAttribute("data-player-slot"));
      slot.classList.toggle("is-hidden", index > activeCount);
    });
  }

  function startNewGame(confirmIfRunning) {
    if (isRoomAttached()) {
      setRoomStatus("Dang o che do phong. Roi phong neu muon mo van local rieng.", true);
      return;
    }
    if (confirmIfRunning && state.players.length > 0 && state.phase !== "setup") {
      const shouldReset = window.confirm("Bat dau van moi? Tien trinh hien tai se mat.");
      if (!shouldReset) {
        return;
      }
    }

    startConfiguredGame(readSetupConfig());
  }

  function readSetupConfig() {
    const playerCount = Math.max(2, Math.min(4, Number(refs.playerCount?.value || 4)));
    const startingCash = Math.max(800, Number(refs.startingCash?.value || 1500));
    const players = refs.playerInputs
      .slice(0, playerCount)
      .map((input, index) => sanitizeName(input?.value, "Ty phu " + (index + 1)));
    return {
      playerCount,
      startingCash,
      passGoAmount: PASS_GO_AMOUNT,
      players: players.map((name) => ({ name }))
    };
  }

  function startConfiguredGame(config) {
    state = createEmptyState();
    state.settings.passGoAmount = Math.max(50, Number(config.passGoAmount || PASS_GO_AMOUNT));
    state.players = config.players.map((player, index) => createPlayerState(player.name, index, config.startingCash, player));
    state.phase = state.players[0]?.inJail ? "jail" : "await_roll";
    state.log = [];
    state.round = 1;
    state.turnNumber = 1;
    state.selectedTileIndex = 0;
    logAction("Van moi", "Ban choi da san sang. " + state.players[0].name + " di truoc.");
    renderAll();
  }

  function createPlayerState(name, index, startingCash, options) {
    const extra = options || {};
    return {
      id: extra.id || extra.playerId || ("p" + (index + 1)),
      name,
      color: extra.color || PLAYER_COLORS[index],
      token: extra.token || null,
      turnOrder: Number.isFinite(extra.turnOrder) ? Number(extra.turnOrder) : index,
      money: startingCash,
      position: 0,
      bankrupt: false,
      inJail: false,
      jailTurns: 0,
      escapeCards: 0,
      consecutiveDoubles: 0,
      isDisconnected: Boolean(extra.isDisconnected)
    };
  }

  function sanitizeName(value, fallback) {
    const normalized = String(value || "").replace(/\s+/g, " ").trim();
    return (normalized || fallback).slice(0, 18);
  }

  function shuffle(items) {
    const copy = items.slice();
    for (let index = copy.length - 1; index > 0; index -= 1) {
      const randomIndex = Math.floor(Math.random() * (index + 1));
      const temp = copy[index];
      copy[index] = copy[randomIndex];
      copy[randomIndex] = temp;
    }
    return copy;
  }

  function createDeck(cards) {
    return shuffle(cards.map((card, index) => ({ ...card, id: "card-" + index + "-" + Math.random().toString(36).slice(2, 8) })));
  }

  function getCurrentPlayer() {
    return state.players[state.currentPlayerIndex] || null;
  }

  function getPlayerById(playerId) {
    return state.players.find((player) => player.id === playerId) || null;
  }

  function getBoardSpace(index) {
    return state.board[index];
  }

  function ownedSpaces(playerId) {
    return state.board.filter((space) => space.ownerId === playerId);
  }

  function activePlayers() {
    return state.players.filter((player) => !player.bankrupt);
  }

  function getOwner(space) {
    return space && space.ownerId ? getPlayerById(space.ownerId) : null;
  }

  function formatMoney(value) {
    return "$" + Number(value || 0).toLocaleString("en-US");
  }

  function escapeHtml(value) {
    return String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function logAction(title, message) {
    state.log.unshift({
      title,
      message,
      stamp: "Luot " + state.turnNumber
    });
    if (state.log.length > MAX_LOG_ITEMS) {
      state.log.length = MAX_LOG_ITEMS;
    }
  }

  function renderAll() {
    renderBoard();
    renderTurnSummary();
    renderSelectedTile();
    renderStandings();
    renderPlayers();
    renderPortfolio();
    renderLog();
    renderControls();
    renderRoomPanels();
    renderSetupAvailability();
    maybeQueueRoomStatePublish();
  }

  function renderBoard() {
    if (!refs.board) {
      return;
    }
    const tilesHtml = state.board.map((space) => renderBoardSpace(space)).join("");
    refs.board.innerHTML = tilesHtml + renderBoardCenter();
  }

  function renderBoardSpace(space) {
    const position = getBoardPosition(space.index);
    const groupMeta = GROUP_META[space.group] || GROUP_META.corner;
    const owner = getOwner(space);
    const ownerStyle = owner ? "--owner-color:" + owner.color + ";" : "";
    const frameClass = resolveSpaceFrameClass(space.index);
    const selectedClass = state.selectedTileIndex === space.index ? " is-selected" : "";
    const ownedClass = owner ? " is-owned" : "";
    const mortgagedClass = space.mortgaged ? " is-mortgaged" : "";
    const houses = renderHouseDots(space);
    const tokens = renderTokenDots(space.index);
    const meta = renderSpaceMeta(space, owner);
    const price = renderSpacePrice(space, owner);
    return `
      <button
        type="button"
        class="monopoly-space ${frameClass} type-${space.type}${selectedClass}${ownedClass}${mortgagedClass}"
        data-space-index="${space.index}"
        style="grid-row:${position.row};grid-column:${position.col};--space-accent:${groupMeta.accent};${ownerStyle}">
        <span class="monopoly-space__band"></span>
        <span class="monopoly-space__badge">${escapeHtml(resolveSpaceBadge(space))}</span>
        <span class="monopoly-space__type">${escapeHtml(groupMeta.label)}</span>
        <strong class="monopoly-space__name">${escapeHtml(space.name)}</strong>
        <span class="monopoly-space__price">${escapeHtml(price)}</span>
        <span class="monopoly-space__meta">${meta}</span>
        <div class="monopoly-space__bottom">
          <div class="monopoly-space__houses">${houses}</div>
          <div class="monopoly-space__tokens">${tokens}</div>
        </div>
      </button>
    `;
  }

  function renderBoardCenter() {
    const currentPlayer = getCurrentPlayer();
    const dieA = state.lastDice ? state.lastDice.a : "-";
    const dieB = state.lastDice ? state.lastDice.b : "-";
    return `
      <div class="monopoly-board-center">
        <div class="monopoly-board-center__tickets" aria-hidden="true">
          <div class="monopoly-board-ticket monopoly-board-ticket--chance">Chance</div>
          <div class="monopoly-board-ticket monopoly-board-ticket--chest">Community</div>
        </div>
        <div class="monopoly-board-center__hero">
          <span class="monopoly-board-center__eyebrow">${escapeHtml(resolveBoardEyebrowLabel())}</span>
          <div class="monopoly-board-center__banner">
            <span>MONOPOLY</span>
            <small>REAL ESTATE EDITION</small>
          </div>
          <h3 class="monopoly-board-center__title">${escapeHtml(currentPlayer ? "Luot cua " + currentPlayer.name : "Ban choi Monopoly")}</h3>
          <p class="monopoly-board-center__subtitle">${escapeHtml(resolveInstructionText())}</p>
        </div>
        <div class="monopoly-dice-row">
          <div class="monopoly-dice">
            <div class="monopoly-die">${dieA}</div>
            <div class="monopoly-die">${dieB}</div>
          </div>
          <div class="monopoly-turn-badge">${escapeHtml(state.phase)}</div>
        </div>
        <div class="monopoly-center-stats">
          <div class="monopoly-center-stat"><span>Round</span><strong>${state.round}</strong></div>
          <div class="monopoly-center-stat"><span>Turn</span><strong>${state.turnNumber}</strong></div>
          <div class="monopoly-center-stat"><span>Free parking</span><strong>${formatMoney(state.freeParkingPot)}</strong></div>
          <div class="monopoly-center-stat"><span>Con nguoi choi</span><strong>${activePlayers().length}</strong></div>
        </div>
      </div>
    `;
  }

  function renderTurnSummary() {
    if (!refs.turnSummary || !refs.instruction) {
      return;
    }
    const currentPlayer = getCurrentPlayer();
    if (!currentPlayer) {
      refs.turnSummary.innerHTML = "";
      refs.instruction.textContent = "";
      return;
    }

    refs.turnSummary.innerHTML = `
      <div class="monopoly-turn-summary__lead">
        <div>
          <strong>${escapeHtml(currentPlayer.name)}</strong>
          <small>${escapeHtml(
            currentPlayer.bankrupt
              ? "Da pha san"
              : currentPlayer.isDisconnected
                ? "Tam mat ket noi - dang giu cho trong room"
                : "Dang dung o " + getBoardSpace(currentPlayer.position).name
          )}</small>
        </div>
        <span class="monopoly-turn-badge">${formatMoney(currentPlayer.money)}</span>
      </div>
    `;
    refs.instruction.textContent = resolveInstructionText();

    if (state.debt) {
      const debtor = getPlayerById(state.debt.playerId);
      const creditor = state.debt.creditorId ? getPlayerById(state.debt.creditorId) : null;
      refs.debtNotice.hidden = false;
      refs.debtNotice.textContent = debtor.name + " dang no " + formatMoney(state.debt.amount) + " cho " + (creditor ? creditor.name : "ngan hang") + ". Ban nha/the chap de can bang hoac tuyen bo pha san.";
    } else {
      refs.debtNotice.hidden = true;
      refs.debtNotice.textContent = "";
    }

    if (state.lastCard) {
      refs.lastCard.hidden = false;
      refs.lastCard.innerHTML = "<strong>" + escapeHtml(state.lastCard.title) + "</strong><span>" + escapeHtml(state.lastCard.label) + "</span>";
    } else {
      refs.lastCard.hidden = true;
      refs.lastCard.innerHTML = "";
    }

    renderAuctionPanel();
    renderTradePanel();
  }

  function renderAuctionPanel() {
    if (!refs.auctionPanel || !refs.auctionSummary || !refs.auctionBidInput || !refs.auctionBidBtn || !refs.auctionPassBtn) {
      return;
    }
    const auction = state.auction;
    if (!auction || state.phase !== "auction") {
      refs.auctionPanel.hidden = true;
      refs.auctionSummary.innerHTML = "";
      refs.auctionBidInput.value = "";
      return;
    }
    const tile = getBoardSpace(auction.tileIndex);
    const leader = auction.leaderId ? getPlayerById(auction.leaderId) : null;
    const activeBidder = auction.activePlayerId ? getPlayerById(auction.activePlayerId) : null;
    refs.auctionPanel.hidden = false;
    refs.auctionSummary.innerHTML = `
      <strong>Dau gia: ${escapeHtml(tile ? tile.name : "Tai san")}</strong>
      <span>Gia hien tai: ${formatMoney(auction.currentBid || 0)}</span>
      <span>Dang dan: ${escapeHtml(leader ? leader.name : "Chua co")}</span>
      <span>Luot hien tai: ${escapeHtml(activeBidder ? activeBidder.name : "Dang cap nhat")}</span>
    `;
    const isAuctionTurn = canUseTurnControls();
    const minimumBid = Math.max((auction.currentBid || 0) + (auction.minIncrement || 10), 1);
    if (!refs.auctionBidInput.value || Number(refs.auctionBidInput.value) <= auction.currentBid) {
      refs.auctionBidInput.value = String(minimumBid);
    }
    refs.auctionBidInput.min = String(minimumBid);
    refs.auctionBidInput.disabled = !isAuctionTurn;
    refs.auctionBidBtn.disabled = !isAuctionTurn;
    refs.auctionPassBtn.disabled = !isAuctionTurn;
  }

  function renderTradePanel() {
    if (!refs.tradePanel) {
      return;
    }
    if (!isRoomAttached() || !roomSession.room || roomSession.room.status !== "PLAYING") {
      refs.tradePanel.hidden = true;
      refs.tradePanel.innerHTML = "";
      return;
    }

    const tradeOffer = state.tradeOffer;
    if (tradeOffer && state.phase === "trade") {
      refs.tradePanel.hidden = false;
      refs.tradePanel.innerHTML = renderPendingTradeOffer(tradeOffer);
      return;
    }

    if (!canCreateTradeOffer()) {
      refs.tradePanel.hidden = true;
      refs.tradePanel.innerHTML = "";
      return;
    }

    const currentPlayer = getCurrentPlayer();
    const targetOptions = state.players.filter((player) => player.id !== currentPlayer.id && !player.bankrupt);
    if (!targetOptions.length) {
      refs.tradePanel.hidden = true;
      refs.tradePanel.innerHTML = "";
      return;
    }
    if (!tradeDraft.targetPlayerId || !targetOptions.some((player) => player.id === tradeDraft.targetPlayerId)) {
      tradeDraft.targetPlayerId = targetOptions[0].id;
      tradeDraft.requestedTileIndices = [];
    }
    const targetPlayer = getPlayerById(tradeDraft.targetPlayerId);
    const offeredAssets = tradableAssets(currentPlayer.id);
    const requestedAssets = targetPlayer ? tradableAssets(targetPlayer.id) : [];

    refs.tradePanel.hidden = false;
    refs.tradePanel.innerHTML = `
      <h3>Mo de nghi trade</h3>
      <div class="monopoly-trade-grid">
        <label class="monopoly-field">
          <span>Doi tac</span>
          <select class="form-select" data-trade-target>
            ${targetOptions.map((player) => `
              <option value="${player.id}" ${player.id === tradeDraft.targetPlayerId ? "selected" : ""}>${escapeHtml(player.name)}</option>
            `).join("")}
          </select>
        </label>
        <div class="monopoly-trade-cash">
          <label class="monopoly-field">
            <span>Ban dua them</span>
            <input type="number" min="0" step="10" class="form-control" data-trade-offered-cash value="${tradeDraft.offeredCash || 0}">
          </label>
          <label class="monopoly-field">
            <span>Ban muon nhan them</span>
            <input type="number" min="0" step="10" class="form-control" data-trade-requested-cash value="${tradeDraft.requestedCash || 0}">
          </label>
        </div>
        <div class="monopoly-trade-assets">
          <div class="monopoly-trade-asset-group">
            <strong>Tai san cua ban</strong>
            <div class="monopoly-trade-checks">
              ${renderTradeAssetChecks(offeredAssets, "offered", tradeDraft.offeredTileIndices)}
            </div>
          </div>
          <div class="monopoly-trade-asset-group">
            <strong>Tai san cua ${escapeHtml(targetPlayer ? targetPlayer.name : "doi tac")}</strong>
            <div class="monopoly-trade-checks">
              ${renderTradeAssetChecks(requestedAssets, "requested", tradeDraft.requestedTileIndices)}
            </div>
          </div>
        </div>
        <div class="monopoly-trade-actions">
          <button type="button" class="hub-portal-inline-btn primary" data-trade-action="offer">Gui de nghi</button>
        </div>
      </div>
    `;
  }

  function renderPendingTradeOffer(tradeOffer) {
    const fromPlayer = getPlayerById(tradeOffer.fromPlayerId);
    const toPlayer = getPlayerById(tradeOffer.toPlayerId);
    const offeredAssets = (tradeOffer.offeredTileIndices || []).map((index) => getBoardSpace(index)).filter(Boolean);
    const requestedAssets = (tradeOffer.requestedTileIndices || []).map((index) => getBoardSpace(index)).filter(Boolean);
    const isTarget = roomSession.playerId === tradeOffer.toPlayerId;
    const isProposer = roomSession.playerId === tradeOffer.fromPlayerId;
    return `
      <h3>De nghi trade dang mo</h3>
      <div class="monopoly-trade-summary">
        <strong>${escapeHtml(fromPlayer ? fromPlayer.name : "Nguoi choi")} -> ${escapeHtml(toPlayer ? toPlayer.name : "Nguoi choi")}</strong>
        <span>Ban gui: ${formatTradeSummary(tradeOffer.offeredCash, offeredAssets)}</span>
        <span>Ban nhan: ${formatTradeSummary(tradeOffer.requestedCash, requestedAssets)}</span>
      </div>
      <div class="monopoly-trade-actions">
        ${isTarget ? '<button type="button" class="hub-portal-inline-btn primary" data-trade-action="accept">Chap nhan</button>' : ""}
        ${(isTarget || isProposer) ? '<button type="button" class="hub-portal-inline-btn" data-trade-action="reject">' + (isProposer ? "Huy de nghi" : "Tu choi") + '</button>' : ""}
      </div>
    `;
  }

  function renderTradeAssetChecks(assets, side, selected) {
    if (!assets.length) {
      return '<div class="monopoly-empty-state">Khong co tai san hop le cho trade.</div>';
    }
    return assets.map((tile) => `
      <label class="monopoly-trade-check">
        <input
          type="checkbox"
          ${selected.includes(tile.index) ? "checked" : ""}
          ${side === "offered" ? 'data-trade-offered-tile="' + tile.index + '"' : 'data-trade-requested-tile="' + tile.index + '"'}>
        <span>
          <strong>${escapeHtml(tile.name)}</strong>
          <small>${escapeHtml((GROUP_META[tile.group] || GROUP_META.corner).label)} | ${formatMoney(calculateTileValue(tile))}</small>
        </span>
      </label>
    `).join("");
  }

  function formatTradeSummary(cash, assets) {
    const parts = [];
    if (Number(cash || 0) > 0) {
      parts.push(formatMoney(cash));
    }
    if (assets.length) {
      parts.push(assets.map((tile) => tile.name).join(", "));
    }
    return parts.length ? parts.join(" + ") : "Khong co gi";
  }

  function renderSelectedTile() {
    if (!refs.selectedTile) {
      return;
    }
    const tile = getBoardSpace(state.selectedTileIndex);
    if (!tile) {
      refs.selectedTile.innerHTML = '<div class="monopoly-empty-state">Chon 1 o tren ban co de xem thong tin.</div>';
      return;
    }

    const groupMeta = GROUP_META[tile.group] || GROUP_META.corner;
    const owner = getOwner(tile);
    const tileHead = `
      <div class="monopoly-tile-panel__head" style="--tile-accent:${groupMeta.accent}">
        <small>${escapeHtml(groupMeta.label)}</small>
        <h3>${escapeHtml(tile.name)}</h3>
        <p>${escapeHtml(describeTile(tile, owner))}</p>
      </div>
    `;

    const actions = renderSelectedTileActions(tile);
    const meta = `
      <div class="monopoly-tile-meta">
        ${renderTileMetaRow("Chu so huu", owner ? owner.name : "Ngan hang")}
        ${renderTileMetaRow("Gia tri", tile.price ? formatMoney(tile.price) : "-")}
        ${renderTileMetaRow("The chap", tile.mortgage ? formatMoney(tile.mortgage) : "-")}
        ${renderTileMetaRow("Nha / hotel", tile.houses ? String(tile.houses) : "0")}
        ${renderTileMetaRow("Tien thue", renderTileRent(tile))}
      </div>
    `;

    refs.selectedTile.innerHTML = '<div class="monopoly-tile-panel">' + tileHead + meta + actions + "</div>";
  }

  function renderStandings() {
    if (!refs.standings) {
      return;
    }
    const ranked = state.players
      .map((player) => ({ player, value: calculateNetWorth(player) }))
      .sort((left, right) => right.value - left.value);
    const maxValue = ranked[0] ? ranked[0].value : 1;
    refs.standings.innerHTML = ranked.map(({ player, value }, index) => `
      <div class="monopoly-standing-item">
        <div class="monopoly-standing-meta">
          <strong>${index + 1}. ${escapeHtml(player.name)}</strong>
          <small>${player.bankrupt ? "Pha san" : "Tien mat " + formatMoney(player.money)} | Gia tri rong ${formatMoney(value)}</small>
        </div>
        <div class="monopoly-standing-track">
          <div class="monopoly-standing-fill" style="--standing-width:${Math.max(8, Math.round((value / maxValue) * 100))}%;--standing-color:${player.color};"></div>
        </div>
      </div>
    `).join("");
  }

  function renderPlayers() {
    if (!refs.players) {
      return;
    }
    refs.players.innerHTML = state.players.map((player) => {
      const owned = ownedSpaces(player.id);
      const badges = [];
      if (state.players[state.currentPlayerIndex]?.id === player.id && !player.bankrupt) {
        badges.push('<span class="monopoly-player-badge">Dang luot</span>');
      }
      if (player.token) {
        badges.push('<span class="monopoly-player-badge">' + escapeHtml(resolveTokenLabel(player.token)) + "</span>");
      }
      if (isRoomAttached() && isRoomHostPlayer(player.id)) {
        badges.push('<span class="monopoly-player-badge">Host</span>');
      }
      if (player.isDisconnected) {
        badges.push('<span class="monopoly-player-badge">Mat ket noi</span>');
      }
      if (player.inJail) {
        badges.push('<span class="monopoly-player-badge">Trong tu</span>');
      }
      if (player.escapeCards > 0) {
        badges.push('<span class="monopoly-player-badge">The ra tu x' + player.escapeCards + "</span>");
      }
      if (player.bankrupt) {
        badges.push('<span class="monopoly-player-badge">Pha san</span>');
      }
      return `
        <article class="monopoly-player-card${state.players[state.currentPlayerIndex]?.id === player.id ? " is-current" : ""}${player.bankrupt ? " is-bankrupt" : ""}">
          <div class="monopoly-player-head">
            <div class="monopoly-player-name">
              <span class="monopoly-player-color" style="--player-color:${player.color}"></span>
              <strong>${escapeHtml(player.name)}</strong>
            </div>
            <div class="monopoly-player-badges">${badges.join("")}</div>
          </div>
          <div class="monopoly-player-stats">
            <div class="monopoly-player-stat"><span>Tien mat</span><strong>${formatMoney(player.money)}</strong></div>
            <div class="monopoly-player-stat"><span>Gia tri rong</span><strong>${formatMoney(calculateNetWorth(player))}</strong></div>
            <div class="monopoly-player-stat"><span>Tai san</span><strong>${owned.length}</strong></div>
            <div class="monopoly-player-stat"><span>Vi tri</span><strong>${escapeHtml(getBoardSpace(player.position).name)}</strong></div>
          </div>
        </article>
      `;
    }).join("");
  }

  function renderPortfolio() {
    if (!refs.portfolio) {
      return;
    }
    const currentPlayer = getCurrentPlayer();
    if (!currentPlayer || currentPlayer.bankrupt) {
      refs.portfolio.innerHTML = '<div class="monopoly-empty-state">Khong co danh muc cho nguoi choi hien tai.</div>';
      return;
    }
    const assets = ownedSpaces(currentPlayer.id);
    if (assets.length === 0) {
      refs.portfolio.innerHTML = '<div class="monopoly-empty-state">' + escapeHtml(currentPlayer.name) + " chua so huu tai san nao.</div>";
      return;
    }
    refs.portfolio.innerHTML = '<div class="monopoly-asset-list">' + assets.map((tile) => `
      <div class="monopoly-asset-item${state.selectedTileIndex === tile.index ? " is-selected" : ""}" data-space-select="${tile.index}" tabindex="0">
        <div class="monopoly-asset-meta">
          <strong>${escapeHtml(tile.name)}</strong>
          <small>${escapeHtml((GROUP_META[tile.group] || GROUP_META.corner).label)} | ${tile.mortgaged ? "Da the chap" : "Hoat dong"} | ${tile.houses ? tile.houses + " nha/hotel" : "Dat trong"}</small>
        </div>
        <span>${formatMoney(calculateTileValue(tile))}</span>
      </div>
    `).join("") + "</div>";
  }

  function renderLog() {
    if (!refs.log) {
      return;
    }
    if (state.log.length === 0) {
      refs.log.innerHTML = '<div class="monopoly-empty-state">Nhat ky se hien o day sau khi van dau bat dau.</div>';
      return;
    }
    refs.log.innerHTML = '<div class="monopoly-log-list">' + state.log.map((entry) => `
      <div class="monopoly-log-item">
        <strong>${escapeHtml(entry.title)} - ${escapeHtml(entry.stamp)}</strong>
        <small>${escapeHtml(entry.message)}</small>
      </div>
    `).join("") + "</div>";
  }

  function renderControls() {
    if (!refs.rollBtn || !refs.buyBtn || !refs.skipBuyBtn || !refs.endTurnBtn || !refs.payBailBtn || !refs.useCardBtn || !refs.bankruptcyBtn || !refs.finishBtn) {
      return;
    }
    const currentPlayer = getCurrentPlayer();
    const isDebtTurn = state.debt && currentPlayer && state.debt.playerId === currentPlayer.id;
    const turnLocked = !canUseTurnControls();
    refs.rollBtn.disabled = turnLocked || !currentPlayer || currentPlayer.bankrupt || !["await_roll", "jail"].includes(state.phase);
    refs.buyBtn.disabled = turnLocked || !state.pendingPurchase || !currentPlayer || currentPlayer.money < getBoardSpace(state.pendingPurchase?.tileIndex || 0).price;
    refs.skipBuyBtn.disabled = turnLocked || !state.pendingPurchase;
    refs.endTurnBtn.disabled = turnLocked || !currentPlayer || currentPlayer.bankrupt || state.phase !== "await_end_turn";
    refs.payBailBtn.disabled = turnLocked || !currentPlayer || !currentPlayer.inJail || state.phase !== "jail" || currentPlayer.money < JAIL_BAIL;
    refs.useCardBtn.disabled = turnLocked || !currentPlayer || !currentPlayer.inJail || state.phase !== "jail" || currentPlayer.escapeCards <= 0;
    refs.bankruptcyBtn.disabled = turnLocked || !isDebtTurn;
    refs.finishBtn.disabled = turnLocked || activePlayers().length <= 1 || state.phase === "ended" || state.phase === "auction";
  }

  function getBoardPosition(index) {
    if (index === 0) {
      return { row: 11, col: 11 };
    }
    if (index > 0 && index < 10) {
      return { row: 11, col: 11 - index };
    }
    if (index === 10) {
      return { row: 11, col: 1 };
    }
    if (index > 10 && index < 20) {
      return { row: 11 - (index - 10), col: 1 };
    }
    if (index === 20) {
      return { row: 1, col: 1 };
    }
    if (index > 20 && index < 30) {
      return { row: 1, col: index - 19 };
    }
    if (index === 30) {
      return { row: 1, col: 11 };
    }
    return { row: index - 29, col: 11 };
  }

  function resolveSpaceFrameClass(index) {
    if (index === 0 || index === 10 || index === 20 || index === 30) {
      return "is-corner";
    }
    if (index > 0 && index < 10) {
      return "edge-bottom";
    }
    if (index > 10 && index < 20) {
      return "edge-left";
    }
    if (index > 20 && index < 30) {
      return "edge-top";
    }
    return "edge-right";
  }

  function renderHouseDots(space) {
    if (!space.houses) {
      return "";
    }
    const dots = [];
    const normalHouseCount = Math.min(space.houses, 4);
    for (let index = 0; index < normalHouseCount; index += 1) {
      dots.push('<span class="monopoly-house-dot"></span>');
    }
    if (space.houses >= 5) {
      dots.push('<span class="monopoly-house-dot is-hotel"></span>');
    }
    return dots.join("");
  }

  function renderTokenDots(tileIndex) {
    return state.players
      .filter((player) => !player.bankrupt && player.position === tileIndex)
      .map((player) => '<span class="monopoly-token-dot" style="--token-color:' + player.color + '"></span>')
      .join("");
  }

  function renderSpaceMeta(space, owner) {
    if (space.type === "property" || space.type === "railroad" || space.type === "utility") {
      const priceText = space.price ? formatMoney(space.price) : "-";
      if (owner) {
        return escapeHtml(owner.name) + (space.mortgaged ? " · the chap" : "");
      }
      return priceText;
    }
    if (space.type === "tax") {
      return formatMoney(space.amount);
    }
    return escapeHtml(space.text || "");
  }

  function renderSpacePrice(space, owner) {
    const passGoAmount = Number(state.settings?.passGoAmount || PASS_GO_AMOUNT);
    if (space.type === "property" || space.type === "railroad" || space.type === "utility") {
      return owner ? "Thue " + renderTileRent(space) : "Gia " + formatMoney(space.price || 0);
    }
    if (space.type === "tax") {
      return "Phi " + formatMoney(space.amount || 0);
    }
    if (space.type === "chance" || space.type === "community") {
      return "Rut the";
    }
    if (space.type === "go") {
      return "Thuong " + formatMoney(passGoAmount);
    }
    if (space.type === "freeParking") {
      return "Jackpot";
    }
    if (space.type === "jail") {
      return "Visit / Jail";
    }
    if (space.type === "goToJail") {
      return "Di den o 10";
    }
    return "Su kien";
  }

  function resolveSpaceBadge(space) {
    switch (space.type) {
      case "go":
        return "GO";
      case "property":
        return "DEED";
      case "railroad":
        return "RAIL";
      case "utility":
        return "UTIL";
      case "tax":
        return "TAX";
      case "chance":
        return "?";
      case "community":
        return "CHEST";
      case "jail":
        return "JAIL";
      case "freeParking":
        return "FREE";
      case "goToJail":
        return "LOCK";
      default:
        return "CARD";
    }
  }

  function renderSpaceMeta(space, owner) {
    if (space.type === "property" || space.type === "railroad" || space.type === "utility") {
      if (owner) {
        return "Chu " + escapeHtml(owner.name) + (space.mortgaged ? " · the chap" : "");
      }
      return "Ngan hang";
    }
    if (space.type === "tax") {
      return "Nop vao quy chung";
    }
    if (space.type === "chance") {
      return "Rut 1 the co hoi";
    }
    if (space.type === "community") {
      return "Rut 1 the cong dong";
    }
    if (space.type === "goToJail") {
      return "Khong linh thuong GO";
    }
    if (space.type === "freeParking") {
      return "Thuong la nghi chan";
    }
    if (space.type === "go") {
      return "Nhan thuong khi di qua";
    }
    return escapeHtml(space.text || "");
  }

  function describeTile(tile, owner) {
    if (tile.type === "property") {
      if (!owner) {
        return "Lo dat chua co chu. Gia mua " + formatMoney(tile.price) + " va chi phi nha " + formatMoney(tile.houseCost) + ".";
      }
      return "Dang thuoc ve " + owner.name + ". " + (tile.mortgaged ? "Tai san dang duoc the chap." : "Tien thue hien tai " + renderTileRent(tile) + ".");
    }
    if (tile.type === "railroad") {
      return owner ? "Nha ga cua " + owner.name + ", tien thue phu thuoc so ga dang so huu." : "Nha ga co gia " + formatMoney(tile.price) + ".";
    }
    if (tile.type === "utility") {
      return owner ? "Cong ty cua " + owner.name + ", tien thue dua tren tong xuc xac." : "Cong ty co gia " + formatMoney(tile.price) + ".";
    }
    if (tile.type === "tax") {
      return "Bat buoc nop " + formatMoney(tile.amount) + " vao quy free parking.";
    }
    return tile.text || "O dac biet tren ban co.";
  }

  function renderTileMetaRow(label, value) {
    return `
      <div class="monopoly-tile-meta-item">
        <span>${escapeHtml(label)}</span>
        <strong>${escapeHtml(value)}</strong>
      </div>
    `;
  }

  function renderTileRent(tile) {
    if (tile.type === "property") {
      if (tile.houses >= 5) {
        return formatMoney(tile.rent[5]);
      }
      if (tile.houses > 0) {
        return formatMoney(tile.rent[tile.houses]);
      }
      return formatMoney(tile.rent[0]);
    }
    if (tile.type === "railroad") {
      return formatMoney(25) + " -> " + formatMoney(200);
    }
    if (tile.type === "utility") {
      return "4x / 10x tong xuc xac";
    }
    return "-";
  }

  function renderSelectedTileActions(tile) {
    const currentPlayer = getCurrentPlayer();
    if (state.phase === "auction" || state.phase === "trade") {
      return "";
    }
    if (!currentPlayer || currentPlayer.bankrupt || state.phase === "ended" || !canUseTurnControls()) {
      return "";
    }
    const buttons = [];
    if (tile.ownerId === currentPlayer.id) {
      if (canBuildHouse(tile.index)) {
        buttons.push(renderActionButton("build", tile.index, "Xay nha (" + formatMoney(tile.houseCost) + ")"));
      }
      if (canSellHouse(tile.index)) {
        buttons.push(renderActionButton("sell-house", tile.index, "Ban nha (+ " + formatMoney(Math.floor(tile.houseCost / 2)) + ")"));
      }
      if (canMortgage(tile.index)) {
        buttons.push(renderActionButton("mortgage", tile.index, "The chap (+ " + formatMoney(tile.mortgage) + ")"));
      }
      if (canUnmortgage(tile.index)) {
        buttons.push(renderActionButton("unmortgage", tile.index, "Giai chap (- " + formatMoney(getUnmortgageCost(tile)) + ")"));
      }
    }
    if (!buttons.length) {
      return "";
    }
    return '<div class="monopoly-tile-actions">' + buttons.join("") + "</div>";
  }

  function renderActionButton(action, tileIndex, label) {
    return '<button type="button" class="hub-portal-inline-btn" data-monopoly-action="' + action + '" data-space-index="' + tileIndex + '">' + escapeHtml(label) + "</button>";
  }

  function resolveInstructionText() {
    const currentPlayer = getCurrentPlayer();
    if (isRoomAttached() && roomSession.room && roomSession.room.status === "WAITING") {
      return isCurrentBrowserHost() ? "Phong da san sang. Chon token cho du nguoi roi bam bat dau." : "Dang cho host bat dau van Monopoly.";
    }
    if (!currentPlayer) {
      return "Chon setup va bat dau van moi.";
    }
    if (currentPlayer.isDisconnected) {
      return currentPlayer.name + " dang tam mat ket noi. Cho nguoi choi nay vao lai room de tiep tuc luot.";
    }
    if (isRoomAttached() && !canUseTurnControls() && roomSession.room && roomSession.room.status === "PLAYING") {
      return "Dang cho " + currentPlayer.name + " thao tac va dong bo luot.";
    }
    if (state.phase === "ended") {
      const winner = state.winnerId ? getPlayerById(state.winnerId) : null;
      return winner ? winner.name + " dan dau va van dau da ket thuc." : "Van dau da ket thuc.";
    }
    if (state.debt && state.debt.playerId === currentPlayer.id) {
      return currentPlayer.name + " dang can xoay von de tra no.";
    }
    if (state.auction && state.phase === "auction") {
      const auctionTile = getBoardSpace(state.auction.tileIndex);
      const activeBidder = state.auction.activePlayerId ? getPlayerById(state.auction.activePlayerId) : null;
      return "Dang dau gia " + (auctionTile ? auctionTile.name : "tai san") + ". " + (activeBidder ? activeBidder.name : "Nguoi choi hien tai") + " dang duoc ra gia tiep theo.";
    }
    if (state.tradeOffer && state.phase === "trade") {
      const target = state.tradeOffer.toPlayerId ? getPlayerById(state.tradeOffer.toPlayerId) : null;
      return "Dang mo trade voi " + (target ? target.name : "nguoi choi khac") + ". Cho chap nhan hoac tu choi de tiep tuc luot.";
    }
    if (state.pendingPurchase) {
      return currentPlayer.name + " dang co quyen mua " + getBoardSpace(state.pendingPurchase.tileIndex).name + ".";
    }
    if (currentPlayer.inJail && state.phase === "jail") {
      return currentPlayer.name + " dang trong tu. Co the nop 50$, dung the hoac tiep tuc tung xuc xac.";
    }
    if (state.pendingExtraRoll && state.phase === "await_roll") {
      return currentPlayer.name + " vua tung double va duoc them 1 luot tung.";
    }
    if (state.phase === "await_end_turn") {
      return "Khong con xu ly treo. Co the ket thuc luot va chuyen may.";
    }
    return "Tung xuc xac de tiep tuc van dau.";
  }

  function calculateTileValue(tile) {
    const baseValue = tile.price || 0;
    const housesValue = tile.houseCost ? tile.houseCost * tile.houses : 0;
    if (tile.mortgaged) {
      return Math.floor((baseValue + housesValue) * 0.55);
    }
    return baseValue + housesValue;
  }

  function calculateNetWorth(player) {
    return player.money + ownedSpaces(player.id).reduce((total, tile) => total + calculateTileValue(tile), 0);
  }

  function groupTiles(groupName) {
    return state.board.filter((space) => space.group === groupName && space.type === "property");
  }

  function playerOwnsFullSet(playerId, groupName) {
    const group = groupTiles(groupName);
    return group.length > 0 && group.every((space) => space.ownerId === playerId);
  }

  function getRailroadCount(playerId) {
    return state.board.filter((space) => space.type === "railroad" && space.ownerId === playerId).length;
  }

  function getUtilityCount(playerId) {
    return state.board.filter((space) => space.type === "utility" && space.ownerId === playerId).length;
  }

  function getPropertyRent(tile) {
    if (tile.mortgaged) {
      return 0;
    }
    if (tile.type === "property") {
      if (tile.houses >= 5) {
        return tile.rent[5];
      }
      if (tile.houses > 0) {
        return tile.rent[tile.houses];
      }
      const owner = getOwner(tile);
      const baseRent = tile.rent[0];
      if (owner && playerOwnsFullSet(owner.id, tile.group)) {
        return baseRent * 2;
      }
      return baseRent;
    }
    if (tile.type === "railroad") {
      const owner = getOwner(tile);
      const count = owner ? getRailroadCount(owner.id) : 0;
      return [0, 25, 50, 100, 200][count] || 0;
    }
    if (tile.type === "utility") {
      return 0;
    }
    return 0;
  }

  function canMortgage(tileIndex) {
    const currentPlayer = getCurrentPlayer();
    const tile = getBoardSpace(tileIndex);
    if (!currentPlayer || !tile || tile.ownerId !== currentPlayer.id || tile.mortgaged) {
      return false;
    }
    if (tile.type === "property") {
      const group = groupTiles(tile.group);
      return tile.houses === 0 && group.every((space) => space.houses === 0);
    }
    return true;
  }

  function getUnmortgageCost(tile) {
    return Math.ceil(tile.mortgage * 1.1);
  }

  function canUnmortgage(tileIndex) {
    const currentPlayer = getCurrentPlayer();
    const tile = getBoardSpace(tileIndex);
    return Boolean(currentPlayer && tile && tile.ownerId === currentPlayer.id && tile.mortgaged && currentPlayer.money >= getUnmortgageCost(tile) && !state.debt);
  }

  function canBuildHouse(tileIndex) {
    const currentPlayer = getCurrentPlayer();
    const tile = getBoardSpace(tileIndex);
    if (!currentPlayer || !tile || tile.type !== "property" || tile.ownerId !== currentPlayer.id || tile.mortgaged || tile.houses >= 5 || currentPlayer.money < tile.houseCost || state.debt) {
      return false;
    }
    if (!playerOwnsFullSet(currentPlayer.id, tile.group)) {
      return false;
    }
    const group = groupTiles(tile.group);
    if (group.some((space) => space.mortgaged)) {
      return false;
    }
    const minimumHouses = Math.min(...group.map((space) => space.houses));
    return tile.houses === minimumHouses;
  }

  function canSellHouse(tileIndex) {
    const currentPlayer = getCurrentPlayer();
    const tile = getBoardSpace(tileIndex);
    if (!currentPlayer || !tile || tile.type !== "property" || tile.ownerId !== currentPlayer.id || tile.houses <= 0) {
      return false;
    }
    const group = groupTiles(tile.group);
    const maximumHouses = Math.max(...group.map((space) => space.houses));
    return tile.houses === maximumHouses;
  }

  function handleRoll() {
    if (isRoomAttached()) {
      void runRoomAction("roll");
      return;
    }
    const currentPlayer = getCurrentPlayer();
    if (!currentPlayer || currentPlayer.bankrupt || state.phase === "ended" || state.debt || !["await_roll", "jail"].includes(state.phase)) {
      return;
    }

    const dice = rollDice();
    state.lastDice = dice;
    state.lastCard = null;

    if (currentPlayer.inJail && state.phase === "jail") {
      handleJailRoll(currentPlayer, dice);
      return;
    }

    currentPlayer.consecutiveDoubles = dice.doubles ? currentPlayer.consecutiveDoubles + 1 : 0;
    state.pendingExtraRoll = dice.doubles;

    if (currentPlayer.consecutiveDoubles >= 3) {
      logAction("3 lan double", currentPlayer.name + " bi dua vao tu vi double 3 lan lien tuc.");
      sendPlayerToJail(currentPlayer, "Double 3 lan lien tuc.");
      return;
    }

    movePlayerBySteps(currentPlayer, dice.total, "Tung " + dice.a + " va " + dice.b + ".");
  }

  function handleJailRoll(player, dice) {
    state.pendingExtraRoll = false;
    if (dice.doubles) {
      player.inJail = false;
      player.jailTurns = 0;
      player.consecutiveDoubles = 0;
      logAction("Ra tu", player.name + " tung double va duoc ra tu.");
      movePlayerBySteps(player, dice.total, "Ra tu bang double.");
      return;
    }

    player.jailTurns += 1;
    if (player.jailTurns >= 3) {
      player.inJail = false;
      player.jailTurns = 0;
      forceChargePlayer(state.currentPlayerIndex, JAIL_BAIL, "Tien bao lanh sau 3 luot trong tu.", null, true);
      logAction("Ra tu", player.name + " het 3 luot trong tu va phai nop 50$ de tiep tuc.");
      if (!state.debt) {
        movePlayerBySteps(player, dice.total, "Ra tu sau 3 luot.");
      } else {
        state.phase = "debt";
        renderAll();
      }
      return;
    }

    state.phase = "await_end_turn";
    logAction("Trong tu", player.name + " chua ra tu va phai cho luot sau.");
    renderAll();
  }

  function handleBuyProperty() {
    if (isRoomAttached()) {
      void runRoomAction("buy");
      return;
    }
    const currentPlayer = getCurrentPlayer();
    if (!currentPlayer || !state.pendingPurchase) {
      return;
    }
    const tile = getBoardSpace(state.pendingPurchase.tileIndex);
    if (!tile || currentPlayer.money < tile.price) {
      return;
    }
    currentPlayer.money -= tile.price;
    tile.ownerId = currentPlayer.id;
    tile.mortgaged = false;
    tile.houses = 0;
    logAction("Mua tai san", currentPlayer.name + " mua " + tile.name + " voi gia " + formatMoney(tile.price) + ".");
    state.pendingPurchase = null;
    finishStepAfterResolution();
  }

  function handleSkipPurchase() {
    if (isRoomAttached()) {
      void runRoomAction("skip_purchase");
      return;
    }
    if (!state.pendingPurchase) {
      return;
    }
    const tile = getBoardSpace(state.pendingPurchase.tileIndex);
    logAction("Bo qua", getCurrentPlayer().name + " bo qua quyen mua " + tile.name + ".");
    state.pendingPurchase = null;
    finishStepAfterResolution();
  }

  function handleEndTurn() {
    if (isRoomAttached()) {
      void runRoomAction("end_turn");
      return;
    }
    const currentPlayer = getCurrentPlayer();
    if (!currentPlayer || state.phase !== "await_end_turn") {
      return;
    }
    currentPlayer.consecutiveDoubles = 0;
    state.pendingExtraRoll = false;
    advanceToNextPlayer();
    renderAll();
  }

  function handlePayBail() {
    if (isRoomAttached()) {
      void runRoomAction("pay_bail");
      return;
    }
    const currentPlayer = getCurrentPlayer();
    if (!currentPlayer || !currentPlayer.inJail || state.phase !== "jail" || currentPlayer.money < JAIL_BAIL) {
      return;
    }
    currentPlayer.money -= JAIL_BAIL;
    state.freeParkingPot += JAIL_BAIL;
    currentPlayer.inJail = false;
    currentPlayer.jailTurns = 0;
    state.phase = "await_roll";
    logAction("Bao lanh", currentPlayer.name + " nop 50$ de ra tu.");
    renderAll();
  }

  function handleUseEscapeCard() {
    if (isRoomAttached()) {
      void runRoomAction("use_escape_card");
      return;
    }
    const currentPlayer = getCurrentPlayer();
    if (!currentPlayer || !currentPlayer.inJail || state.phase !== "jail" || currentPlayer.escapeCards <= 0) {
      return;
    }
    currentPlayer.escapeCards -= 1;
    currentPlayer.inJail = false;
    currentPlayer.jailTurns = 0;
    state.phase = "await_roll";
    logAction("The ra tu", currentPlayer.name + " dung the ra tu mien phi.");
    renderAll();
  }

  function handleDeclareBankruptcy() {
    if (isRoomAttached()) {
      void runRoomAction("declare_bankruptcy");
      return;
    }
    const currentPlayer = getCurrentPlayer();
    if (!currentPlayer || !state.debt || state.debt.playerId !== currentPlayer.id) {
      return;
    }
    bankruptCurrentPlayer("Khong the can bang no.");
  }

  function buildHouse(tileIndex) {
    if (!canBuildHouse(tileIndex)) {
      return;
    }
    const tile = getBoardSpace(tileIndex);
    const currentPlayer = getCurrentPlayer();
    currentPlayer.money -= tile.houseCost;
    tile.houses += 1;
    logAction("Xay dung", currentPlayer.name + " nang cap " + tile.name + " len muc " + tile.houses + ".");
    renderAll();
  }

  function sellHouse(tileIndex) {
    if (!canSellHouse(tileIndex)) {
      return;
    }
    const tile = getBoardSpace(tileIndex);
    const currentPlayer = getCurrentPlayer();
    tile.houses -= 1;
    currentPlayer.money += Math.floor(tile.houseCost / 2);
    logAction("Ban nha", currentPlayer.name + " ban 1 cap nha tai " + tile.name + " de thu ve " + formatMoney(Math.floor(tile.houseCost / 2)) + ".");
    settleDebtIfPossible();
    renderAll();
  }

  function mortgageProperty(tileIndex) {
    if (!canMortgage(tileIndex)) {
      return;
    }
    const tile = getBoardSpace(tileIndex);
    const currentPlayer = getCurrentPlayer();
    tile.mortgaged = true;
    currentPlayer.money += tile.mortgage;
    logAction("The chap", currentPlayer.name + " the chap " + tile.name + " de nhan " + formatMoney(tile.mortgage) + ".");
    settleDebtIfPossible();
    renderAll();
  }

  function unmortgageProperty(tileIndex) {
    if (!canUnmortgage(tileIndex)) {
      return;
    }
    const tile = getBoardSpace(tileIndex);
    const currentPlayer = getCurrentPlayer();
    const cost = getUnmortgageCost(tile);
    currentPlayer.money -= cost;
    tile.mortgaged = false;
    logAction("Giai chap", currentPlayer.name + " giai chap " + tile.name + " voi chi phi " + formatMoney(cost) + ".");
    renderAll();
  }

  function rollDice() {
    const a = 1 + Math.floor(Math.random() * 6);
    const b = 1 + Math.floor(Math.random() * 6);
    return {
      a,
      b,
      total: a + b,
      doubles: a === b
    };
  }

  function movePlayerBySteps(player, steps, reason) {
    const origin = player.position;
    const destination = (origin + steps) % state.board.length;
    const passedGo = origin + steps >= state.board.length;
    const passGoAmount = Math.max(50, Number(state.settings?.passGoAmount || PASS_GO_AMOUNT));
    if (passedGo) {
      player.money += passGoAmount;
      logAction("Qua GO", player.name + " nhan " + formatMoney(passGoAmount) + " khi di qua GO.");
    }
    player.position = destination;
    state.selectedTileIndex = destination;
    logAction("Di chuyen", player.name + " den " + getBoardSpace(destination).name + ". " + reason);
    resolveLanding(player, getBoardSpace(destination));
  }

  function movePlayerTo(player, destination, reason, collectGo) {
    const passedGo = collectGo || destination < player.position;
    const passGoAmount = Math.max(50, Number(state.settings?.passGoAmount || PASS_GO_AMOUNT));
    if (passedGo) {
      player.money += passGoAmount;
      logAction("Qua GO", player.name + " nhan " + formatMoney(passGoAmount) + " khi di qua GO.");
    }
    player.position = destination;
    state.selectedTileIndex = destination;
    logAction("The su kien", player.name + " di toi " + getBoardSpace(destination).name + ". " + reason);
    resolveLanding(player, getBoardSpace(destination));
  }

  function resolveLanding(player, tile) {
    if (!tile || player.bankrupt) {
      renderAll();
      return;
    }

    state.selectedTileIndex = tile.index;

    switch (tile.type) {
      case "go":
      case "jail":
        finishStepAfterResolution();
        return;
      case "freeParking":
        if (state.freeParkingPot > 0) {
          player.money += state.freeParkingPot;
          logAction("Free parking", player.name + " nhat quy jackpot " + formatMoney(state.freeParkingPot) + ".");
          state.freeParkingPot = 0;
        }
        finishStepAfterResolution();
        return;
      case "goToJail":
        sendPlayerToJail(player, "Dung o Go To Jail.");
        return;
      case "tax":
        forceChargePlayer(state.currentPlayerIndex, tile.amount, tile.name, null, true);
        return;
      case "chance":
        drawAndApplyCard("chance");
        return;
      case "community":
        drawAndApplyCard("community");
        return;
      case "property":
      case "railroad":
      case "utility":
        resolveAssetLanding(player, tile);
        return;
      default:
        finishStepAfterResolution();
    }
  }

  function resolveAssetLanding(player, tile) {
    const owner = getOwner(tile);
    if (!owner) {
      state.pendingPurchase = { tileIndex: tile.index };
      state.phase = "await_purchase";
      renderAll();
      return;
    }

    if (owner.id === player.id) {
      finishStepAfterResolution();
      return;
    }

    if (tile.mortgaged) {
      logAction("Khong thu tien", tile.name + " dang duoc the chap nen khong phat sinh tien thue.");
      finishStepAfterResolution();
      return;
    }

    let rent = getPropertyRent(tile);
    if (tile.type === "utility") {
      const utilityCount = getUtilityCount(owner.id);
      rent = (utilityCount >= 2 ? 10 : 4) * (state.lastDice ? state.lastDice.total : 7);
    }
    if (state.specialRent && state.specialRent.tileIndex === tile.index) {
      if (state.specialRent.kind === "railroad") {
        rent *= 2;
      }
      if (state.specialRent.kind === "utility") {
        rent = 10 * (state.lastDice ? state.lastDice.total : 7);
      }
    }
    state.specialRent = null;
    forceChargePlayer(state.currentPlayerIndex, rent, "Tien thue " + tile.name, owner.id, false);
  }

  function drawAndApplyCard(deckType) {
    const deckKey = deckType === "chance" ? "chanceDeck" : "chestDeck";
    if (!state[deckKey] || state[deckKey].length === 0) {
      state[deckKey] = createDeck(deckType === "chance" ? CHANCE_CARDS : CHEST_CARDS);
    }
    const card = state[deckKey].shift();
    state.lastCard = { title: deckType === "chance" ? "Chance" : "Community Chest", label: card.label };
    applyCard(card);
  }

  function applyCard(card) {
    const currentPlayer = getCurrentPlayer();
    if (!currentPlayer) {
      return;
    }

    if (card.kind === "move") {
      movePlayerTo(currentPlayer, card.target, card.label, Boolean(card.collectGo));
      return;
    }
    if (card.kind === "moveBack") {
      const destination = (currentPlayer.position - card.steps + state.board.length) % state.board.length;
      currentPlayer.position = destination;
      state.selectedTileIndex = destination;
      logAction("The su kien", currentPlayer.name + " lui " + card.steps + " o.");
      resolveLanding(currentPlayer, getBoardSpace(destination));
      return;
    }
    if (card.kind === "nearestRailroad") {
      state.specialRent = { tileIndex: findNearestTileIndex(currentPlayer.position, "railroad"), kind: "railroad" };
      movePlayerTo(currentPlayer, state.specialRent.tileIndex, card.label, state.specialRent.tileIndex < currentPlayer.position);
      return;
    }
    if (card.kind === "nearestUtility") {
      state.specialRent = { tileIndex: findNearestTileIndex(currentPlayer.position, "utility"), kind: "utility" };
      movePlayerTo(currentPlayer, state.specialRent.tileIndex, card.label, state.specialRent.tileIndex < currentPlayer.position);
      return;
    }
    if (card.kind === "cash") {
      if (card.amount >= 0) {
        currentPlayer.money += card.amount;
        logAction("The su kien", currentPlayer.name + " nhan " + formatMoney(card.amount) + ".");
        finishStepAfterResolution();
      } else {
        forceChargePlayer(state.currentPlayerIndex, Math.abs(card.amount), card.label, null, Boolean(card.toPot));
      }
      return;
    }
    if (card.kind === "repairs") {
      const repairAmount = ownedSpaces(currentPlayer.id).reduce((total, tile) => {
        if (tile.type !== "property") {
          return total;
        }
        if (tile.houses >= 5) {
          return total + card.hotelFee;
        }
        return total + (tile.houses * card.houseFee);
      }, 0);
      if (repairAmount > 0) {
        forceChargePlayer(state.currentPlayerIndex, repairAmount, card.label, null, true);
      } else {
        logAction("The su kien", currentPlayer.name + " khong co nha/hotel de sua chua.");
        finishStepAfterResolution();
      }
      return;
    }
    if (card.kind === "gotoJail") {
      sendPlayerToJail(currentPlayer, card.label);
      return;
    }
    if (card.kind === "escape") {
      currentPlayer.escapeCards += 1;
      logAction("The su kien", currentPlayer.name + " nhan 1 the ra tu mien phi.");
      finishStepAfterResolution();
    }
  }

  function findNearestTileIndex(startIndex, type) {
    for (let offset = 1; offset <= state.board.length; offset += 1) {
      const candidate = (startIndex + offset) % state.board.length;
      if (state.board[candidate].type === type) {
        return candidate;
      }
    }
    return startIndex;
  }

  function sendPlayerToJail(player, reason) {
    player.position = 10;
    player.inJail = true;
    player.jailTurns = 0;
    player.consecutiveDoubles = 0;
    state.pendingExtraRoll = false;
    state.pendingPurchase = null;
    state.phase = "await_end_turn";
    state.selectedTileIndex = 10;
    logAction("Vao tu", player.name + " bi dua vao tu. " + reason);
    renderAll();
  }

  function forceChargePlayer(playerIndex, amount, reason, creditorId, toPot) {
    const player = state.players[playerIndex];
    if (!player || player.bankrupt || amount <= 0) {
      finishStepAfterResolution();
      return;
    }

    if (player.money >= amount) {
      player.money -= amount;
      if (creditorId) {
        getPlayerById(creditorId).money += amount;
      }
      if (toPot) {
        state.freeParkingPot += amount;
      }
      logAction("Thanh toan", player.name + " tra " + formatMoney(amount) + " cho " + (creditorId ? getPlayerById(creditorId).name : "ngan hang") + ".");
      finishStepAfterResolution();
      return;
    }

    state.debt = {
      playerId: player.id,
      creditorId: creditorId || null,
      amount,
      reason,
      toPot: Boolean(toPot)
    };
    state.phase = "debt";
    logAction("Can xoay von", player.name + " can " + formatMoney(amount) + " de thanh toan: " + reason);
    renderAll();
  }

  function settleDebtIfPossible() {
    if (!state.debt) {
      return false;
    }
    const debtor = getPlayerById(state.debt.playerId);
    if (!debtor || debtor.money < state.debt.amount) {
      return false;
    }
    debtor.money -= state.debt.amount;
    if (state.debt.creditorId) {
      getPlayerById(state.debt.creditorId).money += state.debt.amount;
    }
    if (state.debt.toPot) {
      state.freeParkingPot += state.debt.amount;
    }
    logAction("Tra no", debtor.name + " vua can bang khoan no " + formatMoney(state.debt.amount) + ".");
    state.debt = null;
    finishStepAfterResolution();
    return true;
  }

  function bankruptCurrentPlayer(reason) {
    const currentPlayer = getCurrentPlayer();
    if (!currentPlayer) {
      return;
    }
    const debt = state.debt;
    const creditor = debt && debt.creditorId ? getPlayerById(debt.creditorId) : null;
    const assets = ownedSpaces(currentPlayer.id);

    if (creditor) {
      creditor.money += currentPlayer.money;
    } else if (debt && debt.toPot) {
      state.freeParkingPot += currentPlayer.money;
    }

    assets.forEach((tile) => {
      if (creditor) {
        tile.ownerId = creditor.id;
      } else {
        tile.ownerId = null;
        tile.houses = 0;
        tile.mortgaged = false;
      }
    });

    currentPlayer.money = 0;
    currentPlayer.bankrupt = true;
    currentPlayer.inJail = false;
    currentPlayer.escapeCards = 0;
    currentPlayer.consecutiveDoubles = 0;
    state.debt = null;
    state.pendingPurchase = null;
    state.pendingExtraRoll = false;

    logAction("Pha san", currentPlayer.name + " pha san. " + reason);
    if (activePlayers().length <= 1) {
      finishGame("Chi con 1 nguoi choi chua pha san.");
      return;
    }
    advanceToNextPlayer();
    renderAll();
  }

  function finishStepAfterResolution() {
    if (state.phase === "ended") {
      renderAll();
      return;
    }
    if (state.debt) {
      state.phase = "debt";
      renderAll();
      return;
    }
    if (activePlayers().length <= 1) {
      finishGame("Chi con 1 nguoi choi chua pha san.");
      return;
    }
    state.phase = state.pendingExtraRoll ? "await_roll" : "await_end_turn";
    renderAll();
  }

  function advanceToNextPlayer() {
    let nextIndex = state.currentPlayerIndex;
    do {
      nextIndex = (nextIndex + 1) % state.players.length;
      if (nextIndex === 0) {
        state.round += 1;
      }
    } while (state.players[nextIndex].bankrupt);

    state.currentPlayerIndex = nextIndex;
    state.turnNumber += 1;
    state.lastDice = null;
    state.lastCard = null;
    state.pendingPurchase = null;
    state.specialRent = null;
    state.selectedTileIndex = state.players[nextIndex].position;
    state.phase = state.players[nextIndex].inJail ? "jail" : "await_roll";
    logAction("Chuyen luot", state.players[nextIndex].name + " den luot.");
  }

  function finishGame(reason) {
    const ranked = state.players
      .map((player) => ({ player, value: calculateNetWorth(player) }))
      .sort((left, right) => right.value - left.value);
    state.phase = "ended";
    state.pendingPurchase = null;
    state.pendingExtraRoll = false;
    state.debt = null;
    state.winnerId = ranked[0] ? ranked[0].player.id : null;
    if (ranked[0]) {
      logAction("Ket van", reason + " Dan dau: " + ranked[0].player.name + " voi gia tri rong " + formatMoney(ranked[0].value) + ".");
    } else {
      logAction("Ket van", reason);
    }
    renderAll();
  }

  function createRoomSession() {
    return {
      playerId: "",
      playerName: "",
      roomId: readRoomIdFromUrl(),
      room: null,
      rooms: [],
      pollTimer: 0,
      publishTimer: 0,
      pendingGameState: null,
      pendingFingerprint: "",
      lastPublishedFingerprint: "",
      applyingRemote: false,
      statusMessage: "",
      statusIsError: false
    };
  }

  function hydrateStoredPlayerProfile() {
    const stored = readStoredProfile();
    if (stored.playerId) {
      roomSession.playerId = stored.playerId;
    }
    if (stored.playerName) {
      roomSession.playerName = stored.playerName;
    }
    if (refs.roomPlayerName && roomSession.playerName) {
      refs.roomPlayerName.value = roomSession.playerName;
    }
  }

  function readStoredProfile() {
    try {
      const raw = window.localStorage.getItem(ROOM_STORAGE_KEY);
      if (!raw) {
        return {};
      }
      const parsed = JSON.parse(raw);
      return typeof parsed === "object" && parsed ? parsed : {};
    } catch (_) {
      return {};
    }
  }

  function persistStoredProfile() {
    try {
      window.localStorage.setItem(ROOM_STORAGE_KEY, JSON.stringify({
        playerId: roomSession.playerId || "",
        playerName: roomSession.playerName || ""
      }));
    } catch (_) {
    }
  }

  function readRoomIdFromUrl() {
    try {
      const bootRoomId = String(boot.defaultRoomId || "").trim().toUpperCase();
      if (bootRoomId) {
        return bootRoomId;
      }
      const currentUrl = new URL(window.location.href);
      const pathMatch = currentUrl.pathname.match(/\/games\/monopoly\/room\/([^/?#]+)/i);
      if (pathMatch && pathMatch[1]) {
        return decodeURIComponent(pathMatch[1]).trim().toUpperCase();
      }
      const params = currentUrl.searchParams;
      return String(params.get("roomId") || params.get("room") || "").trim().toUpperCase();
    } catch (_) {
      return "";
    }
  }

  function updateRoomUrl(roomId) {
    try {
      const normalizedRoomId = String(roomId || "").trim().toUpperCase();
      const path = normalizedRoomId
        ? buildRoomPath(normalizedRoomId)
        : (isLocalPage() ? buildLocalPath() : buildLobbyPath());
      const url = new URL(path, window.location.origin);
      window.history.replaceState({}, "", url.pathname + url.search + url.hash);
    } catch (_) {
    }
  }

  function isDedicatedRoomPage() {
    return Boolean(boot.roomPage) || /\/games\/monopoly\/room\//i.test(String(window.location.pathname || ""));
  }

  function isLocalPage() {
    return Boolean(boot.localPage) || /\/games\/monopoly\/local\/?$/i.test(String(window.location.pathname || ""));
  }

  function shouldBootLocalBoard() {
    return isLocalPage();
  }

  function hasRoomUi() {
    return Boolean(refs.roomStatus || refs.currentRoom || refs.roomPlayers || refs.tokenPicker || refs.openRooms || refs.roomCodeInput);
  }

  function buildLobbyPath() {
    return appPath("/games/monopoly");
  }

  function buildLocalPath() {
    return appPath("/games/monopoly/local");
  }

  function buildRoomPath(roomId) {
    return appPath("/games/monopoly/room/" + encodeURIComponent(String(roomId || "").trim().toUpperCase()));
  }

  function navigateToRoomPage(roomId) {
    const normalizedRoomId = String(roomId || "").trim().toUpperCase();
    if (!normalizedRoomId) {
      setRoomStatus("Ma phong khong hop le.", true);
      return;
    }
    window.location.href = buildRoomPath(normalizedRoomId);
  }

  function setRoomStatus(message, isError) {
    roomSession.statusMessage = message || "";
    roomSession.statusIsError = Boolean(isError);
    if (!refs.roomStatus) {
      return;
    }
    refs.roomStatus.textContent = roomSession.statusMessage || "Tao phong moi hoac nhap ma phong de vao lobby Monopoly.";
    refs.roomStatus.classList.toggle("monopoly-note--alert", roomSession.statusIsError);
  }

  function isRoomAttached() {
    return Boolean(roomSession.room && roomSession.room.roomId);
  }

  function isCurrentBrowserHost() {
    return Boolean(roomSession.room && roomSession.playerId && roomSession.room.hostPlayerId === roomSession.playerId);
  }

  function isRoomHostPlayer(playerId) {
    return Boolean(roomSession.room && playerId && roomSession.room.hostPlayerId === playerId);
  }

  function currentBrowserRoomPlayer() {
    if (!roomSession.room || !roomSession.playerId) {
      return null;
    }
    return (roomSession.room.players || []).find((player) => player.playerId === roomSession.playerId) || null;
  }

  function resolveBoardEyebrowLabel() {
    if (roomSession.room && roomSession.room.roomId) {
      return "Room " + roomSession.room.roomId;
    }
    return "Local Monopoly";
  }

  function resolveTokenLabel(tokenId) {
    const token = TOKEN_CATALOG.find((item) => item.id === tokenId);
    return token ? token.label : "Token";
  }

  function canUseTurnControls() {
    if (!isRoomAttached()) {
      return true;
    }
    if (!roomSession.room || roomSession.room.status !== "PLAYING") {
      return false;
    }
    if (state.phase === "auction" && state.auction && roomSession.playerId) {
      return state.auction.activePlayerId === roomSession.playerId;
    }
    if (state.phase === "trade" && state.tradeOffer && roomSession.playerId) {
      return state.tradeOffer.fromPlayerId === roomSession.playerId || state.tradeOffer.toPlayerId === roomSession.playerId;
    }
    const currentPlayer = getCurrentPlayer();
    return Boolean(currentPlayer && roomSession.playerId && currentPlayer.id === roomSession.playerId);
  }

  function canCreateTradeOffer() {
    const currentPlayer = getCurrentPlayer();
    if (!currentPlayer || currentPlayer.bankrupt || !canUseTurnControls()) {
      return false;
    }
    return isRoomAttached()
      && !state.tradeOffer
      && !state.auction
      && !state.debt
      && !state.pendingPurchase
      && ["await_roll", "await_end_turn", "jail"].includes(state.phase);
  }

  function tradableAssets(playerId) {
    return ownedSpaces(playerId).filter((tile) => {
      if (tile.houses > 0) {
        return false;
      }
      if (tile.type === "property") {
        return groupTiles(tile.group).every((space) => space.houses === 0);
      }
      return true;
    });
  }

  function toggleDraftTile(key, tileIndex, checked) {
    if (!Number.isFinite(tileIndex)) {
      return;
    }
    const current = Array.isArray(tradeDraft[key]) ? tradeDraft[key].slice() : [];
    const next = checked
      ? (current.includes(tileIndex) ? current : current.concat(tileIndex))
      : current.filter((value) => value !== tileIndex);
    tradeDraft[key] = next;
  }

  function resetTradeDraft() {
    tradeDraft.targetPlayerId = "";
    tradeDraft.offeredCash = 0;
    tradeDraft.requestedCash = 0;
    tradeDraft.offeredTileIndices = [];
    tradeDraft.requestedTileIndices = [];
  }

  function sendTradeOffer() {
    const currentPlayer = getCurrentPlayer();
    if (!currentPlayer || !tradeDraft.targetPlayerId) {
      setRoomStatus("Chon doi tac trade truoc khi gui de nghi.", true);
      return;
    }
    void runRoomAction("trade_offer", null, null, {
      targetPlayerId: tradeDraft.targetPlayerId,
      offeredCash: Math.max(0, Number(tradeDraft.offeredCash || 0)),
      requestedCash: Math.max(0, Number(tradeDraft.requestedCash || 0)),
      offeredTileIndices: tradeDraft.offeredTileIndices.slice(),
      requestedTileIndices: tradeDraft.requestedTileIndices.slice()
    });
  }

  function renderSetupAvailability() {
    if (!refs.localSetupCard) {
      return;
    }
    refs.localSetupCard.hidden = !isLocalPage() || isRoomAttached();
  }

  function renderRoomPanels() {
    const hasRoomSummary = Boolean(refs.currentRoom || refs.roomPlayers || refs.tokenPicker);
    const hasRoomList = Boolean(refs.openRooms);
    if (!hasRoomSummary && !hasRoomList) {
      return;
    }

    const room = roomSession.room;
    const players = room && Array.isArray(room.players) ? room.players : [];
    if (!room) {
      if (refs.currentRoom) {
        refs.currentRoom.innerHTML = '<div class="monopoly-empty-state">Chua gan voi phong nao. Tao room moi hoac chon mot phong dang mo ben duoi.</div>';
      }
      if (refs.roomPlayers) {
        refs.roomPlayers.innerHTML = '<div class="monopoly-empty-state">Lobby nguoi choi se hien tai day khi ban vao phong.</div>';
      }
      if (refs.tokenPicker) {
        refs.tokenPicker.innerHTML = '<div class="monopoly-empty-state">Vao phong truoc, sau do chon token dai dien nhu cho, xe hoi, mu hoac tau.</div>';
      }
    } else {
      const host = players.find((player) => player.playerId === room.hostPlayerId) || null;
      if (refs.currentRoom) {
        refs.currentRoom.innerHTML = `
          <div class="monopoly-current-room__meta">
            <div><span>Ma phong</span><strong>${escapeHtml(room.roomId)}</strong></div>
            <div><span>Ten phong</span><strong>${escapeHtml(room.roomName)}</strong></div>
            <div><span>Trang thai</span><strong>${escapeHtml(room.status)}</strong></div>
            <div><span>Host</span><strong>${escapeHtml(host ? host.name : "Host")}</strong></div>
            <div><span>So nguoi choi</span><strong>${players.length}/${room.maxPlayers}</strong></div>
            <div><span>Qua GO</span><strong>${formatMoney(room.passGoAmount)}</strong></div>
          </div>
        `;
      }

      if (refs.roomPlayers) {
        refs.roomPlayers.innerHTML = `
          <div class="monopoly-room-player-grid">
            ${players.map((player) => `
              <article class="monopoly-room-player-chip">
                <strong>${escapeHtml(player.name)}${player.playerId === roomSession.playerId ? " (Ban)" : ""}</strong>
                <small>${player.host ? "Host" : "Nguoi choi"} | Token: ${escapeHtml(player.token ? resolveTokenLabel(player.token) : "Chua chon")} | Thu tu: ${player.turnOrder + 1}${player.disconnected ? " | Tam offline" : ""}</small>
              </article>
            `).join("")}
          </div>
        `;
      }

      if (refs.tokenPicker) {
        if (room.status === "WAITING") {
          refs.tokenPicker.innerHTML = `
            <div class="monopoly-token-grid">
              ${TOKEN_CATALOG.map((token) => {
                const owner = players.find((player) => player.token === token.id) || null;
                const selected = owner && owner.playerId === roomSession.playerId;
                const disabled = owner && owner.playerId !== roomSession.playerId;
                return `
                  <button
                    type="button"
                    class="monopoly-token-btn${selected ? " is-selected" : ""}${disabled ? " is-taken" : ""}"
                    data-token-id="${token.id}"
                    ${disabled ? "disabled" : ""}>
                    <strong><i class="bi ${token.icon}"></i> ${escapeHtml(token.label)}</strong>
                    <small>${owner ? "Dang giu: " + escapeHtml(owner.name) : "Con trong"}</small>
                  </button>
                `;
              }).join("")}
            </div>
          `;
        } else {
          refs.tokenPicker.innerHTML = '<div class="monopoly-empty-state">Game da bat dau. Token duoc khoa cho den khi mo room moi.</div>';
        }
      }
    }

    if (refs.openRooms) {
      if (!roomSession.rooms.length) {
        refs.openRooms.innerHTML = '<div class="monopoly-empty-state">Chua co phong dang cho nao. Ban co the tao phong moi de mo lobby.</div>';
      } else {
        refs.openRooms.innerHTML = roomSession.rooms.map((entry) => `
          <article class="monopoly-open-room-card">
            <div class="monopoly-open-room-card__head">
              <strong>${escapeHtml(entry.roomName)}</strong>
              <code>${escapeHtml(entry.roomId)}</code>
            </div>
            <small>${escapeHtml(entry.hostName)} | ${entry.playerCount}/${entry.maxPlayers} nguoi | ${escapeHtml(entry.status)}</small>
            <button type="button" class="hub-portal-inline-btn" data-room-join="${entry.roomId}" ${entry.playerCount >= entry.maxPlayers ? "disabled" : ""}>Vao phong</button>
          </article>
        `).join("");
      }
    }

    const missingToken = room && room.status === "WAITING" && players.some((player) => !player.token);
    if (refs.roomStartBtn) {
      refs.roomStartBtn.disabled = !room || !isCurrentBrowserHost() || room.status !== "WAITING" || players.length < 2 || missingToken;
    }
    if (refs.leaveRoomBtn) {
      refs.leaveRoomBtn.disabled = !room;
    }
    if (refs.copyRoomBtn) {
      refs.copyRoomBtn.disabled = !room;
    }
    if (!roomSession.statusMessage) {
      if (room && room.status === "WAITING") {
        setRoomStatus(isCurrentBrowserHost()
          ? "Phong da tao. Moi nguoi vao phong, chon token roi host bat dau."
          : "Da vao phong. Chon token va cho host mo van.", false);
      } else if (room && room.status === "PLAYING") {
        setRoomStatus("Phong dang trong tran. Chi nguoi dang den luot moi duoc thao tac.", false);
      }
    } else if (refs.roomStatus) {
      refs.roomStatus.textContent = roomSession.statusMessage;
      refs.roomStatus.classList.toggle("monopoly-note--alert", roomSession.statusIsError);
    }
  }

  async function loadRoomList(silent) {
    if (!hasRoomUi()) {
      return;
    }
    try {
      const payload = await fetchJson("/api/games/monopoly/rooms");
      roomSession.rooms = Array.isArray(payload.rooms) ? payload.rooms : [];
      renderRoomPanels();
      if (!silent && !roomSession.room) {
        setRoomStatus("Da cap nhat danh sach phong mo.", false);
      }
    } catch (error) {
      if (!silent) {
        setRoomStatus(error.message || "Khong tai duoc danh sach phong.", true);
      }
    }
  }

  async function createRoom() {
    const playerName = sanitizeName(refs.roomPlayerName?.value, "Ty phu online");
    roomSession.playerName = playerName;
    persistStoredProfile();
    if (isRoomAttached() && roomSession.room && roomSession.room.status === "PLAYING") {
      setRoomStatus("Room hien tai dang o trong tran. Bam 'Roi phong' truoc neu muon mo room moi.", true);
      return;
    }
    try {
      if (isRoomAttached() && roomSession.room && roomSession.room.status !== "PLAYING") {
        await leaveRoomSession();
      }
      const payload = await fetchJson("/api/games/monopoly/rooms", {
        method: "POST",
        body: JSON.stringify({
          playerId: roomSession.playerId || null,
          playerName,
          roomName: sanitizeName(refs.roomName?.value, "Ban Co ty phu"),
          maxPlayers: Number(refs.roomMaxPlayers?.value || 4),
          startingCash: Number(refs.startingCash?.value || 1500),
          passGoAmount: PASS_GO_AMOUNT
        })
      });
      roomSession.playerId = payload.playerId || roomSession.playerId;
      roomSession.playerName = playerName;
      persistStoredProfile();
      if (!isDedicatedRoomPage()) {
        navigateToRoomPage(payload.room.roomId);
        return;
      }
      applyRoomSnapshot(payload.room);
      setRoomStatus("Da tao phong " + payload.room.roomId + ". Chia ma phong va moi them nguoi.", false);
      await loadRoomList(true);
    } catch (error) {
      setRoomStatus(error.message || "Khong tao duoc phong.", true);
    }
  }

  function joinRoomByInput() {
    const roomId = String(refs.roomCodeInput?.value || "").trim().toUpperCase();
    if (!roomId) {
      setRoomStatus("Nhap ma phong truoc khi bam vao phong.", true);
      return;
    }
    joinRoom(roomId, false);
  }

  async function joinRoom(roomId, silent) {
    const normalizedRoomId = String(roomId || "").trim().toUpperCase();
    if (!normalizedRoomId) {
      if (!silent) {
        setRoomStatus("Ma phong khong hop le.", true);
      }
      return;
    }

    const playerName = sanitizeName(refs.roomPlayerName?.value, roomSession.playerName || "Ty phu online");
    roomSession.playerName = playerName;
    persistStoredProfile();
    if (!isDedicatedRoomPage()) {
      navigateToRoomPage(normalizedRoomId);
      return;
    }
    if (isRoomAttached() && roomSession.room && roomSession.room.roomId !== normalizedRoomId && roomSession.room.status === "PLAYING") {
      setRoomStatus("Dang o trong tran khac. Roi phong hien tai truoc khi chuyen room.", true);
      return;
    }

    try {
      if (isRoomAttached() && roomSession.room && roomSession.room.roomId !== normalizedRoomId && roomSession.room.status !== "PLAYING") {
        await leaveRoomSession();
      }
      const payload = await fetchJson("/api/games/monopoly/rooms/" + encodeURIComponent(normalizedRoomId) + "/join", {
        method: "POST",
        body: JSON.stringify({
          playerId: roomSession.playerId || null,
          playerName
        })
      });
      roomSession.playerId = payload.playerId || roomSession.playerId;
      roomSession.playerName = playerName;
      persistStoredProfile();
      applyRoomSnapshot(payload.room);
      if (!silent) {
        setRoomStatus("Da vao phong " + normalizedRoomId + ".", false);
      }
      await loadRoomList(true);
    } catch (error) {
      if (!silent) {
        setRoomStatus(error.message || "Khong vao duoc phong.", true);
      }
      if (roomSession.roomId === normalizedRoomId && !roomSession.room) {
        if (isDedicatedRoomPage()) {
          window.location.href = buildLobbyPath();
          return;
        }
        updateRoomUrl("");
        roomSession.roomId = "";
      }
    }
  }

  async function selectToken(tokenId) {
    if (!isRoomAttached() || !roomSession.room || roomSession.room.status !== "WAITING") {
      return;
    }
    try {
      const payload = await fetchJson("/api/games/monopoly/rooms/" + encodeURIComponent(roomSession.room.roomId) + "/token", {
        method: "POST",
        body: JSON.stringify({
          playerId: roomSession.playerId,
          token: tokenId
        })
      });
      applyRoomSnapshot(payload.room);
      setRoomStatus("Da chon token " + resolveTokenLabel(tokenId) + ".", false);
      await loadRoomList(true);
    } catch (error) {
      setRoomStatus(error.message || "Khong doi duoc token.", true);
    }
  }

  async function startRoomSession() {
    if (!isRoomAttached() || !roomSession.room) {
      return;
    }
    try {
      const payload = await fetchJson("/api/games/monopoly/rooms/" + encodeURIComponent(roomSession.room.roomId) + "/start", {
        method: "POST",
        body: JSON.stringify({
          playerId: roomSession.playerId
        })
      });
      applyRoomSnapshot(payload.room);
      setRoomStatus("Host da bat dau van. Room da chuyen sang che do choi.", false);
    } catch (error) {
      setRoomStatus(error.message || "Khong bat dau duoc phong.", true);
    }
  }

  async function runRoomAction(action, tileIndex, amount, extraPayload) {
    if (!isRoomAttached() || !roomSession.room) {
      return;
    }
    if (roomSession.room.status !== "PLAYING") {
      setRoomStatus("Room chua o trang thai dang choi.", true);
      return;
    }
    if (!roomSession.playerId) {
      setRoomStatus("Khong xac dinh duoc nguoi choi hien tai.", true);
      return;
    }

    try {
      const payload = await fetchJson("/api/games/monopoly/rooms/" + encodeURIComponent(roomSession.room.roomId) + "/action", {
        method: "POST",
        body: JSON.stringify({
          playerId: roomSession.playerId,
          action,
          tileIndex: Number.isFinite(tileIndex) ? tileIndex : null,
          amount: Number.isFinite(amount) ? amount : null,
          ...(extraPayload || {})
        })
      });
      applyRoomSnapshot(payload.room);
      if (action === "trade_offer" || action === "trade_accept" || action === "trade_reject") {
        resetTradeDraft();
      }
      setRoomStatus("Da cap nhat room sau hanh dong: " + describeRoomAction(action) + ".", false);
    } catch (error) {
      if (error.payload && error.payload.room) {
        applyRoomSnapshot(error.payload.room);
      }
      setRoomStatus(error.message || "Khong thuc hien duoc hanh dong trong room.", true);
    }
  }

  function describeRoomAction(action) {
    switch (action) {
      case "roll":
        return "tung xuc xac";
      case "buy":
        return "mua tai san";
      case "skip_purchase":
        return "bo qua mua";
      case "build":
        return "xay nha";
      case "sell-house":
        return "ban nha";
      case "mortgage":
        return "the chap";
      case "unmortgage":
        return "giai chap";
      case "auction_bid":
        return "dat gia";
      case "auction_pass":
        return "bo luot dau gia";
      case "trade_offer":
        return "gui de nghi trade";
      case "trade_accept":
        return "chap nhan trade";
      case "trade_reject":
        return "dong trade";
      case "end_turn":
        return "ket thuc luot";
      case "pay_bail":
        return "nop bao lanh";
      case "use_escape_card":
        return "dung the ra tu";
      case "declare_bankruptcy":
        return "tuyen bo pha san";
      case "finish_game":
        return "chot van";
      default:
        return "cap nhat luot choi";
    }
  }

  async function leaveRoomSession() {
    if (!isRoomAttached() || !roomSession.room) {
      return;
    }
    const roomId = roomSession.room.roomId;
    const wasPlaying = roomSession.room.status === "PLAYING";
    try {
      await fetchJson("/api/games/monopoly/rooms/" + encodeURIComponent(roomId) + "/leave", {
        method: "POST",
        body: JSON.stringify({
          playerId: roomSession.playerId
        })
      });
    } catch (_) {
    }
    stopRoomPolling();
    if (roomSession.publishTimer) {
      window.clearTimeout(roomSession.publishTimer);
      roomSession.publishTimer = 0;
    }
    roomSession.room = null;
    roomSession.roomId = "";
    roomSession.pendingGameState = null;
    roomSession.pendingFingerprint = "";
    roomSession.lastPublishedFingerprint = "";
    roomSession.statusMessage = "";
    roomSession.statusIsError = false;
    resetTradeDraft();
    updateRoomUrl("");
    setRoomStatus(
      wasPlaying
        ? "Da roi room. Slot cua ban duoc danh dau tam offline va co the vao lai bang cung ma phong."
        : "Da roi che do phong. Ban co the tao room khac hoac choi local.",
      false
    );
    if (isDedicatedRoomPage()) {
      window.location.href = buildLobbyPath();
      return;
    }
    if (shouldBootLocalBoard()) {
      startConfiguredGame(readSetupConfig());
    }
    await loadRoomList(true);
  }

  function notifyRoomDisconnect() {
    if (!isRoomAttached() || !roomSession.room || roomSession.room.status !== "PLAYING" || !roomSession.playerId) {
      return;
    }
    const url = "/api/games/monopoly/rooms/" + encodeURIComponent(roomSession.room.roomId) + "/leave";
    const payload = JSON.stringify({ playerId: roomSession.playerId });
    try {
      if (navigator.sendBeacon) {
        navigator.sendBeacon(url, new Blob([payload], { type: "application/json" }));
        return;
      }
    } catch (_) {
    }
    try {
      window.fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: payload,
        keepalive: true
      }).catch(() => {});
    } catch (_) {
    }
  }

  async function copyRoomInvite() {
    if (!isRoomAttached() || !roomSession.room) {
      setRoomStatus("Chua co room nao de sao chep.", true);
      return;
    }
    try {
      const inviteUrl = new URL(buildRoomPath(roomSession.room.roomId), window.location.origin);
      await navigator.clipboard.writeText(inviteUrl.toString());
      setRoomStatus("Da sao chep link moi vao phong " + roomSession.room.roomId + ".", false);
    } catch (_) {
      setRoomStatus("Khong the sao chep tu dong. Hay copy ma phong " + roomSession.room.roomId + ".", true);
    }
  }

  function applyRoomSnapshot(room) {
    roomSession.room = room || null;
    roomSession.roomId = room && room.roomId ? room.roomId : "";
    if (room && room.status !== "PLAYING") {
      resetTradeDraft();
    }
    if (refs.roomCodeInput) {
      refs.roomCodeInput.value = roomSession.roomId || "";
    }
    if (roomSession.roomId) {
      updateRoomUrl(roomSession.roomId);
      startRoomPolling();
    } else {
      stopRoomPolling();
      updateRoomUrl("");
    }

    if (!room) {
      renderAll();
      return;
    }

    if (!room.gameState) {
      applyLobbyPreview(room);
      return;
    }
    applyRemoteGameState(room.gameState);
    return;
  }

  function applyLobbyPreview(room) {
    roomSession.applyingRemote = true;
    state = createEmptyState();
    state.settings.passGoAmount = Number(room.passGoAmount || PASS_GO_AMOUNT);
    state.players = (room.players || []).map((player, index) => createPlayerState(player.name, index, room.startingCash, {
      id: player.playerId,
      token: player.token,
      turnOrder: player.turnOrder,
      isDisconnected: Boolean(player.disconnected)
    }));
    state.phase = "setup";
    state.selectedTileIndex = 0;
    state.log = [{
      title: "Lobby room",
      message: room.status === "PLAYING"
        ? "Room " + room.roomId + " dang khoi tao state van dau. Cho dong bo..."
        : "Phong " + room.roomId + " dang cho host bat dau.",
      stamp: "Lobby"
    }];
    roomSession.lastPublishedFingerprint = "";
    roomSession.applyingRemote = false;
    renderAll();
  }

  function initializeRoomGameState(room) {
    roomSession.applyingRemote = true;
    state = createEmptyState();
    state.settings.passGoAmount = Number(room.passGoAmount || PASS_GO_AMOUNT);
    state.players = (room.players || []).map((player, index) => createPlayerState(player.name, index, room.startingCash, {
      id: player.playerId,
      token: player.token,
      turnOrder: player.turnOrder,
      isDisconnected: Boolean(player.disconnected)
    }));
    state.phase = state.players[0] ? "await_roll" : "setup";
    state.round = 1;
    state.turnNumber = 1;
    state.selectedTileIndex = 0;
    logAction("Phong bat dau", "Room " + room.roomId + " da vao van. " + (state.players[0] ? state.players[0].name : "Nguoi choi") + " di truoc.");
    roomSession.lastPublishedFingerprint = "";
    roomSession.applyingRemote = false;
    renderAll();
  }

  function applyRemoteGameState(gameState) {
    roomSession.applyingRemote = true;
    state = cloneData(gameState);
    if (!state.settings) {
      state.settings = {};
    }
    if (!state.settings.passGoAmount) {
      state.settings.passGoAmount = Number(roomSession.room?.passGoAmount || PASS_GO_AMOUNT);
    }
    if (!Array.isArray(state.board) || !state.board.length) {
      state.board = cloneBoardSpaces();
    }
    const roomPlayers = roomSession.room && Array.isArray(roomSession.room.players) ? roomSession.room.players : [];
    state.players = Array.isArray(state.players) ? state.players.map((player, index) => {
      const roomPlayer = roomPlayers.find((entry) => entry.playerId === player.id) || null;
      return {
        ...player,
        name: roomPlayer && roomPlayer.name ? roomPlayer.name : player.name,
        color: player.color || PLAYER_COLORS[index % PLAYER_COLORS.length],
        token: player.token || (roomPlayer ? roomPlayer.token : null),
        turnOrder: Number.isFinite(player.turnOrder) ? player.turnOrder : (roomPlayer ? roomPlayer.turnOrder : index),
        isDisconnected: roomPlayer ? Boolean(roomPlayer.disconnected) : Boolean(player.isDisconnected)
      };
    }) : [];
    roomSession.lastPublishedFingerprint = fingerprintForRoomState(exportGameStateForRoom());
    roomSession.applyingRemote = false;
    renderAll();
  }

  function startRoomPolling() {
    stopRoomPolling();
    roomSession.pollTimer = window.setInterval(() => {
      void pollCurrentRoom();
    }, ROOM_POLL_MS);
  }

  function stopRoomPolling() {
    if (roomSession.pollTimer) {
      window.clearInterval(roomSession.pollTimer);
      roomSession.pollTimer = 0;
    }
  }

  async function pollCurrentRoom() {
    if (!roomSession.roomId) {
      return;
    }
    try {
      const payload = await fetchJson("/api/games/monopoly/rooms/" + encodeURIComponent(roomSession.roomId));
      const incomingRoom = payload.room || null;
      if (!incomingRoom) {
        return;
      }
      const currentRoom = roomSession.room;
      const localVersion = currentRoom ? Number(currentRoom.version || 0) : 0;
      const incomingVersion = Number(incomingRoom.version || 0);
      if (incomingVersion !== localVersion || Boolean(currentRoom?.gameState) !== Boolean(incomingRoom.gameState) || incomingRoom.status !== (currentRoom?.status || "")) {
        applyRoomSnapshot(incomingRoom);
      } else {
        roomSession.room = incomingRoom;
        roomSession.roomId = incomingRoom.roomId;
        renderRoomPanels();
      }
    } catch (_) {
    }
  }

  function maybeQueueRoomStatePublish() {
    if (!canPublishRoomState()) {
      return;
    }
    const exportState = exportGameStateForRoom();
    const fingerprint = fingerprintForRoomState(exportState);
    if (!fingerprint || fingerprint === roomSession.lastPublishedFingerprint || fingerprint === roomSession.pendingFingerprint) {
      return;
    }
    roomSession.pendingGameState = exportState;
    roomSession.pendingFingerprint = fingerprint;
    if (roomSession.publishTimer) {
      window.clearTimeout(roomSession.publishTimer);
    }
    roomSession.publishTimer = window.setTimeout(() => {
      void publishRoomState();
    }, ROOM_STATE_DEBOUNCE_MS);
  }

  function canPublishRoomState() {
    return false;
  }

  async function publishRoomState() {
    if (!roomSession.pendingGameState || !roomSession.pendingFingerprint || !roomSession.roomId || !roomSession.room) {
      return;
    }
    const roomId = roomSession.roomId;
    const baseVersion = Number(roomSession.room.version || 0);
    const payloadState = roomSession.pendingGameState;
    const payloadFingerprint = roomSession.pendingFingerprint;
    roomSession.pendingGameState = null;
    roomSession.pendingFingerprint = "";
    roomSession.publishTimer = 0;

    try {
      const payload = await fetchJson("/api/games/monopoly/rooms/" + encodeURIComponent(roomId) + "/sync", {
        method: "POST",
        body: JSON.stringify({
          playerId: roomSession.playerId,
          baseVersion,
          gameState: payloadState
        })
      });
      roomSession.lastPublishedFingerprint = payloadFingerprint;
      if (payload.room) {
        applyRoomSnapshot(payload.room);
      }
    } catch (error) {
      if (error.payload && error.payload.room) {
        applyRoomSnapshot(error.payload.room);
      }
      setRoomStatus(error.message || "Dong bo state room that bai.", true);
    }
  }

  function exportGameStateForRoom() {
    return cloneData(state);
  }

  function fingerprintForRoomState(snapshot) {
    if (!snapshot) {
      return "";
    }
    const clone = cloneData(snapshot);
    clone.selectedTileIndex = -1;
    return JSON.stringify(clone);
  }

  function cloneData(value) {
    if (typeof window.structuredClone === "function") {
      try {
        return window.structuredClone(value);
      } catch (_) {
      }
    }
    return JSON.parse(JSON.stringify(value));
  }

  async function fetchJson(url, options) {
    const requestInit = options ? { ...options } : {};
    requestInit.headers = {
      Accept: "application/json",
      ...(requestInit.body ? { "Content-Type": "application/json" } : {}),
      ...(requestInit.headers || {})
    };
    const response = await fetch(url, requestInit);
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.success === false) {
      const error = new Error(payload.error || "Yeu cau Monopoly khong thanh cong.");
      error.payload = payload;
      throw error;
    }
    return payload;
  }
});
