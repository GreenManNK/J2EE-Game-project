document.addEventListener('DOMContentLoaded', () => {
    // --- DỮ LIỆU MẪU (MOCK DATA) ---
    const mockData = {
        dealer: {
            name: "Dealer",
            hand: [],
            score: 0,
        },
        player: {
            seat: 3,
            name: "You",
            balance: 1000,
            bet: 0,
            hand: [],
            score: 0,
            status: "Waiting",
            isTurn: false,
        },
        history: []
    };

    // --- LẤY CÁC ELEMENT ---
    const dealerArea = document.getElementById('dealer-area');
    const playerSeatsContainer = document.getElementById('player-seats');
    const notificationArea = document.getElementById('notification-area');
    const resultHistory = document.querySelector('#result-history .history-items');
    const betAmountInput = document.getElementById('bet-amount');
    const betBtn = document.getElementById('bet-btn');
    const hitBtn = document.getElementById('hit-btn');
    const standBtn = document.getElementById('stand-btn');
    const doubleBtn = document.getElementById('double-btn');
    const dealBtn = document.getElementById('btn-deal');

    // --- LOGIC GAME ---
    let deck = [];

    function createDeck() {
        const suits = ['spades', 'hearts', 'diams', 'clubs'];
        const ranks = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'];
        deck = [];
        for (const suit of suits) {
            for (const rank of ranks) {
                deck.push({ suit, rank });
            }
        }
        shuffleDeck();
    }

    function shuffleDeck() {
        for (let i = deck.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [deck[i], deck[j]] = [deck[j], deck[i]];
        }
    }

    function getCardValue(card) {
        if (['J', 'Q', 'K', '10'].includes(card.rank)) return 10;
        if (card.rank === 'A') return 11;
        return parseInt(card.rank);
    }

    function getHandValue(hand) {
        let value = 0;
        let aceCount = 0;
        for (const card of hand) {
            value += getCardValue(card);
            if (card.rank === 'A') aceCount++;
        }
        while (value > 21 && aceCount > 0) {
            value -= 10;
            aceCount--;
        }
        return value;
    }

    function deal() {
        mockData.player.bet = parseInt(betAmountInput.value);
        mockData.player.balance -= mockData.player.bet;
        mockData.player.hand = [deck.pop(), deck.pop()];
        mockData.dealer.hand = [deck.pop(), deck.pop()];
        mockData.player.score = getHandValue(mockData.player.hand);
        mockData.dealer.score = getHandValue(mockData.dealer.hand);
        mockData.player.status = "Your Turn";
        mockData.player.isTurn = true;
        updateUI();
    }

    function hit() {
        mockData.player.hand.push(deck.pop());
        mockData.player.score = getHandValue(mockData.player.hand);
        if (mockData.player.score > 21) {
            mockData.player.status = "Bust";
            mockData.player.isTurn = false;
            endGame();
        }
        updateUI();
    }

    function stand() {
        mockData.player.isTurn = false;
        dealerTurn();
    }

    function dealerTurn() {
        while (getHandValue(mockData.dealer.hand) < 17) {
            mockData.dealer.hand.push(deck.pop());
        }
        mockData.dealer.score = getHandValue(mockData.dealer.hand);
        endGame();
    }

    function endGame() {
        const playerScore = mockData.player.score;
        const dealerScore = mockData.dealer.score;
        let result = "";

        if (playerScore > 21) {
            result = "Dealer Win";
        } else if (dealerScore > 21 || playerScore > dealerScore) {
            result = "Player Win";
            mockData.player.balance += mockData.player.bet * 2;
        } else if (playerScore < dealerScore) {
            result = "Dealer Win";
        } else {
            result = "Push";
            mockData.player.balance += mockData.player.bet;
        }
        mockData.history.unshift(result);
        if (mockData.history.length > 5) mockData.history.pop();
        updateUI();
    }

    // --- CÁC HÀM RENDER ---
    function updateUI() {
        renderDealer(mockData.dealer);
        renderPlayerSeats([mockData.player]);
        renderHistory(mockData.history);
        updateNotifications([mockData.player]);
        updateControls();
    }

    function updateControls() {
        const playerTurn = mockData.player.isTurn;
        dealBtn.disabled = playerTurn;
        hitBtn.disabled = !playerTurn;
        standBtn.disabled = !playerTurn;
        doubleBtn.disabled = !playerTurn;
    }

    // ... (các hàm render khác từ blackjack-script.js)

    // --- HÀM KHỞI TẠO ---
    function initGame() {
        createDeck();
        updateUI();
    }

    // Chạy game
    initGame();

    dealBtn.addEventListener('click', deal);
    hitBtn.addEventListener('click', hit);
    standBtn.addEventListener('click', stand);
});
