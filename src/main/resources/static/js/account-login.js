(function(){
  const form = document.getElementById('loginForm');
  if(!form) return;
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('email');
    const password = document.getElementById('password');
    const out = document.getElementById('out');
    const res = await fetch('/account/login', {
      method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({email:email.value,password:password.value})
    });
    const data = await res.json();
    out.textContent = JSON.stringify(data, null, 2);

    if (data.success && data.data && data.data.userId && window.CaroUser) {
      window.CaroUser.set({
        userId: data.data.userId,
        displayName: data.data.displayName || data.data.email || 'Player',
        email: data.data.email || '',
        role: data.data.role || 'User',
        avatarPath: data.data.avatarPath || '/uploads/avatars/default-avatar.jpg'
      });
      window.location.href = window.CaroUrl.path('/');
    }
  });
})();
