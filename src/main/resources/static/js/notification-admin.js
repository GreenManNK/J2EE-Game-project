(function(){
  const form = document.getElementById('createForm');
  if(!form) return;
  const out = document.getElementById('out');
  form.onsubmit = async (e)=>{
    e.preventDefault();
    const content = document.getElementById('content');
    const r=await fetch('/notification-admin',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({content:content.value})});
    out.textContent=JSON.stringify(await r.json(),null,2);
    location.reload();
  };

  document.querySelectorAll('.del').forEach(b=>b.onclick=async()=>{
    await fetch('/notification-admin/'+b.dataset.id,{method:'DELETE'});
    location.reload();
  });
})();