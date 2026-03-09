(function(){
  const form = document.getElementById('createForm');
  if(!form) return;
  const out = document.getElementById('out');
  const list = document.querySelector('.list-group');
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
    if (!list) return;
    const rows = Array.from(list.querySelectorAll('.list-group-item'));
    const hasRealRows = rows.some((row) => !row.classList.contains('text-muted'));
    const emptyRow = rows.find((row) => row.classList.contains('text-muted'));
    if (hasRealRows && emptyRow) {
      emptyRow.remove();
      return;
    }
    if (!hasRealRows && !emptyRow) {
      const li = document.createElement('li');
      li.className = 'list-group-item text-muted';
      li.textContent = 'Chua co thong bao.';
      list.appendChild(li);
    }
  }

  function createRow(notification) {
    const li = document.createElement('li');
    li.className = 'list-group-item d-flex justify-content-between align-items-center gap-2';
    const text = document.createElement('span');
    text.textContent = (notification?.content || '') + (notification?.createdAt ? (' - ' + notification.createdAt) : '');
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'del btn btn-sm btn-outline-danger';
    btn.textContent = 'Xoa';
    if (notification?.id != null) {
      btn.dataset.id = String(notification.id);
    }
    li.append(text, btn);
    return li;
  }

  form.onsubmit = async (e)=>{
    e.preventDefault();
    const content = document.getElementById('content');
    try {
      const r = await fetch('/notification-admin', {
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({content:content.value})
      });
      const data = await r.json();
      const ok = report(data, 'Da tao thong bao');
      if (ok) {
        if (content) content.value = '';
        if (list && data.notification) {
          list.prepend(createRow(data.notification));
          ensureEmptyState();
        } else {
          window.location.reload();
        }
      }
    } catch (err) {
      reportError(err);
    }
  };

  document.addEventListener('click', async (event) => {
    const btn = event.target.closest('.del[data-id]');
    if (!btn) return;
    btn.disabled = true;
    try {
      const r = await fetch('/notification-admin/' + btn.dataset.id, {method:'DELETE'});
      let data = null;
      try {
        data = await r.json();
      } catch (_) {
        data = { success: r.ok };
      }
      const ok = report(data, 'Da xoa thong bao');
      if (ok) {
        btn.closest('.list-group-item')?.remove();
        ensureEmptyState();
      } else {
        btn.disabled = false;
      }
    } catch (err) {
      reportError(err);
      btn.disabled = false;
    }
  });
})();

