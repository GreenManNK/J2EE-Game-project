(function(){
  const form = document.getElementById('chatForm');
  if(!form) return;
  form.onsubmit = async (e)=>{
   e.preventDefault();
   const message = document.getElementById('message');
   const out = document.getElementById('out');
   const r=await fetch('/chat-bot/send',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({message:message.value})});
   out.textContent = JSON.stringify(await r.json(), null, 2);
  };
})();