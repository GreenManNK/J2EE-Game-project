(function () {
  const root = document.getElementById('friendshipNotificationsRoot');
  if (!root) return;

  const out = document.getElementById('friendshipNotificationsOut');
  const list = document.getElementById('friendRequestList');
  const ui = window.CaroUi || {};

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

  function ensureEmptyState() {
    if (!list || document.getElementById('friendRequestEmptyState')) return;
    if (list.querySelectorAll('[data-friendship-id]').length > 0) return;
    const msg = document.createElement('div');
    msg.id = 'friendRequestEmptyState';
    msg.className = 'text-muted small mt-2';
    msg.textContent = 'Khong co loi moi ket ban.';
    list.insertAdjacentElement('afterend', msg);
  }

  async function handleAction(friendshipId, action, triggerButton) {
    if (!friendshipId) return;
    try {
      if (triggerButton) {
        triggerButton.disabled = true;
      }
      const res = await fetch('/friendship/' + action, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({ friendshipId: Number(friendshipId) })
      });
      const data = await res.json();
      const ok = report(data, action === 'accept' ? 'Da chap nhan loi moi ket ban' : 'Da tu choi loi moi ket ban');
      if (ok) {
        const row = (triggerButton && triggerButton.closest('[data-friendship-id]')) || null;
        if (row) {
          row.remove();
          ensureEmptyState();
        }
      } else if (triggerButton) {
        triggerButton.disabled = false;
      }
    } catch (err) {
      reportError(err);
      if (triggerButton) {
        triggerButton.disabled = false;
      }
    }
  }

  document.querySelectorAll('.accept[data-id]').forEach((btn) => {
    btn.addEventListener('click', () => handleAction(btn.dataset.id, 'accept', btn));
  });

  document.querySelectorAll('.decline[data-id]').forEach((btn) => {
    btn.addEventListener('click', () => handleAction(btn.dataset.id, 'decline', btn));
  });
})();
