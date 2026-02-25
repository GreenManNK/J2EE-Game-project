const appPath = (window.CaroUrl && typeof window.CaroUrl.path === "function")
    ? window.CaroUrl.path
    : (value) => value;
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
window.connection = new signalR.HubConnectionBuilder()
    .withUrl(appPath("/gamehub"))
    .build();

const connection = window.connection;

let isMatching = false;
let matchmakingInterval = null;
let secondsElapsed = 0;

connection.start().then(() => {
    console.log("Kết nối Lobby thành công!");
    connection.invoke("GetRoomList");
}).catch(err => console.error("❌ Lỗi kết nối SignalR:", err));

// Cập nhật danh sách phòng
connection.on("UpdateRoomList", (rooms) => {
    const roomList = document.getElementById("roomList");
    roomList.innerHTML = "";

    if (!rooms || rooms.length === 0) {
        roomList.innerHTML = "<li class='list-group-item'>Không có phòng nào</li>";
        return;
    }

    rooms.forEach(room => {
        const li = document.createElement("li");
        li.classList.add("list-group-item");
        li.innerHTML = `Phòng <b>${room}</b> - <button onclick="joinRoom('${room}')">🔗 Tham gia</button>`;
        roomList.appendChild(li);
    });
});

// Hàm định dạng thời gian mm:ss
function formatTime(seconds) {
    const m = String(Math.floor(seconds / 60)).padStart(2, '0');
    const s = String(seconds % 60).padStart(2, '0');
    return `${m}:${s}`;
}

// Cập nhật trạng thái hiển thị
function updateMatchmakingStatus() {
    const time = formatTime(secondsElapsed);
    document.getElementById("matchmakingStatus").style.display = "block";
    document.getElementById("matchmakingStatus").innerText = `Đang tìm trận ${time}`;
}

// Bắt đầu hoặc hủy tìm trận (tùy theo trạng thái)
function toggleMatchmaking() {
    const btn = document.getElementById("matchBtn");

    if (!isMatching) {
        // Bắt đầu tìm trận
        const userId = localStorage.getItem("userId");
        const displayName = localStorage.getItem("displayName");
        const avatarPath = localStorage.getItem("avatarPath");

        if (!userId) {
            notify("Không đủ thông tin người chơi. Hãy đăng nhập lại.");
            return;
        }

        connection.invoke("FindMatch", userId, displayName, avatarPath)
            .then(() => {
                isMatching = true;
                secondsElapsed = 0;
                btn.classList.remove("btn-primary");
                btn.classList.add("btn-danger");
                btn.innerText = "⛔ Hủy tìm trận";

                updateMatchmakingStatus();
                matchmakingInterval = setInterval(() => {
                    secondsElapsed++;
                    updateMatchmakingStatus();
                }, 1000);
            })
            .catch(err => {
                console.error("❌ Lỗi khi tìm trận:", err);
                notify("Lỗi khi gửi yêu cầu tìm trận.");
            });

    } else {
        // Hủy tìm trận
        connection.invoke("CancelMatchmaking")
            .then(() => {
                isMatching = false;
                clearInterval(matchmakingInterval);
                document.getElementById("matchmakingStatus").innerText = "Đã hủy tìm trận.";
                btn.classList.remove("btn-danger");
                btn.classList.add("btn-primary");
                btn.innerText = "🔍 Tìm trận";
            })
            .catch(err => console.error("❌ Lỗi khi hủy tìm trận:", err));
    }
}

// Phòng được tạo
connection.on("RoomCreated", function (roomId) {
    console.log(`Phòng được tạo: ${roomId}`);
    document.getElementById("matchmakingStatus").style.display = "block";
    document.getElementById("matchmakingStatus").innerText = `Phòng ${roomId} đã được tạo, chờ đối thủ...`;
});

// Khi người chơi tham gia phòng
connection.on("JoinRoom", function (roomId) {
    console.log(`Tham gia phòng: ${roomId}`);
    clearInterval(matchmakingInterval);
    window.location.href = appPath(`/game?roomId=${roomId}`);
});

// Trận đấu bắt đầu
connection.on("GameStarted", function (data) {
    console.log("Trận đấu bắt đầu:", data);
    clearInterval(matchmakingInterval);
    window.location.href = appPath(`/game?roomId=${data.roomId}`);
});

// Hủy tìm trận từ server
connection.on("MatchmakingCanceled", function () {
    clearInterval(matchmakingInterval);
    document.getElementById("matchmakingStatus").innerText = "Bạn đã hủy tìm trận.";
});

// Khi đối thủ đã vào
connection.on("OpponentJoined", function (data) {
    console.log("Đối thủ đã vào:", data.displayName);
    notify(`Đối thủ ${data.displayName} đã vào phòng!`);
});

// Chờ đối thủ
connection.on("WaitingForOpponent", function () {
    document.getElementById("matchmakingStatus").style.display = "block";
    document.getElementById("matchmakingStatus").innerText = "Đang chờ đối thủ...";
});

function createRoom() {
    let roomId = document.getElementById("roomIdInput").value.trim();

    if (!roomId) {
        notify("Vui lòng nhập mã phòng!");
        return;
    }

    // Kiểm tra trước khi gắn tiền tố
    if (
        roomId.toLowerCase().startsWith("ranked_") ||
        roomId.toLowerCase().startsWith("challenge_") ||
        roomId.toLowerCase().startsWith("normal_")
    ) {
        notify("Không thể tạo phòng với mã bắt đầu bằng 'Ranked_', 'Challenge_' hoặc 'Normal_'");
        return;
    }

    // ✅ Gắn tiền tố sau khi đã kiểm tra
    roomId = `Normal_${roomId}`;

    window.location.href = appPath(`/game?roomId=${roomId}`);
}

// Hàm tham gia phòng từ danh sách
function joinRoom(roomId) {
    window.location.href = appPath(`/game?roomId=${roomId}`);
}

