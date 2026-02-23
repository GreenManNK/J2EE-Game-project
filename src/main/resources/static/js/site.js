(function () {
  function readAppContextPath() {
    const meta = document.querySelector('meta[name="app-context-path"]');
    let value = (meta && meta.getAttribute('content')) ? meta.getAttribute('content') : '';
    value = String(value || '').trim();
    if (!value || value === '/') {
      return '';
    }
    if (!value.startsWith('/')) {
      value = '/' + value;
    }
    if (value.length > 1 && value.endsWith('/')) {
      value = value.slice(0, -1);
    }
    return value;
  }

  const appContextPath = readAppContextPath();

  function toAppPath(urlLike) {
    const value = String(urlLike || '');
    if (!value || !appContextPath) {
      return value;
    }
    if (value.startsWith('//')) {
      return value;
    }
    if (/^[a-zA-Z][a-zA-Z\d+.-]*:/.test(value)) {
      return value;
    }
    if (!value.startsWith('/')) {
      return value;
    }
    if (value === appContextPath || value.startsWith(appContextPath + '/')) {
      return value;
    }
    return value === '/' ? (appContextPath + '/') : (appContextPath + value);
  }

  window.CaroUrl = {
    contextPath: appContextPath,
    path: toAppPath
  };

  const nativeFetch = window.fetch ? window.fetch.bind(window) : null;

  function getCsrfMeta() {
    const tokenEl = document.querySelector('meta[name="_csrf"]');
    const headerEl = document.querySelector('meta[name="_csrf_header"]');
    const token = tokenEl && tokenEl.getAttribute('content') ? tokenEl.getAttribute('content') : '';
    const headerName = headerEl && headerEl.getAttribute('content') ? headerEl.getAttribute('content') : 'X-CSRF-TOKEN';
    return token ? { token, headerName } : null;
  }

  function isSameOriginUrl(urlLike) {
    try {
      const url = new URL(String(urlLike || ''), window.location.origin);
      return url.origin === window.location.origin;
    } catch (_) {
      return false;
    }
  }

  function shouldAttachCsrf(method, urlLike) {
    const m = (method || 'GET').toUpperCase();
    if (m === 'GET' || m === 'HEAD' || m === 'OPTIONS' || m === 'TRACE') {
      return false;
    }
    return isSameOriginUrl(urlLike);
  }

  if (nativeFetch) {
    window.fetch = function (input, init) {
      const normalizedInput = typeof input === 'string' ? toAppPath(input) : input;
      const url = (typeof normalizedInput === 'string' || normalizedInput instanceof URL)
        ? String(normalizedInput)
        : ((input && input.url) || '');
      const method = (init && init.method) || (normalizedInput && normalizedInput.method) || 'GET';

      if (!shouldAttachCsrf(method, url)) {
        return nativeFetch(normalizedInput, init);
      }

      const csrf = getCsrfMeta();
      if (!csrf) {
        return nativeFetch(normalizedInput, init);
      }

      const headers = new Headers((init && init.headers) || (normalizedInput && normalizedInput.headers) || undefined);
      if (!headers.has(csrf.headerName)) {
        headers.set(csrf.headerName, csrf.token);
      }

      return nativeFetch(normalizedInput, Object.assign({}, init || {}, { headers: headers }));
    };
  }

  function parseJsonSafe(raw) {
    try {
      return JSON.parse(raw);
    } catch (_) {
      return null;
    }
  }

  const USER_KEY = 'caro_current_user';

  function getCurrentUser() {
    const stored = parseJsonSafe(localStorage.getItem(USER_KEY));
    if (stored && stored.userId) {
      return stored;
    }

    const legacyUserId = localStorage.getItem('userId');
    if (legacyUserId) {
      const fromLegacy = {
        userId: legacyUserId,
        displayName: localStorage.getItem('displayName') || 'Player',
        email: localStorage.getItem('email') || '',
        role: localStorage.getItem('role') || 'User',
        avatarPath: localStorage.getItem('avatarPath') || '/uploads/avatars/default-avatar.jpg'
      };
      setCurrentUser(fromLegacy);
      return fromLegacy;
    }

    return null;
  }

  function setCurrentUser(user) {
    if (!user || !user.userId) {
      return;
    }

    const normalized = {
      userId: user.userId,
      displayName: user.displayName || 'Player',
      email: user.email || '',
      role: user.role || 'User',
      avatarPath: user.avatarPath || '/uploads/avatars/default-avatar.jpg'
    };

    localStorage.setItem(USER_KEY, JSON.stringify(normalized));
    localStorage.setItem('userId', normalized.userId);
    localStorage.setItem('displayName', normalized.displayName);
    localStorage.setItem('email', normalized.email);
    localStorage.setItem('role', normalized.role);
    localStorage.setItem('avatarPath', normalized.avatarPath);
  }

  function clearCurrentUser() {
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem('userId');
    localStorage.removeItem('displayName');
    localStorage.removeItem('email');
    localStorage.removeItem('role');
    localStorage.removeItem('avatarPath');
  }

  window.CaroUser = {
    get: getCurrentUser,
    set: setCurrentUser,
    clear: clearCurrentUser
  };

  document.addEventListener('DOMContentLoaded', () => {
    const user = getCurrentUser();
    const badges = document.querySelectorAll('[data-current-user-badge]');
    const logoutBtn = document.getElementById('logoutBtn');

    badges.forEach((el) => {
      if (user) {
        el.textContent = user.displayName + ' (' + user.userId + ')';
      } else {
        el.textContent = 'Chua dang nhap';
      }
    });

    if (window.axios && window.axios.defaults && window.axios.defaults.headers) {
      const csrf = getCsrfMeta();
      if (csrf) {
        window.axios.defaults.headers.common = window.axios.defaults.headers.common || {};
        window.axios.defaults.headers.common[csrf.headerName] = csrf.token;
      }
    }

    if (logoutBtn) {
      logoutBtn.style.display = user ? '' : 'none';
      logoutBtn.addEventListener('click', async () => {
        const current = getCurrentUser();
        if (!current || !current.userId) {
          clearCurrentUser();
          window.location.href = window.CaroUrl.path('/');
          return;
        }
        try {
          await fetch('/account/logout', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ userId: current.userId })
          });
        } catch (_) {
        } finally {
          clearCurrentUser();
          window.location.href = window.CaroUrl.path('/');
        }
      });
    }
  });
})();
