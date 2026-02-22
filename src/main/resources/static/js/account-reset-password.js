(function(){
  const sendForm = document.getElementById('sendForm');
  const resetForm = document.getElementById('resetForm');
  if(!sendForm || !resetForm) return;

  sendForm.addEventListener('submit', async (e)=>{
    e.preventDefault();
    const email = document.getElementById('email');
    const out = document.getElementById('out');
    const r=await fetch('/account/send-reset-code',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:email.value})});
    const data = await r.json();
    out.textContent = 'Send code:\n'+JSON.stringify(data,null,2);
    if (data.success && data.data && data.data.userId) {
      const uid = document.getElementById('userId');
      if (uid && !uid.value) {
        uid.value = data.data.userId;
      }
    }
  });

  resetForm.addEventListener('submit', async (e)=>{
    e.preventDefault();
    const out = document.getElementById('out');
    const userId = document.getElementById('userId');
    const code = document.getElementById('code');
    const newPassword = document.getElementById('newPassword');
    const confirmPassword = document.getElementById('confirmPassword');
    const r=await fetch('/account/reset-password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({userId:userId.value,code:code.value,newPassword:newPassword.value,confirmPassword:confirmPassword.value})});
    out.textContent = out.textContent+'\n\nReset:\n'+JSON.stringify(await r.json(),null,2);
  });
})();
