(function () {
    const boot = window.ChessBoot || {};
    const historyApi = window.CaroHistory || {};
    const BOARD_SIZE = 8;
    const CHESS_STATS_KEY = "caroChessOfflineStats.v1";
    const GAME_STATS_CODE = "chess-offline";
    const FILES = ["a", "b", "c", "d", "e", "f", "g", "h"];
    const PIECE_ICONS = {
        wK: "â™”", wQ: "â™•", wR: "â™–", wB: "â™—", wN: "â™˜", wP: "â™™",
        bK: "â™š", bQ: "â™›", bR: "â™œ", bB: "â™", bN: "â™ž", bP: "â™Ÿ"
    };
    const PIECE_NAMES = {
        K: "Vua",
        Q: "Hau",
        R: "Xe",
        B: "Tuong",
        N: "Ma",
        P: "Tot"
    };
    Object.assign(PIECE_ICONS, {
        wK: "\u2654", wQ: "\u2655", wR: "\u2656", wB: "\u2657", wN: "\u2658", wP: "\u2659",
        bK: "\u265A", bQ: "\u265B", bR: "\u265C", bB: "\u265D", bN: "\u265E", bP: "\u265F"
    });

    const state = {
        board: [],
        turn: "w",
        selected: null,
        legalMoves: [],
        moveHistory: [],
        gameOver: false,
        resultText: "Dang choi",
        lastMove: null,
        botEnabled: Boolean(boot.botEnabled),
        botSide: (boot.botSide === "w" ? "w" : "b"),
        botDifficulty: String(boot.botDifficulty || "easy").toLowerCase() === "hard" ? "hard" : "easy",
        botThinking: false,
        snapshots: [],
        stats: {
            whiteWins: 0,
            blackWins: 0,
            draws: 0
        },
        historyMatchCode: "",
        historyRecorded: false
    };

    const PIECE_VALUES = {
        P: 10,
        N: 30,
        B: 30,
        R: 50,
        Q: 90,
        K: 900
    };

    let els = {};

    document.addEventListener("DOMContentLoaded", async () => {
        els = {
            board: document.getElementById("chessBoard"),
            turnStatus: document.getElementById("chessTurnStatus"),
            gameStatus: document.getElementById("chessGameStatus"),
            resultLabel: document.getElementById("chessResultLabel"),
            moveCount: document.getElementById("chessMoveCount"),
            currentColor: document.getElementById("chessCurrentColor"),
            moveLog: document.getElementById("chessMoveLog"),
            statsWld: document.getElementById("chessStatsWld"),
            undoBtn: document.getElementById("chessUndoBtn"),
            resetBtn: document.getElementById("chessResetBtn"),
            surrenderBtn: document.getElementById("chessSurrenderBtn"),
            boardCells: []
        };

        state.stats = await readStats();
        initBoardUi();
        els.undoBtn?.addEventListener("click", undoLastMove);
        els.resetBtn?.addEventListener("click", resetGame);
        els.surrenderBtn?.addEventListener("click", surrenderGame);
        document.addEventListener("keydown", handleHotkeys);
        updateStatsUi();
        resetGame();
    });

    function resetGame() {
        state.board = createInitialBoard();
        state.turn = "w";
        state.selected = null;
        state.legalMoves = [];
        state.moveHistory = [];
        state.gameOver = false;
        state.resultText = "Dang choi";
        state.lastMove = null;
        state.botThinking = false;
        state.snapshots = [];
        state.historyRecorded = false;
        state.historyMatchCode = newMatchCode();
        renderAll();
        maybeQueueBotMove();
    }

    function newMatchCode() {
        return typeof historyApi.newMatchCode === "function"
            ? historyApi.newMatchCode("chess-bot-" + state.botDifficulty)
            : ("BOT-CHESS-" + Date.now());
    }

    function normalizeStats(raw) {
        const source = raw && typeof raw === "object" ? raw : {};
        return {
            whiteWins: Math.max(0, Number.parseInt(String(source.whiteWins || 0), 10) || 0),
            blackWins: Math.max(0, Number.parseInt(String(source.blackWins || 0), 10) || 0),
            draws: Math.max(0, Number.parseInt(String(source.draws || 0), 10) || 0)
        };
    }

    function hasAnyStats(stats) {
        const safe = normalizeStats(stats);
        return safe.whiteWins > 0 || safe.blackWins > 0 || safe.draws > 0;
    }

    function mergeStats(primary, secondary) {
        const first = normalizeStats(primary);
        const second = normalizeStats(secondary);
        return {
            whiteWins: Math.max(first.whiteWins, second.whiteWins),
            blackWins: Math.max(first.blackWins, second.blackWins),
            draws: Math.max(first.draws, second.draws)
        };
    }

    function currentSessionUserId() {
        const current = window.CaroUser?.get?.();
        const userId = current && current.userId ? String(current.userId).trim() : "";
        return userId || null;
    }

    function readStatsFromStorage() {
        try {
            const raw = JSON.parse(window.localStorage.getItem(CHESS_STATS_KEY) || "{}");
            return normalizeStats(raw);
        } catch (_) {
            return normalizeStats({});
        }
    }

    function writeStatsToStorage(stats) {
        try {
            window.localStorage.setItem(CHESS_STATS_KEY, JSON.stringify(normalizeStats(stats)));
        } catch (_) {
        }
    }

    async function readStats() {
        const localStats = readStatsFromStorage();
        const userId = currentSessionUserId();
        const accountStats = window.CaroAccountStats;
        if (!userId || !accountStats || typeof accountStats.get !== "function") {
            return localStats;
        }

        try {
            const remoteRaw = await accountStats.get(GAME_STATS_CODE);
            const remoteStats = normalizeStats(remoteRaw);
            const merged = mergeStats(remoteStats, localStats);
            if (hasAnyStats(localStats) && typeof accountStats.save === "function") {
                const saved = await accountStats.save(GAME_STATS_CODE, merged, true);
                if (saved) {
                    window.localStorage.removeItem(CHESS_STATS_KEY);
                }
            }
            return merged;
        } catch (_) {
            return localStats;
        }
    }

    function writeStats() {
        const normalized = normalizeStats(state.stats);
        state.stats = normalized;

        const userId = currentSessionUserId();
        const accountStats = window.CaroAccountStats;
        if (userId && accountStats && typeof accountStats.save === "function") {
            accountStats.save(GAME_STATS_CODE, normalized, true)
                .then((saved) => {
                    if (saved) {
                        window.localStorage.removeItem(CHESS_STATS_KEY);
                        return;
                    }
                    writeStatsToStorage(normalized);
                })
                .catch(() => {
                    writeStatsToStorage(normalized);
                });
            return;
        }

        writeStatsToStorage(normalized);
    }

    function updateStatsUi() {
        if (!els.statsWld) {
            return;
        }
        els.statsWld.textContent = String(state.stats.whiteWins) + "/" + String(state.stats.blackWins) + "/" + String(state.stats.draws);
    }

    function recordOutcome(outcome) {
        if (outcome === "w") {
            state.stats.whiteWins += 1;
        } else if (outcome === "b") {
            state.stats.blackWins += 1;
        } else {
            state.stats.draws += 1;
        }
        writeStats();
        updateStatsUi();
    }

    async function recordBotHistory(outcome) {
        if (!state.botEnabled || state.historyRecorded || typeof historyApi.recordBotMatch !== "function") {
            return;
        }
        state.historyRecorded = true;
        try {
            await historyApi.recordBotMatch({
                gameCode: "chess",
                difficulty: state.botDifficulty,
                outcome: outcome,
                totalMoves: state.moveHistory.length,
                firstPlayerRole: "player",
                matchCode: state.historyMatchCode || newMatchCode()
            });
        } catch (_) {
            state.historyRecorded = false;
        }
    }

    function cloneMove(move) {
        if (!move) {
            return null;
        }
        return {
            from: move.from ? { ...move.from } : null,
            to: move.to ? { ...move.to } : null
        };
    }

    function captureSnapshot() {
        return {
            board: cloneBoard(state.board),
            turn: state.turn,
            moveHistory: state.moveHistory.slice(),
            gameOver: state.gameOver,
            resultText: state.resultText,
            lastMove: cloneMove(state.lastMove),
            gameStatusText: els.gameStatus ? String(els.gameStatus.textContent || "") : ""
        };
    }

    function restoreSnapshot(snapshot) {
        if (!snapshot) {
            return;
        }
        state.board = cloneBoard(snapshot.board || createInitialBoard());
        state.turn = snapshot.turn === "b" ? "b" : "w";
        state.moveHistory = Array.isArray(snapshot.moveHistory) ? snapshot.moveHistory.slice() : [];
        state.gameOver = !!snapshot.gameOver;
        state.resultText = String(snapshot.resultText || "Dang choi");
        state.lastMove = cloneMove(snapshot.lastMove);
        state.selected = null;
        state.legalMoves = [];
        state.botThinking = false;
        renderAll();
        setGameStatus(snapshot.gameStatusText || "Da quay lai nuoc truoc");
    }

    function undoLastMove() {
        if (state.botThinking) {
            setGameStatus("Bot dang suy nghi...");
            return;
        }
        if (!state.snapshots.length) {
            setGameStatus("Khong co nuoc de undo");
            return;
        }
        const snapshot = state.snapshots.pop();
        restoreSnapshot(snapshot);
    }

    function createInitialBoard() {
        return [
            ["bR", "bN", "bB", "bQ", "bK", "bB", "bN", "bR"],
            ["bP", "bP", "bP", "bP", "bP", "bP", "bP", "bP"],
            [null, null, null, null, null, null, null, null],
            [null, null, null, null, null, null, null, null],
            [null, null, null, null, null, null, null, null],
            [null, null, null, null, null, null, null, null],
            ["wP", "wP", "wP", "wP", "wP", "wP", "wP", "wP"],
            ["wR", "wN", "wB", "wQ", "wK", "wB", "wN", "wR"]
        ];
    }

    function initBoardUi() {
        if (!els.board) return;
        els.board.innerHTML = "";
        for (let r = 0; r < BOARD_SIZE; r++) {
            for (let c = 0; c < BOARD_SIZE; c++) {
                const btn = document.createElement("button");
                btn.type = "button";
                btn.className = "chess-cell " + (((r + c) % 2 === 0) ? "light" : "dark");
                btn.dataset.row = String(r);
                btn.dataset.col = String(c);
                btn.setAttribute("role", "gridcell");
                btn.addEventListener("click", () => onCellClick(r, c));
                els.board.appendChild(btn);
            }
        }
        els.boardCells = Array.from(els.board.querySelectorAll(".chess-cell"));
    }

    function onCellClick(row, col) {
        if (state.gameOver) {
            return;
        }
        if (isBotTurn() || state.botThinking) {
            setGameStatus("Dang den luot bot...");
            return;
        }

        const piece = state.board[row][col];
        const selected = state.selected;
        const clickedOwnPiece = piece && piece[0] === state.turn;

        if (selected) {
            const legal = state.legalMoves.find(m => m.to.row === row && m.to.col === col);
            if (legal) {
                applyMove(legal);
                return;
            }
        }

        if (clickedOwnPiece) {
            selectSquare(row, col);
        } else {
            state.selected = null;
            state.legalMoves = [];
            renderBoard();
            updateStatusText();
        }
    }

    function selectSquare(row, col) {
        const piece = state.board[row][col];
        if (!piece || piece[0] !== state.turn) {
            state.selected = null;
            state.legalMoves = [];
            renderBoard();
            updateStatusText();
            return;
        }
        state.selected = { row, col };
        state.legalMoves = getLegalMovesForPiece(state.board, row, col, state.turn);
        renderBoard();
        const squareName = toSquare(row, col);
        const pieceLabel = pieceLabelText(piece);
        if (state.legalMoves.length > 0) {
            setGameStatus("Dang chon " + pieceLabel + " tai " + squareName + " (" + state.legalMoves.length + " nuoc di hop le)");
        } else {
            setGameStatus(pieceLabel + " tai " + squareName + " khong co nuoc di hop le");
        }
    }

    function applyMove(move) {
        const fromPiece = state.board[move.from.row][move.from.col];
        if (!fromPiece) {
            return;
        }
        state.snapshots.push(captureSnapshot());
        const targetPiece = state.board[move.to.row][move.to.col];

        const nextBoard = cloneBoard(state.board);
        nextBoard[move.from.row][move.from.col] = null;
        let movedPiece = fromPiece;

        if (fromPiece[1] === "P" && (move.to.row === 0 || move.to.row === BOARD_SIZE - 1)) {
            movedPiece = fromPiece[0] + "Q";
            move.promotion = "Q";
        }

        nextBoard[move.to.row][move.to.col] = movedPiece;

        const notation = buildMoveNotation(fromPiece, move.from, move.to, targetPiece, move.promotion);
        state.board = nextBoard;
        state.lastMove = { from: { ...move.from }, to: { ...move.to } };
        state.moveHistory.push(notation);
        state.selected = null;
        state.legalMoves = [];

        const sideJustMoved = state.turn;
        state.turn = (state.turn === "w") ? "b" : "w";

        const opponentInCheck = isKingInCheck(state.board, state.turn);
        const opponentHasMove = hasAnyLegalMove(state.board, state.turn);
        const sideName = colorName(state.turn);

        if (!opponentHasMove && opponentInCheck) {
            state.gameOver = true;
            state.resultText = "Chieu het - " + colorName(sideJustMoved) + " thang";
            setGameStatus("Chieu het! " + colorName(sideJustMoved) + " thang.");
            recordOutcome(sideJustMoved);
            recordBotHistory(sideJustMoved === humanSide() ? "win" : "loss");
        } else if (!opponentHasMove) {
            state.gameOver = true;
            state.resultText = "Hoa";
            setGameStatus("The co - hoa.");
            recordOutcome("draw");
            recordBotHistory("draw");
        } else if (opponentInCheck) {
            state.resultText = "Dang choi";
            setGameStatus(sideName + " dang bi chieu!");
        } else {
            state.resultText = "Dang choi";
            setGameStatus("Nuoc di hop le. Den luot " + sideName + ".");
        }

        renderAll();
        maybeQueueBotMove();
    }

    function renderAll() {
        renderBoard();
        renderMoveLog();
        updateStatusText();
    }

    function renderBoard() {
        if (!els.board) return;
        const cells = els.boardCells || [];
        const selected = state.selected;
        const legalMap = new Map(state.legalMoves.map(m => [moveKey(m.to.row, m.to.col), m]));
        const checkedKing = findKing(state.board, state.turn);
        const kingInCheck = checkedKing && isKingInCheck(state.board, state.turn);

        cells.forEach((cell) => {
            const r = Number(cell.dataset.row);
            const c = Number(cell.dataset.col);
            const piece = state.board[r][c];
            cell.innerHTML = "";
            cell.classList.remove("selected", "legal", "capture", "last-move", "in-check");

            if (selected && selected.row === r && selected.col === c) {
                cell.classList.add("selected");
            }
            const legalMove = legalMap.get(moveKey(r, c));
            if (legalMove) {
                cell.classList.add("legal");
                if (legalMove.capture) {
                    cell.classList.add("capture");
                }
            }
            if (state.lastMove && (
                (state.lastMove.from.row === r && state.lastMove.from.col === c) ||
                (state.lastMove.to.row === r && state.lastMove.to.col === c)
            )) {
                cell.classList.add("last-move");
            }
            if (kingInCheck && checkedKing.row === r && checkedKing.col === c) {
                cell.classList.add("in-check");
            }

            if (piece) {
                const pieceEl = document.createElement("span");
                pieceEl.className = "chess-piece " + (piece[0] === "w" ? "white" : "black");
                pieceEl.textContent = PIECE_ICONS[piece] || piece;
                cell.appendChild(pieceEl);
            }

        });
    }

    function renderMoveLog() {
        if (!els.moveLog) return;
        els.moveLog.innerHTML = "";

        if (state.moveHistory.length === 0) {
            const li = document.createElement("li");
            li.className = "text-muted";
            li.textContent = "Chua co nuoc di";
            els.moveLog.appendChild(li);
            return;
        }

        for (let i = 0; i < state.moveHistory.length; i += 2) {
            const li = document.createElement("li");
            const whiteMove = state.moveHistory[i] || "";
            const blackMove = state.moveHistory[i + 1] || "";
            li.textContent = whiteMove + (blackMove ? " | " + blackMove : "");
            els.moveLog.appendChild(li);
        }
    }

    function updateStatusText() {
        const currentName = colorName(state.turn);
        if (els.turnStatus) {
            if (state.gameOver) {
                els.turnStatus.textContent = "Van dau ket thuc";
            } else if (state.botEnabled) {
                const turnText = isBotTurn() ? ("Luot: " + currentName + " (Bot)") : ("Luot: " + currentName + " (Ban)");
                els.turnStatus.textContent = state.botThinking ? "Bot dang suy nghi..." : turnText;
            } else {
                els.turnStatus.textContent = "Luot: " + currentName;
            }
        }
        if (els.resultLabel) {
            els.resultLabel.textContent = state.resultText;
        }
        if (els.moveCount) {
            els.moveCount.textContent = String(state.moveHistory.length);
        }
        if (els.currentColor) {
            els.currentColor.textContent = currentName;
        }
        if (els.surrenderBtn) {
            els.surrenderBtn.disabled = state.gameOver;
        }
        if (els.undoBtn) {
            els.undoBtn.disabled = state.snapshots.length === 0 || state.botThinking;
        }
        if (!els.gameStatus) {
            return;
        }
        if (state.gameOver && !els.gameStatus.textContent) {
            els.gameStatus.textContent = state.resultText;
        }
    }

    function surrenderGame() {
        if (state.gameOver) {
            return;
        }
        const surrenderSide = state.botEnabled ? humanSide() : state.turn;
        if (!surrenderSide) {
            return;
        }
        const surrenderLabel = colorName(surrenderSide);
        const winnerSide = surrenderSide === "w" ? "b" : "w";
        const winnerLabel = colorName(winnerSide);
        const messagePrefix = state.botEnabled ? "Ban" : surrenderLabel;
        if (!window.confirm(messagePrefix + " chac chan muon dau hang?")) {
            return;
        }
        state.snapshots.push(captureSnapshot());

        state.selected = null;
        state.legalMoves = [];
        state.gameOver = true;
        state.botThinking = false;
        state.resultText = winnerLabel + " thang";
        state.moveHistory.push(surrenderLabel + " dau hang");
        setGameStatus(messagePrefix + " da dau hang. " + winnerLabel + " thang.");
        recordOutcome(winnerSide);
        recordBotHistory(winnerSide === humanSide() ? "win" : "loss");
        renderAll();
    }

    function setGameStatus(text) {
        if (els.gameStatus) {
            els.gameStatus.textContent = text;
        }
    }

    function getLegalMovesForPiece(board, row, col, side) {
        const piece = board[row][col];
        if (!piece || piece[0] !== side) {
            return [];
        }
        const pseudo = getPseudoMoves(board, row, col, piece, false);
        return pseudo.filter((move) => {
            const testBoard = applyMoveOnBoard(board, move);
            return !isKingInCheck(testBoard, side);
        });
    }

    function hasAnyLegalMove(board, side) {
        for (let r = 0; r < BOARD_SIZE; r++) {
            for (let c = 0; c < BOARD_SIZE; c++) {
                const piece = board[r][c];
                if (piece && piece[0] === side && getLegalMovesForPiece(board, r, c, side).length > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    function collectAllLegalMoves(board, side) {
        const moves = [];
        for (let r = 0; r < BOARD_SIZE; r++) {
            for (let c = 0; c < BOARD_SIZE; c++) {
                const piece = board[r][c];
                if (!piece || piece[0] !== side) {
                    continue;
                }
                const pieceMoves = getLegalMovesForPiece(board, r, c, side);
                for (const move of pieceMoves) {
                    moves.push(move);
                }
            }
        }
        return moves;
    }

    function isKingInCheck(board, side) {
        const king = findKing(board, side);
        if (!king) {
            return false;
        }
        return isSquareAttacked(board, king.row, king.col, side === "w" ? "b" : "w");
    }

    function findKing(board, side) {
        for (let r = 0; r < BOARD_SIZE; r++) {
            for (let c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] === side + "K") {
                    return { row: r, col: c };
                }
            }
        }
        return null;
    }

    function isSquareAttacked(board, targetRow, targetCol, attackerSide) {
        for (let r = 0; r < BOARD_SIZE; r++) {
            for (let c = 0; c < BOARD_SIZE; c++) {
                const piece = board[r][c];
                if (!piece || piece[0] !== attackerSide) {
                    continue;
                }
                const attacks = getPseudoMoves(board, r, c, piece, true);
                if (attacks.some((m) => m.to.row === targetRow && m.to.col === targetCol)) {
                    return true;
                }
            }
        }
        return false;
    }

    function getPseudoMoves(board, row, col, piece, attacksOnly) {
        const side = piece[0];
        const type = piece[1];
        switch (type) {
            case "P":
                return pawnMoves(board, row, col, side, attacksOnly);
            case "N":
                return knightMoves(board, row, col, side);
            case "B":
                return slidingMoves(board, row, col, side, [[1, 1], [1, -1], [-1, 1], [-1, -1]]);
            case "R":
                return slidingMoves(board, row, col, side, [[1, 0], [-1, 0], [0, 1], [0, -1]]);
            case "Q":
                return slidingMoves(board, row, col, side, [[1, 1], [1, -1], [-1, 1], [-1, -1], [1, 0], [-1, 0], [0, 1], [0, -1]]);
            case "K":
                return kingMoves(board, row, col, side);
            default:
                return [];
        }
    }

    function pawnMoves(board, row, col, side, attacksOnly) {
        const moves = [];
        const dir = side === "w" ? -1 : 1;
        const startRow = side === "w" ? 6 : 1;

        for (const dc of [-1, 1]) {
            const tr = row + dir;
            const tc = col + dc;
            if (!inside(tr, tc)) continue;
            const target = board[tr][tc];
            if (attacksOnly) {
                moves.push(makeMove(row, col, tr, tc, Boolean(target)));
            } else if (target && target[0] !== side) {
                moves.push(makeMove(row, col, tr, tc, true));
            }
        }

        if (attacksOnly) {
            return moves;
        }

        const oneStepRow = row + dir;
        if (inside(oneStepRow, col) && !board[oneStepRow][col]) {
            moves.push(makeMove(row, col, oneStepRow, col, false));
            const twoStepRow = row + dir * 2;
            if (row === startRow && inside(twoStepRow, col) && !board[twoStepRow][col]) {
                moves.push(makeMove(row, col, twoStepRow, col, false));
            }
        }
        return moves;
    }

    function knightMoves(board, row, col, side) {
        const deltas = [
            [2, 1], [2, -1], [-2, 1], [-2, -1],
            [1, 2], [1, -2], [-1, 2], [-1, -2]
        ];
        const moves = [];
        for (const [dr, dc] of deltas) {
            const tr = row + dr;
            const tc = col + dc;
            if (!inside(tr, tc)) continue;
            const target = board[tr][tc];
            if (!target || target[0] !== side) {
                moves.push(makeMove(row, col, tr, tc, Boolean(target)));
            }
        }
        return moves;
    }

    function kingMoves(board, row, col, side) {
        const moves = [];
        for (let dr = -1; dr <= 1; dr++) {
            for (let dc = -1; dc <= 1; dc++) {
                if (dr === 0 && dc === 0) continue;
                const tr = row + dr;
                const tc = col + dc;
                if (!inside(tr, tc)) continue;
                const target = board[tr][tc];
                if (!target || target[0] !== side) {
                    moves.push(makeMove(row, col, tr, tc, Boolean(target)));
                }
            }
        }
        return moves;
    }

    function slidingMoves(board, row, col, side, directions) {
        const moves = [];
        for (const [dr, dc] of directions) {
            let tr = row + dr;
            let tc = col + dc;
            while (inside(tr, tc)) {
                const target = board[tr][tc];
                if (!target) {
                    moves.push(makeMove(row, col, tr, tc, false));
                } else {
                    if (target[0] !== side) {
                        moves.push(makeMove(row, col, tr, tc, true));
                    }
                    break;
                }
                tr += dr;
                tc += dc;
            }
        }
        return moves;
    }

    function applyMoveOnBoard(board, move) {
        const next = cloneBoard(board);
        const piece = next[move.from.row][move.from.col];
        next[move.from.row][move.from.col] = null;
        if (piece && piece[1] === "P" && (move.to.row === 0 || move.to.row === BOARD_SIZE - 1)) {
            next[move.to.row][move.to.col] = piece[0] + "Q";
        } else {
            next[move.to.row][move.to.col] = piece;
        }
        return next;
    }

    function cloneBoard(board) {
        return board.map((row) => row.slice());
    }

    function isBotTurn() {
        return state.botEnabled && !state.gameOver && state.turn === state.botSide;
    }

    function humanSide() {
        if (!state.botEnabled) {
            return "";
        }
        return state.botSide === "w" ? "b" : "w";
    }

    function maybeQueueBotMove() {
        if (!isBotTurn() || state.botThinking) {
            return;
        }
        state.botThinking = true;
        updateStatusText();
        const delay = Number(boot.botDelayMs) > 0 ? Number(boot.botDelayMs) : 350;
        window.setTimeout(() => {
            try {
                performBotMove();
            } finally {
                state.botThinking = false;
                updateStatusText();
            }
        }, delay);
    }

    function performBotMove() {
        if (!isBotTurn() || state.gameOver) {
            return;
        }
        const moves = collectAllLegalMoves(state.board, state.turn);
        if (!moves || moves.length === 0) {
            return;
        }
        const selectedMove = chooseBotMove(moves);
        if (!selectedMove) {
            return;
        }
        applyMove({
            from: { ...selectedMove.from },
            to: { ...selectedMove.to },
            capture: Boolean(selectedMove.capture),
            promotion: selectedMove.promotion || null
        });
    }

    function chooseBotMove(moves) {
        if (!Array.isArray(moves) || moves.length === 0) {
            return null;
        }
        if (state.botDifficulty !== "hard") {
            const captures = moves.filter((m) => m.capture);
            const source = captures.length > 0 ? captures : moves;
            const index = Math.floor(Math.random() * source.length);
            return source[index];
        }
        return chooseHardBotMove(moves);
    }

    function chooseHardBotMove(moves) {
        let bestScore = Number.NEGATIVE_INFINITY;
        let bestMoves = [];

        for (const move of moves) {
            const score = evaluateBotMove(move);
            if (score > bestScore) {
                bestScore = score;
                bestMoves = [move];
            } else if (Math.abs(score - bestScore) < 0.0001) {
                bestMoves.push(move);
            }
        }

        if (bestMoves.length === 0) {
            return moves[Math.floor(Math.random() * moves.length)];
        }
        return bestMoves[Math.floor(Math.random() * bestMoves.length)];
    }

    function evaluateBotMove(move) {
        const fromPiece = state.board[move.from.row][move.from.col];
        const captured = state.board[move.to.row][move.to.col];
        if (!fromPiece) {
            return -99999;
        }

        const nextBoard = applyMoveOnBoard(state.board, move);
        const botSide = state.turn;
        const oppSide = botSide === "w" ? "b" : "w";
        const movedPieceAfter = nextBoard[move.to.row][move.to.col];

        let score = 0;

        if (captured) {
            score += pieceValue(captured) * 10;
            if (captured[1] === "K") {
                score += 100000;
            }
        }

        if (isKingInCheck(nextBoard, oppSide)) {
            score += 35;
        }
        if (!hasAnyLegalMove(nextBoard, oppSide)) {
            if (isKingInCheck(nextBoard, oppSide)) {
                score += 50000;
            } else {
                score += 200;
            }
        }

        if (movedPieceAfter && isSquareAttacked(nextBoard, move.to.row, move.to.col, oppSide)) {
            score -= pieceValue(movedPieceAfter) * 6;
        }

        const centerDistance = Math.abs(3.5 - move.to.row) + Math.abs(3.5 - move.to.col);
        score += (7 - centerDistance);

        if (fromPiece[1] === "P") {
            const forwardProgress = botSide === "w" ? (7 - move.to.row) : move.to.row;
            score += forwardProgress * 0.8;
        }

        if (move.capture && movedPieceAfter && pieceValue(movedPieceAfter) < pieceValue(captured)) {
            score += 8;
        }

        return score;
    }

    function pieceValue(pieceCode) {
        if (!pieceCode || pieceCode.length < 2) {
            return 0;
        }
        return PIECE_VALUES[pieceCode[1]] || 0;
    }

    function makeMove(fromRow, fromCol, toRow, toCol, capture) {
        return {
            from: { row: fromRow, col: fromCol },
            to: { row: toRow, col: toCol },
            capture: Boolean(capture),
            promotion: null
        };
    }

    function buildMoveNotation(piece, from, to, capturedPiece, promotion) {
        const color = piece[0];
        const type = piece[1];
        const sideText = color === "w" ? "Trang" : "Den";
        const action = capturedPiece ? "an" : "di";
        let text = sideText + " " + PIECE_NAMES[type] + " " + action + " " + toSquare(from.row, from.col) + " -> " + toSquare(to.row, to.col);
        if (promotion) {
            text += " (phong Hau)";
        }
        return text;
    }

    function toSquare(row, col) {
        return FILES[col] + String(8 - row);
    }

    function pieceLabelText(piece) {
        return (piece[0] === "w" ? "Trang " : "Den ") + (PIECE_NAMES[piece[1]] || piece);
    }

    function colorName(side) {
        return side === "w" ? "Trang" : "Den";
    }

    function inside(row, col) {
        return row >= 0 && col >= 0 && row < BOARD_SIZE && col < BOARD_SIZE;
    }

    function moveKey(row, col) {
        return row + ":" + col;
    }

    function isTypingTarget(target) {
        if (!target || !(target instanceof Element)) {
            return false;
        }
        return !!target.closest("input, textarea, select, [contenteditable='true']");
    }

    function handleHotkeys(event) {
        if (!event || event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) {
            return;
        }
        if (isTypingTarget(event.target)) {
            return;
        }
        const key = String(event.key || "").toLowerCase();
        if (key === "u") {
            event.preventDefault();
            undoLastMove();
            return;
        }
        if (key === "r") {
            event.preventDefault();
            resetGame();
            return;
        }
        if (key === "s") {
            event.preventDefault();
            surrenderGame();
        }
    }
})();

