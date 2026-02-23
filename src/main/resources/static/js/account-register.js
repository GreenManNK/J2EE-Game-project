(function(){
  const form = document.getElementById('regForm');
  if(!form) return;
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('email');
    const displayName = document.getElementById('displayName');
    const password = document.getElementById('password');
    const out = document.getElementById('out');
    const res = await fetch('/account/register', {
      method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({email:email.value,displayName:displayName.value,password:password.value,avatarPath:''})
    });
    const data = await res.json();
    out.textContent = JSON.stringify(data, null, 2);
    if (data.success) {
      localStorage.setItem('pendingVerifyEmail', email.value);
      setTimeout(() => {
        window.location.href = window.CaroUrl.path('/account/verify-email-page');
      }, 600);
    }
  });
})();
