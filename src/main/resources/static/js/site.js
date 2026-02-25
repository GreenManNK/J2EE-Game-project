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

  function ensureToastContainer() {
    let container = document.getElementById('caroToastContainer');
    if (container) {
      return container;
    }
    container = document.createElement('div');
    container.id = 'caroToastContainer';
    container.className = 'toast-container position-fixed top-0 end-0 p-3';
    container.style.zIndex = '1085';
    document.body.appendChild(container);
    return container;
  }

  function normalizeToastVariant(type) {
    const value = String(type || 'info').trim().toLowerCase();
    if (value === 'error') {
      return 'danger';
    }
    if (['primary', 'secondary', 'success', 'danger', 'warning', 'info', 'light', 'dark'].includes(value)) {
      return value;
    }
    return 'info';
  }

  function setStatusMessage(el, message, ok) {
    if (!el) {
      return;
    }
    const text = String(message || '').trim();
    el.textContent = text;
    el.classList.remove('text-danger', 'text-success', 'text-muted');
    if (!text) {
      return;
    }
    el.classList.add(ok === true ? 'text-success' : (ok === false ? 'text-danger' : 'text-muted'));
  }

  function showToast(message, options) {
    const opts = (typeof options === 'string') ? { type: options } : (options || {});
    const text = String(message || '').trim();
    if (!text) {
      return;
    }
    const variant = normalizeToastVariant(opts.type);
    const delay = Number.isFinite(Number(opts.delay)) ? Number(opts.delay) : 2800;
    const container = ensureToastContainer();

    const toastEl = document.createElement('div');
    toastEl.className = 'toast border-0';
    toastEl.setAttribute('role', 'status');
    toastEl.setAttribute('aria-live', 'polite');
    toastEl.setAttribute('aria-atomic', 'true');

    const body = document.createElement('div');
    body.className = 'toast-header';
    if (variant === 'danger') {
      body.classList.add('text-bg-danger');
    } else if (variant === 'success') {
      body.classList.add('text-bg-success');
    } else if (variant === 'warning') {
      body.classList.add('text-bg-warning');
    } else if (variant === 'info') {
      body.classList.add('text-bg-info');
    }

    const title = document.createElement('strong');
    title.className = 'me-auto';
    title.textContent = opts.title || (variant === 'danger' ? 'Loi' : 'Thong bao');
    body.appendChild(title);

    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'btn-close' + (
      variant === 'danger' || variant === 'success' ? ' btn-close-white' : ''
    );
    closeBtn.setAttribute('data-bs-dismiss', 'toast');
    closeBtn.setAttribute('aria-label', 'Close');
    body.appendChild(closeBtn);
    toastEl.appendChild(body);

    const content = document.createElement('div');
    content.className = 'toast-body';
    content.textContent = text;
    toastEl.appendChild(content);

    container.appendChild(toastEl);

    if (window.bootstrap && window.bootstrap.Toast) {
      const instance = window.bootstrap.Toast.getOrCreateInstance(toastEl, { delay, autohide: true });
      toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove(), { once: true });
      instance.show();
      return;
    }

    setTimeout(() => toastEl.remove(), delay);
  }

  function reportApiResult(data, options) {
    const opts = options || {};
    const success = !!(data && data.success);
    const successMessage = opts.successMessage || data?.message || 'Thao tac thanh cong';
    const errorMessage = opts.errorMessage || data?.error || data?.message || 'Thao tac that bai';
    const message = success ? successMessage : errorMessage;
    showToast(message, {
      type: success ? (opts.successType || 'success') : (opts.errorType || 'danger'),
      title: opts.title
    });
    if (opts.statusEl) {
      setStatusMessage(opts.statusEl, message, success);
    }
    return success;
  }

  window.CaroUi = {
    toast: showToast,
    apiResult: reportApiResult,
    setStatus: setStatusMessage
  };

  document.addEventListener('DOMContentLoaded', () => {
    const user = getCurrentUser();
    const badges = document.querySelectorAll('[data-current-user-badge]');
    const logoutBtn = document.getElementById('logoutBtn');
    const authOnly = document.querySelectorAll('[data-auth-only]');
    const guestOnly = document.querySelectorAll('[data-guest-only]');
    const roleOnly = document.querySelectorAll('[data-role-allowed]');

    badges.forEach((el) => {
      if (user) {
        el.textContent = user.displayName + ' (' + user.userId + ')';
      } else {
        el.textContent = 'Chua dang nhap';
      }
    });

    authOnly.forEach((el) => {
      el.style.display = user ? '' : 'none';
    });
    guestOnly.forEach((el) => {
      el.style.display = user ? 'none' : '';
    });
    roleOnly.forEach((el) => {
      if (!user || !user.role) {
        el.style.display = 'none';
        return;
      }
      const allowed = String(el.getAttribute('data-role-allowed') || '')
        .split(',')
        .map((v) => v.trim().toLowerCase())
        .filter(Boolean);
      if (allowed.length === 0) {
        return;
      }
      el.style.display = allowed.includes(String(user.role).trim().toLowerCase()) ? '' : 'none';
    });

    if (window.axios && window.axios.defaults && window.axios.defaults.headers) {
      const csrf = getCsrfMeta();
      if (csrf) {
        window.axios.defaults.headers.common = window.axios.defaults.headers.common || {};
        window.axios.defaults.headers.common[csrf.headerName] = csrf.token;
      }
    }

    if (logoutBtn) {
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
