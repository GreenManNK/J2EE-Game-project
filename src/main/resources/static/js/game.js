// Lấy roomId từ URL
const urlParams = new URLSearchParams(window.location.search);
const roomId = urlParams.get("roomId");
const appPath = (window.CaroUrl && typeof window.CaroUrl.path === "function")
    ? window.CaroUrl.path
    : (value) => value;
const ui = window.CaroUi || {};

function notify(message, type) {
    const text = String(message || "");
    if (ui.toast) {
        ui.toast(text, { type: type || "info" });
    } else {
        if (typeof window !== "undefined" && typeof window["alert"] === "function") {
            window["alert"](text);
        } else {
            console.log(text);
        }
    }
}

//Lấy userId từ biến toàn cục do Razor truyền vào
const userId = typeof window.userId !== "undefined" ? window.userId : null;
const displayName = typeof window.displayName !== "undefined" ? window.displayName : "Ẩn danh";
const avatarPath = typeof window.avatarPath !== "undefined" ? window.avatarPath : "/uploads/avatars/default.jpg";

// Kiểm tra nếu roomId hoặc userId không hợp lệ
if (!roomId) {
    notify("❌ Không tìm thấy mã phòng!");
    throw new Error("Room ID không hợp lệ.");
}
if (!userId) {
    notify("❌ Không xác định được tài khoản người chơi!");
    throw new Error("User ID không hợp lệ.");
}

// Biến toàn cục lưu ký hiệu và lượt chơi
let currentSymbol = null;
let currentTurnSymbol = "X";

// Tạo kết nối SignalR (singleton)
if (!window.connection) {
    window.connection = new signalR.HubConnectionBuilder()
        .withUrl(appPath("/gamehub"))
        .withAutomaticReconnect()
        .build();
}
const connection = window.connection;

// Kết nối SignalR nếu chưa kết nối
if (connection.state === signalR.HubConnectionState.Disconnected) {
    connection.start().then(() => {
        console.log("Kết nối thành công!");
        if (!sessionStorage.getItem(`joinedRoom_${roomId}`)) {
            joinGame(roomId, userId, displayName, avatarPath);
        }
    }).catch(err => console.error("❌ Lỗi khi kết nối:", err));
}

// Tham gia phòng với đầy đủ thông tin
function joinGame(roomId, userId, displayName, avatarPath) {
    if (!roomId || !userId) {
        notify("Không có roomId hoặc userId.");
        return;
    }

    if (connection.state === signalR.HubConnectionState.Connected &&
        !sessionStorage.getItem(`joinedRoom_${roomId}`)) {

        console.log("JoinGame:", roomId, userId, displayName, avatarPath);

        connection.invoke("JoinGame", roomId, userId, displayName, avatarPath)
            .then(() => sessionStorage.setItem(`joinedRoom_${roomId}`, "true"))
            .catch(err => console.error("Lỗi khi tham gia phòng:", err));
    }
}

// Rời phòng
function leaveGame() {
    if (connection.state === signalR.HubConnectionState.Connected) {
        connection.invoke("LeaveGame")
            .then(() => {
                sessionStorage.removeItem(`joinedRoom_${roomId}`);
            })
            .catch(err => console.error("Lỗi khi rời phòng:", err));
    }
}

// Xử lý khi người chơi đánh dấu vào ô cờ
function makeMove(x, y) {
    if (!roomId) {
        notify("❌ Không tìm thấy Room ID!");
        return;
    }
    connection.invoke("MakeMove", roomId, x, y).catch(err => console.error(err));
}

// Cập nhật giao diện hiển thị ký hiệu người chơi
function updatePlayerSymbol() {
    let playerSymbolElement = document.getElementById("playerSymbol");
    if (playerSymbolElement) {
        playerSymbolElement.textContent = `Bạn là: ${currentSymbol}`;
    }
}

// Cập nhật trạng thái lượt chơi
function updateTurnStatus() {
    let turnStatusElement = document.getElementById("turnStatus");
    if (turnStatusElement) {
        turnStatusElement.textContent = currentSymbol === currentTurnSymbol
            ? "✅ Lượt của bạn"
            : "⏳ Đợi đối thủ...";
    }
}

