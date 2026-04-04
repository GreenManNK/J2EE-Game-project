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
      root.style.colorScheme = normalized;
      root.classList.toggle('dark-mode', normalized === 'dark');
      root.classList.toggle('light-mode', normalized === 'light');
    }

    const body = document.body;
    if (body) {
      body.style.colorScheme = normalized;
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

  const BOT_HISTORY_MATCHES = new Set();

  function newHistoryMatchCode(prefix) {
    const safePrefix = String(prefix || 'bot-match')
      .trim()
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '') || 'BOT-MATCH';
    let token = '';
    try {
      token = typeof window.crypto?.randomUUID === 'function'
        ? window.crypto.randomUUID().replace(/-/g, '')
        : '';
    } catch (_) {
      token = '';
    }
    if (!token) {
      token = Math.random().toString(36).slice(2, 10) + Date.now().toString(36);
    }
    return safePrefix + '-' + Date.now() + '-' + token.slice(0, 10).toUpperCase();
  }

  async function recordBotMatch(payload) {
    const body = payload && typeof payload === 'object' ? payload : {};
    const matchCode = String(body.matchCode || '').trim();
    const gameCode = String(body.gameCode || '').trim().toLowerCase();
    const dedupeKey = gameCode + '|' + matchCode;

    if (gameCode && matchCode && BOT_HISTORY_MATCHES.has(dedupeKey)) {
      return {
        success: true,
        data: {
          recorded: false,
          skipped: 'client-duplicate',
          matchCode: matchCode
        }
      };
    }

    const response = await fetch(toAppPath('/history/api/bot-match'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    const data = await response.json().catch(() => ({}));
    if (response.ok && data && data.success && gameCode && matchCode) {
      BOT_HISTORY_MATCHES.add(dedupeKey);
    }
    return data;
  }

  window.CaroHistory = {
    recordBotMatch: recordBotMatch,
    newMatchCode: newHistoryMatchCode
  };

  const USER_KEY = 'caro_current_user';
  const USER_CHANGE_EVENT = 'caro:user-changed';
  const DEFAULT_AVATAR_PATH = '/uploads/avatars/default-avatar.jpg';
  const TOAST_ENABLED_KEY = 'caroToastEnabled.v1';

  function emitCurrentUserChange(user, source) {
    const detail = {
      user: user && user.userId ? user : null,
      source: String(source || 'unknown')
    };
    try {
      window.dispatchEvent(new CustomEvent(USER_CHANGE_EVENT, { detail: detail }));
    } catch (_) {
    }
    try {
      document.dispatchEvent(new CustomEvent(USER_CHANGE_EVENT, { detail: detail }));
    } catch (_) {
    }
  }

  function readStoredCurrentUser() {
    return parseJsonSafe(localStorage.getItem(USER_KEY));
  }

  function normalizeNonNegativeInt(value, fallbackValue) {
    const fallback = Number.isFinite(Number(fallbackValue)) ? Math.max(0, Number.parseInt(String(fallbackValue), 10) || 0) : 0;
    const parsed = Number.parseInt(String(value == null ? '' : value), 10);
    return Number.isFinite(parsed) ? Math.max(0, parsed) : fallback;
  }

  function normalizeCurrentUser(user) {
    const normalizedUserId = String(user?.userId || '').trim();
    const stored = normalizedUserId ? readStoredCurrentUser() : null;
    const storedUserId = stored && stored.userId ? String(stored.userId).trim() : '';
    const legacyScore = normalizedUserId ? window.localStorage.getItem('score') : null;
    const fallbackScore = storedUserId && storedUserId === normalizedUserId
      ? normalizeNonNegativeInt(stored.score, normalizeNonNegativeInt(legacyScore, 0))
      : 0;
    const rawUsername = String(user?.username || user?.displayName || '').trim().replace(/^@+/, '');
    return {
      userId: normalizedUserId,
      username: rawUsername,
      displayName: String(user?.displayName || rawUsername || 'Nguoi choi').trim() || 'Nguoi choi',
      email: String(user?.email || '').trim(),
      role: String(user?.role || 'User').trim() || 'User',
      avatarPath: String(user?.avatarPath || DEFAULT_AVATAR_PATH).trim() || DEFAULT_AVATAR_PATH,
      country: String(user?.country || '').trim(),
      gender: String(user?.gender || '').trim(),
      birthDate: String(user?.birthDate || '').trim(),
      score: normalizeNonNegativeInt(user?.score, fallbackScore),
      onboardingCompleted: user?.onboardingCompleted === true
    };
  }

  function getCurrentUser() {
    const stored = parseJsonSafe(localStorage.getItem(USER_KEY));
    if (stored && stored.userId) {
      return normalizeCurrentUser(stored);
    }

    const legacyUserId = localStorage.getItem('userId');
    if (legacyUserId) {
      const fromLegacy = normalizeCurrentUser({
        userId: legacyUserId,
        username: localStorage.getItem('username') || localStorage.getItem('displayName') || '',
        displayName: localStorage.getItem('displayName') || 'Nguoi choi',
        email: localStorage.getItem('email') || '',
        role: localStorage.getItem('role') || 'User',
        avatarPath: localStorage.getItem('avatarPath') || DEFAULT_AVATAR_PATH,
        country: localStorage.getItem('country') || '',
        gender: localStorage.getItem('gender') || '',
        birthDate: localStorage.getItem('birthDate') || '',
        score: localStorage.getItem('score') || '0',
        onboardingCompleted: localStorage.getItem('onboardingCompleted') === 'true'
      });
      setCurrentUser(fromLegacy);
      return fromLegacy;
    }

    return null;
  }

  function setCurrentUser(user) {
    if (!user || !user.userId) {
      return;
    }

    const normalized = normalizeCurrentUser(user);

    localStorage.setItem(USER_KEY, JSON.stringify(normalized));
    localStorage.setItem('userId', normalized.userId);
    localStorage.setItem('username', normalized.username);
    localStorage.setItem('displayName', normalized.displayName);
    localStorage.setItem('email', normalized.email);
    localStorage.setItem('role', normalized.role);
    localStorage.setItem('avatarPath', normalized.avatarPath);
    localStorage.setItem('country', normalized.country);
    localStorage.setItem('gender', normalized.gender);
    localStorage.setItem('birthDate', normalized.birthDate);
    localStorage.setItem('score', String(normalized.score || 0));
    localStorage.setItem('onboardingCompleted', normalized.onboardingCompleted ? 'true' : 'false');
    emitCurrentUserChange(normalized, 'set');
  }

  function clearCurrentUser() {
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem('userId');
    localStorage.removeItem('username');
    localStorage.removeItem('displayName');
    localStorage.removeItem('email');
    localStorage.removeItem('role');
    localStorage.removeItem('avatarPath');
    localStorage.removeItem('country');
    localStorage.removeItem('gender');
    localStorage.removeItem('birthDate');
    localStorage.removeItem('score');
    localStorage.removeItem('onboardingCompleted');
    emitCurrentUserChange(null, 'clear');
  }

  function sameCurrentUser(left, right) {
    const a = left && left.userId ? normalizeCurrentUser(left) : null;
    const b = right && right.userId ? normalizeCurrentUser(right) : null;
    if (!a || !b) {
      return a === b;
    }
    return a.userId === b.userId
      && a.username === b.username
      && a.displayName === b.displayName
      && a.email === b.email
      && a.role === b.role
      && a.avatarPath === b.avatarPath
      && a.country === b.country
      && a.gender === b.gender
      && a.birthDate === b.birthDate
      && a.score === b.score
      && a.onboardingCompleted === b.onboardingCompleted;
  }

  async function refreshCurrentUserFromSession() {
    try {
      const res = await fetch(toAppPath('/account/session-user'), { cache: 'no-store' });
      const data = await res.json().catch(() => ({}));
      const sessionUser = data && data.success === true && data.data && data.data.userId
        ? normalizeCurrentUser(data.data)
        : null;
      const current = getCurrentUser();

      if (!sessionUser) {
        if (current && current.userId) {
          clearCurrentUser();
        }
        return null;
      }

      if (!sameCurrentUser(current, sessionUser)) {
        setCurrentUser(sessionUser);
      }
      return sessionUser;
    } catch (_) {
      return getCurrentUser();
    }
  }

  window.CaroUser = {
    get: getCurrentUser,
    set: setCurrentUser,
    clear: clearCurrentUser,
    refresh: refreshCurrentUserFromSession,
    eventName: USER_CHANGE_EVENT
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

  const MUSIC_KEY = 'music';
  const SIDEBAR_DESKTOP_HIDDEN_KEY = 'caroSidebarDesktopHidden.v2';
  const SIDEBAR_MOBILE_AUTO_CLOSE_KEY = 'caroSidebarMobileAutoClose.v1';
  const FRIEND_LIST_SHOW_OFFLINE_KEY = 'caroFriendListShowOffline.v1';
  const FRIEND_LIST_AUTO_REFRESH_KEY = 'caroFriendListAutoRefresh.v1';
  const FRIEND_LIST_REFRESH_KEY = 'caroFriendListRefreshMs.v1';
  const FRIEND_LIST_ALLOWED_REFRESH_VALUES = [5000, 10000, 15000, 20000, 30000, 60000];
  const LANGUAGE_KEY = 'caro_ui_lang';
  const GUEST_MIGRATION_PENDING_KEY = 'caroGuestMigrationPending.v1';
  const GAMES_BROWSER_FAVORITES_KEY = 'caroFavoriteGames.v1';
  const GAMES_BROWSER_RECENT_KEY = 'caroRecentlyPlayedGames.v1';
  const PUZZLE_FAVORITE_STORAGE_KEY = 'puzzleCatalogFavorites.v1';
  const PUZZLE_RATING_STORAGE_KEY = 'puzzleCatalogRatings.v1';
  const PUZZLE_RECENT_STORAGE_KEY = 'puzzleCatalogRecent.v1';
  const GUEST_DATA_KEYS = [
    { gameCode: 'chess-offline', storageKey: 'caroChessOfflineStats.v1' },
    { gameCode: 'xiangqi-offline', storageKey: 'caroXiangqiOfflineStats.v1' },
    { gameCode: 'minesweeper', storageKey: 'caroMinesweeperStats.v1' },
    { gameCode: 'quiz-practice', storageKey: 'caroQuizPracticeStats.v1' },
    { gameCode: 'typing-practice', storageKey: 'caroTypingPracticeStats.v1' }
  ];
  let guestDataMigrateInFlight = null;

  function readBooleanStorage(key, fallbackValue) {
    try {
      const raw = window.localStorage.getItem(key);
      if (raw == null) {
        return fallbackValue;
      }
      return raw === '1' || raw === 'true';
    } catch (_) {
      return fallbackValue;
    }
  }

  function normalizeLanguageValue(language) {
    return String(language || '').trim().toLowerCase() === 'en' ? 'en' : 'vi';
  }

  function readFriendListRefreshMs() {
    try {
      const raw = Number.parseInt(String(window.localStorage.getItem(FRIEND_LIST_REFRESH_KEY) || '5000'), 10);
      if (Number.isFinite(raw) && FRIEND_LIST_ALLOWED_REFRESH_VALUES.includes(raw)) {
        return raw;
      }
    } catch (_) {
    }
    return 5000;
  }

  function hasPendingGuestMigration() {
    try {
      return window.localStorage.getItem(GUEST_MIGRATION_PENDING_KEY) === '1';
    } catch (_) {
      return false;
    }
  }

  function markGuestMigrationPending() {
    try {
      window.localStorage.setItem(GUEST_MIGRATION_PENDING_KEY, '1');
    } catch (_) {
    }
  }

  function clearGuestMigrationPending() {
    try {
      window.localStorage.removeItem(GUEST_MIGRATION_PENDING_KEY);
    } catch (_) {
    }
  }

  function collectGuestPreferencesPayload() {
    if (!hasPendingGuestMigration()) {
      return null;
    }

    const themeMode = (window.CaroTheme && typeof window.CaroTheme.getMode === 'function')
      ? normalizeThemeMode(window.CaroTheme.getMode())
      : normalizeThemeMode(window.localStorage.getItem(THEME_MODE_KEY));
    const language = (window.CaroI18n && typeof window.CaroI18n.getLanguage === 'function')
      ? normalizeLanguageValue(window.CaroI18n.getLanguage())
      : normalizeLanguageValue(window.localStorage.getItem(LANGUAGE_KEY));

    return {
      themeMode: themeMode,
      language: language,
      sidebarDesktopVisibleByDefault: !readBooleanStorage(SIDEBAR_DESKTOP_HIDDEN_KEY, true),
      sidebarMobileAutoClose: readBooleanStorage(SIDEBAR_MOBILE_AUTO_CLOSE_KEY, true),
      homeMusicEnabled: String(window.localStorage.getItem(MUSIC_KEY) || 'on').trim().toLowerCase() !== 'off',
      toastNotificationsEnabled: readBooleanStorage(TOAST_ENABLED_KEY, true),
      showOfflineFriendsInSidebar: readBooleanStorage(FRIEND_LIST_SHOW_OFFLINE_KEY, true),
      autoRefreshFriendList: readBooleanStorage(FRIEND_LIST_AUTO_REFRESH_KEY, true),
      friendListRefreshMs: readFriendListRefreshMs()
    };
  }

  function collectGuestGameStatsPayload() {
    const payload = {};
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
      payload[item.gameCode] = parsed;
    }
    return payload;
  }

  function hasGuestStatsData() {
    return Object.keys(collectGuestGameStatsPayload()).length > 0;
  }

  function normalizeGamesBrowserCode(value) {
    const normalized = String(value || '').trim().toLowerCase();
    return normalized || '';
  }

  function normalizeGamesBrowserName(value, fallbackCode) {
    const normalized = String(value || '').trim();
    if (normalized) {
      return normalized;
    }
    const fallback = normalizeGamesBrowserCode(fallbackCode);
    return fallback || 'Game';
  }

  function collectGuestGamesBrowserStatePayload() {
    const favorites = parseJsonSafe(window.localStorage.getItem(GAMES_BROWSER_FAVORITES_KEY));
    const recentGames = parseJsonSafe(window.localStorage.getItem(GAMES_BROWSER_RECENT_KEY));

    const normalizedFavorites = Array.isArray(favorites)
      ? Array.from(new Set(favorites.map((item) => normalizeGamesBrowserCode(item)).filter(Boolean))).slice(0, 256)
      : [];
    const normalizedRecentGames = Array.isArray(recentGames)
      ? recentGames
        .map((item) => {
          const code = normalizeGamesBrowserCode(item && item.code);
          if (!code) {
            return null;
          }
          return {
            code: code,
            name: normalizeGamesBrowserName(item && item.name, code),
            at: Number.isFinite(Number(item && item.at)) ? Number(item.at) : Date.now()
          };
        })
        .filter((item) => Boolean(item))
        .slice(0, 20)
      : [];

    if (normalizedFavorites.length === 0 && normalizedRecentGames.length === 0) {
      return null;
    }

    return {
      favorites: normalizedFavorites,
      recentGames: normalizedRecentGames,
      merge: true
    };
  }

  function clearGuestGamesBrowserState() {
    try {
      window.localStorage.removeItem(GAMES_BROWSER_FAVORITES_KEY);
      window.localStorage.removeItem(GAMES_BROWSER_RECENT_KEY);
    } catch (_) {
    }
  }

  function hasGuestGamesBrowserState() {
    return !!collectGuestGamesBrowserStatePayload();
  }

  async function fetchGamesBrowserState() {
    const current = getCurrentUser();
    if (!current || !current.userId) {
      return null;
    }
    try {
      const res = await fetch(toAppPath('/account/games-browser-state'), { cache: 'no-store' });
      const data = await res.json().catch(() => ({}));
      if (!data || data.success !== true || !data.data || typeof data.data !== 'object') {
        return null;
      }
      return data.data;
    } catch (_) {
      return null;
    }
  }

  async function saveGamesBrowserState(state, merge, options) {
    const current = getCurrentUser();
    if (!current || !current.userId || !state || typeof state !== 'object') {
      return false;
    }
    const opts = options || {};
    try {
      const res = await fetch(toAppPath('/account/games-browser-state'), {
        method: 'POST',
        keepalive: opts.keepalive === true,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          favorites: Array.isArray(state.favorites) ? state.favorites : [],
          recentGames: Array.isArray(state.recentGames) ? state.recentGames : [],
          merge: merge !== false
        })
      });
      const data = await res.json().catch(() => ({}));
      return !!(data && data.success === true);
    } catch (_) {
      return false;
    }
  }

  window.CaroGamesBrowserState = {
    get: fetchGamesBrowserState,
    save: saveGamesBrowserState,
    guestKeys: {
      favorites: GAMES_BROWSER_FAVORITES_KEY,
      recentGames: GAMES_BROWSER_RECENT_KEY
    }
  };

  function collectGuestPuzzleCatalogStatePayload() {
    const favorites = parseJsonSafe(window.localStorage.getItem(PUZZLE_FAVORITE_STORAGE_KEY));
    const ratings = parseJsonSafe(window.localStorage.getItem(PUZZLE_RATING_STORAGE_KEY));
    const recentCodes = parseJsonSafe(window.localStorage.getItem(PUZZLE_RECENT_STORAGE_KEY));

    const hasFavorites = Array.isArray(favorites) && favorites.length > 0;
    const hasRatings = ratings && typeof ratings === 'object' && !Array.isArray(ratings) && Object.keys(ratings).length > 0;
    const hasRecentCodes = Array.isArray(recentCodes) && recentCodes.length > 0;

    if (!hasFavorites && !hasRatings && !hasRecentCodes) {
      return null;
    }

    return {
      favorites: hasFavorites ? favorites : [],
      ratings: hasRatings ? ratings : {},
      recentCodes: hasRecentCodes ? recentCodes : [],
      merge: true
    };
  }

  function clearGuestPuzzleCatalogState() {
    try {
      window.localStorage.removeItem(PUZZLE_FAVORITE_STORAGE_KEY);
      window.localStorage.removeItem(PUZZLE_RATING_STORAGE_KEY);
      window.localStorage.removeItem(PUZZLE_RECENT_STORAGE_KEY);
    } catch (_) {
    }
  }

  function hasGuestPuzzleCatalogState() {
    return !!collectGuestPuzzleCatalogStatePayload();
  }

  async function fetchPuzzleCatalogState() {
    const current = getCurrentUser();
    if (!current || !current.userId) {
      return null;
    }
    try {
      const res = await fetch(toAppPath('/account/puzzle-catalog-state'), { cache: 'no-store' });
      const data = await res.json().catch(() => ({}));
      if (!data || data.success !== true || !data.data || typeof data.data !== 'object') {
        return null;
      }
      return data.data;
    } catch (_) {
      return null;
    }
  }

  async function savePuzzleCatalogState(state, merge, options) {
    const current = getCurrentUser();
    if (!current || !current.userId || !state || typeof state !== 'object') {
      return false;
    }
    const opts = options || {};
    try {
      const res = await fetch(toAppPath('/account/puzzle-catalog-state'), {
        method: 'POST',
        keepalive: opts.keepalive === true,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          favorites: Array.isArray(state.favorites) ? state.favorites : [],
          ratings: state.ratings && typeof state.ratings === 'object' ? state.ratings : {},
          recentCodes: Array.isArray(state.recentCodes) ? state.recentCodes : [],
          merge: merge !== false
        })
      });
      const data = await res.json().catch(() => ({}));
      return !!(data && data.success === true);
    } catch (_) {
      return false;
    }
  }

  window.CaroPuzzleCatalog = {
    get: fetchPuzzleCatalogState,
    save: savePuzzleCatalogState,
    guestKeys: {
      favorites: PUZZLE_FAVORITE_STORAGE_KEY,
      ratings: PUZZLE_RATING_STORAGE_KEY,
      recentCodes: PUZZLE_RECENT_STORAGE_KEY
    }
  };

  async function migrateGuestDataToAccount() {
    if (guestDataMigrateInFlight) {
      return guestDataMigrateInFlight;
    }
    const current = getCurrentUser();
    if (!current || !current.userId) {
      return { migratedCount: 0, migratedPreferences: false };
    }

    guestDataMigrateInFlight = (async () => {
      const preferences = collectGuestPreferencesPayload();
      const gameStats = collectGuestGameStatsPayload();
      const gamesBrowserState = collectGuestGamesBrowserStatePayload();
      const puzzleCatalogState = collectGuestPuzzleCatalogStatePayload();
      if (!preferences && Object.keys(gameStats).length === 0 && !gamesBrowserState && !puzzleCatalogState) {
        clearGuestMigrationPending();
        return { migratedCount: 0, migratedPreferences: false };
      }

      try {
        const res = await fetch(toAppPath('/account/migrate-guest-data'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            preferences: preferences,
            gameStats: gameStats,
            gamesBrowserState: gamesBrowserState,
            puzzleCatalogState: puzzleCatalogState
          })
        });
        const data = await res.json().catch(() => ({}));
        if (!data || data.success !== true || !data.data) {
          return {
            migratedCount: 0,
            migratedPreferences: false,
            error: String(data?.error || 'Khong the chuyen du lieu khach')
          };
        }

        const migratedGameStats = Array.isArray(data.data.migratedGameStats) ? data.data.migratedGameStats : [];
        for (const item of GUEST_DATA_KEYS) {
          if (!migratedGameStats.includes(item.gameCode)) {
            continue;
          }
          try {
            window.localStorage.removeItem(item.storageKey);
          } catch (_) {
          }
        }

        if (data.data.migratedGamesBrowserState) {
          clearGuestGamesBrowserState();
        }

        if (data.data.migratedPuzzleCatalogState) {
          clearGuestPuzzleCatalogState();
        }

        if (data.data.migratedPreferences) {
          clearGuestMigrationPending();
        }

        return {
          migratedCount: Number(data.data.migratedGameStatsCount || migratedGameStats.length || 0),
          migratedPreferences: !!data.data.migratedPreferences,
          migratedGamesBrowserState: !!data.data.migratedGamesBrowserState,
          migratedPuzzleCatalogState: !!data.data.migratedPuzzleCatalogState
        };
      } catch (_) {
        return {
          migratedCount: 0,
          migratedPreferences: false,
          migratedGamesBrowserState: false,
          migratedPuzzleCatalogState: false,
          error: 'Khong the chuyen du lieu khach'
        };
      }
    })();

    try {
      return await guestDataMigrateInFlight;
    } finally {
      guestDataMigrateInFlight = null;
    }
  }

  window.CaroGuestData = {
    migrateToAccount: migrateGuestDataToAccount,
    markPendingMigration: markGuestMigrationPending,
    clearPendingMigration: clearGuestMigrationPending,
    hasPendingMigration: hasPendingGuestMigration,
    hasGuestStats: hasGuestStatsData,
    hasGuestGamesBrowserState: hasGuestGamesBrowserState,
    hasGuestPuzzleCatalogState: hasGuestPuzzleCatalogState
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
    title.textContent = opts.title || (variant === 'danger' ? 'Lỗi' : 'Thông báo');
    body.appendChild(title);

    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'btn-close' + (
      variant === 'danger' || variant === 'success' ? ' btn-close-white' : ''
    );
    closeBtn.setAttribute('data-bs-dismiss', 'toast');
    closeBtn.setAttribute('aria-label', 'Đóng');
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
    const successMessage = opts.successMessage || data?.message || 'Thao tác thành công';
    const errorMessage = opts.errorMessage || data?.error || data?.message || 'Thao tác thất bại';
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

  async function confirmAction(options) {
    const opts = options || {};
    const title = String(opts.title || 'Xac nhan thao tac').trim() || 'Xac nhan thao tac';
    const text = String(opts.text || opts.message || '').trim();
    const icon = String(opts.icon || 'warning').trim() || 'warning';
    const confirmText = String(opts.confirmText || 'Xac nhan').trim() || 'Xac nhan';
    const cancelText = String(opts.cancelText || 'Huy').trim() || 'Huy';
    const fallbackText = String(opts.fallbackText || text || title).trim() || 'Xac nhan thao tac?';
    const isDanger = opts.danger !== false;

    if (typeof Swal !== 'undefined' && Swal && typeof Swal.fire === 'function') {
      try {
        const result = await Swal.fire({
          title: title,
          text: text || undefined,
          icon: icon,
          showCancelButton: true,
          reverseButtons: true,
          focusCancel: !isDanger,
          confirmButtonText: confirmText,
          cancelButtonText: cancelText,
          buttonsStyling: false,
          customClass: {
            popup: 'cg-confirm-modal' + (isDanger ? ' cg-confirm-modal--danger' : ''),
            confirmButton: 'cg-confirm-modal__confirm',
            cancelButton: 'cg-confirm-modal__cancel'
          }
        });
        return !!result.isConfirmed;
      } catch (_) {
      }
    }

    if (typeof window !== 'undefined' && typeof window.confirm === 'function') {
      return !!window.confirm(fallbackText);
    }
    return false;
  }

  window.CaroUi = {
    toast: showToast,
    apiResult: reportApiResult,
    setStatus: setStatusMessage,
    confirmAction: confirmAction
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
    const userHandles = document.querySelectorAll('[data-current-user-handle]');
    const userNames = document.querySelectorAll('[data-current-user-name]');
    const userEmails = document.querySelectorAll('[data-current-user-email]');
    const userMetas = document.querySelectorAll('[data-current-user-meta]');
    const userAvatars = document.querySelectorAll('[data-current-user-avatar]');
    const logoutButtons = document.querySelectorAll('[data-logout-btn]');
    const authOnly = document.querySelectorAll('[data-auth-only]');
    const guestOnly = document.querySelectorAll('[data-guest-only]');
    const roleOnly = document.querySelectorAll('[data-role-allowed]');

    const applyAuthState = (user) => {
      const safeUser = user && user.userId ? normalizeCurrentUser(user) : null;
      const handle = safeUser ? ('@' + (safeUser.username || safeUser.displayName)) : '@guest';
      badges.forEach((el) => {
        if (safeUser) {
          el.textContent = safeUser.displayName + ' (' + safeUser.userId + ')';
        } else {
          el.textContent = 'Chua dang nhap';
        }
      });
      userHandles.forEach((el) => {
        el.textContent = handle;
      });
      userNames.forEach((el) => {
        el.textContent = safeUser ? safeUser.displayName : 'Nguoi choi';
      });
      userEmails.forEach((el) => {
        el.textContent = safeUser ? (safeUser.email || '') : '';
      });
      userMetas.forEach((el) => {
        el.textContent = safeUser
          ? ('Diem thuong: ' + String(normalizeNonNegativeInt(safeUser.score, 0)))
          : 'Dang nhap de xem du lieu tai khoan';
      });
      userAvatars.forEach((el) => {
        const avatarPath = safeUser ? safeUser.avatarPath : DEFAULT_AVATAR_PATH;
        el.setAttribute('src', toAppPath(avatarPath));
        el.setAttribute('alt', safeUser ? ('Anh dai dien ' + safeUser.displayName) : 'Anh dai dien');
      });

      authOnly.forEach((el) => {
        el.style.display = safeUser ? '' : 'none';
      });
      guestOnly.forEach((el) => {
        el.style.display = safeUser ? 'none' : '';
      });
      roleOnly.forEach((el) => {
        if (!safeUser || !safeUser.role) {
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
        el.style.display = allowed.includes(String(safeUser.role).trim().toLowerCase()) ? '' : 'none';
      });
    };

    let user = getCurrentUser();
    applyAuthState(user);
    window.addEventListener(USER_CHANGE_EVENT, (event) => {
      user = event?.detail?.user && event.detail.user.userId ? normalizeCurrentUser(event.detail.user) : null;
      applyAuthState(user);
    });

    const shouldMigrateGuestData = () => !!window.CaroGuestData
      && (
        window.CaroGuestData.hasPendingMigration?.()
        || window.CaroGuestData.hasGuestStats?.()
        || window.CaroGuestData.hasGuestGamesBrowserState?.()
        || window.CaroGuestData.hasGuestPuzzleCatalogState?.()
      );

    const syncSessionUser = () => {
      refreshCurrentUserFromSession()
        .then((sessionUser) => {
          user = sessionUser && sessionUser.userId ? normalizeCurrentUser(sessionUser) : null;
          applyAuthState(user);
          if (user && shouldMigrateGuestData()) {
            window.CaroGuestData.migrateToAccount?.();
          }
        })
        .catch(() => {
        });
    };

    const scheduleSessionUserSync = () => {
      if (typeof window.requestIdleCallback === 'function') {
        window.requestIdleCallback(syncSessionUser, { timeout: 1200 });
        return;
      }
      window.setTimeout(syncSessionUser, 120);
    };

    scheduleSessionUserSync();

    if (window.axios && window.axios.defaults && window.axios.defaults.headers) {
      const csrf = getCsrfMeta();
      if (csrf) {
        window.axios.defaults.headers.common = window.axios.defaults.headers.common || {};
        window.axios.defaults.headers.common[csrf.headerName] = csrf.token;
      }
    }

    logoutButtons.forEach((logoutBtn) => {
      logoutBtn.addEventListener('click', async () => {
        const current = getCurrentUser();
        if (!current || !current.userId) {
          clearCurrentUser();
          window.location.href = window.CaroUrl.path('/');
          return;
        }
        try {
          await fetch(toAppPath('/account/logout'), {
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
    });
  });
})();
