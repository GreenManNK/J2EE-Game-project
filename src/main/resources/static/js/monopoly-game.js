document.addEventListener("DOMContentLoaded", () => {
  const root = document.getElementById("monopolyApp");
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
    startBtn: document.getElementById("monopolyStartBtn"),
    restartBtn: document.getElementById("monopolyRestartBtn"),
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
    selectedTile: document.getElementById("monopolySelectedTile"),
    standings: document.getElementById("monopolyStandings"),
    players: document.getElementById("monopolyPlayers"),
    portfolio: document.getElementById("monopolyPortfolio"),
    log: document.getElementById("monopolyLog")
  };

  const PASS_GO_AMOUNT = 200;
  const JAIL_BAIL = 50;
  const MAX_LOG_ITEMS = 28;
  const PLAYER_COLORS = ["#f97316", "#22c55e", "#38bdf8", "#f43f5e"];
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

  bindStaticEvents();
  syncSetupFields();
  startNewGame(false);

  function createEmptyState() {
    return {
      players: [],
      board: cloneBoardSpaces(),
      chanceDeck: createDeck(CHANCE_CARDS),
      chestDeck: createDeck(CHEST_CARDS),
      currentPlayerIndex: 0,
      selectedTileIndex: 0,
      pendingPurchase: null,
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
      winnerId: null
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
    refs.playerCount.addEventListener("change", syncSetupFields);
    refs.startBtn.addEventListener("click", () => startNewGame(true));
    refs.restartBtn.addEventListener("click", () => startNewGame(true));
    refs.rollBtn.addEventListener("click", handleRoll);
    refs.buyBtn.addEventListener("click", handleBuyProperty);
    refs.skipBuyBtn.addEventListener("click", handleSkipPurchase);
    refs.endTurnBtn.addEventListener("click", handleEndTurn);
    refs.payBailBtn.addEventListener("click", handlePayBail);
    refs.useCardBtn.addEventListener("click", handleUseEscapeCard);
    refs.bankruptcyBtn.addEventListener("click", handleDeclareBankruptcy);
    refs.finishBtn.addEventListener("click", () => finishGame("Van dau duoc chot bang tay."));

    refs.board.addEventListener("click", (event) => {
      const button = event.target.closest("[data-space-index]");
      if (!button) {
        return;
      }
      state.selectedTileIndex = Number(button.getAttribute("data-space-index"));
      renderAll();
    });

    refs.selectedTile.addEventListener("click", (event) => {
      const actionButton = event.target.closest("[data-monopoly-action]");
      if (!actionButton) {
        return;
      }
      const action = actionButton.getAttribute("data-monopoly-action");
      const tileIndex = Number(actionButton.getAttribute("data-space-index"));
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

    refs.portfolio.addEventListener("click", (event) => {
      const asset = event.target.closest("[data-space-select]");
      if (!asset) {
        return;
      }
      state.selectedTileIndex = Number(asset.getAttribute("data-space-select"));
      renderAll();
    });
  }

  function syncSetupFields() {
    const activeCount = Number(refs.playerCount.value || 4);
    refs.playerSlots.forEach((slot) => {
      const index = Number(slot.getAttribute("data-player-slot"));
      slot.classList.toggle("is-hidden", index > activeCount);
    });
  }

  function startNewGame(confirmIfRunning) {
    if (confirmIfRunning && state.players.length > 0 && state.phase !== "setup") {
      const shouldReset = window.confirm("Bat dau van moi? Tien trinh hien tai se mat.");
      if (!shouldReset) {
        return;
      }
    }

    const config = readSetupConfig();
    state = createEmptyState();
    state.players = config.players.map((name, index) => createPlayerState(name, index, config.startingCash));
    state.phase = state.players[0]?.inJail ? "jail" : "await_roll";
    state.log = [];
    state.round = 1;
    state.turnNumber = 1;
    state.selectedTileIndex = 0;
    logAction("Van moi", "Ban choi da san sang. " + state.players[0].name + " di truoc.");
    renderAll();
  }

  function readSetupConfig() {
    const playerCount = Math.max(2, Math.min(4, Number(refs.playerCount.value || 4)));
    const startingCash = Math.max(800, Number(refs.startingCash.value || 1500));
    const players = refs.playerInputs
      .slice(0, playerCount)
      .map((input, index) => sanitizeName(input.value, "Ty phu " + (index + 1)));
    return { playerCount, startingCash, players };
  }

  function createPlayerState(name, index, startingCash) {
    return {
      id: "p" + (index + 1),
      name,
      color: PLAYER_COLORS[index],
      money: startingCash,
      position: 0,
      bankrupt: false,
      inJail: false,
      jailTurns: 0,
      escapeCards: 0,
      consecutiveDoubles: 0
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
  }

  function renderBoard() {
    const tilesHtml = state.board.map((space) => renderBoardSpace(space)).join("");
    refs.board.innerHTML = tilesHtml + renderBoardCenter();
  }

  function renderBoardSpace(space) {
    const position = getBoardPosition(space.index);
    const groupMeta = GROUP_META[space.group] || GROUP_META.corner;
    const owner = getOwner(space);
    const ownerStyle = owner ? "--owner-color:" + owner.color + ";" : "";
    const selectedClass = state.selectedTileIndex === space.index ? " is-selected" : "";
    const ownedClass = owner ? " is-owned" : "";
    const houses = renderHouseDots(space);
    const tokens = renderTokenDots(space.index);
    const meta = renderSpaceMeta(space, owner);
    return `
      <button
        type="button"
        class="monopoly-space${selectedClass}${ownedClass}"
        data-space-index="${space.index}"
        style="grid-row:${position.row};grid-column:${position.col};--space-accent:${groupMeta.accent};${ownerStyle}">
        <span class="monopoly-space__band"></span>
        <span class="monopoly-space__type">${escapeHtml(groupMeta.label)}</span>
        <strong class="monopoly-space__name">${escapeHtml(space.name)}</strong>
        <span class="monopoly-space__meta">${meta}</span>
        <div class="monopoly-space__houses">${houses}</div>
        <div class="monopoly-space__tokens">${tokens}</div>
      </button>
    `;
  }

  function renderBoardCenter() {
    const currentPlayer = getCurrentPlayer();
    const dieA = state.lastDice ? state.lastDice.a : "-";
    const dieB = state.lastDice ? state.lastDice.b : "-";
    return `
      <div class="monopoly-board-center">
        <span class="monopoly-board-center__eyebrow">Local Monopoly</span>
        <h3 class="monopoly-board-center__title">${escapeHtml(currentPlayer ? currentPlayer.name : "Ban choi")}</h3>
        <p class="monopoly-board-center__subtitle">${escapeHtml(resolveInstructionText())}</p>
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
          <small>${escapeHtml(currentPlayer.bankrupt ? "Da pha san" : "Dang dung o " + getBoardSpace(currentPlayer.position).name)}</small>
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
  }

  function renderSelectedTile() {
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
    refs.players.innerHTML = state.players.map((player) => {
      const owned = ownedSpaces(player.id);
      const badges = [];
      if (state.players[state.currentPlayerIndex]?.id === player.id && !player.bankrupt) {
        badges.push('<span class="monopoly-player-badge">Dang luot</span>');
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
    const currentPlayer = getCurrentPlayer();
    const isDebtTurn = state.debt && currentPlayer && state.debt.playerId === currentPlayer.id;
    refs.rollBtn.disabled = !currentPlayer || currentPlayer.bankrupt || !["await_roll", "jail"].includes(state.phase);
    refs.buyBtn.disabled = !state.pendingPurchase || !currentPlayer || currentPlayer.money < getBoardSpace(state.pendingPurchase?.tileIndex || 0).price;
    refs.skipBuyBtn.disabled = !state.pendingPurchase;
    refs.endTurnBtn.disabled = !currentPlayer || currentPlayer.bankrupt || state.phase !== "await_end_turn";
    refs.payBailBtn.disabled = !currentPlayer || !currentPlayer.inJail || state.phase !== "jail" || currentPlayer.money < JAIL_BAIL;
    refs.useCardBtn.disabled = !currentPlayer || !currentPlayer.inJail || state.phase !== "jail" || currentPlayer.escapeCards <= 0;
    refs.bankruptcyBtn.disabled = !isDebtTurn;
    refs.finishBtn.disabled = activePlayers().length <= 1 || state.phase === "ended";
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
    if (!currentPlayer || currentPlayer.bankrupt || state.phase === "ended") {
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
    if (!currentPlayer) {
      return "Chon setup va bat dau van moi.";
    }
    if (state.phase === "ended") {
      const winner = state.winnerId ? getPlayerById(state.winnerId) : null;
      return winner ? winner.name + " dan dau va van dau da ket thuc." : "Van dau da ket thuc.";
    }
    if (state.debt && state.debt.playerId === currentPlayer.id) {
      return currentPlayer.name + " dang can xoay von de tra no.";
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
    return Boolean(currentPlayer && tile && tile.ownerId === currentPlayer.id && !tile.mortgaged && tile.houses === 0);
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
    if (!state.pendingPurchase) {
      return;
    }
    const tile = getBoardSpace(state.pendingPurchase.tileIndex);
    logAction("Bo qua", getCurrentPlayer().name + " bo qua quyen mua " + tile.name + ".");
    state.pendingPurchase = null;
    finishStepAfterResolution();
  }

  function handleEndTurn() {
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
    if (passedGo) {
      player.money += PASS_GO_AMOUNT;
      logAction("Qua GO", player.name + " nhan " + formatMoney(PASS_GO_AMOUNT) + " khi di qua GO.");
    }
    player.position = destination;
    state.selectedTileIndex = destination;
    logAction("Di chuyen", player.name + " den " + getBoardSpace(destination).name + ". " + reason);
    resolveLanding(player, getBoardSpace(destination));
  }

  function movePlayerTo(player, destination, reason, collectGo) {
    const passedGo = collectGo || destination < player.position;
    if (passedGo) {
      player.money += PASS_GO_AMOUNT;
      logAction("Qua GO", player.name + " nhan " + formatMoney(PASS_GO_AMOUNT) + " khi di qua GO.");
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
});
