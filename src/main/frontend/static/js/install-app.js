(function () {
  const INSTALL_STATE_EVENT = 'caro:install-state';
  let deferredPrompt = null;
  let isInstalled = false;
  let serviceWorkerReady = false;

  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === 'function')
    ? window.CaroUrl.path
    : function (value) { return value; };

  function isStandaloneMode() {
    try {
      if (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches) {
        return true;
      }
    } catch (_) {
    }
    return window.navigator.standalone === true;
  }

  function isSecureInstallContext() {
    return window.location.protocol === 'https:'
      || window.location.hostname === 'localhost'
      || window.location.hostname === '127.0.0.1';
  }

  function getButtons() {
    return Array.from(document.querySelectorAll('[data-install-app-btn]'));
  }

  function currentState() {
    return {
      available: !!deferredPrompt,
      installed: isInstalled || isStandaloneMode(),
      serviceWorkerReady
    };
  }

  function emitState() {
    try {
      window.dispatchEvent(new CustomEvent(INSTALL_STATE_EVENT, { detail: currentState() }));
    } catch (_) {
    }
  }

  function syncButtons() {
    const state = currentState();
    getButtons().forEach((button) => {
      const label = button.querySelector('[data-install-app-label]');
      button.dataset.installState = state.installed
        ? 'installed'
        : (state.available ? 'ready' : 'guide');
      button.classList.toggle('is-installed', state.installed);
      button.classList.toggle('is-ready', state.available);
      button.classList.toggle('is-guide', !state.available && !state.installed);
      if (label) {
        label.textContent = state.installed ? 'Installed' : 'Install';
      }
      button.setAttribute(
        'title',
        state.installed
          ? 'App da duoc cai dat'
          : (state.available ? 'Cai dat app desktop' : 'Xem huong dan cai dat app')
      );
    });
    emitState();
  }

  function showToast(message, type) {
    try {
      if (window.CaroUi && typeof window.CaroUi.toast === 'function') {
        window.CaroUi.toast(message, { type: type || 'info' });
        return;
      }
    } catch (_) {
    }
  }

  function isAppleMobile() {
    const ua = String(window.navigator.userAgent || '');
    return /iphone|ipad|ipod/i.test(ua);
  }

  function isAndroid() {
    return /android/i.test(String(window.navigator.userAgent || ''));
  }

  function isDesktopViewport() {
    try {
      return window.matchMedia('(min-width: 992px)').matches;
    } catch (_) {
      return true;
    }
  }

  function buildGuideHtml() {
    if (isAppleMobile()) {
      return [
        '<p class="mb-2">Safari khong hien prompt Install giong Chrome/Edge.</p>',
        '<ol class="text-start ps-3 mb-0">',
        '<li>Mo menu Share.</li>',
        '<li>Chon <strong>Add to Home Screen</strong>.</li>',
        '<li>Xac nhan de tao icon app rieng.</li>',
        '</ol>'
      ].join('');
    }

    if (isAndroid()) {
      return [
        '<p class="mb-2">Neu prompt chua hien, ban co the cai dat thu cong:</p>',
        '<ol class="text-start ps-3 mb-0">',
        '<li>Mo menu trinh duyet.</li>',
        '<li>Chon <strong>Install app</strong> hoac <strong>Add to Home screen</strong>.</li>',
        '<li>Xac nhan de mo web nhu mot app rieng.</li>',
        '</ol>'
      ].join('');
    }

    if (isDesktopViewport()) {
      return [
        '<p class="mb-2">Chrome/Edge tren desktop co the cai web nay thanh app rieng.</p>',
        '<ol class="text-start ps-3 mb-0">',
        '<li>Mo menu trinh duyet o goc phai tren.</li>',
        '<li>Chon <strong>Install app</strong> hoac <strong>Install this site as an app</strong>.</li>',
        '<li>Xac nhan de tao shortcut desktop + cua so rieng.</li>',
        '</ol>'
      ].join('');
    }

    return [
      '<p class="mb-2">Trinh duyet nay chua hien prompt Install tu dong.</p>',
      '<ol class="text-start ps-3 mb-0">',
      '<li>Mo menu trinh duyet.</li>',
      '<li>Tim muc <strong>Install app</strong> hoac <strong>Add to Home screen</strong>.</li>',
      '<li>Xac nhan de cai dat.</li>',
      '</ol>'
    ].join('');
  }

  function showInstallGuide() {
    if (typeof Swal !== 'undefined' && Swal && typeof Swal.fire === 'function') {
      void Swal.fire({
        title: 'Install Game Hub',
        html: buildGuideHtml(),
        icon: 'info',
        confirmButtonText: 'Da hieu',
        customClass: {
          popup: 'cg-install-modal'
        }
      });
      return;
    }
    showToast('Mo menu trinh duyet va chon Install app / Add to Home screen.', 'info');
  }

  async function registerServiceWorker() {
    if (!('serviceWorker' in navigator) || !isSecureInstallContext()) {
      return;
    }
    try {
      await navigator.serviceWorker.register(appPath('/service-worker.js'), {
        scope: appPath('/')
      });
      serviceWorkerReady = true;
      syncButtons();
    } catch (_) {
      serviceWorkerReady = false;
      syncButtons();
    }
  }

  async function promptInstall() {
    if (currentState().installed) {
      showToast('App da duoc cai dat tren thiet bi nay.', 'success');
      return;
    }

    if (!deferredPrompt) {
      showInstallGuide();
      return;
    }

    try {
      await deferredPrompt.prompt();
      const choice = await deferredPrompt.userChoice;
      if (choice && choice.outcome === 'accepted') {
        showToast('Dang cai dat app...', 'success');
      }
    } catch (_) {
      showInstallGuide();
    } finally {
      deferredPrompt = null;
      syncButtons();
    }
  }

  window.addEventListener('beforeinstallprompt', function (event) {
    event.preventDefault();
    deferredPrompt = event;
    syncButtons();
  });

  window.addEventListener('appinstalled', function () {
    isInstalled = true;
    deferredPrompt = null;
    syncButtons();
    showToast('Da cai dat Game Hub thanh app.', 'success');
  });

  document.addEventListener('click', function (event) {
    const trigger = event.target.closest('[data-install-app-btn]');
    if (!trigger) {
      return;
    }
    event.preventDefault();
    void promptInstall();
  });

  document.addEventListener('DOMContentLoaded', function () {
    isInstalled = isStandaloneMode();
    syncButtons();
    void registerServiceWorker();
  });

  window.CaroInstall = {
    prompt: function () {
      return promptInstall();
    },
    state: currentState
  };
})();
