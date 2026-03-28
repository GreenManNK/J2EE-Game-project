(function () {
  const app = document.getElementById("blackjack-local-app");
  if (!app) {
    return;
  }

  const state = {
    seatCount: 2,
    selectedSeatIndex: 0,
    activePlayerIndex: -1,
    phase: "betting",
    deck: [],
    dealer: { hand: [] },
    players: [],
    history: [],
    motion: createMotionState()
  };

  const els = {
    seatCount: document.getElementById("blackjack-local-seat-count"),
    resetTable: document.getElementById("blackjack-local-reset-table"),
    startRound: document.getElementById("blackjack-local-start-round"),
    rebetAll: document.getElementById("blackjack-local-rebet-all"),
    dealerValue: document.getElementById("blackjack-local-dealer-value"),
    dealerCards: document.getElementById("blackjack-local-dealer-cards"),
    dealerBox: document.querySelector(".blackjack-local-dealer"),
    players: document.getElementById("blackjack-local-players"),
    status: document.getElementById("blackjack-local-status"),
    activeSeat: document.getElementById("blackjack-local-active-seat"),
    betInput: document.getElementById("blackjack-local-bet-input"),
    placeBet: document.getElementById("blackjack-local-place-bet"),
    clearBet: document.getElementById("blackjack-local-clear-bet"),
    hit: document.getElementById("blackjack-local-hit"),
    stand: document.getElementById("blackjack-local-stand"),
    double: document.getElementById("blackjack-local-double"),
    surrender: document.getElementById("blackjack-local-surrender"),
    history: document.getElementById("blackjack-local-history"),
    chips: Array.from(document.querySelectorAll(".blackjack-local-chip[data-chip]"))
  };

  document.addEventListener("DOMContentLoaded", init);

  function createMotionState() {
    return {
      selectedSeatIndex: 0,
      selectedSeatAt: 0,
      activeSeatIndex: -1,
      activeSeatAt: 0,
      drawOwner: "",
      drawAt: 0,
      dealAllAt: 0,
      historyAt: 0
    };
  }

  function init() {
    bindActions();
    resetTable();
  }

  function bindActions() {
    els.seatCount.addEventListener("change", function () {
      state.seatCount = Math.max(2, Math.min(4, Number.parseInt(els.seatCount.value, 10) || 2));
      resetTable();
    });
    els.resetTable.addEventListener("click", resetTable);
    els.startRound.addEventListener("click", startRound);
    els.rebetAll.addEventListener("click", applyRebetAll);
    els.placeBet.addEventListener("click", placeBetForSelectedSeat);
    els.clearBet.addEventListener("click", clearSelectedBet);
    els.hit.addEventListener("click", hitCurrentPlayer);
    els.stand.addEventListener("click", standCurrentPlayer);
    els.double.addEventListener("click", doubleCurrentPlayer);
    els.surrender.addEventListener("click", surrenderCurrentPlayer);
    els.chips.forEach(function (button) {
      button.addEventListener("click", function () {
        const amount = Number.parseInt(button.dataset.chip || "0", 10) || 0;
        els.betInput.value = String(Math.max(0, (Number.parseInt(els.betInput.value, 10) || 0) + amount));
      });
    });
  }

  function resetTable() {
    state.phase = "betting";
    state.activePlayerIndex = -1;
    state.selectedSeatIndex = 0;
    state.motion = createMotionState();
    state.dealer = { hand: [] };
    state.deck = [];
    state.players = Array.from({ length: state.seatCount }, function (_, index) {
      return createPlayer(index);
    });
    markSelectedSeat(0);
    els.betInput.value = "25";
    setStatus("Chon ghe va dat cuoc de chuan bi van moi.");
    render();
  }

  function createPlayer(index) {
    return {
      id: "P" + (index + 1),
      name: "Nguoi choi " + (index + 1),
      balance: 1000,
      bet: 0,
      lastBet: 0,
      hand: [],
      standing: false,
      surrendered: false,
      busted: false,
      roundResult: "Dang cho"
    };
  }

  function render() {
    app.dataset.phase = String(state.phase || "betting");
    renderDealer();
    renderPlayers();
    renderHistory();
    renderControls();
    renderSummary();
  }

  function renderDealer() {
    if (els.dealerBox) {
      els.dealerBox.classList.toggle("is-live-turn", state.phase === "dealer-turn");
    }
    els.dealerCards.innerHTML = "";
    const hideHoleCard = state.phase === "player-turn" && state.dealer.hand.length > 1;
    const visibleCards = state.dealer.hand.map(function (card, index) {
      return hideHoleCard && index === 1 ? null : card;
    });
    visibleCards.forEach(function (card, index) {
      const shouldAnimate = shouldAnimateDealerCard(index, visibleCards.length);
      els.dealerCards.appendChild(card ? buildCardElement(card, shouldAnimate) : buildBackCardElement(shouldAnimate));
    });
    const visibleValue = hideHoleCard && state.dealer.hand[0]
      ? handValue([state.dealer.hand[0]]) + "+?"
      : handValue(state.dealer.hand) + " diem";
    els.dealerValue.textContent = state.dealer.hand.length ? visibleValue : "0 diem";
  }

  function renderPlayers() {
    els.players.innerHTML = "";
    state.players.forEach(function (player, index) {
      const card = document.createElement("article");
      card.className = "blackjack-local-player"
        + (index === state.selectedSeatIndex ? " selected" : "")
        + (index === state.activePlayerIndex && state.phase === "player-turn" ? " active" : "")
        + (shouldPulseSelected(index) ? " motion-selected" : "")
        + (shouldPulseActive(index) ? " motion-active" : "");
      card.innerHTML = ""
        + "<div class=\"blackjack-local-player__head\">"
        + "<strong>" + escapeHtml(player.name) + "</strong>"
        + "<button type=\"button\" class=\"hub-portal-action\" data-seat-select=\"" + index + "\">Chon ghe</button>"
        + "</div>"
        + "<div class=\"blackjack-local-player__meta\"><span>So du: " + player.balance + "</span><span>Cuoc: " + player.bet + "</span></div>"
        + "<div class=\"blackjack-local-player__meta\"><span>Diem: " + handValue(player.hand) + "</span><span>Ket qua: " + escapeHtml(player.roundResult) + "</span></div>";
      const cardsEl = document.createElement("div");
      cardsEl.className = "blackjack-local-cards";
      player.hand.forEach(function (handCard, handIndex) {
        cardsEl.appendChild(buildCardElement(handCard, shouldAnimatePlayerCard(player, handIndex)));
      });
      card.appendChild(cardsEl);
      els.players.appendChild(card);
    });

    els.players.querySelectorAll("[data-seat-select]").forEach(function (button) {
      button.addEventListener("click", function () {
        const index = Number.parseInt(button.getAttribute("data-seat-select") || "0", 10) || 0;
        selectSeat(index);
      });
    });
  }

  function renderHistory() {
    els.history.innerHTML = "";
    if (!state.history.length) {
      const li = document.createElement("li");
      li.className = "blackjack-local-history__item";
      li.textContent = "Chua co van nao duoc giai quyet.";
      els.history.appendChild(li);
      return;
    }
    state.history.slice(0, 8).forEach(function (item, index) {
      const li = document.createElement("li");
      li.className = "blackjack-local-history__item" + (index === 0 && isRecent(state.motion.historyAt, 1200) ? " is-new" : "");
      li.textContent = item;
      els.history.appendChild(li);
    });
  }

  function renderControls() {
    const selected = selectedPlayer();
    const bettingPhase = state.phase === "betting";
    const actingPlayer = currentPlayer();
    const canAct = Boolean(actingPlayer && state.phase === "player-turn");
    const canDouble = canAct && actingPlayer.hand.length === 2 && actingPlayer.balance >= actingPlayer.bet && actingPlayer.bet > 0;
    const canSurrender = canAct && actingPlayer.hand.length === 2 && actingPlayer.bet > 0;

    els.placeBet.disabled = !bettingPhase;
    els.clearBet.disabled = !bettingPhase || !selected || selected.bet <= 0;
    els.rebetAll.disabled = !bettingPhase || !state.players.some(function (player) { return player.lastBet > 0 && player.balance >= player.lastBet; });
    els.startRound.disabled = !bettingPhase || !state.players.some(function (player) { return player.bet > 0; });
    els.betInput.disabled = !bettingPhase;
    els.chips.forEach(function (button) {
      button.disabled = !bettingPhase;
    });

    els.hit.disabled = !canAct;
    els.stand.disabled = !canAct;
    els.double.disabled = !canDouble;
    els.surrender.disabled = !canSurrender;
  }

  function renderSummary() {
    const selected = selectedPlayer();
    els.activeSeat.textContent = selected ? selected.name : "-";
  }

  function selectSeat(index) {
    if (!Number.isInteger(index) || index < 0 || index >= state.players.length) {
      return;
    }
    state.selectedSeatIndex = index;
    markSelectedSeat(index);
    render();
  }

  function placeBetForSelectedSeat() {
    if (state.phase !== "betting") {
      return;
    }
    const player = selectedPlayer();
    const amount = Math.max(0, Number.parseInt(els.betInput.value, 10) || 0);
    if (!player) {
      return;
    }
    if (player.bet > 0) {
      player.balance += player.bet;
      player.bet = 0;
    }
    if (amount <= 0) {
      player.roundResult = "Dang cho";
      setStatus(player.name + " da xoa cuoc hien tai.");
      render();
      return;
    }
    if (amount > player.balance) {
      setStatus(player.name + " khong du so du de dat cuoc nay.");
      render();
      return;
    }
    player.balance -= amount;
    player.bet = amount;
    player.lastBet = amount;
    player.roundResult = "Da dat cuoc";
    setStatus(player.name + " da dat cuoc " + amount + ".");
    render();
  }

  function clearSelectedBet() {
    if (state.phase !== "betting") {
      return;
    }
    const player = selectedPlayer();
    if (!player || player.bet <= 0) {
      return;
    }
    player.balance += player.bet;
    player.bet = 0;
    player.roundResult = "Dang cho";
    setStatus("Da xoa cuoc cua " + player.name + ".");
    render();
  }

  function applyRebetAll() {
    if (state.phase !== "betting") {
      return;
    }
    state.players.forEach(function (player) {
      if (player.bet > 0) {
        player.balance += player.bet;
        player.bet = 0;
      }
      if (player.lastBet > 0 && player.balance >= player.lastBet) {
        player.balance -= player.lastBet;
        player.bet = player.lastBet;
        player.roundResult = "Da dat lai";
      }
    });
    setStatus("Da ap lai muc cuoc hop le cho ca ban.");
    render();
  }

  function startRound() {
    if (state.phase !== "betting") {
      return;
    }
    const activePlayers = state.players.filter(function (player) { return player.bet > 0; });
    if (!activePlayers.length) {
      setStatus("Can it nhat 1 ghe dat cuoc truoc khi chia bai.");
      return;
    }
    state.phase = "player-turn";
    state.deck = createDeck();
    state.dealer.hand = [drawCard(), drawCard()];
    state.players.forEach(function (player) {
      player.hand = [];
      player.standing = false;
      player.surrendered = false;
      player.busted = false;
      player.roundResult = player.bet > 0 ? "Dang cho luot" : "Khong vao van";
      if (player.bet > 0) {
        player.hand.push(drawCard(), drawCard());
        if (isNaturalBlackjack(player.hand)) {
          player.standing = true;
          player.roundResult = "Blackjack";
        }
      }
    });
    markDraw("all", true);

    if (isNaturalBlackjack(state.dealer.hand)) {
      settleRound("Dealer blackjack tu nhien.");
      return;
    }

    state.activePlayerIndex = findNextActivePlayer(-1);
    if (state.activePlayerIndex < 0) {
      runDealerTurn();
      return;
    }
    state.selectedSeatIndex = state.activePlayerIndex;
    markActiveSeat(state.activePlayerIndex);
    setStatus("Den luot " + currentPlayer().name + ". Dua may cho ghe nay.");
    render();
  }

  function hitCurrentPlayer() {
    const player = currentPlayer();
    if (!player) {
      return;
    }
    player.hand.push(drawCard());
    markDraw(player.id, false);
    if (handValue(player.hand) > 21) {
      player.busted = true;
      player.standing = true;
      player.roundResult = "Bust";
      advanceTurn();
      return;
    }
    player.roundResult = "Dang rut bai";
    setStatus(player.name + " da hit. Co the tiep tuc hoac dung lai.");
    render();
  }

  function standCurrentPlayer() {
    const player = currentPlayer();
    if (!player) {
      return;
    }
    player.standing = true;
    player.roundResult = "Stand";
    advanceTurn();
  }

  function doubleCurrentPlayer() {
    const player = currentPlayer();
    if (!player || player.hand.length !== 2 || player.balance < player.bet || player.bet <= 0) {
      return;
    }
    player.balance -= player.bet;
    player.bet *= 2;
    player.hand.push(drawCard());
    markDraw(player.id, false);
    if (handValue(player.hand) > 21) {
      player.busted = true;
      player.roundResult = "Bust";
    } else {
      player.roundResult = "Double";
    }
    player.standing = true;
    advanceTurn();
  }

  function surrenderCurrentPlayer() {
    const player = currentPlayer();
    if (!player || player.hand.length !== 2 || player.bet <= 0) {
      return;
    }
    player.balance += Math.floor(player.bet / 2);
    player.bet = 0;
    player.surrendered = true;
    player.standing = true;
    player.roundResult = "Surrender";
    advanceTurn();
  }

  function advanceTurn() {
    const nextIndex = findNextActivePlayer(state.activePlayerIndex);
    if (nextIndex < 0) {
      runDealerTurn();
      return;
    }
    state.activePlayerIndex = nextIndex;
    state.selectedSeatIndex = nextIndex;
    markActiveSeat(nextIndex);
    markSelectedSeat(nextIndex);
    setStatus("Den luot " + currentPlayer().name + ". Dua may cho ghe nay.");
    render();
  }

  function runDealerTurn() {
    state.phase = "dealer-turn";
    while (handValue(state.dealer.hand) < 17) {
      state.dealer.hand.push(drawCard());
    }
    markDraw("dealer", false);
    settleRound("Dealer da ket thuc luot rut bai.");
  }

  function settleRound(prefix) {
    const dealerValue = handValue(state.dealer.hand);
    const dealerNatural = isNaturalBlackjack(state.dealer.hand);
    const winners = [];

    state.players.forEach(function (player) {
      const playerValue = handValue(player.hand);
      const natural = isNaturalBlackjack(player.hand);
      if (player.surrendered) {
        player.roundResult = "Surrender";
        return;
      }
      if (player.bet <= 0 && player.hand.length === 0) {
        return;
      }
      if (dealerNatural && natural) {
        player.balance += player.bet;
        player.bet = 0;
        player.roundResult = "Push";
        return;
      }
      if (natural && !dealerNatural) {
        player.balance += Math.floor((player.bet * 5) / 2);
        player.bet = 0;
        player.roundResult = "Blackjack";
        winners.push(player.name);
        return;
      }
      if (dealerNatural) {
        player.bet = 0;
        player.roundResult = "Thua dealer";
        return;
      }
      if (player.busted || playerValue > 21) {
        player.bet = 0;
        player.roundResult = "Bust";
        return;
      }
      if (dealerValue > 21 || playerValue > dealerValue) {
        player.balance += player.bet * 2;
        player.bet = 0;
        player.roundResult = "Thang";
        winners.push(player.name);
        return;
      }
      if (playerValue < dealerValue) {
        player.bet = 0;
        player.roundResult = "Thua dealer";
        return;
      }
      player.balance += player.bet;
      player.bet = 0;
      player.roundResult = "Push";
    });

    state.phase = "round-over";
    state.activePlayerIndex = -1;
    const roundLabel = winners.length ? ("Nguoi thang: " + winners.join(", ")) : "Dealer giu uu the";
    state.history.unshift(prefix + " " + roundLabel);
    markHistory();
    setStatus(prefix + " " + roundLabel + ". Bam Dat lai ca ban hoac dat cuoc moi.");
    render();
    state.phase = "betting";
  }

  function markSelectedSeat(index) {
    state.motion.selectedSeatIndex = index;
    state.motion.selectedSeatAt = Date.now();
  }

  function markActiveSeat(index) {
    state.motion.activeSeatIndex = index;
    state.motion.activeSeatAt = Date.now();
  }

  function markDraw(owner, dealAll) {
    state.motion.drawOwner = String(owner || "");
    state.motion.drawAt = Date.now();
    if (dealAll) {
      state.motion.dealAllAt = state.motion.drawAt;
    }
  }

  function markHistory() {
    state.motion.historyAt = Date.now();
  }

  function isRecent(timestamp, windowMs) {
    return Number(timestamp || 0) > 0 && (Date.now() - Number(timestamp)) < windowMs;
  }

  function shouldPulseSelected(index) {
    return index === state.motion.selectedSeatIndex && isRecent(state.motion.selectedSeatAt, 900);
  }

  function shouldPulseActive(index) {
    return index === state.motion.activeSeatIndex && isRecent(state.motion.activeSeatAt, 1200);
  }

  function shouldAnimatePlayerCard(player, handIndex) {
    if (!player) {
      return false;
    }
    if (isRecent(state.motion.dealAllAt, 900) && state.phase === "player-turn") {
      return true;
    }
    return isRecent(state.motion.drawAt, 820)
      && state.motion.drawOwner === player.id
      && handIndex === (player.hand.length - 1);
  }

  function shouldAnimateDealerCard(index, totalCards) {
    if (isRecent(state.motion.dealAllAt, 900) && state.phase === "player-turn") {
      return true;
    }
    return isRecent(state.motion.drawAt, 820)
      && state.motion.drawOwner === "dealer"
      && index === (totalCards - 1);
  }

  function findNextActivePlayer(currentIndex) {
    for (let step = 1; step <= state.players.length; step += 1) {
      const index = (currentIndex + step + state.players.length) % state.players.length;
      const player = state.players[index];
      if (!player || player.bet <= 0 || player.standing || player.surrendered || player.busted) {
        continue;
      }
      return index;
    }
    return -1;
  }

  function currentPlayer() {
    if (state.phase !== "player-turn" || state.activePlayerIndex < 0) {
      return null;
    }
    return state.players[state.activePlayerIndex] || null;
  }

  function selectedPlayer() {
    return state.players[state.selectedSeatIndex] || null;
  }

  function setStatus(message) {
    els.status.textContent = String(message || "");
  }

  function createDeck() {
    const suits = ["SPADES", "HEARTS", "DIAMONDS", "CLUBS"];
    const ranks = ["A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"];
    const deck = [];
    suits.forEach(function (suit) {
      ranks.forEach(function (rank) {
        deck.push({ suit: suit, rank: rank });
      });
    });
    for (let i = deck.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      const temp = deck[i];
      deck[i] = deck[j];
      deck[j] = temp;
    }
    return deck;
  }

  function drawCard() {
    if (!state.deck.length) {
      state.deck = createDeck();
    }
    return state.deck.pop();
  }

  function handValue(hand) {
    let total = 0;
    let aces = 0;
    hand.forEach(function (card) {
      total += cardValue(card);
      if (card.rank === "A") {
        aces += 1;
      }
    });
    while (total > 21 && aces > 0) {
      total -= 10;
      aces -= 1;
    }
    return total;
  }

  function cardValue(card) {
    if (!card) {
      return 0;
    }
    if (["10", "J", "Q", "K"].includes(card.rank)) {
      return 10;
    }
    if (card.rank === "A") {
      return 11;
    }
    return Number.parseInt(card.rank, 10) || 0;
  }

  function isNaturalBlackjack(hand) {
    return Array.isArray(hand) && hand.length === 2 && handValue(hand) === 21;
  }

  function buildCardElement(card, isFresh) {
    const el = document.createElement("div");
    const red = card.suit === "HEARTS" || card.suit === "DIAMONDS";
    el.className = "blackjack-local-card-face" + (red ? " red" : "") + (isFresh ? " is-fresh" : "");
    el.innerHTML = ""
      + "<div>" + escapeHtml(card.rank) + "</div>"
      + "<div class=\"blackjack-local-card-center\">" + suitSymbol(card.suit) + "</div>"
      + "<div style=\"text-align:right;\">" + escapeHtml(card.rank) + "</div>";
    return el;
  }

  function buildBackCardElement(isFresh) {
    const el = document.createElement("div");
    el.className = "blackjack-local-card-back" + (isFresh ? " is-fresh" : "");
    el.textContent = "?";
    return el;
  }

  function suitSymbol(suit) {
    switch (suit) {
      case "HEARTS":
        return "\u2665";
      case "DIAMONDS":
        return "\u2666";
      case "CLUBS":
        return "\u2663";
      default:
        return "\u2660";
    }
  }

  function escapeHtml(value) {
    return String(value || "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");
  }
})();
