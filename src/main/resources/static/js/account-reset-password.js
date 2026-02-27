(function(){
  const sendForm = document.getElementById('sendForm');
  const resetForm = document.getElementById('resetForm');
  if(!sendForm || !resetForm) return;

  const ui = window.CaroUi || {};
  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === 'function')
    ? window.CaroUrl.path
    : function (value) { return value; };
  const out = document.getElementById('out');
  const email = document.getElementById('email');
  const code = document.getElementById('code');
  const newPassword = document.getElementById('newPassword');
  const confirmPassword = document.getElementById('confirmPassword');
  const verifyCodeBtn = document.getElementById('verifyCodeBtn');
  const verifyCodeStatus = document.getElementById('verifyCodeStatus');
  const resetBtn = document.getElementById('resetBtn');
  let isCodeVerified = false;

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
    setStatus(ok ? successMessage : String(data?.error || data?.message || 'Thao tác thất bại'), ok);
    return ok;
  }

  function reportError(err) {
    const message = String(err?.message || err || 'Lỗi mạng');
    setStatus(message, false);
    ui.toast?.(message, { type: 'danger' });
  }

  function setVerifyState(verified, label, tone) {
    isCodeVerified = !!verified;
    if (verifyCodeStatus) {
      verifyCodeStatus.textContent = label || (verified ? 'Mã hợp lệ' : 'Chưa xác thực mã');
      verifyCodeStatus.classList.remove('text-muted', 'text-success', 'text-danger');
      const resolvedTone = tone || (verified ? 'success' : 'muted');
      verifyCodeStatus.classList.add(
        resolvedTone === 'danger' ? 'text-danger' : (resolvedTone === 'success' ? 'text-success' : 'text-muted')
      );
    }
    if (resetBtn) {
      resetBtn.disabled = !isCodeVerified;
    }
  }

  function invalidateVerifiedCode() {
    if (!isCodeVerified) return;
    setVerifyState(false, 'Mã đã thay đổi, vui lòng xác thực lại', 'danger');
  }

  async function postJson(url, payload) {
    const res = await fetch(appPath(url), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload || {})
    });
    return res.json();
  }

  sendForm.addEventListener('submit', async (e)=>{
    e.preventDefault();
    const emailValue = (email?.value || '').trim();
    if (!emailValue) {
      setStatus('Email là bắt buộc', false);
      ui.toast?.('Email là bắt buộc', { type: 'danger' });
      return;
    }
    const submitBtn = sendForm.querySelector('button[type="submit"]');
    if (submitBtn) submitBtn.disabled = true;
    try {
      const data = await postJson('/account/send-reset-code', { email: emailValue });
      report(data, 'Nếu email tồn tại, mã reset đã được gửi');
      setVerifyState(false, 'Chưa xác thực mã', 'muted');
      code?.focus();
    } catch (err) {
      reportError(err);
    } finally {
      if (submitBtn) submitBtn.disabled = false;
    }
  });

  email?.addEventListener('input', invalidateVerifiedCode);
  code?.addEventListener('input', invalidateVerifiedCode);

  verifyCodeBtn?.addEventListener('click', async ()=>{
    const emailValue = (email?.value || '').trim();
    const codeValue = (code?.value || '').trim();
    if (!emailValue || !codeValue) {
      setVerifyState(false, 'Nhập email và mã xác thực', 'danger');
      setStatus('Nhập email và mã xác thực để tiếp tục', false);
      return;
    }
    try {
      verifyCodeBtn.disabled = true;
      const data = await postJson('/account/verify-reset-code', { email: emailValue, code: codeValue });
      const ok = report(data, 'Mã reset hợp lệ');
      if (ok) {
        setVerifyState(true, 'Mã hợp lệ', 'success');
      } else {
        setVerifyState(false, String(data?.error || 'Mã không hợp lệ'), 'danger');
      }
    } catch (err) {
      reportError(err);
      setVerifyState(false, 'Không thể xác thực mã', 'danger');
    } finally {
      verifyCodeBtn.disabled = false;
    }
  });

  resetForm.addEventListener('submit', async (e)=>{
    e.preventDefault();
    if (!isCodeVerified) {
      setStatus('Vui lòng xác thực mã trước khi đặt lại mật khẩu', false);
      ui.toast?.('Vui lòng xác thực mã trước khi đặt lại mật khẩu', { type: 'warning' });
      return;
    }

    const emailValue = (email?.value || '').trim();
    const codeValue = (code?.value || '').trim();
    try {
      if (resetBtn) resetBtn.disabled = true;
      const data = await postJson('/account/reset-password', {
        email: emailValue,
        code: codeValue,
        newPassword: newPassword?.value || '',
        confirmPassword: confirmPassword?.value || ''
      });
      const ok = report(data, 'Đặt lại mật khẩu thành công');
      if (ok) {
        setVerifyState(false, 'Đặt lại mật khẩu thành công', 'success');
        if (code) code.value = '';
        if (newPassword) newPassword.value = '';
        if (confirmPassword) confirmPassword.value = '';
      } else {
        if (resetBtn) resetBtn.disabled = false;
      }
    } catch (err) {
      reportError(err);
      if (resetBtn) resetBtn.disabled = false;
    } finally {
      if (resetBtn && !isCodeVerified) {
        resetBtn.disabled = true;
      }
    }
  });
})();
