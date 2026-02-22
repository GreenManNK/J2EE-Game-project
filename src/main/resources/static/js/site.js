(function () {
  function parseJsonSafe(raw) {
    try {
      return JSON.parse(raw);
    } catch (_) {
      return null;
    }
  }

  const USER_KEY = 'caro_current_user';

  function getCurrentUser() {
    const stored = parseJsonSafe(localStorage.getItem(USER_KEY));
    if (stored && stored.userId) {
      return stored;
    }

    const legacyUserId = localStorage.getItem('userId');
    if (legacyUserId) {
      const fromLegacy = {
        userId: legacyUserId,
        displayName: localStorage.getItem('displayName') || 'Player',
        email: localStorage.getItem('email') || '',
        role: localStorage.getItem('role') || 'User'
      };
      setCurrentUser(fromLegacy);
      return fromLegacy;
    }

    return null;
  }

  function setCurrentUser(user) {
    if (!user || !user.userId) {
      return;
    }

    const normalized = {
      userId: user.userId,
      displayName: user.displayName || 'Player',
      email: user.email || '',
      role: user.role || 'User'
    };

    localStorage.setItem(USER_KEY, JSON.stringify(normalized));
    localStorage.setItem('userId', normalized.userId);
    localStorage.setItem('displayName', normalized.displayName);
    localStorage.setItem('email', normalized.email);
    localStorage.setItem('role', normalized.role);
  }

  function clearCurrentUser() {
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem('userId');
    localStorage.removeItem('displayName');
    localStorage.removeItem('email');
    localStorage.removeItem('role');
  }

  window.CaroUser = {
    get: getCurrentUser,
    set: setCurrentUser,
    clear: clearCurrentUser
  };

  document.addEventListener('DOMContentLoaded', () => {
    const user = getCurrentUser();
    const badges = document.querySelectorAll('[data-current-user-badge]');

    badges.forEach((el) => {
      if (user) {
        el.textContent = user.displayName + ' (' + user.userId + ')';
      } else {
        el.textContent = 'Chua dang nhap';
      }
    });
  });
})();
