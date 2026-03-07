(function () {
  const FRIEND_LIST_REFRESH_MS = 5000;
  const FRIEND_LIST_REFRESH_KEY = 'caroFriendListRefreshMs.v1';
  const FRIEND_LIST_AUTO_REFRESH_KEY = 'caroFriendListAutoRefresh.v1';
  const FRIEND_LIST_SHOW_OFFLINE_KEY = 'caroFriendListShowOffline.v1';
  const PREFERENCES_CHANGED_EVENT = 'caro:preferences-changed';
  const USER_CHANGE_EVENT = 'caro:user-changed';
  const FRIEND_LIST_ALLOWED_REFRESH_VALUES = [5000, 10000, 15000, 20000, 30000, 60000];
  let friendListPollTimerId = null;
  let friendListLoading = false;
  let friendListLifecycleBound = false;

  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === 'function')
    ? window.CaroUrl.path
    : function (value) { return value; };
  const t = (text) => {
    try {
      return (window.CaroI18n && typeof window.CaroI18n.t === 'function')
        ? window.CaroI18n.t(text)
        : text;
    } catch (_) {
      return text;
    }
  };

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/\"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function appendCurrentUserId(url, userId, paramName) {
    try {
      const parsed = new URL(url, window.location.origin);
      parsed.searchParams.set(paramName || 'currentUserId', userId);
      return appPath(parsed.pathname) + parsed.search + parsed.hash;
    } catch (_) {
      return url;
    }
  }

  function readStorage(key, fallbackValue) {
    try {
      const value = window.localStorage.getItem(key);
      return value == null ? fallbackValue : value;
    } catch (_) {
      return fallbackValue;
    }
  }

  function readBooleanPref(key, fallbackValue) {
    const raw = readStorage(key, null);
    if (raw == null) {
      return fallbackValue;
    }
    return raw === '1' || raw === 'true';
  }

  function readFriendListRefreshMs() {
    const raw = Number.parseInt(String(readStorage(FRIEND_LIST_REFRESH_KEY, FRIEND_LIST_REFRESH_MS)), 10);
    if (Number.isFinite(raw) && FRIEND_LIST_ALLOWED_REFRESH_VALUES.includes(raw)) {
      return raw;
    }
    return FRIEND_LIST_REFRESH_MS;
  }

  async function loadFriendList() {
    const container = document.getElementById('friendListContainer');
    if (!container || !window.CaroUser) {
      return;
    }
    if (friendListLoading) {
      return;
    }

    const current = window.CaroUser.get();
    if (!current || !current.userId) {
      container.innerHTML = '<div class="small text-muted">' + escapeHtml(t('Chưa đăng nhập')) + '</div>';
      return;
    }

    friendListLoading = true;
    try {
      const res = await fetch(appPath('/friendship/friend-list?currentUserId=' + encodeURIComponent(current.userId)), {
        cache: 'no-store'
      });
      if (!res.ok) {
        throw new Error('Cannot fetch friend list');
      }
      const friends = await res.json();
      if (!Array.isArray(friends) || friends.length === 0) {
        container.innerHTML = '<div class="small text-muted">' + escapeHtml(t('Chưa có bạn bè nào.')) + '</div>';
        return;
      }

      const showOfflineFriends = readBooleanPref(FRIEND_LIST_SHOW_OFFLINE_KEY, true);
      const visibleFriends = friends
        .filter((f) => showOfflineFriends ? true : !!f.online)
        .sort((a, b) => {
          const onlineDiff = Number(!!b.online) - Number(!!a.online);
          if (onlineDiff !== 0) {
            return onlineDiff;
          }
          const nameA = String(a.displayName || a.email || a.id || '').toLowerCase();
          const nameB = String(b.displayName || b.email || b.id || '').toLowerCase();
          return nameA.localeCompare(nameB);
        });

      if (visibleFriends.length === 0) {
        container.innerHTML = showOfflineFriends
          ? '<div class="small text-muted">' + escapeHtml(t('Chưa có bạn bè nào.')) + '</div>'
          : '<div class="small text-muted">' + escapeHtml(t('Hiện chưa có bạn nào đang online.')) + '</div>';
        return;
      }

      const items = visibleFriends.map((f) => {
        const name = f.displayName || f.email || f.id;
        const avatar = appPath(f.avatarPath || '/uploads/avatars/default-avatar.jpg');
        const detailHref = appPath('/friendship/user-detail/' + encodeURIComponent(f.id)) + '?currentUserId=' + encodeURIComponent(current.userId);
        const online = !!f.online;
        const stateLabel = online ? t('Trực tuyến') : t('Ngoại tuyến');
        const stateClass = online ? 'app-shell-friend-state--online' : 'app-shell-friend-state--offline';

        return '' +
          '<a class="app-shell-friend-item text-decoration-none theme-text border rounded" href="' + escapeHtml(detailHref) + '">' +
            '<img src="' + escapeHtml(avatar) + '" alt="' + escapeHtml(t('Ảnh đại diện')) + '" class="app-shell-friend-avatar">' +
            '<span class="app-shell-friend-name">' + escapeHtml(name) + '</span>' +
            '<span class="app-shell-friend-state ' + stateClass + '">' + stateLabel + '</span>' +
          '</a>';
      }).join('');

      container.innerHTML = items;
    } catch (_) {
      container.innerHTML = '<div class="small text-danger">' + escapeHtml(t('Không tải được danh sách bạn bè.')) + '</div>';
    } finally {
      friendListLoading = false;
    }
  }

  function startFriendListPolling() {
    if (friendListPollTimerId) {
      window.clearInterval(friendListPollTimerId);
      friendListPollTimerId = null;
    }

    const current = window.CaroUser?.get?.();
    if (!current || !current.userId) {
      void loadFriendList();
      return;
    }

    void loadFriendList();

    const autoRefresh = readBooleanPref(FRIEND_LIST_AUTO_REFRESH_KEY, true);
    if (autoRefresh) {
      friendListPollTimerId = window.setInterval(() => {
        void loadFriendList();
      }, readFriendListRefreshMs());
    }

    if (!friendListLifecycleBound) {
      document.addEventListener('visibilitychange', () => {
        if (!document.hidden) {
          void loadFriendList();
        }
      });
      window.addEventListener('focus', () => {
        void loadFriendList();
      });
      window.addEventListener('online', () => {
        void loadFriendList();
      });
      friendListLifecycleBound = true;
    }
  }

  function bindPreferencesSync() {
    const refreshFromPreferences = () => {
      startFriendListPolling();
      void loadFriendList();
    };

    window.addEventListener(PREFERENCES_CHANGED_EVENT, () => {
      refreshFromPreferences();
    });
    window.addEventListener(USER_CHANGE_EVENT, () => {
      bindCurrentUserLinks();
      refreshFromPreferences();
    });

    window.addEventListener('storage', (event) => {
      const key = String(event?.key || '');
      if (!key) {
        return;
      }
      if (key === FRIEND_LIST_REFRESH_KEY || key === FRIEND_LIST_AUTO_REFRESH_KEY || key === FRIEND_LIST_SHOW_OFFLINE_KEY) {
        refreshFromPreferences();
      }
    });
  }

  function bindCurrentUserLinks() {
    const current = window.CaroUser?.get?.();
    if (!current || !current.userId) {
      return;
    }

    document.querySelectorAll('[data-require-user-id]').forEach((el) => {
      const href = el.getAttribute('href');
      if (!href) {
        return;
      }
      const paramName = el.getAttribute('data-user-param') || 'currentUserId';
      el.setAttribute('href', appendCurrentUserId(href, current.userId, paramName));
    });
  }

  function bindFriendSearch() {
    const form = document.getElementById('friendSearchForm');
    const queryInput = document.getElementById('friendSearchInput');
    if (!form || !queryInput) {
      return;
    }

    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const q = (queryInput.value || '').trim();
      if (!q) {
        return;
      }

      const current = window.CaroUser?.get?.();
      const base = appPath('/friendship/search') + '?query=' + encodeURIComponent(q);
      if (current && current.userId) {
        window.location.href = base + '&currentUserId=' + encodeURIComponent(current.userId);
      } else {
        window.location.href = base;
      }
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    bindCurrentUserLinks();
    bindFriendSearch();
    bindPreferencesSync();
    startFriendListPolling();
  });
})();
