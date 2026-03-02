(function () {
  const FRIEND_LIST_REFRESH_MS = 5000;
  let friendListPollTimerId = null;
  let friendListLoading = false;

  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === 'function')
    ? window.CaroUrl.path
    : function (value) { return value; };

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
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
      container.innerHTML = '<div class="small text-muted">Đăng nhập để xem danh sách bạn bè.</div>';
      return;
    }

    friendListLoading = true;
    try {
      const res = await fetch('/friendship/friend-list?currentUserId=' + encodeURIComponent(current.userId), {
        cache: 'no-store'
      });
      if (!res.ok) {
        throw new Error('Cannot fetch friend list');
      }
      const friends = await res.json();
      if (!Array.isArray(friends) || friends.length === 0) {
        container.innerHTML = '<div class="small text-muted">Chưa có bạn bè nào.</div>';
        return;
      }

      const items = friends.map((f) => {
        const name = f.displayName || f.email || f.id;
        const avatar = appPath(f.avatarPath || '/uploads/avatars/default-avatar.jpg');
        const detailHref = appPath('/friendship/user-detail/' + encodeURIComponent(f.id)) + '?currentUserId=' + encodeURIComponent(current.userId);
        const online = !!f.online;
        const stateLabel = online ? 'Online' : 'Offline';
        const stateClass = online ? 'app-shell-friend-state--online' : 'app-shell-friend-state--offline';

        return '' +
          '<a class="app-shell-friend-item text-decoration-none theme-text border rounded" href="' + escapeHtml(detailHref) + '">' +
            '<img src="' + escapeHtml(avatar) + '" alt="avatar" class="app-shell-friend-avatar">' +
            '<span class="app-shell-friend-name">' + escapeHtml(name) + '</span>' +
            '<span class="app-shell-friend-state ' + stateClass + '">' + stateLabel + '</span>' +
          '</a>';
      }).join('');

      container.innerHTML = items;
    } catch (_) {
      container.innerHTML = '<div class="small text-danger">Không tải được danh sách bạn bè.</div>';
    } finally {
      friendListLoading = false;
    }
  }

  function startFriendListPolling() {
    if (friendListPollTimerId) {
      window.clearInterval(friendListPollTimerId);
    }

    void loadFriendList();
    friendListPollTimerId = window.setInterval(() => {
      void loadFriendList();
    }, FRIEND_LIST_REFRESH_MS);

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
    startFriendListPolling();
  });
})();
