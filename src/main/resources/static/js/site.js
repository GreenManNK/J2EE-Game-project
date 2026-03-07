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

  const THEME_MODE_KEY = 'caroThemeMode.v1';
  const LEGACY_THEME_KEY = 'theme';
  const THEME_CHANGE_EVENT = 'caro:theme-changed';
  const prefersDarkMedia = (typeof window.matchMedia === 'function')
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null;
  const themeListeners = new Set();
  const THEME_MODES = ['system', 'light', 'dark'];
  let currentThemeMode = 'light';
  let currentTheme = 'light';

  function normalizeThemeMode(mode) {
    const value = String(mode || '').trim().toLowerCase();
    if (value === 'dark' || value === 'light' || value === 'system') {
      return value;
    }
    return 'light';
  }

  function resolveEffectiveTheme(mode) {
    if (normalizeThemeMode(mode) === 'dark') {
      return 'dark';
    }
    if (normalizeThemeMode(mode) === 'system') {
      return (prefersDarkMedia && prefersDarkMedia.matches) ? 'dark' : 'light';
    }
    return 'light';
  }

  function readThemeModeFromStorage() {
    try {
      const mode = normalizeThemeMode(window.localStorage.getItem(THEME_MODE_KEY));
      if (mode !== 'light' || String(window.localStorage.getItem(THEME_MODE_KEY) || '').trim().toLowerCase() === 'light') {
        return mode;
      }
    } catch (_) {
    }

    try {
      const legacy = normalizeThemeMode(window.localStorage.getItem(LEGACY_THEME_KEY));
      if (legacy === 'dark' || legacy === 'light') {
        return legacy;
      }
    } catch (_) {
    }
    return 'light';
  }

  function persistTheme(mode, effectiveTheme) {
    try {
      window.localStorage.setItem(THEME_MODE_KEY, normalizeThemeMode(mode));
      window.localStorage.setItem(LEGACY_THEME_KEY, effectiveTheme === 'dark' ? 'dark' : 'light');
    } catch (_) {
    }
  }

  function ensureThemeMetaTag() {
    let meta = document.querySelector('meta[name="theme-color"]');
    if (meta) {
      return meta;
    }
    try {
      meta = document.createElement('meta');
      meta.setAttribute('name', 'theme-color');
      const head = document.head || document.querySelector('head');
      head && head.appendChild(meta);
      return meta;
    } catch (_) {
      return null;
    }
  }

  function applyThemeToDom(effectiveTheme, mode) {
    const normalized = effectiveTheme === 'dark' ? 'dark' : 'light';
    const normalizedMode = normalizeThemeMode(mode);
    const root = document.documentElement;
    if (root) {
      root.setAttribute('data-theme', normalized);
      root.setAttribute('data-theme-mode', normalizedMode);
      root.classList.toggle('dark-mode', normalized === 'dark');
      root.classList.toggle('light-mode', normalized === 'light');
    }

    const body = document.body;
    if (body) {
      body.classList.toggle('dark-mode', normalized === 'dark');
      body.classList.toggle('light-mode', normalized === 'light');
      body.dataset.theme = normalized;
      body.dataset.themeMode = normalizedMode;
    }

    const themeMeta = ensureThemeMetaTag();
    if (themeMeta) {
      themeMeta.setAttribute('content', normalized === 'dark' ? '#0b1220' : '#dcecff');
    }
  }

  function emitThemeChange(source) {
    const detail = {
      mode: currentThemeMode,
      theme: currentTheme,
      source: String(source || 'unknown')
    };
    try {
      window.dispatchEvent(new CustomEvent(THEME_CHANGE_EVENT, { detail }));
    } catch (_) {
    }
    try {
      document.dispatchEvent(new CustomEvent(THEME_CHANGE_EVENT, { detail }));
    } catch (_) {
    }
    themeListeners.forEach((listener) => {
      try {
        listener(detail);
      } catch (_) {
      }
    });
  }

  function setThemeMode(mode, options) {
    const opts = options || {};
    const normalizedMode = normalizeThemeMode(mode);
    const effectiveTheme = resolveEffectiveTheme(normalizedMode);

    currentThemeMode = normalizedMode;
    currentTheme = effectiveTheme;
    applyThemeToDom(effectiveTheme, normalizedMode);

    if (opts.persist !== false) {
      persistTheme(normalizedMode, effectiveTheme);
    }
    if (opts.notify !== false) {
      emitThemeChange(opts.source || 'set');
    }
    return { mode: currentThemeMode, theme: currentTheme };
  }

  function toggleThemeMode() {
    const nextMode = getNextThemeMode(currentThemeMode);
    return setThemeMode(nextMode, { source: 'toggle', persist: true, notify: true });
  }

  function getNextThemeMode(mode) {
    const normalizedMode = normalizeThemeMode(mode);
    const index = THEME_MODES.indexOf(normalizedMode);
    if (index < 0) {
      return THEME_MODES[0];
    }
    return THEME_MODES[(index + 1) % THEME_MODES.length];
  }

  function onThemeChange(listener) {
    if (typeof listener !== 'function') {
      return function () {};
    }
    themeListeners.add(listener);
    return function () {
      themeListeners.delete(listener);
    };
  }

  function initThemeMode() {
    const initialMode = readThemeModeFromStorage();
    setThemeMode(initialMode, { source: 'init', persist: true, notify: false });
  }

  window.CaroTheme = {
    init: initThemeMode,
    getMode: function () { return currentThemeMode; },
    getTheme: function () { return currentTheme; },
    getModes: function () { return THEME_MODES.slice(); },
    nextMode: function (mode) { return getNextThemeMode(mode || currentThemeMode); },
    setMode: function (mode) { return setThemeMode(mode, { source: 'api', persist: true, notify: true }); },
    toggle: toggleThemeMode,
    onChange: onThemeChange
  };

  initThemeMode();

  if (prefersDarkMedia) {
    const onSystemThemeChange = function () {
      if (currentThemeMode === 'system') {
        setThemeMode('system', { source: 'system', persist: true, notify: true });
      }
    };
    if (typeof prefersDarkMedia.addEventListener === 'function') {
      prefersDarkMedia.addEventListener('change', onSystemThemeChange);
    } else if (typeof prefersDarkMedia.addListener === 'function') {
      prefersDarkMedia.addListener(onSystemThemeChange);
    }
  }

  window.addEventListener('storage', function (event) {
    if (!event || (event.key !== THEME_MODE_KEY && event.key !== LEGACY_THEME_KEY)) {
      return;
    }
    const storageMode = readThemeModeFromStorage();
    setThemeMode(storageMode, { source: 'storage', persist: false, notify: true });
  });

  document.addEventListener('DOMContentLoaded', function () {
    applyThemeToDom(currentTheme, currentThemeMode);
  });

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
  const TOAST_ENABLED_KEY = 'caroToastEnabled.v1';

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

  async function fetchAccountGameStats(gameCode) {
    const current = getCurrentUser();
    if (!current || !current.userId) {
      return null;
    }
    const normalizedGameCode = String(gameCode || '').trim();
    if (!normalizedGameCode) {
      return null;
    }
    try {
      const res = await fetch(
        toAppPath('/account/game-stats?gameCode=' + encodeURIComponent(normalizedGameCode)),
        { cache: 'no-store' }
      );
      const data = await res.json().catch(() => ({}));
      if (!data || data.success !== true || !data.data || typeof data.data.stats !== 'object') {
        return null;
      }
      return data.data.stats;
    } catch (_) {
      return null;
    }
  }

  async function saveAccountGameStats(gameCode, stats, merge) {
    const current = getCurrentUser();
    if (!current || !current.userId) {
      return false;
    }
    const normalizedGameCode = String(gameCode || '').trim();
    if (!normalizedGameCode || !stats || typeof stats !== 'object') {
      return false;
    }
    try {
      const res = await fetch(toAppPath('/account/game-stats'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          gameCode: normalizedGameCode,
          stats: stats,
          merge: merge !== false
        })
      });
      const data = await res.json().catch(() => ({}));
      return !!(data && data.success === true);
    } catch (_) {
      return false;
    }
  }

  window.CaroAccountStats = {
    get: fetchAccountGameStats,
    save: saveAccountGameStats
  };

  const GUEST_DATA_KEYS = [
    { gameCode: 'chess-offline', storageKey: 'caroChessOfflineStats.v1' },
    { gameCode: 'xiangqi-offline', storageKey: 'caroXiangqiOfflineStats.v1' },
    { gameCode: 'minesweeper', storageKey: 'caroMinesweeperStats.v1' }
  ];
  let guestDataMigrateInFlight = null;

  async function migrateGuestDataToAccount() {
    if (guestDataMigrateInFlight) {
      return guestDataMigrateInFlight;
    }
    const current = getCurrentUser();
    if (!current || !current.userId) {
      return { migratedCount: 0 };
    }

    guestDataMigrateInFlight = (async () => {
      let migratedCount = 0;
      for (const item of GUEST_DATA_KEYS) {
        const raw = window.localStorage.getItem(item.storageKey);
        if (!raw) {
          continue;
        }
        let parsed = null;
        try {
          parsed = JSON.parse(raw);
        } catch (_) {
          parsed = null;
        }
        if (!parsed || typeof parsed !== 'object') {
          continue;
        }
        const saved = await saveAccountGameStats(item.gameCode, parsed, true);
        if (saved) {
          migratedCount += 1;
          try {
            window.localStorage.removeItem(item.storageKey);
          } catch (_) {
          }
        }
      }
      return { migratedCount: migratedCount };
    })();

    try {
      return await guestDataMigrateInFlight;
    } finally {
      guestDataMigrateInFlight = null;
    }
  }

  window.CaroGuestData = {
    migrateToAccount: migrateGuestDataToAccount
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
    let toastEnabled = true;
    try {
      const raw = window.localStorage.getItem(TOAST_ENABLED_KEY);
      if (raw != null) {
        toastEnabled = raw === '1' || raw === 'true';
      }
    } catch (_) {
    }
    if (!toastEnabled && variant !== 'danger') {
      return;
    }
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

  function normalizeRootRelativeUrls() {
    document.querySelectorAll('a[href]').forEach((anchor) => {
      const rawHref = anchor.getAttribute('href');
      if (!rawHref || !rawHref.startsWith('/')) {
        return;
      }
      anchor.setAttribute('href', toAppPath(rawHref));
    });

    document.querySelectorAll('form[action]').forEach((form) => {
      const rawAction = form.getAttribute('action');
      if (!rawAction || !rawAction.startsWith('/')) {
        return;
      }
      form.setAttribute('action', toAppPath(rawAction));
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    normalizeRootRelativeUrls();

    const badges = document.querySelectorAll('[data-current-user-badge]');
    const logoutBtn = document.getElementById('logoutBtn');
    const authOnly = document.querySelectorAll('[data-auth-only]');
    const guestOnly = document.querySelectorAll('[data-guest-only]');
    const roleOnly = document.querySelectorAll('[data-role-allowed]');

    const applyAuthState = (user) => {
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
    };

    let user = getCurrentUser();
    applyAuthState(user);

    if (user && user.userId) {
      fetch(toAppPath('/account/preferences'), { cache: 'no-store' })
        .then((res) => res.json().catch(() => ({})))
        .then((data) => {
          if (!data || data.success !== true) {
            clearCurrentUser();
            user = null;
            applyAuthState(null);
            return;
          }
          window.CaroGuestData?.migrateToAccount?.();
        })
        .catch(() => {
          clearCurrentUser();
          user = null;
          applyAuthState(null);
        });
    }

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
