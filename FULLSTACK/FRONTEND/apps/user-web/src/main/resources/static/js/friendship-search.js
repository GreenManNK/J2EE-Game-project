(function () {
  const root = document.getElementById('friendshipSearchRoot');
  if (!root) return;

  const out = document.getElementById('friendshipSearchOut');
  const currentUserId = root.dataset.currentUserId || window.CaroUser?.get?.()?.userId || '';
  const ui = window.CaroUi || {};
  if (!currentUserId) return;

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

  document.querySelectorAll('.quick-add[data-user-id]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const addresseeId = btn.dataset.userId;
      if (!addresseeId) return;
      const originalText = btn.textContent;
      btn.textContent = 'Dang gui...';
      btn.disabled = true;
      try {
        const res = await fetch('/friendship/send-request-by-id', {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({ requesterId: currentUserId, addresseeId: addresseeId })
        });
        const data = await res.json();
        const ok = report(data, 'Da gui loi moi ket ban');
        if (ok) {
          btn.textContent = 'Da gui';
          btn.classList.remove('btn-primary');
          btn.classList.add('btn-outline-success');
        } else {
          btn.textContent = originalText;
          btn.disabled = false;
        }
      } catch (err) {
        const message = String(err?.message || err || 'Yeu cau that bai');
        setStatus(message, false);
        ui.toast?.(message, { type: 'danger' });
        btn.textContent = originalText;
        btn.disabled = false;
      }
    });
  });
})();
