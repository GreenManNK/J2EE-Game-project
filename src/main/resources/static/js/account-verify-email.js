(function(){
  const form = document.getElementById('verifyForm');
  if(!form) return;
  const emailInput = document.getElementById('email');
  const pendingEmail = localStorage.getItem('pendingVerifyEmail');
  if (emailInput && pendingEmail) {
    emailInput.value = pendingEmail;
  }
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('email');
    const code = document.getElementById('code');
    const out = document.getElementById('out');
    const res = await fetch('/account/verify-email', {
      method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({email:email.value,code:code.value})
    });
    const data = await res.json();
    out.textContent = JSON.stringify(data, null, 2);
    if (data.success) {
      localStorage.removeItem('pendingVerifyEmail');
    }
  });
})();
