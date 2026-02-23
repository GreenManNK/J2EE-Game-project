(function(){
  const root = document.getElementById('managerUserDetailRoot');
  if(!root) return;
  const id = root.dataset.userId;
  const out = document.getElementById('out');
  const minutes = document.getElementById('minutes');

  function readEditPayload() {
    const scoreVal = document.getElementById('score')?.value;
    return {
      displayName: document.getElementById('displayName')?.value ?? null,
      score: scoreVal === '' || scoreVal == null ? null : Number(scoreVal),
      role: document.getElementById('role')?.value ?? null,
      avatarPath: document.getElementById('avatarPath')?.value ?? null
    };
  }

  document.getElementById('saveBtn')?.addEventListener('click', async () => {
    const r = await fetch('/manager/users/' + id, {
      method: 'PATCH',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(readEditPayload())
    });
    out.textContent = JSON.stringify(await r.json(), null, 2);
  });

  document.getElementById('banBtn')?.addEventListener('click', async () => {
    const r = await fetch('/manager/users/' + id + '/ban', {
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({durationMinutes:parseInt(minutes.value,10)})
    });
    out.textContent = JSON.stringify(await r.json(), null, 2);
    window.location.reload();
  });

  document.getElementById('unbanBtn')?.addEventListener('click', async () => {
    const r = await fetch('/manager/users/' + id + '/unban', {method:'POST'});
    out.textContent = JSON.stringify(await r.json(), null, 2);
    window.location.reload();
  });
})();
