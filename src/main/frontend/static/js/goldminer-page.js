(() => {
  const VIEW = {
    width: 960,
    height: 600,
    anchorX: 480,
    anchorY: 108,
    minLength: 58,
    minAngle: -1.08,
    maxAngle: 1.08
  };
  const STORAGE_KEY = 'caroGoldMinerStats.v1';
  const BASE_BOMBS = 2;
  const TIMED_BOMB_INTERVAL = 10;
  const TIMED_BOMB_BLAST_RADIUS = 108;
  const MAX_ROUNDS = 3;
  const MAX_LOG_ITEMS = 8;
  const ROUND_TARGETS = [650, 1700, 3200];
  const ITEM_PRESETS = {
    goldSmall: { kind: 'gold', label: 'Vang nho', minRadius: 12, maxRadius: 18, minValue: 80, maxValue: 140, minWeight: 1.4, maxWeight: 1.9 },
    goldMedium: { kind: 'gold', label: 'Vang vua', minRadius: 20, maxRadius: 28, minValue: 190, maxValue: 290, minWeight: 2.4, maxWeight: 3.4 },
    goldLarge: { kind: 'gold', label: 'Vang lon', minRadius: 30, maxRadius: 40, minValue: 420, maxValue: 650, minWeight: 4.2, maxWeight: 5.8 },
    rockSmall: { kind: 'rock', label: 'Da nho', minRadius: 18, maxRadius: 24, minValue: 22, maxValue: 42, minWeight: 4.8, maxWeight: 5.8 },
    rockLarge: { kind: 'rock', label: 'Da lon', minRadius: 28, maxRadius: 36, minValue: 40, maxValue: 68, minWeight: 6.2, maxWeight: 7.8 },
    diamond: { kind: 'diamond', label: 'Kim cuong', minRadius: 10, maxRadius: 14, minValue: 420, maxValue: 620, minWeight: 1.0, maxWeight: 1.4 },
    bag: { kind: 'bag', label: 'Tui bi an', minRadius: 16, maxRadius: 20, minValue: 90, maxValue: 520, minWeight: 2.0, maxWeight: 2.8 },
    bomb: { kind: 'bomb', label: 'Bom TNT', minRadius: 14, maxRadius: 18, minValue: 0, maxValue: 0, minWeight: 1.1, maxWeight: 1.5 }
  };
  const UPGRADE_POOL = [
    {
      id: 'reel',
      icon: 'bi-tools',
      title: 'Tang toi',
      description: '+18% toc do keo khi moc da dinh loot.',
      apply: (state) => {
        state.modifiers.pullMultiplier *= 1.18;
      }
    },
    {
      id: 'bomb-pack',
      icon: 'bi-bomb-fill',
      title: 'Them TNT',
      description: '+2 TNT de cat lo trong vong sau.',
      apply: (state) => {
        state.inventory.bombs += 2;
      }
    },
    {
      id: 'clock',
      icon: 'bi-hourglass-split',
      title: 'Dong ho cat',
      description: '+6 giay moi vong.',
      apply: (state) => {
        state.modifiers.timeBonus += 6;
      }
    },
    {
      id: 'gold-lens',
      icon: 'bi-search',
      title: 'May quet vang',
      description: '+20% gia tri tat ca nugget.',
      apply: (state) => {
        state.modifiers.goldBonus += 0.2;
      }
    },
    {
      id: 'gem-polish',
      icon: 'bi-gem',
      title: 'Mai gem',
      description: '+30% gia tri kim cuong.',
      apply: (state) => {
        state.modifiers.diamondBonus += 0.3;
      }
    },
    {
      id: 'lucky-bag',
      icon: 'bi-bag-fill',
      title: 'Lucky bag',
      description: 'Tui bi an co them bonus diem.',
      apply: (state) => {
        state.modifiers.bagLuck += 1;
      }
    },
    {
      id: 'tension',
      icon: 'bi-lightning-charge-fill',
      title: 'Day cang',
      description: '+14% toc do tha day va rut moc rong.',
      apply: (state) => {
        state.modifiers.extendMultiplier *= 1.14;
      }
    }
  ];

  const refs = {
    canvas: document.getElementById('goldminerCanvas'),
    round: document.getElementById('goldminerRound'),
    score: document.getElementById('goldminerScore'),
    target: document.getElementById('goldminerTarget'),
    time: document.getElementById('goldminerTime'),
    bombCountdown: document.getElementById('goldminerBombCountdown'),
    bombs: document.getElementById('goldminerBombs'),
    hazardCount: document.getElementById('goldminerHazardCount'),
    bestScore: document.getElementById('goldminerBestScore'),
    goalLabel: document.getElementById('goldminerGoalLabel'),
    statusLine: document.getElementById('goldminerStatusLine'),
    progressFill: document.getElementById('goldminerProgressFill'),
    hazardPanel: document.getElementById('goldminerHazardPanel'),
    hazardTitle: document.getElementById('goldminerHazardTitle'),
    hazardText: document.getElementById('goldminerHazardText'),
    hazardActive: document.getElementById('goldminerHazardActive'),
    remaining: document.getElementById('goldminerRemaining'),
    bestRound: document.getElementById('goldminerBestRound'),
    totalRuns: document.getElementById('goldminerTotalRuns'),
    wins: document.getElementById('goldminerWins'),
    eventLog: document.getElementById('goldminerEventLog'),
    startBtn: document.getElementById('goldminerStartBtn'),
    heroStartBtn: document.getElementById('goldminerHeroStartBtn'),
    pauseBtn: document.getElementById('goldminerPauseBtn'),
    bombBtn: document.getElementById('goldminerBombBtn'),
    restartBtn: document.getElementById('goldminerRestartBtn'),
    overlay: document.getElementById('goldminerOverlay'),
    overlayEyebrow: document.getElementById('goldminerOverlayEyebrow'),
    overlayTitle: document.getElementById('goldminerOverlayTitle'),
    overlayText: document.getElementById('goldminerOverlayText'),
    overlayChoices: document.getElementById('goldminerOverlayChoices'),
    overlayPrimary: document.getElementById('goldminerOverlayPrimary'),
    overlaySecondary: document.getElementById('goldminerOverlaySecondary')
  };

  if (!refs.canvas) {
    return;
  }

  const ctx = refs.canvas.getContext('2d');
  const boot = window.GoldMinerBoot || {};

  const state = {
    status: 'ready',
    runStarted: false,
    round: 1,
    score: 0,
    target: ROUND_TARGETS[0],
    timeLeft: 57,
    noticeText: 'Nhan Space hoac bam vao canvas de tha moc.',
    roundSuccess: false,
    roundEndPending: false,
    resultCommitted: false,
    items: [],
    logs: [],
    popups: [],
    sparks: [],
    overlayConfig: null,
    lastFrameAt: 0,
    itemSeq: 0,
    bombSpawnElapsed: 0,
    stats: readStats(),
    inventory: {
      bombs: BASE_BOMBS
    },
    modifiers: {
      pullMultiplier: 1,
      extendMultiplier: 1,
      goldBonus: 0,
      diamondBonus: 0,
      bagLuck: 0,
      timeBonus: 0
    },
    tool: {
      angle: -0.96,
      swingDirection: 1,
      length: VIEW.minLength,
      phase: 'aiming',
      attachedId: '',
      swingSpeed: 1.22
    }
  };

  function defaultStats() {
    return {
      bestScore: 0,
      bestRound: 1,
      totalRuns: 0,
      wins: 0
    };
  }

  function readStats() {
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return defaultStats();
      }
      const parsed = JSON.parse(raw);
      return {
        bestScore: toSafeNumber(parsed.bestScore, 0),
        bestRound: Math.max(1, toSafeNumber(parsed.bestRound, 1)),
        totalRuns: Math.max(0, toSafeNumber(parsed.totalRuns, 0)),
        wins: Math.max(0, toSafeNumber(parsed.wins, 0))
      };
    } catch (_) {
      return defaultStats();
    }
  }

  function writeStats() {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state.stats));
    } catch (_) {
    }
  }

  function toSafeNumber(value, fallback) {
    const num = Number(value);
    return Number.isFinite(num) ? num : fallback;
  }

  function formatNumber(value) {
    return Math.round(Math.max(0, value)).toLocaleString('vi-VN');
  }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function randomRange(min, max) {
    return min + (Math.random() * (max - min));
  }

  function randomInt(min, max) {
    return Math.round(randomRange(min, max));
  }

  function shuffle(source) {
    const arr = [...source];
    for (let index = arr.length - 1; index > 0; index -= 1) {
      const swapIndex = Math.floor(Math.random() * (index + 1));
      const temp = arr[index];
      arr[index] = arr[swapIndex];
      arr[swapIndex] = temp;
    }
    return arr;
  }

  function toolDirection() {
    return {
      x: Math.sin(state.tool.angle),
      y: Math.cos(state.tool.angle)
    };
  }

  function hookTipAt(length) {
    const dir = toolDirection();
    return {
      x: VIEW.anchorX + (dir.x * length),
      y: VIEW.anchorY + (dir.y * length)
    };
  }

  function getAttachedItem() {
    return state.items.find((item) => item.id === state.tool.attachedId) || null;
  }

  function calcTarget(round) {
    if (ROUND_TARGETS[round - 1]) {
      return ROUND_TARGETS[round - 1];
    }
    const overflow = round - ROUND_TARGETS.length;
    return ROUND_TARGETS[ROUND_TARGETS.length - 1] + (overflow * 1200);
  }

  function calcRoundTime(round) {
    return Math.max(40, 57 - ((round - 1) * 4) + state.modifiers.timeBonus);
  }

  function clearRoundVisuals() {
    state.popups = [];
    state.sparks = [];
  }

  function resetTool() {
    state.tool.angle = -0.96;
    state.tool.swingDirection = 1;
    state.tool.length = VIEW.minLength;
    state.tool.phase = 'aiming';
    state.tool.attachedId = '';
  }

  function pushLog(title, detail, tone) {
    state.logs.unshift({
      id: Date.now() + Math.random(),
      title: String(title || '').trim() || 'Log',
      detail: String(detail || '').trim(),
      tone: tone || 'neutral'
    });
    state.logs = state.logs.slice(0, MAX_LOG_ITEMS);
    renderLog();
  }

  function renderLog() {
    refs.eventLog.innerHTML = '';
    if (state.logs.length === 0) {
      refs.eventLog.innerHTML = '<div class="goldminer-log__empty">Chua co su kien. Bat dau run de nhat loot.</div>';
      return;
    }
    state.logs.forEach((entry) => {
      const card = document.createElement('article');
      card.className = 'goldminer-log__item';
      if (entry.tone === 'good') {
        card.classList.add('is-good');
      } else if (entry.tone === 'bad') {
        card.classList.add('is-bad');
      }
      card.innerHTML = '<strong>' + escapeHtml(entry.title) + '</strong><small>' + escapeHtml(entry.detail || '-') + '</small>';
      refs.eventLog.appendChild(card);
    });
  }

  function escapeHtml(value) {
    return String(value || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  function setOverlay(config) {
    state.overlayConfig = config || null;
    refs.overlay.hidden = !config;
    refs.overlayChoices.innerHTML = '';
    refs.overlayPrimary.dataset.action = '';
    refs.overlaySecondary.dataset.action = '';

    if (!config) {
      return;
    }

    refs.overlayEyebrow.textContent = config.eyebrow || '';
    refs.overlayTitle.textContent = config.title || '';
    refs.overlayText.textContent = config.text || '';

    refs.overlayPrimary.hidden = !config.primaryLabel;
    refs.overlaySecondary.hidden = !config.secondaryLabel;

    if (config.primaryLabel) {
      refs.overlayPrimary.textContent = config.primaryLabel;
      refs.overlayPrimary.dataset.action = config.primaryAction || '';
    }
    if (config.secondaryLabel) {
      refs.overlaySecondary.textContent = config.secondaryLabel;
      refs.overlaySecondary.dataset.action = config.secondaryAction || '';
    }

    (config.choices || []).forEach((choice) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'goldminer-upgrade';
      button.dataset.upgradeId = choice.id;
      button.innerHTML = `
        <span class="goldminer-upgrade__icon"><i class="bi ${choice.icon}"></i></span>
        <strong>${escapeHtml(choice.title)}</strong>
        <span>${escapeHtml(choice.description)}</span>
      `;
      refs.overlayChoices.appendChild(button);
    });
  }

  function showReadyOverlay() {
    setOverlay({
      eyebrow: 'Ready',
      title: 'Bat dau Dao vang',
      text: 'Tha moc vao goc dep, lay vang gia tri cao va dung TNT de bo qua cu keo xau. Qua 3 vong de hoan tat run.',
      primaryLabel: 'Bat dau run',
      primaryAction: 'start-run',
      secondaryLabel: 'Chi tiet game',
      secondaryAction: 'open-detail',
      choices: []
    });
  }

  function showShopOverlay() {
    setOverlay({
      eyebrow: 'Round clear',
      title: 'Cua hang giua vong',
      text: 'Chon 1 nang cap truoc khi vao vong tiep theo, hoac bo qua neu muon giu nhip nhanh.',
      secondaryLabel: 'Bo qua nang cap',
      secondaryAction: 'skip-upgrade',
      choices: shuffle(UPGRADE_POOL).slice(0, 3)
    });
  }

  function showWinOverlay() {
    setOverlay({
      eyebrow: 'Run complete',
      title: 'Ban da dao trung jackpot',
      text: 'Hoan tat ca 3 vong va chot run voi tong diem ' + formatNumber(state.score) + '.',
      primaryLabel: 'Choi lai',
      primaryAction: 'restart-run',
      secondaryLabel: 'Chi tiet game',
      secondaryAction: 'open-detail',
      choices: []
    });
  }

  function showLoseOverlay() {
    const missing = Math.max(0, state.target - state.score);
    setOverlay({
      eyebrow: 'Run over',
      title: 'Chua dat target',
      text: 'Ban con thieu ' + formatNumber(missing) + ' diem de qua vong ' + state.round + '. Thu lai voi goc moc dep hon hoac dung TNT som hon.',
      primaryLabel: 'Thu lai',
      primaryAction: 'restart-run',
      secondaryLabel: 'Chi tiet game',
      secondaryAction: 'open-detail',
      choices: []
    });
  }

  function applyUpgradeAndAdvance(upgradeId) {
    if (!(state.overlayConfig && Array.isArray(state.overlayConfig.choices))) {
      return;
    }
    const choice = state.overlayConfig.choices.find((item) => item.id === upgradeId);
    if (!choice || typeof choice.apply !== 'function') {
      return;
    }
    choice.apply(state);
    pushLog('Nhan upgrade', choice.title + ' da duoc them vao run.', 'good');
    beginRound(state.round + 1, true);
  }

  function goTo(url) {
    const target = String(url || '').trim();
    if (target) {
      window.location.href = target;
    }
  }

  function handleOverlayAction(action) {
    switch (String(action || '').trim()) {
      case 'start-run':
        startRun();
        break;
      case 'restart-run':
        restartRun();
        break;
      case 'open-detail':
        goTo(boot.detailUrl || '/games/goldminer');
        break;
      case 'skip-upgrade':
        beginRound(state.round + 1, true);
        break;
      default:
        break;
    }
  }

  function createItem(typeKey, x, y) {
    const preset = ITEM_PRESETS[typeKey];
    const radius = randomRange(preset.minRadius, preset.maxRadius);
    return {
      id: 'gold-' + (++state.itemSeq),
      typeKey,
      kind: preset.kind,
      label: preset.label,
      radius,
      x,
      y,
      value: randomInt(preset.minValue, preset.maxValue),
      weight: randomRange(preset.minWeight, preset.maxWeight),
      rotation: randomRange(-0.35, 0.35)
    };
  }

  function spawnPlanForRound(round) {
    const plan = [];
    function repeat(typeKey, count) {
      for (let index = 0; index < count; index += 1) {
        plan.push(typeKey);
      }
    }
    repeat('goldLarge', 1 + Math.floor(round / 2));
    repeat('rockLarge', 1 + Math.floor(round / 2));
    repeat('goldMedium', 2 + round);
    repeat('rockSmall', 3 + round);
    repeat('goldSmall', 4 + round);
    repeat('diamond', 1 + Math.floor(round / 2));
    repeat('bag', 1 + Math.floor((round + 1) / 2));
    return plan;
  }

  function buildRoundItems(round) {
    const plan = spawnPlanForRound(round);
    const items = [];
    const horizontalPadding = 72;
    const bottomPadding = 54;

    plan.forEach((typeKey) => {
      const preset = ITEM_PRESETS[typeKey];
      let placed = null;
      for (let attempt = 0; attempt < 180; attempt += 1) {
        const radius = preset.maxRadius;
        let y = randomRange(188 + radius, VIEW.height - bottomPadding - radius);
        if (typeKey === 'diamond') {
          y -= 36;
        } else if (typeKey === 'goldLarge' || typeKey === 'rockLarge') {
          y += 42;
        }
        const x = randomRange(horizontalPadding + radius, VIEW.width - horizontalPadding - radius);
        const candidate = createItem(typeKey, x, clamp(y, 188 + radius, VIEW.height - bottomPadding - radius));
        const overlaps = items.some((item) => {
          const dx = item.x - candidate.x;
          const dy = item.y - candidate.y;
          const distance = Math.sqrt((dx * dx) + (dy * dy));
          return distance < (item.radius + candidate.radius + 18);
        });
        if (!overlaps) {
          placed = candidate;
          break;
        }
      }
      items.push(placed || createItem(typeKey, randomRange(120, 840), randomRange(220, 520)));
    });

    return items;
  }

  function createTimedBombItem() {
    const preset = ITEM_PRESETS.bomb;
    const horizontalPadding = 72;
    const topPadding = 208;
    const bottomPadding = 54;

    for (let attempt = 0; attempt < 180; attempt += 1) {
      const radius = preset.maxRadius;
      const x = randomRange(horizontalPadding + radius, VIEW.width - horizontalPadding - radius);
      const y = randomRange(topPadding + radius, VIEW.height - bottomPadding - radius);
      const candidate = createItem('bomb', x, y);
      const overlaps = state.items.some((item) => {
        const dx = item.x - candidate.x;
        const dy = item.y - candidate.y;
        const distance = Math.sqrt((dx * dx) + (dy * dy));
        return distance < (item.radius + candidate.radius + 24);
      });
      if (!overlaps) {
        return candidate;
      }
    }

    return createItem('bomb', randomRange(120, 840), randomRange(236, 520));
  }

  function spawnTimedBomb() {
    const bomb = createTimedBombItem();
    state.items.push(bomb);
    createPopup('Bom moi', bomb.x, bomb.y - (bomb.radius + 10), '#ff9f68');
    createSparkBurst(bomb.x, bomb.y, '#ff9f68');
    pushLog('Bom song xuat hien', 'Co 1 qua bom moi trong mo. Tranh moc vao qua gan loot.', 'bad');
  }

  function beginRound(round, keepScore) {
    if (!keepScore) {
      state.score = 0;
      state.inventory.bombs = BASE_BOMBS;
      state.modifiers.pullMultiplier = 1;
      state.modifiers.extendMultiplier = 1;
      state.modifiers.goldBonus = 0;
      state.modifiers.diamondBonus = 0;
      state.modifiers.bagLuck = 0;
      state.modifiers.timeBonus = 0;
      state.resultCommitted = false;
    }

    clearRoundVisuals();
    state.round = round;
    state.target = calcTarget(round);
    state.timeLeft = calcRoundTime(round);
    state.items = buildRoundItems(round);
    state.bombSpawnElapsed = 0;
    state.roundSuccess = state.score >= state.target;
    state.roundEndPending = false;
    state.noticeText = 'Nhan Space hoac bam vao canvas de tha moc.';
    state.status = 'playing';
    state.runStarted = true;
    resetTool();
    setOverlay(null);
    pushLog('Vao vong ' + round, 'Target: ' + formatNumber(state.target) + ' diem.', 'good');
    refreshUi();
  }

  function preparePreviewRound() {
    clearRoundVisuals();
    state.round = 1;
    state.score = 0;
    state.target = calcTarget(1);
    state.timeLeft = calcRoundTime(1);
    state.items = buildRoundItems(1);
    state.bombSpawnElapsed = 0;
    state.roundSuccess = false;
    state.roundEndPending = false;
    state.noticeText = 'Nhan Space hoac bam vao canvas de tha moc.';
    state.status = 'ready';
    state.runStarted = false;
    state.inventory.bombs = BASE_BOMBS;
    state.resultCommitted = false;
    state.logs = [];
    resetTool();
    renderLog();
    showReadyOverlay();
    refreshUi();
  }

  function restartRun() {
    preparePreviewRound();
    startRun();
  }

  function startRun() {
    if (state.status === 'paused') {
      resumeRun();
      return;
    }
    beginRound(1, false);
  }

  function pauseRun() {
    if (state.status !== 'playing') {
      return;
    }
    state.status = 'paused';
    state.noticeText = 'Run dang tam dung.';
    refreshUi();
  }

  function resumeRun() {
    if (state.status !== 'paused') {
      return;
    }
    state.status = 'playing';
    state.noticeText = 'Run tiep tuc. Bam de tha moc.';
    refreshUi();
  }

  function togglePause() {
    if (state.status === 'playing') {
      pauseRun();
    } else if (state.status === 'paused') {
      resumeRun();
    }
  }

  function activeLootCount() {
    return state.items.filter((item) => item.kind !== 'bomb').length;
  }

  function activeBombCount() {
    return state.items.filter((item) => item.kind === 'bomb').length;
  }

  function recordRunResult(success) {
    if (state.resultCommitted) {
      return;
    }
    state.resultCommitted = true;
    state.stats.totalRuns += 1;
    state.stats.bestScore = Math.max(state.stats.bestScore, Math.round(state.score));
    state.stats.bestRound = Math.max(state.stats.bestRound, state.round);
    if (success) {
      state.stats.wins += 1;
    }
    writeStats();
  }

  function finishRound() {
    if (state.status !== 'playing') {
      return;
    }

    if (state.score >= state.target) {
      state.roundSuccess = true;
      if (state.round >= MAX_ROUNDS) {
        state.status = 'over';
        state.noticeText = 'Ban da hoan tat run.';
        recordRunResult(true);
        showWinOverlay();
      } else {
        state.status = 'shop';
        state.noticeText = 'Qua vong. Chon upgrade cho vong tiep theo.';
        showShopOverlay();
      }
      refreshUi();
      return;
    }

    state.status = 'over';
    state.noticeText = 'Het gio va chua dat target.';
    recordRunResult(false);
    showLoseOverlay();
    refreshUi();
  }

  function distanceToSegment(px, py, x1, y1, x2, y2) {
    const dx = x2 - x1;
    const dy = y2 - y1;
    const lengthSquared = (dx * dx) + (dy * dy);
    if (lengthSquared === 0) {
      const simpleDx = px - x1;
      const simpleDy = py - y1;
      return Math.sqrt((simpleDx * simpleDx) + (simpleDy * simpleDy));
    }
    const t = clamp((((px - x1) * dx) + ((py - y1) * dy)) / lengthSquared, 0, 1);
    const closestX = x1 + (dx * t);
    const closestY = y1 + (dy * t);
    const distX = px - closestX;
    const distY = py - closestY;
    return Math.sqrt((distX * distX) + (distY * distY));
  }

  function findHitItem(prevTip, nextTip) {
    let best = null;
    let bestDistance = Number.POSITIVE_INFINITY;

    state.items.forEach((item) => {
      const distance = distanceToSegment(item.x, item.y, prevTip.x, prevTip.y, nextTip.x, nextTip.y);
      const threshold = item.radius + 11;
      if (distance > threshold) {
        return;
      }
      const tipDistance = Math.hypot(nextTip.x - item.x, nextTip.y - item.y);
      if (tipDistance < bestDistance) {
        best = item;
        bestDistance = tipDistance;
      }
    });

    return best;
  }

  function findTriggeredBomb(prevTip, nextTip, excludedId) {
    let best = null;
    let bestDistance = Number.POSITIVE_INFINITY;

    state.items.forEach((item) => {
      if (item.kind !== 'bomb' || item.id === excludedId) {
        return;
      }
      const distance = distanceToSegment(item.x, item.y, prevTip.x, prevTip.y, nextTip.x, nextTip.y);
      const threshold = item.radius + 10;
      if (distance > threshold) {
        return;
      }
      const tipDistance = Math.hypot(nextTip.x - item.x, nextTip.y - item.y);
      if (tipDistance < bestDistance) {
        best = item;
        bestDistance = tipDistance;
      }
    });

    return best;
  }

  function hookOutOfBounds(tip) {
    return tip.x < 28 || tip.x > (VIEW.width - 28) || tip.y > (VIEW.height - 14);
  }

  function currentPullSpeed(item) {
    if (!item) {
      return 980 * state.modifiers.extendMultiplier;
    }
    return clamp((540 / item.weight) * state.modifiers.pullMultiplier, 130, 430);
  }

  function launchHook() {
    if (state.status === 'ready') {
      startRun();
      return;
    }
    if (state.status !== 'playing' || state.tool.phase !== 'aiming') {
      return;
    }
    state.tool.phase = 'extending';
    state.noticeText = 'Dang tha moc...';
  }

  function createPopup(text, x, y, color) {
    state.popups.push({
      text,
      x,
      y,
      color: color || '#fff4d9',
      life: 1.2
    });
  }

  function createSparkBurst(x, y, color) {
    for (let index = 0; index < 16; index += 1) {
      const angle = randomRange(0, Math.PI * 2);
      const speed = randomRange(24, 96);
      state.sparks.push({
        x,
        y,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed,
        color,
        life: randomRange(0.28, 0.7),
        radius: randomRange(2, 5)
      });
    }
  }

  function computeItemScore(item) {
    let value = item.value;
    if (item.kind === 'gold') {
      value *= (1 + state.modifiers.goldBonus);
    }
    if (item.kind === 'diamond') {
      value *= (1 + state.modifiers.diamondBonus);
    }
    if (item.kind === 'bag') {
      value += (state.modifiers.bagLuck * randomInt(35, 90));
    }
    return Math.round(value);
  }

  function finishCollection(item) {
    if (item.kind === 'bomb') {
      pushLog('Cham bom song', 'Bom no truoc khi keo ve mat dat.', 'bad');
      state.noticeText = 'Bom da no trong mo.';
      return;
    }

    const value = computeItemScore(item);
    state.score += value;
    createPopup('+' + formatNumber(value), VIEW.anchorX + randomRange(-24, 24), VIEW.anchorY + 10, '#ffe498');
    createSparkBurst(item.x, item.y, item.kind === 'diamond' ? '#82ecff' : '#ffd166');
    pushLog('Keo ve ' + item.label, '+' + formatNumber(value) + ' diem.', 'good');
    state.noticeText = 'Vua keo duoc ' + item.label.toLowerCase() + '.';

    if (!state.roundSuccess && state.score >= state.target) {
      state.roundSuccess = true;
      createPopup('Target clear', VIEW.anchorX - 12, VIEW.anchorY - 28, '#7ef5d7');
      pushLog('Vuot target', 'Ban da dat moc diem cua vong ' + state.round + '.', 'good');
      state.noticeText = 'Da vuot target. Neu con thoi gian, tiep tuc gom them loot.';
    }
  }

  function useBomb() {
    if (state.status !== 'playing') {
      return;
    }
    const attached = getAttachedItem();
    if (!attached || state.tool.phase !== 'pulling') {
      state.noticeText = 'Chi nem TNT khi moc dang keo vat pham.';
      refreshUi();
      return;
    }
    if (state.inventory.bombs <= 0) {
      state.noticeText = 'Da het TNT.';
      refreshUi();
      return;
    }

    state.inventory.bombs -= 1;
    createPopup('TNT', attached.x, attached.y - 12, '#ff9f68');
    createSparkBurst(attached.x, attached.y, '#ff9f68');
    pushLog('Dung TNT', 'Da cat ' + attached.label.toLowerCase() + ' de giu nhip run.', 'bad');
    state.noticeText = 'Da cat cu keo bang TNT.';
    state.tool.attachedId = '';
    state.tool.phase = 'pulling';
    state.items = state.items.filter((item) => item.id !== attached.id);
    refreshUi();
  }

  function hasLootRemaining() {
    return state.items.some((item) => item.kind !== 'bomb');
  }

  function detonateTimedBomb(bomb) {
    if (!bomb) {
      return;
    }

    const blastRadius = TIMED_BOMB_BLAST_RADIUS + bomb.radius;
    const removedItems = [];

    state.items.forEach((item) => {
      const dx = item.x - bomb.x;
      const dy = item.y - bomb.y;
      const distance = Math.sqrt((dx * dx) + (dy * dy));
      if (item.id === bomb.id || distance <= (blastRadius + item.radius)) {
        removedItems.push(item);
      }
    });

    const removedIds = new Set(removedItems.map((item) => item.id));
    const clearedResources = removedItems.filter((item) => item.id !== bomb.id && item.kind !== 'bomb').length;
    const clearedBombs = removedItems.filter((item) => item.id !== bomb.id && item.kind === 'bomb').length;

    state.items = state.items.filter((item) => !removedIds.has(item.id));
    state.tool.attachedId = '';
    state.tool.phase = 'pulling';

    createPopup('BOOM!', bomb.x, bomb.y - 10, '#ff8f66');
    createSparkBurst(bomb.x, bomb.y, '#ff8f66');
    createSparkBurst(bomb.x, bomb.y, '#ffd166');

    if (clearedResources > 0 || clearedBombs > 0) {
      const detailParts = [];
      if (clearedResources > 0) {
        detailParts.push('xoa ' + clearedResources + ' tai nguyen');
      }
      if (clearedBombs > 0) {
        detailParts.push('quet them ' + clearedBombs + ' bom');
      }
      pushLog('Bom phat no', detailParts.join(', ') + ' quanh vi tri va cham.', 'bad');
    } else {
      pushLog('Bom phat no', 'Khong co tai nguyen nao nam trong tam no.', 'bad');
    }

    state.noticeText = clearedResources > 0
      ? ('Bom no va cuon mat ' + clearedResources + ' tai nguyen gan do.')
      : 'Bom no nhung khong co loot nao bi cuon mat.';
  }

  function updateTool(dt) {
    const tool = state.tool;

    if (tool.phase === 'aiming') {
      tool.angle += (tool.swingDirection * tool.swingSpeed * dt);
      if (tool.angle <= VIEW.minAngle) {
        tool.angle = VIEW.minAngle;
        tool.swingDirection = 1;
      } else if (tool.angle >= VIEW.maxAngle) {
        tool.angle = VIEW.maxAngle;
        tool.swingDirection = -1;
      }
      return;
    }

    const previousTip = hookTipAt(tool.length);

    if (tool.phase === 'extending') {
      tool.length += (780 * state.modifiers.extendMultiplier * dt);
      const nextTip = hookTipAt(tool.length);
      const hit = findHitItem(previousTip, nextTip);
      if (hit) {
        if (hit.kind === 'bomb') {
          detonateTimedBomb(hit);
          return;
        }
        tool.attachedId = hit.id;
        tool.phase = 'pulling';
        hit.x = nextTip.x;
        hit.y = nextTip.y;
        state.noticeText = 'Dang keo ' + hit.label.toLowerCase() + '...';
        return;
      }

      if (hookOutOfBounds(nextTip)) {
        tool.phase = 'pulling';
      }
      return;
    }

    if (tool.phase === 'pulling') {
      const attached = getAttachedItem();
      const pullSpeed = currentPullSpeed(attached);
      tool.length -= (pullSpeed * dt);
      if (tool.length <= VIEW.minLength) {
        tool.length = VIEW.minLength;
        if (attached) {
          finishCollection(attached);
          state.items = state.items.filter((item) => item.id !== attached.id);
        }
        tool.attachedId = '';
        tool.phase = 'aiming';
        if (state.roundEndPending || !hasLootRemaining()) {
          finishRound();
        }
        return;
      }

      const tip = hookTipAt(tool.length);
      if (attached) {
        attached.x = tip.x;
        attached.y = tip.y;
      }

      const triggeredBomb = findTriggeredBomb(previousTip, tip, attached ? attached.id : '');
      if (triggeredBomb) {
        detonateTimedBomb(triggeredBomb);
      }
    }
  }

  function updateTimer(dt) {
    if (state.status !== 'playing') {
      return;
    }
    state.timeLeft = Math.max(0, state.timeLeft - dt);
    if (state.timeLeft > 0) {
      return;
    }
    if (state.tool.phase === 'aiming') {
      finishRound();
      return;
    }
    state.roundEndPending = true;
    state.noticeText = 'Het gio. Cho cu keo hien tai ket thuc.';
  }

  function updateVisualEffects(dt) {
    state.popups = state.popups.filter((popup) => {
      popup.y -= (42 * dt);
      popup.life -= dt;
      return popup.life > 0;
    });

    state.sparks = state.sparks.filter((spark) => {
      spark.x += spark.vx * dt;
      spark.y += spark.vy * dt;
      spark.vx *= 0.96;
      spark.vy = (spark.vy * 0.96) + (36 * dt);
      spark.life -= dt;
      return spark.life > 0;
    });
  }

  function updateBombSpawner(dt) {
    if (state.status !== 'playing' || state.timeLeft <= 0) {
      return;
    }

    state.bombSpawnElapsed += dt;
    while (state.bombSpawnElapsed >= TIMED_BOMB_INTERVAL) {
      state.bombSpawnElapsed -= TIMED_BOMB_INTERVAL;
      spawnTimedBomb();
    }
  }

  function nextBombCountdownSeconds() {
    if (state.status === 'over' || state.status === 'shop') {
      return null;
    }
    const remaining = TIMED_BOMB_INTERVAL - state.bombSpawnElapsed;
    return clamp(Math.ceil(Math.max(0.01, remaining)), 1, TIMED_BOMB_INTERVAL);
  }

  function syncHazardUi() {
    const bombCount = activeBombCount();
    const countdown = nextBombCountdownSeconds();
    const countdownLabel = countdown == null ? '--' : (countdown + 's');

    if (refs.bombCountdown) {
      refs.bombCountdown.textContent = countdownLabel;
    }
    if (refs.hazardCount) {
      refs.hazardCount.textContent = String(bombCount);
    }
    if (refs.hazardActive) {
      refs.hazardActive.textContent = bombCount + ' bom tren map';
    }
    if (!refs.hazardPanel) {
      return;
    }

    let level = 'calm';
    let title = 'Bom song dang cho kich hoat';
    let text = 'Vao round de bat dau dem 10 giay. Khi hook cham bom, no se xoa loot trong vung no.';

    if (state.status === 'paused') {
      level = 'warning';
      title = 'Run dang tam dung';
      text = 'Dong ho spawn bom dang dung. Tiep tuc run de hazard quay lai nhan nhip.';
    } else if (state.status === 'playing') {
      const imminent = countdown != null && countdown <= 3;
      if (bombCount >= 3 || imminent) {
        level = 'danger';
      } else if (bombCount > 0 || (countdown != null && countdown <= 6)) {
        level = 'warning';
      }

      if (bombCount <= 0) {
        title = 'Map tam thoi an toan';
        text = 'Qua bom song tiep theo se roi xuong sau ' + countdownLabel + '.';
      } else if (imminent) {
        title = 'Bom moi sap roi xuong';
        text = 'Tren map dang co ' + bombCount + ' bom song. Qua tiep theo den sau ' + countdownLabel + '.';
      } else {
        title = 'Co bom song trong mo';
        text = 'Hien co ' + bombCount + ' bom tren map. Trach cac goc moc di xuyen qua vung loot day.';
      }
    } else if (state.status === 'over') {
      level = 'calm';
      title = 'Run da ket thuc';
      text = 'Bom song da dung spawn. Ban co the chon choi lai de vao mot run moi.';
    } else if (state.status === 'shop') {
      level = 'calm';
      title = 'Dang nghi giua vong';
      text = 'Bomb timer da tam dung trong cua hang. Chon nang cap de vao round tiep theo.';
    }

    refs.hazardPanel.dataset.level = level;
    if (refs.hazardTitle) {
      refs.hazardTitle.textContent = title;
    }
    if (refs.hazardText) {
      refs.hazardText.textContent = text;
    }
  }

  function refreshUi() {
    refs.round.textContent = String(state.round);
    refs.score.textContent = formatNumber(state.score);
    refs.target.textContent = formatNumber(state.target);
    refs.time.textContent = Math.ceil(state.timeLeft) + 's';
    refs.bombs.textContent = String(state.inventory.bombs);
    refs.bestScore.textContent = formatNumber(state.stats.bestScore);
    refs.bestRound.textContent = String(state.stats.bestRound);
    refs.totalRuns.textContent = String(state.stats.totalRuns);
    refs.wins.textContent = String(state.stats.wins);
    refs.remaining.textContent = activeLootCount() + ' vat pham';

    const missing = Math.max(0, state.target - state.score);
    refs.goalLabel.textContent = missing > 0
      ? 'Con ' + formatNumber(missing) + ' diem de qua vong.'
      : 'Da vuot target. Tiep tuc gom them loot neu con thoi gian.';
    refs.statusLine.textContent = state.noticeText;

    const progress = state.target > 0 ? clamp((state.score / state.target) * 100, 0, 100) : 0;
    refs.progressFill.style.width = progress + '%';

    const canResume = state.status === 'paused';
    const canPause = state.status === 'playing';
    const canBomb = state.status === 'playing'
      && state.tool.phase === 'pulling'
      && !!state.tool.attachedId
      && state.inventory.bombs > 0;

    refs.startBtn.querySelector('span').textContent = canResume ? 'Tiep tuc' : 'Bat dau';
    refs.heroStartBtn.querySelector('span').textContent = canResume ? 'Tiep tuc run' : 'Bat dau run';
    refs.pauseBtn.querySelector('span').textContent = 'Tam dung';
    refs.pauseBtn.disabled = !(canPause || canResume);
    refs.bombBtn.disabled = !canBomb;
    syncHazardUi();
  }

  function drawBackground() {
    const sky = ctx.createLinearGradient(0, 0, 0, 180);
    sky.addColorStop(0, '#26160d');
    sky.addColorStop(1, '#5c3515');
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, VIEW.width, 118);

    const cave = ctx.createLinearGradient(0, 118, 0, VIEW.height);
    cave.addColorStop(0, '#6a3d18');
    cave.addColorStop(0.36, '#4b2a14');
    cave.addColorStop(1, '#1b120d');
    ctx.fillStyle = cave;
    ctx.fillRect(0, 118, VIEW.width, VIEW.height - 118);

    ctx.fillStyle = '#2c1809';
    ctx.fillRect(0, 0, VIEW.width, 96);
    ctx.fillStyle = '#8d5a28';
    ctx.fillRect(118, 72, 724, 22);
    ctx.fillRect(170, 92, 18, 74);
    ctx.fillRect(772, 92, 18, 74);

    ctx.save();
    ctx.translate(VIEW.anchorX, 90);
    ctx.fillStyle = '#fed472';
    ctx.beginPath();
    ctx.arc(0, 0, 24, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#efaa42';
    ctx.beginPath();
    ctx.arc(0, 0, 15, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();

    ctx.fillStyle = 'rgba(255, 194, 73, 0.12)';
    ctx.beginPath();
    ctx.arc(230, 194, 90, 0, Math.PI * 2);
    ctx.fill();
    ctx.beginPath();
    ctx.arc(706, 220, 118, 0, Math.PI * 2);
    ctx.fill();

    ctx.fillStyle = '#3b2410';
    ctx.beginPath();
    ctx.moveTo(0, 192);
    ctx.bezierCurveTo(134, 158, 214, 232, 350, 208);
    ctx.bezierCurveTo(490, 184, 624, 148, 960, 180);
    ctx.lineTo(960, VIEW.height);
    ctx.lineTo(0, VIEW.height);
    ctx.closePath();
    ctx.fill();

    ctx.fillStyle = '#5b3417';
    ctx.beginPath();
    ctx.moveTo(0, 284);
    ctx.bezierCurveTo(152, 240, 272, 314, 382, 288);
    ctx.bezierCurveTo(518, 254, 604, 226, 960, 246);
    ctx.lineTo(960, VIEW.height);
    ctx.lineTo(0, VIEW.height);
    ctx.closePath();
    ctx.fill();

    ctx.fillStyle = '#201108';
    ctx.fillRect(0, VIEW.height - 44, VIEW.width, 44);
    ctx.fillStyle = '#8d5a28';
    ctx.fillRect(112, VIEW.height - 38, 160, 8);
    ctx.fillRect(690, VIEW.height - 38, 182, 8);
  }

  function drawGold(item) {
    ctx.save();
    ctx.translate(item.x, item.y);
    ctx.rotate(item.rotation);
    const gradient = ctx.createRadialGradient(-item.radius * 0.3, -item.radius * 0.4, item.radius * 0.2, 0, 0, item.radius);
    gradient.addColorStop(0, '#fff3c4');
    gradient.addColorStop(0.35, '#f2c562');
    gradient.addColorStop(1, '#c1771d');
    ctx.fillStyle = gradient;
    ctx.beginPath();
    ctx.arc(0, 0, item.radius, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = 'rgba(255, 245, 213, 0.42)';
    ctx.beginPath();
    ctx.arc(-item.radius * 0.28, -item.radius * 0.32, item.radius * 0.32, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  function drawRock(item) {
    ctx.save();
    ctx.translate(item.x, item.y);
    ctx.rotate(item.rotation);
    ctx.fillStyle = '#8b919c';
    ctx.beginPath();
    ctx.moveTo(-item.radius * 0.9, -item.radius * 0.1);
    ctx.lineTo(-item.radius * 0.3, -item.radius);
    ctx.lineTo(item.radius * 0.55, -item.radius * 0.72);
    ctx.lineTo(item.radius, item.radius * 0.12);
    ctx.lineTo(item.radius * 0.26, item.radius);
    ctx.lineTo(-item.radius * 0.72, item.radius * 0.64);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = '#b9bec8';
    ctx.lineWidth = 3;
    ctx.stroke();
    ctx.restore();
  }

  function drawDiamond(item) {
    ctx.save();
    ctx.translate(item.x, item.y);
    ctx.rotate(item.rotation);
    ctx.fillStyle = '#73e3ff';
    ctx.beginPath();
    ctx.moveTo(0, -item.radius);
    ctx.lineTo(item.radius * 0.9, -item.radius * 0.2);
    ctx.lineTo(item.radius * 0.45, item.radius);
    ctx.lineTo(-item.radius * 0.45, item.radius);
    ctx.lineTo(-item.radius * 0.9, -item.radius * 0.2);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = '#e3fbff';
    ctx.lineWidth = 2.5;
    ctx.stroke();
    ctx.restore();
  }

  function drawBag(item) {
    ctx.save();
    ctx.translate(item.x, item.y);
    ctx.rotate(item.rotation);
    ctx.fillStyle = '#7a4d2b';
    ctx.beginPath();
    ctx.moveTo(-item.radius * 0.86, -item.radius * 0.16);
    ctx.quadraticCurveTo(-item.radius * 0.7, -item.radius * 1.05, 0, -item.radius * 0.8);
    ctx.quadraticCurveTo(item.radius * 0.72, -item.radius * 1.02, item.radius * 0.86, -item.radius * 0.1);
    ctx.lineTo(item.radius * 0.62, item.radius);
    ctx.lineTo(-item.radius * 0.62, item.radius);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = '#dcb57b';
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.moveTo(-item.radius * 0.32, -item.radius * 0.5);
    ctx.lineTo(item.radius * 0.28, -item.radius * 0.5);
    ctx.stroke();
    ctx.restore();
  }

  function drawBomb(item) {
    const pulse = 0.5 + (0.5 * Math.sin(((state.lastFrameAt || 0) / 180) + (item.id.length * 0.3)));

    ctx.save();
    ctx.translate(item.x, item.y);
    ctx.rotate(item.rotation);

    ctx.save();
    ctx.globalAlpha = 0.18 + (pulse * 0.16);
    ctx.strokeStyle = '#ff6f57';
    ctx.lineWidth = 2;
    ctx.setLineDash([8, 6]);
    ctx.beginPath();
    ctx.arc(0, 0, item.radius + 8 + (pulse * 4), 0, Math.PI * 2);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.restore();

    const gradient = ctx.createRadialGradient(-item.radius * 0.28, -item.radius * 0.34, item.radius * 0.18, 0, 0, item.radius * 1.1);
    gradient.addColorStop(0, '#8f949c');
    gradient.addColorStop(0.42, '#343841');
    gradient.addColorStop(1, '#101318');
    ctx.fillStyle = gradient;
    ctx.beginPath();
    ctx.arc(0, 0, item.radius, 0, Math.PI * 2);
    ctx.fill();

    ctx.strokeStyle = 'rgba(255, 255, 255, 0.16)';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(-item.radius * 0.18, -item.radius * 0.22, item.radius * 0.42, Math.PI * 1.16, Math.PI * 1.9);
    ctx.stroke();

    ctx.strokeStyle = '#d9b370';
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.moveTo(item.radius * 0.2, -item.radius * 0.62);
    ctx.quadraticCurveTo(item.radius * 0.74, -item.radius * 1.2, item.radius * 0.46, -item.radius * 1.52);
    ctx.stroke();

    ctx.fillStyle = pulse > 0.55 ? '#ffd35e' : '#ff7f50';
    ctx.beginPath();
    ctx.arc(item.radius * 0.46, -item.radius * 1.52, item.radius * 0.2, 0, Math.PI * 2);
    ctx.fill();

    ctx.restore();
  }

  function drawItems() {
    state.items.forEach((item) => {
      if (item.kind === 'gold') {
        drawGold(item);
      } else if (item.kind === 'rock') {
        drawRock(item);
      } else if (item.kind === 'diamond') {
        drawDiamond(item);
      } else if (item.kind === 'bomb') {
        drawBomb(item);
      } else {
        drawBag(item);
      }
    });
  }

  function drawHook() {
    const tip = hookTipAt(state.tool.length);

    ctx.save();
    ctx.strokeStyle = '#d2d7df';
    ctx.lineWidth = 4;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(VIEW.anchorX, VIEW.anchorY);
    ctx.lineTo(tip.x, tip.y);
    ctx.stroke();

    ctx.fillStyle = '#f4d38c';
    ctx.beginPath();
    ctx.arc(VIEW.anchorX, VIEW.anchorY, 9, 0, Math.PI * 2);
    ctx.fill();

    ctx.strokeStyle = '#e9eef5';
    ctx.lineWidth = 4.5;
    ctx.beginPath();
    ctx.moveTo(tip.x, tip.y);
    ctx.quadraticCurveTo(tip.x + 14, tip.y + 16, tip.x + 24, tip.y + 10);
    ctx.moveTo(tip.x, tip.y);
    ctx.quadraticCurveTo(tip.x - 14, tip.y + 16, tip.x - 24, tip.y + 10);
    ctx.stroke();

    ctx.fillStyle = '#fff2cd';
    ctx.beginPath();
    ctx.arc(tip.x, tip.y, 6, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  function drawPopups() {
    state.popups.forEach((popup) => {
      ctx.save();
      ctx.globalAlpha = clamp(popup.life / 1.2, 0, 1);
      ctx.fillStyle = popup.color;
      ctx.font = '700 22px Manrope, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(popup.text, popup.x, popup.y);
      ctx.restore();
    });

    state.sparks.forEach((spark) => {
      ctx.save();
      ctx.globalAlpha = clamp(spark.life / 0.7, 0, 1);
      ctx.fillStyle = spark.color;
      ctx.beginPath();
      ctx.arc(spark.x, spark.y, spark.radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    });
  }

  function drawHudHints() {
    ctx.save();
    ctx.font = '700 18px Manrope, sans-serif';
    ctx.fillStyle = 'rgba(255, 243, 212, 0.84)';
    ctx.textAlign = 'left';
    ctx.fillText('Round ' + state.round, 28, 142);
    ctx.textAlign = 'right';
    ctx.fillText('Target ' + formatNumber(state.target), VIEW.width - 28, 142);
    ctx.restore();
  }

  function drawScene() {
    ctx.clearRect(0, 0, VIEW.width, VIEW.height);
    drawBackground();
    drawItems();
    drawHook();
    drawHudHints();
    drawPopups();
  }

  function update(dt) {
    updateTimer(dt);
    updateTool(dt);
    updateBombSpawner(dt);
    updateVisualEffects(dt);
    refreshUi();
  }

  function loop(now) {
    if (!state.lastFrameAt) {
      state.lastFrameAt = now;
    }
    const dt = clamp((now - state.lastFrameAt) / 1000, 0, 0.033);
    state.lastFrameAt = now;

    if (state.status === 'playing') {
      update(dt);
    } else {
      updateVisualEffects(dt);
      refreshUi();
    }

    drawScene();
    window.requestAnimationFrame(loop);
  }

  function isTypingTarget(target) {
    return !!(target && target.closest && target.closest('input, textarea, select, [contenteditable="true"]'));
  }

  function handleKeyboard(event) {
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
    if (key === ' ' || key === 'arrowdown') {
      event.preventDefault();
      launchHook();
      return;
    }
    if (key === 'd' || key === 'b') {
      event.preventDefault();
      useBomb();
      return;
    }
    if (key === 'p') {
      event.preventDefault();
      togglePause();
      return;
    }
    if (key === 'r') {
      event.preventDefault();
      restartRun();
    }
  }

  function bindControls() {
    refs.canvas.addEventListener('pointerdown', () => {
      if (state.status === 'paused') {
        resumeRun();
        return;
      }
      launchHook();
    });

    refs.startBtn.addEventListener('click', startRun);
    refs.heroStartBtn.addEventListener('click', startRun);
    refs.pauseBtn.addEventListener('click', togglePause);
    refs.bombBtn.addEventListener('click', useBomb);
    refs.restartBtn.addEventListener('click', restartRun);

    refs.overlayPrimary.addEventListener('click', () => {
      handleOverlayAction(refs.overlayPrimary.dataset.action);
    });
    refs.overlaySecondary.addEventListener('click', () => {
      handleOverlayAction(refs.overlaySecondary.dataset.action);
    });
    refs.overlayChoices.addEventListener('click', (event) => {
      const button = event.target.closest('[data-upgrade-id]');
      if (!button) {
        return;
      }
      applyUpgradeAndAdvance(button.dataset.upgradeId);
    });

    document.addEventListener('keydown', handleKeyboard);
  }

  preparePreviewRound();
  bindControls();
  window.requestAnimationFrame(loop);
})();
