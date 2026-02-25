(function(){
  const root = document.getElementById('managerUserDetailRoot');
  if(!root) return;
  const id = root.dataset.userId;
  const out = document.getElementById('out');
  const ui = window.CaroUi || {};
  const minutes = document.getElementById('minutes');

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
    const message = String(err?.message || err || 'Request failed');
    setStatus(message, false);
    ui.toast?.(message, { type: 'danger' });
  }

  function readEditPayload() {
    const scoreVal = document.getElementById('score')?.value;
    return {
      displayName: document.getElementById('displayName')?.value ?? null,
      score: scoreVal === '' || scoreVal == null ? null : Number(scoreVal),
      avatarPath: document.getElementById('avatarPath')?.value ?? null
    };
  }

  document.getElementById('saveBtn')?.addEventListener('click', async () => {
    try {
      const r = await fetch('/manager/users/' + id, {
        method: 'PATCH',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(readEditPayload())
      });
      report(await r.json(), 'Cap nhat nguoi dung thanh cong');
    } catch (err) {
      reportError(err);
    }
  });

  document.getElementById('banBtn')?.addEventListener('click', async () => {
    try {
      const r = await fetch('/manager/users/' + id + '/ban', {
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({durationMinutes:parseInt(minutes.value,10)})
      });
      const ok = report(await r.json(), 'Da khoa tai khoan');
      if (ok) {
        window.location.reload();
      }
    } catch (err) {
      reportError(err);
    }
  });

  document.getElementById('unbanBtn')?.addEventListener('click', async () => {
    try {
      const r = await fetch('/manager/users/' + id + '/unban', {method:'POST'});
      const ok = report(await r.json(), 'Da mo khoa tai khoan');
      if (ok) {
        window.location.reload();
      }
    } catch (err) {
      reportError(err);
    }
  });
})();
