(function () {
  const form = document.getElementById('managerCreateUserForm');
  if (!form) return;

  const out = document.getElementById('managerCreateUserOut');
  const ui = window.CaroUi || {};

  function setStatus(message, ok) {
    if (ui.setStatus) {
      ui.setStatus(out, message, ok);
    } else if (out) {
      out.textContent = String(message || '');
    }
  }

  function value(id) {
    return document.getElementById(id)?.value ?? '';
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const scoreRaw = value('createScore');
    const payload = {
      email: value('createEmail').trim(),
      displayName: value('createDisplayName').trim(),
      password: value('createPassword'),
      score: scoreRaw === '' ? 0 : Number(scoreRaw),
      role: 'User',
      avatarPath: value('createAvatarPath').trim()
    };

    try {
      const res = await fetch('/manager/users', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      const ok = ui.apiResult
        ? ui.apiResult(data, { statusEl: out, successMessage: 'Tao nguoi dung thanh cong' })
        : !!data.success;
      if (!ui.apiResult) {
        setStatus(ok ? 'Tao nguoi dung thanh cong' : String(data?.error || data?.message || 'Tao nguoi dung that bai'), ok);
      }
      if (ok) {
        window.location.reload();
      }
    } catch (err) {
      const message = String(err?.message || err || 'Request failed');
      setStatus(message, false);
      ui.toast?.(message, { type: 'danger' });
    }
  });
})();
