(function () {
    const historyApi = window.CaroHistory || {};
    const ui = window.CaroUi || {};

    const state = {
        deck: [],
        dealer: {
            name: "Dealer",
            hand: [],
            score: 0
        },
        player: {
            seat: 3,
            name: "You",
            balance: 1000,
            bet: 0,
            hand: [],
            score: 0,
            status: "Waiting",
            isTurn: false
        },
        history: [],
        betStack: [],
        lastResult: "Waiting",
        roundActive: false,
        historyRecorded: false,
        historyMatchCode: ""
    };

    const els = {};

    document.addEventListener("DOMContentLoaded", () => {
        bindEls();
        bindActions();
        createDeck();
        syncRoundState();
    });

    function bindEls() {
        els.dealerArea = document.getElementById("dealer-area");
        els.playerSeats = document.getElementById("player-seats");
        els.notificationArea = document.getElementById("notification-area");
        els.resultHistory = document.querySelector("#result-history .history-items");
        els.currentBetAmount = document.getElementById("current-bet-amount");
        els.balanceValue = document.getElementById("bot-balance-value");
        els.betValue = document.getElementById("bot-bet-value");
        els.lastResultValue = document.getElementById("bot-last-result-value");
        els.hitBtn = document.getElementById("btn-hit");
        els.standBtn = document.getElementById("btn-stand");
        els.doubleBtn = document.getElementById("btn-double");
        els.dealBtn = document.getElementById("btn-deal");
        els.clearBetBtn = document.getElementById("btn-clear-bet");
        els.undoBetBtn = document.getElementById("btn-undo-bet");
        els.chips = Array.from(document.querySelectorAll(".chip[data-value]"));
    }

    function bindActions() {
        els.hitBtn?.addEventListener("click", hit);
        els.standBtn?.addEventListener("click", stand);
        els.doubleBtn?.addEventListener("click", doubleDown);
        els.dealBtn?.addEventListener("click", deal);
        els.clearBetBtn?.addEventListener("click", clearBet);
        els.undoBetBtn?.addEventListener("click", undoBet);
        els.chips.forEach((chip) => {
            chip.addEventListener("click", () => addBet(Number(chip.dataset.value || 0)));
        });
    }

    function createDeck() {
        const suits = ["spades", "hearts", "diams", "clubs"];
        const ranks = ["A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"];
        state.deck = [];
        for (const suit of suits) {
            for (const rank of ranks) {
                state.deck.push({ suit, rank });
            }
        }
        shuffleDeck();
    }

    function shuffleDeck() {
        for (let i = state.deck.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            const tmp = state.deck[i];
            state.deck[i] = state.deck[j];
            state.deck[j] = tmp;
        }
    }

    function drawCard() {
        if (!state.deck.length) {
            createDeck();
        }
        return state.deck.pop();
    }

    function getCardValue(card) {
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

    function getHandValue(hand) {
        let total = 0;
        let aces = 0;
        for (const card of hand) {
            total += getCardValue(card);
            if (card.rank === "A") {
                aces += 1;
            }
        }
        while (total > 21 && aces > 0) {
            total -= 10;
            aces -= 1;
        }
        return total;
    }

    function addBet(value) {
        const chipValue = Math.max(0, Number(value || 0));
        if (!chipValue || state.roundActive) {
            return;
        }
        if (state.player.bet + chipValue > state.player.balance) {
            setNotification("Khong du so du de dat them.");
            ui.toast?.("Khong du so du de dat them.", { type: "warning" });
            return;
        }
        state.player.bet += chipValue;
        state.betStack.push(chipValue);
        syncRoundState();
    }

    function clearBet() {
        if (state.roundActive) {
            return;
        }
        state.player.bet = 0;
        state.betStack = [];
        syncRoundState();
    }

    function undoBet() {
        if (state.roundActive) {
            return;
        }
        const lastChip = Number(state.betStack.pop() || 0);
        if (lastChip > 0 && state.player.bet >= lastChip) {
            state.player.bet -= lastChip;
        } else {
            state.player.bet = Math.max(0, state.player.bet - 10);
        }
        syncRoundState();
    }

    function deal() {
        if (state.roundActive) {
            return;
        }
        if (state.player.bet <= 0) {
            setNotification("Hay dat cuoc truoc khi chia bai.");
            ui.toast?.("Hay dat cuoc truoc khi chia bai.", { type: "warning" });
            return;
        }

        if (state.player.bet > state.player.balance) {
            setNotification("Tien cuoc lon hon so du hien tai.");
            return;
        }

        if (state.deck.length < 10) {
            createDeck();
        }

        state.historyRecorded = false;
        state.historyMatchCode = newMatchCode();
        state.player.balance -= state.player.bet;
        state.player.hand = [drawCard(), drawCard()];
        state.dealer.hand = [drawCard(), drawCard()];
        state.player.score = getHandValue(state.player.hand);
        state.dealer.score = getHandValue(state.dealer.hand);
        state.player.status = "Your Turn";
        state.player.isTurn = true;
        state.roundActive = true;

        if (state.player.score === 21) {
            stand();
            return;
        }

        syncRoundState();
    }

    function hit() {
        if (!state.roundActive || !state.player.isTurn) {
            return;
        }
        state.player.hand.push(drawCard());
        state.player.score = getHandValue(state.player.hand);
        if (state.player.score > 21) {
            state.player.status = "Bust";
            state.player.isTurn = false;
            finishRound();
            return;
        }
        syncRoundState();
    }

    function stand() {
        if (!state.roundActive || !state.player.isTurn) {
            return;
        }
        state.player.isTurn = false;
        dealerTurn();
    }

    function doubleDown() {
        if (!state.roundActive || !state.player.isTurn) {
            return;
        }
        if (state.player.bet > state.player.balance) {
            setNotification("Khong du so du de gap doi cuoc.");
            return;
        }
        state.player.balance -= state.player.bet;
        state.player.bet *= 2;
        state.player.hand.push(drawCard());
        state.player.score = getHandValue(state.player.hand);
        if (state.player.score > 21) {
            state.player.status = "Bust";
            state.player.isTurn = false;
            finishRound();
            return;
        }
        state.player.isTurn = false;
        dealerTurn();
    }

    function dealerTurn() {
        while (getHandValue(state.dealer.hand) < 17) {
            state.dealer.hand.push(drawCard());
        }
        state.dealer.score = getHandValue(state.dealer.hand);
        finishRound();
    }

    function finishRound() {
        state.roundActive = false;
        state.player.isTurn = false;

        const playerScore = state.player.score;
        const dealerScore = state.dealer.score;
        let resultLabel = "Push";
        let outcome = "draw";

        if (playerScore > 21) {
            resultLabel = "Dealer Win";
            outcome = "loss";
        } else if (dealerScore > 21 || playerScore > dealerScore) {
            resultLabel = "Player Win";
            outcome = "win";
            state.player.balance += state.player.bet * 2;
        } else if (playerScore < dealerScore) {
            resultLabel = "Dealer Win";
            outcome = "loss";
        } else {
            state.player.balance += state.player.bet;
        }

        state.player.status = resultLabel;
        state.lastResult = resultLabel;
        state.history.unshift(resultLabel);
        if (state.history.length > 5) {
            state.history = state.history.slice(0, 5);
        }

        recordBotHistory(outcome);
        syncRoundState();
        state.player.bet = 0;
        state.betStack = [];
        syncRoundState();
    }

    function setNotification(text) {
        if (els.notificationArea) {
            els.notificationArea.textContent = text || "-";
        }
    }

    function syncRoundState() {
        renderDealer();
        renderPlayer();
        renderHistory();
        updateControls();
        syncChipHighlight();
        if (state.roundActive) {
            setNotification(state.player.isTurn ? "Your turn" : "Dealer turn");
        } else if (state.player.status && state.player.status !== "Waiting") {
            setNotification(state.player.status);
        } else {
            setNotification(state.player.bet > 0 ? "Nhan Deal de bat dau" : "Place your bet");
        }
        if (els.currentBetAmount) {
            els.currentBetAmount.textContent = formatCurrency(state.player.bet);
        }
        if (els.balanceValue) {
            els.balanceValue.textContent = formatCurrency(state.player.balance);
        }
        if (els.betValue) {
            els.betValue.textContent = formatCurrency(state.player.bet);
        }
        if (els.lastResultValue) {
            els.lastResultValue.textContent = state.lastResult;
        }
    }

    function createCardElement(card) {
        const cardEl = document.createElement("div");
        cardEl.className = "card";
        if (card.suit === "hearts" || card.suit === "diams") {
            cardEl.style.color = "#a91b1b";
        }
        const rank = document.createElement("span");
        rank.className = "rank";
        rank.textContent = card.rank;
        const suit = document.createElement("span");
        suit.className = "suit";
        suit.innerHTML = suitEntity(card.suit);
        cardEl.append(rank, suit);
        return cardEl;
    }

    function createHiddenCardElement() {
        const cardEl = document.createElement("div");
        cardEl.className = "card hidden";
        return cardEl;
    }

    function renderHand(hand, options = {}) {
        const hiddenIndexes = new Set(options.hiddenIndexes || []);
        const handContainer = document.createElement("div");
        handContainer.className = "cards-hand";
        hand.forEach((card, index) => {
            const cardEl = hiddenIndexes.has(index) ? createHiddenCardElement() : createCardElement(card);
            const angle = (index - (hand.length - 1) / 2) * 12;
            const translateY = Math.abs(index - (hand.length - 1) / 2) * 5;
            cardEl.style.transform = "rotate(" + angle + "deg) translateY(" + translateY + "px)";
            cardEl.style.zIndex = String(index);
            handContainer.appendChild(cardEl);
        });
        return handContainer;
    }

    function renderDealer() {
        if (!els.dealerArea) {
            return;
        }
        els.dealerArea.innerHTML = "";
        const hideHoleCard = state.roundActive && state.player.isTurn && state.dealer.hand.length > 1;
        const info = document.createElement("div");
        info.className = "dealer-info";
        info.textContent = state.dealer.name;
        const hand = renderHand(state.dealer.hand, {
            hiddenIndexes: hideHoleCard ? [1] : []
        });
        const score = document.createElement("div");
        score.className = "score-display";
        score.textContent = hideHoleCard
            ? dealerPreview()
            : String(state.dealer.score || 0);
        els.dealerArea.append(info, hand, score);
    }

    function renderPlayer() {
        if (!els.playerSeats) {
            return;
        }
        els.playerSeats.innerHTML = "";
        [1, 2, 3, 4, 5].forEach((seatNumber) => {
            const seat = document.createElement("div");
            const isPlayerSeat = seatNumber === state.player.seat;
            seat.className = "player-seat"
                + (isPlayerSeat && state.player.isTurn ? " is-turn" : "")
                + (isPlayerSeat ? "" : " is-empty");
            seat.dataset.seat = String(seatNumber);

            const content = document.createElement("div");
            content.className = "seat-content";

            const info = document.createElement("div");
            info.className = "player-info";
            info.textContent = isPlayerSeat ? state.player.name : ("Seat " + seatNumber);

            if (isPlayerSeat) {
                const balance = document.createElement("div");
                balance.className = "player-balance";
                balance.textContent = "Balance " + formatCurrency(state.player.balance);

                const hand = renderHand(state.player.hand);

                const bet = document.createElement("div");
                bet.className = "bet-chips";
                bet.textContent = state.player.bet > 0 ? formatCurrency(state.player.bet) : "No bet";

                const score = document.createElement("div");
                score.className = "score-display";
                score.textContent = state.player.score > 0 ? String(state.player.score) : "-";

                const status = document.createElement("div");
                status.className = "player-status";
                status.textContent = state.player.status || "Waiting";

                content.append(info, balance, hand, bet, score, status);
            } else {
                const waiting = document.createElement("div");
                waiting.className = "player-status";
                waiting.textContent = "Open table";
                content.append(info, waiting);
            }

            seat.appendChild(content);
            els.playerSeats.appendChild(seat);
        });
    }

    function renderHistory() {
        if (!els.resultHistory) {
            return;
        }
        els.resultHistory.innerHTML = state.history.length
            ? state.history.map((item) => "<div>" + escapeHtml(item) + "</div>").join("")
            : "<div>No hands yet</div>";
    }

    function updateControls() {
        const playerTurn = state.roundActive && state.player.isTurn;
        if (els.dealBtn) {
            els.dealBtn.disabled = playerTurn || state.player.bet <= 0;
        }
        if (els.hitBtn) {
            els.hitBtn.disabled = !playerTurn;
        }
        if (els.standBtn) {
            els.standBtn.disabled = !playerTurn;
        }
        if (els.doubleBtn) {
            els.doubleBtn.disabled = !playerTurn || state.player.bet > state.player.balance;
        }
        if (els.clearBetBtn) {
            els.clearBetBtn.disabled = state.roundActive || state.player.bet <= 0;
        }
        if (els.undoBetBtn) {
            els.undoBetBtn.disabled = state.roundActive || state.player.bet <= 0;
        }
        els.chips.forEach((chip) => {
            chip.disabled = state.roundActive;
            chip.classList.toggle("disabled", state.roundActive);
        });
    }

    function dealerPreview() {
        if (!state.dealer.hand.length) {
            return "0";
        }
        return String(getHandValue([state.dealer.hand[0]]) || 0) + "+?";
    }

    function syncChipHighlight() {
        const activeValue = String(state.betStack[state.betStack.length - 1] || "");
        els.chips.forEach((chip) => {
            chip.classList.toggle("is-last-chip", Boolean(activeValue) && chip.dataset.value === activeValue);
        });
    }

    function formatCurrency(value) {
        return new Intl.NumberFormat("en-US", {
            style: "currency",
            currency: "USD",
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(Number(value || 0));
    }

    function suitEntity(suit) {
        switch (String(suit || "").toLowerCase()) {
            case "hearts":
                return "&hearts;";
            case "diams":
                return "&diams;";
            case "clubs":
                return "&clubs;";
            case "spades":
            default:
                return "&spades;";
        }
    }

    function newMatchCode() {
        return typeof historyApi.newMatchCode === "function"
            ? historyApi.newMatchCode("blackjack-bot")
            : ("BOT-BLACKJACK-" + Date.now());
    }

    async function recordBotHistory(outcome) {
        if (state.historyRecorded || typeof historyApi.recordBotMatch !== "function") {
            return;
        }
        state.historyRecorded = true;
        try {
            await historyApi.recordBotMatch({
                gameCode: "blackjack",
                difficulty: "easy",
                outcome: outcome,
                totalMoves: state.player.hand.length + state.dealer.hand.length,
                firstPlayerRole: "player",
                matchCode: state.historyMatchCode || newMatchCode()
            });
        } catch (_) {
            state.historyRecorded = false;
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
