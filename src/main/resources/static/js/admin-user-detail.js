(function(){
  const root = document.getElementById('adminUserDetailRoot');
  if(!root) return;
  const id = root.dataset.userId;
  const minutes = document.getElementById('minutes');
  const out = document.getElementById('out');
  const banBtn = document.getElementById('banBtn');
  const unbanBtn = document.getElementById('unbanBtn');
  const delBtn = document.getElementById('delBtn');

  banBtn.onclick = async()=>{
    const r=await fetch('/admin/users/'+id+'/ban',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({durationMinutes:parseInt(minutes.value,10)})});
    out.textContent=JSON.stringify(await r.json(),null,2);
  };
  unbanBtn.onclick = async()=>{
    const r=await fetch('/admin/users/'+id+'/unban',{method:'POST'});
    out.textContent=JSON.stringify(await r.json(),null,2);
  };
  delBtn.onclick = async()=>{
    const r=await fetch('/admin/users/'+id,{method:'DELETE'});
    out.textContent=JSON.stringify(await r.json(),null,2);
  };
})();