document.addEventListener("DOMContentLoaded", function () {
    console.log("site.js đã được load");

    // Gán mặc định để tránh ReferenceError
    window.challengeFriend = function () {
        console.warn("⏳ Kết nối chưa sẵn sàng, vui lòng chờ...");
    };

    // ✅ Khởi tạo SignalR KỂ CẢ khi không có toast
    window.connection = new signalR.HubConnectionBuilder()
        .withUrl("/gamehub")
        .build();

    window.connection.start()
        .then(() => {
            console.log("✅ Kết nối SignalR thành công");
            window.connection.invoke("RegisterPlayerInfo");

            // Gán lại challengeFriend sau khi đã kết nối xong
            window.challengeFriend = function (friendId) {
                window.connection.invoke("SendChallenge", friendId).then((requestId) => {
                    console.log("📤 Đã gửi lời mời, chuyển đến trang chờ...");
                    window.location.href = `/Game/Waiting?requestId=${requestId}`;
                }).catch(err => {
                    console.error("❌ Lỗi khi gửi lời mời:", err);
                });
            };

            // Nhận lời mời thách đấu
            window.connection.on("ReceiveChallengeInvite", function (fromId, fromName, roomId, requestId) {
                console.log(`📩 Nhận lời mời từ ${fromName} (${fromId})`);

                let countdown = 30;
                let timer = null;

                Swal.fire({
                    title: `${fromName} thách đấu bạn!`,
                    html: `Bạn có chấp nhận không?<br><strong id="timer">30</strong>s`,
                    icon: 'question',
                    showCancelButton: true,
                    confirmButtonText: 'Chấp nhận',
                    cancelButtonText: 'Từ chối',
                    allowOutsideClick: false,
                    didOpen: () => {
                        const container = Swal.getHtmlContainer().querySelector('#timer');
                        timer = setInterval(() => {
                            countdown--;
                            if (container) container.textContent = countdown;
                            if (countdown <= 0) {
                                clearInterval(timer);
                                Swal.close();
                                window.connection.invoke("RespondToChallenge", requestId, false);
                                console.log("⏰ Hết giờ - auto từ chối");
                            }
                        }, 1000);
                    },
                    didClose: () => clearInterval(timer)
                }).then((result) => {
                    clearInterval(timer);
                    window.connection.invoke("RespondToChallenge", requestId, result.isConfirmed);
                });
            });

            // Bắt đầu game
            window.connection.on("StartChallengeGame", function (roomId) {
                console.log(`🎮 Vào phòng: ${roomId}`);
                window.location.href = `/Game/Index?roomId=${roomId}`;
            });

            // Từ chối
            window.connection.on("ChallengeDeclined", function () {
                console.log("❌ Lời mời bị từ chối hoặc hết hạn");
                Swal.fire("Lời mời đã bị từ chối hoặc hết hạn!", "", "info");
            });

            // ✅ Toast: chạy riêng nếu có
            const toastEl = document.getElementById('achievementToast');
            const toastBody = document.getElementById('toastBody');

            if (toastEl && toastBody && window.bootstrap) {
                const achievementToast = new bootstrap.Toast(toastEl, { delay: 4000 });

                window.connection.on("ShowAchievementToast", (achievementName, isBad) => {
                    const toast = document.getElementById('achievementToast');
                    const header = toast.querySelector('.toast-header');
                    const toastBody = document.getElementById('toastBody');
                    const achievementToast = new bootstrap.Toast(toast);

                    toast.classList.remove("bg-success", "bg-danger");
                    header.classList.remove("bg-success", "bg-danger");

                    toast.classList.add("text-white");
                    header.classList.add("text-white");

                    if (isBad) {
                        toast.classList.add("bg-danger");
                        header.classList.add("bg-danger");
                    } else {
                        toast.classList.add("bg-success");
                        header.classList.add("bg-success");
                    }

                    toastBody.innerHTML = `Bạn đã đạt được thành tựu <strong>${achievementName}</strong>!`;
                    achievementToast.show();
                });
            }
        })
        .catch(err => console.error("❌ Lỗi kết nối SignalR:", err));
});