// Xóa phòng
connection.on("RemoveRoom", function (roomId) {
    console.log(`Phòng ${roomId} đã bị xóa`);
    let roomElement = document.getElementById(`room-${roomId}`);
    if (roomElement) {
        roomElement.remove();
    }
});
// 📤 Gửi tin nhắn lên server
function sendMessage() {
    const messageInput = document.getElementById("message-input");
    const message = messageInput.value.trim();
    if (message) {
        connection.invoke("SendMessage", roomId, userId, message).catch(err => console.error(err));
        messageInput.value = "";
    }
}

// Reset bàn cờ
function resetBoard() {
    document.querySelectorAll(".cell").forEach(cell => {
        cell.textContent = "";
        cell.classList.remove("player-x", "player-o");
    });
    currentTurnSymbol = "X";
    updateTurnStatus();
}

// Nhận nước đi từ server
connection.on("ReceiveMove", (x, y, symbol) => {
    console.log(`Người chơi ${symbol} đánh vào ô (${x}, ${y})`);
    let cell = document.getElementById(`cell-${x}-${y}`);
    if (cell) {
        cell.textContent = symbol;
        cell.classList.add(symbol === "X" ? "player-x" : "player-o");
    }

    currentTurnSymbol = symbol === "X" ? "O" : "X";
    updateTurnStatus();
});

//Nhận sự kiện người chơi tham gia (gán symbol đúng)
connection.on("PlayerJoined", (playerId, symbol) => {
    console.log(`PlayerJoined: ${playerId} với ký hiệu ${symbol}`);
    if (playerId === userId) {
        currentSymbol = symbol;
        updatePlayerSymbol();
    }

    updateTurnStatus();
});

// Nhận thông tin đối thủ từ server
connection.on("OpponentJoined", function (opponent) {
    console.log("OpponentJoined:", opponent);
    document.getElementById("opponentName").innerText = opponent.displayName;
    document.getElementById("opponentId").innerText = opponent.userId;
    document.querySelector(".opponent-avatar img").src = opponent.avatarPath;
    document.getElementById("opponentScore").textContent = opponent.score ?? 0;
});

// Khi đối thủ rời → bạn thắng
connection.on("UpdateSymbol", (symbol) => {
    currentSymbol = symbol;
    updatePlayerSymbol();
});

// Cập nhật khi game kết thúc
connection.on("GameOver", message => {
    notify(`${message}`);

    // Nếu đối thủ rời phòng thì reset lại thông tin
    if (message.includes("Đối thủ đã rời phòng")) {
        const avatar = document.getElementById("opponentAvatar");
        const name = document.getElementById("opponentName");
        const id = document.getElementById("opponentId");
        const score = document.getElementById("opponentScore");

        if (avatar) avatar.src = "/uploads/avatars/default-opponent.jpg";
        if (name) name.textContent = "Đang chờ...";
        if (id) id.textContent = "...";
        if (score) score.textContent = "0";
    }
});

// Reset game khi chơi lại
connection.on("GameReset", () => {
    resetBoard();
    updateTurnStatus();
});

// Game bắt đầu
connection.on("GameStarted", () => {
    notify("Trò chơi bắt đầu!");
    resetBoard();
    updateTurnStatus();
});

// Chuyển về sảnh
connection.on("RedirectToLobby", function () {
    console.log("Nhận sự kiện RedirectToLobby từ server");
    window.location.href = appPath("/lobby");
});

// 🎧 Nhận tin nhắn từ server
connection.on("ReceiveMessage", function (userId, displayName, avatarPath, message) {
    const chatBox = document.getElementById("chat-box");
    const msgElement = document.createElement("div");
    msgElement.className = "chat-message";
    msgElement.innerHTML = `
        <div style="margin-bottom: 5px;">
            <img src="${avatarPath}" alt="avatar" style="width: 24px; height: 24px; border-radius: 50%; vertical-align: middle;">
            <strong style="margin-left: 5px;">${displayName}</strong>: ${message}
        </div>
    `;
    chatBox.appendChild(msgElement);
    chatBox.scrollTop = chatBox.scrollHeight;
});

connection.on("UpdateScore", function (newScore) {
    document.getElementById("myScore").textContent = newScore;
});

// gửi tin nhắn bằng phím Enter
document.getElementById("message-input").addEventListener("keydown", function (event) {
    if (event.key === "Enter") {
        event.preventDefault(); 
        sendMessage();          
    }
});

// Thông báo lỗi
connection.on("ErrorMessage", message => {
    notify(`${message}`);
});
connection.on("ForceLeave", () => {
    console.log("Nhận yêu cầu rời phòng từ server");
    connection.invoke("LeaveGame");
});




