let currentSymbol = "X";
let board = Array.from({ length: 10 }, () => Array(10).fill(""));
let gameOver = false;
let winLine = [];
let lastMove = null;

const ui = window.CaroUi || {};

function notify(message, type) {
    const text = String(message || "");
    if (ui.toast) {
        ui.toast(text, { type: type || "info" });
    } else if (typeof window !== "undefined" && typeof window["alert"] === "function") {
        window["alert"](text);
    } else {
        console.log(text);
    }
}

function confirmSurrender(currentPlayer) {
    if (typeof ui.confirmAction === "function") {
        return ui.confirmAction({
            title: "Dau hang van Caro?",
            text: "Nguoi choi " + currentPlayer + " se thua ngay va doi thu duoc tinh la chien thang.",
            confirmText: "Dau hang",
            cancelText: "Tiep tuc choi",
            fallbackText: "Xac nhan dau hang cho nguoi choi " + currentPlayer + "?",
            danger: true
        });
    }
    return Promise.resolve(
        typeof window !== "undefined" && typeof window.confirm === "function"
            ? window.confirm(`Xac nhan dau hang cho nguoi choi ${currentPlayer}?`)
            : false
    );
}

// Tao ban co
document.addEventListener("DOMContentLoaded", function () {
    const boardContainer = document.getElementById("game-board");
    for (let i = 0; i < 10; i++) {
        const row = document.createElement("div");
        row.classList.add("row");

        for (let j = 0; j < 10; j++) {
            const cell = document.createElement("div");
            cell.classList.add("cell");
            cell.id = `cell-${i}-${j}`;
            cell.onclick = () => handleOfflineMove(i, j);
            row.appendChild(cell);
        }
        boardContainer.appendChild(row);
    }

    document.getElementById("offlineResetBtn")?.addEventListener("click", resetBoard);
    document.getElementById("offlineSurrenderBtn")?.addEventListener("click", surrenderCurrentPlayer);
    updateTurnDisplay();
});

function handleOfflineMove(x, y) {
    if (gameOver) return;
    if (board[x][y] !== "") return;

    board[x][y] = currentSymbol;
    const cell = document.getElementById(`cell-${x}-${y}`);
    if (cell) {
        cell.textContent = currentSymbol;
        cell.classList.add(currentSymbol === "X" ? "player-x" : "player-o");
    }
    setLastMove(x, y);

    const nextWinLine = findWinLine(x, y, currentSymbol);
    if (nextWinLine) {
        gameOver = true;
        winLine = nextWinLine;
        highlightWinLine();
        updateTurnDisplay(`${currentSymbol} thang! Bam "Choi lai" de tiep tuc.`);
        notify(`${currentSymbol} thang!`, "success");
        return;
    }

    if (isBoardFull()) {
        gameOver = true;
        winLine = [];
        updateTurnDisplay("Hoa co! Bam \"Choi lai\" de bat dau van moi.");
        notify("Hoa co!", "info");
        return;
    }

    currentSymbol = currentSymbol === "X" ? "O" : "X";
    updateTurnDisplay();
}

function updateTurnDisplay(text) {
    const turnStatus = document.getElementById("turnStatus");
    if (!turnStatus) return;
    turnStatus.textContent = text || `Luot cua: ${currentSymbol}`;
}

function resetBoard() {
    board = Array.from({ length: 10 }, () => Array(10).fill(""));
    currentSymbol = "X";
    gameOver = false;
    winLine = [];
    lastMove = null;

    for (let i = 0; i < 10; i++) {
        for (let j = 0; j < 10; j++) {
            const cell = document.getElementById(`cell-${i}-${j}`);
            if (!cell) continue;
            cell.textContent = "";
            cell.classList.remove("player-x", "player-o", "win", "last-move");
        }
    }
    updateTurnDisplay();
}

async function surrenderCurrentPlayer() {
    if (gameOver) return;
    const accepted = await confirmSurrender(currentSymbol);
    if (!accepted) return;

    gameOver = true;
    winLine = [];
    const loser = currentSymbol;
    const winner = currentSymbol === "X" ? "O" : "X";
    updateTurnDisplay(`${loser} dau hang! ${winner} thang. Bam "Choi lai" de tiep tuc.`);
    notify(`${loser} dau hang. ${winner} thang!`, "warning");
}

function isBoardFull() {
    for (let i = 0; i < 10; i++) {
        for (let j = 0; j < 10; j++) {
            if (board[i][j] === "") {
                return false;
            }
        }
    }
    return true;
}

function findWinLine(x, y, symbol) {
    const directions = [
        [1, 0],
        [0, 1],
        [1, 1],
        [1, -1]
    ];

    for (const [dx, dy] of directions) {
        const line = collectLine(x, y, symbol, dx, dy);
        if (line.length >= 5) {
            return line;
        }
    }

    return null;
}

function collectLine(x, y, symbol, dx, dy) {
    const line = [{ x, y }];

    for (let i = 1; i < 5; i++) {
        const nx = x + i * dx;
        const ny = y + i * dy;
        if (!inside(nx, ny)) break;
        if (board[nx][ny] !== symbol) break;
        line.push({ x: nx, y: ny });
    }

    for (let i = 1; i < 5; i++) {
        const nx = x - i * dx;
        const ny = y - i * dy;
        if (!inside(nx, ny)) break;
        if (board[nx][ny] !== symbol) break;
        line.unshift({ x: nx, y: ny });
    }

    return line;
}

function highlightWinLine() {
    for (const point of winLine) {
        document.getElementById(`cell-${point.x}-${point.y}`)?.classList.add("win");
    }
}

function setLastMove(x, y) {
    if (lastMove && inside(lastMove.x, lastMove.y)) {
        document.getElementById(`cell-${lastMove.x}-${lastMove.y}`)?.classList.remove("last-move");
    }
    if (!inside(x, y)) {
        lastMove = null;
        return;
    }
    lastMove = { x, y };
    document.getElementById(`cell-${x}-${y}`)?.classList.add("last-move");
}

function inside(x, y) {
    return x >= 0 && y >= 0 && x < 10 && y < 10;
}
