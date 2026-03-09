(function(){
  const root = document.getElementById('adminUserDetailRoot');
  if(!root) return;
  const id = root.dataset.userId;
  const minutes = document.getElementById('minutes');
  const out = document.getElementById('out');
  const ui = window.CaroUi || {};
  const saveBtn = document.getElementById('saveBtn');
  const banBtn = document.getElementById('banBtn');
  const unbanBtn = document.getElementById('unbanBtn');
  const delBtn = document.getElementById('delBtn');

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

  function readEditPayload() {
    const scoreVal = document.getElementById('score')?.value;
    return {
      displayName: document.getElementById('displayName')?.value ?? null,
      score: scoreVal === '' || scoreVal == null ? null : Number(scoreVal),
      role: document.getElementById('role')?.value ?? null,
      avatarPath: document.getElementById('avatarPath')?.value ?? null
    };
  }

  saveBtn?.addEventListener('click', async () => {
    try {
      const r = await fetch('/admin/users/' + id, {
        method: 'PATCH',
        headers: {'Content-Type':'application/json'},
        body: JSON.stringify(readEditPayload())
      });
      report(await r.json(), 'Cap nhat tai khoan thanh cong');
    } catch (err) {
      reportError(err);
    }
  });

  banBtn.onclick = async()=>{
    try {
      const r=await fetch('/admin/users/'+id+'/ban',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({durationMinutes:parseInt(minutes.value,10)})});
      report(await r.json(), 'Da khoa tai khoan');
    } catch (err) {
      reportError(err);
    }
  };
  unbanBtn.onclick = async()=>{
    try {
      const r=await fetch('/admin/users/'+id+'/unban',{method:'POST'});
      report(await r.json(), 'Da mo khoa tai khoan');
    } catch (err) {
      reportError(err);
    }
  };
  delBtn.onclick = async()=>{
    try {
      const r=await fetch('/admin/users/'+id,{method:'DELETE'});
      const ok = report(await r.json(), 'Da xoa tai khoan');
      if (ok) {
        setTimeout(() => {
          window.location.href = window.CaroUrl?.path?.('/admin/users') || '/admin/users';
        }, 500);
      }
    } catch (err) {
      reportError(err);
    }
  };
})();
