(function(){
  const form = document.getElementById('verifyForm');
  if(!form) return;
  const emailInput = document.getElementById('email');
  const resendBtn = document.getElementById('resendCodeBtn');
  const out = document.getElementById('out');
  const ui = window.CaroUi || {};
  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === 'function')
    ? window.CaroUrl.path
    : function (value) { return value; };
  const pendingEmail = localStorage.getItem('pendingVerifyEmail');
  if (emailInput && pendingEmail) {
    emailInput.value = pendingEmail;
  }
  let resendCooldownTimer = null;

  function renderOut(data) {
    if (ui.apiResult) {
      ui.apiResult(data, {
        statusEl: out,
        successMessage: 'Xử lý thành công'
      });
      return;
    }
    if (!out) return;
    const ok = !!(data && data.success);
    out.textContent = ok ? 'Xử lý thành công' : String(data?.error || data?.message || 'Thất bại');
  }

  function startResendCooldown(seconds) {
    if (!resendBtn) return;
    if (resendCooldownTimer) {
      clearInterval(resendCooldownTimer);
      resendCooldownTimer = null;
    }
    let remain = Number(seconds) || 0;
    if (remain <= 0) {
      resendBtn.disabled = false;
      resendBtn.textContent = 'Gửi lại mã';
      return;
    }
    resendBtn.disabled = true;
    resendBtn.textContent = `Gửi lại mã (${remain}s)`;
    resendCooldownTimer = setInterval(() => {
      remain -= 1;
      if (remain <= 0) {
        clearInterval(resendCooldownTimer);
        resendCooldownTimer = null;
        resendBtn.disabled = false;
        resendBtn.textContent = 'Gửi lại mã';
        return;
      }
      resendBtn.textContent = `Gửi lại mã (${remain}s)`;
    }, 1000);
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('email');
    const code = document.getElementById('code');
    try {
      const res = await fetch(appPath('/account/verify-email'), {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({email:email.value,code:code.value})
      });
      const data = await res.json();
      if (data.success && ui.apiResult) {
        ui.apiResult(data, { statusEl: out, successMessage: 'Xác thực email thành công' });
      } else {
        renderOut(data);
      }
      if (data.success) {
        localStorage.removeItem('pendingVerifyEmail');
        if (window.CaroUser && data.data && data.data.userId) {
          window.CaroUser.set({
            userId: data.data.userId,
            displayName: data.data.displayName || data.data.email || 'Player',
            email: data.data.email || '',
            role: data.data.role || 'User',
            avatarPath: data.data.avatarPath || '/uploads/avatars/default-avatar.jpg'
          });
          await window.CaroGuestData?.migrateToAccount?.();
          window.location.href = appPath('/');
        }
      }
    } catch (err) {
      const message = String(err?.message || 'Lỗi mạng');
      if (ui.setStatus) ui.setStatus(out, message, false);
      else if (out) out.textContent = message;
      ui.toast?.(message, { type: 'danger' });
    }
  });

  resendBtn?.addEventListener('click', async () => {
    const email = emailInput?.value?.trim();
    if (!email) {
      renderOut({ success: false, error: 'Email là bắt buộc' });
      return;
    }
    resendBtn.disabled = true;
    const originalText = resendBtn.textContent;
    resendBtn.textContent = 'Đang gửi...';
    try {
      const res = await fetch(appPath('/account/resend-verification-code'), {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({ email })
      });
      const data = await res.json();
      if (data.success && ui.apiResult) {
        ui.apiResult(data, { statusEl: out, successMessage: 'Đã gửi lại mã xác thực' });
      } else {
        renderOut(data);
      }
      if (data.success) {
        localStorage.setItem('pendingVerifyEmail', email);
        startResendCooldown(30);
      } else {
        resendBtn.disabled = false;
        resendBtn.textContent = originalText || 'Gửi lại mã';
      }
    } catch (err) {
      const message = err?.message || 'Lỗi mạng';
      renderOut({ success: false, error: message });
      ui.toast?.(message, { type: 'danger' });
      resendBtn.disabled = false;
      resendBtn.textContent = originalText || 'Gửi lại mã';
    }
  });
})();
