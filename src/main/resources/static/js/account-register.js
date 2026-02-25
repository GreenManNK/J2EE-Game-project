(function(){
  const form = document.getElementById('regForm');
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
    setStatus(ok ? successMessage : String(data?.error || data?.message || 'Dang ky that bai'), ok);
    return ok;
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('email');
    const displayName = document.getElementById('displayName');
    const password = document.getElementById('password');
    try {
      const res = await fetch('/account/register', {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({email:email.value,displayName:displayName.value,password:password.value,avatarPath:''})
      });
      const data = await res.json();
      const ok = report(data, 'Da gui ma xac thuc email');
      if (ok) {
        localStorage.setItem('pendingVerifyEmail', email.value);
        setTimeout(() => {
          window.location.href = window.CaroUrl.path('/account/verify-email-page');
        }, 600);
      }
    } catch (err) {
      const message = String(err?.message || err || 'Dang ky that bai');
      setStatus(message, false);
      ui.toast?.(message, { type: 'danger' });
    }
  });
})();
