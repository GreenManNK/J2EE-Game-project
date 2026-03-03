(function () {
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
    inviteUrlPathTemplate: String(boot.inviteUrlPathTemplate || '').trim()
  };

  const els = {};

  document.addEventListener('DOMContentLoaded', () => {
    bindEls();
    bindActions();
    if (els.roomInput && state.roomId) {
      els.roomInput.value = state.roomId;
    }
    syncRoomUi();
    loadRooms();
  });

  function bindEls() {
    els.roomInput = document.getElementById('onlineHubRoomIdInput');
    els.createBtn = document.getElementById('onlineHubCreateBtn');
    els.joinBtn = document.getElementById('onlineHubJoinBtn');
    els.goPlayBtn = document.getElementById('onlineHubGoPlayBtn');
    els.refreshBtn = document.getElementById('onlineHubRefreshBtn');
    els.currentRoom = document.getElementById('onlineHubCurrentRoom');
    els.status = document.getElementById('onlineHubStatus');
    els.roomList = document.getElementById('onlineHubRoomList');
    els.inviteUrl = document.getElementById('onlineHubInviteUrl');
    els.copyInviteBtn = document.getElementById('onlineHubCopyInviteBtn');
    els.openInviteBtn = document.getElementById('onlineHubOpenInviteBtn');
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
      setStatus('Da chon phong ' + roomId);
      setRoomQueryInUrl(roomId);
      syncRoomUi();
    });

    els.goPlayBtn?.addEventListener('click', () => {
      if (!state.roomId || !state.onlineSupportedNow || !state.playUrlBase) {
        return;
      }
      const target = buildPlayUrl(state.roomId, false);
      if (target) {
        window.location.href = target;
      }
    });

    els.refreshBtn?.addEventListener('click', () => loadRooms());

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

    els.roomInput?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        els.joinBtn?.click();
      }
    });
  }

  async function loadRooms() {
    if (!els.roomList) return;
    els.roomList.innerHTML = '<div class="text-muted">Dang tai danh sach phong...</div>';
    try {
      const res = await fetch(appPath('/online-hub/api/rooms?game=' + encodeURIComponent(state.gameCode)), {
        cache: 'no-store'
      });
      if (!res.ok) {
        throw new Error('HTTP ' + res.status);
      }
      const data = await res.json();
      renderRoomList(Array.isArray(data.rooms) ? data.rooms : []);
      if (els.roomListNote) {
        els.roomListNote.textContent = data.onlineSupportedNow ? 'Nguon du lieu: server' : 'Nguon du lieu: server (chua co room online thuc te)';
      }
    } catch (_) {
      els.roomList.innerHTML = '<div class="text-danger">Khong tai duoc danh sach phong</div>';
      if (els.roomListNote) {
        els.roomListNote.textContent = 'Nguon du lieu: loi ket noi';
      }
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
        setStatus('Da chon phong ' + roomId);
        setRoomQueryInUrl(roomId);
        syncRoomUi();
      });

      const playBtn = document.createElement('button');
      playBtn.type = 'button';
      playBtn.className = 'btn btn-sm theme-outline-btn';
      playBtn.textContent = state.onlineSupportedNow ? 'Vao ban' : 'Moi nguoi choi';
      playBtn.disabled = !roomId;
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
    if (els.inviteUrl) {
      els.inviteUrl.value = invite || '';
    }
    if (els.copyInviteBtn) {
      els.copyInviteBtn.disabled = !invite;
    }
    if (els.openInviteBtn) {
      els.openInviteBtn.href = invite || '#';
      els.openInviteBtn.classList.toggle('disabled', !invite);
      els.openInviteBtn.setAttribute('aria-disabled', invite ? 'false' : 'true');
    }
    if (els.goPlayBtn) {
      els.goPlayBtn.disabled = !(state.roomId && state.onlineSupportedNow && state.playUrlBase);
      els.goPlayBtn.textContent = state.onlineSupportedNow
        ? 'Vao ban choi'
        : 'Gameplay online chua san sang';
    }
  }

  function buildInviteUrl() {
    if (!state.roomId || !state.inviteUrlPathTemplate) {
      return '';
    }
    const path = state.inviteUrlPathTemplate.replace('{roomId}', encodeURIComponent(state.roomId));
    return new URL(appPath(path), window.location.origin).toString();
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

  function setStatus(text) {
    if (els.status) {
      els.status.textContent = text || '-';
    }
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
