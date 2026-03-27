(() => {
  const PRESET_LEVELS = {
    beginner: { rows: 9, cols: 9, mines: 10, label: 'De' },
    intermediate: { rows: 16, cols: 16, mines: 40, label: 'Trung binh' },
    expert: { rows: 16, cols: 30, mines: 99, label: 'Kho' }
  };

  const LIMITS = {
    minRows: 5,
    maxRows: 30,
    minCols: 5,
    maxCols: 40,
    minDensityPercent: 6,
    maxDensityPercent: 40
  };
  const LONG_PRESS_MS = 420;
  const LONG_PRESS_MOVE_CANCEL_PX = 12;
  const STATS_STORAGE_KEY = 'caroMinesweeperStats.v1';
  const GAME_STATS_CODE = 'minesweeper';
  const MAX_HINTS_PER_GAME = 3;

  const state = {
    level: 'beginner',
    currentConfig: null,
    rows: 0,
    cols: 0,
    mines: 0,
    board: [],
    cellEls: [],
    firstMove: true,
    gameOver: false,
    win: false,
    revealedSafeCells: 0,
    flagsPlaced: 0,
    timerSeconds: 0,
    timerId: null,
    flagMode: false,
    hintsUsed: 0,
    stats: {
      totalGames: 0,
      wins: 0,
      losses: 0,
      bestTimes: {}
    },
    progressive: {
      enabled: false,
      round: 1,
      winStreak: 0,
      baseConfig: null,
      nextConfig: null,
      canAdvance: false
    },
    longPress: {
      pointerId: null,
      timerId: null,
      row: -1,
      col: -1,
      startX: 0,
      startY: 0,
      triggered: false
    },
    suppressNextClickKey: '',
    suppressNextClickUntil: 0
  };

  const refs = {
    board: document.getElementById('msBoard'),
    boardMeta: document.getElementById('msBoardMeta'),
    minesLeft: document.getElementById('msMinesLeft'),
    unopenedCount: document.getElementById('msUnopenedCount'),
    timer: document.getElementById('msTimer'),
    statusText: document.getElementById('msStatusText'),
    statusPill: document.getElementById('msStatusPill'),
    levelPill: document.getElementById('msLevelPill'),
    restartBtn: document.getElementById('msRestartBtn'),
    flagModeBtn: document.getElementById('msFlagModeBtn'),
    hintBtn: document.getElementById('msHintBtn'),
    hintMeta: document.getElementById('msHintMeta'),
    bestTime: document.getElementById('msBestTime'),
    winRate: document.getElementById('msWinRate'),
    levelButtons: Array.from(document.querySelectorAll('.js-ms-level')),
    progressiveStateBadge: document.getElementById('msProgressiveStateBadge'),
    progressiveModeBtn: document.getElementById('msProgressiveModeBtn'),
    nextRoundBtn: document.getElementById('msNextRoundBtn'),
    progressiveRound: document.getElementById('msProgressiveRound'),
    winStreak: document.getElementById('msWinStreak'),
    nextRoundPreview: document.getElementById('msNextRoundPreview'),
    customRowsInput: document.getElementById('msCustomRowsInput'),
    customColsInput: document.getElementById('msCustomColsInput'),
    customDensityInput: document.getElementById('msCustomDensityInput'),
    applyCustomBtn: document.getElementById('msApplyCustomBtn'),
    applyCustomProgressiveBtn: document.getElementById('msApplyCustomProgressiveBtn')
  };

  if (!refs.board) {
    return;
  }

  function normalizeLevel(level) {
    return Object.prototype.hasOwnProperty.call(PRESET_LEVELS, level) ? level : 'beginner';
  }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function parseInteger(value, fallback) {
    const parsed = Number.parseInt(String(value ?? '').trim(), 10);
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  function parseFloatSafe(value, fallback) {
    const parsed = Number.parseFloat(String(value ?? '').trim());
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  function sanitizeRows(value) {
    return clamp(parseInteger(value, 9), LIMITS.minRows, LIMITS.maxRows);
  }

  function sanitizeCols(value) {
    return clamp(parseInteger(value, 9), LIMITS.minCols, LIMITS.maxCols);
  }

  function sanitizeDensityPercent(value) {
    return clamp(parseInteger(value, 16), LIMITS.minDensityPercent, LIMITS.maxDensityPercent);
  }

  function ratioToPercent(ratio) {
    return Math.round((ratio || 0) * 100);
  }

  function configMineRatio(config) {
    if (config && Number.isFinite(config.mineRatio)) {
      return config.mineRatio;
    }
    if (!config || !config.rows || !config.cols) {
      return 0.16;
    }
    return clamp(config.mines / (config.rows * config.cols), 0.01, 0.95);
  }

  function computeMineCount(rows, cols, mineRatio) {
    const total = rows * cols;
    if (total <= 1) {
      return 1;
    }
    const ratio = clamp(mineRatio, 0.01, 0.95);
    const desired = Math.round(total * ratio);
    return clamp(desired, 1, total - 1);
  }

  function cloneConfig(config) {
    return config
      ? {
          rows: config.rows,
          cols: config.cols,
          mines: config.mines,
          label: config.label,
          levelKey: config.levelKey || null,
          source: config.source || null,
          mineRatio: configMineRatio(config)
        }
      : null;
  }

  function createConfig(rows, cols, mines, options) {
    const total = rows * cols;
    const normalizedMines = clamp(parseInteger(mines, 1), 1, Math.max(1, total - 1));
    const opts = options || {};
    return {
      rows,
      cols,
      mines: normalizedMines,
      label: opts.label || 'Tuy chinh',
      levelKey: opts.levelKey || null,
      source: opts.source || 'custom',
      mineRatio: clamp(opts.mineRatio ?? normalizedMines / total, 0.01, 0.95)
    };
  }

  function presetConfig(levelKey) {
    const normalizedLevel = normalizeLevel(levelKey);
    const cfg = PRESET_LEVELS[normalizedLevel];
    return createConfig(cfg.rows, cfg.cols, cfg.mines, {
      label: cfg.label,
      levelKey: normalizedLevel,
      source: 'preset',
      mineRatio: cfg.mines / (cfg.rows * cfg.cols)
    });
  }

  function customConfig(rows, cols, densityPercent) {
    const safeRows = sanitizeRows(rows);
    const safeCols = sanitizeCols(cols);
    const safeDensity = sanitizeDensityPercent(densityPercent);
    const ratio = safeDensity / 100;
    return createConfig(safeRows, safeCols, computeMineCount(safeRows, safeCols, ratio), {
      label: 'Tuy chinh',
      levelKey: null,
      source: 'custom',
      mineRatio: ratio
    });
  }

  function progressiveConfigForRound(baseConfig, round) {
    const safeRound = Math.max(1, parseInteger(round, 1));
    const growth = safeRound - 1;
    const base = cloneConfig(baseConfig) || presetConfig('beginner');
    const baseRows = sanitizeRows(base.rows);
    const baseCols = sanitizeCols(base.cols);
    const rows = clamp(baseRows + Math.floor(growth / 2), LIMITS.minRows, LIMITS.maxRows);
    const cols = clamp(baseCols + Math.ceil(growth / 2), LIMITS.minCols, LIMITS.maxCols);
    const ratio = clamp(configMineRatio(base) + (growth * 0.02), 0.10, 0.38);
    const mines = computeMineCount(rows, cols, ratio);
    return createConfig(rows, cols, mines, {
      label: 'Leo thang V' + safeRound,
      levelKey: null,
      source: 'progressive',
      mineRatio: ratio
    });
  }

  function describeConfig(config) {
    if (!config) {
      return '-';
    }
    return config.rows + ' x ' + config.cols + ' - ' + config.mines + ' min';
  }

  function defaultStats() {
    return {
      totalGames: 0,
      wins: 0,
      losses: 0,
      bestTimes: {}
    };
  }

  function normalizeStats(raw) {
    const source = raw && typeof raw === 'object' ? raw : {};
    const totalGames = Math.max(0, parseInteger(source.totalGames, 0));
    const wins = Math.max(0, parseInteger(source.wins, 0));
    const losses = Math.max(0, parseInteger(source.losses, Math.max(0, totalGames - wins)));
    const bestTimes = {};
    if (source.bestTimes && typeof source.bestTimes === 'object') {
      Object.keys(source.bestTimes).forEach((key) => {
        const sec = parseInteger(source.bestTimes[key], -1);
        if (sec >= 0) {
          bestTimes[String(key)] = sec;
        }
      });
    }
    return {
      totalGames: Math.max(totalGames, wins + losses),
      wins,
      losses,
      bestTimes
    };
  }

  function readStatsFromStorage() {
    try {
      const raw = window.localStorage.getItem(STATS_STORAGE_KEY);
      if (!raw) {
        return defaultStats();
      }
      return normalizeStats(JSON.parse(raw));
    } catch (_) {
      return defaultStats();
    }
  }

  function writeStatsToStorage(stats) {
    try {
      window.localStorage.setItem(STATS_STORAGE_KEY, JSON.stringify(normalizeStats(stats)));
    } catch (_) {
    }
  }

  function hasAnyStats(stats) {
    const safe = normalizeStats(stats);
    if (safe.totalGames > 0 || safe.wins > 0 || safe.losses > 0) {
      return true;
    }
    return Object.keys(safe.bestTimes || {}).length > 0;
  }

  function mergeStats(primary, secondary) {
    const first = normalizeStats(primary);
    const second = normalizeStats(secondary);
    const mergedBestTimes = Object.assign({}, first.bestTimes || {});
    Object.keys(second.bestTimes || {}).forEach((key) => {
      const sec = parseInteger(second.bestTimes[key], -1);
      const prev = parseInteger(mergedBestTimes[key], -1);
      if (sec >= 0 && (prev < 0 || sec < prev)) {
        mergedBestTimes[key] = sec;
      }
    });
    const wins = Math.max(first.wins, second.wins);
    const losses = Math.max(first.losses, second.losses);
    const totalGames = Math.max(first.totalGames, second.totalGames, wins + losses);
    return {
      totalGames,
      wins,
      losses,
      bestTimes: mergedBestTimes
    };
  }

  function currentSessionUserId() {
    const current = window.CaroUser?.get?.();
    const userId = current && current.userId ? String(current.userId).trim() : '';
    return userId || null;
  }

  async function readStats() {
    const localStats = readStatsFromStorage();
    const userId = currentSessionUserId();
    const accountStats = window.CaroAccountStats;
    if (!userId || !accountStats || typeof accountStats.get !== 'function') {
      return localStats;
    }

    try {
      const remoteRaw = await accountStats.get(GAME_STATS_CODE);
      const remoteStats = normalizeStats(remoteRaw);
      const merged = mergeStats(remoteStats, localStats);
      if (hasAnyStats(localStats) && typeof accountStats.save === 'function') {
        const saved = await accountStats.save(GAME_STATS_CODE, merged, true);
        if (saved) {
          window.localStorage.removeItem(STATS_STORAGE_KEY);
        }
      }
      return merged;
    } catch (_) {
      return localStats;
    }
  }

  function writeStats() {
    const normalized = normalizeStats(state.stats);
    state.stats = normalized;

    const userId = currentSessionUserId();
    const accountStats = window.CaroAccountStats;
    if (userId && accountStats && typeof accountStats.save === 'function') {
      accountStats.save(GAME_STATS_CODE, normalized, true)
        .then((saved) => {
          if (saved) {
            window.localStorage.removeItem(STATS_STORAGE_KEY);
            return;
          }
          writeStatsToStorage(normalized);
        })
        .catch(() => {
          writeStatsToStorage(normalized);
        });
      return;
    }

    writeStatsToStorage(normalized);
  }

  function statsConfigKey(config) {
    const current = config || state.currentConfig;
    if (!current) {
      return 'unknown';
    }
    if (current.levelKey && PRESET_LEVELS[current.levelKey]) {
      return 'preset:' + current.levelKey;
    }
    return 'custom:' + current.rows + 'x' + current.cols + ':' + current.mines;
  }

  function formatSeconds(seconds) {
    const safe = Math.max(0, parseInteger(seconds, 0));
    const mm = Math.floor(safe / 60);
    const ss = safe % 60;
    if (mm <= 0) {
      return ss + 's';
    }
    return mm + 'm ' + String(ss).padStart(2, '0') + 's';
  }

  function currentBestTime() {
    const key = statsConfigKey(state.currentConfig);
    const sec = parseInteger(state.stats.bestTimes[key], -1);
    return sec >= 0 ? sec : null;
  }

  function updateStatsUi() {
    const total = Math.max(0, parseInteger(state.stats.totalGames, 0));
    const wins = Math.max(0, parseInteger(state.stats.wins, 0));
    const rate = total > 0 ? Math.round((wins * 1000) / total) / 10 : 0;
    const best = currentBestTime();
    if (refs.bestTime) {
      refs.bestTime.textContent = best == null ? '--' : formatSeconds(best);
    }
    if (refs.winRate) {
      refs.winRate.textContent = wins + '/' + total + ' (' + rate + '%)';
    }
  }

  function recordGameResult(win) {
    const resultWin = !!win;
    state.stats.totalGames = Math.max(0, parseInteger(state.stats.totalGames, 0)) + 1;
    if (resultWin) {
      state.stats.wins = Math.max(0, parseInteger(state.stats.wins, 0)) + 1;
      const key = statsConfigKey(state.currentConfig);
      const previous = parseInteger(state.stats.bestTimes[key], -1);
      if (previous < 0 || state.timerSeconds < previous) {
        state.stats.bestTimes[key] = state.timerSeconds;
      }
    } else {
      state.stats.losses = Math.max(0, parseInteger(state.stats.losses, 0)) + 1;
    }
    writeStats();
    updateStatsUi();
  }

  function toast(message, type) {
    const ui = window.CaroUi || {};
    if (ui.toast) {
      ui.toast(message, { type: type || 'info' });
      return;
    }
    if (message) {
      window.alert(message);
    }
  }

  function stopTimer() {
    if (state.timerId) {
      clearInterval(state.timerId);
      state.timerId = null;
    }
  }

  function startTimer() {
    stopTimer();
    state.timerId = window.setInterval(() => {
      state.timerSeconds += 1;
      refs.timer.textContent = state.timerSeconds + 's';
    }, 1000);
  }

  function setStatus(text, pillType) {
    refs.statusText.textContent = text;
    refs.statusPill.textContent = text;
    refs.statusPill.classList.remove('text-bg-primary', 'text-bg-success', 'text-bg-danger', 'text-bg-warning', 'text-bg-secondary');
    refs.statusPill.classList.add(pillType || 'text-bg-primary');
  }

  function updateLevelButtons() {
    refs.levelButtons.forEach((btn) => {
      const active = !!state.currentConfig?.levelKey && btn.dataset.level === state.currentConfig.levelKey;
      btn.classList.toggle('active', active);
      btn.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
  }

  function updateFlagModeUi() {
    refs.flagModeBtn.setAttribute('aria-pressed', state.flagMode ? 'true' : 'false');
    refs.flagModeBtn.textContent = 'Dat co: ' + (state.flagMode ? 'Bat' : 'Tat');
    refs.flagModeBtn.classList.toggle('is-on', state.flagMode);
  }

  function updateHintUi() {
    if (refs.hintMeta) {
      refs.hintMeta.textContent = 'Goi y: ' + state.hintsUsed + '/' + MAX_HINTS_PER_GAME;
    }
    if (refs.hintBtn) {
      refs.hintBtn.disabled = state.gameOver || state.hintsUsed >= MAX_HINTS_PER_GAME;
    }
  }

  function updateProgressiveUi() {
    const p = state.progressive;
    if (refs.progressiveRound) refs.progressiveRound.textContent = String(p.round);
    if (refs.winStreak) refs.winStreak.textContent = String(p.winStreak);
    if (refs.nextRoundPreview) refs.nextRoundPreview.textContent = p.enabled && p.nextConfig ? describeConfig(p.nextConfig) : '-';

    if (refs.progressiveStateBadge) {
      refs.progressiveStateBadge.textContent = p.enabled ? 'Bat' : 'Tat';
      refs.progressiveStateBadge.classList.toggle('text-bg-secondary', !p.enabled);
      refs.progressiveStateBadge.classList.toggle('text-bg-warning', p.enabled);
    }

    if (refs.progressiveModeBtn) {
      refs.progressiveModeBtn.textContent = p.enabled ? 'Tat leo thang' : 'Bat leo thang';
      refs.progressiveModeBtn.classList.toggle('btn-outline-secondary', !p.enabled);
      refs.progressiveModeBtn.classList.toggle('btn-warning', p.enabled);
      refs.progressiveModeBtn.setAttribute('aria-pressed', p.enabled ? 'true' : 'false');
    }

    if (refs.nextRoundBtn) {
      refs.nextRoundBtn.disabled = !(p.enabled && p.canAdvance && state.gameOver);
    }
  }

  function updateCounters() {
    refs.minesLeft.textContent = String(state.mines - state.flagsPlaced);
    refs.timer.textContent = state.timerSeconds + 's';
    if (refs.unopenedCount) {
      refs.unopenedCount.textContent = String(countUnopenedCells());
    }

    const label = state.progressive.enabled
      ? ('Leo thang V' + state.progressive.round)
      : (state.currentConfig?.label || 'Minesweeper');
    refs.levelPill.textContent = label;
    refs.boardMeta.textContent = describeConfig(state.currentConfig);
    updateHintUi();
    updateStatsUi();
  }

  function countUnopenedCells() {
    let hidden = 0;
    for (let r = 0; r < state.rows; r += 1) {
      for (let c = 0; c < state.cols; c += 1) {
        if (!state.board[r][c].revealed) {
          hidden += 1;
        }
      }
    }
    return hidden;
  }

  function syncCustomInputs() {
    if (!state.currentConfig) {
      return;
    }
    refs.customRowsInput.value = String(state.currentConfig.rows);
    refs.customColsInput.value = String(state.currentConfig.cols);
    refs.customDensityInput.value = String(sanitizeDensityPercent(ratioToPercent(configMineRatio(state.currentConfig))));
  }

  function syncUrl() {
    const url = new URL(window.location.href);
    const isPreset = !!state.currentConfig?.levelKey && PRESET_LEVELS[state.currentConfig.levelKey];

    url.searchParams.set('level', isPreset ? state.currentConfig.levelKey : 'beginner');

    if (isPreset && !state.progressive.enabled) {
      url.searchParams.delete('rows');
      url.searchParams.delete('cols');
      url.searchParams.delete('density');
    } else if (state.currentConfig) {
      url.searchParams.set('rows', String(state.currentConfig.rows));
      url.searchParams.set('cols', String(state.currentConfig.cols));
      url.searchParams.set('density', String(sanitizeDensityPercent(ratioToPercent(configMineRatio(state.currentConfig)))));
    }

    if (state.progressive.enabled) {
      url.searchParams.set('progressive', '1');
    } else {
      url.searchParams.delete('progressive');
    }

    window.history.replaceState(null, '', url.pathname + url.search);
  }

  function createCell() {
    return {
      mine: false,
      revealed: false,
      flagged: false,
      questioned: false,
      adjacent: 0,
      exploded: false,
      wrongFlagged: false
    };
  }

  function buildEmptyBoard(rows, cols) {
    return Array.from({ length: rows }, () => Array.from({ length: cols }, () => createCell()));
  }

  function eachNeighbor(row, col, callback) {
    for (let dr = -1; dr <= 1; dr += 1) {
      for (let dc = -1; dc <= 1; dc += 1) {
        if (dr === 0 && dc === 0) continue;
        const nr = row + dr;
        const nc = col + dc;
        if (nr < 0 || nr >= state.rows || nc < 0 || nc >= state.cols) continue;
        callback(nr, nc);
      }
    }
  }

  function placeMines(firstRow, firstCol) {
    const candidates = [];
    const safeZone = new Set();
    eachNeighbor(firstRow, firstCol, (nr, nc) => safeZone.add(nr + ',' + nc));
    safeZone.add(firstRow + ',' + firstCol);

    for (let r = 0; r < state.rows; r += 1) {
      for (let c = 0; c < state.cols; c += 1) {
        const key = r + ',' + c;
        if (!safeZone.has(key)) {
          candidates.push([r, c]);
        }
      }
    }

    if (candidates.length < state.mines) {
      candidates.length = 0;
      for (let r = 0; r < state.rows; r += 1) {
        for (let c = 0; c < state.cols; c += 1) {
          if (r === firstRow && c === firstCol) continue;
          candidates.push([r, c]);
        }
      }
    }

    for (let i = candidates.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      const tmp = candidates[i];
      candidates[i] = candidates[j];
      candidates[j] = tmp;
    }

    for (let i = 0; i < state.mines; i += 1) {
      const [r, c] = candidates[i];
      state.board[r][c].mine = true;
    }

    for (let r = 0; r < state.rows; r += 1) {
      for (let c = 0; c < state.cols; c += 1) {
        if (state.board[r][c].mine) continue;
        let count = 0;
        eachNeighbor(r, c, (nr, nc) => {
          if (state.board[nr][nc].mine) count += 1;
        });
        state.board[r][c].adjacent = count;
      }
    }
  }

  function createBoardDom() {
    refs.board.innerHTML = '';
    refs.board.style.setProperty('--ms-cols', String(state.cols));
    state.cellEls = Array.from({ length: state.rows }, () => Array(state.cols));

    const frag = document.createDocumentFragment();
    for (let r = 0; r < state.rows; r += 1) {
      for (let c = 0; c < state.cols; c += 1) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'ms-cell';
        btn.dataset.row = String(r);
        btn.dataset.col = String(c);
        btn.setAttribute('role', 'gridcell');
        btn.setAttribute('aria-label', 'O ' + (r + 1) + ',' + (c + 1));
        frag.appendChild(btn);
        state.cellEls[r][c] = btn;
      }
    }
    refs.board.appendChild(frag);
  }

  function renderCell(row, col) {
    const cell = state.board[row][col];
    const el = state.cellEls[row][col];
    if (!el) return;

    el.className = 'ms-cell';
    el.textContent = '';

    if (cell.revealed) {
      el.classList.add('revealed');
      if (cell.mine) {
        el.classList.add('mine');
        el.textContent = '✹';
      } else if (cell.adjacent > 0) {
        el.textContent = String(cell.adjacent);
        el.classList.add('n' + cell.adjacent);
      } else {
        el.innerHTML = '&nbsp;';
      }
    } else if (cell.flagged) {
      el.classList.add('flagged');
      el.textContent = '⚑';
    } else if (cell.questioned) {
      el.classList.add('questioned');
      el.textContent = '?';
    } else if (cell.wrongFlagged) {
      el.classList.add('wrong-flag');
      el.textContent = '×';
    }

    if (cell.exploded) {
      el.classList.add('exploded');
      el.textContent = '✹';
    }
  }

  function renderBoard() {
    for (let r = 0; r < state.rows; r += 1) {
      for (let c = 0; c < state.cols; c += 1) {
        renderCell(r, c);
      }
    }
  }

  function revealConnectedZeros(startRow, startCol) {
    const queue = [[startRow, startCol]];
    const seen = new Set();

    while (queue.length > 0) {
      const [row, col] = queue.shift();
      const key = row + ',' + col;
      if (seen.has(key)) continue;
      seen.add(key);

      const cell = state.board[row][col];
      if (cell.revealed || cell.flagged) continue;
      if (cell.mine) continue;

      cell.revealed = true;
      state.revealedSafeCells += 1;
      renderCell(row, col);

      if (cell.adjacent !== 0) {
        continue;
      }

      eachNeighbor(row, col, (nr, nc) => {
        const neighbor = state.board[nr][nc];
        if (!neighbor.revealed && !neighbor.flagged && !neighbor.mine) {
          queue.push([nr, nc]);
        }
      });
    }
  }

  function revealAllMines(explodedRow, explodedCol) {
    for (let r = 0; r < state.rows; r += 1) {
      for (let c = 0; c < state.cols; c += 1) {
        const cell = state.board[r][c];
        if (cell.mine) {
          cell.revealed = true;
          cell.questioned = false;
          if (r === explodedRow && c === explodedCol) {
            cell.exploded = true;
          }
        } else if (cell.flagged) {
          cell.flagged = false;
          cell.wrongFlagged = true;
          cell.questioned = false;
        }
      }
    }
    renderBoard();
  }

  function seedProgressiveFromCurrent() {
    state.progressive.baseConfig = cloneConfig(state.currentConfig) || presetConfig('beginner');
    state.progressive.round = 1;
    state.progressive.winStreak = 0;
    state.progressive.canAdvance = false;
    state.progressive.nextConfig = progressiveConfigForRound(state.progressive.baseConfig, 2);
    updateProgressiveUi();
    updateCounters();
    syncUrl();
  }

  function setProgressiveEnabled(enabled) {
    const nextEnabled = !!enabled;
    if (state.progressive.enabled === nextEnabled && nextEnabled) {
      seedProgressiveFromCurrent();
      return;
    }

    state.progressive.enabled = nextEnabled;
    state.progressive.canAdvance = false;
    state.progressive.winStreak = 0;
    state.progressive.round = 1;
    state.progressive.nextConfig = null;
    state.progressive.baseConfig = null;

    if (nextEnabled) {
      seedProgressiveFromCurrent();
      toast('Da bat che do leo thang. Thang moi van de mo khoa van tiep theo.', 'info');
    } else {
      updateProgressiveUi();
      updateCounters();
      syncUrl();
    }
  }

  function finishGame(win) {
    if (state.gameOver) {
      return;
    }
    state.gameOver = true;
    state.win = !!win;
    stopTimer();
    recordGameResult(!!win);

    if (win) {
      fetch('/minesweeper/win', { method: 'POST' });
      if (state.progressive.enabled) {
        state.progressive.canAdvance = true;
        state.progressive.winStreak += 1;
        state.progressive.nextConfig = progressiveConfigForRound(
          state.progressive.baseConfig || state.currentConfig,
          state.progressive.round + 1
        );
        setStatus('Ban thang - san sang len van', 'text-bg-success');
      } else {
        setStatus('Ban thang', 'text-bg-success');
      }
    } else {
      if (state.progressive.enabled) {
        state.progressive.canAdvance = false;
        state.progressive.winStreak = 0;
      }
      setStatus('Ban da thua', 'text-bg-danger');
    }

    updateProgressiveUi();
    updateHintUi();
  }

  function checkWin() {
    const safeCells = state.rows * state.cols - state.mines;
    if (state.revealedSafeCells >= safeCells) {
      finishGame(true);
    }
  }

  function revealCell(row, col) {
    if (state.gameOver) return;
    const cell = state.board[row][col];
    if (cell.revealed || cell.flagged) return;

    if (state.firstMove) {
      placeMines(row, col);
      state.firstMove = false;
      setStatus('Dang choi', 'text-bg-primary');
      startTimer();
    }

    if (cell.mine) {
      revealAllMines(row, col);
      finishGame(false);
      return;
    }

    revealConnectedZeros(row, col);
    checkWin();
  }

  function cycleMark(row, col) {
    if (state.gameOver) return;
    const cell = state.board[row][col];
    if (cell.revealed) return;

    if (cell.flagged) {
      cell.flagged = false;
      cell.questioned = true;
      state.flagsPlaced = Math.max(0, state.flagsPlaced - 1);
    } else if (cell.questioned) {
      cell.questioned = false;
    } else {
      cell.flagged = true;
      cell.questioned = false;
      state.flagsPlaced += 1;
    }
    renderCell(row, col);
    updateCounters();
  }

  function setFlagged(row, col, flagged) {
    const cell = state.board[row][col];
    if (!cell || cell.revealed) return false;
    const nextFlagged = !!flagged;
    if (nextFlagged) {
      if (cell.flagged) return false;
      cell.flagged = true;
      cell.questioned = false;
      state.flagsPlaced += 1;
      renderCell(row, col);
      return true;
    }
    if (!cell.flagged) return false;
    cell.flagged = false;
    state.flagsPlaced = Math.max(0, state.flagsPlaced - 1);
    renderCell(row, col);
    return true;
  }

  function collectNeighborInfo(row, col) {
    const info = {
      flaggedCount: 0,
      hiddenUnflagged: [],
      hiddenAny: []
    };
    eachNeighbor(row, col, (nr, nc) => {
      const neighbor = state.board[nr][nc];
      if (!neighbor) return;
      if (neighbor.flagged) {
        info.flaggedCount += 1;
      }
      if (!neighbor.revealed) {
        info.hiddenAny.push([nr, nc]);
        if (!neighbor.flagged) {
          info.hiddenUnflagged.push([nr, nc]);
        }
      }
    });
    return info;
  }

  function chordReveal(row, col) {
    if (state.gameOver || state.firstMove) return false;
    const cell = state.board[row][col];
    if (!cell || !cell.revealed || cell.mine || cell.adjacent <= 0) return false;

    const info = collectNeighborInfo(row, col);
    if (info.flaggedCount !== cell.adjacent) return false;

    let changed = false;
    for (const [nr, nc] of info.hiddenUnflagged) {
      const neighbor = state.board[nr][nc];
      if (!neighbor || neighbor.revealed || neighbor.flagged) continue;
      changed = true;
      if (neighbor.mine) {
        revealAllMines(nr, nc);
        finishGame(false);
        updateCounters();
        return true;
      }
      revealConnectedZeros(nr, nc);
    }
    if (changed) {
      checkWin();
      updateCounters();
    }
    return changed;
  }

  function autoFlagNeighbors(row, col) {
    if (state.gameOver || state.firstMove) return false;
    const cell = state.board[row][col];
    if (!cell || !cell.revealed || cell.mine || cell.adjacent <= 0) return false;

    const info = collectNeighborInfo(row, col);
    if (info.hiddenUnflagged.length <= 0) return false;
    if (info.flaggedCount + info.hiddenUnflagged.length !== cell.adjacent) return false;

    let changed = false;
    for (const [nr, nc] of info.hiddenUnflagged) {
      changed = setFlagged(nr, nc, true) || changed;
    }
    if (changed) {
      updateCounters();
    }
    return changed;
  }

  function collectSafeHintCandidates() {
    if (state.gameOver) {
      return [];
    }

    const deterministic = new Set();
    if (!state.firstMove) {
      for (let r = 0; r < state.rows; r += 1) {
        for (let c = 0; c < state.cols; c += 1) {
          const cell = state.board[r][c];
          if (!cell || !cell.revealed || cell.mine || cell.adjacent <= 0) {
            continue;
          }
          const info = collectNeighborInfo(r, c);
          if (info.flaggedCount !== cell.adjacent) {
            continue;
          }
          info.hiddenUnflagged.forEach(([nr, nc]) => {
            deterministic.add(nr + ',' + nc);
          });
        }
      }
    }

    if (deterministic.size > 0) {
      return Array.from(deterministic).map((key) => {
        const [row, col] = key.split(',').map((v) => parseInteger(v, -1));
        return [row, col];
      }).filter(([row, col]) => row >= 0 && col >= 0);
    }

    const fallback = [];
    for (let r = 0; r < state.rows; r += 1) {
      for (let c = 0; c < state.cols; c += 1) {
        const cell = state.board[r][c];
        if (!cell || cell.revealed || cell.flagged) {
          continue;
        }
        if (state.firstMove || !cell.mine) {
          fallback.push([r, c]);
        }
      }
    }
    return fallback;
  }

  function useSafeHint() {
    if (state.gameOver) {
      setStatus('Van da ket thuc', 'text-bg-secondary');
      return;
    }
    if (state.hintsUsed >= MAX_HINTS_PER_GAME) {
      setStatus('Da het luot goi y', 'text-bg-warning');
      return;
    }
    const candidates = collectSafeHintCandidates();
    if (!candidates.length) {
      setStatus('Khong con o an toan de goi y', 'text-bg-warning');
      return;
    }

    const [row, col] = candidates[Math.floor(Math.random() * candidates.length)];
    state.hintsUsed += 1;
    revealCell(row, col);
    updateCounters();
    if (!state.gameOver) {
      setStatus('Da mo 1 o an toan', 'text-bg-warning');
    }
    const el = state.cellEls[row]?.[col];
    if (el && typeof el.focus === 'function') {
      el.focus();
    }
  }

  function handlePrimaryAction(row, col) {
    const cell = state.board[row][col];
    if (!cell) return;

    if (state.flagMode) {
      cycleMark(row, col);
      return;
    }

    if (cell.revealed) {
      chordReveal(row, col);
      return;
    }

    revealCell(row, col);
    updateCounters();
  }

  function resetBoardWithConfig(config) {
    const nextConfig = cloneConfig(config) || presetConfig('beginner');
    stopTimer();

    state.currentConfig = nextConfig;
    state.level = nextConfig.levelKey || 'custom';
    state.rows = nextConfig.rows;
    state.cols = nextConfig.cols;
    state.mines = nextConfig.mines;
    state.board = buildEmptyBoard(nextConfig.rows, nextConfig.cols);
    state.cellEls = [];
    state.firstMove = true;
    state.gameOver = false;
    state.win = false;
    state.revealedSafeCells = 0;
    state.flagsPlaced = 0;
    state.hintsUsed = 0;
    state.timerSeconds = 0;
    state.suppressNextClickKey = '';
    state.suppressNextClickUntil = 0;
    clearLongPressGesture();

    createBoardDom();
    renderBoard();
    updateCounters();
    updateLevelButtons();
    updateProgressiveUi();
    syncCustomInputs();
    setStatus('San sang', 'text-bg-secondary');
    refs.statusText.textContent = 'Cho nuoc dau';
    refs.timer.textContent = '0s';
    refs.minesLeft.textContent = String(state.mines);
    syncUrl();
  }

  function restartCurrentBoard() {
    if (state.progressive.enabled && state.progressive.baseConfig) {
      const currentRoundConfig = progressiveConfigForRound(state.progressive.baseConfig, state.progressive.round);
      state.progressive.canAdvance = false;
      resetBoardWithConfig(currentRoundConfig);
      return;
    }
    resetBoardWithConfig(state.currentConfig);
  }

  function startPresetLevel(levelKey) {
    resetBoardWithConfig(presetConfig(levelKey));
    if (state.progressive.enabled) {
      seedProgressiveFromCurrent();
    }
  }

  function applyCustomFromInputs(enableProgressiveAfter) {
    const rows = sanitizeRows(refs.customRowsInput.value);
    const cols = sanitizeCols(refs.customColsInput.value);
    const density = sanitizeDensityPercent(refs.customDensityInput.value);
    refs.customRowsInput.value = String(rows);
    refs.customColsInput.value = String(cols);
    refs.customDensityInput.value = String(density);

    const cfg = customConfig(rows, cols, density);
    resetBoardWithConfig(cfg);

    if (enableProgressiveAfter) {
      if (!state.progressive.enabled) {
        state.progressive.enabled = true;
      }
      seedProgressiveFromCurrent();
    } else if (state.progressive.enabled) {
      seedProgressiveFromCurrent();
    }
  }

  function advanceToNextRound() {
    if (!(state.progressive.enabled && state.progressive.canAdvance)) {
      return;
    }
    state.progressive.round += 1;
    state.progressive.canAdvance = false;
    const cfg = progressiveConfigForRound(state.progressive.baseConfig || state.currentConfig, state.progressive.round);
    state.progressive.nextConfig = progressiveConfigForRound(
      state.progressive.baseConfig || state.currentConfig,
      state.progressive.round + 1
    );
    resetBoardWithConfig(cfg);
  }

  function handleBoardClick(event) {
    const cellBtn = event.target.closest('.ms-cell');
    if (!cellBtn) return;
    const row = Number(cellBtn.dataset.row);
    const col = Number(cellBtn.dataset.col);
    const clickKey = row + ',' + col;
    if (state.suppressNextClickKey && state.suppressNextClickKey === clickKey && Date.now() <= Number(state.suppressNextClickUntil || 0)) {
      state.suppressNextClickKey = '';
      state.suppressNextClickUntil = 0;
      event.preventDefault();
      return;
    }
    handlePrimaryAction(row, col);
  }

  function handleSecondaryAction(row, col) {
    const cell = state.board[row]?.[col];
    if (cell && cell.revealed) {
      autoFlagNeighbors(row, col);
      return;
    }
    cycleMark(row, col);
  }

  function handleBoardContextMenu(event) {
    const cellBtn = event.target.closest('.ms-cell');
    if (!cellBtn) return;
    event.preventDefault();
    const row = Number(cellBtn.dataset.row);
    const col = Number(cellBtn.dataset.col);
    handleSecondaryAction(row, col);
  }

  function clearLongPressGesture() {
    const lp = state.longPress;
    if (lp.timerId) {
      window.clearTimeout(lp.timerId);
    }
    lp.timerId = null;
    lp.pointerId = null;
    lp.row = -1;
    lp.col = -1;
    lp.startX = 0;
    lp.startY = 0;
    lp.triggered = false;
  }

  function handleBoardPointerDown(event) {
    if (!event || event.pointerType !== 'touch') return;
    const cellBtn = event.target.closest('.ms-cell');
    if (!cellBtn) return;

    clearLongPressGesture();
    const row = Number(cellBtn.dataset.row);
    const col = Number(cellBtn.dataset.col);
    const lp = state.longPress;
    lp.pointerId = event.pointerId;
    lp.row = row;
    lp.col = col;
    lp.startX = Number(event.clientX || 0);
    lp.startY = Number(event.clientY || 0);
    lp.triggered = false;
    lp.timerId = window.setTimeout(() => {
      lp.timerId = null;
      if (lp.pointerId == null) return;
      lp.triggered = true;
      state.suppressNextClickKey = lp.row + ',' + lp.col;
      state.suppressNextClickUntil = Date.now() + 1000;
      handleSecondaryAction(lp.row, lp.col);
    }, LONG_PRESS_MS);
  }

  function cancelLongPressIfMoved(event) {
    const lp = state.longPress;
    if (!event || lp.pointerId == null || lp.pointerId !== event.pointerId || lp.triggered) {
      return;
    }
    const dx = Math.abs(Number(event.clientX || 0) - lp.startX);
    const dy = Math.abs(Number(event.clientY || 0) - lp.startY);
    if (dx > LONG_PRESS_MOVE_CANCEL_PX || dy > LONG_PRESS_MOVE_CANCEL_PX) {
      clearLongPressGesture();
    }
  }

  function handleBoardPointerEnd(event) {
    const lp = state.longPress;
    if (!event || lp.pointerId == null || lp.pointerId !== event.pointerId) {
      return;
    }
    if (lp.timerId) {
      window.clearTimeout(lp.timerId);
      lp.timerId = null;
    }
    if (lp.triggered) {
      event.preventDefault();
    }
    lp.pointerId = null;
    lp.row = -1;
    lp.col = -1;
    lp.startX = 0;
    lp.startY = 0;
    lp.triggered = false;
  }

  function readInitialConfigFromUrl() {
    const url = new URL(window.location.href);
    const params = url.searchParams;
    const queryRows = params.get('rows');
    const queryCols = params.get('cols');
    const queryDensity = params.get('density');
    const progressiveParam = (params.get('progressive') || '').toLowerCase();
    const progressiveEnabled = progressiveParam === '1' || progressiveParam === 'true' || progressiveParam === 'on';

    const rows = queryRows == null ? null : sanitizeRows(queryRows);
    const cols = queryCols == null ? null : sanitizeCols(queryCols);
    const density = queryDensity == null ? null : sanitizeDensityPercent(queryDensity);

    if (rows != null && cols != null) {
      return {
        config: customConfig(rows, cols, density ?? 16),
        progressiveEnabled
      };
    }

    const bootLevel = normalizeLevel((window.MinesweeperBoot && window.MinesweeperBoot.initialLevel) || 'beginner');
    return {
      config: presetConfig(bootLevel),
      progressiveEnabled
    };
  }

  function isTypingTarget(target) {
    if (!target || !(target instanceof Element)) {
      return false;
    }
    if (target.closest('input, textarea, select, [contenteditable="true"]')) {
      return true;
    }
    return false;
  }

  function handleGlobalHotkeys(event) {
    if (!event || event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) {
      return;
    }
    if (isTypingTarget(event.target)) {
      return;
    }
    const key = String(event.key || '').toLowerCase();
    if (!key) {
      return;
    }
    if (key === 'r') {
      event.preventDefault();
      restartCurrentBoard();
      return;
    }
    if (key === 'f') {
      event.preventDefault();
      state.flagMode = !state.flagMode;
      updateFlagModeUi();
      return;
    }
    if (key === 'h') {
      event.preventDefault();
      useSafeHint();
      return;
    }
    if (key === 'n') {
      event.preventDefault();
      advanceToNextRound();
      return;
    }
    if (key === '1') {
      event.preventDefault();
      startPresetLevel('beginner');
      return;
    }
    if (key === '2') {
      event.preventDefault();
      startPresetLevel('intermediate');
      return;
    }
    if (key === '3') {
      event.preventDefault();
      startPresetLevel('expert');
    }
  }

  function bindEvents() {
    refs.board.addEventListener('click', handleBoardClick);
    refs.board.addEventListener('contextmenu', handleBoardContextMenu);
    refs.board.addEventListener('pointerdown', handleBoardPointerDown);
    refs.board.addEventListener('pointermove', cancelLongPressIfMoved);
    refs.board.addEventListener('pointerup', handleBoardPointerEnd);
    refs.board.addEventListener('pointercancel', handleBoardPointerEnd);

    refs.restartBtn.addEventListener('click', restartCurrentBoard);
    refs.flagModeBtn.addEventListener('click', () => {
      state.flagMode = !state.flagMode;
      updateFlagModeUi();
    });
    refs.hintBtn?.addEventListener('click', useSafeHint);

    refs.levelButtons.forEach((btn) => {
      btn.addEventListener('click', () => startPresetLevel(btn.dataset.level || 'beginner'));
    });

    refs.progressiveModeBtn?.addEventListener('click', () => {
      setProgressiveEnabled(!state.progressive.enabled);
    });

    refs.nextRoundBtn?.addEventListener('click', advanceToNextRound);

    refs.applyCustomBtn?.addEventListener('click', () => applyCustomFromInputs(false));
    refs.applyCustomProgressiveBtn?.addEventListener('click', () => applyCustomFromInputs(true));
    document.addEventListener('keydown', handleGlobalHotkeys);
  }

  async function boot() {
    state.stats = await readStats();
    bindEvents();
    clearLongPressGesture();
    state.flagMode = false;
    updateFlagModeUi();
    updateHintUi();
    updateStatsUi();
    updateProgressiveUi();

    const initial = readInitialConfigFromUrl();
    resetBoardWithConfig(initial.config);

    if (initial.progressiveEnabled) {
      setProgressiveEnabled(true);
      if (state.progressive.baseConfig) {
        resetBoardWithConfig(progressiveConfigForRound(state.progressive.baseConfig, state.progressive.round));
      }
    }
  }

  boot();
})();
