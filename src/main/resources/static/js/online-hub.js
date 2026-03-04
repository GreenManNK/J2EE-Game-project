(function () {
  const ROOM_AUTO_REFRESH_MS = 10000;
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
    spectateParamName: String(boot.spectateParamName || '').trim(),
    spectateParamValue: String(boot.spectateParamValue || '').trim(),
    inviteUrlPathTemplate: String(boot.inviteUrlPathTemplate || '').trim(),
    roomRowsById: Object.create(null)
  };

  const els = {};
  let roomAutoRefreshTimer = 0;

  document.addEventListener('DOMContentLoaded', () => {
    bindEls();
    bindActions();
    if (els.roomInput && state.roomId) {
      els.roomInput.value = state.roomId;
    }
    syncRoomUi();
    loadRooms(false);
    startRoomAutoRefresh();
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
      const nextRoomId = created || generateRoomId();
      state.roomId = nextRoomId;
      if (els.roomInput) {
        els.roomInput.value = nextRoomId;
      }
      setStatus(created
        ? ('Da tao phong moi: ' + nextRoomId)
        : ('Da tao ma phong moi: ' + nextRoomId));
      setRoomQueryInUrl(nextRoomId);
      syncRoomUi();
    });

    els.joinBtn?.addEventListener('click', () => {
      const roomId = normalizeRoomId(els.roomInput?.value);
      if (!roomId) {
        setStatus('Vui long nhap ma phong');
        return;
      }
      state.roomId = roomId;
      if (isRoomFull(selectedRoomRow()) && state.supportsSpectateNow) {
        setStatus('Phong da day. Bam "Vao che do xem" de theo doi tran dau.');
      } else {
        setStatus('Da chon phong ' + roomId);
      }
      setRoomQueryInUrl(roomId);
      syncRoomUi();
    });

    els.goPlayBtn?.addEventListener('click', () => {
      if (!state.roomId || !state.onlineSupportedNow || !state.playUrlBase) {
        return;
      }
      if (isRoomFull(selectedRoomRow()) && state.supportsSpectateNow) {
        setStatus('Phong da day. Hay vao che do xem.');
        return;
      }
      const target = buildPlayUrl(state.roomId, false);
      if (target) {
        window.location.href = target;
      }
    });

    els.goSpectateBtn?.addEventListener('click', () => {
      if (!state.roomId || !state.supportsSpectateNow || !state.playUrlBase) {
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
        setStatus('Da copy link moi');
      } catch (_) {
        if (els.inviteUrl) {
          els.inviteUrl.focus();
          els.inviteUrl.select();
        }
        setStatus('Khong copy tu dong duoc. Hay copy thu cong');
      }
    });

    els.copySpectateBtn?.addEventListener('click', async () => {
      const spectateInvite = buildSpectateInviteUrl();
      if (!spectateInvite) return;
      try {
        await navigator.clipboard.writeText(spectateInvite);
        setStatus('Da copy link xem');
      } catch (_) {
        if (els.spectateUrl) {
          els.spectateUrl.focus();
          els.spectateUrl.select();
        }
        setStatus('Khong copy tu dong duoc. Hay copy thu cong');
      }
    });

    els.roomInput?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        els.joinBtn?.click();
      }
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
      chooseBtn.textContent = 'Chon phong';
      chooseBtn.addEventListener('click', () => {
        state.roomId = roomId;
        if (els.roomInput) els.roomInput.value = roomId;
        if (roomFull && state.supportsSpectateNow) {
          setStatus('Phong da day. Bam "Vao che do xem" de theo doi tran dau.');
        } else {
          setStatus('Da chon phong ' + roomId);
        }
        setRoomQueryInUrl(roomId);
        syncRoomUi();
      });

      const playBtn = document.createElement('button');
      playBtn.type = 'button';
      playBtn.className = 'btn btn-sm theme-outline-btn';
      const roomFull = playerLimit > 0 && playerCount >= playerLimit;
      playBtn.textContent = state.onlineSupportedNow ? (roomFull ? 'Phong day' : 'Vao ban') : 'Moi nguoi choi';
      playBtn.disabled = !roomId || (roomFull && state.supportsSpectateNow);
      playBtn.addEventListener('click', () => {
        state.roomId = roomId;
        if (els.roomInput) els.roomInput.value = roomId;
        setRoomQueryInUrl(roomId);
        syncRoomUi();
        if (state.onlineSupportedNow && state.playUrlBase) {
          els.goPlayBtn?.click();
        }
      });

      const spectateBtn = document.createElement('button');
      spectateBtn.type = 'button';
      spectateBtn.className = 'btn btn-sm btn-info';
      spectateBtn.textContent = 'Xem';
      spectateBtn.disabled = !roomId || !state.supportsSpectateNow || !state.playUrlBase;
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
        state.playUrlBase &&
        !(selectedRoomFull && state.supportsSpectateNow)
      );
      els.goPlayBtn.disabled = !canPlay;
      if (!state.onlineSupportedNow) {
        els.goPlayBtn.textContent = 'Gameplay online chua san sang';
      } else if (selectedRoomFull && state.supportsSpectateNow) {
        els.goPlayBtn.textContent = 'Phong day - hay vao xem';
      } else {
        els.goPlayBtn.textContent = 'Vao ban choi';
      }
    }
    if (els.goSpectateBtn) {
      els.goSpectateBtn.disabled = !(state.roomId && state.supportsSpectateNow && state.playUrlBase);
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
      const roomId = normalizeRoomId(data?.roomId);
      return roomId || '';
    } catch (_) {
      return '';
    }
  }

  function buildPlayUrl(roomId, spectateMode) {
    if (!state.playUrlBase || !roomId) {
      return '';
    }
    const url = new URL(appPath(state.playUrlBase), window.location.origin);
    url.searchParams.set(normalizeRoomParamName(state.playRoomParam), String(roomId).trim());

    if (spectateMode) {
      const name = normalizeSpectateParamName(state.spectateParamName);
      if (name) {
        url.searchParams.set(name, normalizeSpectateParamValue(state.spectateParamValue));
      }
    }
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

  function normalizeRoomId(value) {
    const text = String(value || '').trim();
    return text || '';
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
