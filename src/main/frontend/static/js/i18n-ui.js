(function () {
  const STORAGE_KEY = "caro_ui_lang";
  const SUPPORTED_LANGS = ["vi", "en"];
  const TEXT_ATTRS = ["placeholder", "title", "aria-label", "alt", "data-bs-original-title"];
  const BUTTON_INPUT_TYPES = new Set(["button", "submit", "reset"]);
  const TRANSLATABLE_INPUT_TYPES = new Set(["button", "submit", "reset", "text", "search", "email", "url", "tel", "number"]);

  let currentLang = "vi";
  let initialized = false;
  let applying = false;
  let pendingApply = false;
  let observer = null;

  const changeListeners = new Set();
  const textState = new WeakMap();
  const attrState = new WeakMap();

  const PHRASE_PAIRS = [
    { vi: "Trung tâm điều khiển", en: "Control Center", aliases: ["Trung tam dieu khien"] },
    { vi: "Trang chủ", en: "Home", aliases: ["Trang chu"] },
    { vi: "Thư viện game", en: "Game Library", aliases: ["Thu vien game"] },
    { vi: "Bảng xếp hạng", en: "Leaderboard", aliases: ["Bang xep hang"] },
    { vi: "Lịch sử đấu", en: "Match History", aliases: ["Lich su dau"] },
    { vi: "Bạn bè", en: "Friends", aliases: ["Ban be"] },
    { vi: "Thông báo", en: "Notifications", aliases: ["Thong bao"] },
    { vi: "Hồ sơ của tôi", en: "My Profile", aliases: ["Ho so cua toi"] },
    { vi: "Đăng nhập", en: "Login", aliases: ["Dang nhap"] },
    { vi: "Đăng ký", en: "Register", aliases: ["Dang ky"] },
    { vi: "Đăng xuất", en: "Logout", aliases: ["Dang xuat"] },
    { vi: "Tìm bạn...", en: "Search friends...", aliases: ["Tim ban..."] },
    { vi: "Tìm", en: "Search", aliases: ["Tim"] },
    { vi: "Chưa đăng nhập", en: "Not logged in", aliases: ["Chua dang nhap"] },
    { vi: "Đang tải danh sách bạn bè...", en: "Loading friends list...", aliases: ["Dang tai danh sach ban be..."] },
    { vi: "Chế độ", en: "Mode", aliases: ["Che do"] },
    { vi: "Truy cập nhanh", en: "Quick Access", aliases: ["Truy cap nhanh"] },
    { vi: "Cộng đồng", en: "Community", aliases: ["Cong dong"] },
    { vi: "Giao diện", en: "Interface", aliases: ["Giao dien"] },
    { vi: "Hướng dẫn nhanh", en: "Quick Guide", aliases: ["Huong dan nhanh"] },
    { vi: "Mẹo chơi", en: "Tips", aliases: ["Meo choi"] },
    { vi: "Đăng bài", en: "Publish", aliases: ["Dang bai"] },
    { vi: "Bảng tin", en: "Feed", aliases: ["Bang tin"] },
    { vi: "Bình luận", en: "Comments", aliases: ["Binh luan"] },
    { vi: "Gửi", en: "Send", aliases: ["Gui"] },
    { vi: "Nhan tin", en: "Message", aliases: ["Nhan tin"] },
    { vi: "Tin nhan rieng", en: "Private messages", aliases: ["Tin nhan rieng"] },
    { vi: "Tro ly hoi dap", en: "Assistant", aliases: ["Tro ly hoi dap"] },
    { vi: "Loi tro ly", en: "Assistant error", aliases: ["Loi tro ly"] },
    { vi: "Da nhan phan hoi tu tro ly", en: "Received assistant response", aliases: ["Da nhan phan hoi tu tro ly"] },
    { vi: "Tro ly da phan hoi", en: "Assistant responded", aliases: ["Tro ly da phan hoi"] },
    { vi: "Da tao thong bao", en: "Notification created", aliases: ["Da tao thong bao"] },
    { vi: "Chua co thong bao.", en: "No notifications yet.", aliases: ["Chua co thong bao."] },
    { vi: "Tai anh dai dien thanh cong", en: "Avatar uploaded successfully", aliases: ["Tai anh dai dien thanh cong"] },
    { vi: "Vui long chon tep anh", en: "Please choose an image file", aliases: ["Vui long chon tep anh"] },
    { vi: "Chua chon tai khoan", en: "No account selected", aliases: ["Chua chon tai khoan"] },
    { vi: "Xep hang theo diem", en: "Ranked by score", aliases: ["Xep hang theo diem"] },
    { vi: "Tra cuu tran dau theo tai khoan, ma game, ket qua, khoang ngay va xuat du lieu nhanh.", en: "Look up matches by account, game code, result, date range, and export quickly.", aliases: ["Tra cuu tran dau theo tai khoan, ma game, ket qua, khoang ngay va xuat du lieu nhanh."] },
    { vi: "Quen mat khau?", en: "Forgot password?", aliases: ["Quen mat khau?"] },
    { vi: "Dang nhap voi Google", en: "Login with Google", aliases: ["Dang nhap voi Google"] },
    { vi: "Dang nhap voi Facebook", en: "Login with Facebook", aliases: ["Dang nhap voi Facebook"] },
    { vi: "Gui ma xac thuc", en: "Send verification code", aliases: ["Gui ma xac thuc"] },
    { vi: "Dat lai mat khau", en: "Reset password", aliases: ["Dat lai mat khau"] },
    { vi: "Ma xac thuc", en: "Verification code", aliases: ["Ma xac thuc"] },
    { vi: "Duong dan anh dai dien", en: "Avatar path", aliases: ["Duong dan anh dai dien"] },
    { vi: "Tai khoan duy nhat", en: "Unique accounts", aliases: ["Tai khoan duy nhat"] },
    { vi: "Trinh duyet", en: "Browser", aliases: ["Trinh duyet"] },
    { vi: "Truy van", en: "Query", aliases: ["Truy van"] },
    { vi: "Xuat CSV trang nay", en: "Export current page CSV", aliases: ["Xuat CSV trang nay"] },
    { vi: "Xuat Excel trang nay", en: "Export current page Excel", aliases: ["Xuat Excel trang nay"] },
    { vi: "Xuat Excel tat ca da loc", en: "Export all filtered Excel", aliases: ["Xuat Excel tat ca da loc"] },
    { vi: "Da xuat tep cai dat JSON.", en: "Settings JSON exported.", aliases: ["Da xuat tep cai dat JSON."] },
    { vi: "Khong the xuat cai dat.", en: "Cannot export settings.", aliases: ["Khong the xuat cai dat."] },
    { vi: "Khong doc duoc tep cai dat.", en: "Cannot read settings file.", aliases: ["Khong doc duoc tep cai dat."] },
    { vi: "Tieng Anh", en: "English", aliases: ["Tieng Anh"] },
    { vi: "Đầu hàng", en: "Surrender", aliases: ["Dau hang"] },
    { vi: "Rời phòng", en: "Leave room", aliases: ["Roi phong"] },
    { vi: "Ván mới", en: "New match", aliases: ["Van moi"] },
    { vi: "Trạng thái", en: "Status", aliases: ["Trang thai"] },
    { vi: "Kết nối", en: "Connection", aliases: ["Ket noi"] },
    { vi: "Đang kết nối", en: "Connecting", aliases: ["Dang ket noi"] },
    { vi: "Người chơi", en: "Player", aliases: ["Nguoi choi"] },
    { vi: "Nước đi", en: "Move", aliases: ["Nuoc di"] },
    { vi: "Màu quân của bạn", en: "Your side", aliases: ["Mau quan cua ban"] },
    { vi: "Chưa chọn", en: "Not selected", aliases: ["Chua chon"] },
    { vi: "Chưa vào phòng", en: "Not in room", aliases: ["Chua vao phong"] },
    { vi: "Đang chơi", en: "In progress", aliases: ["Dang choi"] },
    { vi: "Đang chờ đối thủ", en: "Waiting for opponent", aliases: ["Dang cho doi thu", "Dang cho doi doi thu"] },
    { vi: "Đang chờ kết nối...", en: "Waiting for connection...", aliases: ["Dang cho ket noi..."] },
    { vi: "Đang khởi tạo...", en: "Initializing...", aliases: ["Dang khoi tao..."] },
    { vi: "Đang vào phòng...", en: "Joining room...", aliases: ["Dang vao phong..."] },
    { vi: "Chưa có", en: "No data yet", aliases: ["Chua co"] },
    { vi: "Không có", en: "None", aliases: ["Khong co"] },
    { vi: "Không thể", en: "Cannot", aliases: ["Khong the"] },
    { vi: "Không thể sao chép tự động. Hãy sao chép thủ công", en: "Cannot copy automatically. Please copy manually.", aliases: ["Khong the sao chep tu dong. Hay sao chep thu cong"] },
    { vi: "Không xác định được", en: "Unable to determine", aliases: ["Khong xac dinh duoc"] },
    { vi: "Không kết nối được máy chủ", en: "Cannot connect to server", aliases: ["Khong ket noi duoc may chu"] },
    { vi: "Vui lòng", en: "Please", aliases: ["Vui long"] },
    { vi: "Điều hướng", en: "Navigation", aliases: ["Dieu huong"] },
    { vi: "Danh mục", en: "Category", aliases: ["Danh muc"] },
    { vi: "Thư viện", en: "Library", aliases: ["Thu vien"] },
    { vi: "Trò chơi", en: "Games", aliases: ["Tro choi"] },
    { vi: "Gần đây", en: "Recent", aliases: ["Gan day"] },
    { vi: "Mới", en: "New", aliases: ["Moi"] },
    { vi: "Xu hướng", en: "Trending", aliases: ["Xu huong"] },
    { vi: "Cập nhật", en: "Updates", aliases: ["Cap nhat"] },
    { vi: "Nguyên bản", en: "Originals", aliases: ["Nguyen ban"] },
    { vi: "Nhiều người", en: "Multiplayer", aliases: ["Nhieu nguoi"] },
    { vi: "Đánh bài", en: "Card games", aliases: ["Danh bai"] },
    { vi: "Dò mìn", en: "Minesweeper", aliases: ["Do min"] },
    { vi: "Gõ chữ", en: "Typing", aliases: ["Go chu"] },
    { vi: "Giải đố", en: "Puzzle", aliases: ["Giai do"] },
    { vi: "Phòng online", en: "Room hub", aliases: ["Sanh phong", "Phong online", "Phong truc tuyen"] },
    { vi: "Tài khoản", en: "Account", aliases: ["Tai khoan"] },
    { vi: "Cài đặt", en: "Settings", aliases: ["Cai dat"] },
    { vi: "Yêu thích", en: "Favorites", aliases: ["Yeu thich"] },
    { vi: "Kích hoạt Admin", en: "Activate admin", aliases: ["Kich hoat quan tri", "Kich hoat Admin"] },
    { vi: "Quản lý người dùng", en: "User management", aliases: ["Quan ly nguoi dung"] },
    { vi: "Trung tâm Admin", en: "Admin center", aliases: ["Trung tam quan tri", "Trung tam Admin"] },
    { vi: "Ẩn sidebar", en: "Hide sidebar", aliases: ["An sidebar"] },
    { vi: "Hiện sidebar", en: "Show sidebar", aliases: ["Hien sidebar"] },
    { vi: "Đóng sidebar", en: "Close sidebar", aliases: ["Dong sidebar"] },
    { vi: "Mở sidebar", en: "Open sidebar", aliases: ["Mo sidebar"] },
    { vi: "Bật/tắt sidebar", en: "Toggle sidebar", aliases: ["Bat/tat sidebar"] },
    { vi: "Ảnh đại diện", en: "Avatar", aliases: ["Anh dai dien"] },
    { vi: "Trực tuyến", en: "Online", aliases: ["Truc tuyen"] },
    { vi: "Ngoại tuyến", en: "Offline", aliases: ["Ngoai tuyen"] },
    { vi: "Bạn cần đăng nhập", en: "Login required", aliases: ["Ban can dang nhap"] },
    { vi: "Email là bắt buộc", en: "Email is required", aliases: ["Email la bat buoc"] },
    { vi: "Xử lý thành công", en: "Operation completed successfully", aliases: ["Xu ly thanh cong"] },
    { vi: "Xác thực email thành công", en: "Email verified successfully", aliases: ["Xac thuc email thanh cong"] },
    { vi: "Gửi lại mã", en: "Resend code", aliases: ["Gui lai ma"] },
    { vi: "Đã sao chép link mời", en: "Invite link copied", aliases: ["Da sao chep link moi"] },
    { vi: "Đã sao chép link xem", en: "Spectator link copied", aliases: ["Da sao chep link xem"] },
    { vi: "Mã kích hoạt là bắt buộc", en: "Activation code is required", aliases: ["Ma kich hoat la bat buoc"] },
    { vi: "Đã kích hoạt vai trò Admin", en: "Admin access activated", aliases: ["Da kich hoat quyen quan tri", "Da kich hoat vai tro Admin"] },
    { vi: "Không thể kích hoạt vai trò Admin", en: "Cannot activate admin access", aliases: ["Khong the kich hoat quyen quan tri", "Khong the kich hoat vai tro Admin"] },
    { vi: "Chưa có bạn bè nào.", en: "No friends yet.", aliases: ["Chua co ban be nao."] },
    { vi: "Hiện chưa có bạn nào đang online.", en: "No friends are currently online.", aliases: ["Hien chua co ban nao dang online."] },
    { vi: "Không tải được danh sách bạn bè.", en: "Unable to load friends list.", aliases: ["Khong tai duoc danh sach ban be."] },
    { vi: "Tự động", en: "Auto", aliases: ["Tu dong"] },
    { vi: "Sáng", en: "Light", aliases: ["Sang"] },
    { vi: "Tối", en: "Dark", aliases: ["Toi"] },
    { vi: "Bấm để chuyển sang", en: "Click to switch to", aliases: ["Bam de chuyen sang"] },
    { vi: "Chuyển giao diện", en: "Switch theme", aliases: ["Chuyen giao dien"] },
    { vi: "tiếp theo", en: "next", aliases: ["tiep theo"] },
    { vi: "Nhập mã kích hoạt Admin:", en: "Enter admin activation code:", aliases: ["Nhap ma kich hoat quan tri:", "Nhap ma kich hoat Admin:"] },
    { vi: "Điều khiển giao diện", en: "Interface controls", aliases: ["Dieu khien giao dien", "Interface controls"] },
    { vi: "Cài đặt giao diện và thông báo", en: "Theme and notification settings", aliases: ["Cai dat giao dien va thong bao"] },
    { vi: "Chế độ giao diện", en: "Theme mode", aliases: ["Che do giao dien"] },
    { vi: "Theo hệ thống", en: "Follow system", aliases: ["Theo he thong"] },
    { vi: "Ngôn ngữ", en: "Language", aliases: ["Ngon ngu"] },
    { vi: "Tiếng Việt", en: "Vietnamese", aliases: ["Tieng Viet"] },
    { vi: "Thông báo và quyền riêng tư", en: "Notifications and privacy", aliases: ["Thong bao va quyen rieng tu"] },
    { vi: "Sidebar desktop mở sẵn mặc định", en: "Keep desktop sidebar open by default", aliases: ["Sidebar desktop mo san mac dinh"] },
    { vi: "Tự động đóng sidebar mobile sau khi bấm menu", en: "Auto-close mobile sidebar after tapping a menu item", aliases: ["Tu dong dong sidebar mobile sau khi bam menu"] },
    { vi: "Bật nhạc trang chủ", en: "Enable home music", aliases: ["Bat nhac trang chu"] },
    { vi: "Bật toast thông báo toàn hệ thống", en: "Enable system toast notifications", aliases: ["Bat toast thong bao toan he thong"] },
    { vi: "Hiển thị bạn ngoại tuyến trong sidebar", en: "Show offline friends in the sidebar", aliases: ["Hien thi ban ngoai tuyen trong sidebar"] },
    { vi: "Tự động làm mới danh sách bạn bè", en: "Auto-refresh the friends list", aliases: ["Tu dong lam moi danh sach ban be"] },
    { vi: "Chu kỳ làm mới bạn bè", en: "Friends refresh interval", aliases: ["Chu ky lam moi ban be"] },
    { vi: "Lưu tùy chọn", en: "Save preferences", aliases: ["Luu tuy chon"] },
    { vi: "Về mặc định", en: "Reset to defaults", aliases: ["Ve mac dinh"] },
    { vi: "Xuất cài đặt", en: "Export settings", aliases: ["Xuat cai dat"] },
    { vi: "Nhập cài đặt", en: "Import settings", aliases: ["Nhap cai dat"] },
    { vi: "Nâng cao", en: "Advanced", aliases: ["Nang cao", "Advanced"] },
    { vi: "Bảo mật nâng cao", en: "Advanced security", aliases: ["Bao mat nang cao"] },
    { vi: "Mã kích hoạt quản trị", en: "Admin activation code", aliases: ["Ma kich hoat quan tri"] },
    { vi: "Nhập mã kích hoạt", en: "Enter activation code", aliases: ["Nhap ma kich hoat"] },
    { vi: "Kích hoạt", en: "Activate", aliases: ["Kich hoat"] },
    { vi: "5 giây", en: "5 seconds", aliases: ["5 giay"] },
    { vi: "10 giây", en: "10 seconds", aliases: ["10 giay"] },
    { vi: "15 giây", en: "15 seconds", aliases: ["15 giay"] },
    { vi: "20 giây", en: "20 seconds", aliases: ["20 giay"] },
    { vi: "30 giây", en: "30 seconds", aliases: ["30 giay"] },
    { vi: "60 giây", en: "60 seconds", aliases: ["60 giay"] },
    { vi: "Tên người dùng là bắt buộc.", en: "Username is required.", aliases: ["Ten nguoi dung la bat buoc."] },
    { vi: "Tên người dùng cần 6-20 ký tự và chỉ dùng chữ cái, số, . hoặc _.", en: "Username must be 6-20 characters and use only letters, digits, . or _.", aliases: ["Ten nguoi dung can 6-20 ky tu va chi dung chu cai, so, . hoac _."] },
    { vi: "Đang kiểm tra tên người dùng...", en: "Checking username...", aliases: ["Dang kiem tra ten nguoi dung..."] },
    { vi: "Tên người dùng hợp lệ và có sẵn.", en: "Username is valid and available.", aliases: ["Ten nguoi dung hop le va co san."] },
    { vi: "Tên người dùng không hợp lệ", en: "Username is invalid", aliases: ["Ten nguoi dung khong hop le"] },
    { vi: "Không thể kiểm tra tên người dùng lúc này.", en: "Cannot check the username right now.", aliases: ["Khong the kiem tra ten nguoi dung luc nay."] },
    { vi: "Đã lưu cài đặt", en: "Settings saved", aliases: ["Da luu cai dat"] },
    { vi: "(đã đồng bộ tài khoản).", en: "(synced to your account).", aliases: ["(da dong bo tai khoan)."] },
    { vi: "Không thể lưu cài đặt", en: "Cannot save settings", aliases: ["Khong the luu cai dat"] },
    { vi: "Không thể lưu tùy chọn vào tài khoản", en: "Cannot save preferences to your account", aliases: ["Khong the luu tuy chon vao tai khoan"] },
    { vi: "Đã đưa tất cả tùy chọn về mặc định.", en: "All preferences were reset to defaults.", aliases: ["Da dua tat ca tuy chon ve mac dinh."] },
    { vi: "Không thể reset cài đặt", en: "Cannot reset settings", aliases: ["Khong the reset cai dat"] },
    { vi: "Đã import cài đặt thành công.", en: "Settings imported successfully.", aliases: ["Da import cai dat thanh cong."] },
    { vi: "File cài đặt không hợp lệ.", en: "The settings file is invalid.", aliases: ["File cai dat khong hop le."] },
    { vi: "Không thể import cài đặt", en: "Cannot import settings", aliases: ["Khong the import cai dat"] },
    { vi: "Không thể tải avatar", en: "Cannot upload avatar", aliases: ["Khong the tai avatar"] },
    { vi: "Vui lòng nhập tên người dùng hợp lệ.", en: "Please enter a valid username.", aliases: ["Vui long nhap ten nguoi dung hop le."] },
    { vi: "Không thể cập nhật hồ sơ", en: "Cannot update profile", aliases: ["Khong the cap nhat ho so"] },
    { vi: "Nhập lại mật khẩu mới không khớp.", en: "The new password confirmation does not match.", aliases: ["Nhap lai mat khau moi khong khop."] },
    { vi: "Không thể đổi mật khẩu", en: "Cannot change password", aliases: ["Khong the doi mat khau"] },
    { vi: "Không thể hủy liên kết", en: "Cannot unlink", aliases: ["Khong the huy lien ket"] },
    { vi: "Khám phá", en: "Explore", aliases: ["Kham pha"] },
    { vi: "Trò chơi & lịch sử", en: "Games & history", aliases: ["Tro choi & lich su", "Tro choi va lich su"] },
    { vi: "Đang online", en: "Online now", aliases: ["Dang online"] },
    { vi: "Lịch sử chơi", en: "Play history", aliases: ["Lich su choi"] },
    { vi: "Cờ vua", en: "Chess", aliases: ["Co vua"] },
    { vi: "Cờ tỷ phú", en: "Monopoly", aliases: ["Co ty phu"] },
    { vi: "Người chơi & xã hội", en: "Players & social", aliases: ["Nguoi choi & xa hoi", "Nguoi choi va xa hoi"] },
    { vi: "Trợ lý chat", en: "Chat assistant", aliases: ["Tro ly chat"] },
    { vi: "Cài đặt tài khoản", en: "Account settings", aliases: ["Cai dat tai khoan"] },
    { vi: "Trung tâm cài đặt", en: "Settings hub", aliases: ["Trung tam cai dat"] },
    { vi: "Hồ sơ & tài khoản", en: "Profile & account", aliases: ["Ho so & tai khoan", "Ho so va tai khoan"] },
    { vi: "Thông báo giao diện", en: "Interface notifications", aliases: ["Thong bao giao dien"] },
    { vi: "Quyền riêng tư", en: "Privacy", aliases: ["Quyen rieng tu"] },
    { vi: "Kích hoạt quản trị", en: "Activate admin", aliases: ["Kich hoat quan tri"] },
    { vi: "Quản lý tài khoản", en: "Account management", aliases: ["Quan ly tai khoan"] },
    { vi: "Trung tâm quản trị", en: "Admin center", aliases: ["Trung tam quan tri"] },
    { vi: "Trung tâm báo cáo", en: "Report center", aliases: ["Trung tam bao cao"] },
    { vi: "Trạng thái trực tuyến/ngoại tuyến cập nhật mỗi 5 giây", en: "Online/offline status updates every 5 seconds", aliases: ["Trang thai truc tuyen/ngoai tuyen cap nhat moi 5 giay"] },
    { vi: "Tìm kiếm trò chơi và danh mục", en: "Search games and categories", aliases: ["Tim kiem tro choi va danh muc"] },
    { vi: "Cài đặt app desktop", en: "Install desktop app", aliases: ["Cai dat app desktop"] },
    { vi: "Đồng bộ hồ sơ, cài đặt và thông báo cho toàn bộ Game Hub.", en: "Sync profile, settings, and notifications across Game Hub.", aliases: ["Dong bo ho so, cai dat va thong bao cho toan bo Game Hub."] },
    { vi: "Đang tải thông báo...", en: "Loading notifications...", aliases: ["Dang tai thong bao..."] },
    { vi: "Tìm kiếm bạn bè mới hoặc hiện tại", en: "Search for new or existing friends", aliases: ["Tim kiem ban be moi hoac hien tai"] },
    { vi: "Chơi với bạn bè", en: "Play with friends", aliases: ["Choi voi ban be"] },
    { vi: "Chia sẻ hồ sơ", en: "Share profile", aliases: ["Chia se ho so"] },
    { vi: "Tùy chọn tài khoản", en: "Account options", aliases: ["Tuy chon tai khoan"] },
    { vi: "Tùy chọn thông báo", en: "Notification preferences", aliases: ["Tuy chon thong bao"] },
    { vi: "Tùy chọn quyền riêng tư", en: "Privacy preferences", aliases: ["Tuy chon quyen rieng tu"] },
    { vi: "Hồ sơ", en: "Profile", aliases: ["Ho so"] },
    { vi: "Chỉnh sửa tài khoản", en: "Edit account", aliases: ["Chinh sua tai khoan"] },
    { vi: "Đóng panel tài khoản", en: "Close account panel", aliases: ["Dong panel tai khoan"] },
    { vi: "Tài khoản hiện tại", en: "Current account", aliases: ["Tai khoan hien tai"] },
    { vi: "game đang sẵn sàng", en: "games ready now", aliases: ["game dang san sang"] },
    { vi: "Duyệt kiểu thư mục", en: "Directory-style browse", aliases: ["Directory-style browse"] },
    { vi: "Lane ngang + card lớn", en: "Horizontal lanes + large cards", aliases: ["Lane ngang + card lon"] },
    { vi: "1 click vào detail", en: "One click to details", aliases: ["1 click vao detail"] },
    { vi: "Game Hub theo phong cách arcade directory, tối ưu cho việc tìm game và vào trận nhanh", en: "Game Hub in an arcade-directory style, optimized for finding games and jumping into matches fast", aliases: ["Game Hub theo phong cach arcade directory, toi uu cho viec tim game va vao tran nhanh"] },
    { vi: "Toàn bộ home được đẩy sang giao diện card-first: một recommended strip đầu trang, các lane ngang cho từng nhóm game, và khu cộng đồng đẩy xuống phía dưới để không làm loạn luồng khám phá.", en: "The homepage uses a card-first layout: a recommended strip at the top, horizontal lanes for each game group, and the community section pushed lower to keep discovery focused.", aliases: ["Toan bo home duoc day sang giao dien card-first: mot recommended strip dau trang, cac lane ngang cho tung nhom game, va khu cong dong day xuong phia duoi de khong lam loan luong kham pha."] },
    { vi: "Mở thư viện", en: "Open library", aliases: ["Mo thu vien"] },
    { vi: "Sảnh online", en: "Online lobby", aliases: ["Sanh online"] },
    { vi: "Lựa chọn hàng đầu dành cho bạn", en: "Top picks for you", aliases: ["Lua chon hang dau danh cho ban"] },
    { vi: "Bố cục card lớn + tile nhỏ lấy cảm hứng từ home của game portal", en: "Large cards and small tiles inspired by game portal homepages", aliases: ["Bo cuc card lon + tile nho lay cam hung tu home cua game portal"] },
    { vi: "Xem thêm", en: "View more", aliases: ["Xem them"] },
    { vi: "Mới cập nhật", en: "Freshly updated", aliases: ["Moi cap nhat"] },
    { vi: "game native đang hiển thị trong hub", en: "native games currently shown in the hub", aliases: ["game native dang hien thi trong hub"] },
    { vi: "tựa game có phòng online realtime", en: "titles with real-time online rooms", aliases: ["tua game co phong online realtime"] },
    { vi: "vào nhanh được với guest/local", en: "quick start with guest/local", aliases: ["vao nhanh duoc voi guest/local"] },
    { vi: "bài viết mới từ người chơi trong hub", en: "new posts from players in the hub", aliases: ["bai viet moi tu nguoi choi trong hub"] },
    { vi: "Trò chơi nổi bật", en: "Featured games", aliases: ["Tro choi noi bat"] },
    { vi: "Nổi bật online", en: "Hot online", aliases: ["Hot online"] },
    { vi: "Đề xuất", en: "Recommended", aliases: ["Recommended"] },
    { vi: "Chi tiết", en: "Details", aliases: ["Chi tiet"] },
    { vi: "Chơi online cùng bạn bè", en: "Play online with friends", aliases: ["Choi online cung ban be"] },
    { vi: "Mở sảnh", en: "Open lobby", aliases: ["Mo sanh"] },
    { vi: "Quick play và casual", en: "Quick play and casual", aliases: ["Quick play va casual"] },
    { vi: "Board, chiến thuật và arcade", en: "Board, strategy, and arcade", aliases: ["Board, chien thuat va arcade"] },
    { vi: "Chiến thuật", en: "Strategy", aliases: ["Strategy", "Chien thuat"] },
    { vi: "Mới cập nhật trong hub", en: "Recently updated in the hub", aliases: ["Moi cap nhat trong hub"] },
    { vi: "Xem tất cả", en: "View all", aliases: ["Xem tat ca"] },
    { vi: "Cộng đồng Game Hub", en: "Game Hub community", aliases: ["Cong dong Game Hub"] },
    { vi: "Feed được đẩy xuống dưới để home ưu tiên discovery trước", en: "The feed is pushed lower so the homepage prioritizes discovery first", aliases: ["Feed duoc day xuong duoi de home uu tien discovery truoc"] },
    { vi: "Đăng bài viết mới", en: "Create a new post", aliases: ["Dang bai viet moi"] },
    { vi: "Cập nhật nhanh cho cộng đồng", en: "Quick update for the community", aliases: ["Cap nhat nhanh cho cong dong"] },
    { vi: "Bạn đang nghĩ gì?", en: "What's on your mind?", aliases: ["Ban dang nghi gi?"] },
    { vi: "Tác giả:", en: "Author:", aliases: ["Tac gia:"] },
    { vi: "Cần đăng nhập", en: "Sign in required", aliases: ["Can dang nhap"] },
    { vi: "Đăng bài", en: "Publish", aliases: ["Dang bai"] },
    { vi: "bài viết", en: "posts", aliases: ["bai viet"] },
    { vi: "Chưa có bài đăng nào.", en: "No posts yet.", aliases: ["Chua co bai dang nao."] },
    { vi: "Xóa bài viết", en: "Delete post", aliases: ["Xoa bai viet"] },
    { vi: "Ảnh bài viết", en: "Post image", aliases: ["Anh bai viet"] },
    { vi: "Bình luận", en: "Comments", aliases: ["Binh luan"] },
    { vi: "Chưa có bình luận", en: "No comments yet", aliases: ["Chua co binh luan"] },
    { vi: "Viết bình luận...", en: "Write a comment...", aliases: ["Viet binh luan..."] },
    { vi: "Tắt nhạc", en: "Mute music", aliases: ["Tat nhac"] },
    { vi: "Bật nhạc", en: "Play music", aliases: ["Bat nhac"] },
    { vi: "Cần đăng nhập để đăng bài", en: "Sign in to publish a post", aliases: ["Can dang nhap de dang bai"] },
    { vi: "Không đăng bài được", en: "Cannot publish the post", aliases: ["Khong dang bai duoc"] },
    { vi: "Cần đăng nhập để bình luận", en: "Sign in to comment", aliases: ["Can dang nhap de binh luan"] },
    { vi: "Không gửi bình luận được", en: "Cannot send the comment", aliases: ["Khong gui binh luan duoc"] },
    { vi: "Bạn chắc chắn muốn xóa bài viết này?", en: "Are you sure you want to delete this post?", aliases: ["Ban chac chan muon xoa bai viet nay?"] },
    { vi: "Không thể xóa bài viết", en: "Cannot delete this post", aliases: ["Khong the xoa bai viet"] },
    { vi: "Đã xóa bài viết", en: "Post deleted", aliases: ["Da xoa bai viet"] },
    { vi: "Cờ tướng", en: "Xiangqi", aliases: ["Co tuong"] },
    { vi: "Dò mìn", en: "Minesweeper", aliases: ["Do min"] },
    { vi: "Đào vàng", en: "Gold Miner", aliases: ["Dao vang"] },
    { vi: "Đánh bài", en: "Card games", aliases: ["Danh bai"] },
    { vi: "Caro đã hỗ trợ online room, offline 2 người, bot Easy/Hard và guest.", en: "Caro supports online rooms, local 2-player mode, Easy/Hard bots, and guest access.", aliases: ["Caro da ho tro online room, offline 2 nguoi, bot Easy/Hard va guest."] },
    { vi: "Vào Caro", en: "Open Caro", aliases: ["Vao Caro"] },
    { vi: "Lobby + room invite + ws realtime.", en: "Lobby, room invites, and real-time WebSocket play.", aliases: ["Lobby + room invite + ws realtime."] },
    { vi: "Bot Easy/Hard và lịch sử trận đấu.", en: "Easy/Hard bots and match history.", aliases: ["Bot Easy/Hard va lich su tran dau."] },
    { vi: "Guest mode và profile đăng nhập.", en: "Guest mode and signed-in profiles.", aliases: ["Guest mode va profile dang nhap."] },
    { vi: "Cờ vua đã có online room 1v1, bot Easy/Hard và offline 2 người cùng máy.", en: "Chess now has 1v1 online rooms, Easy/Hard bots, and local 2-player mode on the same device.", aliases: ["Co vua da co online room 1v1, bot Easy/Hard va offline 2 nguoi cung may."] },
    { vi: "Mở Cờ vua", en: "Open Chess", aliases: ["Mo Co vua"] },
    { vi: "Bàn cờ 8x8 và setup quân cờ đầy đủ.", en: "8x8 board with the full chess setup.", aliases: ["Ban co 8x8 va setup quan co day du."] },
    { vi: "Bot Easy/Hard và chế độ offline local.", en: "Easy/Hard bots and local offline mode.", aliases: ["Bot Easy/Hard va che do offline local."] },
    { vi: "Phòng online và đồng bộ nước đi realtime.", en: "Online rooms and real-time move syncing.", aliases: ["Phong online va dong bo nuoc di realtime."] },
    { vi: "Cờ tướng đã có online room, bot Easy/Hard và offline 2 người cùng máy.", en: "Xiangqi now has online rooms, Easy/Hard bots, and local 2-player mode on the same device.", aliases: ["Co tuong da co online room, bot Easy/Hard va offline 2 nguoi cung may."] },
    { vi: "Mở Cờ tướng", en: "Open Xiangqi", aliases: ["Mo Co tuong"] },
    { vi: "Bot Easy/Hard đã sẵn sàng.", en: "Easy/Hard bots are ready.", aliases: ["Bot Easy/Hard da san sang."] },
    { vi: "Offline 2 người cùng máy.", en: "Local 2-player mode on the same device.", aliases: ["Offline 2 nguoi cung may."] },
    { vi: "Minesweeper offline với beginner/intermediate/expert, có cắm cờ và first-click safe.", en: "Offline Minesweeper with beginner/intermediate/expert modes, flags, and first-click safety.", aliases: ["Minesweeper offline voi beginner/intermediate/expert, co cam co va first-click safe."] },
    { vi: "Mở Minesweeper", en: "Open Minesweeper", aliases: ["Mo Minesweeper"] },
    { vi: "3 mức độ beginner/intermediate/expert.", en: "Three difficulty levels: beginner, intermediate, and expert.", aliases: ["3 muc do beginner/intermediate/expert."] },
    { vi: "Cắm cờ và first-click safe.", en: "Flags and first-click safety.", aliases: ["Cam co va first-click safe."] },
    { vi: "Cập nhật achievement khi thắng.", en: "Achievements update when you win.", aliases: ["Cap nhat achievement khi thang."] },
    { vi: "Đào vàng browser với móc quay, TNT, 3 vòng mục tiêu điểm và nâng cấp giữa các vòng.", en: "Browser Gold Miner with a swinging hook, TNT, 3 score-target rounds, and upgrades between rounds.", aliases: ["Dao vang browser voi moc quay, TNT, 3 vong muc tieu diem va nang cap giua cac vong."] },
    { vi: "Chơi Đào vàng", en: "Play Gold Miner", aliases: ["Choi Dao vang"] },
    { vi: "Móc quay, thả dây và kéo về theo trọng lượng vật phẩm.", en: "Swing the hook, drop the line, and reel items back based on their weight.", aliases: ["Moc quay, tha day va keo ve theo trong luong vat pham."] },
    { vi: "Vàng, đá, kim cương, túi bí ẩn và TNT để cắt lỗ.", en: "Gold, rocks, diamonds, mystery bags, and TNT to cut losses.", aliases: ["Vang, da, kim cuong, tui bi an va TNT de cat lo."] },
    { vi: "Qua 3 vòng với shop nâng cấp nhỏ giữa mỗi vòng.", en: "Clear 3 rounds with a small upgrade shop between rounds.", aliases: ["Qua 3 vong voi shop nang cap nho giua moi vong."] },
    { vi: "Module Cards đã có Tiến Lên online 4 người, bot Easy/Hard và thêm mode Blackjack.", en: "The Cards module now includes 4-player online Tien Len, Easy/Hard bots, and Blackjack.", aliases: ["Module Cards da co Tien Len online 4 nguoi, bot Easy/Hard va them mode Blackjack."] },
    { vi: "Mở Cards hub", en: "Open Cards hub", aliases: ["Mo Cards hub"] },
    { vi: "Tiến Lên online 4 người + room realtime.", en: "4-player online Tien Len with real-time rooms.", aliases: ["Tien Len online 4 nguoi + room realtime."] },
    { vi: "Tiến Lên bot Easy/Hard và bộ luật mở rộng.", en: "Tien Len Easy/Hard bots and expanded rules.", aliases: ["Tien Len bot Easy/Hard va bo luat mo rong."] },
    { vi: "Blackjack với bot dealer đã có route riêng.", en: "Blackjack with a bot dealer now has its own dedicated route.", aliases: ["Blackjack voi bot dealer da co route rieng."] },
    { vi: "Blackjack đã có room realtime, dealer bot và bàn local PvP cùng máy.", en: "Blackjack now has real-time rooms, a bot dealer, and local PvP on the same device.", aliases: ["Blackjack da co room realtime, dealer bot va ban local PvP cung may."] },
    { vi: "Mở Blackjack", en: "Open Blackjack", aliases: ["Mo Blackjack"] },
    { vi: "Tạo/join/spectate room blackjack.", en: "Create, join, or spectate blackjack rooms.", aliases: ["Tao/join/spectate room blackjack."] },
    { vi: "Bàn local 2-4 người cùng máy với dealer.", en: "Local 2-4 player tables on the same device with a dealer.", aliases: ["Ban local 2-4 nguoi cung may voi dealer."] },
    { vi: "Đặt cược + hit/stand/double theo luật cơ bản.", en: "Bet, hit, stand, and double with the core rules.", aliases: ["Dat cuoc + hit/stand/double theo luat co ban."] },
    { vi: "Quiz đã có room realtime, luyện tập local và đấu bot Easy/Hard.", en: "Quiz now has real-time rooms, local practice, and Easy/Hard bot matches.", aliases: ["Quiz da co room realtime, luyen tap local va dau bot Easy/Hard."] },
    { vi: "Mở Quiz", en: "Open Quiz", aliases: ["Mo Quiz"] },
    { vi: "Tạo/join/spectate room quiz.", en: "Create, join, or spectate quiz rooms.", aliases: ["Tao/join/spectate room quiz."] },
    { vi: "Nhiều dạng câu hỏi: single, multiple, typed.", en: "Multiple question types: single, multiple, and typed.", aliases: ["Nhieu dang cau hoi: single, multiple, typed."] },
    { vi: "Luyện tập local và đấu bot theo nhạc câu hỏi.", en: "Practice locally and challenge bots with timed question music.", aliases: ["Luyen tap local va dau bot theo nhac cau hoi."] },
    { vi: "Theo dõi high score.", en: "Track the high score.", aliases: ["Theo doi high score."] },
    { vi: "Typing Battle đã có room realtime, chế độ practice local và race với bot.", en: "Typing Battle now has real-time rooms, local practice mode, and bot races.", aliases: ["Typing Battle da co room realtime, che do practice local va race voi bot."] },
    { vi: "Mở Typing Battle", en: "Open Typing Battle", aliases: ["Mo Typing Battle"] },
    { vi: "Tạo/join room typing realtime.", en: "Create and join real-time typing rooms.", aliases: ["Tao/join room typing realtime."] },
    { vi: "Theo dõi progress + accuracy của từng người chơi.", en: "Track each player's progress and accuracy.", aliases: ["Theo doi progress + accuracy cua tung nguoi choi."] },
    { vi: "Practice local và race với bot Easy/Hard.", en: "Practice locally and race against Easy/Hard bots.", aliases: ["Practice local va race voi bot Easy/Hard."] },
    { vi: "Thông báo winner khi kết thúc.", en: "Announce the winner when the match ends.", aliases: ["Thong bao winner khi ket thuc."] },
    { vi: "Puzzle pack gồm Jigsaw, Sliding, Word Puzzle và Sudoku.", en: "The puzzle pack includes Jigsaw, Sliding, Word Puzzle, and Sudoku.", aliases: ["Puzzle pack gom Jigsaw, Sliding, Word Puzzle va Sudoku."] },
    { vi: "Mở Puzzle Pack", en: "Open Puzzle Pack", aliases: ["Mo Puzzle Pack"] },
    { vi: "Cờ tỷ phú đã chơi được local 2-4 người và room mode MVP, với board 40 ô, chance/community, nhà-hotel, thế chấp và bảng xếp hạng tài sản realtime.", en: "Monopoly supports local 2-4 players and MVP room mode, with a 40-tile board, chance/community cards, houses-hotels, mortgages, and real-time asset rankings.", aliases: ["Co ty phu da choi duoc local 2-4 nguoi va room mode MVP, voi board 40 o, chance/community, nha-hotel, the chap va bang xep hang tai san realtime."] },
    { vi: "Mở Cờ tỷ phú", en: "Open Monopoly", aliases: ["Mo Co ty phu"] },
    { vi: "Board 40 ô + room MVP", en: "40-tile board + MVP room", aliases: ["Board 40 o + room MVP"] },
    { vi: "Monopoly / Lobby", en: "Monopoly / Lobby", aliases: ["Monopoly / Lobby"] },
    { vi: "Room 2-4 người", en: "2-4 player rooms", aliases: ["Room 2-4 nguoi"] },
    { vi: "Nhà / hotel / thế chấp", en: "Houses / hotels / mortgages", aliases: ["Nha / hotel / the chap"] },
    { vi: "1 người + 1-3 bot", en: "1 player + 1-3 bots", aliases: ["1 nguoi + 1-3 bot"] },
    { vi: "40 ô / 8 nhóm màu / 4 nhà ga", en: "40 tiles / 8 color groups / 4 railroads", aliases: ["40 o / 8 nhom mau / 4 nha ga"] },
    { vi: "Chơi local", en: "Play local", aliases: ["Choi local"] },
    { vi: "Bot dễ", en: "Easy bot", aliases: ["Bot de", "Bot De"] },
    { vi: "Bot khó", en: "Hard bot", aliases: ["Bot kho", "Bot Kho"] },
    { vi: "Mở sảnh room", en: "Open room lobby", aliases: ["Mo sanh room"] },
    { vi: "Về lobby", en: "Back to lobby", aliases: ["Ve lobby"] },
    { vi: "Về setup bot", en: "Back to bot setup", aliases: ["Ve setup bot"] },
    { vi: "Nhịp vào trận thống nhất", en: "Unified match flow", aliases: ["Nhip vao tran thong nhat"] },
    { vi: "Chọn đúng mode", en: "Choose the right mode", aliases: ["Chon dung mode"] },
    { vi: "Vào ván ngay", en: "Jump into a match", aliases: ["Vao van ngay"] },
    { vi: "Giữ nhịp sau trận", en: "Keep the post-match flow", aliases: ["Giu nhip sau tran"] },
    { vi: "Online, bot, local và room route được đặt gần nhau để vào trận nhanh hơn.", en: "Online, bot, local, and room routes are grouped closely so you can enter a match faster.", aliases: ["Online, bot, local va room route duoc dat gan nhau de vao tran nhanh hon."] },
    { vi: "Phòng chờ, random join hoặc setup local không bị tách thành nhiều lớp điều hướng.", en: "Waiting rooms, random join, and local setup stay in one flow instead of being split across navigation layers.", aliases: ["Phong cho, random join hoac setup local khong bi tach thanh nhieu lop dieu huong."] },
    { vi: "Lịch sử chơi, BXH và jump link nằm ngay trong loop gameplay để đổi mode nhanh.", en: "Play history, leaderboard, and jump links stay inside the gameplay loop so you can switch modes quickly.", aliases: ["Lich su choi, BXH va jump link nam ngay trong loop gameplay de doi mode nhanh."] },
    { vi: "Room 2-4 người + local + bot", en: "2-4 player rooms + local + bots", aliases: ["Room 2-4 nguoi + local + bot"] },
    { vi: "Vốn, deal, trade, bot AI", en: "Cash, deals, trades, bot AI", aliases: ["Von, deal, trade, bot AI"] },
    { vi: "Mở bot mode", en: "Open bot mode", aliases: ["Mo bot mode"] },
    { vi: "Room online", en: "Online rooms", aliases: ["Room online"] },
    { vi: "2-4 người", en: "2-4 players", aliases: ["2-4 nguoi"] },
    { vi: "Room riêng", en: "Private rooms", aliases: ["Room rieng"] },
    { vi: "Local cùng máy", en: "Same-device local", aliases: ["Local cung may"] },
    { vi: "Setup nhanh", en: "Quick setup", aliases: ["Setup nhanh"] },
    { vi: "Chọn vốn khởi điểm", en: "Choose starting cash", aliases: ["Chon von khoi diem"] },
    { vi: "Mở local mode", en: "Open local mode", aliases: ["Mo local mode"] },
    { vi: "Chơi với bot", en: "Play against bots", aliases: ["Choi voi bot"] },
    { vi: "Bot mode", en: "Bot mode", aliases: ["Bot mode"] },
    { vi: "Bot tự động đánh", en: "Bots play automatically", aliases: ["Bot tu dong danh"] },
    { vi: "Lưu lịch sử", en: "Save history", aliases: ["Luu lich su"] },
    { vi: "Monopoly / Bot setup", en: "Monopoly / Bot setup", aliases: ["Monopoly / Bot setup"] },
    { vi: "Sảnh bot riêng", en: "Dedicated bot lobby", aliases: ["Sanh bot rieng"] },
    { vi: "Trang này chỉ dùng để chọn số ghế, vốn và độ khó. Sau khi bắt đầu, hệ thống sẽ mở bàn chơi bot ở màn riêng.", en: "This page is only for choosing seat count, cash, and difficulty. After you start, the system opens the bot board on a separate screen.", aliases: ["Trang nay chi dung de chon so ghe, von va do kho. Sau khi bat dau, he thong se mo ban choi bot o man rieng."] },
    { vi: "Monopoly / Bot mode", en: "Monopoly / Bot mode", aliases: ["Monopoly / Bot mode"] },
    { vi: "Phòng bot đã mở", en: "Bot room opened", aliases: ["Phong bot da mo"] },
    { vi: "Đây là màn bàn chơi riêng. Setup bot đã được tách ra khỏi sảnh và board chỉ còn hiện ở trang này.", en: "This is the dedicated board screen. Bot setup has been separated from the lobby, and the board now appears only on this page.", aliases: ["Day la man ban choi rieng. Setup bot da duoc tach ra khoi sanh va board chi con hien o trang nay."] },
    { vi: "Phòng Cờ tỷ phú online", en: "Online Monopoly room", aliases: ["Phong Co ty phu online"] },
    { vi: "tập trung vào HUD, bàn chơi và thao tác trong ván.", en: "focuses on the HUD, board, and in-match actions.", aliases: ["tap trung vao HUD, ban choi va thao tac trong van."] },
    { vi: "Bàn chơi Cờ tỷ phú local", en: "Local Monopoly board", aliases: ["Ban choi Co ty phu local"] },
    { vi: "Chơi 2-4 người trên cùng thiết bị với setup và bảng xếp hạng tại chỗ.", en: "Play 2-4 players on the same device with local setup and on-table standings.", aliases: ["Choi 2-4 nguoi tren cung thiet bi voi setup va bang xep hang tai cho."] },
    { vi: "Bàn Cờ tỷ phú với bot", en: "Monopoly board vs bots", aliases: ["Ban Co ty phu voi bot"] },
    { vi: "Setup bot mode ở đây, sau đó vào bàn chơi riêng để đánh. Không để board nằm chung với trang sảnh nữa.", en: "Set up bot mode here, then move to the dedicated board to play. The board is no longer mixed into the lobby page.", aliases: ["Setup bot mode o day, sau do vao ban choi rieng de danh. Khong de board nam chung voi trang sanh nua."] },
    { vi: "Phòng Cờ tỷ phú với bot", en: "Monopoly room vs bots", aliases: ["Phong Co ty phu voi bot"] },
    { vi: "Sảnh phòng Cờ tỷ phú", en: "Monopoly room lobby", aliases: ["Sanh phong Co ty phu"] },
    { vi: "Chọn tên, tạo room, vào room đang mở hoặc sang local mode nếu muốn chơi cùng máy.", en: "Choose a name, create a room, join an open room, or switch to local mode if you want same-device play.", aliases: ["Chon ten, tao room, vao room dang mo hoac sang local mode neu muon choi cung may."] },
    { vi: "Room route riêng", en: "Dedicated room route", aliases: ["Room route rieng"] },
    { vi: "Đồng bộ người chơi + token", en: "Player + token sync", aliases: ["Dong bo nguoi choi + token"] },
    { vi: "BXH giá trị ròng realtime", en: "Real-time net worth leaderboard", aliases: ["BXH gia tri rong realtime"] },
    { vi: "Board 40 ô", en: "40-tile board", aliases: ["Board 40 o"] },
    { vi: "Starting cash tùy chọn", en: "Custom starting cash", aliases: ["Starting cash tuy chon"] },
    { vi: "Lobby riêng", en: "Dedicated lobby", aliases: ["Lobby rieng"] },
    { vi: "Room page riêng", en: "Dedicated room page", aliases: ["Room page rieng"] },
    { vi: "Local page riêng", en: "Dedicated local page", aliases: ["Local page rieng"] },
    { vi: "Phòng chờ Monopoly", en: "Monopoly waiting room", aliases: ["Phong cho Monopoly"] },
    { vi: "Room sync MVP", en: "MVP room sync", aliases: ["Room sync MVP"] },
    { vi: "Tên của bạn", en: "Your name", aliases: ["Ten cua ban"] },
    { vi: "Tên phòng", en: "Room name", aliases: ["Ten phong"] },
    { vi: "Số chỗ tối đa", en: "Max seats", aliases: ["So cho toi da"] },
    { vi: "2 chỗ", en: "2 seats", aliases: ["2 cho"] },
    { vi: "3 chỗ", en: "3 seats", aliases: ["3 cho"] },
    { vi: "4 chỗ", en: "4 seats", aliases: ["4 cho"] },
    { vi: "Vào phòng", en: "Join room", aliases: ["Vao phong"] },
    { vi: "Tạo room, vào ngay và giữ quyền host", en: "Create a room, join immediately, and keep host control", aliases: ["Tao room, vao ngay va giu quyen host"] },
    { vi: "Tải lại phòng mở", en: "Refresh open rooms", aliases: ["Tai lai phong mo"] },
    { vi: "Cập nhật danh sách room đang chờ người", en: "Refresh the list of rooms waiting for players", aliases: ["Cap nhat danh sach room dang cho nguoi"] },
    { vi: "Sao chép link mời", en: "Copy invite link", aliases: ["Sao chep link moi"] },
    { vi: "Gửi room code và link vào phòng nhanh", en: "Share the room code and quick join link", aliases: ["Gui room code va link vao phong nhanh"] },
    { vi: "Host bắt đầu game", en: "Host starts the game", aliases: ["Host bat dau game"] },
    { vi: "Phòng đang mở", en: "Open rooms", aliases: ["Phong dang mo"] },
    { vi: "Rút 1 thẻ cộng đồng.", en: "Draw 1 community card.", aliases: ["Rut 1 the cong dong."] },
    { vi: "Rút 1 thẻ cộng đồng", en: "Draw 1 community card", aliases: ["Rut 1 the cong dong"] },
    { vi: "Tiến tới nhà ga gần nhất. Nếu đã có chủ, trả gấp đôi tiền thuê.", en: "Advance to the nearest railroad. If it is owned, pay double rent.", aliases: ["Tien toi nha ga gan nhat. Neu da co chu, tra gap doi tien thue."] },
    { vi: "Đề nghị trade không còn hợp lệ", en: "The trade offer is no longer valid", aliases: ["De nghi trade khong con hop le"] },
    { vi: "gửi đề nghị trade", en: "send trade offer", aliases: ["gui de nghi trade"] },
    { vi: "Chưa có room nào để sao chép.", en: "No room is available to copy.", aliases: ["Chua co room nao de sao chep."] },
    { vi: "⬅ Quay lại", en: "⬅ Back", aliases: ["⬅ Quay lai"] },
    { vi: "Mode hiện tại", en: "Current mode", aliases: ["Mode hien tai"] },
    { vi: "Phiên chơi", en: "Session", aliases: ["Phien choi"] },
    { vi: "Kỹ năng trung tâm", en: "Core skill", aliases: ["Ky nang trung tam"] },
    { vi: "Đọc trận", en: "Read the match", aliases: ["Doc tran"] },
    { vi: "Mở mode chính", en: "Open main mode", aliases: ["Mo mode chinh"] },
    { vi: "Mode thứ hai", en: "Secondary mode", aliases: ["Mode thu hai"] },
    { vi: "Mở cài đặt thông báo", en: "Open notification settings", aliases: ["Mo cai dat thong bao"] },
    { vi: "Mở trang bạn bè", en: "Open friends page", aliases: ["Mo trang ban be"] },
    { vi: "Đóng danh sách bạn bè", en: "Close friends list", aliases: ["Dong danh sach ban be"] },
    { vi: "Bạn là người chơi thật, các ghế còn lại được bot tự động điều khiển theo mức dễ hoặc mức khó.", en: "You are the human player; the remaining seats are controlled by bots at Easy or Hard difficulty.", aliases: ["Ban la nguoi choi that, cac ghe con lai duoc bot tu dong dieu khien theo muc de hoac muc kho."] },
    { vi: "Lưu vào lịch sử bot", en: "Save to bot history", aliases: ["Luu vao lich su bot"] },
    { vi: "Tạo phòng", en: "Create room", aliases: ["Tao phong"] },
    { vi: "Thông tin room", en: "Room details", aliases: ["Thong tin room"] },
    { vi: "Đang đồng bộ thông tin room...", en: "Syncing room details...", aliases: ["Dang dong bo thong tin room..."] },
    { vi: "Setup ván local", en: "Set up a local match", aliases: ["Setup van local"] },
    { vi: "Bạn là người chơi 1. Tổng số ghế sẽ quyết định số bot tham gia. Mức hiện tại:", en: "You are player 1. The total seat count determines how many bots join. Current setting:", aliases: ["Ban la nguoi choi 1. Tong so ghe se quyet dinh so bot tham gia. Muc hien tai:"] },
    { vi: "Dễ", en: "Easy", aliases: ["De"] },
    { vi: "Khó", en: "Hard", aliases: ["Kho"] },
    { vi: "Về local thường", en: "Back to local mode", aliases: ["Ve local thuong"] },
    { vi: "2 người", en: "2 players", aliases: ["2 nguoi"] },
    { vi: "3 người", en: "3 players", aliases: ["3 nguoi"] },
    { vi: "4 người", en: "4 players", aliases: ["4 nguoi"] },
    { vi: "Vốn khởi điểm", en: "Starting cash", aliases: ["Von khoi diem"] },
    { vi: "1500$ / cơ bản", en: "1500$ / standard", aliases: ["1500$ / co ban"] },
    { vi: "2000$ / mở rộng", en: "2000$ / extended", aliases: ["2000$ / mo rong"] },
    { vi: "Ở chế độ bot, các slot 2-4 sẽ được khóa tên và đổi thành bot tự động theo số ghế đã chọn.", en: "In bot mode, seats 2-4 are locked and converted into automatic bots based on the selected seat count.", aliases: ["O che do bot, cac slot 2-4 se duoc khoa ten va doi thanh bot tu dong theo so ghe da chon."] },
    { vi: "Bắt đầu ván mới", en: "Start a new match", aliases: ["Bat dau van moi"] },
    { vi: "Tạo bàn chơi mới theo setup phía trên", en: "Create a new board from the setup above", aliases: ["Tao ban choi moi theo setup phia tren"] },
    { vi: "Điều khiển lượt", en: "Turn controls", aliases: ["Dieu khien luot"] },
    { vi: "Bỏ lượt", en: "Pass", aliases: ["Bo luot"] },
    { vi: "Mua ô đang đứng", en: "Buy current tile", aliases: ["Mua o dang dung"] },
    { vi: "Kết thúc lượt", en: "End turn", aliases: ["Ket thuc luot"] },
    { vi: "Nộp 50$ để ra tù", en: "Pay $50 to leave jail", aliases: ["Nop 50$ de ra tu"] },
    { vi: "Dùng thẻ ra tù", en: "Use jail-free card", aliases: ["Dung the ra tu"] },
    { vi: "Chốt ván và chấm điểm", en: "Finalize match and score it", aliases: ["Chot van va cham diem"] },
    { vi: "Chi tiết ô đã chọn", en: "Selected tile details", aliases: ["Chi tiet o da chon"] },
    { vi: "Nhật ký ván đấu", en: "Match log", aliases: ["Nhat ky van dau"] },
    { vi: "Nhập giá đặt", en: "Enter bid amount", aliases: ["Nhap gia dat"] },
    { vi: "Tỷ phú online", en: "Online Monopoly", aliases: ["Ty phu online"] },
    { vi: "Bàn Cờ tỷ phú", en: "Monopoly table", aliases: ["Ban Co ty phu"] },
    { vi: "Tỷ phú 1", en: "Monopoly 1", aliases: ["Ty phu 1"] },
    { vi: "Tỷ phú 2", en: "Monopoly 2", aliases: ["Ty phu 2"] },
    { vi: "Tỷ phú 3", en: "Monopoly 3", aliases: ["Ty phu 3"] },
    { vi: "Tỷ phú 4", en: "Monopoly 4", aliases: ["Ty phu 4"] },
    { vi: "Turn tự động", en: "Automatic turns", aliases: ["Turn tu dong"] },
    { vi: "Còn người chơi", en: "Players left", aliases: ["Con nguoi choi"] },
    { vi: "Đã phá sản", en: "Bankrupt", aliases: ["Da pha san"] },
    { vi: "Tạm mất kết nối - đang giữ chỗ trong room", en: "Temporarily disconnected - seat kept in the room", aliases: ["Tam mat ket noi - dang giu cho trong room"] },
    { vi: "đang nợ", en: "owes", aliases: ["dang no"] },
    { vi: "ngân hàng", en: "the bank", aliases: ["ngan hang"] },
    { vi: ". Bán nhà/thế chấp để cân bằng hoặc tuyên bố phá sản.", en: ". Sell houses or mortgage properties to recover, or declare bankruptcy.", aliases: [". Ban nha/the chap de can bang hoac tuyen bo pha san."] },
    { vi: "Đấu giá:", en: "Auction:", aliases: ["Dau gia:"] },
    { vi: "Giá hiện tại:", en: "Current bid:", aliases: ["Gia hien tai:"] },
    { vi: "Đang dẫn:", en: "Leading:", aliases: ["Dang dan:"] },
    { vi: "Lượt hiện tại:", en: "Current turn:", aliases: ["Luot hien tai:"] },
    { vi: "Đang cập nhật", en: "Updating", aliases: ["Dang cap nhat"] },
    { vi: "Chọn setup và bắt đầu ván mới.", en: "Choose the setup and start a new match.", aliases: ["Chon setup va bat dau van moi."] },
    { vi: "đang tạm mất kết nối. Cho người chơi này vào lại room để tiếp tục lượt.", en: "is temporarily disconnected. Let this player rejoin the room to continue the turn.", aliases: ["dang tam mat ket noi. Cho nguoi choi nay vao lai room de tiep tuc luot."] },
    { vi: "đang tự xử lý lượt theo chiến lược bot.", en: "is processing the turn automatically with bot logic.", aliases: ["dang tu xu ly luot theo chien luoc bot."] },
    { vi: "Đang chờ", en: "Waiting for", aliases: ["Dang cho"] },
    { vi: "thao tác và đồng bộ lượt.", en: "to act and sync the turn.", aliases: ["thao tac va dong bo luot."] },
    { vi: "dẫn đầu và ván đấu đã kết thúc.", en: "is leading and the match has ended.", aliases: ["dan dau va van dau da ket thuc."] },
    { vi: "Ván đấu đã kết thúc.", en: "The match has ended.", aliases: ["Van dau da ket thuc."] },
    { vi: "đang cần xoay vốn để trả nợ.", en: "needs to raise cash to pay the debt.", aliases: ["dang can xoay von de tra no."] },
    { vi: "Đang đấu giá", en: "Auctioning", aliases: ["Dang dau gia"] },
    { vi: "Người chơi hiện tại", en: "Current player", aliases: ["Nguoi choi hien tai"] },
    { vi: "đang được ra giá tiếp theo.", en: "is next to bid.", aliases: ["dang duoc ra gia tiep theo."] },
    { vi: "đang đánh giá đề nghị trade.", en: "is reviewing the trade offer.", aliases: ["dang danh gia de nghi trade."] },
    { vi: "Đang mở trade với", en: "Trade open with", aliases: ["Dang mo trade voi"] },
    { vi: ". Trao máy cho người chơi này để chấp nhận hoặc từ chối.", en: ". Pass the device to this player to accept or decline.", aliases: [". Trao may cho nguoi choi nay de chap nhan hoac tu choi."] },
    { vi: ". Chờ chấp nhận hoặc từ chối để tiếp tục lượt.", en: ". Wait for an accept or decline response to continue the turn.", aliases: [". Cho chap nhan hoac tu choi de tiep tuc luot."] },
    { vi: "đang có quyền mua", en: "can buy", aliases: ["dang co quyen mua"] },
    { vi: "đang trong tù. Có thể nộp 50$, dùng thẻ hoặc tiếp tục tung xúc xắc.", en: "is in jail. You can pay $50, use a card, or keep rolling.", aliases: ["dang trong tu. Co the nop 50$, dung the hoac tiep tuc tung xuc xac."] },
    { vi: "vừa tung double và được thêm 1 lượt tung.", en: "rolled doubles and earned one extra roll.", aliases: ["vua tung double va duoc them 1 luot tung."] },
    { vi: "Tung xúc xắc để tiếp tục ván đấu.", en: "Roll the dice to continue the match.", aliases: ["Tung xuc xac de tiep tuc van dau."] },
    { vi: "Ra tù", en: "Leave jail", aliases: ["Ra tu"] },
    { vi: "Mua tài sản", en: "Buy property", aliases: ["Mua tai san"] },
    { vi: "Thẻ ra tù", en: "Jail-free card", aliases: ["The ra tu"] },
    { vi: "Mở đấu giá", en: "Open auction", aliases: ["Mo dau gia"] },
    { vi: "Bỏ lượt đấu giá", en: "Pass auction turn", aliases: ["Bo luot dau gia"] },
    { vi: "Kết quả đấu giá", en: "Auction result", aliases: ["Ket qua dau gia"] },
    { vi: "Hủy đấu giá", en: "Cancel auction", aliases: ["Huy dau gia"] },
    { vi: "Không ai mua", en: "No buyer", aliases: ["Khong ai mua"] },
    { vi: ". Lượt được tiếp tục.", en: ". The turn continues.", aliases: [". Luot duoc tiep tuc."] },
    { vi: "Chọn đối tác trade trước khi gửi đề nghị.", en: "Choose a trade partner before sending the offer.", aliases: ["Chon doi tac trade truoc khi gui de nghi."] },
    { vi: "Người nhận trade không hợp lệ", en: "Invalid trade recipient", aliases: ["Nguoi nhan trade khong hop le"] },
    { vi: "Đề nghị trade đang rỗng", en: "The trade offer is empty", aliases: ["De nghi trade dang rong"] },
    { vi: "Bạn không đủ tiền mặt để đưa vào trade", en: "You do not have enough cash to include in the trade", aliases: ["Ban khong du tien mat de dua vao trade"] },
    { vi: "Đối tác không đủ tiền mặt theo đề nghị", en: "The partner does not have enough cash for this trade", aliases: ["Doi tac khong du tien mat theo de nghi"] },
    { vi: "Đề nghị trade", en: "Trade offer", aliases: ["De nghi trade"] },
    { vi: "gửi đề nghị trao đổi cho", en: "sent a trade offer to", aliases: ["gui de nghi trao doi cho"] },
    { vi: "đã thông qua một giao dịch.", en: "completed a trade.", aliases: ["da thong qua mot giao dich."] },
    { vi: "Bot từ chối trade", en: "Bot declined the trade", aliases: ["Bot tu choi trade"] },
    { vi: "Từ chối trade", en: "Decline trade", aliases: ["Tu choi trade"] },
    { vi: "từ chối đề nghị trade.", en: "declined the trade offer.", aliases: ["tu choi de nghi trade."] },
    { vi: "Bạn không sở hữu đầy đủ các tài sản đã chọn", en: "You do not own all selected properties", aliases: ["Ban khong so huu day du cac tai san da chon"] },
    { vi: "Đối tác không sở hữu đầy đủ các tài sản được yêu cầu", en: "The partner does not own all requested properties", aliases: ["Doi tac khong so huu day du cac tai san duoc yeu cau"] },
    { vi: "Không thể trade tài sản đang có nhà/hotel", en: "Cannot trade a property that still has houses or hotels", aliases: ["Khong the trade tai san dang co nha/hotel"] },
    { vi: "Phải bán hết nhà/hotel trong nhóm trước khi trade", en: "All houses and hotels in the group must be sold before trading", aliases: ["Phai ban het nha/hotel trong nhom truoc khi trade"] },
    { vi: "Đã vào phòng. Chọn token và chờ host mở ván.", en: "Joined the room. Choose a token and wait for the host to start.", aliases: ["Da vao phong. Chon token va cho host mo van."] },
    { vi: "Không tải được danh sách phòng.", en: "Unable to load the room list.", aliases: ["Khong tai duoc danh sach phong."] },
    { vi: "Room hiện tại đang ở trong trận. Bấm 'Rời phòng' trước nếu muốn mở room mới.", en: "The current room is already in a match. Click 'Leave room' first if you want to open a new room.", aliases: ["Room hien tai dang o trong tran. Bam 'Roi phong' truoc neu muon mo room moi."] },
    { vi: "Đã tạo phòng", en: "Room created", aliases: ["Da tao phong"] },
    { vi: ". Chia mã phòng và mời thêm người.", en: ". Share the room code and invite more players.", aliases: [". Chia ma phong va moi them nguoi."] },
    { vi: "Không tạo được phòng.", en: "Unable to create the room.", aliases: ["Khong tao duoc phong."] },
    { vi: "Nhập mã phòng trước khi bấm vào phòng.", en: "Enter a room code before joining.", aliases: ["Nhap ma phong truoc khi bam vao phong."] },
    { vi: "Không vào được phòng.", en: "Unable to join the room.", aliases: ["Khong vao duoc phong."] },
    { vi: "Đã chọn token", en: "Token selected", aliases: ["Da chon token"] },
    { vi: "Không đổi được token.", en: "Unable to change the token.", aliases: ["Khong doi duoc token."] },
    { vi: "Không bắt đầu được phòng.", en: "Unable to start the room.", aliases: ["Khong bat dau duoc phong."] },
    { vi: "Không xác định được người chơi hiện tại.", en: "Unable to determine the current player.", aliases: ["Khong xac dinh duoc nguoi choi hien tai."] },
    { vi: "Không thực hiện được hành động trong room.", en: "Unable to perform the room action.", aliases: ["Khong thuc hien duoc hanh dong trong room."] },
    { vi: "Không thể sao chép tự động. Hãy copy mã phòng", en: "Cannot copy automatically. Please copy the room code", aliases: ["Khong the sao chep tu dong. Hay copy ma phong"] },
    { vi: "đang khởi tạo state ván đấu. Chờ đồng bộ...", en: "is initializing the match state. Waiting for sync...", aliases: ["dang khoi tao state van dau. Cho dong bo..."] },
    { vi: "đang chờ host bắt đầu.", en: "is waiting for the host to start.", aliases: ["dang cho host bat dau."] },
    { vi: "Phòng bắt đầu", en: "Room started", aliases: ["Phong bat dau"] },
    { vi: "đã vào ván.", en: "entered the match.", aliases: ["da vao van."] },
    { vi: "Đồng bộ state room thất bại.", en: "Room state sync failed.", aliases: ["Dong bo state room that bai."] },
    { vi: "Yêu cầu Monopoly không thành công.", en: "Monopoly request failed.", aliases: ["Yeu cau Monopoly khong thanh cong."] },
    { vi: "Chợ", en: "Market", aliases: ["Cho"] },
    { vi: "Vàng", en: "Gold", aliases: ["Vang"] },
    { vi: "Nhà ga", en: "Railroad", aliases: ["Nha ga"] },
    { vi: "Đi qua nhận 200$.", en: "Collect $200 when passing GO.", aliases: ["Di qua nhan 200$."] },
    { vi: "Đóng 120$ vào quỹ free parking.", en: "Pay $120 into the free parking pot.", aliases: ["Dong 120$ vao quy free parking."] },
    { vi: "Ga Sài Gòn", en: "Saigon Station", aliases: ["Ga Sai Gon"] },
    { vi: "Rút 1 thẻ cơ hội.", en: "Draw 1 chance card.", aliases: ["Rut 1 the co hoi."] },
    { vi: "Phú Nhuận", en: "Phu Nhuan", aliases: ["Phu Nhuan"] },
    { vi: "Vào thăm hoặc chờ ra tù.", en: "Just visiting or waiting to get out of jail.", aliases: ["Vao tham hoac cho ra tu."] },
    { vi: "Ga Hà Nội", en: "Hanoi Station", aliases: ["Ga Ha Noi"] },
    { vi: "Nhận toàn bộ quỹ jackpot.", en: "Collect the full jackpot pot.", aliases: ["Nhan toan bo quy jackpot."] },
    { vi: "Ga Đà Nẵng", en: "Da Nang Station", aliases: ["Ga Da Nang"] },
    { vi: "Cấp nước", en: "Water Works", aliases: ["Cap nuoc"] },
    { vi: "Phú Mỹ Hưng", en: "Phu My Hung", aliases: ["Phu My Hung"] },
    { vi: "Đi thẳng vào tù.", en: "Go directly to jail.", aliases: ["Di thang vao tu."] },
    { vi: "Văn Miếu", en: "Temple of Literature", aliases: ["Van Mieu"] },
    { vi: "Ga Nha Trang", en: "Nha Trang Station", aliases: ["Ga Nha Trang"] },
    { vi: "Đóng 100$ vào quỹ free parking.", en: "Pay $100 into the free parking pot.", aliases: ["Dong 100$ vao quy free parking."] },
    { vi: "Tiến tới GO và nhận 200$.", en: "Advance to GO and collect $200.", aliases: ["Tien toi GO va nhan 200$."] },
    { vi: "Tiến tới Hải Phòng Port.", en: "Advance to Hai Phong Port.", aliases: ["Tien toi Hai Phong Port."] },
    { vi: "Cổ tức ngân hàng: nhận 50$.", en: "Bank dividend: collect $50.", aliases: ["Co tuc ngan hang: nhan 50$."] },
    { vi: "Sửa chữa tài sản: 25$/nhà, 100$/hotel.", en: "Property repairs: $25 per house, $100 per hotel.", aliases: ["Sua chua tai san: 25$/nha, 100$/hotel."] },
    { vi: "Giữ thẻ này để ra tù miễn phí.", en: "Keep this card to get out of jail free.", aliases: ["Giu the nay de ra tu mien phi."] },
    { vi: "Lỗi hệ thống ngân hàng: nhận 200$.", en: "Bank error in your favor: collect $200.", aliases: ["Loi he thong ngan hang: nhan 200$."] },
    { vi: "Hoàn trả phúc lợi: nhận 100$.", en: "Benefit refund: collect $100.", aliases: ["Hoan tra phuc loi: nhan 100$."] },
    { vi: "Bán tài sản nhỏ: nhận 45$.", en: "Sell a small asset: collect $45.", aliases: ["Ban tai san nho: nhan 45$."] },
    { vi: "Sửa hệ thống nhà cửa: nộp 100$.", en: "Housing maintenance: pay $100.", aliases: ["Sua he thong nha cua: nop 100$."] },
    { vi: "Phí tư vấn thành công: nhận 25$.", en: "Consulting fee earned: collect $25.", aliases: ["Phi tu van thanh cong: nhan 25$."] },
    { vi: "Bảo trì nhà đất: 40$/nhà, 115$/hotel.", en: "Property maintenance: $40 per house, $115 per hotel.", aliases: ["Bao tri nha dat: 40$/nha, 115$/hotel."] },
    { vi: "Bị triệu tập: vào tù.", en: "Summoned: go to jail.", aliases: ["Bi trieu tap: vao tu."] },
    { vi: "Monopoly đã tách room, local và bot thành 3 luồng vào trận riêng để dễ chọn đúng mode ngay từ đầu.", en: "Monopoly splits room, local, and bot play into three separate entry flows so you can choose the right mode from the start.", aliases: ["Monopoly da tach room, local va bot thanh 3 luong vao tran rieng de de chon dung mode ngay tu dau."] },
    { vi: "Ván đấu được chốt bằng tay.", en: "The match was finalized manually.", aliases: ["Van dau duoc chot bang tay."] },
    { vi: "Nhập mức giá hợp lệ trước khi đặt giá.", en: "Enter a valid bid before placing it.", aliases: ["Nhap muc gia hop le truoc khi dat gia."] },
    { vi: "Đang ở chế độ phòng. Rời phòng nếu muốn mở ván local riêng.", en: "You are in room mode. Leave the room if you want to open a separate local match.", aliases: ["Dang o che do phong. Roi phong neu muon mo van local rieng."] },
    { vi: "Bắt đầu ván mới? Tiến trình hiện tại sẽ mất.", en: "Start a new match? Current progress will be lost.", aliases: ["Bat dau van moi? Tien trinh hien tai se mat."] },
    { vi: "Tỷ phú", en: "Monopoly", aliases: ["Ty phu"] },
    { vi: "Bàn chơi đã sẵn sàng.", en: "The board is ready.", aliases: ["Ban choi da san sang."] },
    { vi: "Lượt", en: "Turn", aliases: ["Luot"] },
    { vi: "Lượt của", en: "Turn of", aliases: ["Luot cua"] },
    { vi: "Bàn chơi Monopoly", en: "Monopoly board", aliases: ["Ban choi Monopoly"] },
    { vi: "Đang đứng ở", en: "Standing on", aliases: ["Dang dung o"] },
    { vi: "Tài sản", en: "Property", aliases: ["Tai san", "tai san"] },
    { vi: "Từ chối", en: "Decline", aliases: ["Tu choi"] },
    { vi: "Thế chấp", en: "Mortgage", aliases: ["The chap", "the chap"] },
    { vi: "Nhà / hotel", en: "Houses / hotels", aliases: ["Nha / hotel"] },
    { vi: "Đã thế chấp", en: "Mortgaged", aliases: ["Da the chap"] },
    { vi: "nhà/hotel", en: "houses/hotels", aliases: ["nha/hotel"] },
    { vi: "· thế chấp", en: "· mortgaged", aliases: ["· the chap"] },
    { vi: "Rút thẻ", en: "Draw card", aliases: ["Rut the"] },
    { vi: "Nộp vào quỹ chung", en: "Pay into the common pot", aliases: ["Nop vao quy chung"] },
    { vi: "Rút 1 thẻ cơ hội", en: "Draw 1 chance card", aliases: ["Rut 1 the co hoi"] },
    { vi: "Không lĩnh thưởng GO", en: "No GO reward", aliases: ["Khong linh thuong GO"] },
    { vi: "Nhận thưởng khi đi qua", en: "Collect reward when passing", aliases: ["Nhan thuong khi di qua"] },
    { vi: "và chi phí nhà", en: "and housing costs", aliases: ["va chi phi nha"] },
    { vi: "Đang thuộc về", en: "Owned by", aliases: ["Dang thuoc ve"] },
    { vi: "Tài sản đang được thế chấp.", en: "This property is mortgaged.", aliases: ["Tai san dang duoc the chap."] },
    { vi: "Tiền thuê hiện tại", en: "Current rent", aliases: ["Tien thue hien tai"] },
    { vi: ", tiền thuê phụ thuộc số ga đang sở hữu.", en: ", rent depends on how many railroads are owned.", aliases: [", tien thue phu thuoc so ga dang so huu."] },
    { vi: ", tiền thuê dựa trên tổng xúc xắc.", en: ", rent is based on the total dice roll.", aliases: [", tien thue dua tren tong xuc xac."] },
    { vi: "vào quỹ free parking.", en: "into the free parking pot.", aliases: ["vao quy free parking."] },
    { vi: "Ô đặc biệt trên bàn cờ.", en: "A special tile on the board.", aliases: ["O dac biet tren ban co."] },
    { vi: "Xây nhà (", en: "Build house (", aliases: ["Xay nha ("] },
    { vi: "Bán nhà (+", en: "Sell house (+", aliases: ["Ban nha (+"] },
    { vi: "Thế chấp (+", en: "Mortgage (+", aliases: ["The chap (+"] },
    { vi: "bị đưa vào tù vì double 3 lần liên tục.", en: "was sent to jail for rolling doubles three times in a row.", aliases: ["bi dua vao tu vi double 3 lan lien tuc."] },
    { vi: "tung double và được ra tù.", en: "rolled doubles and got out of jail.", aliases: ["tung double va duoc ra tu."] },
    { vi: "Tiền bảo lãnh sau 3 lượt trong tù.", en: "Bail after 3 turns in jail.", aliases: ["Tien bao lanh sau 3 luot trong tu."] },
    { vi: "hết 3 lượt trong tù và phải nộp 50$ để tiếp tục.", en: "spent 3 turns in jail and must pay $50 to continue.", aliases: ["het 3 luot trong tu va phai nop 50$ de tiep tuc."] },
    { vi: "Ra tù sau 3 lượt.", en: "Leave jail after 3 turns.", aliases: ["Ra tu sau 3 luot."] },
    { vi: "chưa ra tù và phải chờ lượt sau.", en: "did not get out of jail and must wait for the next turn.", aliases: ["chua ra tu va phai cho luot sau."] },
    { vi: ". Phiên đấu giá đã được mở.", en: ". The auction has started.", aliases: [". Phien dau gia da duoc mo."] },
    { vi: "thắng đấu giá", en: "won the auction", aliases: ["thang dau gia"] },
    { vi: "lên mức", en: "to", aliases: ["len muc"] },
    { vi: "Bán nhà", en: "Sell house", aliases: ["Ban nha", "ban nha"] },
    { vi: "bán 1 cấp nhà tại", en: "sold one house level on", aliases: ["ban 1 cap nha tai"] },
    { vi: "Thẻ sự kiện", en: "Event card", aliases: ["The su kien"] },
    { vi: "Không thu tiền", en: "No fee charged", aliases: ["Khong thu tien"] },
    { vi: "đang được thế chấp nên không phát sinh tiền thuê.", en: "is mortgaged, so no rent is charged.", aliases: ["dang duoc the chap nen khong phat sinh tien thue."] },
    { vi: "nhận 1 thẻ ra tù miễn phí.", en: "receives 1 get-out-of-jail-free card.", aliases: ["nhan 1 the ra tu mien phi."] },
    { vi: "Vào tù", en: "Go to jail", aliases: ["Vao tu"] },
    { vi: "bị đưa vào tù.", en: "was sent to jail.", aliases: ["bi dua vao tu."] },
    { vi: "vừa cân bằng khoản nợ", en: "just cleared the debt", aliases: ["vua can bang khoan no"] },
    { vi: "Chỉ còn 1 người chơi chưa phá sản.", en: "Only one player remains without bankruptcy.", aliases: ["Chi con 1 nguoi choi chua pha san."] },
    { vi: "Chuyển lượt", en: "Next turn", aliases: ["Chuyen luot"] },
    { vi: "đến lượt.", en: "is up.", aliases: ["den luot."] },
    { vi: "Kết ván", en: "Finalize match", aliases: ["Ket van"] },
    { vi: "Dẫn đầu:", en: "Leader:", aliases: ["Dan dau:"] },
    { vi: "Mã phòng không hợp lệ.", en: "Invalid room code.", aliases: ["Ma phong khong hop le."] },
    { vi: "Đang giữ:", en: "Holding:", aliases: ["Dang giu:"] },
    { vi: "chấp nhận trade", en: "accept trade", aliases: ["chap nhan trade"] },
    { vi: "chốt ván", en: "finalize match", aliases: ["chot van"] },
    { vi: "cập nhật lượt chơi", en: "update turn state", aliases: ["cap nhat luot choi"] },
    { vi: "Tất cả", en: "All", aliases: ["Tat ca"] },
    { vi: "Tất cả trò chơi", en: "All games", aliases: ["Tat ca tro choi"] },
    { vi: "Tất cả thành tựu đã mở khóa.", en: "All achievements unlocked.", aliases: ["Tat ca thanh tuu da mo khoa."] },
    { vi: "Mở sảnh phòng", en: "Open room lobby", aliases: ["Mo sanh phong"] },
    { vi: "Khách tạm thời", en: "Guest user", aliases: ["Khach tam thoi"] },
    { vi: "Khách", en: "Guest", aliases: ["Khach"] },
    { vi: "Tên hiển thị", en: "Display name", aliases: ["Ten hien thi"] },
    { vi: "Quyền", en: "Role", aliases: ["Quyen"] },
    { vi: "Về thư viện", en: "Back to library", aliases: ["Ve thu vien"] },
    { vi: "Về thư viện game", en: "Back to game library", aliases: ["Ve thu vien game"] },
    { vi: "Về trang chế độ", en: "Back to modes", aliases: ["Ve trang che do"] },
    { vi: "Vào ngay", en: "Join now", aliases: ["Vao ngay"] },
    { vi: "Tìm kiếm", en: "Search", aliases: ["Tim kiem"] },
    { vi: "Chơi lại", en: "Play again", aliases: ["Choi lai"] },
    { vi: "Sẵn sàng", en: "Ready", aliases: ["San sang"] },
    { vi: "Sẵn sàng chơi", en: "Ready to play", aliases: ["San sang choi"] },
    { vi: "Khung đã sẵn sàng", en: "Setup ready", aliases: ["Khung da san sang"] },
    { vi: "Sẵn sàng kết nối", en: "Ready to connect", aliases: ["San sang ket noi"] },
    { vi: "Sẵn sàng ngay", en: "Available now", aliases: ["San sang ngay"] },
    { vi: "Đang cấu hình", en: "Configuring", aliases: ["Dang cau hinh"] },
    { vi: "Vào nhanh random", en: "Quick random join", aliases: ["Vao nhanh random"] },
    { vi: "Yêu cầu thất bại", en: "Request failed", aliases: ["Yeu cau that bai"] },
    { vi: "Thao tác thất bại", en: "Action failed", aliases: ["Thao tac that bai"] },
    { vi: "Thời gian", en: "Time", aliases: ["Thoi gian"] },
    { vi: "Đang kết nối...", en: "Connecting...", aliases: ["Dang ket noi..."] },
    { vi: "Đang kết nối lại...", en: "Reconnecting...", aliases: ["Dang ket noi lai..."] },
    { vi: "Đang kết nối realtime...", en: "Connecting to real-time service...", aliases: ["Dang ket noi realtime..."] },
    { vi: "Đang kết nối máy chủ...", en: "Connecting to server...", aliases: ["Dang ket noi may chu..."] },
    { vi: "Kết nối:", en: "Connection:", aliases: ["Ket noi:"] },
    { vi: "Phòng:", en: "Room:", aliases: ["Phong:"] },
    { vi: "Phòng đã đóng", en: "Room closed", aliases: ["Phong da dong"] },
    { vi: "Ảnh đại diện người chơi", en: "Player avatar", aliases: ["Anh dai dien nguoi choi"] },
    { vi: "Cùng máy", en: "Same device", aliases: ["Cung may"] },
    { vi: "Đã khóa", en: "Banned", aliases: ["Da khoa"] },
    { vi: "Đang hoạt động", en: "Active", aliases: ["Dang hoat dong"] },
    { vi: "Trạng thái:", en: "Status:", aliases: ["Trang thai:"] },
    { vi: "Trạng thái ván đấu", en: "Match status", aliases: ["Trang thai van dau"] },
    { vi: "Trạng thái ván đấu:", en: "Match status:", aliases: ["Trang thai van dau:"] },
    { vi: "Ván đấu kết thúc", en: "Match finished", aliases: ["Van dau ket thuc"] },
    { vi: "Đang chờ / Chờ phản hồi", en: "Waiting / Pending response", aliases: ["Dang cho / Cho phan hoi"] },
    { vi: "Đang chờ đối thủ phản hồi...", en: "Waiting for opponent response...", aliases: ["Dang cho doi thu phan hoi..."] },
    { vi: "Thông tin ván đấu", en: "Match details", aliases: ["Thong tin van dau"] },
    { vi: "Chế độ:", en: "Mode:", aliases: ["Che do:"] },
    { vi: "Từ ngày", en: "From date", aliases: ["Tu ngay"] },
    { vi: "Đến ngày", en: "To date", aliases: ["Den ngay"] },
    { vi: "Số dòng", en: "Rows", aliases: ["So dong"] },
    { vi: "Trang hiện tại", en: "Current page", aliases: ["Trang hien tai"] },
    { vi: "Tải CSV", en: "Download CSV", aliases: ["Tai CSV"] },
    { vi: "Tải Excel", en: "Download Excel", aliases: ["Tai Excel"] },
    { vi: "Giữ nguyên bộ lọc", en: "Keep current filters", aliases: ["Giu nguyen bo loc"] },
    { vi: "Lọc khóa", en: "Status filter", aliases: ["Loc khoa"] },
    { vi: "Quản trị", en: "Admin", aliases: ["Quan tri"] },
    { vi: "Quản lý", en: "Manager", aliases: ["Quan ly"] },
    { vi: "Tạo tài khoản", en: "Create account", aliases: ["Tao tai khoan"] },
    { vi: "Tạo tài khoản mới", en: "Create a new account", aliases: ["Tao tai khoan moi"] },
    { vi: "Tạo tài khoản của bạn", en: "Create your account", aliases: ["Tao tai khoan cua ban"] },
    { vi: "Tạo tài khoản thành công", en: "Account created successfully", aliases: ["Tao tai khoan thanh cong"] },
    { vi: "Tạo tài khoản thất bại", en: "Failed to create account", aliases: ["Tao tai khoan that bai"] },
    { vi: "Nhấn Enter hoặc dán mã phòng để vào trận ngay.", en: "Press Enter or paste a room code to join immediately.", aliases: ["Nhan Enter hoac dan ma phong de vao tran ngay."] },
    { vi: "Nhập hoặc dán mã phòng để vào ngay...", en: "Type or paste a room code to join right away...", aliases: ["Nhap hoac dan ma phong de vao ngay..."] },
    { vi: "Nhập hoặc dán mã phòng để vào ngay", en: "Type or paste a room code to join right away", aliases: ["Nhap hoac dan ma phong de vao ngay"] },
    { vi: "Nhật ký nước đi", en: "Move log", aliases: ["Nhat ky nuoc di"] },
    { vi: "Nước đi:", en: "Moves:", aliases: ["Nuoc di:"] },
    { vi: "Tiếp tục chơi", en: "Keep playing", aliases: ["Tiep tuc choi"] },
    { vi: "Không thể đầu hàng lúc này.", en: "You cannot surrender right now.", aliases: ["Khong the dau hang luc nay."] },
    { vi: "Đang mất kết nối... Hệ thống sẽ tự kết nối lại.", en: "Connection lost... The system will reconnect automatically.", aliases: ["Dang mat ket noi... He thong se tu ket noi lai."] },
    { vi: "Không xác định được user session", en: "Cannot determine the user session", aliases: ["Khong xac dinh duoc user session"] },
    { vi: "Tài khoản đăng nhập", en: "Signed-in account", aliases: ["Tai khoan dang nhap"] },
    { vi: "Tạo phòng và vào ngay", en: "Create room and join now", aliases: ["Tao phong va vao ngay"] },
    { vi: "Tìm theo tên hoặc email", en: "Search by name or email", aliases: ["Tim theo ten hoac email"] },
    { vi: "Đang chờ đủ người...", en: "Waiting for enough players...", aliases: ["Dang cho du nguoi..."] },
    { vi: "Đang chờ đủ 4 người", en: "Waiting for 4 players", aliases: ["Dang cho du 4 nguoi"] },
    { vi: "Đang chờ thêm 1 người chơi...", en: "Waiting for 1 more player...", aliases: ["Dang cho them 1 nguoi choi..."] },
    { vi: "Đang chờ người chơi...", en: "Waiting for players...", aliases: ["Dang cho nguoi choi..."] },
    { vi: "Đang chờ thêm người chơi...", en: "Waiting for more players...", aliases: ["Dang cho them nguoi choi..."] },
    { vi: "Đang chờ chủ phòng bắt đầu...", en: "Waiting for the host to start...", aliases: ["Dang cho chu phong bat dau..."] },
    { vi: "Đang chờ vào phòng...", en: "Waiting to enter room...", aliases: ["Dang cho vao phong..."] },
    { vi: "Đang chờ vào phòng", en: "Waiting to enter room", aliases: ["Dang cho vao phong"] },
    { vi: "Đang chờ server xác nhận nước đi...", en: "Waiting for the server to confirm the move...", aliases: ["Dang cho server xac nhan nuoc di..."] },
    { vi: "Đang chờ đối thủ đi", en: "Waiting for opponent move", aliases: ["Dang cho doi thu di"] },
    { vi: "Đang chờ đối thủ đi.", en: "Waiting for opponent move.", aliases: ["Dang cho doi thu di."] },
    { vi: "Đến lượt bạn", en: "Your turn", aliases: ["Den luot ban"] },
    { vi: "Đến lượt bạn.", en: "Your turn.", aliases: ["Den luot ban."] },
    { vi: "Lượt:", en: "Turn:", aliases: ["Luot:"] },
    { vi: "Lượt: Đang chờ đối thủ", en: "Turn: Waiting for opponent", aliases: ["Luot: Dang cho doi thu"] },
    { vi: "Đang chờ phản hồi", en: "Pending response", aliases: ["Dang cho phan hoi"] },
    { vi: "Đang chờ xử lý lời mời", en: "Invite pending", aliases: ["Dang cho xu ly loi moi"] },
    { vi: "Đang chờ", en: "Waiting", aliases: ["Dang cho", "Waiting"] },
    { vi: "Đang chờ lượt", en: "Waiting for turn", aliases: ["Dang cho luot"] },
    { vi: "Đang chọn", en: "Selected", aliases: ["Dang chon"] },
    { vi: "nước đi hợp lệ", en: "legal moves", aliases: ["nuoc di hop le"] },
    { vi: "Chưa kết nối.", en: "Not connected.", aliases: ["Chua ket noi."] },
    { vi: "Đã đặt cược. Đang chờ chia bài...", en: "Bet placed. Waiting for the deal...", aliases: ["Da dat cuoc. Dang cho chia bai..."] },
    { vi: "Bạn đã đứng bài. Đang chờ nhà cái.", en: "You stood. Waiting for the dealer.", aliases: ["Ban da dung bai. Dang cho nha cai."] },
    { vi: "Đang chờ trận bắt đầu...", en: "Waiting for the match to start...", aliases: ["Dang cho tran bat dau..."] },
    { vi: "Phòng đã sẵn sàng. Chọn token cho đủ người rồi bấm bắt đầu.", en: "Room ready. Pick tokens for all players, then press start.", aliases: ["Phong da san sang. Chon token cho du nguoi roi bam bat dau."] },
    { vi: "Đang chờ host bắt đầu ván Monopoly.", en: "Waiting for the host to start the Monopoly match.", aliases: ["Dang cho host bat dau van Monopoly."] },
    { vi: "Đang chơi nhiều", en: "Played often", aliases: ["Dang choi nhieu"] },
    { vi: "Mới xuất hiện", en: "Newly added", aliases: ["Moi xuat hien"] },
    { vi: "Không vào ván", en: "Sitting out", aliases: ["Khong vao van"] },
    { vi: "Đã đủ 4 người - sẵn sàng bắt đầu", en: "All 4 players are in - ready to start", aliases: ["Da du 4 nguoi - san sang bat dau"] },
    { vi: "Sẵn sàng bắt đầu ván mới", en: "Ready to start a new match", aliases: ["San sang bat dau van moi"] },
    { vi: "Bạn đã thắng", en: "You won", aliases: ["Ban da thang"] },
    { vi: "Bạn đã thắng ván Tiến lên!", en: "You won the Tien Len round!", aliases: ["Ban da thang van Tien len!"] },
    { vi: "Chơi. Phòng. Xã hội.", en: "Play. Room. Social.", aliases: ["Choi. Phong. Xa hoi.", "Play. Room. Social."] },
    { vi: "Hồ sơ", en: "Profile", aliases: ["Ho so", "Profile"] },
    { vi: "Xem trận", en: "Spectate", aliases: ["Xem tran", "Spectate"] },
    { vi: "Mã phòng", en: "Room key", aliases: ["Ma phong", "Room key"] },
    { vi: "Phòng đang kết nối", en: "Connecting room", aliases: ["Phong dang ket noi", "Room dang ket noi"] },
    { vi: "Quản lý - Tài khoản", en: "Manager - Accounts", aliases: ["Quan ly - Tai khoan"] },
    { vi: "Quản lý / Quản lý tài khoản", en: "Manager / Account management", aliases: ["Quan ly / Quan ly tai khoan"] },
    { vi: "Quản lý - Chi tiết tài khoản", en: "Manager - Account details", aliases: ["Quan ly - Chi tiet tai khoan"] },
    { vi: "Quản lý / Chi tiết tài khoản", en: "Manager / Account details", aliases: ["Quan ly / Chi tiet tai khoan"] },
    { vi: "Quản trị / Quản lý tài khoản", en: "Admin / Account management", aliases: ["Quan tri / Quan ly tai khoan"] },
    { vi: "Quản trị / Thông báo", en: "Admin / Notifications", aliases: ["Quan tri / Thong bao"] },
    { vi: "CSV / Excel giữ nguyên bộ lọc", en: "CSV / Excel keep current filters", aliases: ["CSV / Excel giu nguyen bo loc"] },
    { vi: "CSV / Excel theo page hoặc all", en: "CSV / Excel by page or all", aliases: ["CSV / Excel theo page hoac all"] },
    { vi: "CSV / Excel cho audit nhanh", en: "CSV / Excel for quick audit", aliases: ["CSV / Excel cho audit nhanh"] },
    { vi: "Export chỉ mở cho admin", en: "Export is admin-only", aliases: ["Export chi mo cho admin"] },
    { vi: "Tất cả route vào chơi, lịch sử và BXH được gom trong cùng một bề mặt gameplay.", en: "All play, history, and leaderboard routes are grouped into one gameplay surface.", aliases: ["Tat ca route vao choi, lich su va BXH duoc gom trong cung mot be mat gameplay."] },
    { vi: "Không cần đăng nhập", en: "No sign-in required", aliases: ["Khong can dang nhap"] },
    { vi: "Không cần đăng nhập.", en: "No sign-in required.", aliases: ["Khong can dang nhap."] },
    { vi: "Cần nhập mã phòng.", en: "Please enter a room code.", aliases: ["Can nhap ma phong."] },
    { vi: "Không thể chọn phòng random", en: "Cannot choose a random room", aliases: ["Khong the chon phong random"] },
    { vi: "Không thể vào phòng random. Thử lại sau.", en: "Cannot join a random room right now. Please try again later.", aliases: ["Khong the vao phong random. Thu lai sau."] },
    { vi: "Dữ liệu phòng không hợp lệ", en: "Invalid room data", aliases: ["Du lieu phong khong hop le"] },
    { vi: "Không có phòng chờ hợp lệ. Đang tạo phòng mới...", en: "No suitable waiting room found. Creating a new room...", aliases: ["Khong co phong cho hop le. Dang tao phong moi..."] },
    { vi: "Mất kết nối. Không thể tự kết nối lại, hãy vào lại phòng.", en: "Connection lost. Automatic reconnect failed; please rejoin the room.", aliases: ["Mat ket noi. Khong the tu ket noi lai, hay vao lai phong."] },
    { vi: "Đã gửi yêu cầu tái đấu. Đang chuẩn bị ván mới...", en: "Rematch request sent. Preparing a new match...", aliases: ["Da gui yeu cau tai dau. Dang chuan bi van moi..."] },
    { vi: "Tạo phòng mới hoặc nhập mã phòng để vào lobby Monopoly.", en: "Create a new room or enter a room code to join the Monopoly lobby.", aliases: ["Tao phong moi hoac nhap ma phong de vao lobby Monopoly."] },
    { vi: "Phiên đang thay đổi, trang sẽ tải lại để kết nối ổn định...", en: "The session changed, and the page will reload to restore a stable connection...", aliases: ["Phien dang thay doi, trang se tai lai de ket noi on dinh..."] },
    { vi: "Internet đã quay lại. Đang khôi phục kết nối...", en: "Internet is back. Restoring the connection...", aliases: ["Internet da quay lai. Dang khoi phuc ket noi..."] },
    { vi: "Không tìm thấy phòng random và không tạo được phòng mới.", en: "No random room was found, and a new room could not be created.", aliases: ["Khong tim thay phong random va khong tao duoc phong moi."] },
    { vi: "Phòng đã đầy. Bấm \"Vào chế độ xem\" để theo dõi trận đấu.", en: "The room is full. Click \"Enter spectate mode\" to follow the match.", aliases: ["Phong da day. Bam \"Vao che do xem\" de theo doi tran dau."] },
    { vi: "Nguồn dữ liệu: server (chưa có room online thực tế)", en: "Data source: server (no real online room yet)", aliases: ["Nguon du lieu: server (chua co room online thuc te)"] },
    { vi: "Bạn đang là người chơi. Hãy rời phòng nếu muốn vào chế độ xem.", en: "You are currently a player. Leave the room if you want to switch to spectate mode.", aliases: ["Ban dang la nguoi choi. Hay roi phong neu muon vao che do xem."] },
    { vi: "Hãy quay lại trang phòng trực tuyến để tạo/chọn phòng Cờ vua.", en: "Return to the online room page to create or choose a Chess room.", aliases: ["Hay quay lai trang phong truc tuyen de tao/chon phong Co vua."] },
    { vi: "Hãy quay lại trang phòng trực tuyến để tạo/chọn phòng Cờ tướng.", en: "Return to the online room page to create or choose a Xiangqi room.", aliases: ["Hay quay lai trang phong truc tuyen de tao/chon phong Co tuong."] },
    { vi: "Chỉ có thể tạo ván mới khi phòng đã đủ 2 người chơi.", en: "You can start a new match only when the room has 2 players.", aliases: ["Chi co the tao van moi khi phong da du 2 nguoi choi."] },
    { vi: "Ván đấu đã kết thúc. Bấm 'Ván mới' để chơi tiếp.", en: "The match has ended. Click 'New match' to continue.", aliases: ["Van dau da ket thuc. Bam 'Van moi' de choi tiep."] },
    { vi: "Không xác định được người chơi. Hệ thống sẽ quay lại sảnh.", en: "Cannot determine the player. The system will return to the lobby.", aliases: ["Khong xac dinh duoc nguoi choi. He thong se quay lai sanh."] },
    { vi: "1 người chơi + 3 bot trên cùng thiết bị. Không cần đăng nhập.", en: "1 player + 3 bots on the same device. No sign-in required.", aliases: ["1 nguoi choi + 3 bot tren cung thiet bi. Khong can dang nhap."] },
    { vi: "2 người chơi trên cùng thiết bị. Không cần đăng nhập.", en: "2 players on the same device. No sign-in required.", aliases: ["2 nguoi choi tren cung thiet bi. Khong can dang nhap."] },
    { vi: "Nhấn Enter hoặc dán mã phòng để vào thẳng bàn chơi.", en: "Press Enter or paste a room code to enter the table directly.", aliases: ["Nhan Enter hoac dan ma phong de vao thang ban choi."] },
    { vi: "Dán mã phòng vào ô trên để vào thẳng phòng chơi.", en: "Paste the room code into the field above to enter the play room directly.", aliases: ["Dan ma phong vao o tren de vao thang phong choi."] },
    { vi: "Đã rời room. Slot của bạn được đánh dấu tạm offline và có thể vào lại bằng cùng mã phòng.", en: "You left the room. Your slot is marked temporarily offline, and you can rejoin with the same room code.", aliases: ["Da roi room. Slot cua ban duoc danh dau tam offline va co the vao lai bang cung ma phong."] },
    { vi: "Đang ở trong trận khác. Rời phòng hiện tại trước khi chuyển room.", en: "You are already in another match. Leave the current room before switching rooms.", aliases: ["Dang o trong tran khac. Roi phong hien tai truoc khi chuyen room."] },
    { vi: "Phòng đang trong trận. Chỉ người đang đến lượt mới được thao tác.", en: "The room is in an active match. Only the player whose turn it is can act.", aliases: ["Phong dang trong tran. Chi nguoi dang den luot moi duoc thao tac."] },
    { vi: "Phòng đã tạo. Mọi người vào phòng, chọn token rồi host bắt đầu.", en: "The room has been created. Everyone joins, picks a token, and then the host starts.", aliases: ["Phong da tao. Moi nguoi vao phong, chon token roi host bat dau."] },
    { vi: "Đã rời chế độ phòng. Bạn có thể tạo room khác hoặc chơi local.", en: "You left room mode. You can create another room or play locally.", aliases: ["Da roi che do phong. Ban co the tao room khac hoac choi local."] },
    { vi: "Host đã bắt đầu ván. Room đã chuyển sang chế độ chơi.", en: "The host started the match. The room is now in play mode.", aliases: ["Host da bat dau van. Room da chuyen sang che do choi."] },
    { vi: "Không còn xử lý treo. Có thể kết thúc lượt và chuyển máy.", en: "No pending action remains. You can end the turn and pass the device.", aliases: ["Khong con xu ly treo. Co the ket thuc luot va chuyen may."] },
    { vi: "Một trong hai bên không còn đủ tiền để hoàn tất trade", en: "One side no longer has enough money to complete the trade", aliases: ["Mot trong hai ben khong con du tien de hoan tat trade"] },
    { vi: "Theo dõi người chơi đã vào game nào, đúng trận nào, chơi tại đâu và vào lúc mấy giờ. Giao diện này ưu tiên đọc nhanh từng trận thay vì chỉ nhìn bảng dữ liệu thô.", en: "Track which game each player joined, the exact match, where it was played, and when it happened. This view is optimized for scanning matches quickly instead of reading raw tables.", aliases: ["Theo doi nguoi choi da vao game nao, dung tran nao, choi tai dau va vao luc may gio. Giao dien nay uu tien doc nhanh tung tran thay vi chi nhin bang du lieu tho."] },
    { vi: "Chỉ admin mới được xuất dữ liệu lịch sử. Mọi tài khoản khác đều không được truy cập vào file CSV/Excel, kể cả khi gọi trực tiếp URL export hoặc report center.", en: "Only admins can export history data. All other accounts are blocked from CSV/Excel files, even via direct export URLs or the report center.", aliases: ["Chi admin moi duoc xuat du lieu lich su. Moi tai khoan khac deu khong duoc truy cap vao file CSV/Excel, ke ca khi goi truc tiep URL export hoac report center."] },
    { vi: "Admin không tải file trực tiếp tại trang này nữa. Bộ lọc hiện tại sẽ được chuyển sang report center để export và ghi audit log tập trung.", en: "Admins no longer download files directly from this page. The current filters are forwarded to the report center for exporting and centralized audit logging.", aliases: ["Admin khong tai file truc tiep tai trang nay nua. Bo loc hien tai se duoc chuyen sang report center de export va ghi audit log tap trung."] },
    { vi: "Cập nhật tên hiển thị, email, avatar và đổi mật khẩu hiện được thực hiện tập trung tại trang cài đặt để giao diện và API thống nhất.", en: "Display name, email, avatar, and password changes are handled centrally in Settings so the UI and API stay consistent.", aliases: ["Cap nhat ten hien thi, email, avatar va doi mat khau hien duoc thuc hien tap trung tai trang cai dat de giao dien va API thong nhat."] },
    { vi: "Nhập mã phòng và nhấn Enter là vào ngay cả khi chưa đăng nhập. Hệ thống sẽ tạo tên khách tự động để bạn vào trận nhanh hơn.", en: "Enter a room code and press Enter to join immediately, even before signing in. The system will create a guest name automatically so you can enter a match faster.", aliases: ["Nhap ma phong va nhan Enter la vao ngay ca khi chua dang nhap. He thong se tao ten khach tu dong de ban vao tran nhanh hon."] },
    { vi: "Tách rõ 3 mode: room online, local cùng máy và bot offline. Mỗi mode đều có màn riêng và vào trận theo đường dẫn riêng.", en: "The page separates 3 clear modes: online rooms, same-device local play, and offline bots. Each mode has its own screen and route into the match.", aliases: ["Tach ro 3 mode: room online, local cung may va bot offline. Moi mode deu co man rieng va vao tran theo duong dan rieng."] },
    { vi: "Chỉ admin mới được xuất dữ liệu bảng xếp hạng. Mọi tài khoản khác sẽ bị khóa truy cập vào CSV/Excel và report center.", en: "Only admins can export leaderboard data. All other accounts are blocked from CSV/Excel and the report center.", aliases: ["Chi admin moi duoc xuat du lieu bang xep hang. Moi tai khoan khac se bi khoa truy cap vao CSV/Excel va report center."] },
    { vi: "Tất cả lời mời kết bạn, thông báo thành tựu và thông điệp hệ thống được đẩy vào cùng một dashboard để xử lý liên tục.", en: "All friend invites, achievement notifications, and system messages are grouped in one dashboard for continuous handling.", aliases: ["Tat ca loi moi ket ban, thong bao thanh tuu va thong diep he thong duoc day vao cung mot dashboard de xu ly lien tuc."] },
    { vi: "Gom danh sách bạn, lời mời, tìm kiếm tài khoản và các thao tác nhắn tin vào cùng một surface để di chuyển nhanh hơn.", en: "Friends list, invites, account search, and messaging actions are combined into one surface for quicker navigation.", aliases: ["Gom danh sach ban, loi moi, tim kiem tai khoan va cac thao tac nhan tin vao cung mot surface de di chuyen nhanh hon."] },
    { vi: "Một luồng chat riêng giữa bạn và một người chơi khác, đồng bộ theo room key và hiển thị lịch sử ngay trong màn hình này.", en: "A private chat thread between you and another player, synchronized by room key and showing the history directly on this screen.", aliases: ["Mot luong chat rieng giua ban va mot nguoi choi khac, dong bo theo room key va hien lich su ngay trong man hinh nay."] },
    { vi: "Bạn bè mở link này sẽ vào đúng game và đúng mã phòng. Nếu game đã có bàn chơi trực tuyến, họ có thể vào bàn ngay.", en: "Friends opening this link land in the correct game and room. If the game already has a live table, they can join it immediately.", aliases: ["Ban be mo link nay se vao dung game va dung ma phong. Neu game da co ban choi truc tuyen, ho co the vao ban ngay."] },
    { vi: "Theo dõi hạng, tỉ lệ thắng và pace thi đấu ngay trong hồ sơ theo một bố cục giống trang account của game portal.", en: "Track rank, win rate, and play pace directly in the profile with a layout inspired by game portal account pages.", aliases: ["Theo doi hang, ti le thang va pace thi dau ngay trong ho so theo mot bo cuc giong trang account cua game portal."] },
    { vi: "Nếu bạn vừa chơi bằng guest, hệ thống sẽ tự động chuyển dữ liệu guest vào account sau khi đăng nhập thành công.", en: "If you just played as a guest, the system will automatically migrate guest data into your account after sign-in succeeds.", aliases: ["Neu ban vua choi bang guest, he thong se tu dong chuyen du lieu guest vao account sau khi dang nhap thanh cong."] },
    { vi: "Một workspace duy nhất cho account, social login, đổi mật khẩu, tùy chọn giao diện và dữ liệu xuất báo cáo.", en: "A single workspace for account management, social login, password changes, interface options, and exported report data.", aliases: ["Mot workspace duy nhat cho account, social login, doi mat khau, tuy chon giao dien va du lieu xuat bao cao."] },
    { vi: "Chọn nhanh Tiến Lên online, room bot riêng hoặc nhảy sang Blackjack mà không qua các khối giới thiệu dài.", en: "Jump straight into online Tien Len, the dedicated bot room, or Blackjack without passing through long intro sections.", aliases: ["Chon nhanh Tien Len online, room bot rieng hoac nhay sang Blackjack ma khong qua cac khoi gioi thieu dai."] },
    { vi: "Chọn mức độ bot để bắt đầu. Luồng này được dùng chung cho Caro, Cờ vua, Cờ tướng, Cờ tỷ phú và Đánh bài.", en: "Choose a bot difficulty to start. This flow is shared by Caro, Chess, Xiangqi, Monopoly, and Cards.", aliases: ["Chon muc do bot de bat dau. Luong nay duoc dung chung cho Caro, Co vua, Co tuong, Co ty phu va Danh bai."] },
    { vi: "Gameplay rail giữ người chơi trong cùng một loop: vào trận, xem kết quả và đổi mode mà không lạc route.", en: "The gameplay rail keeps players in one loop: enter a match, see the result, and switch modes without losing the route.", aliases: ["Gameplay rail giu nguoi choi trong cung mot loop: vao tran, xem ket qua va doi mode ma khong lac route."] },
    { vi: "Bạn có thể cập nhật lại phần này sau trong profile, nhưng cần một bộ thông tin hợp lệ để tạo account.", en: "You can update this later in your profile, but a valid set of details is required to create the account.", aliases: ["Ban co the cap nhat lai phan nay sau trong profile, nhung can mot bo thong tin hop le de tao account."] },
    { vi: "Quản lý danh sách tài khoản, lọc theo trạng thái, tạo mới tài khoản và điều hướng đến trang chi tiết.", en: "Manage the account list, filter by status, create new accounts, and navigate to detail pages.", aliases: ["Quan ly danh sach tai khoan, loc theo trang thai, tao moi tai khoan va dieu huong den trang chi tiet."] },
    { vi: "Tên người dùng độc hại, gây nhầm lẫn hoặc không phù hợp có thể bị khóa mà không cần thông báo trước.", en: "Usernames that are harmful, misleading, or inappropriate may be locked without prior notice.", aliases: ["Ten nguoi dung doc hai, gay nham lan hoac khong phu hop co the bi khoa ma khong can thong bao truoc."] },
    { vi: "Mọi notice tại đây được đẩy vào app shell chung để người dùng nhận thông tin từ một nguồn duy nhất.", en: "Every notice here is pushed into the shared app shell so users receive information from a single source.", aliases: ["Moi notice tai day duoc day vao app shell chung de nguoi dung nhan thong tin tu mot nguon duy nhat."] },
    { vi: "Trang này giữ rõ ranh giới quyền hạn: sửa thông tin cơ bản và khóa/mở khóa trong phạm vi được giao.", en: "This page keeps permission boundaries clear: edit basic information and lock/unlock only within the assigned scope.", aliases: ["Trang nay giu ro ranh gioi quyen han: sua thong tin co ban va khoa/mo khoa trong pham vi duoc giao."] },
    { vi: "Manager duyệt, lọc và mở chi tiết user trên một mặt bằng giống admin nhưng thu hẹp scope thao tác.", en: "Managers browse, filter, and open user details on an admin-like surface with a narrower action scope.", aliases: ["Manager duyet, loc va mo chi tiet user tren mot mat bang giong admin nhung thu hep scope thao tac."] },
    { vi: "Nhập user, game và mốc thời gian. Nếu mở từ trang lịch sử, bộ lọc hiện tại sẽ được đổ sẵn vào đây.", en: "Enter the user, game, and time range. If opened from the history page, the current filters are prefilled here.", aliases: ["Nhap user, game va moc thoi gian. Neu mo tu trang lich su, bo loc hien tai se duoc do san vao day."] },
    { vi: "Từ room online đến solo với bot, dữ liệu của bạn sẽ đi cùng một profile và một lịch sử xuyên suốt.", en: "From online rooms to solo bot matches, your data follows one continuous profile and history.", aliases: ["Tu room online den solo voi bot, du lieu cua ban se di cung mot profile va mot lich su xuyen suot."] },
    { vi: "Danh sách phòng cập nhật từ server để vào đúng room của game này mà không cần qua hub trung gian.", en: "The room list updates from the server so you can enter this game's room directly without an intermediate hub.", aliases: ["Danh sach phong cap nhat tu server de vao dung room cua game nay ma khong can qua hub trung gian."] },
    { vi: "1 người chơi thật + 1-3 bot. Bot có 2 mức độ và ván đấu được ghi vào lịch sử bot trên tài khoản.", en: "1 real player plus 1-3 bots. Bots have 2 difficulties, and the matches are recorded in the account's bot history.", aliases: ["1 nguoi choi that + 1-3 bot. Bot co 2 muc do va van dau duoc ghi vao lich su bot tren tai khoan."] },
    { vi: "Tạo phòng, mời người chơi vào bằng link room và đồng bộ trạng thái ván đấu theo thời gian thực.", en: "Create a room, invite players by room link, and sync match status in real time.", aliases: ["Tao phong, moi nguoi choi vao bang link room va dong bo trang thai van dau theo thoi gian thuc."] },
    { vi: "Module được gọi qua hub, có thể mở trong browser và ghim thành desktop app cùng giao diện này.", en: "The module is launched through the hub and can be opened in the browser or pinned as a desktop app with the same interface.", aliases: ["Module duoc goi qua hub, co the mo trong browser va ghim thanh desktop app cung giao dien nay."] },
    { vi: "Tổng hợp các game puzzle với tìm kiếm, bộ lọc, sắp xếp và danh sách đề xuất để vào chơi nhanh.", en: "A combined puzzle hub with search, filters, sorting, and recommended picks for quick play.", aliases: ["Tong hop cac game puzzle voi tim kiem, bo loc, sap xep va danh sach de xuat de vao choi nhanh."] },
    { vi: "Vào thẳng sảnh phòng, dán mã để join nhanh và giữ màn hình tập trung vào room list + bàn chơi.", en: "Jump straight into the room lobby, paste a code for quick join, and keep the screen focused on the room list and table.", aliases: ["Vao thang sanh phong, dan ma de join nhanh va giu man hinh tap trung vao room list + ban choi."] },
    { vi: "Thông tin cốt lõi của tài khoản được đặt sát hero để admin nhìn đúng đối tượng đang thao tác.", en: "Core account information sits next to the hero so admins can immediately confirm which account they are editing.", aliases: ["Thong tin cot loi cua tai khoan duoc dat sat hero de admin nhin dung doi tuong dang thao tac."] },
    { vi: "Danh sách thông báo được giữ sát form tạo để admin tạo và dọn dẹp nhanh trong một view ngắn.", en: "The notification list stays next to the create form so admins can create and clean up quickly in a compact view.", aliases: ["Danh sach thong bao duoc giu sat form tao de admin tao va don dep nhanh trong mot view ngan."] },
    { vi: "Summary đầu trang giúp manager xác định nhanh tài khoản đang sửa mà không cần cuộn vào form.", en: "The summary at the top helps managers identify the account being edited without scrolling into the form.", aliases: ["Summary dau trang giup manager xac dinh nhanh tai khoan dang sua ma khong can cuon vao form."] },
    { vi: "Tên này sẽ hiện trong room, lịch sử chơi và profile công khai. Bạn nên chọn một tên ổn định.", en: "This name appears in rooms, play history, and the public profile. Choose a stable name.", aliases: ["Ten nay se hien trong room, lich su choi va profile cong khai. Ban nen chon mot ten on dinh."] },
    { vi: "Bot surface được giữ tối giản để thử nghiệm hỏi đáp nhanh mà không chen vào gameplay chính.", en: "The bot surface stays minimal for quick Q&A experiments without getting in the way of the main gameplay.", aliases: ["Bot surface duoc giu toi gian de thu nghiem hoi dap nhanh ma khong chen vao gameplay chinh."] },
    { vi: "Cài đặt Game Hub để mở module trong cửa sổ riêng, có shortcut desktop và vào lại nhanh hơn.", en: "Configure Game Hub to open modules in a separate window, support desktop shortcuts, and re-enter faster.", aliases: ["Cai dat Game Hub de mo module trong cua so rieng, co shortcut desktop va vao lai nhanh hon."] },
    { vi: "Lobby đặt cược, spectate và route đánh bài liên quan được giữ trong cùng một gameplay loop.", en: "The betting lobby, spectate flow, and related card routes stay inside the same gameplay loop.", aliases: ["Lobby dat cuoc, spectate va route danh bai lien quan duoc giu trong cung mot gameplay loop."] },
    { vi: "Thông báo hệ thống và access log được nối sát dashboard để admin quản lý một mạch liên tục.", en: "System notifications and the access log sit next to the dashboard so admins can manage everything in one continuous flow.", aliases: ["Thong bao he thong va access log duoc noi sat dashboard de admin quan ly mot mach lien tuc."] },
    { vi: "Lọc, tạo, đổi quyền và khóa tài khoản được đặt trong một bộ điều khiển duy nhất cho admin.", en: "Filtering, account creation, role changes, and locking accounts are grouped into one admin control surface.", aliases: ["Loc, tao, doi quyen va khoa tai khoan duoc dat trong mot bo dieu khien duy nhat cho admin."] },
    { vi: "Theo dõi request vào web, lọc theo tài khoản, đường dẫn, method và xuất báo cáo CSV/Excel.", en: "Track incoming web requests, filter by account, path, and method, then export CSV/Excel reports.", aliases: ["Theo doi request vao web, loc theo tai khoan, duong dan, method va xuat bao cao CSV/Excel."] },
    { vi: "Trả lời từng câu, đối đầu bot theo cùng bộ câu hỏi và chốt kết quả ngay trong trình duyệt.", en: "Answer each question, face bots with the same question set, and finalize the result directly in the browser.", aliases: ["Tra loi tung cau, doi dau bot theo cung bo cau hoi va chot ket qua ngay trong trinh duyet."] },
    { vi: "Bộ export đầy đủ dành cho admin center, phù hợp khi cần backup nhanh danh sách tài khoản.", en: "A full export set for the admin center, suitable when you need a quick backup of the account list.", aliases: ["Bo export day du danh cho admin center, phu hop khi can backup nhanh danh sach tai khoan."] },
    { vi: "Chơi 2-4 người trên cùng máy, không cần room, phù hợp cho một bàn đánh thử nhanh tại chỗ.", en: "Play 2-4 players on the same device without a room, suitable for a quick local trial table.", aliases: ["Choi 2-4 nguoi tren cung may, khong can room, phu hop cho mot ban danh thu nhanh tai cho."] },
    { vi: "Mọi game đều có nhạc chơi, trạng thái và cách vào ván trong một bố cục thống nhất.", en: "Every game exposes music, status, and match entry in a consistent layout.", aliases: ["Moi game deu co nhac choi, trang thai va cach vao van trong mot bo cuc thong nhat."] },
    { vi: "Nội dung chi tiết của trận đấu sẽ được mở rộng tiếp theo theo metadata.", en: "Detailed match content will be expanded further based on metadata.", aliases: ["Noi dung chi tiet cua tran dau se duoc mo rong tiep theo theo metadata."] },
    { vi: "Kết ván theo checkmate/stalemate/đầu hàng, lưu trạng thái theo trận.", en: "Matches end by checkmate, stalemate, or surrender, with status saved per match.", aliases: ["Ket van theo checkmate/stalemate/dau hang, luu trang thai theo tran."] },
    { vi: "Hết bộ câu hỏi sẽ tổng kết điểm và mở room để chờ lần đấu tiếp theo.", en: "After the question set ends, scores are summarized and the room stays open for the next round.", aliases: ["Het bo cau hoi se tong ket diem va mo room de cho lan dau tiep theo."] },
    { vi: "Nước đi và ăn quân được validate bởi server trước khi phát sự kiện.", en: "Moves and captures are validated by the server before events are broadcast.", aliases: ["Nuoc di va an quan duoc validate boi server truoc khi phat su kien."] },
    { vi: "Room bắt đầu khi đủ người, đồng bộ cùng 1 đoạn văn bản cho tất cả.", en: "The room starts when enough players join and synchronizes the same text passage for everyone.", aliases: ["Room bat dau khi du nguoi, dong bo cung 1 doan van ban cho tat ca."] },
    { vi: "Vào room Cờ tướng, chờ đối thủ sẵn sàng và bắt đầu theo lượt.", en: "Enter the Xiangqi room, wait for the opponent to be ready, and start turn by turn.", aliases: ["Vao room Co tuong, cho doi thu san sang va bat dau theo luot."] },
    { vi: "Kết thúc ván khi có đường 5 quân hợp lệ, cập nhật kết quả ngay.", en: "The match ends on a valid line of 5 pieces and updates the result immediately.", aliases: ["Ket thuc van khi co duong 5 quan hop le, cap nhat ket qua ngay."] },
    { vi: "Qua màn sẽ mở độ khó tiếp theo, thua sẽ hiện vị trí mìn sai.", en: "Clearing a board unlocks the next difficulty; losing shows the incorrect mine positions.", aliases: ["Qua man se mo do kho tiep theo, thua se hien vi tri min sai."] },
    { vi: "Tổng kết ván có điểm, phạt và delta từng người sau mỗi trận.", en: "Each round summary shows score, penalties, and per-player deltas after every match.", aliases: ["Tong ket van co diem, phat va delta tung nguoi sau moi tran."] },
    { vi: "Khi bắt đầu chơi game, các trận đấu mới nhất sẽ được đưa vào đây.", en: "As you start playing, your latest matches will appear here.", aliases: ["Khi bat dau choi game, cac tran dau moi nhat se duoc dua vao day."] },
    { vi: "Chọn tên để hiện trên BXH, phòng chơi và profile công khai.", en: "Choose a name to display on leaderboards, in rooms, and on your public profile.", aliases: ["Chon ten de hien tren BXH, phong choi va profile cong khai."] },
    { vi: "Hoàn tất thông tin cơ bản và nhận mã email để kích hoạt.", en: "Complete the basic information and receive an email code to activate the account.", aliases: ["Hoan tat thong tin co ban va nhan ma email de kich hoat."] },
    { vi: "Mã xác thực sẽ được gửi vào email ngay sau khi hoàn tất đăng ký.", en: "The verification code is sent to your email immediately after registration is completed.", aliases: ["Ma xac thuc se duoc gui vao email ngay sau khi hoan tat dang ky."] },
    { vi: "Bắt đầu bằng email thật. Đây là địa chỉ dùng để gửi mã xác thực và khôi phục mật khẩu.", en: "Start with a real email address. It is used to send verification codes and recover your password.", aliases: ["Bat dau bang email that. Day la dia chi dung de gui ma xac thuc va khoi phuc mat khau."] },
    { vi: "Giữ avatar, quốc gia, thông tin profile và cài đặt.", en: "Keep your avatar, country, profile information, and settings.", aliases: ["Giu avatar, quoc gia, thong tin profile va cai dat."] },
    { vi: "Đăng nhập một lần để quay lại đúng trận đấu của bạn.", en: "Sign in once to return to the exact match you left.", aliases: ["Dang nhap mot lan de quay lai dung tran dau cua ban."] },
    { vi: "Hệ thống đang cập nhật phiên đăng nhập và chuyển bạn về trang chủ.", en: "The system is updating the sign-in session and will send you back to the home page.", aliases: ["He thong dang cap nhat phien dang nhap va chuyen ban ve trang chu."] },
    { vi: "Tìm đủ tất cả từ để kết màn và vào bảng mới tiếp theo.", en: "Find all words to clear the board and move to the next grid.", aliases: ["Tim du tat ca tu de ket man va vao bang moi tiep theo."] },
    { vi: "Chọn điểm bắt đầu và điểm kết thúc để tìm từ nằm trên đường thẳng.", en: "Choose the start and end points to find a word on a straight line.", aliases: ["Chon diem bat dau va diem ket thuc de tim tu nam tren duong thang."] },
    { vi: "Tạo bảng mới, phát sinh danh sách từ và reset bộ đếm thời gian.", en: "Create a new board, generate a word list, and reset the timer.", aliases: ["Tao bang moi, phat sinh danh sach tu va reset bo dem thoi gian."] },
    { vi: "Tạo bàn mới theo độ khó, trộn ô và khởi động đồng hồ.", en: "Create a new board by difficulty, shuffle the tiles, and start the timer.", aliases: ["Tao ban moi theo do kho, tron o va khoi dong dong ho."] },
    { vi: "Khởi tạo bàn mới, trộn mảnh ghép và reset HUD move/time.", en: "Initialize a new board, shuffle the pieces, and reset the move/time HUD.", aliases: ["Khoi tao ban moi, tron manh ghep va reset HUD move/time."] },
    { vi: "Hoàn tất ảnh mẫu, chốt kết quả và vào bàn mới ngay.", en: "Complete the sample image, lock the result, and jump into a new board immediately.", aliases: ["Hoan tat anh mau, chot ket qua va vao ban moi ngay."] },
    { vi: "Điền số 1-9 sao cho mỗi hàng, cột và ô 3x3 không bị trùng lặp.", en: "Fill digits 1-9 so each row, column, and 3x3 box has no duplicates.", aliases: ["Dien so 1-9 sao cho moi hang, cot va o 3x3 khong bi trung lap."] },
    { vi: "Tạo puzzle mới và reset đồng hồ, mistakes, độ khó.", en: "Create a new puzzle and reset the timer, mistakes, and difficulty.", aliases: ["Tao puzzle moi va reset dong ho, mistakes, do kho."] },
    { vi: "Mỗi màn có HUD riêng (moves/time/progress) để theo dõi kết quả.", en: "Each screen has its own HUD (moves/time/progress) for tracking results.", aliases: ["Moi man co HUD rieng (moves/time/progress) de theo doi ket qua."] },
    { vi: "Tìm từ theo hàng, cột và đường chéo, cập nhật tiến độ theo bước chơi.", en: "Search words across rows, columns, and diagonals while updating progress as you play.", aliases: ["Tim tu theo hang, cot va duong cheo, cap nhat tien do theo buoc choi."] },
    { vi: "Trượt các ô để đưa bàn cờ về trạng thái hoàn chỉnh theo đúng thứ tự.", en: "Slide the tiles until the board returns to the correct completed order.", aliases: ["Truot cac o de dua ban co ve trang thai hoan chinh theo dung thu tu."] },
    { vi: "Kết màn nhanh và quay về sảnh puzzle để vào game tiếp theo.", en: "Finish a board quickly and return to the puzzle lobby for the next game.", aliases: ["Ket man nhanh va quay ve sanh puzzle de vao game tiep theo."] }
    ,{ vi: "Sidebar desktop/mobile đọc preferences mới từ local storage và server.", en: "The desktop/mobile sidebar reads the latest preferences from local storage and the server.", aliases: ["Sidebar desktop/mobile doc preferences moi tu local storage va server."] }
    ,{ vi: "Lỗi WebSocket, đang thử kết nối lại...", en: "WebSocket error, retrying the connection...", aliases: ["Loi WebSocket, dang thu ket noi lai..."] }
    ,{ vi: "Không thể chuẩn bị phòng cho typing.", en: "Cannot prepare the typing room.", aliases: ["Khong the chuan bi phong cho typing."] }
    ,{ vi: "Không thể chuẩn bị phòng blackjack.", en: "Cannot prepare the blackjack room.", aliases: ["Khong the chuan bi phong blackjack."] }
    ,{ vi: "Không tìm thấy game puzzle phù hợp.", en: "No matching puzzle game was found.", aliases: ["Khong tim thay game puzzle phu hop."] }
    ,{ vi: "Tổng quan chi tiết tài khoản manager", en: "Manager account detail overview", aliases: ["Tong quan chi tiet tai khoan manager"] }
    ,{ vi: "Tổng quan quản lý tài khoản manager", en: "Manager account management overview", aliases: ["Tong quan quan ly tai khoan manager"] }
    ,{ vi: "Tổng quan chi tiết tài khoản admin", en: "Admin account detail overview", aliases: ["Tong quan chi tiet tai khoan admin"] }
    ,{ vi: "Tổng quan quản lý tài khoản admin", en: "Admin account management overview", aliases: ["Tong quan quan ly tai khoan admin"] }
    ,{ vi: "Đang kiểm tra lại kết nối phòng...", en: "Rechecking the room connection...", aliases: ["Dang kiem tra lai ket noi phong..."] }
    ,{ vi: "Đang đồng bộ lại kết nối phòng...", en: "Resyncing the room connection...", aliases: ["Dang dong bo lai ket noi phong..."] }
    ,{ vi: "Phòng hoặc người chơi không hợp lệ", en: "Invalid room or player", aliases: ["Phong hoac nguoi choi khong hop le"] }
    ,{ vi: "Chưa kết nối xong, vui lòng đợi...", en: "The connection is not ready yet, please wait...", aliases: ["Chua ket noi xong, vui long doi..."] }
    ,{ vi: "Mở module game ngoài trong tab mới", en: "Open the external game module in a new tab", aliases: ["Mo module game ngoai trong tab moi"] }
    ,{ vi: "join / random / start / rời phòng", en: "join / random / start / leave room", aliases: ["join / random / start / roi phong"] }
    ,{ vi: "Không tải được danh sách tài khoản", en: "Cannot load the account list", aliases: ["Khong tai duoc danh sach tai khoan"] }
    ,{ vi: "Đang tìm bàn blackjack phù hợp...", en: "Searching for a suitable blackjack table...", aliases: ["Dang tim ban blackjack phu hop..."] }
    ,{ vi: "Bàn 2-4 người cùng máy với dealer", en: "Local 2-4 player tables with a dealer", aliases: ["Ban 2-4 nguoi cung may voi dealer"] }
    ,{ vi: "Kết thúc 10 trận Cờ tướng online.", en: "Finish 10 online Xiangqi matches.", aliases: ["Ket thuc 10 tran Co tuong online."] }
    ,{ vi: "Manager không đổi quyền tài khoản", en: "Managers cannot change account roles", aliases: ["Manager khong doi quyen tai khoan"] }
    ,{ vi: "Room chưa ở trạng thái đang chơi.", en: "The room is not currently in progress.", aliases: ["Room chua o trang thai dang choi."] }
    ,{ vi: "Bàn chơi trực tuyến chưa sẵn sàng", en: "The online table is not ready yet", aliases: ["Ban choi truc tuyen chua san sang"] }
    ,{ vi: "Không có kết quả khớp chính xác.", en: "No exact matches found.", aliases: ["Khong co ket qua khop chinh xac."] }
    ,{ vi: "Đã liên kết thành công tài khoản", en: "Account linked successfully", aliases: ["Da lien ket thanh cong tai khoan"] }
    ,{ vi: "Chưa có ván nào được giải quyết.", en: "No rounds have been resolved yet.", aliases: ["Chua co van nao duoc giai quyet."] }
    ,{ vi: "Xác nhận đầu hàng cho người chơi", en: "Confirm surrender for the player", aliases: ["Xac nhan dau hang cho nguoi choi"] }
    ,{ vi: "Bạn cần đăng nhập để tải avatar.", en: "You need to sign in to upload an avatar.", aliases: ["Ban can dang nhap de tai avatar."] }
    ,{ vi: "Đăng nhập mạng xã hội thất bại.", en: "Social sign-in failed.", aliases: ["Dang nhap mang xa hoi that bai."] }
    ,{ vi: "Số tài khoản admin đã tải file.", en: "Number of admin accounts included in the export.", aliases: ["So tai khoan admin da tai file."] }
    ,{ vi: "Đối thủ đã đầu hàng. Bạn thắng!", en: "The opponent surrendered. You win!", aliases: ["Doi thu da dau hang. Ban thang!"] }
    ,{ vi: "Vui lòng chọn ngày sinh hợp lệ.", en: "Please choose a valid birth date.", aliases: ["Vui long chon ngay sinh hop le."] }
    ,{ vi: "Không tải được danh sách bạn bè", en: "Unable to load the friends list", aliases: ["Khong tai duoc danh sach ban be"] }
    ,{ vi: "không có nhà/hotel để sửa chữa.", en: "No house/hotel is available to repair.", aliases: ["khong co nha/hotel de sua chua."] }
    ,{ vi: "Đã cập nhật danh sách phòng mở.", en: "Open room list updated.", aliases: ["Da cap nhat danh sach phong mo."] }
    ,{ vi: "Đang làm mới danh sách phòng...", en: "Refreshing the room list...", aliases: ["Dang lam moi danh sach phong..."] }
    ,{ vi: "Đang đồng bộ tài khoản của bạn", en: "Syncing your account", aliases: ["Dang dong bo tai khoan cua ban"] }
    ,{ vi: "Tạo và xóa thông báo hệ thống.", en: "Create and remove system notifications.", aliases: ["Tao va xoa thong bao he thong."] }
    ,{ vi: "Mở thư viện game như app riêng", en: "Open the game library as a standalone app", aliases: ["Mo thu vien game nhu app rieng"] }
    ,{ vi: "Khởi tạo lại ván đấu đang chơi", en: "Reinitialize the current in-progress match", aliases: ["Khoi tao lai van dau dang choi"] }
    ,{ vi: "Đang tìm phòng quiz phù hợp...", en: "Searching for a suitable quiz room...", aliases: ["Dang tim phong quiz phu hop..."] }
    ,{ vi: "Không thể chuẩn bị phòng quiz.", en: "Cannot prepare the quiz room.", aliases: ["Khong the chuan bi phong quiz."] }
    ,{ vi: "Bản đồ trận đấu của người chơi", en: "Player match map", aliases: ["Ban do tran dau cua nguoi choi"] }
    ,{ vi: "Chọn setup và bắt đầu ván mới.", en: "Choose the setup and start a new match.", aliases: ["Chon setup va bat dau van moi."] }
    ,{ vi: "Đã sao chép link mời vào phòng", en: "Room invite link copied", aliases: ["Da sao chep link moi vao phong"] }
    ,{ vi: "Không thể đồng bộ lịch sử chat", en: "Cannot sync chat history", aliases: ["Khong the dong bo lich su chat"] }
    ,{ vi: "Chưa có câu hỏi nào được chấm.", en: "No questions have been graded yet.", aliases: ["Chua co cau hoi nao duoc cham."] }
    ,{ vi: "Vui lòng nhập đầy đủ mật khẩu.", en: "Please enter the full password.", aliases: ["Vui long nhap day du mat khau."] }
    ,{ vi: "Không tìm thấy bạn bè phù hợp.", en: "No matching friends found.", aliases: ["Khong tim thay ban be phu hop."] }
    ,{ vi: "Muốn kết nối và chơi cùng bạn", en: "Wants to connect and play with you", aliases: ["Muon ket noi va choi cung ban"] }
    ,{ vi: "Đang chờ đối thủ vào phòng...", en: "Waiting for the opponent to join the room...", aliases: ["Dang cho doi thu vao phong..."] }
    ,{ vi: "Đối thủ đầu hàng - bạn thắng!", en: "The opponent surrendered - you win!", aliases: ["Doi thu dau hang - ban thang!"] }
    ,{ vi: "Chưa có phòng đang hoạt động.", en: "No active rooms right now.", aliases: ["Chua co phong dang hoat dong."] }
    ,{ vi: "Không tải được bảng xếp hạng.", en: "Cannot load the leaderboard.", aliases: ["Khong tai duoc bang xep hang."] }
    ,{ vi: "Đã kết nối. Có thể vào phòng.", en: "Connected. You can join the room.", aliases: ["Da ket noi. Co the vao phong."] }
    ,{ vi: "Không có log phù hợp bộ lọc.", en: "No logs match the current filters.", aliases: ["Khong co log phu hop bo loc."] }
    ,{ vi: "Bạn chắc chắn muốn đầu hàng?", en: "Are you sure you want to surrender?", aliases: ["Ban chac chan muon dau hang?"] }
    ,{ vi: "Bot trả nước đi không hợp lệ", en: "The bot returned an invalid move", aliases: ["Bot tra nuoc di khong hop le"] }
    ,{ vi: "Chưa có thông điệp hệ thống.", en: "No system messages yet.", aliases: ["Chua co thong diep he thong."] }
    ,{ vi: "Nhập tên hiển thị hoặc email", en: "Enter a display name or email", aliases: ["Nhap ten hien thi hoac email"] }
    ,{ vi: "Đặt cược để bắt đầu ván mới.", en: "Place a bet to start a new round.", aliases: ["Dat cuoc de bat dau van moi."] }
    ,{ vi: "Phòng 4 người, bot, spectate", en: "4-player rooms, bots, spectate", aliases: ["Phong 4 nguoi, bot, spectate"] }
    ,{ vi: "Danh mục người đang đến lượt", en: "Current-turn player category", aliases: ["Danh muc nguoi dang den luot"] }
    ,{ vi: "Hiện tại không có phòng nào.", en: "There are currently no rooms.", aliases: ["Hien tai khong co phong nao."] }
    ,{ vi: "Tổng quan thông báo hệ thống", en: "System notifications overview", aliases: ["Tong quan thong bao he thong"] }
    ,{ vi: "Đang gửi yêu cầu đầu hàng...", en: "Sending surrender request...", aliases: ["Dang gui yeu cau dau hang..."] }
    ,{ vi: "Đang gửi...", en: "Sending...", aliases: ["Dang gui..."] }
    ,{ vi: "Bạn thắng - sẵn sàng lên ván", en: "You won - ready for the next board", aliases: ["Ban thang - san sang len van"] }
    ,{ vi: "Mất kết nối. Đang thử lại...", en: "Connection lost. Retrying...", aliases: ["Mat ket noi. Dang thu lai..."] }
    ,{ vi: "Lỗi kết nối. Đang thử lại...", en: "Connection error. Retrying...", aliases: ["Loi ket noi. Dang thu lai..."] }
    ,{ vi: "Bạn đã đầu hàng và rời phòng", en: "You surrendered and left the room", aliases: ["Ban da dau hang va roi phong"] }
    ,{ vi: "Chưa có quote nào được chạy.", en: "No quote has been run yet.", aliases: ["Chua co quote nao duoc chay."] }
    ,{ vi: "Đóng trang đặt lại mật khẩu", en: "Close the password reset page", aliases: ["Dong trang dat lai mat khau"] }
    ,{ vi: "Cập nhật thông tin và quyền", en: "Update account information and roles", aliases: ["Cap nhat thong tin va quyen"] }
    ,{ vi: "Bạn đã đầu hàng! Bot thắng.", en: "You surrendered! The bot wins.", aliases: ["Ban da dau hang! Bot thang."] }
    ,{ vi: "Tiền cược cho ghế đang chọn", en: "Bet amount for the selected seat", aliases: ["Tien cuoc cho ghe dang chon"] }
    ,{ vi: "Bàn chờ tối đa 5 người chơi", en: "The table supports up to 5 players", aliases: ["Ban cho toi da 5 nguoi choi"] }
    ,{ vi: "3. Tìm phòng online đang mở", en: "3. Find an open online room", aliases: ["3. Tim phong online dang mo"] }
    ,{ vi: "Không thể khởi tạo realtime", en: "Cannot initialize realtime", aliases: ["Khong the khoi tao realtime"] }
    ,{ vi: "Tài khoản cần được xem lại", en: "Account requires review", aliases: ["Tai khoan can duoc xem lai"] }
    ,{ vi: "Tổng quan thông báo bạn bè", en: "Friends notifications overview", aliases: ["Tong quan thong bao ban be"] }
    ,{ vi: "Không có kết quả tương tự.", en: "No similar results found.", aliases: ["Khong co ket qua tuong tu."] }
    ,{ vi: "kênh đăng nhập đã liên kết", en: "linked sign-in providers", aliases: ["kenh dang nhap da lien ket"] }
    ,{ vi: "Chưa có lịch sử hoạt động", en: "No activity history yet", aliases: ["Chua co lich su hoat dong"] }
    ,{ vi: "Chưa có mốc điểm được mở.", en: "No score milestones unlocked yet.", aliases: ["Chua co moc diem duoc mo."] }
    ,{ vi: "Vui lòng chọn tệp avatar.", en: "Please choose an avatar file.", aliases: ["Vui long chon tep avatar."] }
    ,{ vi: "Không tìm thấy người chơi", en: "Player not found", aliases: ["Khong tim thay nguoi choi"] }
    ,{ vi: "Thông báo khóa tài khoản", en: "Account lock notice", aliases: ["Thong bao khoa tai khoan"] }
    ,{ vi: "Nhật ký tải file gần đây", en: "Recent file export log", aliases: ["Nhat ky tai file gan day"] }
    ,{ vi: "Đang khởi tạo ván mới...", en: "Initializing a new match...", aliases: ["Dang khoi tao van moi..."] }
    ,{ vi: "Đang khởi tạo kết nối...", en: "Initializing the connection...", aliases: ["Dang khoi tao ket noi..."] }
    ,{ vi: "Danh sách phòng đang chờ", en: "Waiting room list", aliases: ["Danh sach phong dang cho"] }
    ,{ vi: "Không tìm thấy tài khoản", en: "Account not found", aliases: ["Khong tim thay tai khoan"] }
    ,{ vi: "Tổng quan trợ lý hỏi đáp", en: "Assistant overview", aliases: ["Tong quan tro ly hoi dap"] }
    ,{ vi: "Cập nhật thông tin hồ sơ", en: "Update profile information", aliases: ["Cap nhat thong tin ho so"] }
    ,{ vi: "Mở chế độ chính của game", en: "Open the main mode of this game", aliases: ["Mo che do chinh cua game"] }
    ,{ vi: "Bàn chơi Cờ tỷ phú local", en: "Local Monopoly table", aliases: ["Ban choi Co ty phu local"] }
    ,{ vi: "Chơi trực tuyến sẵn sàng", en: "Online play ready", aliases: ["Choi truc tuyen san sang"] }
    ,{ vi: "Khách được chơi", en: "Guests can play", aliases: ["Khach duoc choi"] }
    ,{ vi: "Lượt người chơi", en: "Player turn", aliases: ["Luot nguoi choi"] }
    ,{ vi: "Trạng thái bàn", en: "Table status", aliases: ["Trang thai ban"] }
    ,{ vi: "Chờ người chơi", en: "Waiting for players", aliases: ["Cho nguoi choi"] }
    ,{ vi: "Bàn người chơi", en: "Player table", aliases: ["Ban nguoi choi"] }
    ,{ vi: "Điểm tối thiểu", en: "Minimum score", aliases: ["Diem toi thieu"] }
    ,{ vi: "Đang đăng nhập", en: "Signing in", aliases: ["Dang dang nhap"] }
    ,{ vi: "Tạo thông báo", en: "Create notification", aliases: ["Tao thong bao"] }
    ,{ vi: "Tìm phòng đấu", en: "Find a match room", aliases: ["Tim phong dau"] }
    ,{ vi: "Chưa cập nhật", en: "Not updated yet", aliases: ["Chua cap nhat"] }
    ,{ vi: "Đang tới lượt", en: "Almost your turn", aliases: ["Dang toi luot"] }
    ,{ vi: "Chơi cùng máy", en: "Same-device play", aliases: ["Choi cung may"] }
    ,{ vi: "Ván tiếp theo", en: "Next round", aliases: ["Van tiep theo"] }
    ,{ vi: "Mới chấp nhận", en: "Just accepted", aliases: ["Moi chap nhan"] }
    ,{ vi: "Đang cập nhật", en: "Updating", aliases: ["Dang cap nhat"] }
    ,{ vi: "Về đăng nhập", en: "Back to login", aliases: ["Ve dang nhap"] }
    ,{ vi: "Làm mới form", en: "Reset form", aliases: ["Lam moi form"] }
    ,{ vi: "Về chúng tôi", en: "About us", aliases: ["Ve chung toi"] }
    ,{ vi: "Về trang chủ", en: "Back to home", aliases: ["Ve trang chu"] }
    ,{ vi: "Chơi gần đây", en: "Recently played", aliases: ["Choi gan day"] }
    ,{ vi: "Tạo bảng mới", en: "Create new board", aliases: ["Tao bang moi"] }
    ,{ vi: "Tạo bàn mới", en: "Create a new board", aliases: ["Tao ban moi"] }
    ,{ vi: "Mã tài khoản", en: "Account ID", aliases: ["Ma tai khoan"] }
    ,{ vi: "Mở vòng mới.", en: "Open a new round.", aliases: ["Mo vong moi."] }
    ,{ vi: "mở vòng mới", en: "open a new round", aliases: ["mo vong moi"] }
    ,{ vi: "tới trắng (", en: "instant win (", aliases: ["toi trang ("] }
    ,{ vi: "Mở profile", en: "Open profile", aliases: ["Mo profile"] }
    ,{ vi: "Tên bạn bè", en: "Friend name", aliases: ["Ten ban be"] }
    ,{ vi: "Về bạn bè", en: "Back to friends", aliases: ["Ve ban be"] }
    ,{ vi: "Người gửi", en: "Sender", aliases: ["Nguoi gui"] }
    ,{ vi: "Mở hồ sơ", en: "Open profile", aliases: ["Mo ho so"] }
    ,{ vi: "Lượt: Trắng", en: "Turn: White", aliases: ["Luot: Trang"] }
    ,{ vi: "Mở link mời", en: "Open invite link", aliases: ["Mo link moi"] }
  ];

  const VI_FIXES = [
    ["Dang cho doi doi thu", "Đang chờ đối thủ"],
    ["Dang cho doi thu", "Đang chờ đối thủ"],
    ["Dang cho du nguoi", "Đang chờ đủ người"],
    ["Dang cho them 1 nguoi choi", "Đang chờ thêm 1 người chơi"],
    ["Dang cho vao phong", "Đang chờ vào phòng"],
    ["Dang cho ket noi", "Đang chờ kết nối"],
    ["Dang cho server xac nhan nuoc di", "Đang chờ server xác nhận nước đi"],
    ["Dang ket noi", "Đang kết nối"],
    ["Da ket noi", "Đã kết nối"],
    ["Dang vao phong", "Đang vào phòng"],
    ["Dang khoi tao ket noi", "Đang khởi tạo kết nối"],
    ["Dang khoi tao van", "Đang khởi tạo ván"],
    ["Dang tai danh sach phong", "Đang tải danh sách phòng"],
    ["Dang tai danh sach ban be", "Đang tải danh sách bạn bè"],
    ["Dang den luot", "Đang đến lượt"],
    ["Dang chon", "Đang chọn"],
    ["Dang gui", "Đang gửi"],
    ["Dang danh", "Đang đánh"],
    ["Dang choi", "Đang chơi"],
    ["Chua den luot", "Chưa đến lượt"],
    ["Chua dang nhap", "Chưa đăng nhập"],
    ["Chua vao phong", "Chưa vào phòng"],
    ["Chua co ma phong", "Chưa có mã phòng"],
    ["Chua co phong dang cho", "Chưa có phòng đang chờ"],
    ["Chua co nuoc di", "Chưa có nước đi"],
    ["Chua co du lieu lich su", "Chưa có dữ liệu lịch sử"],
    ["Chua ket noi server", "Chưa kết nối server"],
    ["Chua ket ban", "Chưa kết bạn"],
    ["Chua chon", "Chưa chọn"],
    ["Khong ket noi duoc may chu", "Không kết nối được máy chủ"],
    ["Khong xac dinh duoc", "Không xác định được"],
    ["Khong tai duoc SockJS/STOMP.", "Không tải được SockJS/STOMP."],
    ["Khong gui duoc tin nhan", "Không gửi được tin nhắn"],
    ["Khong gui duoc nuoc di. Vui long thu lai.", "Không gửi được nước đi. Vui lòng thử lại."],
    ["Khong copy tu dong duoc. Hay copy thu cong", "Không copy tự động được. Hãy copy thủ công"],
    ["Khong goi duoc API bot", "Không gọi được API bot"],
    ["Khong reset duoc bot. Thu lai.", "Không reset được bot. Thử lại."],
    ["Khong co loi moi ket ban", "Không có lời mời kết bạn"],
    ["Khong co loi moi dang cho", "Không có lời mời đang chờ"],
    ["Khong co loi moi da gui", "Không có lời mời đã gửi"],
    ["Khong co ket qua khop chinh xac", "Không có kết quả khớp chính xác"],
    ["Khong co ket qua tuong tu", "Không có kết quả tương tự"],
    ["Khong can dang nhap", "Không cần đăng nhập"],
    ["Khong can", "Không cần"],
    ["Khong the", "Không thể"],
    ["Khong co", "Không có"],
    ["Loi WebSocket", "Lỗi WebSocket"],
    ["Mat ket noi", "Mất kết nối"],
    ["Ket noi mang on dinh", "Kết nối mạng ổn định"],
    ["Internet da ket noi lai", "Internet đã kết nối lại"],
    ["Dang offline - kiem tra mang internet", "Đang offline - kiểm tra mạng internet"],
    ["Bang xep hang", "Bảng xếp hạng"],
    ["Danh sach xep hang", "Danh sách xếp hạng"],
    ["Lich su dau", "Lịch sử đấu"],
    ["Thu vien game", "Thư viện game"],
    ["Ban be", "Bạn bè"],
    ["Thong bao", "Thông báo"],
    ["Tai khoan", "Tài khoản"],
    ["Mat khau", "Mật khẩu"],
    ["Xac thuc", "Xác thực"],
    ["Xac nhan", "Xác nhận"],
    ["Vui long", "Vui lòng"],
    ["Hay ", "Hãy "],
    ["Mau quan cua ban", "Màu quân của bạn"],
    ["Nguoi choi trong phong", "Người chơi trong phòng"],
    ["Nguoi choi", "Người chơi"],
    ["Nuoc di", "Nước đi"],
    ["Luot hien tai", "Lượt hiện tại"],
    ["Luot cua", "Lượt của"],
    ["Luot:", "Lượt:"],
    ["Den luot", "Đến lượt"],
    ["Trang thai van dau", "Trạng thái ván đấu"],
    ["Thong tin van dau", "Thông tin ván đấu"],
    ["Trang thai", "Trạng thái"],
    ["Dau hang", "Đầu hàng"],
    ["Roi phong", "Rời phòng"],
    ["Van moi", "Ván mới"],
    ["Choi lai", "Chơi lại"],
    ["Nhap ma phong", "Nhập mã phòng"],
    ["Nhap tin nhan", "Nhập tin nhắn"],
    ["Nhap cau hoi", "Nhập câu hỏi"],
    ["Nhan tin", "Nhắn tin"],
    ["Tin nhan rieng", "Tin nhắn riêng"],
    ["Tro ly hoi dap", "Trợ lý hỏi đáp"],
    ["Loi tro ly", "Lỗi trợ lý"],
    ["Da nhan phan hoi tu tro ly", "Đã nhận phản hồi từ trợ lý"],
    ["Tro ly da phan hoi", "Trợ lý đã phản hồi"],
    ["Da tao thong bao", "Đã tạo thông báo"],
    ["Chua co thong bao.", "Chưa có thông báo."],
    ["Tai anh dai dien thanh cong", "Tải ảnh đại diện thành công"],
    ["Vui long chon tep anh", "Vui lòng chọn tệp ảnh"],
    ["Chua chon tai khoan", "Chưa chọn tài khoản"],
    ["Xep hang theo diem", "Xếp hạng theo điểm"],
    ["Tra cuu tran dau theo tai khoan, ma game, ket qua, khoang ngay va xuat du lieu nhanh.", "Tra cứu trận đấu theo tài khoản, mã game, kết quả, khoảng ngày và xuất dữ liệu nhanh."],
    ["Quen mat khau?", "Quên mật khẩu?"],
    ["Dang nhap voi Google", "Đăng nhập với Google"],
    ["Dang nhap voi Facebook", "Đăng nhập với Facebook"],
    ["Gui ma xac thuc", "Gửi mã xác thực"],
    ["Dat lai mat khau", "Đặt lại mật khẩu"],
    ["Ma xac thuc", "Mã xác thực"],
    ["Duong dan anh dai dien", "Đường dẫn ảnh đại diện"],
    ["Tai khoan duy nhat", "Tài khoản duy nhất"],
    ["Trinh duyet", "Trình duyệt"],
    ["Truy van", "Truy vấn"],
    ["Xuat CSV trang nay", "Xuất CSV trang này"],
    ["Xuat Excel trang nay", "Xuất Excel trang này"],
    ["Xuat Excel tat ca da loc", "Xuất Excel tất cả đã lọc"],
    ["Da xuat tep cai dat JSON.", "Đã xuất tệp cài đặt JSON."],
    ["Khong the xuat cai dat.", "Không thể xuất cài đặt."],
    ["Khong doc duoc tep cai dat.", "Không đọc được tệp cài đặt."],
    ["Tieng Anh", "Tiếng Anh"],
    ["Khong the sao chep tu dong. Hay sao chep thu cong", "Không thể sao chép tự động. Hãy sao chép thủ công"],
    ["Email la bat buoc", "Email là bắt buộc"],
    ["Xu ly thanh cong", "Xử lý thành công"],
    ["Xac thuc email thanh cong", "Xác thực email thành công"],
    ["Gui lai ma", "Gửi lại mã"],
    ["Da sao chep link moi", "Đã sao chép link mời"],
    ["Da sao chep link xem", "Đã sao chép link xem"],
    ["Nhap user id de loc", "Nhập user ID để lọc"],
    ["Co vua", "Cờ vua"],
    ["Co tuong", "Cờ tướng"],
    ["Dieu huong", "Điều hướng"],
    ["Danh muc", "Danh mục"],
    ["Gan day", "Gần đây"],
    ["Moi", "Mới"],
    ["Xu huong", "Xu hướng"],
    ["Cap nhat", "Cập nhật"],
    ["Nguyen ban", "Nguyên bản"],
    ["Nhieu nguoi", "Nhiều người"],
    ["Danh bai", "Đánh bài"],
    ["Do min", "Dò mìn"],
    ["Go chu", "Gõ chữ"],
    ["Giai do", "Giải đố"],
    ["Phong online", "Phòng online"],
    ["Sanh phong", "Sảnh phòng"],
    ["Kich hoat Admin", "Kích hoạt Admin"],
    ["Kich hoat quan tri", "Kích hoạt quản trị"],
    ["Quan ly nguoi dung", "Quản lý người dùng"],
    ["Trung tam Admin", "Trung tâm Admin"],
    ["Trung tam quan tri", "Trung tâm quản trị"],
    ["An sidebar", "Ẩn sidebar"],
    ["Hien sidebar", "Hiện sidebar"],
    ["Dong sidebar", "Đóng sidebar"],
    ["Mo sidebar", "Mở sidebar"],
    ["Bat/tat sidebar", "Bật/tắt sidebar"],
    ["Giao dien", "Giao diện"],
    ["Tu dong", "Tự động"],
    ["Sang", "Sáng"],
    ["Toi", "Tối"],
    ["Bam de chuyen sang", "Bấm để chuyển sang"],
    ["tiep theo", "tiếp theo"],
    ["Che do", "Chế độ"],
    ["Trang chu", "Trang chủ"],
    ["Nguoi xem", "Người xem"],
    ["Doi thu", "Đối thủ"],
    ["An quan", "Ăn quân"],
    ["Tham gia choi", "Tham gia chơi"],
    ["Vao che do xem", "Vào chế độ xem"],
    ["Ve Online Hub", "Về Online Hub"],
    ["Da kich hoat quyen quan tri", "Đã kích hoạt quyền quản trị"],
    ["Khong the kich hoat quyen quan tri", "Không thể kích hoạt quyền quản trị"],
    ["Nhap ma kich hoat quan tri:", "Nhập mã kích hoạt quản trị:"],
    ["Quay lai", "Quay lại"],
    ["Ban co the", "Bạn có thể"],
    ["tu cach khach", "tư cách khách"],
    ["khong can dang nhap", "không cần đăng nhập"],
    ["Phong cho", "Phòng chờ"]
  ];

  function preferredViText(pair) {
    return String(pair?.vi || "").trim();
  }

  function toAscii(value) {
    return String(value || "")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/đ/g, "d")
      .replace(/Đ/g, "D");
  }

  function escapeRegExp(value) {
    return String(value || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }

  function buildSearchPattern(text) {
    const value = String(text || "").trim();
    if (!value) {
      return "";
    }
    const escaped = escapeRegExp(value);
    if (/^[A-Za-z0-9]+$/.test(value)) {
      return "\\b" + escaped + "\\b";
    }
    return escaped;
  }

  function buildPairReplacements(language) {
    const all = [];
    PHRASE_PAIRS.forEach((pair) => {
      const aliases = pair.aliases || [];
      const preferredVi = preferredViText(pair);
      const sources = language === "en"
        ? new Set([pair.vi, preferredVi, toAscii(pair.vi), toAscii(preferredVi), ...aliases])
        : new Set([pair.en, toAscii(pair.en), toAscii(pair.vi), toAscii(preferredVi), ...aliases]);
      sources.forEach((source) => {
        const text = String(source || "").trim();
        if (!text) return;
        all.push({
          regex: new RegExp(buildSearchPattern(text), "gi"),
          value: language === "en" ? pair.en : preferredVi,
          weight: text.length
        });
      });
    });
    all.sort((a, b) => b.weight - a.weight || String(b.value).length - String(a.value).length);
    return all;
  }

  function normalizeExactKey(value) {
    return String(value || "")
      .replace(/\s+/g, " ")
      .trim()
      .toLowerCase();
  }

  function buildExactPhraseMap(language) {
    const map = new Map();
    PHRASE_PAIRS.forEach((pair) => {
      const aliases = pair.aliases || [];
      const preferredVi = preferredViText(pair);
      const sources = language === "en"
        ? new Set([pair.vi, preferredVi, toAscii(pair.vi), toAscii(preferredVi), ...aliases])
        : new Set([pair.en, toAscii(pair.en), toAscii(pair.vi), toAscii(preferredVi), ...aliases]);
      sources.forEach((source) => {
        const key = normalizeExactKey(source);
        if (!key || map.has(key)) return;
        map.set(key, language === "en" ? pair.en : preferredVi);
      });
    });
    return map;
  }

  const PHRASE_REPLACEMENTS = {
    vi: buildPairReplacements("vi"),
    en: buildPairReplacements("en")
  };
  const EXACT_PHRASE_MAPS = {
    vi: buildExactPhraseMap("vi"),
    en: buildExactPhraseMap("en")
  };
  const VI_FIX_REPLACEMENTS = VI_FIXES
    .flatMap(([oldValue, newValue]) => {
      const normalizedValue = String(newValue || "").trim();
      return [String(oldValue || "").trim()]
        .filter(Boolean)
        .map((pattern) => ({
          regex: new RegExp(escapeRegExp(pattern), "gi"),
          value: normalizedValue,
          weight: pattern.length
        }));
    })
    .sort((a, b) => b.weight - a.weight || String(b.value).length - String(a.value).length);

  function normalizeLanguage(language) {
    const value = String(language || "").trim().toLowerCase();
    return SUPPORTED_LANGS.includes(value) ? value : "vi";
  }

  function translateByPairs(text, language) {
    let output = String(text || "");
    const replacements = PHRASE_REPLACEMENTS[language] || [];
    replacements.forEach((entry) => {
      output = output.replace(entry.regex, entry.value);
    });
    return output;
  }

  function translateExactPhrase(text, language) {
    const key = normalizeExactKey(text);
    if (!key) return null;
    return EXACT_PHRASE_MAPS[language]?.get(key) || null;
  }

  function looksLongPhrase(text) {
    const normalized = String(text || "").trim();
    if (!normalized) return false;
    const wordCount = normalized.split(/\s+/).filter(Boolean).length;
    return normalized.length >= 20 || wordCount >= 4;
  }

  function stillLooksVietnameseAfterEnglishTranslation(text) {
    const normalized = toAscii(text).toLowerCase();
    return /\b(?:dang|khong|chua|nhap|tao|tim|ve|phong|tran|nguoi|quan|thong|ket|xuat|lich|tai|choi|dau|mo|sanh|tat|ten|khach|ma|cung|nuoc|van|trang|quay|tiep|hoan|kich|danh|hay|duoc|thoi|luot|chon|diem|thua|thang|moi|quyen|khoa|nhan|muc|giu|vua|tuong|phu|bai|min|vang|lam|noi|mot|moi|ban|cho|roi|vao|ra|tren|duoi)\b/.test(normalized);
  }

  function shouldKeepSourceText(source, translated, language, exactMatchUsed) {
    if (language !== "en" || exactMatchUsed) return false;
    if (translated === source) return false;
    if (!looksLongPhrase(source)) return false;
    return stillLooksVietnameseAfterEnglishTranslation(translated);
  }

  function applyVietnameseFixes(text) {
    let output = String(text || "");
    VI_FIX_REPLACEMENTS.forEach((entry) => {
      output = output.replace(entry.regex, entry.value);
    });
    return output;
  }

  function translateText(rawText, language) {
    const text = String(rawText || "");
    if (!text.trim()) return text;

    const lang = normalizeLanguage(language || currentLang);
    const exactMatch = translateExactPhrase(text, lang);
    let output = exactMatch != null ? exactMatch : translateByPairs(text, lang);
    if (lang === "vi") {
      output = applyVietnameseFixes(output);
    }
    if (shouldKeepSourceText(text, output, lang, exactMatch != null)) {
      return text;
    }
    return output;
  }

  function shouldSkipElement(element) {
    if (!element || element.nodeType !== Node.ELEMENT_NODE) return true;
    if (["SCRIPT", "STYLE", "NOSCRIPT", "TEXTAREA", "CODE", "PRE", "IFRAME"].includes(element.tagName)) {
      return true;
    }
    return !!element.closest("[data-no-i18n='true']");
  }

  function updateTextNode(node) {
    if (!node || node.nodeType !== Node.TEXT_NODE) return;
    const parent = node.parentElement;
    if (!parent || shouldSkipElement(parent)) return;

    const currentValue = node.nodeValue || "";
    let state = textState.get(node);
    if (!state) {
      state = { source: currentValue, rendered: currentValue };
      textState.set(node, state);
    } else if (currentValue !== state.rendered && currentValue !== state.source) {
      state.source = currentValue;
    }

    const translated = translateText(state.source, currentLang);
    if (translated !== currentValue) {
      node.nodeValue = translated;
    }
    state.rendered = translated;
  }

  function updateElementAttribute(element, attrName) {
    const currentValue = element.getAttribute(attrName);
    if (currentValue == null || !String(currentValue).trim()) return;

    let state = attrState.get(element);
    if (!state) {
      state = {};
      attrState.set(element, state);
    }

    let attr = state[attrName];
    if (!attr) {
      attr = { source: currentValue, rendered: currentValue };
      state[attrName] = attr;
    } else if (currentValue !== attr.rendered && currentValue !== attr.source) {
      attr.source = currentValue;
    }

    const translated = translateText(attr.source, currentLang);
    if (translated !== currentValue) {
      element.setAttribute(attrName, translated);
    }
    attr.rendered = translated;
  }

  function updateInputValue(element) {
    if (!element || element.tagName !== "INPUT") return;
    const type = String(element.type || "text").toLowerCase();
    if (!TRANSLATABLE_INPUT_TYPES.has(type)) return;
    const isButtonInput = BUTTON_INPUT_TYPES.has(type);
    const currentValue = element.value;
    if (!String(currentValue || "").trim()) return;

    let state = attrState.get(element);
    if (!state) {
      state = {};
      attrState.set(element, state);
    }

    let valueState = state.value;
    if (!valueState) {
      valueState = {
        source: currentValue,
        rendered: currentValue,
        defaultSource: element.defaultValue || currentValue
      };
      state.value = valueState;
    } else if (currentValue !== valueState.rendered && currentValue !== valueState.source) {
      if (!isButtonInput) {
        return;
      }
      valueState.source = currentValue;
    }

    const translated = translateText(valueState.source, currentLang);
    if (translated !== currentValue) {
      element.value = translated;
    }
    if (!isButtonInput) {
      const defaultTranslated = translateText(valueState.defaultSource, currentLang);
      if (defaultTranslated !== element.defaultValue) {
        element.defaultValue = defaultTranslated;
      }
    }
    valueState.rendered = translated;
  }

  function translateRoot(root) {
    const start = root && root.nodeType === Node.ELEMENT_NODE ? root : document.body;
    if (!start || shouldSkipElement(start)) return;

    const walker = document.createTreeWalker(
      start,
      NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT,
      {
        acceptNode(node) {
          if (node.nodeType === Node.ELEMENT_NODE) {
            return shouldSkipElement(node) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT;
          }
          if (node.nodeType === Node.TEXT_NODE) {
            const parent = node.parentElement;
            if (!parent || shouldSkipElement(parent)) return NodeFilter.FILTER_REJECT;
            if (!String(node.nodeValue || "").trim()) return NodeFilter.FILTER_REJECT;
            return NodeFilter.FILTER_ACCEPT;
          }
          return NodeFilter.FILTER_REJECT;
        }
      }
    );

    let node = walker.currentNode;
    while (node) {
      if (node.nodeType === Node.TEXT_NODE) {
        updateTextNode(node);
      } else if (node.nodeType === Node.ELEMENT_NODE) {
        TEXT_ATTRS.forEach((attr) => updateElementAttribute(node, attr));
        updateInputValue(node);
      }
      node = walker.nextNode();
    }
  }

  function applyAll() {
    if (!document.body) return;
    applying = true;
    try {
      translateRoot(document.body);
      document.documentElement.setAttribute("lang", currentLang);
    } finally {
      applying = false;
    }
  }

  function scheduleApplyAll() {
    if (pendingApply) return;
    pendingApply = true;
    window.requestAnimationFrame(() => {
      pendingApply = false;
      applyAll();
    });
  }

  function startObserver() {
    if (observer || !document.body) return;
    observer = new MutationObserver(() => {
      if (!applying) scheduleApplyAll();
    });
    observer.observe(document.body, {
      childList: true,
      subtree: true,
      characterData: true,
      attributes: true,
      attributeFilter: TEXT_ATTRS
    });
  }

  function notifyChange() {
    changeListeners.forEach((listener) => {
      try { listener(currentLang); } catch (_) { }
    });
  }

  function setLanguage(language) {
    const next = normalizeLanguage(language);
    if (next === currentLang) return;
    currentLang = next;
    localStorage.setItem(STORAGE_KEY, next);
    applyAll();
    notifyChange();
  }

  function getLanguage() {
    return currentLang;
  }

  function onChange(listener) {
    if (typeof listener !== "function") return function () {};
    changeListeners.add(listener);
    return function unsubscribe() {
      changeListeners.delete(listener);
    };
  }

  function init() {
    if (initialized) {
      applyAll();
      return;
    }
    initialized = true;
    currentLang = normalizeLanguage(localStorage.getItem(STORAGE_KEY) || "vi");
    localStorage.setItem(STORAGE_KEY, currentLang);
    applyAll();
    startObserver();
    notifyChange();
  }

  window.CaroI18n = {
    init,
    setLanguage,
    getLanguage,
    onChange,
    apply: applyAll,
    t: function (text) { return translateText(text, currentLang); },
    supportedLanguages: SUPPORTED_LANGS.slice()
  };
})();


