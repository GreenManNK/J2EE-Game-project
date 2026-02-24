(function () {
  const boot = window.TienLenBotBoot || {};

  const SUITS = [
    { code: 'S', symbol: '\u2660', red: false, order: 0 },
    { code: 'C', symbol: '\u2663', red: false, order: 1 },
    { code: 'D', symbol: '\u2666', red: true, order: 2 },
    { code: 'H', symbol: '\u2665', red: true, order: 3 }
  ];
  const RANK_VALUES = [3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15];
  const RANK_LABELS = new Map([
    [3, '3'], [4, '4'], [5, '5'], [6, '6'], [7, '7'], [8, '8'], [9, '9'],
    [10, '10'], [11, 'J'], [12, 'Q'], [13, 'K'], [14, 'A'], [15, '2']
  ]);
  const TYPE_ORDER = { SINGLE: 1, PAIR: 2, TRIPLE: 3, STRAIGHT: 4, FOUR_KIND: 5 };

  const state = {
    difficulty: normalizeDifficulty(boot.botDifficulty),
    players: [],
    selectedCodes: new Set(),
    started: false,
    gameOver: false,
    winnerId: null,
    currentTurnId: null,
    controlUserId: null,
    currentTrick: null,
    playCount: 0,
    passedIds: new Set(),
    message: '-',
    status: 'Dang khoi tao van...',
    moveLog: [],
    botTimer: null
  };

  const me = {
    id: String(boot.sessionUserId || 'guest-local').trim() || 'guest-local',
    name: String(boot.sessionDisplayName || 'Guest').trim() || 'Guest'
  };

  const els = {};

  document.addEventListener('DOMContentLoaded', () => {
    bindEls();
    bindActions();
    initPlayers();
    startNewGame();
  });

  window.addEventListener('beforeunload', () => {
    clearBotTimer();
  });

  function bindEls() {
    els.selfName = document.getElementById('tlBotSelfName');
    els.difficultyLabel = document.getElementById('tlBotDifficultyLabel');
    els.statusText = document.getElementById('tlBotStatusText');
    els.messageText = document.getElementById('tlBotMessageText');
    els.newGameBtn = document.getElementById('tlBotNewGameBtn');
    els.turnText = document.getElementById('tlBotTurnText');
    els.controlText = document.getElementById('tlBotControlText');
    els.currentTrickLabel = document.getElementById('tlBotCurrentTrickLabel');
    els.trickOwnerText = document.getElementById('tlBotTrickOwnerText');
    els.trickOwnerTextDup = document.getElementById('tlBotTrickOwnerTextDup');
    els.moveCount = document.getElementById('tlBotMoveCount');
    els.resultText = document.getElementById('tlBotResultText');
    els.playersBoard = document.getElementById('tlBotPlayersBoard');
    els.trickCards = document.getElementById('tlBotTrickCards');
    els.myHand = document.getElementById('tlBotMyHand');
    els.playBtn = document.getElementById('tlBotPlayBtn');
    els.passBtn = document.getElementById('tlBotPassBtn');
    els.clearBtn = document.getElementById('tlBotClearBtn');
    els.selectedCount = document.getElementById('tlBotSelectedCount');
    els.handCount = document.getElementById('tlBotHandCount');
    els.moveLog = document.getElementById('tlBotMoveLog');
  }

  function bindActions() {
    els.newGameBtn?.addEventListener('click', () => startNewGame());
    els.playBtn?.addEventListener('click', () => onHumanPlaySelected());
    els.passBtn?.addEventListener('click', () => onHumanPass());
    els.clearBtn?.addEventListener('click', () => {
      state.selectedCodes.clear();
      renderHand();
      updateActionButtons();
    });
  }

  function initPlayers() {
    state.players = [
      { id: me.id, name: me.name, isHuman: true, hand: [] },
      { id: 'bot-1', name: 'Bot 1', isHuman: false, hand: [] },
      { id: 'bot-2', name: 'Bot 2', isHuman: false, hand: [] },
      { id: 'bot-3', name: 'Bot 3', isHuman: false, hand: [] }
    ];
    if (els.selfName) {
      els.selfName.textContent = me.name;
    }
    if (els.difficultyLabel) {
      els.difficultyLabel.textContent = state.difficulty === 'hard' ? 'Hard' : 'Easy';
    }
  }

  function startNewGame() {
    clearBotTimer();

    const deck = standardDeck();
    shuffleInPlace(deck);
    state.players.forEach((p) => {
      p.hand = [];
    });
    for (let i = 0; i < deck.length; i++) {
      state.players[i % state.players.length].hand.push(deck[i]);
    }
    state.players.forEach((p) => p.hand.sort(compareCards));

    state.started = true;
    state.gameOver = false;
    state.winnerId = null;
    state.currentTrick = null;
    state.playCount = 0;
    state.passedIds.clear();
    state.selectedCodes.clear();
    state.moveLog = [];

    const firstPlayer = state.players.find((p) => p.hand.some((c) => c.code === '3S')) || state.players[0];
    state.currentTurnId = firstPlayer.id;
    state.controlUserId = firstPlayer.id;
    setStatus('Van moi da bat dau');
    setMessage(firstPlayer.name + ' giu 3S va danh truoc');
    pushMoveLog('Van moi bat dau. ' + firstPlayer.name + ' giu 3S.');

    renderAll();
    scheduleBotTurnIfNeeded();
  }

  function onHumanPlaySelected() {
    const player = getCurrentPlayer();
    if (!player || !player.isHuman) {
      return;
    }
    const selected = player.hand.filter((card) => state.selectedCodes.has(card.code));
    const result = attemptPlay(player, selected);
    if (!result.ok) {
      setMessage(result.error || 'Nuoc di khong hop le');
      renderAll();
      return;
    }
    state.selectedCodes.clear();
    renderAll();
    scheduleBotTurnIfNeeded();
  }

  function onHumanPass() {
    const player = getCurrentPlayer();
    if (!player || !player.isHuman) {
      return;
    }
    const result = attemptPass(player);
    if (!result.ok) {
      setMessage(result.error || 'Khong the bo luot');
      renderAll();
      return;
    }
    renderAll();
    scheduleBotTurnIfNeeded();
  }

  function scheduleBotTurnIfNeeded() {
    clearBotTimer();
    if (!state.started || state.gameOver) {
      return;
    }
    const player = getCurrentPlayer();
    if (!player || player.isHuman) {
      updateActionButtons();
      return;
    }
    updateActionButtons();
    const delayMs = Math.max(250, Number(boot.botDelayMs || 650));
    state.botTimer = window.setTimeout(() => {
      state.botTimer = null;
      runBotTurn();
    }, delayMs);
  }

  function runBotTurn() {
    if (state.gameOver || !state.started) {
      return;
    }
    const player = getCurrentPlayer();
    if (!player || player.isHuman) {
      return;
    }

    const legalMoves = getLegalMoves(player);
    const canPass = canCurrentPlayerPass(player);

    if (legalMoves.length === 0) {
      if (canPass) {
        attemptPass(player);
      } else {
        setMessage(player.name + ' khong co nuoc hop le de mo vong (logic fallback)');
      }
      renderAll();
      scheduleBotTurnIfNeeded();
      return;
    }

    const chosen = chooseBotMove(player, legalMoves);
    attemptPlay(player, chosen.cards);
    renderAll();
    scheduleBotTurnIfNeeded();
  }

  function chooseBotMove(player, legalMoves) {
    if (state.difficulty === 'hard') {
      let best = legalMoves[0];
      let bestScore = -Infinity;
      for (const move of legalMoves) {
        const score = scoreHardMove(player, move);
        if (score > bestScore) {
          best = move;
          bestScore = score;
        }
      }
      return best;
    }

    const sorted = legalMoves.slice().sort(compareMoveStrengthAsc);
    const poolSize = Math.min(sorted.length, 3);
    const idx = Math.floor(Math.random() * poolSize);
    return sorted[idx];
  }

  function scoreHardMove(player, move) {
    const combo = move.combo;
    const handCount = player.hand.length;
    const remaining = handCount - combo.cards.length;
    let score = 0;

    if (remaining === 0) {
      score += 100000;
    }

    if (state.currentTrick) {
      const target = state.currentTrick.combo;
      const excessRank = combo.highestRank - target.highestRank;
      const excessSuit = combo.highestSuit - target.highestSuit;
      score -= excessRank * 18 + excessSuit * 3;
      score += combo.cards.length * 20;
    } else {
      score += combo.cards.length * 35;
      if (combo.type === 'STRAIGHT') {
        score += combo.length * 8;
      }
      score -= combo.highestRank * 4;
      score -= combo.highestSuit;
    }

    if (combo.highestRank === 15 && remaining > 0) {
      score -= 70;
    }
    if (combo.type === 'FOUR_KIND' && remaining > 0 && handCount > 5) {
      score -= 120;
    }
    if (combo.type === 'PAIR' && combo.highestRank >= 14 && handCount > 4) {
      score -= 30;
    }
    if (remaining <= 3) {
      score += 60;
    }

    score += scoreForBreakingHandShape(player, combo);
    return score;
  }

  function scoreForBreakingHandShape(player, combo) {
    // Heuristic: avoid breaking many same-rank groups early unless it significantly reduces cards.
    const counts = countByRank(player.hand);
    let penalty = 0;
    for (const card of combo.cards) {
      const count = counts.get(card.rankValue) || 0;
      if (count >= 3 && combo.type === 'SINGLE' && player.hand.length > 4) {
        penalty += 6;
      }
      if (count === 2 && combo.type === 'SINGLE') {
        penalty += 3;
      }
    }
    return -penalty;
  }

  function attemptPlay(player, cards) {
    if (!player) {
      return { ok: false, error: 'Khong tim thay nguoi choi' };
    }
    if (!state.started || state.gameOver) {
      return { ok: false, error: 'Van dau khong hoat dong' };
    }
    if (state.currentTurnId !== player.id) {
      return { ok: false, error: 'Chua den luot' };
    }
    if (!Array.isArray(cards) || cards.length === 0) {
      return { ok: false, error: 'Phai chon it nhat 1 la bai' };
    }

    const selected = resolveSelectionFromHand(player.hand, cards.map((c) => c.code || c));
    if (!selected) {
      return { ok: false, error: 'La bai khong hop le' };
    }
    const combo = parseCombination(selected);
    if (!combo) {
      return { ok: false, error: 'Bo bai khong hop le (MVP: don, doi, sam, tu quy, sanh)' };
    }
    if (state.playCount === 0 && !selected.some((c) => c.code === '3S')) {
      return { ok: false, error: 'Nuoc dau tien phai chua 3S' };
    }
    if (state.currentTrick && !comboCanBeat(combo, state.currentTrick.combo)) {
      return { ok: false, error: 'Bo bai khong de hon bo bai hien tai' };
    }

    removeCardsFromHand(player.hand, selected);
    player.hand.sort(compareCards);

    state.currentTrick = {
      ownerId: player.id,
      ownerName: player.name,
      combo,
      cards: combo.cards.slice()
    };
    state.controlUserId = player.id;
    state.passedIds.clear();
    state.playCount += 1;
    pushMoveLog(player.name + ': ' + combo.label);

    if (player.hand.length === 0) {
      state.gameOver = true;
      state.winnerId = player.id;
      state.currentTurnId = null;
      setStatus('Van dau ket thuc');
      setMessage(player.name + ' da het bai va chien thang');
      pushMoveLog('Ket thuc van dau: ' + player.name + ' thang.');
      return { ok: true, eventType: 'GAME_OVER' };
    }

    state.currentTurnId = nextPlayerId(player.id);
    setStatus('Dang choi');
    setMessage(player.name + ' danh ' + combo.label);
    return { ok: true, eventType: 'PLAYED' };
  }

  function attemptPass(player) {
    if (!player) {
      return { ok: false, error: 'Khong tim thay nguoi choi' };
    }
    if (!state.started || state.gameOver) {
      return { ok: false, error: 'Van dau khong hoat dong' };
    }
    if (state.currentTurnId !== player.id) {
      return { ok: false, error: 'Chua den luot' };
    }
    if (!state.currentTrick) {
      return { ok: false, error: 'Khong the bo luot khi dang mo vong moi' };
    }
    if (state.controlUserId === player.id) {
      return { ok: false, error: 'Nguoi dang nam vong khong the bo luot' };
    }

    state.passedIds.add(player.id);
    pushMoveLog(player.name + ': bo luot');

    const next = nextPlayerId(player.id);
    const allOthersPass = everyoneExceptControlPassed();
    if (!next || next === state.controlUserId || allOthersPass) {
      const control = findPlayerById(state.controlUserId);
      state.currentTurnId = state.controlUserId;
      state.currentTrick = null;
      state.passedIds.clear();
      setStatus('Dang choi');
      setMessage('Tat ca da bo luot. ' + (control ? control.name : 'Nguoi giu vong') + ' mo vong moi');
      pushMoveLog('Mo vong moi.');
      return { ok: true, eventType: 'ROUND_RESET' };
    }

    state.currentTurnId = next;
    setStatus('Dang choi');
    setMessage(player.name + ' bo luot');
    return { ok: true, eventType: 'PASSED' };
  }

  function everyoneExceptControlPassed() {
    for (const player of state.players) {
      if (player.id === state.controlUserId) continue;
      if (!state.passedIds.has(player.id)) {
        return false;
      }
    }
    return true;
  }

  function canCurrentPlayerPass(player) {
    if (!player) return false;
    if (!state.currentTrick) return false;
    return state.controlUserId !== player.id;
  }

  function getLegalMoves(player) {
    const all = generateAllCombinations(player.hand);
    const result = [];
    for (const move of all) {
      if (state.playCount === 0 && !move.cards.some((c) => c.code === '3S')) {
        continue;
      }
      if (state.currentTrick && !comboCanBeat(move.combo, state.currentTrick.combo)) {
        continue;
      }
      result.push(move);
    }
    return dedupeMoves(result).sort(compareMoveStrengthAsc);
  }

  function dedupeMoves(moves) {
    const seen = new Set();
    const out = [];
    for (const move of moves) {
      const key = move.cards.map((c) => c.code).sort().join('|');
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(move);
    }
    return out;
  }

  function generateAllCombinations(hand) {
    if (!Array.isArray(hand) || hand.length === 0) return [];
    const moves = [];
    const sortedHand = hand.slice().sort(compareCards);

    for (const card of sortedHand) {
      const combo = parseCombination([card]);
      if (combo) moves.push({ combo, cards: combo.cards.slice() });
    }

    const byRank = new Map();
    for (const card of sortedHand) {
      if (!byRank.has(card.rankValue)) byRank.set(card.rankValue, []);
      byRank.get(card.rankValue).push(card);
    }
    for (const cards of byRank.values()) {
      cards.sort(compareCards);
      for (let size = 2; size <= cards.length; size++) {
        const combos = chooseK(cards, size);
        for (const group of combos) {
          const combo = parseCombination(group);
          if (combo) moves.push({ combo, cards: combo.cards.slice() });
        }
      }
    }

    const rankGroups = Array.from(byRank.entries())
      .filter(([rank]) => rank !== 15)
      .sort((a, b) => a[0] - b[0]);
    for (let i = 0; i < rankGroups.length; i++) {
      let j = i;
      while (j + 1 < rankGroups.length && rankGroups[j + 1][0] === rankGroups[j][0] + 1) {
        j++;
      }
      const runLength = j - i + 1;
      if (runLength >= 3) {
        for (let start = i; start <= j - 2; start++) {
          for (let end = start + 2; end <= j; end++) {
            const segment = rankGroups.slice(start, end + 1);
            collectStraightChoices(segment, 0, [], moves);
          }
        }
      }
      i = j;
    }

    return moves;
  }

  function collectStraightChoices(rankSegment, index, chosen, moves) {
    if (index >= rankSegment.length) {
      const combo = parseCombination(chosen);
      if (combo) {
        moves.push({ combo, cards: combo.cards.slice() });
      }
      return;
    }
    const cardsAtRank = rankSegment[index][1];
    for (const card of cardsAtRank) {
      chosen.push(card);
      collectStraightChoices(rankSegment, index + 1, chosen, moves);
      chosen.pop();
    }
  }

  function chooseK(cards, size) {
    const result = [];
    const cur = [];
    function dfs(start) {
      if (cur.length === size) {
        result.push(cur.slice());
        return;
      }
      for (let i = start; i <= cards.length - (size - cur.length); i++) {
        cur.push(cards[i]);
        dfs(i + 1);
        cur.pop();
      }
    }
    dfs(0);
    return result;
  }

  function parseCombination(cards) {
    if (!Array.isArray(cards) || cards.length === 0) {
      return null;
    }
    const sorted = cards.slice().sort(compareCards);
    const size = sorted.length;

    if (size === 1) {
      const c = sorted[0];
      return {
        type: 'SINGLE',
        length: 1,
        highestRank: c.rankValue,
        highestSuit: c.suitOrder,
        cards: sorted,
        label: 'don ' + c.label
      };
    }

    const sameRank = sorted.every((c) => c.rankValue === sorted[0].rankValue);
    if (sameRank && size === 2) {
      const hi = sorted[sorted.length - 1];
      return {
        type: 'PAIR',
        length: 2,
        highestRank: hi.rankValue,
        highestSuit: hi.suitOrder,
        cards: sorted,
        label: 'doi ' + rankLabel(hi.rankValue)
      };
    }
    if (sameRank && size === 3) {
      const hi = sorted[sorted.length - 1];
      return {
        type: 'TRIPLE',
        length: 3,
        highestRank: hi.rankValue,
        highestSuit: hi.suitOrder,
        cards: sorted,
        label: 'sam ' + rankLabel(hi.rankValue)
      };
    }
    if (sameRank && size === 4) {
      const hi = sorted[sorted.length - 1];
      return {
        type: 'FOUR_KIND',
        length: 4,
        highestRank: hi.rankValue,
        highestSuit: hi.suitOrder,
        cards: sorted,
        label: 'tu quy ' + rankLabel(hi.rankValue)
      };
    }

    if (size >= 3) {
      if (sorted.some((c) => c.rankValue === 15)) {
        return null;
      }
      for (let i = 1; i < sorted.length; i++) {
        if (sorted[i].rankValue !== sorted[i - 1].rankValue + 1) {
          return null;
        }
      }
      const hi = sorted[sorted.length - 1];
      return {
        type: 'STRAIGHT',
        length: size,
        highestRank: hi.rankValue,
        highestSuit: hi.suitOrder,
        cards: sorted,
        label: 'sanh ' + size + ' la (' + sorted[0].label + ' - ' + hi.label + ')'
      };
    }

    return null;
  }

  function comboCanBeat(next, current) {
    if (!next) return false;
    if (!current) return true;
    if (next.type !== current.type) return false;
    if (next.length !== current.length) return false;
    if (next.highestRank !== current.highestRank) return next.highestRank > current.highestRank;
    return next.highestSuit > current.highestSuit;
  }

  function compareMoveStrengthAsc(a, b) {
    if (a.combo.type !== b.combo.type) {
      return (TYPE_ORDER[a.combo.type] || 99) - (TYPE_ORDER[b.combo.type] || 99);
    }
    if (a.combo.length !== b.combo.length) {
      return a.combo.length - b.combo.length;
    }
    if (a.combo.highestRank !== b.combo.highestRank) {
      return a.combo.highestRank - b.combo.highestRank;
    }
    if (a.combo.highestSuit !== b.combo.highestSuit) {
      return a.combo.highestSuit - b.combo.highestSuit;
    }
    return a.cards.map((c) => c.code).join('|').localeCompare(b.cards.map((c) => c.code).join('|'));
  }

  function resolveSelectionFromHand(hand, cardCodes) {
    if (!Array.isArray(cardCodes) || cardCodes.length === 0) return null;
    const wanted = new Set();
    for (const raw of cardCodes) {
      const code = String(raw || '').trim().toUpperCase();
      if (!code || wanted.has(code)) {
        return null;
      }
      wanted.add(code);
    }
    const handByCode = new Map(hand.map((c) => [c.code.toUpperCase(), c]));
    const selected = [];
    for (const code of wanted) {
      const card = handByCode.get(code);
      if (!card) return null;
      selected.push(card);
    }
    selected.sort(compareCards);
    return selected;
  }

  function removeCardsFromHand(hand, selected) {
    const counts = new Map();
    for (const card of selected) {
      counts.set(card.code, (counts.get(card.code) || 0) + 1);
    }
    for (let i = hand.length - 1; i >= 0; i--) {
      const code = hand[i].code;
      const remaining = counts.get(code) || 0;
      if (remaining <= 0) continue;
      hand.splice(i, 1);
      if (remaining === 1) counts.delete(code);
      else counts.set(code, remaining - 1);
    }
  }

  function nextPlayerId(fromId) {
    const order = state.players.map((p) => p.id);
    const idx = order.indexOf(fromId);
    if (idx < 0) return order[0] || null;
    return order[(idx + 1) % order.length] || null;
  }

  function getCurrentPlayer() {
    return findPlayerById(state.currentTurnId);
  }

  function findPlayerById(id) {
    return state.players.find((p) => p.id === id) || null;
  }

  function setStatus(text) {
    state.status = text || '-';
    if (els.statusText) {
      els.statusText.textContent = state.status;
    }
  }

  function setMessage(text) {
    state.message = text || '-';
    if (els.messageText) {
      els.messageText.textContent = state.message;
    }
  }

  function pushMoveLog(text) {
    if (!text) return;
    state.moveLog.push(text);
    if (state.moveLog.length > 80) {
      state.moveLog = state.moveLog.slice(state.moveLog.length - 80);
    }
  }

  function renderAll() {
    renderPlayers();
    renderTrick();
    renderHand();
    renderMoveLog();
    renderSummary();
    updateActionButtons();
  }

  function renderPlayers() {
    if (!els.playersBoard) return;
    const slots = els.playersBoard.querySelectorAll('.tienlen-bot-player-slot');
    slots.forEach((slot, idx) => {
      const p = state.players[idx];
      slot.classList.remove('is-turn', 'is-control', 'is-passed', 'is-me');
      slot.innerHTML = '';
      if (!p) {
        slot.textContent = 'Khong co nguoi choi';
        return;
      }
      if (state.currentTurnId === p.id) slot.classList.add('is-turn');
      if (state.controlUserId === p.id) slot.classList.add('is-control');
      if (state.passedIds.has(p.id)) slot.classList.add('is-passed');
      if (p.isHuman) slot.classList.add('is-me');

      const name = document.createElement('div');
      name.className = 'fw-bold theme-text';
      name.textContent = (idx + 1) + '. ' + p.name;
      const meta = document.createElement('div');
      meta.className = 'small';
      meta.innerHTML = '<span>Con bai: <strong>' + p.hand.length + '</strong></span>'
        + (p.isHuman ? ' <span class="badge text-bg-info ms-1">Ban</span>' : ' <span class="badge text-bg-secondary ms-1">Bot</span>');
      const flags = document.createElement('div');
      flags.className = 'small text-muted';
      const parts = [];
      if (state.currentTurnId === p.id) parts.push('Dang den luot');
      if (state.controlUserId === p.id) parts.push('Nam vong');
      if (state.passedIds.has(p.id)) parts.push('Da pass');
      if (state.winnerId === p.id) parts.push('Thang');
      flags.textContent = parts.join(' | ') || '-';
      slot.append(name, meta, flags);
    });
  }

  function renderTrick() {
    const trick = state.currentTrick;
    if (els.currentTrickLabel) els.currentTrickLabel.textContent = trick ? trick.combo.label : 'Chua co';
    if (els.trickOwnerText) els.trickOwnerText.textContent = trick ? trick.ownerName : '-';
    if (els.trickOwnerTextDup) els.trickOwnerTextDup.textContent = trick ? trick.ownerName : '-';
    if (!els.trickCards) return;
    els.trickCards.innerHTML = '';
    const cards = trick ? trick.cards : [];
    if (!cards || cards.length === 0) {
      const muted = document.createElement('div');
      muted.className = 'text-muted small';
      muted.textContent = 'Chua co bo bai nao';
      els.trickCards.appendChild(muted);
      return;
    }
    for (const card of cards) {
      els.trickCards.appendChild(renderCardElement(card, false, true));
    }
  }

  function renderHand() {
    if (!els.myHand) return;
    const mePlayer = state.players.find((p) => p.isHuman);
    els.myHand.innerHTML = '';
    if (!mePlayer || !mePlayer.hand.length) {
      els.myHand.innerHTML = '<div class="text-muted small">Khong co bai tren tay.</div>';
      if (els.handCount) els.handCount.textContent = '0';
      if (els.selectedCount) els.selectedCount.textContent = '0';
      return;
    }

    const myTurn = state.currentTurnId === mePlayer.id && !state.gameOver;
    const validCodes = new Set(mePlayer.hand.map((c) => c.code));
    for (const code of Array.from(state.selectedCodes)) {
      if (!validCodes.has(code)) state.selectedCodes.delete(code);
    }

    for (const card of mePlayer.hand) {
      const selected = state.selectedCodes.has(card.code);
      const cardEl = renderCardElement(card, selected, false);
      if (!myTurn) cardEl.classList.add('disabled');
      cardEl.addEventListener('click', () => {
        if (!myTurn) return;
        if (state.selectedCodes.has(card.code)) state.selectedCodes.delete(card.code);
        else state.selectedCodes.add(card.code);
        renderHand();
        updateActionButtons();
      });
      els.myHand.appendChild(cardEl);
    }

    if (els.handCount) els.handCount.textContent = String(mePlayer.hand.length);
    if (els.selectedCount) els.selectedCount.textContent = String(state.selectedCodes.size);
  }

  function renderCardElement(card, selected, readonly) {
    const el = document.createElement(readonly ? 'div' : 'button');
    if (!readonly) el.type = 'button';
    el.className = 'tienlen-bot-card-btn';
    if (card.red) el.classList.add('red');
    if (selected) el.classList.add('selected');
    el.title = card.code;

    const top = document.createElement('div');
    top.className = 'fw-bold';
    top.textContent = card.label;
    const bottom = document.createElement('div');
    bottom.className = 'small';
    bottom.textContent = card.code;
    el.append(top, bottom);
    return el;
  }

  function renderMoveLog() {
    if (!els.moveLog) return;
    els.moveLog.innerHTML = '';
    if (!state.moveLog.length) {
      const li = document.createElement('li');
      li.className = 'text-muted';
      li.textContent = 'Chua co nuoc di';
      els.moveLog.appendChild(li);
      return;
    }
    state.moveLog.forEach((entry) => {
      const li = document.createElement('li');
      li.textContent = entry;
      els.moveLog.appendChild(li);
    });
    els.moveLog.scrollTop = els.moveLog.scrollHeight;
  }

  function renderSummary() {
    const turnPlayer = getCurrentPlayer();
    const controlPlayer = findPlayerById(state.controlUserId);
    const winnerPlayer = findPlayerById(state.winnerId);

    if (els.turnText) {
      els.turnText.textContent = state.gameOver
        ? (winnerPlayer ? winnerPlayer.name + ' thang' : 'Ket thuc')
        : (turnPlayer ? turnPlayer.name : '-');
    }
    if (els.controlText) {
      els.controlText.textContent = controlPlayer ? controlPlayer.name : '-';
    }
    if (els.moveCount) {
      els.moveCount.textContent = String(state.playCount);
    }
    if (els.resultText) {
      if (state.gameOver) {
        els.resultText.textContent = winnerPlayer
          ? (winnerPlayer.isHuman ? 'Ban thang' : winnerPlayer.name + ' thang')
          : 'Ket thuc';
      } else {
        els.resultText.textContent = 'Dang choi';
      }
    }
    if (els.statusText) {
      els.statusText.textContent = state.status || '-';
    }
    if (els.messageText) {
      els.messageText.textContent = state.message || '-';
    }
  }

  function updateActionButtons() {
    const mePlayer = state.players.find((p) => p.isHuman);
    const myTurn = !!(mePlayer && state.currentTurnId === mePlayer.id && !state.gameOver);
    const hasSelection = state.selectedCodes.size > 0;
    const canPass = !!(myTurn && canCurrentPlayerPass(mePlayer));

    if (els.playBtn) els.playBtn.disabled = !myTurn || !hasSelection;
    if (els.passBtn) els.passBtn.disabled = !canPass;
    if (els.clearBtn) els.clearBtn.disabled = state.selectedCodes.size === 0;
    if (els.selectedCount) els.selectedCount.textContent = String(state.selectedCodes.size);
    if (els.handCount && mePlayer) els.handCount.textContent = String(mePlayer.hand.length);
  }

  function standardDeck() {
    const deck = [];
    for (const rankValue of RANK_VALUES) {
      for (const suit of SUITS) {
        const rankText = rankLabel(rankValue);
        deck.push({
          code: rankText + suit.code,
          label: rankText + suit.symbol,
          rankValue,
          suitOrder: suit.order,
          suitCode: suit.code,
          red: suit.red
        });
      }
    }
    return deck;
  }

  function countByRank(cards) {
    const counts = new Map();
    for (const card of cards) {
      counts.set(card.rankValue, (counts.get(card.rankValue) || 0) + 1);
    }
    return counts;
  }

  function rankLabel(rankValue) {
    return RANK_LABELS.get(rankValue) || String(rankValue);
  }

  function compareCards(a, b) {
    if (a.rankValue !== b.rankValue) return a.rankValue - b.rankValue;
    return a.suitOrder - b.suitOrder;
  }

  function shuffleInPlace(arr) {
    for (let i = arr.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      const tmp = arr[i];
      arr[i] = arr[j];
      arr[j] = tmp;
    }
  }

  function clearBotTimer() {
    if (state.botTimer != null) {
      window.clearTimeout(state.botTimer);
      state.botTimer = null;
    }
  }

  function normalizeDifficulty(d) {
    return String(d || '').toLowerCase() === 'hard' ? 'hard' : 'easy';
  }
})();
