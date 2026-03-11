(function () {
  const ROOM_AUTO_REFRESH_MS = 10000;
  const ROOM_TOPIC_MAP = {
    caro: { topic: '/topic/lobby.rooms', requestDestination: '' },
    cards: { topic: '/topic/tienlen.rooms', requestDestination: '/app/tienlen.roomList' },
    chess: { topic: '/topic/chess.rooms', requestDestination: '/app/chess.roomList' },
    xiangqi: { topic: '/topic/xiangqi.rooms', requestDestination: '/app/xiangqi.roomList' }
  };
  const boot = window.OnlineHubBoot || {};
  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === 'function')
    ? window.CaroUrl.path
    : (v) => v;

  const state = {
    gameCode: String(boot.gameCode || '').trim(),
    gameName: String(boot.gameName || '').trim(),
    roomId: String(boot.selectedRoomId || '').trim(),
    onlineSupportedNow: Boolean(boot.onlineSupportedNow),
    supportsSpectateNow: Boolean(boot.supportsSpectateNow),
    playUrlBase: String(boot.playUrlBase || '').trim(),
    playRoomParam: String(boot.playRoomParam || '').trim(),
    playUrlTemplate: String(boot.playUrlTemplate || '').trim(),
    spectateUrlTemplate: String(boot.spectateUrlTemplate || '').trim(),
    spectateParamName: String(boot.spectateParamName || '').trim(),
    spectateParamValue: String(boot.spectateParamValue || '').trim(),
    inviteUrlPathTemplate: String(boot.inviteUrlPathTemplate || '').trim(),
    roomRowsById: Object.create(null)
  };

  const els = {};
  let roomAutoRefreshTimer = 0;
  let roomRealtimeClient = null;

  document.addEventListener('DOMContentLoaded', () => {
    bindEls();
    bindActions();
    if (els.roomInput && state.roomId) {
      els.roomInput.value = state.roomId;
    }
    syncRoomUi();
    initRealtimeRoomUpdates();
    loadRooms(false);
    startRoomAutoRefresh();
    window.addEventListener('beforeunload', stopRealtimeRoomUpdates);
  });

  function bindEls() {
    els.roomInput = document.getElementById('onlineHubRoomIdInput');
    els.createBtn = document.getElementById('onlineHubCreateBtn');
    els.joinBtn = document.getElementById('onlineHubJoinBtn');
    els.goPlayBtn = document.getElementById('onlineHubGoPlayBtn');
    els.goSpectateBtn = document.getElementById('onlineHubGoSpectateBtn');
    els.refreshBtn = document.getElementById('onlineHubRefreshBtn');
    els.currentRoom = document.getElementById('onlineHubCurrentRoom');
    els.status = document.getElementById('onlineHubStatus');
    els.roomList = document.getElementById('onlineHubRoomList');
    els.inviteUrl = document.getElementById('onlineHubInviteUrl');
    els.copyInviteBtn = document.getElementById('onlineHubCopyInviteBtn');
    els.openInviteBtn = document.getElementById('onlineHubOpenInviteBtn');
    els.spectateInviteWrap = document.getElementById('onlineHubSpectateInviteWrap');
    els.spectateUrl = document.getElementById('onlineHubSpectateUrl');
    els.copySpectateBtn = document.getElementById('onlineHubCopySpectateBtn');
    els.openSpectateBtn = document.getElementById('onlineHubOpenSpectateBtn');
    els.roomListNote = document.getElementById('onlineHubRoomListNote');
  }

  function bindActions() {
    els.createBtn?.addEventListener('click', async () => {
      const created = await createRoomViaApi();
      let nextRoomId = normalizeRoomId(created?.roomId);
      if (!nextRoomId && supportsClientGeneratedRoom(state.gameCode)) {
        nextRoomId = generateRoomId();
      }
      if (!nextRoomId) {
        setStatus('Khong tao duoc phong. Hay thu lai.');
        syncRoomUi();
        return;
      }
      state.roomId = nextRoomId;
      if (els.roomInput) {
        els.roomInput.value = nextRoomId;
      }
      setRoomQueryInUrl(nextRoomId);
      syncRoomUi();
      if (enterRoom(nextRoomId)) {
        return;
      }
      setStatus(created?.serverCreated
        ? ('Da tao phong moi: ' + nextRoomId)
        : ('Da tao ma phong moi: ' + nextRoomId));
    });

    els.joinBtn?.addEventListener('click', () => {
      const roomId = normalizeRoomId(els.roomInput?.value);
      if (enterRoom(roomId)) {
        return;
      }
      if (!roomId) {
        setStatus('Vui long nhap ma phong');
        return;
      }
      state.roomId = roomId;
      if (isRoomFull(selectedRoomRow()) && state.supportsSpectateNow) {
        setStatus('Phong da day. Bam "Vao che do xem" de theo doi tran dau.');
      } else {
        setStatus('Da dien ma phong ' + roomId);
      }
      setRoomQueryInUrl(roomId);
      syncRoomUi();
    });

    els.goPlayBtn?.addEventListener('click', () => {
      enterRoom(state.roomId);
    });

    els.goSpectateBtn?.addEventListener('click', () => {
      if (!state.roomId || !state.supportsSpectateNow || !hasSpectateTarget()) {
        return;
      }
      const target = buildPlayUrl(state.roomId, true);
      if (target) {
        window.location.href = target;
      }
    });

    els.refreshBtn?.addEventListener('click', () => {
      setStatus('Dang lam moi danh sach phong...');
      loadRooms(false);
    });

    els.copyInviteBtn?.addEventListener('click', async () => {
      const invite = buildInviteUrl();
      if (!invite) return;
      try {
        await navigator.clipboard.writeText(invite);
        setStatus('Da sao chep link moi');
      } catch (_) {
        if (els.inviteUrl) {
          els.inviteUrl.focus();
          els.inviteUrl.select();
        }
        setStatus('Khong the sao chep tu dong. Hay sao chep thu cong');
      }
    });

    els.copySpectateBtn?.addEventListener('click', async () => {
      const spectateInvite = buildSpectateInviteUrl();
      if (!spectateInvite) return;
      try {
        await navigator.clipboard.writeText(spectateInvite);
        setStatus('Da sao chep link xem');
      } catch (_) {
        if (els.spectateUrl) {
          els.spectateUrl.focus();
          els.spectateUrl.select();
        }
        setStatus('Khong the sao chep tu dong. Hay sao chep thu cong');
      }
    });

    els.roomInput?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        els.joinBtn?.click();
      }
    });
    els.roomInput?.addEventListener('paste', () => {
      window.setTimeout(() => {
        if (normalizeRoomId(els.roomInput?.value)) {
          els.joinBtn?.click();
        }
      }, 0);
    });
  }

  function startRoomAutoRefresh() {
    stopRoomAutoRefresh();
    roomAutoRefreshTimer = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        loadRooms(true);
      }
    }, ROOM_AUTO_REFRESH_MS);
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        loadRooms(true);
      }
    });
    window.addEventListener('beforeunload', stopRoomAutoRefresh);
  }

  function stopRoomAutoRefresh() {
    if (roomAutoRefreshTimer) {
      window.clearInterval(roomAutoRefreshTimer);
      roomAutoRefreshTimer = 0;
    }
  }

  function initRealtimeRoomUpdates() {
    const channel = roomTopicChannel(state.gameCode);
    if (!channel || !window.StompJs || typeof window.SockJS === 'undefined') {
      return;
    }

    roomRealtimeClient = new window.StompJs.Client({
      webSocketFactory: () => new window.SockJS(appPath('/ws'), null, {
        transports: ['websocket', 'xhr-streaming', 'xhr-polling']
      }),
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      connectionTimeout: 12000,
      onConnect: () => {
        roomRealtimeClient.subscribe(channel.topic, (frame) => {
          const payload = parseJsonSafe(frame?.body);
          if (!payload || typeof payload !== 'object') {
            return;
          }
          const rows = normalizeRealtimeRows(state.gameCode, payload.rooms);
          if (!rows) {
            return;
          }
          cacheRoomRows(rows);
          renderRoomList(rows);
          syncRoomUi();
          setRoomListNote('Nguon du lieu: server realtime');
        });

        if (channel.requestDestination) {
          roomRealtimeClient.publish({
            destination: channel.requestDestination,
            body: '{}'
          });
        }
      }
    });
    roomRealtimeClient.activate();
  }

  function stopRealtimeRoomUpdates() {
    try {
      if (roomRealtimeClient) {
        roomRealtimeClient.deactivate();
      }
    } catch (_) {
    } finally {
      roomRealtimeClient = null;
    }
  }

  function roomTopicChannel(gameCode) {
    const key = String(gameCode || '').trim().toLowerCase();
    return ROOM_TOPIC_MAP[key] || null;
  }

  function normalizeRealtimeRows(gameCode, roomsPayload) {
    const key = String(gameCode || '').trim().toLowerCase();
    if (key === 'caro') {
      if (!Array.isArray(roomsPayload)) {
        return null;
      }
      return roomsPayload.map((roomId) => ({
        roomId: String(roomId || '').trim(),
        playerCount: 1,
        playerLimit: 2,
        note: 'Dang cho doi thu'
      })).filter((row) => row.roomId);
    }

    if (!Array.isArray(roomsPayload)) {
      return null;
    }

    return roomsPayload.map((room) => {
      const roomId = normalizeRoomId(room?.roomId);
      const playerCount = Number(room?.playerCount || 0);
      const playerLimit = Number(room?.playerLimit || 0);
      const note = String(room?.note || '').trim();
      return roomId ? {
        roomId,
        playerCount: Number.isFinite(playerCount) ? playerCount : 0,
        playerLimit: Number.isFinite(playerLimit) ? playerLimit : 0,
        note
      } : null;
    }).filter(Boolean);
  }

  async function loadRooms(silent) {
    if (!els.roomList) return;
    if (!silent) {
      els.roomList.innerHTML = '<div class="text-muted">Dang tai danh sach phong...</div>';
    }
    try {
      const res = await fetch(appPath('/online-hub/api/rooms?game=' + encodeURIComponent(state.gameCode)), {
        cache: 'no-store'
      });
      if (!res.ok) {
        throw new Error('HTTP ' + res.status);
      }
      const data = await res.json();
      const rooms = Array.isArray(data.rooms) ? data.rooms : [];
      cacheRoomRows(rooms);
      renderRoomList(rooms);
      syncRoomUi();
      setRoomListNote(data.onlineSupportedNow
        ? 'Nguon du lieu: server'
        : 'Nguon du lieu: server (chua co room online thuc te)');
    } catch (_) {
      if (!silent) {
        els.roomList.innerHTML = '<div class="text-danger">Khong tai duoc danh sach phong</div>';
      }
      setRoomListNote('Nguon du lieu: loi ket noi');
    }
  }

  function renderRoomList(rooms) {
    if (!els.roomList) return;
    if (!rooms || rooms.length === 0) {
      els.roomList.innerHTML = '<div class="text-muted">Chua co phong dang cho.</div>';
      return;
    }
    els.roomList.innerHTML = '';
    rooms.forEach((room) => {
      const row = document.createElement('div');
      row.className = 'border rounded p-2 d-flex justify-content-between align-items-center gap-2 flex-wrap';

      const info = document.createElement('div');
      info.className = 'small';
      const roomId = String(room.roomId || '').trim();
      const playerCount = Number(room.playerCount || 0);
      const playerLimit = Number(room.playerLimit || 0);
      const playerLimitLabel = playerLimit > 0 ? String(playerLimit) : '?';
      info.innerHTML =
        '<div><strong>' + escapeHtml(roomId) + '</strong></div>' +
        '<div class="text-muted">' +
          playerCount + '/' + playerLimitLabel + ' nguoi' +
          (room.note ? ' - ' + escapeHtml(String(room.note)) : '') +
        '</div>';

      const actions = document.createElement('div');
      actions.className = 'd-flex gap-2';

      const chooseBtn = document.createElement('button');
      chooseBtn.type = 'button';
      chooseBtn.className = 'btn btn-sm theme-outline-btn';
      chooseBtn.textContent = 'Dien ma';
      chooseBtn.addEventListener('click', () => {
        state.roomId = roomId;
        if (els.roomInput) els.roomInput.value = roomId;
        if (roomFull && state.supportsSpectateNow) {
          setStatus('Phong da day. Bam "Vao che do xem" de theo doi tran dau.');
        } else {
          setStatus('Da dien ma phong ' + roomId);
        }
        setRoomQueryInUrl(roomId);
        syncRoomUi();
      });

      const playBtn = document.createElement('button');
      playBtn.type = 'button';
      playBtn.className = 'btn btn-sm theme-outline-btn';
      const roomFull = playerLimit > 0 && playerCount >= playerLimit;
      playBtn.textContent = state.onlineSupportedNow ? (roomFull ? 'Phong day' : 'Vao ngay') : 'Moi nguoi choi';
      playBtn.disabled = !roomId || (roomFull && state.supportsSpectateNow);
      playBtn.addEventListener('click', () => {
        enterRoom(roomId);
      });

      const spectateBtn = document.createElement('button');
      spectateBtn.type = 'button';
      spectateBtn.className = 'btn btn-sm btn-info';
      spectateBtn.textContent = 'Xem';
      spectateBtn.disabled = !roomId || !state.supportsSpectateNow || !hasSpectateTarget();
      if (!state.supportsSpectateNow) {
        spectateBtn.style.display = 'none';
      }
      spectateBtn.addEventListener('click', () => {
          const target = buildPlayUrl(roomId, true);
          if (target) {
            window.location.href = target;
          }
      });

      actions.append(chooseBtn, playBtn, spectateBtn);
      row.append(info, actions);
      els.roomList.appendChild(row);
    });
  }

  function syncRoomUi() {
    if (els.currentRoom) {
      els.currentRoom.textContent = state.roomId || 'Chua chon';
    }
    const invite = buildInviteUrl();
    const spectateInvite = buildSpectateInviteUrl();
    const selectedRoom = selectedRoomRow();
    const selectedRoomFull = isRoomFull(selectedRoom);
    if (els.inviteUrl) {
      els.inviteUrl.value = invite || '';
    }
    if (els.spectateInviteWrap) {
      els.spectateInviteWrap.hidden = !state.supportsSpectateNow;
    }
    if (els.spectateUrl) {
      els.spectateUrl.value = spectateInvite || '';
    }
    if (els.copyInviteBtn) {
      els.copyInviteBtn.disabled = !invite;
    }
    if (els.copySpectateBtn) {
      els.copySpectateBtn.disabled = !spectateInvite || !state.supportsSpectateNow;
    }
    if (els.openInviteBtn) {
      els.openInviteBtn.href = invite || '#';
      els.openInviteBtn.classList.toggle('disabled', !invite);
      els.openInviteBtn.setAttribute('aria-disabled', invite ? 'false' : 'true');
    }
    if (els.openSpectateBtn) {
      els.openSpectateBtn.href = spectateInvite || '#';
      els.openSpectateBtn.classList.toggle('disabled', !spectateInvite || !state.supportsSpectateNow);
      els.openSpectateBtn.setAttribute('aria-disabled', (spectateInvite && state.supportsSpectateNow) ? 'false' : 'true');
    }
    if (els.goPlayBtn) {
      const canPlay = Boolean(
        state.roomId &&
        state.onlineSupportedNow &&
        hasPlayTarget() &&
        !(selectedRoomFull && state.supportsSpectateNow)
      );
      els.goPlayBtn.disabled = !canPlay;
      if (!state.onlineSupportedNow) {
        els.goPlayBtn.textContent = 'Ban choi truc tuyen chua san sang';
      } else if (selectedRoomFull && state.supportsSpectateNow) {
        els.goPlayBtn.textContent = 'Phong day - hay vao xem';
      } else {
        els.goPlayBtn.textContent = 'Vao ngay';
      }
    }
    if (els.goSpectateBtn) {
      els.goSpectateBtn.disabled = !(state.roomId && state.supportsSpectateNow && hasSpectateTarget());
      els.goSpectateBtn.hidden = !state.supportsSpectateNow;
    }
  }

  function buildInviteUrl() {
    if (!state.roomId || !state.inviteUrlPathTemplate) {
      return '';
    }
    const path = state.inviteUrlPathTemplate.replace('{roomId}', encodeURIComponent(state.roomId));
    return new URL(appPath(path), window.location.origin).toString();
  }

  function buildSpectateInviteUrl() {
    if (!state.supportsSpectateNow || !state.roomId) {
      return '';
    }
    const target = buildPlayUrl(state.roomId, true);
    if (!target) {
      return '';
    }
    return new URL(target, window.location.origin).toString();
  }

  async function createRoomViaApi() {
    try {
      const res = await fetch(appPath('/online-hub/api/create-room?game=' + encodeURIComponent(state.gameCode)), {
        method: 'POST'
      });
      if (!res.ok) {
        throw new Error('HTTP ' + res.status);
      }
      const data = await res.json();
      if (data && typeof data === 'object') {
        state.playUrlBase = String(data.playUrlBase || state.playUrlBase || '').trim();
        state.playRoomParam = String(data.playRoomParam || state.playRoomParam || '').trim();
        state.playUrlTemplate = String(data.playUrlTemplate || state.playUrlTemplate || '').trim();
        state.spectateUrlTemplate = String(data.spectateUrlTemplate || state.spectateUrlTemplate || '').trim();
        state.inviteUrlPathTemplate = String(data.inviteUrlPathTemplate || state.inviteUrlPathTemplate || '').trim();
      }
      return {
        roomId: normalizeRoomId(data?.roomId),
        serverCreated: Boolean(data?.serverCreated)
      };
    } catch (_) {
      return null;
    }
  }

  function enterRoom(roomId) {
    const normalizedRoomId = normalizeRoomId(roomId);
    if (!normalizedRoomId) {
      return false;
    }
    state.roomId = normalizedRoomId;
    if (els.roomInput) {
      els.roomInput.value = normalizedRoomId;
    }
    setRoomQueryInUrl(normalizedRoomId);
    syncRoomUi();
    if (!state.onlineSupportedNow || !hasPlayTarget()) {
      return false;
    }
    if (isRoomFull(selectedRoomRow()) && state.supportsSpectateNow) {
      setStatus('Phong da day. Hay vao che do xem.');
      return false;
    }
    const target = buildPlayUrl(normalizedRoomId, false);
    if (!target) {
      return false;
    }
    setStatus('Dang vao phong ' + normalizedRoomId + ' ...');
    window.location.href = target;
    return true;
  }

  function buildPlayUrl(roomId, spectateMode) {
    const normalizedRoomId = String(roomId || '').trim();
    if (!normalizedRoomId) {
      return '';
    }
    const template = spectateMode
      ? String(state.spectateUrlTemplate || '').trim()
      : String(state.playUrlTemplate || '').trim();
    if (template) {
      return applyRoomTemplate(template, normalizedRoomId);
    }
    if (!state.playUrlBase) {
      return '';
    }
    const url = new URL(appPath(state.playUrlBase), window.location.origin);
    url.searchParams.set(normalizeRoomParamName(state.playRoomParam), normalizedRoomId);

    if (spectateMode) {
      const name = normalizeSpectateParamName(state.spectateParamName);
      if (name) {
        url.searchParams.set(name, normalizeSpectateParamValue(state.spectateParamValue));
      }
    }
    return url.pathname + url.search;
  }

  function hasPlayTarget() {
    return Boolean(state.playUrlTemplate || state.playUrlBase);
  }

  function hasSpectateTarget() {
    return Boolean(state.spectateUrlTemplate || (state.supportsSpectateNow && state.playUrlBase));
  }

  function applyRoomTemplate(template, roomId) {
    const path = String(template || '').trim();
    if (!path) {
      return '';
    }
    const resolvedPath = path.replace('{roomId}', encodeURIComponent(String(roomId || '').trim()));
    const url = new URL(appPath(resolvedPath), window.location.origin);
    return url.pathname + url.search;
  }

  function setRoomQueryInUrl(roomId) {
    try {
      const url = new URL(window.location.href);
      if (roomId) {
        url.searchParams.set('roomId', roomId);
      } else {
        url.searchParams.delete('roomId');
      }
      window.history.replaceState({}, '', url.pathname + url.search + url.hash);
    } catch (_) {
    }
  }

  function generateRoomId() {
    const prefixes = {
      caro: 'CARO',
      chess: 'CHESS',
      xiangqi: 'XQ',
      cards: 'TL',
      blackjack: 'BJ',
      typing: 'TYP',
      quiz: 'QUIZ'
    };
    const prefix = prefixes[state.gameCode] || 'ROOM';
    const token = Math.random().toString(36).slice(2, 8).toUpperCase();
    return prefix + '-' + token;
  }

  function supportsClientGeneratedRoom(gameCode) {
    const normalized = String(gameCode || '').trim().toLowerCase();
    return normalized === 'caro'
      || normalized === 'cards'
      || normalized === 'chess'
      || normalized === 'xiangqi';
  }

  function normalizeRoomId(value) {
    const text = String(value || '').trim();
    return text || '';
  }

  function parseJsonSafe(raw) {
    try {
      return JSON.parse(raw || '{}');
    } catch (_) {
      return null;
    }
  }

  function cacheRoomRows(rooms) {
    const index = Object.create(null);
    for (const row of rooms || []) {
      const roomId = normalizeRoomId(row?.roomId);
      if (roomId) {
        index[roomId] = row;
      }
    }
    state.roomRowsById = index;
  }

  function selectedRoomRow() {
    if (!state.roomId) {
      return null;
    }
    return state.roomRowsById[state.roomId] || null;
  }

  function isRoomFull(room) {
    if (!room) {
      return false;
    }
    const playerCount = Number(room.playerCount || 0);
    const playerLimit = Number(room.playerLimit || 0);
    return playerLimit > 0 && playerCount >= playerLimit;
  }

  function setStatus(text) {
    if (els.status) {
      els.status.textContent = text || '-';
    }
  }

  function setRoomListNote(prefix) {
    if (!els.roomListNote) {
      return;
    }
    els.roomListNote.textContent = String(prefix || 'Nguon du lieu: server') + ' | ' + formatNowTime();
  }

  function formatNowTime() {
    const now = new Date();
    return now.toLocaleTimeString('vi-VN', { hour12: false });
  }

  function normalizeRoomParamName(value) {
    const text = String(value || '').trim();
    return text || 'roomId';
  }

  function normalizeSpectateParamName(value) {
    const text = String(value || '').trim();
    return text || '';
  }

  function normalizeSpectateParamValue(value) {
    const text = String(value || '').trim();
    return text || 'true';
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
