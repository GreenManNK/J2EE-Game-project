const connection = new signalR.HubConnectionBuilder()
    .withUrl("/gamehub")
    .build();
connection.start().then(() => {
    connection.invoke("RegisterPlayerInfo");
}).catch(err => console.error("❌ Kết nối thất bại:", err));

connection.on("StartGame", function (roomId) {
    window.location.href = `/Game/Index?roomId=${roomId}`;
});

connection.on("ChallengeDeclined", function () {
    window.location.href = "/Home/Index";
});
