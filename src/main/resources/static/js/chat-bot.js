(function(){
  const form = document.getElementById('chatForm');
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

  form.onsubmit = async (e)=>{
    e.preventDefault();
    const message = document.getElementById('message');
    try {
      const r = await fetch('/chat-bot/send', {
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({message:message.value})
      });
      const data = await r.json();
      const ok = !!(data && data.success);
      setStatus(ok ? 'Da nhan phan hoi tu tro ly' : String(data?.message || data?.error || 'Loi tro ly'), ok);
      if (out) {
        out.textContent = ok ? String(data.reply || '') : String(data?.message || data?.error || 'Loi tro ly');
      }
      ui.toast?.(ok ? 'Tro ly da phan hoi' : String(data?.message || 'Loi tro ly'), { type: ok ? 'success' : 'danger' });
    } catch (err) {
      const msg = String(err?.message || err || 'Loi tro ly');
      setStatus(msg, false);
      ui.toast?.(msg, { type: 'danger' });
    }
  };
})();
