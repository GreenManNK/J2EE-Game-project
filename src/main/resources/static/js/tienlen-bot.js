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
  const TYPE_ORDER = { SINGLE: 1, PAIR: 2, TRIPLE: 3, STRAIGHT: 4, DOUBLE_STRAIGHT: 5, FOUR_KIND: 6 };
  const PENALTY_TWO_BLACK = 5;
  const PENALTY_TWO_RED = 10;
  const PENALTY_CONG_BONUS = 13;
  const PENALTY_LEFTOVER_FOUR_KIND = 8;

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
    roundNumber: 0,
    passedIds: new Set(),
    message: '-',
    status: 'Dang khoi tao van...',
    lastRoundSummary: '',
    roundPenaltyEvents: [],
    moveLog: [],
    botTimer: null,
    pendingHandDealAnim: false,
    pendingTrickAnim: false,
    pendingTurnFlashUserId: '',
    lastActionType: ''
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
    els.statusTimeline = document.getElementById('tlBotStatusTimeline');
    els.newGameBtn = document.getElementById('tlBotNewGameBtn');
    els.surrenderBtn = document.getElementById('tlBotSurrenderBtn');
    els.turnText = document.getElementById('tlBotTurnText');
    els.controlText = document.getElementById('tlBotControlText');
    els.currentTrickLabel = document.getElementById('tlBotCurrentTrickLabel');
    els.currentTrickLabelDup = document.getElementById('tlBotCurrentTrickLabelDup');
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
    els.playerSlots = Array.from(document.querySelectorAll('#tlBotPlayersBoard .tienlen-bot-player-slot'));
  }

  function bindActions() {
    els.newGameBtn?.addEventListener('click', () => startNewGame());
    els.surrenderBtn?.addEventListener('click', () => surrenderGame());
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
      { id: me.id, name: me.name, isHuman: true, hand: [], seatIndex: 0, score: 0, lastRoundDelta: 0, lastRoundChopDelta: 0, lastRoundPenalty: 0, lastRoundCong: false, lastRoundTwos: 0, lastRoundSpecialPenalty: 0, roundPlayedCardCount: 0, roundSideBetDelta: 0 },
      { id: 'bot-1', name: 'Bot 1', isHuman: false, hand: [], seatIndex: 1, score: 0, lastRoundDelta: 0, lastRoundChopDelta: 0, lastRoundPenalty: 0, lastRoundCong: false, lastRoundTwos: 0, lastRoundSpecialPenalty: 0, roundPlayedCardCount: 0, roundSideBetDelta: 0 },
      { id: 'bot-2', name: 'Bot 2', isHuman: false, hand: [], seatIndex: 2, score: 0, lastRoundDelta: 0, lastRoundChopDelta: 0, lastRoundPenalty: 0, lastRoundCong: false, lastRoundTwos: 0, lastRoundSpecialPenalty: 0, roundPlayedCardCount: 0, roundSideBetDelta: 0 },
      { id: 'bot-3', name: 'Bot 3', isHuman: false, hand: [], seatIndex: 3, score: 0, lastRoundDelta: 0, lastRoundChopDelta: 0, lastRoundPenalty: 0, lastRoundCong: false, lastRoundTwos: 0, lastRoundSpecialPenalty: 0, roundPlayedCardCount: 0, roundSideBetDelta: 0 }
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
    state.roundNumber = Number(state.roundNumber || 0) + 1;
    state.passedIds.clear();
    state.selectedCodes.clear();
    state.moveLog = [];
    state.pendingHandDealAnim = true;
    state.pendingTrickAnim = false;
    resetRoundTrackingForNewGame();

    const instantWin = detectInstantWinWinner(state.players);
    if (instantWin) {
      state.gameOver = true;
      state.winnerId = instantWin.player.id;
      state.currentTurnId = null;
      state.controlUserId = null;
      state.lastActionType = 'GAME_OVER';
      applyRoundSettlementForWinner(instantWin.player.id);
      setStatus('Van dau ket thuc');
      setMessage(instantWin.player.name + ' toi trang (' + instantWin.rule.label + '). ' + (state.lastRoundSummary || ''));
      pushMoveLog('Ket thuc van dau: ' + instantWin.player.name + ' toi trang (' + instantWin.rule.label + ').');
      if (state.lastRoundSummary) pushMoveLog(state.lastRoundSummary);
      renderAll();
      return;
    }

    const firstPlayer = state.players.find((p) => p.hand.some((c) => c.code === '3S')) || state.players[0];
    state.currentTurnId = firstPlayer.id;
    state.controlUserId = firstPlayer.id;
    state.pendingTurnFlashUserId = firstPlayer.id;
    state.lastActionType = 'GAME_STARTED';
    setStatus('Van moi da bat dau');
    setMessage(firstPlayer.name + ' giu 3S va danh truoc');
    pushMoveLog('Van moi bat dau. ' + firstPlayer.name + ' giu 3S.');

    renderAll();
    scheduleBotTurnIfNeeded();
  }

  function resetRoundTrackingForNewGame() {
    state.lastRoundSummary = '';
    state.roundPenaltyEvents = [];
    for (const p of state.players) {
      p.lastRoundDelta = 0;
      p.lastRoundChopDelta = 0;
      p.lastRoundPenalty = 0;
      p.lastRoundCong = false;
      p.lastRoundTwos = 0;
      p.lastRoundSpecialPenalty = 0;
      p.roundPlayedCardCount = 0;
      p.roundSideBetDelta = 0;
    }
  }

  function applyRoundSettlementForWinner(winnerId) {
    const winner = state.players.find((p) => p.id === winnerId);
    if (!winner) return;

    let winnerGain = 0;
    const loserParts = [];
    const chopEvents = Array.isArray(state.roundPenaltyEvents) ? state.roundPenaltyEvents.slice() : [];
    for (const p of state.players) {
      p.lastRoundDelta = Number(p.roundSideBetDelta || 0);
      p.lastRoundChopDelta = Number(p.roundSideBetDelta || 0);
      p.lastRoundPenalty = 0;
      p.lastRoundCong = false;
      p.lastRoundTwos = 0;
      p.lastRoundSpecialPenalty = 0;
    }

    for (const p of state.players) {
      if (p.id === winnerId) continue;
      const penalty = calculateLoserPenalty(p.hand, Number(p.roundPlayedCardCount || 0) <= 0);
      p.lastRoundPenalty = penalty.total;
      p.lastRoundCong = penalty.cong;
      p.lastRoundTwos = penalty.twoCount;
      p.lastRoundSpecialPenalty = penalty.specialPenalty;
      p.lastRoundDelta -= penalty.total;
      p.score = Number(p.score || 0) + p.lastRoundDelta;
      winnerGain += penalty.total;

      const part = p.name + ' -' + penalty.total +
        ' (con ' + p.hand.length + ' la' +
        (penalty.cong ? ', cong x2 +13' : '') +
        (penalty.twoCount > 0 ? (', thoi ' + penalty.twoCount + ' heo') : '') +
        (penalty.specialPenalty > 0 ? (', thoi hang +' + penalty.specialPenalty) : '') +
        ')';
      loserParts.push(part);
    }

    winner.lastRoundDelta = Number(winner.lastRoundDelta || 0) + winnerGain;
    winner.lastRoundChopDelta = Number(winner.roundSideBetDelta || 0);
    winner.score = Number(winner.score || 0) + winner.lastRoundDelta;
    state.lastRoundSummary = 'Tinh diem van: ' + winner.name + ' +' + winnerGain +
      (chopEvents.length ? (' | Chat: ' + chopEvents.join(' ; ')) : '') +
      (loserParts.length ? (' | ' + loserParts.join(' ; ')) : '');

    for (const p of state.players) {
      p.roundPlayedCardCount = 0;
    }
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

  function surrenderGame() {
    if (!state.started || state.gameOver) {
      setMessage('Khong co van dau dang dien ra de dau hang');
      renderAll();
      return;
    }
    if (!window.confirm('Ban chac chan muon dau hang van Tien len nay?')) {
      return;
    }

    clearBotTimer();
    const human = state.players.find((p) => p.isHuman) || null;
    const remaining = state.players.filter((p) => !p.isHuman);
    remaining.sort((a, b) => {
      if (a.hand.length !== b.hand.length) return a.hand.length - b.hand.length;
      return a.name.localeCompare(b.name);
    });
    const winner = remaining[0] || null;

    state.gameOver = true;
    state.currentTurnId = null;
    state.passedIds.clear();
    state.winnerId = winner ? winner.id : null;
    state.lastActionType = 'SURRENDER';
    if (winner) {
      applyRoundSettlementForWinner(winner.id);
    }
    setStatus('Van dau ket thuc');
    setMessage('Ban da dau hang.' + (winner ? (' ' + winner.name + ' duoc tinh la thang.') : '') + (state.lastRoundSummary ? (' ' + state.lastRoundSummary) : ''));
    pushMoveLog((human ? human.name : 'Ban') + ': dau hang');
    if (winner) {
      pushMoveLog('Ket thuc van dau: ' + winner.name + ' thang (do dau hang).');
    }
    if (state.lastRoundSummary) pushMoveLog(state.lastRoundSummary);
    renderAll();
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
      if (combo.type === 'DOUBLE_STRAIGHT') {
        score += combo.length * 10;
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
      return { ok: false, error: 'Bo bai khong hop le (ho tro: don, doi, sam, tu quy, sanh, doi thong)' };
    }
    if (state.playCount === 0 && !selected.some((c) => c.code === '3S')) {
      return { ok: false, error: 'Nuoc dau tien phai chua 3S' };
    }
    if (state.currentTrick && !comboCanBeat(combo, state.currentTrick.combo)) {
      return { ok: false, error: 'Bo bai khong de hon bo bai hien tai' };
    }
    const specialBeatPenalty = state.currentTrick
      ? detectSpecialBeatPenalty(combo, state.currentTrick.combo, state.currentTrick.cards, state.currentTrick.ownerId)
      : null;

    removeCardsFromHand(player.hand, selected);
    player.hand.sort(compareCards);
    player.roundPlayedCardCount = Number(player.roundPlayedCardCount || 0) + selected.length;

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
    const specialBeatMessage = specialBeatPenalty ? recordSpecialBeatPenalty(player.id, specialBeatPenalty) : '';

    if (player.hand.length === 0) {
      state.gameOver = true;
      state.winnerId = player.id;
      state.currentTurnId = null;
      state.pendingTrickAnim = true;
      state.lastActionType = 'GAME_OVER';
      applyRoundSettlementForWinner(player.id);
      setStatus('Van dau ket thuc');
      setMessage(player.name + ' da het bai va chien thang. ' + (state.lastRoundSummary || ''));
      pushMoveLog('Ket thuc van dau: ' + player.name + ' thang.');
      if (state.lastRoundSummary) pushMoveLog(state.lastRoundSummary);
      return { ok: true, eventType: 'GAME_OVER' };
    }

    state.currentTurnId = nextPlayerId(player.id);
    state.pendingTrickAnim = true;
    state.pendingTurnFlashUserId = state.currentTurnId || '';
    state.lastActionType = 'PLAYED';
    setStatus('Dang choi');
    setMessage(player.name + ' danh ' + combo.label + (specialBeatMessage ? ('. ' + specialBeatMessage) : ''));
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
      state.pendingTrickAnim = true;
      state.pendingTurnFlashUserId = state.currentTurnId || '';
      state.lastActionType = 'ROUND_RESET';
      setStatus('Dang choi');
      setMessage('Tat ca da bo luot. ' + (control ? control.name : 'Nguoi giu vong') + ' mo vong moi');
      pushMoveLog('Mo vong moi.');
      return { ok: true, eventType: 'ROUND_RESET' };
    }

    state.currentTurnId = next;
    state.pendingTurnFlashUserId = next || '';
    state.lastActionType = 'PASSED';
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

    const pairableRankGroups = rankGroups.filter(([, cards]) => cards.length >= 2);
    for (let i = 0; i < pairableRankGroups.length; i++) {
      let j = i;
      while (j + 1 < pairableRankGroups.length && pairableRankGroups[j + 1][0] === pairableRankGroups[j][0] + 1) {
        j++;
      }
      const runLength = j - i + 1;
      if (runLength >= 3) {
        for (let start = i; start <= j - 2; start++) {
          for (let end = start + 2; end <= j; end++) {
            const segment = pairableRankGroups.slice(start, end + 1);
            collectDoubleStraightChoices(segment, 0, [], moves);
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

  function collectDoubleStraightChoices(rankSegment, index, chosen, moves) {
    if (index >= rankSegment.length) {
      const combo = parseCombination(chosen);
      if (combo) {
        moves.push({ combo, cards: combo.cards.slice() });
      }
      return;
    }
    const cardsAtRank = rankSegment[index][1];
    const pairChoices = chooseK(cardsAtRank, 2);
    for (const pair of pairChoices) {
      chosen.push(pair[0], pair[1]);
      collectDoubleStraightChoices(rankSegment, index + 1, chosen, moves);
      chosen.pop();
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

  function detectInstantWinWinner(players) {
    if (!Array.isArray(players)) return null;
    for (const player of players) {
      const rule = detectInstantWinRule(player?.hand);
      if (rule) {
        return { player, rule };
      }
    }
    return null;
  }

  function detectInstantWinRule(hand) {
    if (!Array.isArray(hand) || hand.length !== 13) return null;
    const counts = rankCountsArray(hand);
    if (hasDragonStraightThreeToAce(counts)) {
      return { key: 'DRAGON_STRAIGHT', label: 'sanh rong 3-A' };
    }
    if (hasConsecutivePairs(counts, 5)) {
      return { key: 'FIVE_CONSECUTIVE_PAIRS', label: '5 doi thong' };
    }
    if (countPairs(counts) >= 6) {
      return { key: 'SIX_PAIRS', label: '6 doi' };
    }
    if ((counts[15] || 0) === 4) {
      return { key: 'FOUR_TWOS', label: 'tu quy 2' };
    }
    return null;
  }

  function rankCountsArray(hand) {
    const counts = new Array(16).fill(0);
    for (const card of hand) {
      const rank = Number(card?.rankValue || 0);
      if (rank >= 0 && rank < counts.length) {
        counts[rank] += 1;
      }
    }
    return counts;
  }

  function hasDragonStraightThreeToAce(counts) {
    for (let rank = 3; rank <= 14; rank++) {
      if ((counts[rank] || 0) <= 0) return false;
    }
    return true;
  }

  function hasConsecutivePairs(counts, requiredPairs) {
    if (!Array.isArray(counts) || requiredPairs <= 0) return false;
    let streak = 0;
    for (let rank = 3; rank <= 14; rank++) {
      if ((counts[rank] || 0) >= 2) {
        streak += 1;
        if (streak >= requiredPairs) return true;
      } else {
        streak = 0;
      }
    }
    return false;
  }

  function countPairs(counts) {
    if (!Array.isArray(counts)) return 0;
    let total = 0;
    for (let rank = 3; rank <= 15; rank++) {
      total += Math.floor((counts[rank] || 0) / 2);
    }
    return total;
  }

  function calculateLoserPenalty(hand, cong) {
    const cards = Array.isArray(hand) ? hand : [];
    const base = cards.length;
    const baseAfterCongMultiplier = cong ? (base * 2) : base;
    const twoCount = countRankValue(cards, 15);
    const twoPenalty = twoPenaltyPoints(cards);
    const specialPenalty = countLeftoverSpecialPenalty(cards);
    const congPenalty = cong ? PENALTY_CONG_BONUS : 0;
    return {
      total: baseAfterCongMultiplier + congPenalty + twoPenalty + specialPenalty,
      cong: Boolean(cong),
      twoCount,
      specialPenalty
    };
  }

  function countLeftoverSpecialPenalty(hand) {
    if (!Array.isArray(hand) || hand.length === 0) return 0;
    const counts = rankCountsArray(hand);
    let penalty = 0;
    for (let rank = 3; rank <= 15; rank++) {
      if ((counts[rank] || 0) >= 4) {
        penalty += PENALTY_LEFTOVER_FOUR_KIND;
      }
    }
    let streak = 0;
    for (let rank = 3; rank <= 14; rank++) {
      if ((counts[rank] || 0) >= 2) {
        streak += 1;
      } else {
        if (streak >= 3) penalty += doubleStraightLeftoverPenaltyPoints(streak);
        streak = 0;
      }
    }
    if (streak >= 3) penalty += doubleStraightLeftoverPenaltyPoints(streak);
    return penalty;
  }

  function doubleStraightLeftoverPenaltyPoints(pairCount) {
    const n = Number(pairCount || 0);
    if (n >= 5) return 15;
    if (n === 4) return 10;
    if (n === 3) return 6;
    return 0;
  }

  function countRankValue(hand, rankValue) {
    if (!Array.isArray(hand) || hand.length === 0) return 0;
    let total = 0;
    for (const card of hand) {
      if (Number(card?.rankValue || 0) === Number(rankValue)) {
        total += 1;
      }
    }
    return total;
  }

  function twoPenaltyPoints(hand) {
    if (!Array.isArray(hand) || hand.length === 0) return 0;
    let total = 0;
    for (const card of hand) {
      total += twoCardPenaltyPoints(card);
    }
    return total;
  }

  function detectSpecialBeatPenalty(challenger, current, currentCards, currentOwnerId) {
    if (!challenger || !current || !Array.isArray(currentCards)) return null;
    if (current.type === 'SINGLE' && current.highestRank === 15) {
      const points = singleTwoPenaltyPoints(currentCards);
      return points > 0 ? { points, label: 'chat 1 heo', victimUserId: currentOwnerId } : null;
    }
    if (current.type === 'PAIR' && current.highestRank === 15) {
      const points = pairTwoPenaltyPoints(currentCards);
      return points > 0 ? { points, label: 'chat doi heo', victimUserId: currentOwnerId } : null;
    }
    if (current.type === 'FOUR_KIND' && challenger.type === 'DOUBLE_STRAIGHT' && Number(challenger.length || 0) >= 8) {
      const pairCount = Math.floor(Number(challenger.length || 0) / 2);
      return { points: pairCount >= 5 ? 20 : 16, label: 'chat tu quy', victimUserId: currentOwnerId };
    }
    if (current.type === 'FOUR_KIND' && challenger.type === 'FOUR_KIND') {
      return { points: PENALTY_LEFTOVER_FOUR_KIND, label: 'chat tu quy', victimUserId: currentOwnerId };
    }
    if (current.type === 'DOUBLE_STRAIGHT' && challenger.type === 'DOUBLE_STRAIGHT' && Number(challenger.length || 0) > Number(current.length || 0)) {
      const pairCount = Math.max(3, Math.floor(Number(current.length || 0) / 2));
      return { points: pairCount * 3, label: 'chat doi thong ' + pairCount + ' doi', victimUserId: currentOwnerId };
    }
    return null;
  }

  function singleTwoPenaltyPoints(cards) {
    if (!Array.isArray(cards) || cards.length === 0) return 0;
    return twoCardPenaltyPoints(cards[0]);
  }

  function pairTwoPenaltyPoints(cards) {
    if (!Array.isArray(cards) || cards.length === 0) return 0;
    let sum = 0;
    for (const card of cards) {
      if (Number(card?.rankValue || 0) === 15) {
        sum += twoCardPenaltyPoints(card);
      }
    }
    return sum;
  }

  function twoCardPenaltyPoints(card) {
    if (!card || Number(card.rankValue || 0) !== 15) return 0;
    return Number(card.suitOrder || 0) >= 2 ? PENALTY_TWO_RED : PENALTY_TWO_BLACK;
  }

  function recordSpecialBeatPenalty(chopperUserId, penalty) {
    if (!penalty || Number(penalty.points || 0) <= 0) return '';
    const victimUserId = String(penalty.victimUserId || '');
    if (!victimUserId || victimUserId === chopperUserId) return '';

    const chopper = findPlayerById(chopperUserId);
    const victim = findPlayerById(victimUserId);
    if (!chopper || !victim) return '';

    chopper.roundSideBetDelta = Number(chopper.roundSideBetDelta || 0) + Number(penalty.points || 0);
    victim.roundSideBetDelta = Number(victim.roundSideBetDelta || 0) - Number(penalty.points || 0);

    const message = chopper.name + ' ' + String(penalty.label || 'chat bai') + ' cua ' + victim.name + ' (+' + Number(penalty.points || 0) + ')';
    if (!Array.isArray(state.roundPenaltyEvents)) state.roundPenaltyEvents = [];
    state.roundPenaltyEvents.push(message);
    pushMoveLog(message);
    return message;
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

    if (size >= 6 && size % 2 === 0) {
      const hasTwo = sorted.some((c) => c.rankValue === 15);
      if (!hasTwo) {
        let validDoubleStraight = true;
        let prevRank = -1;
        for (let i = 0; i < sorted.length; i += 2) {
          const currentRank = sorted[i].rankValue;
          if (!sorted[i + 1] || sorted[i + 1].rankValue !== currentRank) {
            validDoubleStraight = false;
            break;
          }
          if (prevRank >= 0 && currentRank !== prevRank + 1) {
            validDoubleStraight = false;
            break;
          }
          prevRank = currentRank;
        }
        if (validDoubleStraight) {
          const hi = sorted[sorted.length - 1];
          return {
            type: 'DOUBLE_STRAIGHT',
            length: size,
            highestRank: hi.rankValue,
            highestSuit: hi.suitOrder,
            cards: sorted,
            label: 'doi thong ' + (size / 2) + ' doi (' + sorted[0].label + ' - ' + hi.label + ')'
          };
        }
      }
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
    if (canBeatSpecial(next, current)) return true;
    if (next.type !== current.type) return false;
    if (next.length !== current.length) return false;
    if (next.highestRank !== current.highestRank) return next.highestRank > current.highestRank;
    return next.highestSuit > current.highestSuit;
  }

  function canBeatSpecial(next, current) {
    if (!next || !current) return false;
    if (isSingleTwoCombo(current)) {
      if (next.type === 'FOUR_KIND') return true;
      if (next.type === 'DOUBLE_STRAIGHT' && next.length >= 6) return true;
    }
    if (isPairTwoCombo(current)) {
      if (next.type === 'FOUR_KIND') return true;
      if (next.type === 'DOUBLE_STRAIGHT' && next.length >= 8) return true;
    }
    if (current.type === 'FOUR_KIND') {
      if (next.type === 'DOUBLE_STRAIGHT' && next.length >= 8) return true;
    }
    if (current.type === 'DOUBLE_STRAIGHT' && next.type === 'DOUBLE_STRAIGHT') {
      return next.length > current.length;
    }
    return false;
  }

  function isSingleTwoCombo(combo) {
    return !!combo && combo.type === 'SINGLE' && combo.highestRank === 15;
  }

  function isPairTwoCombo(combo) {
    return !!combo && combo.type === 'PAIR' && combo.length === 2 && combo.highestRank === 15;
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
    renderStatusTimeline();
    updateActionButtons();
  }

  function renderPlayers() {
    if (!els.playersBoard) return;
    const slots = els.playerSlots || [];
    const players = playersForTableOrder(state.players);
    slots.forEach((slot, idx) => {
      const p = players[idx];
      const tablePos = String(slot?.dataset?.tablePos || tablePositionKey(idx));
      slot.classList.remove('is-turn', 'is-control', 'is-passed', 'is-me', 'is-winner');
      slot.classList.remove('turn-flash');
      slot.innerHTML = '';
      if (!p) {
        slot.innerHTML =
          '<div class="tl-player-empty">' +
          '<div class="tl-player-empty__icon">+</div>' +
          '<div class="tl-player-empty__text">Cho vi tri ' + escapeHtml(tablePositionLabel(tablePos)) + '</div>' +
          '</div>';
        return;
      }
      if (state.currentTurnId === p.id) slot.classList.add('is-turn');
      if (state.controlUserId === p.id) slot.classList.add('is-control');
      if (state.passedIds.has(p.id)) slot.classList.add('is-passed');
      if (p.isHuman) slot.classList.add('is-me');
      if (state.winnerId === p.id) slot.classList.add('is-winner');
      if (state.pendingTurnFlashUserId && state.pendingTurnFlashUserId === p.id && !state.gameOver) {
        slot.classList.add('turn-flash');
      }

      const badges = [
        p.isHuman ? '<span class="tl-seat-badge tl-seat-badge--me">Ban</span>' : '<span class="tl-seat-badge tl-seat-badge--bot">Bot</span>',
        (state.currentTurnId === p.id && !state.gameOver) ? '<span class="tl-seat-badge tl-seat-badge--turn">Luot</span>' : '',
        (state.controlUserId === p.id && state.started && !state.gameOver) ? '<span class="tl-seat-badge tl-seat-badge--control">Nam vong</span>' : '',
        state.passedIds.has(p.id) ? '<span class="tl-seat-badge tl-seat-badge--pass">Pass</span>' : '',
        (state.winnerId === p.id) ? '<span class="tl-seat-badge tl-seat-badge--win">Thang</span>' : '',
        p.lastRoundCong ? '<span class="tl-seat-badge tl-seat-badge--pass">Cong</span>' : '',
        (Number(p.lastRoundTwos || 0) > 0) ? ('<span class="tl-seat-badge tl-seat-badge--control">Thoi ' + Number(p.lastRoundTwos || 0) + ' heo</span>') : '',
        (Number(p.lastRoundSpecialPenalty || 0) > 0) ? '<span class="tl-seat-badge tl-seat-badge--turn">Thoi hang</span>' : ''
      ].filter(Boolean).join('');

      const flags = [];
      flags.push(tablePositionLabel(tablePos));
      flags.push('Ghe ' + (Number(p.seatIndex || 0) + 1));
      if (state.currentTurnId === p.id) flags.push('Dang den luot');
      if (state.controlUserId === p.id) flags.push('Nam vong');
      if (state.passedIds.has(p.id)) flags.push('Da pass');
      if (state.winnerId === p.id) flags.push('Da het bai');
      const score = Number(p.score || 0);
      const roundDelta = Number(p.lastRoundDelta || 0);
      const chopDelta = Number(p.lastRoundChopDelta || 0);
      flags.push('Diem ' + (score >= 0 ? '+' : '') + score);
      if (roundDelta !== 0) flags.push('Van nay ' + (roundDelta > 0 ? '+' : '') + roundDelta);
      if (chopDelta !== 0) flags.push('Chat ' + (chopDelta > 0 ? '+' : '') + chopDelta);

      slot.innerHTML =
        '<div class="tl-player-slot__head">' +
          '<div class="tl-player-avatar" aria-hidden="true">' + escapeHtml(initialsOfName(p.name)) + '</div>' +
          '<div class="tl-player-slot__identity">' +
            '<div class="tl-player-slot__name">' + escapeHtml((Number(p.seatIndex || 0) + 1) + '. ' + p.name) + '</div>' +
            '<div class="tl-player-slot__uid">' + escapeHtml(p.id) + '</div>' +
          '</div>' +
        '</div>' +
        '<div class="tl-player-slot__meta">' +
          '<div class="tl-player-slot__chips">' + badges + '</div>' +
          '<div class="tl-player-slot__count">Con bai <strong>' + p.hand.length + '</strong> | Diem <strong>' + (score > 0 ? '+' : '') + score + '</strong></div>' +
        '</div>' +
        '<div class="tl-player-slot__footer">' + escapeHtml(flags.join(' | ') || 'San sang') + '</div>';
    });
    state.pendingTurnFlashUserId = '';
  }

  function playersForTableOrder(seatPlayers) {
    const ordered = [null, null, null, null]; // top, right, bottom, left
    const players = Array.isArray(seatPlayers) ? seatPlayers.slice(0, 4) : [];
    if (players.length === 0) return ordered;

    const human = players.find((p) => p && p.isHuman) || players[0];
    const anchorSeat = normalizeSeatIndex(human?.seatIndex, 0);
    const used = new Set();

    for (const player of players) {
      if (!player) continue;
      const seat = normalizeSeatIndex(player.seatIndex, -1);
      if (seat < 0) continue;
      const delta = mod4(seat - anchorSeat);
      const tableIndex = deltaToTableIndex(delta);
      if (tableIndex < 0 || tableIndex >= ordered.length || ordered[tableIndex]) continue;
      ordered[tableIndex] = player;
      used.add(player.id);
    }

    let fillIdx = 0;
    for (const player of players) {
      if (!player || used.has(player.id)) continue;
      while (fillIdx < ordered.length && ordered[fillIdx]) fillIdx++;
      if (fillIdx < ordered.length) ordered[fillIdx] = player;
    }
    return ordered;
  }

  function deltaToTableIndex(delta) {
    switch (mod4(delta)) {
      case 0: return 2; // human -> bottom
      case 1: return 1; // next -> right
      case 2: return 0; // opposite -> top
      case 3: return 3; // previous -> left
      default: return -1;
    }
  }

  function mod4(value) {
    const n = Number(value) || 0;
    return ((n % 4) + 4) % 4;
  }

  function normalizeSeatIndex(value, fallback) {
    const n = Number(value);
    return Number.isFinite(n) ? Math.max(0, Math.trunc(n)) : fallback;
  }

  function tablePositionKey(idx) {
    return ['top', 'right', 'bottom', 'left'][Number(idx) || 0] || 'bottom';
  }

  function tablePositionLabel(pos) {
    switch (String(pos || '').toLowerCase()) {
      case 'top': return 'Phia tren';
      case 'right': return 'Ben phai';
      case 'left': return 'Ben trai';
      case 'bottom':
      default:
        return 'Phia duoi';
    }
  }

  function renderTrick() {
    const trick = state.currentTrick;
    if (els.currentTrickLabel) els.currentTrickLabel.textContent = trick ? trick.combo.label : 'Chua co';
    if (els.currentTrickLabelDup) els.currentTrickLabelDup.textContent = trick ? trick.combo.label : 'Chua co';
    if (els.trickOwnerText) els.trickOwnerText.textContent = trick ? trick.ownerName : '-';
    if (els.trickOwnerTextDup) els.trickOwnerTextDup.textContent = trick ? trick.ownerName : '-';
    if (!els.trickCards) return;
    els.trickCards.innerHTML = '';
    if (state.pendingTrickAnim) {
      restartAnimation(els.trickCards, 'tl-trick-pulse');
    }
    const cards = trick ? trick.cards : [];
    if (!cards || cards.length === 0) {
      state.pendingTrickAnim = false;
      const muted = document.createElement('div');
      muted.className = 'text-muted small';
      muted.textContent = 'Chua co bo bai nao';
      els.trickCards.appendChild(muted);
      return;
    }
    const animateEnter = state.pendingTrickAnim;
    for (let i = 0; i < cards.length; i++) {
      const card = cards[i];
      const cardEl = renderCardElement(card, false, true);
      if (animateEnter) {
        cardEl.classList.add('tl-card-enter-play');
        cardEl.style.animationDelay = String(i * 40) + 'ms';
      }
      els.trickCards.appendChild(cardEl);
    }
    state.pendingTrickAnim = false;
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

    const animateDeal = state.pendingHandDealAnim;
    for (let idx = 0; idx < mePlayer.hand.length; idx++) {
      const card = mePlayer.hand[idx];
      const selected = state.selectedCodes.has(card.code);
      const cardEl = renderCardElement(card, selected, false);
      if (!myTurn) cardEl.classList.add('disabled');
      if (animateDeal) {
        cardEl.classList.add('tl-card-enter-deal');
        cardEl.style.animationDelay = String(Math.min(idx, 12) * 24) + 'ms';
      }
      cardEl.addEventListener('click', () => {
        if (!myTurn) return;
        if (state.selectedCodes.has(card.code)) state.selectedCodes.delete(card.code);
        else state.selectedCodes.add(card.code);
        renderHand();
        updateActionButtons();
      });
      els.myHand.appendChild(cardEl);
    }
    state.pendingHandDealAnim = false;

    if (els.handCount) els.handCount.textContent = String(mePlayer.hand.length);
    if (els.selectedCount) els.selectedCount.textContent = String(state.selectedCodes.size);
  }

  function renderCardElement(card, selected, readonly) {
    const el = document.createElement(readonly ? 'div' : 'button');
    if (!readonly) el.type = 'button';
    el.className = 'tienlen-bot-card-btn';
    const visual = cardVisual(card);
    if (visual.red) el.classList.add('red');
    if (selected) el.classList.add('selected');
    if (readonly) el.classList.add('readonly');
    el.title = card.code;
    el.setAttribute('aria-label', visual.rankText + ' ' + visual.suitNameVi);
    el.dataset.code = card.code;

    const cornerTop = document.createElement('div');
    cornerTop.className = 'tl-card-corner tl-card-corner--top';
    cornerTop.innerHTML = '<span class="tl-card-rank">' + escapeHtml(visual.rankText) + '</span><span class="tl-card-suit">' + escapeHtml(visual.suitSymbol) + '</span>';

    const center = document.createElement('div');
    center.className = 'tl-card-center';
    center.innerHTML =
      '<div class="tl-card-center__rank">' + escapeHtml(visual.rankText) + '</div>' +
      '<div class="tl-card-center__suit">' + escapeHtml(visual.suitSymbol) + '</div>';

    const cornerBottom = document.createElement('div');
    cornerBottom.className = 'tl-card-corner tl-card-corner--bottom';
    cornerBottom.innerHTML = '<span class="tl-card-rank">' + escapeHtml(visual.rankText) + '</span><span class="tl-card-suit">' + escapeHtml(visual.suitSymbol) + '</span>';

    const codeChip = document.createElement('div');
    codeChip.className = 'tl-card-code';
    codeChip.textContent = card.code;

    el.append(cornerTop, center, cornerBottom, codeChip);
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

  function renderStatusTimeline() {
    if (!els.statusTimeline) {
      return;
    }
    const ready = state.players.length === 4;
    const started = state.started;
    const dealt = started && state.playCount === 0 && !state.gameOver;
    const playing = started && !state.gameOver && state.playCount > 0;
    const ended = state.gameOver;
    const turnPlayer = getCurrentPlayer();
    const winnerPlayer = findPlayerById(state.winnerId);

    const steps = [
      { label: 'San sang', state: ready ? 'done' : 'active' },
      { label: 'Chia bai', state: dealt ? 'active' : (started ? 'done' : 'off') },
      { label: 'Dang danh', state: playing ? 'active' : ((started && (ended || dealt)) ? 'done' : 'off') },
      { label: 'Ket thuc', state: ended ? 'active' : 'off' }
    ];

    const phaseLabel = ended
      ? ('Ket thuc - ' + (winnerPlayer ? winnerPlayer.name + ' thang' : 'co nguoi thang'))
      : started
        ? ('Dang choi - luot ' + (turnPlayer ? turnPlayer.name : '-'))
        : 'San sang bat dau van moi';
    const eventLabel = state.message || '-';

    els.statusTimeline.innerHTML =
      '<div class="tl-status-timeline__track">' +
        steps.map((step) =>
          '<div class="tl-status-step is-' + step.state + '">' +
            '<span class="tl-status-step__dot" aria-hidden="true"></span>' +
            '<span class="tl-status-step__label">' + escapeHtml(step.label) + '</span>' +
          '</div>'
        ).join('') +
      '</div>' +
      '<div class="tl-status-timeline__summary">' +
        '<span class="tl-status-summary-chip">' + escapeHtml(phaseLabel) + '</span>' +
        '<span class="tl-status-summary-chip is-muted">' + escapeHtml(eventLabel) + '</span>' +
      '</div>';
  }

  function updateActionButtons() {
    const mePlayer = state.players.find((p) => p.isHuman);
    const myTurn = !!(mePlayer && state.currentTurnId === mePlayer.id && !state.gameOver);
    const hasSelection = state.selectedCodes.size > 0;
    const canPass = !!(myTurn && canCurrentPlayerPass(mePlayer));

    if (els.playBtn) els.playBtn.disabled = !myTurn || !hasSelection;
    if (els.passBtn) els.passBtn.disabled = !canPass;
    if (els.clearBtn) els.clearBtn.disabled = state.selectedCodes.size === 0;
    if (els.surrenderBtn) els.surrenderBtn.disabled = !state.started || state.gameOver;
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

  function initialsOfName(name) {
    const parts = String(name || '').trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  function cardVisual(card) {
    const code = String(card?.code || '').trim().toUpperCase();
    const suitCode = String(card?.suitCode || code.slice(-1) || '').toUpperCase();
    const rankText = code.length >= 2 ? code.slice(0, -1) : (String(card?.label || '').trim() || '?');
    const suitMeta = suitVisualMeta(suitCode);
    return {
      rankText,
      suitCode,
      suitSymbol: suitMeta.symbol,
      suitNameVi: suitMeta.nameVi,
      red: typeof card?.red === 'boolean' ? card.red : suitMeta.red
    };
  }

  function suitVisualMeta(suitCode) {
    switch (String(suitCode || '').toUpperCase()) {
      case 'H': return { symbol: '♥', nameVi: 'co', red: true };
      case 'D': return { symbol: '♦', nameVi: 'ro', red: true };
      case 'C': return { symbol: '♣', nameVi: 'tep', red: false };
      case 'S':
      default:
        return { symbol: '♠', nameVi: 'bich', red: false };
    }
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

  function restartAnimation(el, className) {
    if (!el) {
      return;
    }
    el.classList.remove(className);
    void el.offsetWidth;
    el.classList.add(className);
    window.setTimeout(() => {
      el.classList.remove(className);
    }, 420);
  }

  function escapeHtml(value) {
    return String(value || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }
})();
