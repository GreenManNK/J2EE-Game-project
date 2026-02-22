let currentSymbol = "X";
let board = Array.from({ length: 10 }, () => Array(10).fill(""));

// Tạo bàn cờ
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
    updateTurnDisplay();
});

function handleOfflineMove(x, y) {
    if (board[x][y] !== "") return;

    board[x][y] = currentSymbol;
    const cell = document.getElementById(`cell-${x}-${y}`);
    cell.textContent = currentSymbol;
    cell.classList.add(currentSymbol === "X" ? "player-x" : "player-o");

    if (checkWin(x, y, currentSymbol)) {
        setTimeout(() => {
            alert(`${currentSymbol} thắng!`);
            resetBoard();
        }, 100);
        return;
    }

    currentSymbol = currentSymbol === "X" ? "O" : "X";
    updateTurnDisplay();
}

function updateTurnDisplay() {
    const turnStatus = document.getElementById("turnStatus");
    if (turnStatus) turnStatus.textContent = `Lượt của: ${currentSymbol}`;
}

function resetBoard() {
    board = Array.from({ length: 10 }, () => Array(10).fill(""));
    currentSymbol = "X";
    for (let i = 0; i < 10; i++) {
        for (let j = 0; j < 10; j++) {
            const cell = document.getElementById(`cell-${i}-${j}`);
            cell.textContent = "";
            cell.classList.remove("player-x", "player-o");
        }
    }
    updateTurnDisplay();
}

function checkWin(x, y, symbol) {
    return (
        countConsecutive(x, y, symbol, 1, 0) + countConsecutive(x, y, symbol, -1, 0) + 1 >= 5 || // dọc
        countConsecutive(x, y, symbol, 0, 1) + countConsecutive(x, y, symbol, 0, -1) + 1 >= 5 || // ngang
        countConsecutive(x, y, symbol, 1, 1) + countConsecutive(x, y, symbol, -1, -1) + 1 >= 5 || // chéo chính
        countConsecutive(x, y, symbol, 1, -1) + countConsecutive(x, y, symbol, -1, 1) + 1 >= 5    // chéo phụ
    );
}

function countConsecutive(x, y, symbol, dx, dy) {
    let count = 0;
    for (let i = 1; i < 5; i++) {
        const nx = x + i * dx;
        const ny = y + i * dy;
        if (nx < 0 || ny < 0 || nx >= 10 || ny >= 10) break;
        if (board[nx][ny] === symbol) count++;
        else break;
    }
    return count;
}
