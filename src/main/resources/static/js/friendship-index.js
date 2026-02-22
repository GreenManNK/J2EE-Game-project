(function(){
  const root = document.getElementById('friendshipRoot');
  if(!root) return;
  const uid = root.dataset.currentUserId || window.CaroUser?.get?.()?.userId || '';
  const form = document.getElementById('sendByEmailForm');
  const out = document.getElementById('out');
  if(!form || !out) return;

  form.onsubmit = async (e)=>{
   e.preventDefault();
   if (!uid) {
     out.textContent = JSON.stringify({success:false,error:'Can dang nhap de su dung tinh nang ban be'}, null, 2);
     return;
   }
   const email = document.getElementById('email');
   const r=await fetch('/friendship/send-request',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({requesterId:uid,email:email.value})});
   out.textContent=JSON.stringify(await r.json(),null,2);
  };

  document.querySelectorAll('.accept').forEach(b=>b.onclick=async()=>{
    const r=await fetch('/friendship/accept',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({friendshipId:Number(b.dataset.id)})});
    out.textContent=JSON.stringify(await r.json(),null,2);
    location.reload();
  });

  document.querySelectorAll('.decline').forEach(b=>b.onclick=async()=>{
    const r=await fetch('/friendship/decline',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({friendshipId:Number(b.dataset.id)})});
    out.textContent=JSON.stringify(await r.json(),null,2);
    location.reload();
  });
})();
