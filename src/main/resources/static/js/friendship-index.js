(function(){
  const root = document.getElementById('friendshipRoot');
  if(!root) return;

  const uid = root.dataset.currentUserId || window.CaroUser?.get?.()?.userId || '';
  const form = document.getElementById('sendByEmailForm');
  const out = document.getElementById('out');
  if(!form || !out) return;

  const ui = window.CaroUi || {};
  const lists = {
    friends: document.getElementById('friendsList'),
    pending: document.getElementById('pendingRequestsList'),
    sent: document.getElementById('sentRequestsList')
  };

  function setStatus(message, ok){
    if (ui.setStatus) {
      ui.setStatus(out, message, ok);
    } else if (out) {
      out.textContent = String(message || '');
    }
  }

  function report(data, successMessage){
    if (ui.apiResult) {
      return ui.apiResult(data, { statusEl: out, successMessage });
    }
    const ok = !!(data && data.success);
    setStatus(ok ? successMessage : String(data?.error || data?.message || 'Thao tac that bai'), ok);
    return ok;
  }

  function reportError(err){
    const message = String(err?.message || err || 'Yeu cau that bai');
    setStatus(message, false);
    ui.toast?.(message, { type: 'danger' });
  }

  function ensureLogin(){
    if (!uid) {
      setStatus('Can dang nhap de su dung tinh nang ban be', false);
      ui.toast?.('Can dang nhap de su dung tinh nang ban be', { type: 'danger' });
      return false;
    }
    return true;
  }

  async function postJson(url, payload){
    const r = await fetch(url, {
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify(payload || {})
    });
    return r.json();
  }

  function refreshEmptyState(listEl, message){
    if (!listEl) return;
    const items = Array.from(listEl.querySelectorAll(':scope > .list-group-item'));
    const hasRealItem = items.some((li) => !li.classList.contains('text-muted'));
    const placeholder = items.find((li) => li.classList.contains('text-muted'));
    if (hasRealItem && placeholder) {
      placeholder.remove();
      return;
    }
    if (!hasRealItem && !placeholder) {
      const li = document.createElement('li');
      li.className = 'list-group-item text-muted small';
      li.textContent = message;
      listEl.appendChild(li);
    }
  }

  function removeRowFromButton(btn){
    const row = btn && btn.closest('.list-group-item');
    if (row) row.remove();
  }

  function appPath(path) {
    return window.CaroUrl?.path?.(path) || path;
  }

  function buildHref(path, params) {
    const base = appPath(path);
    const query = new URLSearchParams();
    Object.entries(params || {}).forEach(([key, value]) => {
      if (value == null || value === '') return;
      query.set(key, String(value));
    });
    const qs = query.toString();
    return qs ? (base + (base.includes('?') ? '&' : '?') + qs) : base;
  }

  function createButton(className, text, dataset) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = className;
    btn.textContent = text;
    Object.entries(dataset || {}).forEach(([k, v]) => {
      if (v != null) {
        btn.dataset[k] = String(v);
      }
    });
    return btn;
  }

  function createLink(className, text, href) {
    const a = document.createElement('a');
    a.className = className;
    a.textContent = text;
    a.href = href;
    return a;
  }

  function appendAcceptedFriendFromPendingRow(pendingRow, acceptedFriend) {
    if (!pendingRow || !lists.friends) return;
    const friend = acceptedFriend && typeof acceptedFriend === 'object' ? acceptedFriend : null;
    const friendId = String(friend?.userId || pendingRow.dataset.requesterId || '').trim();
    if (!friendId) return;

    const name = String(friend?.displayName || pendingRow.querySelector('.fw-semibold')?.textContent?.trim() || friendId);
    const email = String(friend?.email || pendingRow.querySelector('.text-muted')?.textContent?.trim() || '');
    const avatarEl = pendingRow.querySelector('img');
    const avatarPath = String(friend?.avatarPath || avatarEl?.getAttribute('src') || avatarEl?.src || '/uploads/avatars/default-avatar.jpg');
    const isOnline = !!friend?.online;
    const scoreValue = friend?.score;

    const li = document.createElement('li');
    li.className = 'list-group-item';
    li.dataset.friendId = friendId;

    const row = document.createElement('div');
    row.className = 'd-flex align-items-start gap-2';

    const img = document.createElement('img');
    img.src = avatarPath;
    img.alt = 'Anh dai dien';
    img.width = 40;
    img.height = 40;
    img.className = 'rounded-circle border flex-shrink-0';
    row.appendChild(img);

    const content = document.createElement('div');
    content.className = 'flex-grow-1';

    const titleRow = document.createElement('div');
    titleRow.className = 'd-flex flex-wrap align-items-center gap-2';

    const nameEl = document.createElement('span');
    nameEl.className = 'fw-semibold';
    nameEl.textContent = name;
    titleRow.appendChild(nameEl);

    const onlineBadge = document.createElement('span');
    onlineBadge.className = 'badge ' + (isOnline ? 'text-bg-success' : 'text-bg-secondary');
    onlineBadge.textContent = isOnline ? 'Truc tuyen' : 'Ngoai tuyen';
    titleRow.appendChild(onlineBadge);

    const scoreBadge = document.createElement('span');
    scoreBadge.className = 'badge text-bg-light border';
    scoreBadge.textContent = Number.isFinite(Number(scoreValue))
      ? ('Diem ' + Number(scoreValue))
      : 'Moi chap nhan';
    titleRow.appendChild(scoreBadge);

    content.appendChild(titleRow);

    const emailEl = document.createElement('div');
    emailEl.className = 'small text-muted';
    emailEl.textContent = email;
    content.appendChild(emailEl);

    const actions = document.createElement('div');
    actions.className = 'd-flex flex-wrap gap-2 mt-2';
    actions.appendChild(createLink(
      'btn btn-sm btn-outline-primary',
      'Xem',
      buildHref('/friendship/user-detail/' + encodeURIComponent(friendId), { currentUserId: uid })
    ));
    actions.appendChild(createLink(
      'btn btn-sm btn-outline-success',
      'Nhan tin',
      buildHref('/chat/private', { currentUserId: uid, friendId })
    ));
    actions.appendChild(createButton(
      'remove-friend btn btn-sm btn-outline-danger',
      'Huy ban',
      { friendId }
    ));
    content.appendChild(actions);

    row.appendChild(content);
    li.appendChild(row);
    lists.friends.appendChild(li);
    refreshEmptyState(lists.friends, 'Chua co ban be.');
  }

  form.onsubmit = async (e)=>{
    e.preventDefault();
    if (!ensureLogin()) return;
    const email = document.getElementById('email');
    try {
      const data = await postJson('/friendship/send-request', {requesterId:uid,email:email.value});
      const ok = report(data, 'Da gui loi moi ket ban');
      if (ok && email) {
        email.value = '';
      }
    } catch (err) {
      reportError(err);
    }
  };

  root.addEventListener('click', async (event) => {
    const btn = event.target.closest('button');
    if (!btn) return;

    try {
      if (btn.classList.contains('accept')) {
        const pendingRow = btn.closest('[data-friendship-id]');
        const siblings = pendingRow ? pendingRow.querySelectorAll('button') : [btn];
        siblings.forEach((node) => { node.disabled = true; });
        const data = await postJson('/friendship/accept', {friendshipId:Number(btn.dataset.id)});
        const ok = report(data, 'Da chap nhan loi moi ket ban');
        if (ok) {
          appendAcceptedFriendFromPendingRow(pendingRow, data?.acceptedFriend);
          if (pendingRow) pendingRow.remove();
          refreshEmptyState(lists.pending, 'Khong co loi moi dang cho.');
        } else {
          siblings.forEach((node) => { node.disabled = false; });
        }
        return;
      }

      if (btn.classList.contains('decline')) {
        btn.disabled = true;
        const data = await postJson('/friendship/decline', {friendshipId:Number(btn.dataset.id)});
        const ok = report(data, 'Da tu choi loi moi ket ban');
        if (ok) {
          removeRowFromButton(btn);
          refreshEmptyState(lists.pending, 'Khong co loi moi dang cho.');
        } else {
          btn.disabled = false;
        }
        return;
      }

      if (btn.classList.contains('remove-friend')) {
        if (!ensureLogin()) return;
        btn.disabled = true;
        const friendId = btn.dataset.friendId;
        const data = await postJson('/friendship/remove', {userId: uid, friendId});
        const ok = report(data, 'Da huy ket ban');
        if (ok) {
          removeRowFromButton(btn);
          refreshEmptyState(lists.friends, 'Chua co ban be.');
        } else {
          btn.disabled = false;
        }
        return;
      }

      if (btn.classList.contains('cancel-sent')) {
        if (!ensureLogin()) return;
        btn.disabled = true;
        const friendId = btn.dataset.friendId;
        const data = await postJson('/friendship/remove', {userId: uid, friendId});
        const ok = report(data, 'Da huy loi moi ket ban');
        if (ok) {
          removeRowFromButton(btn);
          refreshEmptyState(lists.sent, 'Khong co loi moi da gui.');
        } else {
          btn.disabled = false;
        }
      }
    } catch (err) {
      reportError(err);
      btn.disabled = false;
      const row = btn.closest('[data-friendship-id]');
      row?.querySelectorAll('button').forEach((node) => { node.disabled = false; });
    }
  });
})();
