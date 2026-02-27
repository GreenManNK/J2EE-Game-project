(function () {
  const STORAGE_KEY = "caro_ui_lang";
  const SUPPORTED_LANGS = ["vi", "en"];
  const TEXT_ATTRS = ["placeholder", "title", "aria-label", "data-bs-original-title"];
  const BUTTON_INPUT_TYPES = new Set(["button", "submit", "reset"]);

  let currentLang = "vi";
  let initialized = false;
  let applying = false;
  let pendingApply = false;
  let observer = null;
  let sourceTitle = "";
  let renderedTitle = "";

  const changeListeners = new Set();
  const textState = new WeakMap();
  const attrState = new WeakMap();

  const PHRASE_PAIRS = [
    { vi: "Trung tâm điều khiển", en: "Control Center" },
    { vi: "Chơi nhanh, quản lý bạn bè và theo dõi bảng xếp hạng.", en: "Play quickly, manage friends, and track the leaderboard." },
    { vi: "Trang chủ", en: "Home" },
    { vi: "Thư viện game", en: "Game Library" },
    { vi: "Bảng xếp hạng", en: "Leaderboard" },
    { vi: "Lịch sử đấu", en: "Match History" },
    { vi: "Bạn bè", en: "Friends" },
    { vi: "Thông báo", en: "Notifications" },
    { vi: "Hồ sơ của tôi", en: "My Profile" },
    { vi: "Đăng nhập", en: "Login" },
    { vi: "Đăng ký", en: "Register" },
    { vi: "Đăng xuất", en: "Logout" },
    { vi: "Chuyển giao diện", en: "Toggle Theme" },
    { vi: "Tìm bạn...", en: "Search friends..." },
    { vi: "Tìm", en: "Search" },
    { vi: "Chưa đăng nhập", en: "Not logged in" },
    { vi: "Đang tải danh sách bạn bè...", en: "Loading friends list..." },
    { vi: "Trạng thái online cập nhật định kỳ", en: "Online status updates periodically" },

    { vi: "Chọn game và vào trận ngay", en: "Pick a game and jump in now" },
    { vi: "Trang chủ được tối ưu theo kiểu web game portal: hành động chính đặt trên cùng, bảng tin cộng đồng tách riêng, và khung giao diện đồng bộ với các trang game/detail.", en: "Home is optimized as a game portal: key actions on top, separate community feed, and a unified layout with game/detail pages." },
    { vi: "Chế độ", en: "Modes" },
    { vi: "Truy cập nhanh", en: "Quick access" },
    { vi: "Cộng đồng", en: "Community" },
    { vi: "Giao diện", en: "Interface" },
    { vi: "Hướng dẫn nhanh", en: "Quick guide" },
    { vi: "Mẹo chơi", en: "Tips" },
    { vi: "Chọn game ở trên, vào trang chi tiết, sau đó chọn offline / online / bot phù hợp.", en: "Choose a game above, open its detail page, then pick the right mode: offline / online / bot." },
    { vi: "Đăng nhập để đồng bộ hồ sơ, lưu lịch sử đấu và thao tác với bạn bè nhanh hơn trên thanh bên trái.", en: "Log in to sync profile, save match history, and interact with friends faster via the left sidebar." },
    { vi: "Đăng bài viết mới", en: "Create new post" },
    { vi: "Cập nhật nhanh cho cộng đồng", en: "Quick update for the community" },
    { vi: "Bản tin", en: "Feed" },
    { vi: "Bạn đang nghĩ gì?", en: "What's on your mind?" },
    { vi: "Tác giả: Cần đăng nhập", en: "Author: Login required" },
    { vi: "Cần đăng nhập", en: "Login required" },
    { vi: "Đăng bài", en: "Post" },
    { vi: "bài viết", en: "posts" },
    { vi: "Chưa có bài đăng nào.", en: "No posts yet." },
    { vi: "Bình luận", en: "Comments" },
    { vi: "Chưa có bình luận", en: "No comments yet" },
    { vi: "Viết bình luận...", en: "Write a comment..." },
    { vi: "Gửi", en: "Send" },
    { vi: "Tắt Nhạc", en: "Mute Music" },
    { vi: "Bật Nhạc", en: "Unmute Music" },

    { vi: "Đăng ký tài khoản", en: "Create account" },
    { vi: "Mật khẩu", en: "Password" },
    { vi: "Tên hiển thị", en: "Display name" },
    { vi: "Gửi mã xác thực", en: "Send verification code" },
    { vi: "Đã có mã? Xác thực email", en: "Already have a code? Verify email" },
    { vi: "Xác thực Email", en: "Verify Email" },
    { vi: "Mã xác thực", en: "Verification code" },
    { vi: "Gửi lại mã", en: "Resend code" },
    { vi: "Đăng nhập để mở tính năng đầy đủ", en: "Log in to unlock full features" },
    { vi: "Đồng bộ profile, lịch sử đấu, bảng xếp hạng và hệ thống bạn bè/chat riêng.", en: "Sync profile, match history, leaderboard, and the friends/private-chat system." },
    { vi: "Quên mật khẩu?", en: "Forgot password?" },
    { vi: "Đặt lại mật khẩu", en: "Reset password" },
    { vi: "Bước 1: Gửi mã reset", en: "Step 1: Send reset code" },
    { vi: "Bước 2: Xác thực mã reset + Đặt lại mật khẩu (dùng email ở trên)", en: "Step 2: Verify reset code + set a new password (using the email above)" },
    { vi: "Gửi mã", en: "Send code" },
    { vi: "Chưa xác thực mã", en: "Code not verified" },
    { vi: "Mật khẩu mới", en: "New password" },
    { vi: "Xác nhận mật khẩu", en: "Confirm password" },
    { vi: "Khôi phục truy cập an toàn", en: "Secure access recovery" },
    { vi: "Quy trình 2 bước giúp xác minh email trước khi cho phép đổi mật khẩu mới.", en: "A two-step flow verifies email before allowing password reset." },

    { vi: "Tạo phòng", en: "Create room" },
    { vi: "Tham gia", en: "Join" },
    { vi: "Hiện tại không có phòng nào.", en: "There are no rooms currently." },
    { vi: "Sảnh chơi online", en: "Online lobby" },
    { vi: "Cập nhật realtime", en: "Realtime updates" },
    { vi: "Ván mới", en: "New match" },
    { vi: "Đầu hàng", en: "Surrender" },
    { vi: "Rời phòng", en: "Leave room" },
    { vi: "Về Online Hub", en: "Back to Online Hub" },
    { vi: "Đang khởi tạo...", en: "Initializing..." },
    { vi: "Đang chờ kết nối...", en: "Waiting for connection..." },
    { vi: "Đang chờ vào phòng...", en: "Waiting to enter room..." },
    { vi: "Đang chờ đủ người...", en: "Waiting for enough players..." },
    { vi: "Thông tin ván đấu", en: "Match information" },
    { vi: "Nhật ký nước đi", en: "Move log" },
    { vi: "Trạng thái", en: "Status" },
    { vi: "Nước đi", en: "Moves" },
    { vi: "Lượt hiện tại", en: "Current turn" },
    { vi: "Đang chơi", en: "In progress" },
    { vi: "Phòng", en: "Room" },
    { vi: "Bạn", en: "You" },
    { vi: "Màu quân của bạn", en: "Your side" },
    { vi: "Chưa chọn", en: "Not selected" },
    { vi: "Chưa vào phòng", en: "Not in room" },

    { vi: "Cập nhật thông tin", en: "Update profile" },
    { vi: "Đổi mật khẩu", en: "Change password" },
    { vi: "Lưu thay đổi", en: "Save changes" },
    { vi: "Về Home", en: "Back to Home" },
    { vi: "Chưa có.", en: "None yet." },
    { vi: "Điểm Achievements", en: "Achievement score" },
    { vi: "Achievement Lặp Lại", en: "Repeat achievements" },
    { vi: "Achievement Đặc Biệt", en: "Special achievements" },

    { vi: "Đăng nhập để xem danh sách bạn bè.", en: "Log in to view your friends list." },
    { vi: "Chưa có bạn bè nào.", en: "No friends yet." },
    { vi: "Không tải được danh sách bạn bè.", en: "Unable to load friends list." },
    { vi: "Xử lý thành công", en: "Success" },
    { vi: "Thao tác thành công", en: "Action completed successfully" },
    { vi: "Thao tác thất bại", en: "Action failed" },
    { vi: "Lỗi", en: "Error" },
    { vi: "Thông báo", en: "Notification" },
    { vi: "Lỗi mạng", en: "Network error" },

    { vi: "Email là bắt buộc", en: "Email is required" },
    { vi: "Mật khẩu là bắt buộc", en: "Password is required" },
    { vi: "Tên hiển thị là bắt buộc", en: "Display name is required" },
    { vi: "Email đã tồn tại", en: "Email already exists" },
    { vi: "Mã xác thực đã được gửi", en: "Verification code sent" },
    { vi: "Nếu email tồn tại, mã reset đã được gửi", en: "If the email exists, a reset code has been sent" },
    { vi: "Không thể gửi mã xác thực lúc này. Vui lòng thử lại.", en: "Cannot send verification email right now. Please try again." },
    { vi: "Không thể gửi mã reset lúc này. Vui lòng thử lại.", en: "Cannot send reset code right now. Please try again." },
    { vi: "Mã không hợp lệ hoặc đã hết hạn", en: "Invalid or expired code" },
    { vi: "Xác thực email thành công", en: "Email verification successful" },
    { vi: "Đăng nhập thành công", en: "Login successful" },
    { vi: "Đăng ký thất bại", en: "Registration failed" },
    { vi: "Đăng nhập thất bại", en: "Login failed" },
    { vi: "Chờ", en: "Wait" }
  ];

  const VI_WORD_FIX = {
    "dang": "đang",
    "nhap": "nhập",
    "ky": "ký",
    "khong": "không",
    "choi": "chơi",
    "thong": "thông",
    "bao": "báo",
    "lich": "lịch",
    "su": "sử",
    "dau": "đấu",
    "vien": "viện",
    "ban": "bạn",
    "be": "bè",
    "tim": "tìm",
    "gui": "gửi",
    "lai": "lại",
    "ma": "mã",
    "xac": "xác",
    "thuc": "thực",
    "mat": "mật",
    "khau": "khẩu",
    "ten": "tên",
    "hien": "hiện",
    "thi": "thị",
    "nguoi": "người",
    "phong": "phòng",
    "roi": "rời",
    "ve": "về",
    "moi": "mới",
    "tao": "tạo",
    "ket": "kết",
    "noi": "nối",
    "van": "ván",
    "sanh": "sảnh",
    "cap": "cập",
    "nhat": "nhật",
    "dinh": "định"
  };

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

  function isUpper(value) {
    return value === value.toUpperCase() && value !== value.toLowerCase();
  }

  function isCapitalized(value) {
    if (!value) return false;
    return value.charAt(0) === value.charAt(0).toUpperCase() && value.slice(1) === value.slice(1).toLowerCase();
  }

  function applyCase(source, replacement) {
    if (!source || !replacement) return replacement;
    if (isUpper(source)) return replacement.toUpperCase();
    if (isCapitalized(source)) return replacement.charAt(0).toUpperCase() + replacement.slice(1);
    return replacement;
  }

  function buildPhraseReplacements() {
    const list = [];
    PHRASE_PAIRS.forEach((pair) => {
      const variants = new Set([
        pair.vi,
        pair.en,
        toAscii(pair.vi),
        toAscii(pair.en)
      ]);
      variants.forEach((source) => {
        const text = String(source || "").trim();
        if (!text) return;
        list.push({
          source: text,
          regex: new RegExp(escapeRegExp(text), "gi"),
          vi: pair.vi,
          en: pair.en
        });
      });
    });
    return list.sort((a, b) => b.source.length - a.source.length);
  }

  const PHRASE_REPLACEMENTS = buildPhraseReplacements();

  function normalizeLanguage(language) {
    const value = String(language || "").trim().toLowerCase();
    return SUPPORTED_LANGS.includes(value) ? value : "vi";
  }

  function getStoredLanguage() {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (!stored) return null;
    return normalizeLanguage(stored);
  }

  function saveLanguage(language) {
    localStorage.setItem(STORAGE_KEY, language);
  }

  function applyPhraseReplacement(text, language) {
    let output = String(text || "");
    PHRASE_REPLACEMENTS.forEach((entry) => {
      output = output.replace(entry.regex, entry[language]);
    });
    return output;
  }

  function applyVietnameseWordFix(text) {
    return String(text || "").replace(/\b[\p{L}\p{M}]+\b/gu, (token) => {
      const key = toAscii(token).toLowerCase();
      const replacement = VI_WORD_FIX[key];
      if (!replacement) return token;
      return applyCase(token, replacement);
    });
  }

  function translateText(rawText, language) {
    const text = String(rawText || "");
    if (!text.trim()) return text;

    const lang = normalizeLanguage(language || currentLang);
    let output = applyPhraseReplacement(text, lang);
    if (lang === "vi") {
      output = output.replace(
        /Please wait\s+(\d+)\s+seconds before requesting a new code/gi,
        "Vui lòng chờ $1 giây trước khi yêu cầu mã mới"
      );
    } else {
      output = output.replace(
        /Vui lòng chờ\s+(\d+)\s+giây trước khi yêu cầu mã mới/gi,
        "Please wait $1 seconds before requesting a new code"
      );
    }
    if (lang === "vi") {
      output = applyVietnameseWordFix(output);
    }
    return output;
  }

  function shouldSkipElement(element) {
    if (!element || element.nodeType !== Node.ELEMENT_NODE) return true;
    const tag = element.tagName;
    if (["SCRIPT", "STYLE", "NOSCRIPT", "TEXTAREA", "CODE", "PRE", "IFRAME"].includes(tag)) {
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
    if (currentValue !== translated) {
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
    if (currentValue !== translated) {
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
    if (currentValue !== translated) {
      element.value = translated;
    }
    valueState.rendered = translated;
  }

  function translateRoot(root) {
    if (!root) return;

    const start = root.nodeType === Node.ELEMENT_NODE ? root : document.body;
    if (!start) return;
    if (start.nodeType === Node.ELEMENT_NODE && shouldSkipElement(start)) return;

    if (start.nodeType === Node.ELEMENT_NODE) {
      TEXT_ATTRS.forEach((attr) => updateElementAttribute(start, attr));
      updateInputValue(start);
    }

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

  function translateDocumentTitle() {
    const title = String(document.title || "");
    if (!sourceTitle) {
      sourceTitle = title;
      renderedTitle = title;
    } else if (title !== renderedTitle && title !== sourceTitle) {
      sourceTitle = title;
    }
    const translated = translateText(sourceTitle, currentLang);
    if (title !== translated) {
      document.title = translated;
    }
    renderedTitle = translated;
  }

  function setHtmlLanguage(language) {
    document.documentElement.setAttribute("lang", language === "en" ? "en" : "vi");
  }

  function applyAll() {
    if (!document.body) return;
    applying = true;
    try {
      translateRoot(document.body);
      translateDocumentTitle();
      setHtmlLanguage(currentLang);
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
      if (applying) return;
      scheduleApplyAll();
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
      try {
        listener(currentLang);
      } catch (_) {
        // no-op
      }
    });
  }

  function setLanguage(language) {
    const next = normalizeLanguage(language);
    if (next === currentLang) return;
    currentLang = next;
    saveLanguage(currentLang);
    applyAll();
    notifyChange();
  }

  function getLanguage() {
    return currentLang;
  }

  function onChange(listener) {
    if (typeof listener !== "function") {
      return function () { return undefined; };
    }
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

    const stored = getStoredLanguage();
    currentLang = stored || "vi";
    saveLanguage(currentLang);

    applyAll();
    startObserver();
    notifyChange();
  }

  window.CaroI18n = {
    init: init,
    setLanguage: setLanguage,
    getLanguage: getLanguage,
    onChange: onChange,
    apply: applyAll,
    t: function (text) {
      return translateText(text, currentLang);
    },
    supportedLanguages: SUPPORTED_LANGS.slice()
  };
})();
