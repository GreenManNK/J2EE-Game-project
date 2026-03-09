(function () {
    const boot = window.XiangqiOnlineBoot || {};
    const ROWS = 10;
    const COLS = 9;
    const appPath = (window.CaroUrl && typeof window.CaroUrl.path === "function")
        ? window.CaroUrl.path
        : (value) => value;
    const FILE_LABELS = ["1", "2", "3", "4", "5", "6", "7", "8", "9"];
    const PIECE_LABELS = {
        rG: "G", rA: "A", rE: "E", rH: "H", rR: "R", rC: "C", rP: "P",
        bG: "G", bA: "A", bE: "E", bH: "H", bR: "R", bC: "C", bP: "P"
    };
    const PIECE_NAMES = {
        G: "Tuong",
        A: "Si",
        E: "Tinh",
        H: "Ma",
        R: "Xe",
        C: "Phao",
        P: "Tot"
    };
    const PIECE_VALUES = {
        G: 1000,
        R: 90,
        C: 45,
        H: 40,
        E: 20,
        A: 20,
        P: 10
    };
    const SESSION_RESYNC_KEY = "xiangqiOnline.sessionResync.v1";
    const SESSION_RESYNC_COOLDOWN_MS = 20000;
    const USER_CHANGE_EVENT = (window.CaroUser && window.CaroUser.eventName) || "caro:user-changed";
    const DEFAULT_AVATAR_PATH = "/uploads/avatars/default-avatar.jpg";
    const routeState = resolveRouteState();

    const state = {
        roomId: String(boot.roomId || routeState.roomId || "").trim(),
        userId: String(boot.sessionUserId || "").trim(),
        displayName: String(boot.sessionDisplayName || "").trim(),
        avatarPath: String(boot.sessionAvatarPath || "").trim(),
        spectate: routeState.spectate,
        board: [],
        turn: "r",
        currentTurnUserId: "",
        selected: null,
        legalMoves: [],
        moveHistory: [],
        gameOver: false,
        resultText: "Dang cho doi doi thu",
        roomStatus: "WAITING",
        lastMove: null,
        spectatorCount: 0,
        players: [],
        myColor: "",
        connected: false,
        joining: false,
        client: null,
        botEnabled: Boolean(boot.botEnabled),
        botSide: boot.botSide === "r" ? "r" : "b",
        botDifficulty: String(boot.botDifficulty || "easy").toLowerCase() === "hard" ? "hard" : "easy",
        botThinking: false,
        pendingMove: false
    };

    let els = {};

    document.addEventListener("DOMContentLoaded", () => {
        els = {
            board: document.getElementById("xiangqiBoard"),
            turnStatus: document.getElementById("xiangqiTurnStatus"),
            gameStatus: document.getElementById("xiangqiGameStatus"),
            resultLabel: document.getElementById("xiangqiResultLabel"),
            moveCount: document.getElementById("xiangqiMoveCount"),
            currentColor: document.getElementById("xiangqiCurrentColor"),
            spectatorCount: document.getElementById("xiangqiOnlineSpectatorCount"),
            myMoveCount: document.getElementById("xiangqiOnlineMyMoveCount"),
            opponentMoveCount: document.getElementById("xiangqiOnlineOpponentMoveCount"),
            myCaptureCount: document.getElementById("xiangqiOnlineMyCaptureCount"),
            opponentCaptureCount: document.getElementById("xiangqiOnlineOpponentCaptureCount"),
            moveLog: document.getElementById("xiangqiMoveLog"),
            resetBtn: document.getElementById("xiangqiOnlineResetBtn"),
            surrenderBtn: document.getElementById("xiangqiOnlineSurrenderBtn"),
            joinPlayBtn: document.getElementById("xiangqiOnlineJoinPlayBtn"),
            switchSpectateBtn: document.getElementById("xiangqiOnlineSwitchSpectateBtn"),
            leaveBtn: document.getElementById("xiangqiOnlineLeaveBtn"),
            roomLabel: document.getElementById("xiangqiOnlineRoomLabel"),
            userLabel: document.getElementById("xiangqiOnlineUserLabel"),
            userAvatar: document.getElementById("xiangqiOnlineUserAvatar"),
            userMeta: document.getElementById("xiangqiOnlineUserMeta"),
            modeLabel: document.getElementById("xiangqiOnlineModeLabel"),
            myColorLabel: document.getElementById("xiangqiOnlineMyColor"),
            connectionStatus: document.getElementById("xiangqiOnlineConnectionStatus"),
            roomStatus: document.getElementById("xiangqiOnlineStatusText"),
            playersBox: document.getElementById("xiangqiOnlinePlayers"),
            boardCells: []
        };
        if (els.roomLabel) {
            els.roomLabel.textContent = state.roomId || "Chua chon";
        }
        syncCurrentUserIdentity();
        renderCurrentUserSummary();
        window.addEventListener(USER_CHANGE_EVENT, handleCurrentUserChange);
        updateModeUi();
        initBoardUi();
        bindPageActions();
        resetLocalBoardState("Dang khoi tao ket noi...");
        connectSocket();
    });

    function resetGame() {
        if (!canResetGame()) {
            setGameStatus("Chi co the tao van moi khi phong da du 2 nguoi choi.");
            return;
        }
        state.client.publish({
            destination: "/app/xiangqi.reset",
            body: JSON.stringify({ roomId: state.roomId, userId: state.userId })
        });
    }

    function surrenderGame() {
        if (!canSurrenderGame()) {
            setGameStatus("Khong the dau hang luc nay.");
            return;
        }
        if (!window.confirm("Ban chac chan muon dau hang van dau nay?")) {
            return;
        }
        state.client.publish({
            destination: "/app/xiangqi.surrender",
            body: JSON.stringify({ roomId: state.roomId, userId: state.userId })
        });
        setGameStatus("Dang gui yeu cau dau hang...");
    }

    function requestJoinAsPlayer() {
        if (!state.spectate) {
            setGameStatus("Ban da o che do choi.");
            return;
        }
        if (!state.client || !state.connected) {
            setGameStatus("Dang mat ket noi... He thong se tu ket noi lai.");
            return;
        }
        if (!state.roomId || !state.userId) {
            setGameStatus("Phong hoac nguoi choi khong hop le");
            return;
        }
        if (state.players.length >= 2) {
            setGameStatus("Phong da du nguoi choi. Ban dang o che do xem.");
            return;
        }
        state.client.publish({
            destination: "/app/xiangqi.join",
            body: JSON.stringify({
                roomId: state.roomId,
                userId: state.userId,
                displayName: state.displayName,
                avatarPath: state.avatarPath
            })
        });
        setGameStatus("Dang yeu cau tham gia choi...");
    }

    function requestSwitchToSpectateMode() {
        if (state.spectate) {
            setGameStatus("Ban dang o che do xem.");
            return;
        }
        if (!state.roomId) {
            setGameStatus("Chua co ma phong.");
            return;
        }
        if (state.myColor) {
            setGameStatus("Ban dang la nguoi choi. Hay roi phong neu muon vao che do xem.");
            return;
        }
        const target = buildCurrentModeUrl(true);
        if (target) {
            window.location.href = target;
        }
    }

    function resetLocalBoardState(statusText) {
        state.board = createInitialBoard();
        state.turn = "r";
        state.currentTurnUserId = "";
        state.selected = null;
        state.legalMoves = [];
        state.moveHistory = [];
        state.gameOver = false;
        state.resultText = "Dang cho doi doi thu";
        state.roomStatus = "WAITING";
        state.lastMove = null;
        state.spectatorCount = 0;
        state.pendingMove = false;
        renderAll();
        renderPlayers();
        if (statusText) {
            setGameStatus(statusText);
        }
    }

    function createInitialBoard() {
        const board = Array.from({ length: ROWS }, () => Array(COLS).fill(null));

        // Black side (top)
        board[0] = ["bR", "bH", "bE", "bA", "bG", "bA", "bE", "bH", "bR"];
        board[2][1] = "bC";
        board[2][7] = "bC";
        board[3][0] = "bP";
        board[3][2] = "bP";
        board[3][4] = "bP";
        board[3][6] = "bP";
        board[3][8] = "bP";

        // Red side (bottom)
        board[9] = ["rR", "rH", "rE", "rA", "rG", "rA", "rE", "rH", "rR"];
        board[7][1] = "rC";
        board[7][7] = "rC";
        board[6][0] = "rP";
        board[6][2] = "rP";
        board[6][4] = "rP";
        board[6][6] = "rP";
        board[6][8] = "rP";

        return board;
    }

    function initBoardUi() {
        if (!els.board) return;
        els.board.innerHTML = "";
        for (let r = 0; r < ROWS; r++) {
            for (let c = 0; c < COLS; c++) {
                const btn = document.createElement("button");
                btn.type = "button";
                btn.className = "xiangqi-cell";
                btn.dataset.row = String(r);
                btn.dataset.col = String(c);
                btn.setAttribute("role", "gridcell");
                btn.addEventListener("click", () => onCellClick(r, c));
                els.board.appendChild(btn);
            }
        }
        els.boardCells = Array.from(els.board.querySelectorAll(".xiangqi-cell"));
    }

    function onCellClick(row, col) {
        if (state.spectate) {
            setGameStatus("Ban dang o che do xem.");
            return;
        }
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
            setGameStatus("Phong da du nguoi choi. Bam 'Vao che do xem' hoac phim V.");
            return;
        }
        if (!isMyTurn()) {
            setGameStatus("Chua den luot cua ban");
            return;
        }
        if (state.pendingMove) {
            setGameStatus("Dang cho server xac nhan nuoc di...");
            return;
        }

        const piece = state.board[row][col];
        const selected = state.selected;
        const clickedOwn = piece && piece[0] === state.turn;

        if (selected) {
            const legal = state.legalMoves.find((m) => m.to.row === row && m.to.col === col);
            if (legal) {
                sendMoveToServer(legal);
                return;
            }
        }

        if (clickedOwn) {
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
        const sq = toSquare(row, col);
        const pieceLabel = pieceDisplayName(piece);
        setGameStatus(
            state.legalMoves.length > 0
                ? ("Dang chon " + pieceLabel + " tai " + sq + " (" + state.legalMoves.length + " nuoc di hop le)")
                : (pieceLabel + " tai " + sq + " khong co nuoc di hop le")
        );
    }

    function applyMove(move) {
        const fromPiece = state.board[move.from.row][move.from.col];
        if (!fromPiece) return;
        const captured = state.board[move.to.row][move.to.col];

        const nextBoard = applyMoveOnBoard(state.board, move);
        state.board = nextBoard;
        state.lastMove = { from: { ...move.from }, to: { ...move.to } };
        state.moveHistory.push(buildMoveNotation(fromPiece, move.from, move.to, captured));
        state.selected = null;
        state.legalMoves = [];

        const sideJustMoved = state.turn;
        const opponent = sideJustMoved === "r" ? "b" : "r";

        if (!findGeneral(state.board, opponent)) {
            state.gameOver = true;
            state.resultText = colorName(sideJustMoved) + " thang";
            setGameStatus(colorName(sideJustMoved) + " da an Tuong va chien thang!");
            renderAll();
            return;
        }

        state.turn = opponent;
        const inCheck = isGeneralInCheck(state.board, opponent);
        const hasMove = hasAnyLegalMove(state.board, opponent);

        if (!hasMove && inCheck) {
            state.gameOver = true;
            state.resultText = "Chieu bi - " + colorName(sideJustMoved) + " thang";
            setGameStatus("Chieu bi! " + colorName(sideJustMoved) + " thang.");
        } else if (!hasMove) {
            state.gameOver = true;
            state.resultText = "Hoa";
            setGameStatus("Het nuoc di hop le - hoa.");
        } else if (inCheck) {
            state.resultText = "Dang choi";
            setGameStatus(colorName(opponent) + " dang bi chieu!");
        } else {
            state.resultText = "Dang choi";
            setGameStatus("Nuoc di hop le. Den luot " + colorName(opponent) + ".");
        }

        renderAll();
        maybeQueueBotMove();
    }

    function renderAll() {
        renderCurrentUserSummary();
        renderBoard();
        renderMoveLog();
        updateStatusText();
    }

    function renderBoard() {
        if (!els.board) return;
        const cells = els.boardCells || [];
        const selected = state.selected;
        const legalMap = new Map(state.legalMoves.map((m) => [key(m.to.row, m.to.col), m]));
        const checkedGeneral = findGeneral(state.board, state.turn);
        const inCheck = checkedGeneral && isGeneralInCheck(state.board, state.turn);

        cells.forEach((cell) => {
            const row = Number(cell.dataset.row);
            const col = Number(cell.dataset.col);
            const piece = state.board[row][col];
            cell.innerHTML = "";
            cell.classList.remove("selected", "legal", "capture", "last-move", "in-check");

            if (selected && selected.row === row && selected.col === col) {
                cell.classList.add("selected");
            }
            const legal = legalMap.get(key(row, col));
            if (legal) {
                cell.classList.add("legal");
                if (legal.capture) {
                    cell.classList.add("capture");
                }
            }
            if (state.lastMove && (
                (state.lastMove.from.row === row && state.lastMove.from.col === col) ||
                (state.lastMove.to.row === row && state.lastMove.to.col === col)
            )) {
                cell.classList.add("last-move");
            }
            if (inCheck && checkedGeneral.row === row && checkedGeneral.col === col) {
                cell.classList.add("in-check");
            }

            if (piece) {
                const pieceEl = document.createElement("span");
                pieceEl.className = "xiangqi-piece " + (piece[0] === "r" ? "red" : "black");
                pieceEl.textContent = PIECE_LABELS[piece] || piece[1];
                pieceEl.title = pieceDisplayName(piece);
                cell.appendChild(pieceEl);
            }

            if (row === ROWS - 1) {
                const f = document.createElement("span");
                f.className = "xiangqi-coord file";
                f.textContent = FILE_LABELS[col];
                cell.appendChild(f);
            }
            if (col === 0) {
                const r = document.createElement("span");
                r.className = "xiangqi-coord rank";
                r.textContent = String(ROWS - row);
                cell.appendChild(r);
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
            li.textContent = state.moveHistory[i] + (state.moveHistory[i + 1] ? (" | " + state.moveHistory[i + 1]) : "");
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
        if (els.resultLabel) els.resultLabel.textContent = state.resultText;
        if (els.moveCount) els.moveCount.textContent = String(state.moveHistory.length);
        if (els.currentColor) els.currentColor.textContent = currentName;
        if (els.spectatorCount) {
            els.spectatorCount.textContent = String(state.spectatorCount || 0);
        }
        if (els.myColorLabel) {
            if (state.myColor) {
                els.myColorLabel.textContent = colorName(state.myColor);
            } else if (state.spectate) {
                els.myColorLabel.textContent = "Nguoi xem";
            } else {
                els.myColorLabel.textContent = "Chua vao phong";
            }
        }
        if (els.roomStatus) {
            if (waitingForOpponent) {
                els.roomStatus.textContent = "Dang cho doi thu vao phong";
            } else if (!state.myColor) {
                els.roomStatus.textContent = state.spectate
                    ? "Ban dang theo doi tran dau"
                    : "Phong da day - hay vao che do xem";
            } else if (state.gameOver) {
                els.roomStatus.textContent = state.resultText;
            } else {
                els.roomStatus.textContent = isMyTurn() ? "Den luot ban" : "Dang cho doi thu di";
            }
        }
        if (els.resetBtn) {
            els.resetBtn.disabled = !canResetGame();
        }
        if (els.surrenderBtn) {
            els.surrenderBtn.disabled = !canSurrenderGame();
        }
        if (els.joinPlayBtn) {
            const canJoinPlay = Boolean(
                state.spectate &&
                state.client &&
                state.connected &&
                state.roomId &&
                !state.myColor &&
                state.players.length < 2
            );
            els.joinPlayBtn.disabled = !canJoinPlay;
            els.joinPlayBtn.classList.toggle("d-none", !state.spectate);
        }
        if (els.switchSpectateBtn) {
            const canSwitchSpectate = Boolean(
                !state.spectate &&
                state.roomId &&
                !state.myColor
            );
            els.switchSpectateBtn.disabled = !canSwitchSpectate;
            els.switchSpectateBtn.classList.toggle("d-none", !canSwitchSpectate);
        }
        updateSessionStatsUi();
        if (state.gameOver && els.gameStatus && !els.gameStatus.textContent) {
            els.gameStatus.textContent = state.resultText;
        }
    }

    function setGameStatus(text) {
        if (els.gameStatus) {
            els.gameStatus.textContent = text;
        }
    }

    function updateSessionStatsUi() {
        const stats = computeSessionStats();
        if (els.myMoveCount) {
            els.myMoveCount.textContent = stats ? String(stats.myMoves) : "-";
        }
        if (els.opponentMoveCount) {
            els.opponentMoveCount.textContent = stats ? String(stats.opponentMoves) : "-";
        }
        if (els.myCaptureCount) {
            els.myCaptureCount.textContent = stats ? String(stats.myCaptures) : "-";
        }
        if (els.opponentCaptureCount) {
            els.opponentCaptureCount.textContent = stats ? String(stats.opponentCaptures) : "-";
        }
    }

    function computeSessionStats() {
        if (state.myColor !== "r" && state.myColor !== "b") {
            return null;
        }
        let redMoves = 0;
        let blackMoves = 0;
        let redCaptures = 0;
        let blackCaptures = 0;

        for (let i = 0; i < state.moveHistory.length; i++) {
            const notation = String(state.moveHistory[i] || "");
            const redTurn = i % 2 === 0;
            if (redTurn) {
                redMoves += 1;
                if (isCaptureNotation(notation)) {
                    redCaptures += 1;
                }
            } else {
                blackMoves += 1;
                if (isCaptureNotation(notation)) {
                    blackCaptures += 1;
                }
            }
        }

        if (state.myColor === "r") {
            return {
                myMoves: redMoves,
                opponentMoves: blackMoves,
                myCaptures: redCaptures,
                opponentCaptures: blackCaptures
            };
        }
        return {
            myMoves: blackMoves,
            opponentMoves: redMoves,
            myCaptures: blackCaptures,
            opponentCaptures: redCaptures
        };
    }

    function isCaptureNotation(text) {
        return /(^|\s)(an|x)(\s|$)/i.test(String(text || ""));
    }

    function getLegalMovesForPiece(board, row, col, side) {
        const piece = board[row]?.[col];
        if (!piece || piece[0] !== side) return [];
        const pseudo = getPseudoMoves(board, row, col, piece, false);
        return pseudo.filter((move) => {
            const next = applyMoveOnBoard(board, move);
            return !isGeneralInCheck(next, side);
        });
    }

    function hasAnyLegalMove(board, side) {
        for (let r = 0; r < ROWS; r++) {
            for (let c = 0; c < COLS; c++) {
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
        for (let r = 0; r < ROWS; r++) {
            for (let c = 0; c < COLS; c++) {
                const piece = board[r][c];
                if (!piece || piece[0] !== side) continue;
                moves.push(...getLegalMovesForPiece(board, r, c, side));
            }
        }
        return moves;
    }

    function isGeneralInCheck(board, side) {
        const general = findGeneral(board, side);
        if (!general) return true;
        const opp = side === "r" ? "b" : "r";
        return isSquareAttacked(board, general.row, general.col, opp);
    }

    function findGeneral(board, side) {
        for (let r = 0; r < ROWS; r++) {
            for (let c = 0; c < COLS; c++) {
                if (board[r][c] === side + "G") {
                    return { row: r, col: c };
                }
            }
        }
        return null;
    }

    function isSquareAttacked(board, targetRow, targetCol, attackerSide) {
        for (let r = 0; r < ROWS; r++) {
            for (let c = 0; c < COLS; c++) {
                const piece = board[r][c];
                if (!piece || piece[0] !== attackerSide) continue;
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
            case "R": return rookMoves(board, row, col, side);
            case "C": return cannonMoves(board, row, col, side, attacksOnly);
            case "H": return horseMoves(board, row, col, side);
            case "E": return elephantMoves(board, row, col, side);
            case "A": return advisorMoves(board, row, col, side);
            case "G": return generalMoves(board, row, col, side, attacksOnly);
            case "P": return pawnMoves(board, row, col, side);
            default: return [];
        }
    }

    function rookMoves(board, row, col, side) {
        return linearMoves(board, row, col, side, [[1, 0], [-1, 0], [0, 1], [0, -1]]);
    }

    function cannonMoves(board, row, col, side, attacksOnly) {
        const moves = [];
        const dirs = [[1, 0], [-1, 0], [0, 1], [0, -1]];
        for (const [dr, dc] of dirs) {
            let r = row + dr;
            let c = col + dc;
            let seenScreen = false;
            while (inside(r, c)) {
                const target = board[r][c];
                if (!seenScreen) {
                    if (!target) {
                        if (!attacksOnly) {
                            moves.push(makeMove(row, col, r, c, false));
                        }
                    } else {
                        seenScreen = true;
                    }
                } else if (target) {
                    if (attacksOnly || target[0] !== side) {
                        if (target[0] !== side) {
                            moves.push(makeMove(row, col, r, c, true));
                        } else if (attacksOnly) {
                            moves.push(makeMove(row, col, r, c, false));
                        }
                    }
                    break;
                }
                r += dr;
                c += dc;
            }
        }
        return moves;
    }

    function horseMoves(board, row, col, side) {
        const moves = [];
        const patterns = [
            { leg: [1, 0], steps: [[2, 1], [2, -1]] },
            { leg: [-1, 0], steps: [[-2, 1], [-2, -1]] },
            { leg: [0, 1], steps: [[1, 2], [-1, 2]] },
            { leg: [0, -1], steps: [[1, -2], [-1, -2]] }
        ];
        for (const p of patterns) {
            const legRow = row + p.leg[0];
            const legCol = col + p.leg[1];
            if (!inside(legRow, legCol) || board[legRow][legCol]) continue;
            for (const [dr, dc] of p.steps) {
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

    function elephantMoves(board, row, col, side) {
        const moves = [];
        const deltas = [[2, 2], [2, -2], [-2, 2], [-2, -2]];
        for (const [dr, dc] of deltas) {
            const tr = row + dr;
            const tc = col + dc;
            const eyeRow = row + dr / 2;
            const eyeCol = col + dc / 2;
            if (!inside(tr, tc)) continue;
            if (board[eyeRow][eyeCol]) continue;
            if (!staysOnOwnSideRiver(tr, side)) continue;
            const target = board[tr][tc];
            if (!target || target[0] !== side) {
                moves.push(makeMove(row, col, tr, tc, Boolean(target)));
            }
        }
        return moves;
    }

    function advisorMoves(board, row, col, side) {
        const moves = [];
        const deltas = [[1, 1], [1, -1], [-1, 1], [-1, -1]];
        for (const [dr, dc] of deltas) {
            const tr = row + dr;
            const tc = col + dc;
            if (!inside(tr, tc) || !insidePalace(tr, tc, side)) continue;
            const target = board[tr][tc];
            if (!target || target[0] !== side) {
                moves.push(makeMove(row, col, tr, tc, Boolean(target)));
            }
        }
        return moves;
    }

    function generalMoves(board, row, col, side, attacksOnly) {
        const moves = [];
        const deltas = [[1, 0], [-1, 0], [0, 1], [0, -1]];
        for (const [dr, dc] of deltas) {
            const tr = row + dr;
            const tc = col + dc;
            if (!inside(tr, tc) || !insidePalace(tr, tc, side)) continue;
            const target = board[tr][tc];
            if (!target || target[0] !== side) {
                moves.push(makeMove(row, col, tr, tc, Boolean(target)));
            }
        }

        // Flying general attack/capture on same file if clear
        let r = row + (side === "r" ? -1 : 1);
        while (inside(r, col)) {
            const target = board[r][col];
            if (target) {
                if (target === (side === "r" ? "bG" : "rG")) {
                    moves.push(makeMove(row, col, r, col, true));
                }
                break;
            }
            r += (side === "r" ? -1 : 1);
        }
        return moves;
    }

    function pawnMoves(board, row, col, side) {
        const moves = [];
        const dirs = [];
        dirs.push(side === "r" ? [-1, 0] : [1, 0]); // forward
        if (crossedRiver(row, side)) {
            dirs.push([0, 1], [0, -1]);
        }
        for (const [dr, dc] of dirs) {
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

    function linearMoves(board, row, col, side, dirs) {
        const moves = [];
        for (const [dr, dc] of dirs) {
            let r = row + dr;
            let c = col + dc;
            while (inside(r, c)) {
                const target = board[r][c];
                if (!target) {
                    moves.push(makeMove(row, col, r, c, false));
                } else {
                    if (target[0] !== side) {
                        moves.push(makeMove(row, col, r, c, true));
                    }
                    break;
                }
                r += dr;
                c += dc;
            }
        }
        return moves;
    }

    function applyMoveOnBoard(board, move) {
        const next = cloneBoard(board);
        next[move.to.row][move.to.col] = next[move.from.row][move.from.col];
        next[move.from.row][move.from.col] = null;
        return next;
    }

    function cloneBoard(board) {
        return board.map((row) => row.slice());
    }

    function makeMove(fromRow, fromCol, toRow, toCol, capture) {
        return {
            from: { row: fromRow, col: fromCol },
            to: { row: toRow, col: toCol },
            capture: Boolean(capture)
        };
    }

    function bindPageActions() {
        els.resetBtn?.addEventListener("click", resetGame);
        els.surrenderBtn?.addEventListener("click", surrenderGame);
        els.joinPlayBtn?.addEventListener("click", requestJoinAsPlayer);
        els.switchSpectateBtn?.addEventListener("click", requestSwitchToSpectateMode);
        els.leaveBtn?.addEventListener("click", () => leaveAndRedirect());
        document.addEventListener("keydown", handleHotkeys);
        window.addEventListener("beforeunload", () => {
            try {
                if (state.client && state.connected && state.roomId && state.userId) {
                    state.client.publish({
                        destination: "/app/xiangqi.leave",
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
            setGameStatus("Hay quay lai trang phong truc tuyen de tao/chon phong Co tuong.");
            return;
        }
        if (!state.userId) {
            setConnectionStatus("Khong xac dinh duoc user session");
            setGameStatus("Vui long tai lai trang.");
            return;
        }
        if (!window.StompJs || typeof SockJS === "undefined") {
            setConnectionStatus("Thieu thu vien WebSocket");
            setGameStatus("Khong tai duoc thu vien ket noi realtime.");
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
                clearSessionResyncMarker();

                state.client.subscribe("/topic/xiangqi.room." + state.roomId, (frame) => {
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
                    if (payload.scope && String(payload.scope) !== "xiangqi") {
                        return;
                    }
                    if (payload.error) {
                        state.pendingMove = false;
                        const errorText = String(payload.error);
                        setGameStatus(errorText);
                        handleSessionError(errorText);
                    }
                });

                publishJoin();
                updateStatusText();
            },
            onStompError: () => {
                state.connected = false;
                state.pendingMove = false;
                setConnectionStatus("Loi STOMP");
                setGameStatus("Loi STOMP - dang thu ket noi lai...");
                updateStatusText();
            },
            onWebSocketError: () => {
                state.connected = false;
                state.pendingMove = false;
                setConnectionStatus("Loi WebSocket");
                setGameStatus("Loi WebSocket - dang thu ket noi lai...");
                updateStatusText();
            },
            onWebSocketClose: () => {
                state.connected = false;
                state.pendingMove = false;
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
        const destination = state.spectate ? "/app/xiangqi.spectate" : "/app/xiangqi.join";
        state.client.publish({
            destination: destination,
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
            state.pendingMove = false;
            setConnectionStatus("Phong da dong");
            setGameStatus(String(payload.message || "Phong da dong"));
            updateStatusText();
            return;
        }
        if (payload.type === "ERROR") {
            if (!payload.userId || payload.userId === state.userId) {
                state.pendingMove = false;
                const errorText = String(payload.error || "Loi");
                setGameStatus(errorText);
                handleSessionError(errorText);
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
                displayName: normalizeText(p.displayName) || normalizeText(p.userId) || "Khach",
                avatarPath: normalizeText(p.avatarPath),
                color: normalizeText(p.color) === "b" ? "b" : "r"
            }))
            : [];
        const me = state.players.find((p) => p.userId === state.userId) || null;
        state.myColor = me ? me.color : "";
        if (state.spectate && state.myColor) {
            state.spectate = false;
        }
        updateSpectateQueryInUrl(state.spectate);
        updateModeUi();
        state.currentTurnUserId = normalizeText(room.currentTurnUserId);
        state.turn = normalizeText(room.currentTurnColor) === "b" ? "b" : "r";
        state.roomStatus = normalizeText(room.status).toUpperCase();
        state.spectatorCount = Math.max(0, Number.parseInt(String(room.spectatorCount ?? 0), 10) || 0);
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
        state.pendingMove = false;
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
        if (state.roomStatus === "GAME_OVER") {
            state.gameOver = true;
            state.resultText = preferredMessage || "Van dau ket thuc";
            setGameStatus(preferredMessage || "Van dau da ket thuc");
            return;
        }
        const sideToMove = state.turn === "b" ? "b" : "r";
        const inCheck = isGeneralInCheck(state.board, sideToMove);
        const hasMove = hasAnyLegalMove(state.board, sideToMove);
        const winnerSide = sideToMove === "r" ? "b" : "r";

        if (!findGeneral(state.board, sideToMove)) {
            state.gameOver = true;
            state.resultText = colorName(winnerSide) + " thang";
            setGameStatus(preferredMessage || (colorName(winnerSide) + " da an Tuong va chien thang!"));
            return;
        }
        if (!hasMove && inCheck) {
            state.gameOver = true;
            state.resultText = "Chieu bi - " + colorName(winnerSide) + " thang";
            setGameStatus(preferredMessage || ("Chieu bi! " + colorName(winnerSide) + " thang."));
            return;
        }
        if (!hasMove) {
            state.gameOver = true;
            state.resultText = "Hoa";
            setGameStatus(preferredMessage || "Het nuoc di hop le - hoa.");
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
        const sorted = state.players.slice().sort((a, b) => (a.color || "r").localeCompare(b.color || "r"));
        els.playersBox.innerHTML = "";
        sorted.forEach((player) => {
            const row = document.createElement("div");
            row.className = "xiangqi-player-item";
            if (player.userId === state.userId) {
                row.classList.add("is-me");
            }
            if (state.currentTurnUserId && player.userId === state.currentTurnUserId && state.players.length >= 2 && !state.gameOver) {
                row.classList.add("is-turn");
            }
            const colorText = player.color === "r" ? "Do" : "Den";
            const meText = player.userId === state.userId ? " (Ban)" : "";
            const displayName = player.userId === state.userId
                ? (state.displayName || player.displayName || player.userId || "Khach")
                : (player.displayName || player.userId || "Khach");
            const avatarHtml = buildAvatarHtml(
                player.userId === state.userId ? state.avatarPath : player.avatarPath,
                displayName,
                "online-player-item__avatar"
            );
            row.innerHTML =
                '<div class="online-player-item__head">' +
                    avatarHtml +
                    '<div class="online-player-item__identity">' +
                        '<div class="fw-semibold online-player-item__name">' + escapeHtml(displayName) + meText + '</div>' +
                        '<div class="text-muted online-player-item__meta">Quan: ' + colorText + ' | ' + escapeHtml(playerPresenceText(player)) + '</div>' +
                    '</div>' +
                '</div>';
            els.playersBox.appendChild(row);
        });
        if (state.players.length < 2) {
            const waiting = document.createElement("div");
            waiting.className = "text-muted small";
            waiting.textContent = "Dang cho them 1 nguoi choi...";
            els.playersBox.appendChild(waiting);
        }
        if (state.spectatorCount > 0) {
            const watcher = document.createElement("div");
            watcher.className = "text-muted small";
            watcher.textContent = "Nguoi xem: " + String(state.spectatorCount);
            els.playersBox.appendChild(watcher);
        }
    }

    function sendMoveToServer(move) {
        if (!state.client || !state.connected || !state.roomId || !state.userId) {
            setGameStatus("Chua ket noi server");
            return;
        }
        if (state.pendingMove) {
            setGameStatus("Dang cho server xac nhan nuoc di...");
            return;
        }
        state.selected = null;
        state.legalMoves = [];
        state.pendingMove = true;
        renderBoard();
        setGameStatus("Dang gui nuoc di...");
        try {
            state.client.publish({
                destination: "/app/xiangqi.move",
                body: JSON.stringify({
                    roomId: state.roomId,
                    userId: state.userId,
                    fromRow: move.from.row,
                    fromCol: move.from.col,
                    toRow: move.to.row,
                    toCol: move.to.col
                })
            });
        } catch (_) {
            state.pendingMove = false;
            setGameStatus("Khong gui duoc nuoc di. Vui long thu lai.");
        }
    }

    function leaveAndRedirect() {
        const roomId = state.roomId;
        try {
            if (state.client && state.connected && roomId && state.userId) {
                state.client.publish({
                    destination: "/app/xiangqi.leave",
                    body: JSON.stringify({ roomId, userId: state.userId })
                });
            }
            if (state.client) {
                state.client.deactivate();
            }
        } catch (_) {
        }
        const target = new URL(appPath("/online-hub"), window.location.origin);
        target.searchParams.set("game", "xiangqi");
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

    function handleSessionError(errorText) {
        const text = normalizeText(errorText).toLowerCase();
        if (!text) {
            return;
        }
        if (!text.includes("session user not found") && !text.includes("session user mismatch")) {
            return;
        }
        scheduleSessionResync();
    }

    function scheduleSessionResync() {
        const now = Date.now();
        let lastResyncAt = 0;
        try {
            lastResyncAt = Number(window.sessionStorage.getItem(SESSION_RESYNC_KEY) || "0");
        } catch (_) {
            lastResyncAt = 0;
        }
        if (Number.isFinite(lastResyncAt) && now - lastResyncAt < SESSION_RESYNC_COOLDOWN_MS) {
            return;
        }
        try {
            window.sessionStorage.setItem(SESSION_RESYNC_KEY, String(now));
        } catch (_) {
        }
        setConnectionStatus("Dang dong bo phien");
        setGameStatus("Phien dang thay doi, trang se tai lai de ket noi on dinh...");
        window.setTimeout(() => {
            try {
                window.location.reload();
            } catch (_) {
            }
        }, 900);
    }

    function clearSessionResyncMarker() {
        try {
            window.sessionStorage.removeItem(SESSION_RESYNC_KEY);
        } catch (_) {
        }
    }

    function canResetGame() {
        return Boolean(
            state.client &&
            state.connected &&
            state.roomId &&
            state.userId &&
            state.players.length >= 2 &&
            state.myColor
        );
    }

    function canSurrenderGame() {
        return Boolean(
            state.client &&
            state.connected &&
            state.roomId &&
            state.userId &&
            state.players.length >= 2 &&
            state.myColor &&
            !state.gameOver
        );
    }

    function copyBoard(board) {
        if (!Array.isArray(board) || board.length !== ROWS) {
            return createInitialBoard();
        }
        const copy = [];
        for (let r = 0; r < ROWS; r++) {
            const row = Array.isArray(board[r]) ? board[r] : [];
            copy.push(Array.from({ length: COLS }, (_, c) => {
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

    function normalizeAvatarPath(value) {
        const raw = normalizeText(value);
        if (!raw) {
            return "";
        }
        if (/^(https?:)?\/\//i.test(raw) || /^data:/i.test(raw) || /^blob:/i.test(raw)) {
            return raw;
        }
        if (raw.startsWith("/")) {
            return appPath(raw);
        }
        return appPath("/" + raw.replace(/^\.?\//, ""));
    }

    function currentUserAvatarSrc(value) {
        return normalizeAvatarPath(value) || appPath(DEFAULT_AVATAR_PATH);
    }

    function initialsOfName(name) {
        const parts = String(name || "").trim().split(/\s+/).filter(Boolean);
        if (parts.length === 0) {
            return "?";
        }
        if (parts.length === 1) {
            return parts[0].slice(0, 2).toUpperCase();
        }
        return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    }

    function buildAvatarHtml(avatarPath, displayName, className) {
        const avatarSrc = normalizeAvatarPath(avatarPath);
        if (avatarSrc) {
            return '<img class="' + className + '" src="' + escapeHtml(avatarSrc) + '" alt="Anh dai dien">';
        }
        return '<div class="' + className + ' ' + className + '--fallback" aria-hidden="true">' + escapeHtml(initialsOfName(displayName)) + '</div>';
    }

    function isGuestUserId(value) {
        return /^guest-/i.test(normalizeText(value));
    }

    function playerPresenceText(player) {
        const userId = normalizeText(player && player.userId);
        if (!userId) {
            return "Nguoi choi";
        }
        if (player && player.bot) {
            return "Bot";
        }
        if (userId === state.userId && !isGuestUserId(userId)) {
            return "Tai khoan cua ban";
        }
        return isGuestUserId(userId) ? "Khach tam thoi" : "Tai khoan";
    }

    function syncCurrentUserIdentity(nextUser) {
        const candidate = nextUser && normalizeText(nextUser.userId) === state.userId
            ? nextUser
            : (window.CaroUser && typeof window.CaroUser.get === "function" ? window.CaroUser.get() : null);
        if (candidate && normalizeText(candidate.userId) === state.userId) {
            state.displayName = normalizeText(candidate.displayName) || state.displayName || state.userId || "Khach";
            state.avatarPath = normalizeText(candidate.avatarPath) || state.avatarPath || DEFAULT_AVATAR_PATH;
            return;
        }
        state.displayName = normalizeText(state.displayName) || state.userId || "Khach";
        state.avatarPath = normalizeText(state.avatarPath) || DEFAULT_AVATAR_PATH;
    }

    function renderCurrentUserSummary() {
        if (els.userLabel) {
            els.userLabel.textContent = state.displayName || state.userId || "Khach";
        }
        if (els.userMeta) {
            els.userMeta.textContent = isGuestUserId(state.userId) ? "Khach tam thoi" : "Tai khoan dang nhap";
        }
        if (els.userAvatar) {
            els.userAvatar.setAttribute("src", currentUserAvatarSrc(state.avatarPath));
            els.userAvatar.setAttribute("alt", "Anh dai dien " + (state.displayName || "nguoi choi"));
        }
    }

    function handleCurrentUserChange(event) {
        const nextUser = event && event.detail ? event.detail.user : null;
        if (!nextUser || normalizeText(nextUser.userId) !== state.userId) {
            return;
        }
        syncCurrentUserIdentity(nextUser);
        renderCurrentUserSummary();
        renderPlayers();
    }

    function updateModeUi() {
        if (els.modeLabel) {
            els.modeLabel.textContent = state.spectate ? "Nguoi xem" : "Nguoi choi";
        }
    }

    function updateSpectateQueryInUrl(spectateMode) {
        try {
            const target = buildCurrentModeUrl(spectateMode);
            if (!target) {
                return;
            }
            const parsed = new URL(target, window.location.origin);
            window.history.replaceState({}, "", parsed.pathname + parsed.search + parsed.hash);
        } catch (_) {
        }
    }

    function buildCurrentModeUrl(spectateMode) {
        try {
            const normalizedRoomId = normalizeText(state.roomId);
            const basePath = normalizedRoomId
                ? "/xiangqi/online/room/" + encodeURIComponent(normalizedRoomId) + (spectateMode ? "/spectate" : "")
                : "/xiangqi/online";
            const url = new URL(appPath(basePath), window.location.origin);
            return url.pathname + url.search;
        } catch (_) {
            return "";
        }
    }

    function resolveRouteState() {
        const pathname = String(window.location.pathname || "").replace(/\/+$/, "");
        const roomMatch = pathname.match(/\/xiangqi\/online\/room\/([^/]+?)(?:\/(spectate))?$/i);
        const roomId = roomMatch
            ? decodeURIComponent(roomMatch[1] || "").trim()
            : String(boot.roomId || "").trim();
        const spectate = Boolean(roomMatch && roomMatch[2]);
        return { roomId, spectate };
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

    function isTypingTarget(target) {
        if (!target || !(target instanceof Element)) {
            return false;
        }
        return !!target.closest("input, textarea, select, [contenteditable='true']");
    }

    function handleHotkeys(event) {
        if (!event || event.defaultPrevented || event.repeat || event.altKey || event.ctrlKey || event.metaKey) {
            return;
        }
        if (isTypingTarget(event.target)) {
            return;
        }
        const keyName = String(event.key || "").toLowerCase();
        if (keyName === "r") {
            event.preventDefault();
            if (canResetGame()) {
                resetGame();
            } else {
                setGameStatus("Chua the bat dau van moi luc nay.");
            }
            return;
        }
        if (keyName === "s") {
            event.preventDefault();
            if (canSurrenderGame()) {
                surrenderGame();
            } else {
                setGameStatus("Khong the dau hang luc nay.");
            }
            return;
        }
        if (keyName === "l") {
            event.preventDefault();
            leaveAndRedirect();
            return;
        }
        if (keyName === "j") {
            event.preventDefault();
            requestJoinAsPlayer();
            return;
        }
        if (keyName === "v") {
            event.preventDefault();
            requestSwitchToSpectateMode();
        }
    }

    function maybeQueueBotMove() {
        if (!isBotTurn() || state.botThinking) return;
        state.botThinking = true;
        updateStatusText();
        const delay = Number(boot.botDelayMs) > 0 ? Number(boot.botDelayMs) : 450;
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
        if (!isBotTurn() || state.gameOver) return;
        const moves = collectAllLegalMoves(state.board, state.turn);
        if (moves.length === 0) return;
        const move = chooseBotMove(moves);
        if (!move) return;
        applyMove({
            from: { ...move.from },
            to: { ...move.to },
            capture: Boolean(move.capture)
        });
    }

    function chooseBotMove(moves) {
        if (state.botDifficulty !== "hard") {
            const captures = moves.filter((m) => m.capture);
            const source = captures.length > 0 ? captures : moves;
            return source[Math.floor(Math.random() * source.length)];
        }
        return chooseHardBotMove(moves);
    }

    function chooseHardBotMove(moves) {
        let bestScore = Number.NEGATIVE_INFINITY;
        let best = [];
        for (const move of moves) {
            const score = evaluateBotMove(move);
            if (score > bestScore) {
                bestScore = score;
                best = [move];
            } else if (Math.abs(score - bestScore) < 0.0001) {
                best.push(move);
            }
        }
        if (best.length === 0) {
            return moves[Math.floor(Math.random() * moves.length)];
        }
        return best[Math.floor(Math.random() * best.length)];
    }

    function evaluateBotMove(move) {
        const fromPiece = state.board[move.from.row][move.from.col];
        const captured = state.board[move.to.row][move.to.col];
        if (!fromPiece) return -99999;

        const botSide = state.turn;
        const oppSide = botSide === "r" ? "b" : "r";
        const next = applyMoveOnBoard(state.board, move);
        const movedPiece = next[move.to.row][move.to.col];

        let score = 0;
        if (captured) {
            score += pieceValue(captured) * 12;
            if (captured[1] === "G") score += 100000;
        }

        if (isGeneralInCheck(next, oppSide)) {
            score += 35;
        }
        const oppHasMove = hasAnyLegalMove(next, oppSide);
        if (!oppHasMove) {
            score += isGeneralInCheck(next, oppSide) ? 50000 : 200;
        }
        if (movedPiece && isSquareAttacked(next, move.to.row, move.to.col, oppSide)) {
            score -= pieceValue(movedPiece) * 7;
        }

        // Prefer moving rooks/cannons/horses to active center lanes
        const centerBias = 4 - Math.abs(4 - move.to.col);
        score += centerBias * 1.2;

        // Pawn advancement bonus
        if (fromPiece[1] === "P") {
            score += botSide === "r" ? (9 - move.to.row) : move.to.row;
        }

        // Reward creating flying general pressure / opening file to attack
        if (fromPiece[1] === "R" || fromPiece[1] === "C") {
            const oppGeneral = findGeneral(next, oppSide);
            if (oppGeneral && move.to.col === oppGeneral.col) {
                score += 4;
            }
        }

        return score;
    }

    function pieceValue(piece) {
        return PIECE_VALUES[piece?.[1]] || 0;
    }

    function isBotTurn() {
        return state.botEnabled && !state.gameOver && state.turn === state.botSide;
    }

    function inside(row, col) {
        return row >= 0 && row < ROWS && col >= 0 && col < COLS;
    }

    function insidePalace(row, col, side) {
        if (col < 3 || col > 5) return false;
        if (side === "r") return row >= 7 && row <= 9;
        return row >= 0 && row <= 2;
    }

    function staysOnOwnSideRiver(row, side) {
        return side === "r" ? row >= 5 : row <= 4;
    }

    function crossedRiver(row, side) {
        return side === "r" ? row <= 4 : row >= 5;
    }

    function colorName(side) {
        return side === "r" ? "Do" : "Den";
    }

    function pieceDisplayName(piece) {
        if (!piece) return "";
        return (piece[0] === "r" ? "Do " : "Den ") + (PIECE_NAMES[piece[1]] || piece[1]);
    }

    function buildMoveNotation(piece, from, to, captured) {
        const sideText = piece[0] === "r" ? "Do" : "Den";
        const action = captured ? "an" : "di";
        return sideText + " " + (PIECE_NAMES[piece[1]] || piece[1]) + " " + action + " " +
            toSquare(from.row, from.col) + " -> " + toSquare(to.row, to.col);
    }

    function toSquare(row, col) {
        return FILE_LABELS[col] + "-" + (ROWS - row);
    }

    function key(row, col) {
        return row + ":" + col;
    }
})();
