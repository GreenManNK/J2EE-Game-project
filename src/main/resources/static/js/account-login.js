(function(){
  const form = document.getElementById('loginForm');
  if(!form) return;
  const out = document.getElementById('out');
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
    setStatus(ok ? successMessage : String(data?.error || data?.message || 'Dang nhap that bai'), ok);
    return ok;
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('email');
    const password = document.getElementById('password');
    try {
      const res = await fetch('/account/login', {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({email:email.value,password:password.value})
      });
      const data = await res.json();
      const ok = report(data, 'Dang nhap thanh cong');

      if (ok && data.data && data.data.userId && window.CaroUser) {
        window.CaroUser.set({
          userId: data.data.userId,
          displayName: data.data.displayName || data.data.email || 'Player',
          email: data.data.email || '',
          role: data.data.role || 'User',
          avatarPath: data.data.avatarPath || '/uploads/avatars/default-avatar.jpg'
        });
        window.location.href = window.CaroUrl.path('/');
      }
    } catch (err) {
      const message = String(err?.message || err || 'Dang nhap that bai');
      setStatus(message, false);
      ui.toast?.(message, { type: 'danger' });
    }
  });
})();
