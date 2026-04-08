(function () {
  const USER_CHANGE_EVENT = (window.CaroUser && window.CaroUser.eventName) || 'caro:user-changed';

  function appPath(url) {
    if (window.CaroUrl && typeof window.CaroUrl.path === 'function') {
      return window.CaroUrl.path(url);
    }
    return url;
  }

  function getCurrentUser() {
    return window.CaroUser && typeof window.CaroUser.get === 'function'
      ? window.CaroUser.get()
      : null;
  }

  function toast(message, type) {
    window.CaroUi?.toast?.(message, { type: type || 'info' });
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  document.addEventListener('DOMContentLoaded', () => {
    const body = document.body;
    const backdrop = document.getElementById('socialDrawerBackdrop');
    const drawers = Array.from(document.querySelectorAll('[data-social-drawer]'));
    const triggers = Array.from(document.querySelectorAll('[data-social-drawer-trigger]'));
    const closeButtons = Array.from(document.querySelectorAll('[data-social-drawer-close]'));
    const notificationsBody = document.querySelector('[data-notifications-drawer-body]');
    const friendsBody = document.querySelector('[data-friends-drawer-body]');
    const friendsSearchInput = document.getElementById('friendsDrawerSearchInput');
    const shareButtons = Array.from(document.querySelectorAll('[data-social-drawer-share-profile]'));
    const navButtons = Array.from(document.querySelectorAll('[data-social-drawer-nav]'));

    if (!backdrop || drawers.length === 0) {
      return;
    }

    let currentDrawer = '';
    let cachedFriends = [];

    const setDrawerState = (name) => {
      currentDrawer = name || '';
      body.classList.toggle('social-drawer-open', Boolean(currentDrawer));
      if (currentDrawer) {
        body.setAttribute('data-open-social-drawer', currentDrawer);
      } else {
        body.removeAttribute('data-open-social-drawer');
      }
      backdrop.setAttribute('aria-hidden', String(!currentDrawer));
      drawers.forEach((drawer) => {
        const matches = drawer.getAttribute('data-social-drawer') === currentDrawer;
        drawer.setAttribute('aria-hidden', String(!matches));
      });
    };

    const closeDrawer = () => {
      setDrawerState('');
    };

    const requireUser = () => {
      const current = getCurrentUser();
      if (current && current.userId) {
        return current;
      }
      window.location.href = appPath('/account/login-page');
      return null;
    };

    const buildProfileUrl = () => {
      const current = getCurrentUser();
      if (!current || !current.userId) {
        return appPath('/account/login-page');
      }
      return appPath('/profile/' + encodeURIComponent(current.userId));
    };

    const buildUserDetailUrl = (friendId) => {
      const current = getCurrentUser();
      const query = current && current.userId
        ? ('?currentUserId=' + encodeURIComponent(current.userId))
        : '';
      return appPath('/friendship/user-detail/' + encodeURIComponent(friendId)) + query;
    };

    const buildChatUrl = (friendId) => {
      const current = getCurrentUser();
      if (!current || !current.userId) {
        return appPath('/account/login-page');
      }
      return appPath('/chat/private?currentUserId=' + encodeURIComponent(current.userId) + '&friendId=' + encodeURIComponent(friendId));
    };

    const renderNotifications = (payload) => {
      const data = payload && typeof payload === 'object' ? payload : {};
      const friendRequestViews = Array.isArray(data.friendRequestViews) ? data.friendRequestViews : [];
      const achievementNotifications = Array.isArray(data.achievementNotifications) ? data.achievementNotifications : [];
      const systemNotifications = Array.isArray(data.systemNotifications) ? data.systemNotifications : [];

      const sections = [];

      if (friendRequestViews.length > 0) {
        sections.push(
          '<section class="social-drawer__stack">' +
            '<div class="social-drawer__section-title">Loi moi ket ban</div>' +
            friendRequestViews.map((item) => {
              const avatar = appPath(item.requesterAvatarPath || '/uploads/avatars/default-avatar.jpg');
              return '' +
                '<article class="social-drawer__notification-card">' +
                  '<div class="d-flex align-items-center gap-3">' +
                    '<img class="social-drawer__friend-avatar" src="' + escapeHtml(avatar) + '" alt="Anh dai dien">' +
                    '<div>' +
                      '<strong>' + escapeHtml(item.requesterName || item.requesterId || 'Nguoi choi') + '</strong>' +
                      '<small>' + escapeHtml(item.requesterEmail || 'Loi moi ket ban moi') + '</small>' +
                    '</div>' +
                  '</div>' +
                  '<p>' + escapeHtml('Muon ket noi va choi cung ban trong Game Hub.') + '</p>' +
                  '<div class="social-drawer__notification-actions">' +
                    '<a href="' + escapeHtml(buildUserDetailUrl(item.requesterId || '')) + '">Ho so</a>' +
                    '<button class="is-primary" type="button" data-friendship-action="accept" data-friendship-id="' + escapeHtml(item.friendshipId) + '">Chap nhan</button>' +
                    '<button type="button" data-friendship-action="decline" data-friendship-id="' + escapeHtml(item.friendshipId) + '">Tu choi</button>' +
                  '</div>' +
                '</article>';
            }).join('') +
          '</section>'
        );
      }

      if (achievementNotifications.length > 0) {
        sections.push(
          '<section class="social-drawer__stack">' +
            '<div class="social-drawer__section-title">Thanh tuu</div>' +
            achievementNotifications.slice(0, 6).map((item) => {
              const createdAt = item && item.createdAt ? String(item.createdAt) : '';
              return '' +
                '<article class="social-drawer__notification-card">' +
                  '<strong>' + escapeHtml(item.achievementName || 'Thanh tuu moi') + '</strong>' +
                  '<small>' + escapeHtml(createdAt) + '</small>' +
                '</article>';
            }).join('') +
          '</section>'
        );
      }

      if (systemNotifications.length > 0) {
        sections.push(
          '<section class="social-drawer__stack">' +
            '<div class="social-drawer__section-title">He thong</div>' +
            systemNotifications.slice(0, 6).map((item) => {
              const createdAt = item && item.createdAt ? String(item.createdAt) : '';
              return '' +
                '<article class="social-drawer__notification-card">' +
                  '<strong>' + escapeHtml(item.content || 'Thong diep he thong') + '</strong>' +
                  '<small>' + escapeHtml(createdAt) + '</small>' +
                '</article>';
            }).join('') +
          '</section>'
        );
      }

      notificationsBody.innerHTML = sections.length > 0
        ? sections.join('')
        : '<div class="social-drawer__empty">Chua co thong bao moi.</div>';

      notificationsBody.querySelectorAll('[data-friendship-action]').forEach((button) => {
        button.addEventListener('click', async () => {
          const action = button.getAttribute('data-friendship-action');
          const friendshipId = Number(button.getAttribute('data-friendship-id') || 0);
          if (!action || !friendshipId) {
            return;
          }
          button.disabled = true;
          try {
            const response = await fetch(appPath('/friendship/' + encodeURIComponent(action)), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ friendshipId: friendshipId })
            });
            const data = await response.json().catch(() => ({}));
            if (!response.ok || data.success === false) {
              throw new Error(data.error || 'Khong the cap nhat loi moi ket ban');
            }
            toast(action === 'accept' ? 'Da chap nhan loi moi ket ban' : 'Da tu choi loi moi ket ban', 'success');
            await loadNotifications(true);
            await loadFriends(true);
          } catch (error) {
            button.disabled = false;
            toast(String(error?.message || error || 'Khong the cap nhat loi moi ket ban'), 'danger');
          }
        });
      });
    };

    const loadNotifications = async (force) => {
      if (!notificationsBody) {
        return;
      }
      const current = requireUser();
      if (!current) {
        return;
      }
      notificationsBody.innerHTML = '<div class="social-drawer__placeholder">Dang tai thong bao...</div>';
      try {
        const response = await fetch(appPath('/friendship/api/notifications?currentUserId=' + encodeURIComponent(current.userId)), {
          cache: 'no-store'
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Khong the tai thong bao');
        }
        renderNotifications(data);
      } catch (error) {
        notificationsBody.innerHTML = '<div class="social-drawer__empty">' + escapeHtml(String(error?.message || error || 'Khong the tai thong bao')) + '</div>';
        if (force) {
          toast(String(error?.message || error || 'Khong the tai thong bao'), 'danger');
        }
      }
    };

    const renderFriends = () => {
      if (!friendsBody) {
        return;
      }
      const keyword = String(friendsSearchInput?.value || '').trim().toLowerCase();
      const visibleFriends = cachedFriends.filter((friend) => {
        if (!keyword) {
          return true;
        }
        const haystack = (
          String(friend.displayName || '') + ' ' +
          String(friend.email || '') + ' ' +
          String(friend.id || '')
        ).toLowerCase();
        return haystack.includes(keyword);
      });

      friendsBody.innerHTML = visibleFriends.length > 0
        ? visibleFriends.map((friend) => {
          const avatar = appPath(friend.avatarPath || '/uploads/avatars/default-avatar.jpg');
          const online = !!friend.online;
          return '' +
            '<article class="social-drawer__friend-item">' +
              '<img class="social-drawer__friend-avatar" src="' + escapeHtml(avatar) + '" alt="Anh dai dien">' +
              '<div class="social-drawer__friend-meta">' +
                '<div>' +
                  '<strong>' + escapeHtml(friend.displayName || friend.email || friend.id || 'Nguoi choi') + '</strong>' +
                  '<small>' + escapeHtml(friend.email || '') + '</small>' +
                '</div>' +
                '<span class="social-drawer__friend-status ' + (online ? 'is-online' : 'is-offline') + '">' + (online ? 'Truc tuyen' : 'Ngoai tuyen') + '</span>' +
                '<div class="social-drawer__friend-actions">' +
                  '<a href="' + escapeHtml(buildUserDetailUrl(friend.id || '')) + '">Ho so</a>' +
                  '<a href="' + escapeHtml(buildChatUrl(friend.id || '')) + '">Nhan tin</a>' +
                '</div>' +
              '</div>' +
            '</article>';
        }).join('')
        : '<div class="social-drawer__empty">' + escapeHtml(keyword ? 'Khong tim thay ban be phu hop.' : 'O day yen tinh qua. Hay tim kiem va ket noi ban be moi.') + '</div>';
    };

    const loadFriends = async (force) => {
      if (!friendsBody) {
        return;
      }
      const current = requireUser();
      if (!current) {
        return;
      }
      if (!force && cachedFriends.length > 0) {
        renderFriends();
        return;
      }
      friendsBody.innerHTML = '<div class="social-drawer__placeholder">Dang tai danh sach ban be...</div>';
      try {
        const response = await fetch(appPath('/friendship/friend-list?currentUserId=' + encodeURIComponent(current.userId)), {
          cache: 'no-store'
        });
        if (!response.ok) {
          throw new Error('Khong the tai danh sach ban be');
        }
        const data = await response.json();
        cachedFriends = Array.isArray(data) ? data : [];
        renderFriends();
      } catch (error) {
        cachedFriends = [];
        friendsBody.innerHTML = '<div class="social-drawer__empty">' + escapeHtml(String(error?.message || error || 'Khong the tai danh sach ban be')) + '</div>';
        if (force) {
          toast(String(error?.message || error || 'Khong the tai danh sach ban be'), 'danger');
        }
      }
    };

    const openDrawer = async (name) => {
      const current = requireUser();
      if (!current || !name) {
        return;
      }
      body.classList.remove('account-drawer-open');
      document.getElementById('accountDrawer')?.setAttribute('aria-hidden', 'true');
      document.getElementById('accountDrawerBackdrop')?.setAttribute('aria-hidden', 'true');
      setDrawerState(name);
      if (name === 'notifications') {
        await loadNotifications(false);
      }
      if (name === 'friends') {
        await loadFriends(false);
      }
    };

    triggers.forEach((trigger) => {
      trigger.addEventListener('click', async (event) => {
        event.preventDefault();
        await openDrawer(trigger.getAttribute('data-social-drawer-trigger'));
      });
    });

    closeButtons.forEach((button) => {
      button.addEventListener('click', closeDrawer);
    });

    backdrop.addEventListener('click', closeDrawer);

    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && currentDrawer) {
        closeDrawer();
      }
      if (event.key === 'Enter' && document.activeElement === friendsSearchInput) {
        const current = getCurrentUser();
        const query = String(friendsSearchInput.value || '').trim();
        if (!query) {
          return;
        }
        const base = appPath('/friendship/search?query=' + encodeURIComponent(query));
        const target = current && current.userId
          ? (base + '&currentUserId=' + encodeURIComponent(current.userId))
          : base;
        window.location.href = target;
      }
    });

    friendsSearchInput?.addEventListener('input', renderFriends);

    shareButtons.forEach((button) => {
      button.addEventListener('click', async () => {
        const current = requireUser();
        if (!current) {
          return;
        }
        const profileUrl = window.location.origin + buildProfileUrl();
        try {
          if (navigator.share) {
            await navigator.share({
              title: current.displayName || current.userId,
              url: profileUrl
            });
            return;
          }
        } catch (_) {
        }
        try {
          await navigator.clipboard.writeText(profileUrl);
          toast('Da sao chep lien ket ho so', 'success');
        } catch (_) {
          toast(profileUrl, 'info');
        }
      });
    });

    navButtons.forEach((button) => {
      button.addEventListener('click', () => {
        const target = button.getAttribute('data-social-drawer-nav');
        if (!target) {
          return;
        }
        window.location.href = appPath(target);
      });
    });

    window.addEventListener(USER_CHANGE_EVENT, () => {
      cachedFriends = [];
      if (!getCurrentUser()) {
        closeDrawer();
        return;
      }
      if (currentDrawer === 'friends') {
        void loadFriends(true);
      }
      if (currentDrawer === 'notifications') {
        void loadNotifications(true);
      }
    });
  });
})();
