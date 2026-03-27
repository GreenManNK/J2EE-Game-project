document.addEventListener('DOMContentLoaded', () => {
    // --- DỮ LIỆU MẪU (MOCK DATA) ---
    const mockData = {
        dealer: {
            name: "Dealer",
            hand: [
                { suit: 'spades', rank: 'K' },
                { suit: '?', rank: '?' } // Lá bài úp
            ],
            score: 10,
        },
        players: [
            {
                seat: 1,
                name: "Player 1",
                balance: 850,
                bet: 150,
                hand: [
                    { suit: 'hearts', rank: 'A' },
                    { suit: 'clubs', rank: 'K' }
                ],
                score: 21,
                status: "Blackjack!",
                isTurn: false,
            },
            {
                seat: 2,
                name: "Player 2",
                balance: 400,
                bet: 100,
                hand: [
                    { suit: 'diams', rank: '10' },
                    { suit: 'spades', rank: '7' },
                    { suit: 'hearts', rank: '8' }
                ],
                score: 25,
                status: "Bust",
                isTurn: false,
            },
            {
                seat: 3, // Ghế người chơi chính
                name: "You",
                balance: 1500,
                bet: 200,
                hand: [
                    { suit: 'clubs', rank: '9' },
                    { suit: 'hearts', rank: '7' }
                ],
                score: 16,
                status: "Your Turn",
                isTurn: true,
            },
            {
                seat: 4,
                name: "Player 4",
                balance: 2000,
                bet: 0,
                hand: [],
                score: 0,
                status: "Waiting",
                isTurn: false,
            },
            {
                seat: 5,
                name: null, // Ghế trống
                balance: 0,
                bet: 0,
                hand: [],
                score: 0,
                status: "Empty",
                isTurn: false,
            }
        ],
        history: ["Player Win", "Dealer Win", "Push", "Player Win"]
    };

    // --- LẤY CÁC ELEMENT ---
    const dealerArea = document.getElementById('dealer-area');
    const playerSeatsContainer = document.getElementById('player-seats');
    const notificationArea = document.getElementById('notification-area');
    const resultHistory = document.querySelector('#result-history .history-items');

    // --- CÁC HÀM RENDER ---

    function createCardElement(card) {
        const cardEl = document.createElement('div');
        cardEl.className = 'card';
        if (card.rank === '?') {
            cardEl.classList.add('hidden');
        } else {
            const rank = document.createElement('span');
            rank.className = 'rank';
            rank.textContent = card.rank;
            const suit = document.createElement('span');
            suit.className = 'suit';
            suit.innerHTML = `&${card.suit.toLowerCase()};`;
            cardEl.append(rank, suit);
        }
        return cardEl;
    }

    function renderHand(hand) {
        const handContainer = document.createElement('div');
        handContainer.className = 'cards-hand';
        hand.forEach((card, index) => {
            const cardEl = createCardElement(card);
            // Hiệu ứng quạt bài
            const angle = (index - (hand.length - 1) / 2) * 12;
            const translateY = Math.abs(index - (hand.length - 1) / 2) * 5;
            cardEl.style.transform = `translateX(-50%) rotate(${angle}deg) translateY(${translateY}px)`;
            cardEl.style.zIndex = index;
            handContainer.appendChild(cardEl);
        });
        return handContainer;
    }

    function renderDealer(dealerData) {
        dealerArea.innerHTML = `
            <div class="dealer-info">DEALER</div>
            ${renderHand(dealerData.hand).outerHTML}
            <div class="score-display">${dealerData.score}</div>
        `;
    }

    function renderPlayerSeats(players) {
        playerSeatsContainer.innerHTML = '';
        players.forEach(player => {
            const seatEl = document.createElement('div');
            seatEl.className = 'player-seat';
            seatEl.dataset.seat = player.seat;

            if (player.isTurn) seatEl.classList.add('is-turn');
            if (!player.name) seatEl.classList.add('is-empty');

            let content = '';
            if (player.name) {
                content = `
                    <div class="player-info">${player.name}</div>
                    <div class="player-balance">$${player.balance}</div>
                    ${renderHand(player.hand).outerHTML}
                    <div class="bet-chips">${player.bet > 0 ? '$' + player.bet : ''}</div>
                    <div class="score-display">${player.score > 0 ? player.score : ''}</div>
                    <div class="player-status">${player.status}</div>
                `;
            } else {
                content = `<div class="player-info">Empty Seat</div>`;
            }

            const seatContent = document.createElement('div');
            seatContent.className = 'seat-content';
            seatContent.innerHTML = content;

            // Đảo ngược transform xoay để nội dung thẳng đứng
            const rotation = seatEl.style.transform.match(/rotate\(([^)]+)\)/);
            if (rotation) {
                seatContent.style.transform = `rotate(${-parseFloat(rotation[1])}deg)`;
            }

            seatEl.appendChild(seatContent);
            playerSeatsContainer.appendChild(seatEl);
        });
    }

    function renderHistory(history) {
        resultHistory.innerHTML = history.map(item => `<div>${item}</div>`).join('');
    }

    function updateNotifications(players) {
        const currentPlayer = players.find(p => p.isTurn);
        if (currentPlayer) {
            notificationArea.textContent = `${currentPlayer.status}`;
        }
    }

    // --- HÀM KHỞI TẠO ---
    function initGame() {
        renderDealer(mockData.dealer);
        renderPlayerSeats(mockData.players);
        renderHistory(mockData.history);
        updateNotifications(mockData.players);
    }

    // Chạy game
    initGame();
});
