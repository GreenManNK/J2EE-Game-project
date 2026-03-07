const appPath = (window.CaroUrl && typeof window.CaroUrl.path === "function")
    ? window.CaroUrl.path
    : (value) => value;

const connection = new signalR.HubConnectionBuilder()
    .withUrl(appPath("/gamehub"))
    .build();

connection.start().then(() => {
    connection.invoke("RegisterPlayerInfo");
}).catch(err => console.error("SignalR connect failed:", err));

connection.on("StartGame", function (roomId) {
    const normalizedRoomId = String(roomId || "").trim();
    window.location.href = appPath(`/game/room/${encodeURIComponent(normalizedRoomId)}`);
});

connection.on("ChallengeDeclined", function () {
    window.location.href = appPath("/");
});
