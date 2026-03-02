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
    timer: document.getElementById('msTimer'),
    statusText: document.getElementById('msStatusText'),
    statusPill: document.getElementById('msStatusPill'),
    levelPill: document.getElementById('msLevelPill'),
    restartBtn: document.getElementById('msRestartBtn'),
    flagModeBtn: document.getElementById('msFlagModeBtn'),
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
    refs.flagModeBtn.textContent = 'Che do dat co: ' + (state.flagMode ? 'Bat' : 'Tat');
    refs.flagModeBtn.classList.toggle('btn-outline-secondary', !state.flagMode);
    refs.flagModeBtn.classList.toggle('btn-warning', state.flagMode);
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

    const label = state.progressive.enabled
      ? ('Leo thang V' + state.progressive.round)
      : (state.currentConfig?.label || 'Minesweeper');
    refs.levelPill.textContent = label;
    refs.boardMeta.textContent = describeConfig(state.currentConfig);
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
        el.textContent = '*';
      } else if (cell.adjacent > 0) {
        el.textContent = String(cell.adjacent);
        el.classList.add('n' + cell.adjacent);
      } else {
        el.innerHTML = '&nbsp;';
      }
    } else if (cell.flagged) {
      el.classList.add('flagged');
      el.textContent = 'F';
    } else if (cell.questioned) {
      el.classList.add('questioned');
      el.textContent = '?';
    } else if (cell.wrongFlagged) {
      el.classList.add('wrong-flag');
      el.textContent = 'X';
    }

    if (cell.exploded) {
      el.classList.add('exploded');
      el.textContent = '*';
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
    state.gameOver = true;
    state.win = !!win;
    stopTimer();

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

    refs.levelButtons.forEach((btn) => {
      btn.addEventListener('click', () => startPresetLevel(btn.dataset.level || 'beginner'));
    });

    refs.progressiveModeBtn?.addEventListener('click', () => {
      setProgressiveEnabled(!state.progressive.enabled);
    });

    refs.nextRoundBtn?.addEventListener('click', advanceToNextRound);

    refs.applyCustomBtn?.addEventListener('click', () => applyCustomFromInputs(false));
    refs.applyCustomProgressiveBtn?.addEventListener('click', () => applyCustomFromInputs(true));
  }

  function boot() {
    bindEvents();
    clearLongPressGesture();
    state.flagMode = false;
    updateFlagModeUi();
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
