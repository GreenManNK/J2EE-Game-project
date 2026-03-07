(function () {
  const STORAGE_KEY = "caro_ui_lang";
  const SUPPORTED_LANGS = ["vi", "en"];
  const TEXT_ATTRS = ["placeholder", "title", "aria-label", "alt", "data-bs-original-title"];
  const BUTTON_INPUT_TYPES = new Set(["button", "submit", "reset"]);

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
    { vi: "Đăng bài", en: "Post", aliases: ["Dang bai"] },
    { vi: "Bảng tin", en: "Feed", aliases: ["Bang tin"] },
    { vi: "Bình luận", en: "Comments", aliases: ["Binh luan"] },
    { vi: "Gửi", en: "Send", aliases: ["Gui"] },
    { vi: "Đầu hàng", en: "Surrender", aliases: ["Dau hang"] },
    { vi: "Rời phòng", en: "Leave room", aliases: ["Roi phong"] },
    { vi: "Ván mới", en: "New match", aliases: ["Van moi"] },
    { vi: "Trạng thái", en: "Status", aliases: ["Trang thai"] },
    { vi: "Kết nối", en: "Connection", aliases: ["Ket noi"] },
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
    { vi: "Phòng online", en: "Online hub", aliases: ["Phong online", "Phong truc tuyen"] },
    { vi: "Tài khoản", en: "Account", aliases: ["Tai khoan"] },
    { vi: "Cài đặt", en: "Settings", aliases: ["Cai dat"] },
    { vi: "Yêu thích", en: "Favorites", aliases: ["Yeu thich"] },
    { vi: "Kích hoạt Admin", en: "Activate Admin", aliases: ["Kich hoat Admin"] },
    { vi: "Quản lý người dùng", en: "Manager Users", aliases: ["Quan ly nguoi dung"] },
    { vi: "Trung tâm Admin", en: "Admin Center", aliases: ["Trung tam Admin"] },
    { vi: "Ẩn sidebar", en: "Hide sidebar", aliases: ["An sidebar"] },
    { vi: "Hiện sidebar", en: "Show sidebar", aliases: ["Hien sidebar"] },
    { vi: "Đóng sidebar", en: "Close sidebar", aliases: ["Dong sidebar"] },
    { vi: "Mở sidebar", en: "Open sidebar", aliases: ["Mo sidebar"] },
    { vi: "Bật/tắt sidebar", en: "Toggle sidebar", aliases: ["Bat/tat sidebar"] },
    { vi: "Ảnh đại diện", en: "Avatar", aliases: ["Anh dai dien"] },
    { vi: "Trực tuyến", en: "Online", aliases: ["Truc tuyen"] },
    { vi: "Ngoại tuyến", en: "Offline", aliases: ["Ngoai tuyen"] },
    { vi: "Bạn cần đăng nhập", en: "Login required", aliases: ["Ban can dang nhap"] },
    { vi: "Mã kích hoạt là bắt buộc", en: "Activation code is required", aliases: ["Ma kich hoat la bat buoc"] },
    { vi: "Đã kích hoạt vai trò Admin", en: "Admin role activated", aliases: ["Da kich hoat vai tro Admin"] },
    { vi: "Không thể kích hoạt vai trò Admin", en: "Cannot activate admin role", aliases: ["Khong the kich hoat vai tro Admin"] },
    { vi: "Chưa có bạn bè nào.", en: "No friends yet.", aliases: ["Chua co ban be nao."] },
    { vi: "Hiện chưa có bạn nào đang online.", en: "No friends are currently online.", aliases: ["Hien chua co ban nao dang online."] },
    { vi: "Không tải được danh sách bạn bè.", en: "Unable to load friends list.", aliases: ["Khong tai duoc danh sach ban be."] },
    { vi: "Giao diện", en: "Theme", aliases: ["Giao dien"] },
    { vi: "Tự động", en: "System", aliases: ["Tu dong"] },
    { vi: "Sáng", en: "Light", aliases: ["Sang"] },
    { vi: "Tối", en: "Dark", aliases: ["Toi"] },
    { vi: "Bấm để chuyển sang", en: "Click to switch to", aliases: ["Bam de chuyen sang"] },
    { vi: "Chuyển giao diện", en: "Switch theme", aliases: ["Chuyen giao dien"] },
    { vi: "tiếp theo", en: "next", aliases: ["tiep theo"] },
    { vi: "Nhập mã kích hoạt Admin:", en: "Enter admin activation code:", aliases: ["Nhap ma kich hoat Admin:"] }
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
    ["Kich hoat Admin", "Kích hoạt Admin"],
    ["Quan ly nguoi dung", "Quản lý người dùng"],
    ["Trung tam Admin", "Trung tâm Admin"],
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
    ["Quay lai", "Quay lại"],
    ["Ban co the", "Bạn có thể"],
    ["tu cach khach", "tư cách khách"],
    ["khong can dang nhap", "không cần đăng nhập"],
    ["Phong cho", "Phòng chờ"]
  ];

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

  function buildPairReplacements() {
    const all = [];
    PHRASE_PAIRS.forEach((pair) => {
      const aliases = pair.aliases || [];
      const sources = new Set([pair.vi, pair.en, toAscii(pair.vi), toAscii(pair.en), ...aliases]);
      sources.forEach((source) => {
        const text = String(source || "").trim();
        if (!text) return;
        all.push({
          regex: new RegExp(buildSearchPattern(text), "gi"),
          vi: pair.vi,
          en: pair.en
        });
      });
    });
    all.sort((a, b) => String(b.vi).length - String(a.vi).length);
    return all;
  }

  const PHRASE_REPLACEMENTS = buildPairReplacements();
  const VI_FIX_REPLACEMENTS = VI_FIXES
    .map(([oldValue, newValue]) => ({
      regex: new RegExp(escapeRegExp(oldValue), "gi"),
      value: newValue
    }))
    .sort((a, b) => String(b.value).length - String(a.value).length);

  function normalizeLanguage(language) {
    const value = String(language || "").trim().toLowerCase();
    return SUPPORTED_LANGS.includes(value) ? value : "vi";
  }

  function translateByPairs(text, language) {
    let output = String(text || "");
    PHRASE_REPLACEMENTS.forEach((entry) => {
      output = output.replace(entry.regex, entry[language]);
    });
    return output;
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
    let output = translateByPairs(text, lang);
    if (lang === "vi") {
      output = applyVietnameseFixes(output);
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
    const type = String(element.type || "").toLowerCase();
    if (!BUTTON_INPUT_TYPES.has(type)) return;
    const currentValue = element.value;
    if (!String(currentValue || "").trim()) return;

    let state = attrState.get(element);
    if (!state) {
      state = {};
      attrState.set(element, state);
    }

    let valueState = state.value;
    if (!valueState) {
      valueState = { source: currentValue, rendered: currentValue };
      state.value = valueState;
    } else if (currentValue !== valueState.rendered && currentValue !== valueState.source) {
      valueState.source = currentValue;
    }

    const translated = translateText(valueState.source, currentLang);
    if (translated !== currentValue) {
      element.value = translated;
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
