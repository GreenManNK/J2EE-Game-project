(function () {
  const root = document.getElementById('friendshipUserDetailRoot');
  if (!root) return;

  const actionBtn = document.getElementById('friendRelationBtn');
  const out = document.getElementById('friendshipUserDetailOut');
  const ui = window.CaroUi || {};
  if (!actionBtn) return;

  const targetUserId = root.dataset.targetUserId || '';
  const currentUserId = root.dataset.currentUserId || '';

  function setStatus(message, ok) {
    if (ui.setStatus) {
      ui.setStatus(out, message, ok);
    } else if (out) {
      out.textContent = String(message || '');
    }
  }

  function report(data, successMessage) {
    if (ui.apiResult) {
      return ui.apiResult(data, { statusEl: out, successMessage });
    }
    const ok = !!(data && data.success);
    setStatus(ok ? successMessage : String(data?.error || data?.message || 'Thao tac that bai'), ok);
    return ok;
  }

  function reportError(err) {
    const message = String(err?.message || err || 'Yeu cau that bai');
    setStatus(message, false);
    ui.toast?.(message, { type: 'danger' });
  }

  function toBool(value) {
    return String(value).toLowerCase() === 'true';
  }

  function updateActionLabel() {
    if (toBool(root.dataset.isFriend)) {
      actionBtn.textContent = 'Huy ket ban';
      actionBtn.className = 'btn btn-outline-danger';
      return;
    }
    if (toBool(root.dataset.hasPending)) {
      actionBtn.textContent = 'Xem thong bao loi moi';
      actionBtn.className = 'btn btn-outline-warning';
      return;
    }
    actionBtn.textContent = 'Gui loi moi ket ban';
    actionBtn.className = 'btn btn-primary';
  }

  updateActionLabel();

  actionBtn.addEventListener('click', async () => {
    if (!currentUserId || !targetUserId) {
      setStatus('Thieu thong tin tai khoan.', false);
      ui.toast?.('Thieu thong tin tai khoan.', { type: 'danger' });
      return;
    }

    if (toBool(root.dataset.hasPending) && !toBool(root.dataset.isFriend)) {
      window.location.href = (window.CaroUrl?.path?.('/friendship/notifications') || '/friendship/notifications')
        + '?currentUserId=' + encodeURIComponent(currentUserId);
      return;
    }

    actionBtn.disabled = true;
    try {
      let res;
      if (toBool(root.dataset.isFriend)) {
        res = await fetch('/friendship/remove', {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({ userId: currentUserId, friendId: targetUserId })
        });
      } else {
        res = await fetch('/friendship/send-request-by-id', {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({ requesterId: currentUserId, addresseeId: targetUserId })
        });
      }

      const data = await res.json();
      const wasFriend = toBool(root.dataset.isFriend);
      const ok = report(data, wasFriend ? 'Da huy ket ban' : 'Da gui loi moi ket ban');
      if (ok) {
        if (toBool(root.dataset.isFriend)) {
          root.dataset.isFriend = 'false';
        } else {
          root.dataset.hasPending = 'true';
        }
        updateActionLabel();
      }
    } catch (err) {
      reportError(err);
    } finally {
      actionBtn.disabled = false;
    }
  });
})();
