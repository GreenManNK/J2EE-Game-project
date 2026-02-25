(function () {
  const BANNER_ID = 'networkStatusBanner';
  const appPath = (() => {
    const meta = document.querySelector('meta[name="app-context-path"]');
    let base = meta?.getAttribute('content') || '';
    base = String(base).trim();
    if (!base || base === '/') return '';
    if (!base.startsWith('/')) base = '/' + base;
    if (base.length > 1 && base.endsWith('/')) base = base.slice(0, -1);
    return base;
  })();

  function toAppPath(path) {
    const value = String(path || '');
    if (!appPath || !value.startsWith('/')) return value;
    if (value === appPath || value.startsWith(appPath + '/')) return value;
    return appPath + value;
  }

  function ensureBanner() {
    let el = document.getElementById(BANNER_ID);
    if (!el) {
      el = document.createElement('div');
      el.id = BANNER_ID;
      el.style.position = 'fixed';
      el.style.top = '10px';
      el.style.left = '50%';
      el.style.transform = 'translateX(-50%)';
      el.style.zIndex = '2000';
      el.style.padding = '8px 12px';
      el.style.borderRadius = '8px';
      el.style.fontSize = '13px';
      el.style.fontWeight = '600';
      el.style.boxShadow = '0 2px 10px rgba(0,0,0,.2)';
      el.style.display = 'none';
      document.body.appendChild(el);
    }
    return el;
  }

  function showStatus(text, kind) {
    const el = ensureBanner();
    el.textContent = text;
    if (kind === 'error') {
      el.style.backgroundColor = '#dc3545';
      el.style.color = '#fff';
      el.style.display = 'block';
      return;
    }
    if (kind === 'warn') {
      el.style.backgroundColor = '#ffc107';
      el.style.color = '#212529';
      el.style.display = 'block';
      return;
    }
    if (kind === 'ok') {
      el.style.backgroundColor = '#198754';
      el.style.color = '#fff';
      el.style.display = 'block';
      window.setTimeout(() => {
        const current = document.getElementById(BANNER_ID);
        if (current && current.textContent === text) {
          current.style.display = 'none';
        }
      }, 1500);
    }
  }

  async function pingServer() {
    if (!navigator.onLine) {
      showStatus('Mat ket noi internet', 'error');
      return;
    }

    try {
      const res = await fetch(toAppPath('/api/connectivity/ping'), {
        method: 'GET',
        cache: 'no-store'
      });
      if (!res.ok) {
        showStatus('Khong ket noi duoc may chu', 'warn');
        return;
      }
      showStatus('Ket noi mang on dinh', 'ok');
    } catch (e) {
      showStatus('Khong ket noi duoc may chu', 'warn');
    }
  }

  window.addEventListener('online', () => {
    showStatus('Internet da ket noi lai', 'ok');
    pingServer();
  });

  window.addEventListener('offline', () => {
    showStatus('Dang offline - kiem tra mang internet', 'error');
  });

  document.addEventListener('DOMContentLoaded', () => {
    pingServer();
    window.setInterval(pingServer, 20000);
  });
})();
