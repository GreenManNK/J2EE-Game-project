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
      setStatus(ok ? 'Da nhan phan hoi tu chatbot' : String(data?.message || data?.error || 'Loi chatbot'), ok);
      if (out) {
        out.textContent = ok ? String(data.reply || '') : String(data?.message || data?.error || 'Loi chatbot');
      }
      ui.toast?.(ok ? 'Chatbot da phan hoi' : String(data?.message || 'Loi chatbot'), { type: ok ? 'success' : 'danger' });
    } catch (err) {
      const msg = String(err?.message || err || 'Loi chatbot');
      setStatus(msg, false);
      ui.toast?.(msg, { type: 'danger' });
    }
  };
})();
