(function () {
  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === 'function')
    ? window.CaroUrl.path
    : function (value) { return value; };

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

    const current = window.CaroUser.get();
    if (!current || !current.userId) {
      container.innerHTML = '<div class="small text-muted">Dang nhap de xem danh sach ban be.</div>';
      return;
    }

    try {
      const res = await fetch('/friendship/friend-list?currentUserId=' + encodeURIComponent(current.userId), {
        cache: 'no-store'
      });
      if (!res.ok) {
        throw new Error('Cannot fetch friend list');
      }
      const friends = await res.json();
      if (!Array.isArray(friends) || friends.length === 0) {
        container.innerHTML = '<div class="small text-muted">Chua co ban be nao.</div>';
        return;
      }

      const items = friends.map((f) => {
        const name = f.displayName || f.email || f.id;
        const avatar = appPath(f.avatarPath || '/uploads/avatars/default-avatar.jpg');
        const online = f.online ? '🟢' : '⚪';
        return '' +
          '<a class="d-flex align-items-center text-decoration-none theme-text border rounded p-2 mb-2" href="' + appPath('/friendship/user-detail/' + encodeURIComponent(f.id)) + '?currentUserId=' + encodeURIComponent(current.userId) + '">' +
            '<img src="' + avatar + '" alt="avatar" width="28" height="28" class="rounded-circle me-2">' +
            '<span class="small flex-grow-1">' + name + '</span>' +
            '<span class="small">' + online + '</span>' +
          '</a>';
      }).join('');

      container.innerHTML = items;
    } catch (_) {
      container.innerHTML = '<div class="small text-danger">Khong tai duoc danh sach ban be.</div>';
    }
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
    loadFriendList();
    window.setInterval(loadFriendList, 10000);
  });
})();
