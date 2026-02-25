(function () {
  const boot = window.TienLenBoot || {};
  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === 'function')
    ? window.CaroUrl.path
    : (v) => v;
  const ui = window.CaroUi || {};

  const state = {
    connected: false,
    roomId: '',
    room: null,
    myHand: [],
    selectedCodes: new Set(),
    roomSub: null,
    client: null,
    pendingHandDealAnim: false,
    pendingTrickAnim: false,
    pendingTurnFlashUserId: '',
    lastRoomEventType: ''
  };

  const me = {
    userId: (boot.sessionUserId || '').trim(),
    displayName: (boot.sessionDisplayName || '').trim(),
    avatarPath: (boot.sessionAvatarPath || '').trim()
  };

  const els = {};

  document.addEventListener('DOMContentLoaded', () => {
    bindEls();
    bindActions();
    initClient();
    renderAll();
    const defaultRoomId = String(boot.defaultRoomId || '').trim();
    if (defaultRoomId) {
      els.roomInput.value = defaultRoomId;
    }
  });

  function bindEls() {
    els.roomInput = document.getElementById('tlRoomIdInput');
    els.joinBtn = document.getElementById('tlJoinBtn');
    els.startBtn = document.getElementById('tlStartBtn');
    els.surrenderBtn = document.getElementById('tlSurrenderBtn');
    els.leaveBtn = document.getElementById('tlLeaveBtn');
    els.playBtn = document.getElementById('tlPlayBtn');
    els.passBtn = document.getElementById('tlPassBtn');
    els.clearSelectBtn = document.getElementById('tlClearSelectBtn');
    els.selfName = document.getElementById('tlSelfName');
    els.selfUserId = document.getElementById('tlSelfUserId');
    els.currentRoomLabel = document.getElementById('tlCurrentRoomLabel');
    els.statusText = document.getElementById('tlStatusText');
    els.messageText = document.getElementById('tlMessageText');
    els.statusTimeline = document.getElementById('tlStatusTimeline');
    els.roomList = document.getElementById('tlRoomList');
    els.playersBoard = document.getElementById('tlPlayersBoard');
    els.turnText = document.getElementById('tlTurnText');
    els.currentTrickLabel = document.getElementById('tlCurrentTrickLabel');
    els.trickOwnerText = document.getElementById('tlTrickOwnerText');
    els.trickCards = document.getElementById('tlTrickCards');
    els.myHand = document.getElementById('tlMyHand');
    els.selectedCount = document.getElementById('tlSelectedCount');
    els.handCount = document.getElementById('tlHandCount');
    els.playerSlots = Array.from(document.querySelectorAll('#tlPlayersBoard .tienlen-player-slot'));
  }

  function bindActions() {
    els.joinBtn?.addEventListener('click', () => joinRoomByInput());
    els.startBtn?.addEventListener('click', () => publish('/app/tienlen.start', { roomId: state.roomId, userId: me.userId }));
    els.surrenderBtn?.addEventListener('click', () => surrenderCurrentRoom());
    els.leaveBtn?.addEventListener('click', () => leaveCurrentRoom());
    els.playBtn?.addEventListener('click', () => playSelectedCards());
    els.passBtn?.addEventListener('click', () => publish('/app/tienlen.pass', { roomId: state.roomId, userId: me.userId }));
    els.clearSelectBtn?.addEventListener('click', () => {
      state.selectedCodes.clear();
      renderHand();
      updateActionButtons();
    });
    els.roomInput?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        joinRoomByInput();
      }
    });
  }

  function initClient() {
    setStatus('Dang ket noi WebSocket...');
    const client = new window.StompJs.Client({
      webSocketFactory: () => new SockJS(appPath('/ws'), null, {
        transports: ['websocket', 'xhr-streaming', 'xhr-polling']
      }),
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        state.connected = true;
        setStatus('Da ket noi. Co the vao phong.');
        subscribeSharedChannels(client);
        requestRoomList();
        const defaultRoomId = String(els.roomInput?.value || '').trim();
        if (defaultRoomId && !state.roomId) {
          joinRoom(defaultRoomId);
        }
      },
      onStompError: () => {
        state.connected = false;
        setStatus('Loi STOMP');
        updateActionButtons();
      },
      onWebSocketClose: () => {
        state.connected = false;
        setStatus('Mat ket noi. Dang thu lai...');
        updateActionButtons();
      },
      onWebSocketError: () => {
        state.connected = false;
        setStatus('Loi WebSocket. Dang thu lai...');
        updateActionButtons();
      }
    });
    state.client = client;
    client.activate();
  }

  function subscribeSharedChannels(client) {
    client.subscribe('/topic/tienlen.rooms', (message) => {
      const payload = safeParse(message.body);
      renderRoomList(Array.isArray(payload.rooms) ? payload.rooms : []);
    });

    client.subscribe('/user/queue/tienlen.private', (message) => {
      const payload = safeParse(message.body);
      const privateState = payload && payload.state;
      if (!privateState || privateState.roomId !== state.roomId || privateState.userId !== me.userId) {
        return;
      }
      const prevSig = handSignature(state.myHand);
      state.myHand = Array.isArray(privateState.hand) ? privateState.hand.slice() : [];
      const nextSig = handSignature(state.myHand);
      if (prevSig !== nextSig && shouldAnimateDealFromPrivateState()) {
        state.pendingHandDealAnim = true;
      }
      cleanupSelected();
      renderHand();
      updateActionButtons();
    });

    client.subscribe('/user/queue/errors', (message) => {
      const payload = safeParse(message.body);
      if (!payload || (payload.scope && payload.scope !== 'tienlen')) {
        return;
      }
      if (payload.error) {
        setMessage(payload.error);
      }
    });
  }

  function joinRoomByInput() {
    const roomId = String(els.roomInput?.value || '').trim();
    if (!roomId) {
      setMessage('Vui long nhap ma phong');
      return;
    }
    joinRoom(roomId);
  }

  function joinRoom(roomId) {
    if (!state.connected) {
      setMessage('Chua ket noi xong, vui long doi...');
      return;
    }
    if (!me.userId) {
      setMessage('Khong xac dinh duoc user session');
      return;
    }

    if (state.roomSub) {
      state.roomSub.unsubscribe();
      state.roomSub = null;
    }
    state.roomId = roomId;
    state.room = null;
    state.myHand = [];
    state.selectedCodes.clear();
    state.currentRoomLabel.textContent = roomId;
    state.roomSub = state.client.subscribe('/topic/tienlen.room.' + roomId, onRoomMessage);
    publish('/app/tienlen.join', {
      roomId,
      userId: me.userId,
      displayName: me.displayName,
      avatarPath: me.avatarPath
    });
    setStatus('Dang vao phong ' + roomId + ' ...');
    renderAll();
  }

  function leaveCurrentRoom() {
    leaveCurrentRoomWithOptions(null);
  }

  function leaveCurrentRoomWithOptions(options) {
    const opts = options || {};
    if (!state.roomId) {
      return;
    }
    if (state.connected && opts.publish !== false) {
      publish(opts.destination || '/app/tienlen.leave', { roomId: state.roomId, userId: me.userId });
    }
    if (state.roomSub) {
      state.roomSub.unsubscribe();
      state.roomSub = null;
    }
    state.roomId = '';
    state.room = null;
    state.myHand = [];
    state.selectedCodes.clear();
    els.currentRoomLabel.textContent = 'Chua vao';
    setStatus(opts.statusText || 'Da roi phong');
    setMessage(opts.messageText || '-');
    renderAll();
    requestRoomList();
  }

  function surrenderCurrentRoom() {
    if (!state.roomId || !state.room || !playerExistsInRoom(me.userId)) {
      setMessage('Ban chua o trong phong');
      return;
    }
    if (!state.room.started || state.room.gameOver) {
      setMessage('Chi co the dau hang khi van dau dang dien ra');
      return;
    }
    if (!window.confirm('Ban chac chan muon dau hang va roi phong?')) {
      return;
    }
    leaveCurrentRoomWithOptions({
      destination: '/app/tienlen.surrender',
      statusText: 'Da dau hang va roi phong',
      messageText: 'Ban da dau hang va roi phong'
    });
  }

  function onRoomMessage(message) {
    const payload = safeParse(message.body);
    if (!payload) return;
    state.lastRoomEventType = String(payload.type || '');

    if (payload.type === 'ERROR' && payload.userId === me.userId && payload.error) {
      setMessage(payload.error);
    }

    if (payload.type === 'ROOM_CLOSED') {
      setMessage(payload.message || 'Phong da dong');
      leaveCurrentRoomWithOptions({ publish: false, statusText: 'Phong da dong', messageText: payload.message || 'Phong da dong' });
      return;
    }

    if (payload.room) {
      queueRoomAnimations(payload);
      state.room = payload.room;
      if (payload.room.roomId && payload.room.roomId !== state.roomId) {
        return;
      }
      if (payload.message) {
        setMessage(payload.message);
      } else if (payload.room.statusMessage) {
        setMessage(payload.room.statusMessage);
      }
      setStatus(roomStatusText(payload.room));
      if (payload.type === 'GAME_OVER' && payload.room.winnerUserId) {
        const youWin = payload.room.winnerUserId === me.userId;
        window.setTimeout(() => { if (ui.toast) { ui.toast(youWin ? 'Ban da thang van Tien len!' : 'Van dau ket thuc. Co nguoi da het bai.', { type: youWin ? 'success' : 'warning' }); } else if (typeof window !== 'undefined' && typeof window['alert'] === 'function') { window['alert'](youWin ? 'Ban da thang van Tien len!' : 'Van dau ket thuc. Co nguoi da het bai.'); } }, 50);
      }
      renderAll();
    }
  }

  function playSelectedCards() {
    if (!state.roomId || state.selectedCodes.size === 0) {
      return;
    }
    publish('/app/tienlen.play', {
      roomId: state.roomId,
      userId: me.userId,
      cardCodes: Array.from(state.selectedCodes)
    });
  }

  function requestRoomList() {
    if (!state.connected) return;
    publish('/app/tienlen.roomList', {});
  }

  function publish(destination, body) {
    if (!state.client || !state.connected) {
      return;
    }
    state.client.publish({
      destination,
      body: JSON.stringify(body || {})
    });
  }

  function renderAll() {
    renderPlayers();
    renderTrick();
    renderHand();
    renderStatusTimeline();
    updateActionButtons();
    if (els.selfName) els.selfName.textContent = me.displayName || 'Guest';
    if (els.selfUserId) els.selfUserId.textContent = me.userId || '-';
    if (els.currentRoomLabel) els.currentRoomLabel.textContent = state.roomId || 'Chua vao';
  }

  function renderRoomList(rooms) {
    if (!els.roomList) return;
    if (!rooms || rooms.length === 0) {
      els.roomList.innerHTML = '<div class="text-muted tl-empty-note">Chua co phong dang cho</div>';
      return;
    }
    els.roomList.innerHTML = '';
    rooms.forEach((room) => {
      const row = document.createElement('div');
      row.className = 'tl-room-item';
      const left = document.createElement('div');
      left.className = 'small flex-grow-1';
      const playerCount = Number(room.playerCount || 0);
      const playerLimit = Math.max(1, Number(room.playerLimit || 4));
      const fillPct = Math.min(100, Math.max(0, Math.round((playerCount / playerLimit) * 100)));
      left.innerHTML =
        '<div class="tl-room-item__title"><strong>' + escapeHtml(room.roomId || '') + '</strong></div>'
        + '<div class="tl-room-item__meta text-muted">' + playerCount + '/' + playerLimit + ' nguoi</div>'
        + '<div class="tl-room-item__meter" aria-hidden="true"><span style="width:' + fillPct + '%"></span></div>';
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'btn btn-sm theme-outline-btn tl-room-item__btn';
      btn.textContent = 'Vao';
      btn.addEventListener('click', () => {
        if (els.roomInput) {
          els.roomInput.value = room.roomId || '';
        }
        joinRoom(String(room.roomId || '').trim());
      });
      row.append(left, btn);
      els.roomList.appendChild(row);
    });
  }

  function renderPlayers() {
    if (!els.playersBoard) return;
    const room = state.room;
    const seatPlayers = Array.isArray(room?.players) ? room.players.slice().sort((a, b) => Number(a.seatIndex) - Number(b.seatIndex)) : [];
    const players = playersForTableOrder(seatPlayers);
    const passed = new Set(Array.isArray(room?.passedUserIds) ? room.passedUserIds : []);
    const slotEls = els.playerSlots || [];

    slotEls.forEach((slotEl, idx) => {
      const p = players[idx];
      const tablePos = String(slotEl?.dataset?.tablePos || tablePositionKey(idx));
      slotEl.classList.remove('is-turn', 'is-control', 'is-passed', 'is-me', 'is-winner');
      slotEl.classList.remove('turn-flash');
      slotEl.innerHTML = '';
      if (!p) {
        slotEl.innerHTML =
          '<div class="tl-player-empty">' +
          '<div class="tl-player-empty__icon">+</div>' +
          '<div class="tl-player-empty__text">Cho vi tri ' + escapeHtml(tablePositionLabel(tablePos)) + '</div>' +
          '</div>';
        return;
      }
      if (room?.currentTurnUserId === p.userId) slotEl.classList.add('is-turn');
      if (room?.controlUserId === p.userId) slotEl.classList.add('is-control');
      if (passed.has(p.userId)) slotEl.classList.add('is-passed');
      if (p.userId === me.userId) slotEl.classList.add('is-me');
      if (room?.winnerUserId && room.winnerUserId === p.userId) slotEl.classList.add('is-winner');
      if (state.pendingTurnFlashUserId && state.pendingTurnFlashUserId === p.userId && !room?.gameOver) {
        slotEl.classList.add('turn-flash');
      }

      const displayName = p.displayName || p.userId || 'Player';
      const badgeHtml = [
        (p.userId === me.userId) ? '<span class="tl-seat-badge tl-seat-badge--me">Ban</span>' : '',
        (p.bot) ? '<span class="tl-seat-badge tl-seat-badge--bot">Bot</span>' : '',
        (room?.currentTurnUserId === p.userId && !room?.gameOver) ? '<span class="tl-seat-badge tl-seat-badge--turn">Luot</span>' : '',
        (room?.controlUserId === p.userId && room?.started && !room?.gameOver) ? '<span class="tl-seat-badge tl-seat-badge--control">Nam vong</span>' : '',
        (passed.has(p.userId)) ? '<span class="tl-seat-badge tl-seat-badge--pass">Pass</span>' : '',
        (room?.winnerUserId === p.userId) ? '<span class="tl-seat-badge tl-seat-badge--win">Thang</span>' : ''
      ].filter(Boolean).join('');
      const flags = [];
      flags.push(tablePositionLabel(tablePos));
      flags.push('Ghe ' + (Number(p.seatIndex || 0) + 1));
      flags.push('Con ' + Number(p.handCount || 0) + ' la');
      slotEl.innerHTML =
        '<div class="tl-player-slot__head">' +
          '<div class="tl-player-avatar" aria-hidden="true">' + escapeHtml(initialsOfName(displayName)) + '</div>' +
          '<div class="tl-player-slot__identity">' +
            '<div class="tl-player-slot__name">' + escapeHtml(displayName) + '</div>' +
            '<div class="tl-player-slot__uid">' + escapeHtml(p.userId || '') + '</div>' +
          '</div>' +
        '</div>' +
        '<div class="tl-player-slot__meta">' +
          '<div class="tl-player-slot__chips">' + badgeHtml + '</div>' +
          '<div class="tl-player-slot__count">Con bai <strong>' + Number(p.handCount || 0) + '</strong></div>' +
        '</div>' +
        '<div class="tl-player-slot__footer">' + escapeHtml(flags.join(' | ')) + '</div>';
    });
    state.pendingTurnFlashUserId = '';

    els.turnText.textContent = currentTurnLabel();
  }

  function playersForTableOrder(seatPlayers) {
    const ordered = [null, null, null, null]; // top, right, bottom, left
    const players = Array.isArray(seatPlayers) ? seatPlayers.slice(0, 4) : [];
    if (players.length === 0) {
      return ordered;
    }

    const mePlayer = players.find((p) => p && p.userId === me.userId);
    const anchorSeat = normalizeSeatIndex(mePlayer?.seatIndex, 0);
    const used = new Set();

    players.forEach((player) => {
      if (!player) return;
      const seat = normalizeSeatIndex(player.seatIndex, -1);
      if (seat < 0) return;
      const delta = mod4(seat - anchorSeat);
      const tableIndex = deltaToTableIndex(delta);
      if (tableIndex < 0 || tableIndex >= ordered.length || ordered[tableIndex]) {
        return;
      }
      ordered[tableIndex] = player;
      used.add(player.userId);
    });

    let fillIdx = 0;
    players.forEach((player) => {
      if (!player || used.has(player.userId)) return;
      while (fillIdx < ordered.length && ordered[fillIdx]) fillIdx += 1;
      if (fillIdx < ordered.length) {
        ordered[fillIdx] = player;
      }
    });

    return ordered;
  }

  function deltaToTableIndex(delta) {
    switch (mod4(delta)) {
      case 0: return 2; // me -> bottom
      case 1: return 1; // next clockwise -> right
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
    const room = state.room;
    const trick = room?.currentTrick || null;
    if (els.currentTrickLabel) {
      els.currentTrickLabel.textContent = trick?.combinationLabel || 'Chua co';
    }
    if (els.trickOwnerText) {
      els.trickOwnerText.textContent = trick?.playedByDisplayName || '-';
    }
    if (!els.trickCards) return;
    els.trickCards.innerHTML = '';
    if (state.pendingTrickAnim) {
      restartAnimation(els.trickCards, 'tl-trick-pulse');
    }
    const cards = Array.isArray(trick?.cards) ? trick.cards : [];
    if (cards.length === 0) {
      state.pendingTrickAnim = false;
      const muted = document.createElement('div');
      muted.className = 'text-muted small';
      muted.textContent = 'Chua co bo bai nao';
      els.trickCards.appendChild(muted);
      return;
    }
    const animateEnter = state.pendingTrickAnim;
    cards.forEach((card, idx) => {
      const el = renderCardElement(card, false, true);
      if (animateEnter) {
        el.classList.add('tl-card-enter-play');
        el.style.animationDelay = String(idx * 40) + 'ms';
      }
      els.trickCards.appendChild(el);
    });
    state.pendingTrickAnim = false;
  }

  function renderHand() {
    if (!els.myHand) return;
    els.myHand.innerHTML = '';
    if (!Array.isArray(state.myHand) || state.myHand.length === 0) {
      els.myHand.innerHTML = '<div class="text-muted small">Chua co bai tren tay (vao phong va bat dau van).</div>';
      if (els.handCount) els.handCount.textContent = '0';
      if (els.selectedCount) els.selectedCount.textContent = '0';
      return;
    }

    const isMyTurn = roomTurnIsMine();
    const animateDeal = state.pendingHandDealAnim;
    state.myHand.forEach((card, idx) => {
      const selected = state.selectedCodes.has(card.code);
      const el = renderCardElement(card, selected, false);
      if (!isMyTurn) {
        el.classList.add('disabled');
      }
      if (animateDeal) {
        el.classList.add('tl-card-enter-deal');
        el.style.animationDelay = String(Math.min(idx, 12) * 24) + 'ms';
      }
      el.addEventListener('click', () => {
        if (!roomTurnIsMine()) {
          return;
        }
        if (state.selectedCodes.has(card.code)) {
          state.selectedCodes.delete(card.code);
        } else {
          state.selectedCodes.add(card.code);
        }
        renderHand();
        updateActionButtons();
      });
      els.myHand.appendChild(el);
    });
    state.pendingHandDealAnim = false;

    if (els.handCount) els.handCount.textContent = String(state.myHand.length);
    if (els.selectedCount) els.selectedCount.textContent = String(state.selectedCodes.size);
  }

  function renderCardElement(card, selected, readonly) {
    const el = document.createElement(readonly ? 'div' : 'button');
    if (!readonly) el.type = 'button';
    el.className = 'tienlen-card-btn';
    const code = String(card?.code || '').trim();
    const visual = cardVisual(card);
    if (visual.red) el.classList.add('red');
    if (selected) el.classList.add('selected');
    if (readonly) el.classList.add('readonly');
    el.setAttribute('title', code || (visual.rankText + visual.suitSymbol));
    el.setAttribute('aria-label', (visual.rankText || '') + ' ' + (visual.suitNameVi || ''));
    el.dataset.code = code;

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
    codeChip.textContent = code;

    el.append(cornerTop, center, cornerBottom, codeChip);
    return el;
  }

  function cleanupSelected() {
    const validCodes = new Set((state.myHand || []).map(c => c.code));
    for (const code of Array.from(state.selectedCodes)) {
      if (!validCodes.has(code)) {
        state.selectedCodes.delete(code);
      }
    }
  }

  function updateActionButtons() {
    const room = state.room;
    const inRoom = !!state.roomId;
    const isMine = roomTurnIsMine();
    const canStart = !!(room && room.canStart && playerExistsInRoom(me.userId));
    const hasSelection = state.selectedCodes.size > 0;
    const canPass = !!(isMine && room && room.currentTrick && room.controlUserId !== me.userId && room.started && !room.gameOver);
    const canSurrender = !!(state.connected && inRoom && room && room.started && !room.gameOver && playerExistsInRoom(me.userId));

    if (els.joinBtn) els.joinBtn.disabled = !state.connected;
    if (els.startBtn) els.startBtn.disabled = !state.connected || !inRoom || !canStart;
    if (els.surrenderBtn) els.surrenderBtn.disabled = !canSurrender;
    if (els.leaveBtn) els.leaveBtn.disabled = !inRoom;
    if (els.playBtn) els.playBtn.disabled = !state.connected || !inRoom || !isMine || !hasSelection || !(room && room.started && !room.gameOver);
    if (els.passBtn) els.passBtn.disabled = !state.connected || !inRoom || !canPass;
    if (els.clearSelectBtn) els.clearSelectBtn.disabled = state.selectedCodes.size === 0;

    if (els.selectedCount) els.selectedCount.textContent = String(state.selectedCodes.size);
    if (els.handCount) els.handCount.textContent = String((state.myHand || []).length);
  }

  function playerExistsInRoom(userId) {
    return !!state.room?.players?.some?.((p) => p.userId === userId);
  }

  function roomTurnIsMine() {
    return !!(state.room && state.room.currentTurnUserId && state.room.currentTurnUserId === me.userId);
  }

  function currentTurnLabel() {
    if (!state.room) return '-';
    if (state.room.gameOver) {
      if (state.room.winnerUserId) {
        return state.room.winnerUserId === me.userId ? 'Ban da thang' : 'Van dau ket thuc';
      }
      return 'Van dau ket thuc';
    }
    if (!state.room.currentTurnUserId) return 'Dang cho';
    const player = (state.room.players || []).find(p => p.userId === state.room.currentTurnUserId);
    return player ? player.displayName : state.room.currentTurnUserId;
  }

  function roomStatusText(room) {
    if (!room) return 'Chua vao phong';
    if (room.gameOver && room.winnerUserId) {
      const winner = (room.players || []).find(p => p.userId === room.winnerUserId);
      return 'Ket thuc - ' + (winner?.displayName || room.winnerUserId) + ' thang';
    }
    if (!room.started) {
      return 'Phong ' + room.playerCount + '/' + room.playerLimit + (room.canStart ? ' - co the bat dau' : ' - cho du 4 nguoi');
    }
    const turnName = currentTurnLabel();
    return 'Dang choi - Luot: ' + turnName;
  }

  function setStatus(text) {
    if (els.statusText) {
      els.statusText.textContent = text || '-';
    }
  }

  function setMessage(text) {
    if (els.messageText) {
      els.messageText.textContent = text || '-';
    }
  }

  function renderStatusTimeline() {
    if (!els.statusTimeline) {
      return;
    }
    const room = state.room;
    const inRoom = Boolean(state.roomId && playerExistsInRoom(me.userId));
    const enoughPlayers = Boolean(room && Number(room.playerCount || 0) >= Number(room.playerLimit || 4));
    const playing = Boolean(room && room.started && !room.gameOver);
    const ended = Boolean(room && room.gameOver);
    const waiting = Boolean(inRoom && room && !room.started && !room.gameOver);
    const canStart = Boolean(room && room.canStart);

    const steps = [
      { label: 'Ket noi', state: state.connected ? 'done' : 'active' },
      { label: 'Vao phong', state: inRoom ? 'done' : (state.connected ? 'active' : 'off') },
      { label: 'Du 4', state: enoughPlayers ? 'done' : (inRoom ? 'active' : 'off') },
      { label: 'Dang choi', state: playing ? 'active' : ((room && room.started) ? 'done' : 'off') },
      { label: 'Ket thuc', state: ended ? 'active' : 'off' }
    ];

    const phaseLabel = ended
      ? 'Van dau ket thuc'
      : playing
        ? ('Dang choi - luot ' + currentTurnLabel())
        : waiting
          ? (canStart ? 'Da du 4 nguoi - san sang bat dau' : 'Dang cho du 4 nguoi')
          : (inRoom ? 'Dang dong bo phong' : 'Chua vao phong');
    const eventLabel = room?.statusMessage || '-';

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

  function queueRoomAnimations(payload) {
    const room = payload && payload.room ? payload.room : null;
    if (!room) {
      return;
    }
    const prevRoom = state.room;
    const prevStarted = Boolean(prevRoom && prevRoom.started);
    const nextStarted = Boolean(room.started);
    const prevTurn = String(prevRoom?.currentTurnUserId || '');
    const nextTurn = String(room.currentTurnUserId || '');
    const prevTrick = trickSignature(prevRoom?.currentTrick);
    const nextTrick = trickSignature(room.currentTrick);
    const type = String(payload.type || '');

    if (!prevStarted && nextStarted) {
      state.pendingHandDealAnim = true;
      state.pendingTurnFlashUserId = nextTurn;
    }
    if (prevTurn && nextTurn && prevTurn !== nextTurn && !room.gameOver) {
      state.pendingTurnFlashUserId = nextTurn;
    } else if (!prevTurn && nextTurn && (type === 'GAME_STARTED' || type === 'ROOM_STATE')) {
      state.pendingTurnFlashUserId = nextTurn;
    }
    if (prevTrick !== nextTrick && (type === 'PLAYED' || type === 'ROUND_RESET' || type === 'GAME_OVER')) {
      state.pendingTrickAnim = true;
    }
  }

  function shouldAnimateDealFromPrivateState() {
    const room = state.room;
    if (!room || !room.started || room.gameOver) {
      return false;
    }
    return Number(room.playCount || 0) <= 0;
  }

  function handSignature(cards) {
    if (!Array.isArray(cards) || cards.length === 0) {
      return '';
    }
    return cards.map((c) => String(c?.code || '')).join('|');
  }

  function trickSignature(trick) {
    const cards = Array.isArray(trick?.cards) ? trick.cards : [];
    if (cards.length === 0) {
      return '';
    }
    return cards.map((c) => String(c?.code || '')).join('|') + '|' + String(trick?.playedByUserId || '');
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

  function initialsOfName(name) {
    const parts = String(name || '').trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  function cardVisual(card) {
    const code = String(card?.code || '').trim().toUpperCase();
    const suitCode = code.slice(-1);
    const rankText = code.length >= 2 ? code.slice(0, -1) : (String(card?.label || '').trim() || '?');
    const suitMeta = suitVisualMeta(suitCode);
    return {
      rankText,
      suitCode,
      suitSymbol: suitMeta.symbol,
      suitNameVi: suitMeta.nameVi,
      red: suitMeta.red
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

  function safeParse(json) {
    try {
      return JSON.parse(json || '{}');
    } catch (_) {
      return null;
    }
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

