(function () {
  const boot = window.TienLenBoot || {};
  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === 'function')
    ? window.CaroUrl.path
    : (v) => v;

  const state = {
    connected: false,
    roomId: '',
    room: null,
    myHand: [],
    selectedCodes: new Set(),
    roomSub: null,
    client: null
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
    els.leaveBtn = document.getElementById('tlLeaveBtn');
    els.playBtn = document.getElementById('tlPlayBtn');
    els.passBtn = document.getElementById('tlPassBtn');
    els.clearSelectBtn = document.getElementById('tlClearSelectBtn');
    els.selfName = document.getElementById('tlSelfName');
    els.selfUserId = document.getElementById('tlSelfUserId');
    els.currentRoomLabel = document.getElementById('tlCurrentRoomLabel');
    els.statusText = document.getElementById('tlStatusText');
    els.messageText = document.getElementById('tlMessageText');
    els.roomList = document.getElementById('tlRoomList');
    els.playersBoard = document.getElementById('tlPlayersBoard');
    els.turnText = document.getElementById('tlTurnText');
    els.currentTrickLabel = document.getElementById('tlCurrentTrickLabel');
    els.trickOwnerText = document.getElementById('tlTrickOwnerText');
    els.trickCards = document.getElementById('tlTrickCards');
    els.myHand = document.getElementById('tlMyHand');
    els.selectedCount = document.getElementById('tlSelectedCount');
    els.handCount = document.getElementById('tlHandCount');
  }

  function bindActions() {
    els.joinBtn?.addEventListener('click', () => joinRoomByInput());
    els.startBtn?.addEventListener('click', () => publish('/app/tienlen.start', { roomId: state.roomId, userId: me.userId }));
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
      state.myHand = Array.isArray(privateState.hand) ? privateState.hand.slice() : [];
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
    if (!state.roomId) {
      return;
    }
    if (state.connected) {
      publish('/app/tienlen.leave', { roomId: state.roomId, userId: me.userId });
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
    setStatus('Da roi phong');
    setMessage('-');
    renderAll();
    requestRoomList();
  }

  function onRoomMessage(message) {
    const payload = safeParse(message.body);
    if (!payload) return;

    if (payload.type === 'ERROR' && payload.userId === me.userId && payload.error) {
      setMessage(payload.error);
    }

    if (payload.type === 'ROOM_CLOSED') {
      setMessage(payload.message || 'Phong da dong');
      leaveCurrentRoom();
      return;
    }

    if (payload.room) {
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
        window.setTimeout(() => alert(youWin ? 'Ban da thang van Tien len!' : 'Van dau ket thuc. Co nguoi da het bai.'), 50);
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
    updateActionButtons();
    if (els.selfName) els.selfName.textContent = me.displayName || 'Guest';
    if (els.selfUserId) els.selfUserId.textContent = me.userId || '-';
    if (els.currentRoomLabel) els.currentRoomLabel.textContent = state.roomId || 'Chua vao';
  }

  function renderRoomList(rooms) {
    if (!els.roomList) return;
    if (!rooms || rooms.length === 0) {
      els.roomList.innerHTML = '<div class="text-muted">Chua co phong dang cho</div>';
      return;
    }
    els.roomList.innerHTML = '';
    rooms.forEach((room) => {
      const row = document.createElement('div');
      row.className = 'd-flex justify-content-between align-items-center border rounded p-2 gap-2';
      const left = document.createElement('div');
      left.className = 'small';
      left.innerHTML = '<div><strong>' + escapeHtml(room.roomId || '') + '</strong></div>'
        + '<div class="text-muted">' + Number(room.playerCount || 0) + '/' + Number(room.playerLimit || 4) + ' nguoi</div>';
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'btn btn-sm theme-outline-btn';
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
    const players = Array.isArray(room?.players) ? room.players.slice().sort((a, b) => Number(a.seatIndex) - Number(b.seatIndex)) : [];
    const passed = new Set(Array.isArray(room?.passedUserIds) ? room.passedUserIds : []);
    const slotEls = els.playersBoard.querySelectorAll('.tienlen-player-slot');

    slotEls.forEach((slotEl, idx) => {
      const p = players[idx];
      slotEl.classList.remove('is-turn', 'is-control', 'is-passed', 'is-me');
      slotEl.innerHTML = '';
      if (!p) {
        slotEl.textContent = 'Chua co nguoi choi';
        return;
      }
      if (room?.currentTurnUserId === p.userId) slotEl.classList.add('is-turn');
      if (room?.controlUserId === p.userId) slotEl.classList.add('is-control');
      if (passed.has(p.userId)) slotEl.classList.add('is-passed');
      if (p.userId === me.userId) slotEl.classList.add('is-me');

      const name = document.createElement('div');
      name.className = 'fw-bold theme-text';
      name.textContent = (idx + 1) + '. ' + (p.displayName || p.userId || 'Player');
      const meta = document.createElement('div');
      meta.className = 'small';
      meta.innerHTML = '<span>Con bai: <strong>' + Number(p.handCount || 0) + '</strong></span>'
        + (p.userId === me.userId ? ' <span class="badge text-bg-info ms-1">Ban</span>' : '');
      const uid = document.createElement('div');
      uid.className = 'small text-muted';
      uid.textContent = p.userId || '';
      slotEl.append(name, meta, uid);
    });

    els.turnText.textContent = currentTurnLabel();
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
    const cards = Array.isArray(trick?.cards) ? trick.cards : [];
    if (cards.length === 0) {
      const muted = document.createElement('div');
      muted.className = 'text-muted small';
      muted.textContent = 'Chua co bo bai nao';
      els.trickCards.appendChild(muted);
      return;
    }
    cards.forEach((card) => {
      els.trickCards.appendChild(renderCardElement(card, false, true));
    });
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
    state.myHand.forEach((card) => {
      const selected = state.selectedCodes.has(card.code);
      const el = renderCardElement(card, selected, false);
      if (!isMyTurn) {
        el.classList.add('disabled');
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

    if (els.handCount) els.handCount.textContent = String(state.myHand.length);
    if (els.selectedCount) els.selectedCount.textContent = String(state.selectedCodes.size);
  }

  function renderCardElement(card, selected, readonly) {
    const el = document.createElement(readonly ? 'div' : 'button');
    if (!readonly) el.type = 'button';
    el.className = 'tienlen-card-btn';
    const label = String(card?.label || card?.code || '').trim();
    const code = String(card?.code || '').trim();
    const isRed = /[♦♥]$/.test(label);
    if (isRed) el.classList.add('red');
    if (selected) el.classList.add('selected');
    el.setAttribute('title', code || label);

    const top = document.createElement('div');
    top.className = 'fw-bold';
    top.textContent = label;
    const bottom = document.createElement('div');
    bottom.className = 'small';
    bottom.textContent = code;
    el.append(top, bottom);
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

    if (els.joinBtn) els.joinBtn.disabled = !state.connected;
    if (els.startBtn) els.startBtn.disabled = !state.connected || !inRoom || !canStart;
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
