(function () {
    const boot = window.ChessOnlineBoot || {};
    const BOARD_SIZE = 8;
    const FILES = ["a", "b", "c", "d", "e", "f", "g", "h"];
    const appPath = (window.CaroUrl && typeof window.CaroUrl.path === "function")
        ? window.CaroUrl.path
        : (value) => value;
    const PIECE_ICONS = {
        wK: "♔", wQ: "♕", wR: "♖", wB: "♗", wN: "♘", wP: "♙",
        bK: "♚", bQ: "♛", bR: "♜", bB: "♝", bN: "♞", bP: "♟"
    };
    const PIECE_NAMES = {
        K: "Vua",
        Q: "Hau",
        R: "Xe",
        B: "Tuong",
        N: "Ma",
        P: "Tot"
    };

    const state = {
        roomId: String(boot.roomId || "").trim(),
        userId: String(boot.sessionUserId || "").trim(),
        displayName: String(boot.sessionDisplayName || "").trim(),
        avatarPath: String(boot.sessionAvatarPath || "").trim(),
        board: [],
        turn: "w",
        currentTurnUserId: "",
        selected: null,
        legalMoves: [],
        moveHistory: [],
        gameOver: false,
        resultText: "Dang cho doi doi thu",
        lastMove: null,
        players: [],
        myColor: "",
        connected: false,
        joining: false,
        client: null,
        botEnabled: Boolean(boot.botEnabled),
        botSide: (boot.botSide === "w" ? "w" : "b"),
        botDifficulty: String(boot.botDifficulty || "easy").toLowerCase() === "hard" ? "hard" : "easy",
        botThinking: false
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

    document.addEventListener("DOMContentLoaded", () => {
        els = {
            board: document.getElementById("chessBoard"),
            turnStatus: document.getElementById("chessTurnStatus"),
            gameStatus: document.getElementById("chessGameStatus"),
            resultLabel: document.getElementById("chessResultLabel"),
            moveCount: document.getElementById("chessMoveCount"),
            currentColor: document.getElementById("chessCurrentColor"),
            moveLog: document.getElementById("chessMoveLog"),
            resetBtn: document.getElementById("chessOnlineResetBtn"),
            leaveBtn: document.getElementById("chessOnlineLeaveBtn"),
            roomLabel: document.getElementById("chessOnlineRoomLabel"),
            userLabel: document.getElementById("chessOnlineUserLabel"),
            myColorLabel: document.getElementById("chessOnlineMyColor"),
            connectionStatus: document.getElementById("chessOnlineConnectionStatus"),
            roomStatus: document.getElementById("chessOnlineStatusText"),
            playersBox: document.getElementById("chessOnlinePlayers")
        };

        if (els.roomLabel) {
            els.roomLabel.textContent = state.roomId || "Chua chon";
        }
        if (els.userLabel) {
            els.userLabel.textContent = state.displayName || state.userId || "Guest";
        }
        initBoardUi();
        bindPageActions();
        resetLocalBoardState("Dang khoi tao ket noi...");
        connectSocket();
    });

    function resetGame() {
        if (!state.client || !state.connected || !state.roomId || !state.userId) {
            setGameStatus("Chua the reset van dau khi chua ket noi phong");
            return;
        }
        state.client.publish({
            destination: "/app/chess.reset",
            body: JSON.stringify({ roomId: state.roomId, userId: state.userId })
        });
    }

    function resetLocalBoardState(statusText) {
        state.board = createInitialBoard();
        state.turn = "w";
        state.currentTurnUserId = "";
        state.selected = null;
        state.legalMoves = [];
        state.moveHistory = [];
        state.gameOver = false;
        state.resultText = "Dang cho doi doi thu";
        state.lastMove = null;
        renderAll();
        renderPlayers();
        if (statusText) {
            setGameStatus(statusText);
        }
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
    }

    function onCellClick(row, col) {
        if (!state.connected) {
            setGameStatus("Dang mat ket noi... He thong se tu ket noi lai.");
            return;
        }
        if (!state.roomId || !state.userId) {
            setGameStatus("Phong hoac nguoi choi khong hop le");
            return;
        }
        if (state.players.length < 2) {
            setGameStatus("Dang cho doi thu vao phong");
            return;
        }
        if (state.gameOver) {
            setGameStatus("Van dau da ket thuc. Bam 'Van moi' de choi tiep.");
            return;
        }
        if (!state.myColor) {
            setGameStatus("Ban chua duoc gan mau quan");
            return;
        }
        if (!isMyTurn()) {
            setGameStatus("Chua den luot cua ban");
            return;
        }

        const piece = state.board[row][col];
        const selected = state.selected;
        const clickedOwnPiece = piece && piece[0] === state.turn;

        if (selected) {
            const legal = state.legalMoves.find(m => m.to.row === row && m.to.col === col);
            if (legal) {
                sendMoveToServer(legal);
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
            state.resultText = "Checkmate - " + colorName(sideJustMoved) + " thang";
            setGameStatus("Checkmate! " + colorName(sideJustMoved) + " thang.");
        } else if (!opponentHasMove) {
            state.gameOver = true;
            state.resultText = "Hoa (stalemate)";
            setGameStatus("Stalemate - hoa.");
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
        const cells = els.board.querySelectorAll(".chess-cell");
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

            if (r === 7) {
                const fileEl = document.createElement("span");
                fileEl.className = "chess-coord file";
                fileEl.textContent = FILES[c];
                cell.appendChild(fileEl);
            }
            if (c === 0) {
                const rankEl = document.createElement("span");
                rankEl.className = "chess-coord rank";
                rankEl.textContent = String(8 - r);
                cell.appendChild(rankEl);
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
        const waitingForOpponent = state.players.length < 2;
        if (els.turnStatus) {
            if (waitingForOpponent) {
                els.turnStatus.textContent = "Luot: Dang cho doi thu";
            } else if (state.gameOver) {
                els.turnStatus.textContent = "Van dau ket thuc";
            } else {
                els.turnStatus.textContent = "Luot: " + currentName + (isMyTurn() ? " (Ban)" : " (Doi thu)");
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
        if (els.myColorLabel) {
            els.myColorLabel.textContent = state.myColor ? colorName(state.myColor) : "Chua vao phong";
        }
        if (els.roomStatus) {
            if (waitingForOpponent) {
                els.roomStatus.textContent = "Dang cho doi thu vao phong";
            } else if (state.gameOver) {
                els.roomStatus.textContent = state.resultText;
            } else {
                els.roomStatus.textContent = isMyTurn() ? "Den luot ban" : "Dang cho doi thu di";
            }
        }
        if (els.resetBtn) {
            els.resetBtn.disabled = !(state.connected && state.roomId && state.userId);
        }
        if (!els.gameStatus) {
            return;
        }
        if (state.gameOver && !els.gameStatus.textContent) {
            els.gameStatus.textContent = state.resultText;
        }
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

    function bindPageActions() {
        els.resetBtn?.addEventListener("click", resetGame);
        els.leaveBtn?.addEventListener("click", () => leaveAndRedirect());
        window.addEventListener("beforeunload", () => {
            try {
                if (state.client && state.connected && state.roomId && state.userId) {
                    state.client.publish({
                        destination: "/app/chess.leave",
                        body: JSON.stringify({ roomId: state.roomId, userId: state.userId })
                    });
                }
            } catch (_) {
            }
        });
    }

    function connectSocket() {
        if (!state.roomId) {
            setConnectionStatus("Chua co ma phong");
            setGameStatus("Hay quay lai Online Hub de tao/chon phong C\u1edd vua.");
            return;
        }
        if (!state.userId) {
            setConnectionStatus("Khong xac dinh duoc user session");
            setGameStatus("Vui long tai lai trang.");
            return;
        }
        if (!window.StompJs || typeof SockJS === "undefined") {
            setConnectionStatus("Thieu thu vien WebSocket");
            setGameStatus("Khong tai duoc SockJS/STOMP.");
            return;
        }

        setConnectionStatus("Dang ket noi...");
        state.client = new window.StompJs.Client({
            webSocketFactory: () => new SockJS(appPath("/ws"), null, {
                transports: ["websocket", "xhr-streaming", "xhr-polling"]
            }),
            reconnectDelay: 3000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            connectionTimeout: 12000,
            onConnect: () => {
                state.connected = true;
                setConnectionStatus("Da ket noi");
                setGameStatus("Dang vao phong...");

                state.client.subscribe("/topic/chess.room." + state.roomId, (frame) => {
                    let payload = {};
                    try {
                        payload = JSON.parse(frame.body || "{}");
                    } catch (_) {
                        payload = {};
                    }
                    handleRoomMessage(payload);
                });

                state.client.subscribe("/user/queue/errors", (frame) => {
                    let payload = {};
                    try {
                        payload = JSON.parse(frame.body || "{}");
                    } catch (_) {
                        payload = {};
                    }
                    if (payload.scope && String(payload.scope) !== "chess") {
                        return;
                    }
                    if (payload.error) {
                        setGameStatus(String(payload.error));
                    }
                });

                publishJoin();
                updateStatusText();
            },
            onStompError: () => {
                state.connected = false;
                setConnectionStatus("Loi STOMP");
                setGameStatus("Loi STOMP - dang thu ket noi lai...");
                updateStatusText();
            },
            onWebSocketError: () => {
                state.connected = false;
                setConnectionStatus("Loi WebSocket");
                setGameStatus("Loi WebSocket - dang thu ket noi lai...");
                updateStatusText();
            },
            onWebSocketClose: () => {
                state.connected = false;
                setConnectionStatus("Mat ket noi - dang thu lai");
                setGameStatus("Mat ket noi - dang thu ket noi lai...");
                updateStatusText();
            }
        });
        state.client.activate();
    }

    function publishJoin() {
        if (!state.client || !state.roomId || !state.userId) {
            return;
        }
        state.joining = true;
        state.client.publish({
            destination: "/app/chess.join",
            body: JSON.stringify({
                roomId: state.roomId,
                userId: state.userId,
                displayName: state.displayName,
                avatarPath: state.avatarPath
            })
        });
    }

    function handleRoomMessage(payload) {
        if (!payload || typeof payload !== "object") {
            return;
        }
        if (payload.type === "ROOM_CLOSED") {
            state.connected = false;
            setConnectionStatus("Phong da dong");
            setGameStatus(String(payload.message || "Phong da dong"));
            updateStatusText();
            return;
        }
        if (payload.type === "ERROR") {
            if (!payload.userId || payload.userId === state.userId) {
                setGameStatus(String(payload.error || "Loi"));
            }
            return;
        }
        if (payload.room) {
            applyRoomSnapshot(payload.room, payload.message);
            if (payload.type === "ROOM_STATE") {
                state.joining = false;
            }
            return;
        }
        if (payload.message) {
            setGameStatus(String(payload.message));
        }
    }

    function applyRoomSnapshot(room, messageText) {
        if (!room || typeof room !== "object") {
            return;
        }
        const nextRoomId = normalizeText(room.roomId);
        if (nextRoomId) {
            state.roomId = nextRoomId;
            if (els.roomLabel) {
                els.roomLabel.textContent = nextRoomId;
            }
        }

        state.players = Array.isArray(room.players)
            ? room.players.map((p) => ({
                userId: normalizeText(p.userId),
                displayName: normalizeText(p.displayName) || normalizeText(p.userId) || "Guest",
                avatarPath: normalizeText(p.avatarPath),
                color: normalizeText(p.color) === "b" ? "b" : "w"
            }))
            : [];

        const me = state.players.find((p) => p.userId === state.userId) || null;
        state.myColor = me ? me.color : "";
        state.currentTurnUserId = normalizeText(room.currentTurnUserId);
        state.turn = normalizeText(room.currentTurnColor) === "b" ? "b" : "w";
        state.board = copyBoard(room.board);
        state.moveHistory = Array.isArray(room.moveHistory)
            ? room.moveHistory.map((m) => String(m || "")).filter((m) => m)
            : [];

        if (room.lastMove && typeof room.lastMove === "object") {
            state.lastMove = {
                from: { row: coerceIndex(room.lastMove.fromRow), col: coerceIndex(room.lastMove.fromCol) },
                to: { row: coerceIndex(room.lastMove.toRow), col: coerceIndex(room.lastMove.toCol) }
            };
        } else {
            state.lastMove = null;
        }

        state.selected = null;
        state.legalMoves = [];
        refreshLocalGameState(normalizeText(messageText) || normalizeText(room.statusMessage));
        renderAll();
        renderPlayers();
        updateStatusText();
    }

    function refreshLocalGameState(preferredMessage) {
        if (state.players.length < 2) {
            state.gameOver = false;
            state.resultText = "Dang cho doi doi thu";
            setGameStatus(preferredMessage || "Dang cho doi thu vao phong");
            return;
        }

        const sideToMove = state.turn === "b" ? "b" : "w";
        const inCheck = isKingInCheck(state.board, sideToMove);
        const hasMove = hasAnyLegalMove(state.board, sideToMove);
        const winnerSide = sideToMove === "w" ? "b" : "w";

        if (!hasMove && inCheck) {
            state.gameOver = true;
            state.resultText = "Checkmate - " + colorName(winnerSide) + " thang";
            setGameStatus(preferredMessage || ("Checkmate! " + colorName(winnerSide) + " thang."));
            return;
        }
        if (!hasMove) {
            state.gameOver = true;
            state.resultText = "Hoa (stalemate)";
            setGameStatus(preferredMessage || "Stalemate - hoa.");
            return;
        }

        state.gameOver = false;
        state.resultText = "Dang choi";
        if (preferredMessage) {
            setGameStatus(preferredMessage);
        } else if (inCheck) {
            setGameStatus(colorName(sideToMove) + " dang bi chieu!");
        } else {
            setGameStatus(isMyTurn() ? "Den luot ban." : "Dang cho doi thu di.");
        }
    }

    function renderPlayers() {
        if (!els.playersBox) {
            return;
        }
        if (!Array.isArray(state.players) || state.players.length === 0) {
            els.playersBox.innerHTML = '<div class="text-muted">Dang cho du nguoi...</div>';
            return;
        }

        const sorted = state.players.slice().sort((a, b) => (a.color || "w").localeCompare(b.color || "w"));
        els.playersBox.innerHTML = "";
        sorted.forEach((player) => {
            const row = document.createElement("div");
            row.className = "chess-player-item";
            if (player.userId === state.userId) {
                row.classList.add("is-me");
            }
            if (state.currentTurnUserId && player.userId === state.currentTurnUserId && state.players.length >= 2 && !state.gameOver) {
                row.classList.add("is-turn");
            }
            const colorText = player.color === "b" ? "Den" : "Trang";
            const meText = player.userId === state.userId ? " (Ban)" : "";
            row.innerHTML =
                '<div class="fw-semibold">' + escapeHtml(player.displayName || player.userId || "Guest") + meText + '</div>' +
                '<div class="text-muted">Quan: ' + colorText + ' | ID: ' + escapeHtml(player.userId || "-") + '</div>';
            els.playersBox.appendChild(row);
        });

        if (state.players.length < 2) {
            const waiting = document.createElement("div");
            waiting.className = "text-muted small";
            waiting.textContent = "Dang cho them 1 nguoi choi...";
            els.playersBox.appendChild(waiting);
        }
    }

    function sendMoveToServer(move) {
        if (!state.client || !state.connected || !state.roomId || !state.userId) {
            setGameStatus("Chua ket noi server");
            return;
        }
        const piece = state.board[move.from.row]?.[move.from.col];
        if (!piece) {
            return;
        }
        const payload = {
            roomId: state.roomId,
            userId: state.userId,
            fromRow: move.from.row,
            fromCol: move.from.col,
            toRow: move.to.row,
            toCol: move.to.col
        };
        if (piece[1] === "P" && (move.to.row === 0 || move.to.row === BOARD_SIZE - 1)) {
            payload.promotion = "Q";
        }
        state.selected = null;
        state.legalMoves = [];
        renderBoard();
        setGameStatus("Dang gui nuoc di...");
        state.client.publish({
            destination: "/app/chess.move",
            body: JSON.stringify(payload)
        });
    }

    function leaveAndRedirect() {
        const roomId = state.roomId;
        try {
            if (state.client && state.connected && roomId && state.userId) {
                state.client.publish({
                    destination: "/app/chess.leave",
                    body: JSON.stringify({ roomId, userId: state.userId })
                });
            }
            if (state.client) {
                state.client.deactivate();
            }
        } catch (_) {
        }
        const target = new URL(appPath("/online-hub"), window.location.origin);
        target.searchParams.set("game", "chess");
        if (roomId) {
            target.searchParams.set("roomId", roomId);
        }
        window.location.href = target.pathname + target.search;
    }

    function setConnectionStatus(text) {
        if (els.connectionStatus) {
            els.connectionStatus.textContent = text || "-";
        }
    }

    function copyBoard(board) {
        if (!Array.isArray(board) || board.length !== BOARD_SIZE) {
            return createInitialBoard();
        }
        const copy = [];
        for (let r = 0; r < BOARD_SIZE; r++) {
            const row = Array.isArray(board[r]) ? board[r] : [];
            copy.push(Array.from({ length: BOARD_SIZE }, (_, c) => {
                const value = row[c];
                return value == null ? null : String(value);
            }));
        }
        return copy;
    }

    function coerceIndex(value) {
        const n = Number(value);
        return Number.isFinite(n) ? n : 0;
    }

    function normalizeText(value) {
        const text = String(value || "").trim();
        return text || "";
    }

    function isMyTurn() {
        if (!state.myColor || state.turn !== state.myColor) {
            return false;
        }
        if (!state.currentTurnUserId) {
            return false;
        }
        return state.currentTurnUserId === state.userId;
    }

    function escapeHtml(value) {
        return String(value || "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    }

    function isBotTurn() {
        return state.botEnabled && !state.gameOver && state.turn === state.botSide;
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
})();
