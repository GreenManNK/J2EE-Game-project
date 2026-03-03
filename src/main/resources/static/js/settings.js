(function () {
  const THEME_KEY = "theme";
  const MUSIC_KEY = "music";
  const TOAST_ENABLED_KEY = "caroToastEnabled.v1";
  const SIDEBAR_DESKTOP_HIDDEN_KEY = "caroSidebarDesktopHidden.v2";
  const SIDEBAR_MOBILE_AUTO_CLOSE_KEY = "caroSidebarMobileAutoClose.v1";
  const FRIEND_LIST_SHOW_OFFLINE_KEY = "caroFriendListShowOffline.v1";
  const FRIEND_LIST_AUTO_REFRESH_KEY = "caroFriendListAutoRefresh.v1";
  const FRIEND_LIST_REFRESH_MS_KEY = "caroFriendListRefreshMs.v1";
  const FRIEND_LIST_ALLOWED_REFRESH_VALUES = [5000, 10000, 15000, 20000, 30000, 60000];

  function appPath(url) {
    if (window.CaroUrl && typeof window.CaroUrl.path === "function") {
      return window.CaroUrl.path(url);
    }
    return url;
  }

  function readStorage(key, fallbackValue) {
    try {
      const value = window.localStorage.getItem(key);
      return value == null ? fallbackValue : value;
    } catch (_) {
      return fallbackValue;
    }
  }

  function writeStorage(key, value) {
    try {
      window.localStorage.setItem(key, value);
    } catch (_) {
    }
  }

  function readBooleanPref(key, fallbackValue) {
    const raw = readStorage(key, null);
    if (raw == null) {
      return fallbackValue;
    }
    return raw === "1" || raw === "true";
  }

  function writeBooleanPref(key, value) {
    writeStorage(key, value ? "1" : "0");
  }

  function readFriendListRefreshMs() {
    const raw = Number.parseInt(String(readStorage(FRIEND_LIST_REFRESH_MS_KEY, "5000")), 10);
    if (Number.isFinite(raw) && FRIEND_LIST_ALLOWED_REFRESH_VALUES.includes(raw)) {
      return String(raw);
    }
    return "5000";
  }

  function applyTheme(mode) {
    const normalized = mode === "dark" ? "dark" : "light";
    document.body.classList.toggle("dark-mode", normalized === "dark");
    document.body.classList.toggle("light-mode", normalized === "light");
    writeStorage(THEME_KEY, normalized);
  }

  function setStatus(target, message, isSuccess) {
    if (!target) {
      return;
    }
    if (window.CaroUi && typeof window.CaroUi.setStatus === "function") {
      window.CaroUi.setStatus(target, message, isSuccess);
      return;
    }
    target.textContent = String(message || "");
  }

  function notify(message, type) {
    if (window.CaroUi && typeof window.CaroUi.toast === "function") {
      window.CaroUi.toast(message, { type: type || "info" });
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    const root = document.getElementById("settingsRoot");
    if (!root) {
      return;
    }

    const isAuthenticated = String(root.dataset.authenticated || "").toLowerCase() === "true";
    const authUserId = String(root.dataset.authUserId || "").trim();

    const accountForm = document.getElementById("accountSettingsForm");
    const passwordForm = document.getElementById("changePasswordSettingsForm");
    const accountStatus = document.getElementById("accountSettingsStatus");
    const passwordStatus = document.getElementById("passwordSettingsStatus");
    const preferencesStatus = document.getElementById("preferencesStatus");
    const securityStatus = document.getElementById("securitySettingsStatus");

    const displayNameInput = document.getElementById("settingsDisplayName");
    const emailInput = document.getElementById("settingsEmail");
    const avatarPathInput = document.getElementById("settingsAvatarPath");

    const currentPasswordInput = document.getElementById("settingsCurrentPassword");
    const newPasswordInput = document.getElementById("settingsNewPassword");
    const confirmPasswordInput = document.getElementById("settingsConfirmPassword");

    const themeSelect = document.getElementById("themeModeSelect");
    const languageSelect = document.getElementById("languageModeSelect");
    const desktopSidebarVisibleByDefault = document.getElementById("desktopSidebarVisibleByDefault");
    const mobileSidebarAutoClose = document.getElementById("mobileSidebarAutoClose");
    const homeMusicEnabled = document.getElementById("homeMusicEnabled");
    const toastNotificationsEnabled = document.getElementById("toastNotificationsEnabled");
    const showOfflineFriendsInSidebar = document.getElementById("showOfflineFriendsInSidebar");
    const autoRefreshFriendList = document.getElementById("autoRefreshFriendList");
    const friendListRefreshMsSelect = document.getElementById("friendListRefreshMsSelect");
    const savePreferencesBtn = document.getElementById("savePreferencesBtn");
    const resetPreferencesBtn = document.getElementById("resetPreferencesBtn");

    const activateAdminCodeInput = document.getElementById("settingsAdminCode");
    const activateAdminFromSettingsBtn = document.getElementById("activateAdminFromSettingsBtn");

    const i18n = window.CaroI18n;

    const syncFriendRefreshSelectState = () => {
      if (!friendListRefreshMsSelect || !autoRefreshFriendList) {
        return;
      }
      friendListRefreshMsSelect.disabled = !autoRefreshFriendList.checked;
    };

    const currentTheme = readStorage(THEME_KEY, "light") === "dark" ? "dark" : "light";
    if (themeSelect) {
      themeSelect.value = currentTheme;
    }
    applyTheme(currentTheme);

    if (languageSelect) {
      languageSelect.value = (i18n && typeof i18n.getLanguage === "function") ? (i18n.getLanguage() || "vi") : "vi";
    }
    if (i18n && typeof i18n.onChange === "function") {
      i18n.onChange((lang) => {
        if (languageSelect) {
          languageSelect.value = String(lang || "vi");
        }
      });
    }

    if (desktopSidebarVisibleByDefault) {
      desktopSidebarVisibleByDefault.checked = !readBooleanPref(SIDEBAR_DESKTOP_HIDDEN_KEY, true);
    }
    if (mobileSidebarAutoClose) {
      mobileSidebarAutoClose.checked = readBooleanPref(SIDEBAR_MOBILE_AUTO_CLOSE_KEY, true);
    }
    if (homeMusicEnabled) {
      homeMusicEnabled.checked = readStorage(MUSIC_KEY, "on") !== "off";
    }
    if (toastNotificationsEnabled) {
      toastNotificationsEnabled.checked = readBooleanPref(TOAST_ENABLED_KEY, true);
    }
    if (showOfflineFriendsInSidebar) {
      showOfflineFriendsInSidebar.checked = readBooleanPref(FRIEND_LIST_SHOW_OFFLINE_KEY, true);
    }
    if (autoRefreshFriendList) {
      autoRefreshFriendList.checked = readBooleanPref(FRIEND_LIST_AUTO_REFRESH_KEY, true);
    }
    if (friendListRefreshMsSelect) {
      friendListRefreshMsSelect.value = readFriendListRefreshMs();
    }
    syncFriendRefreshSelectState();

    themeSelect?.addEventListener("change", () => {
      applyTheme(themeSelect.value);
    });

    languageSelect?.addEventListener("change", () => {
      if (!i18n || typeof i18n.setLanguage !== "function") {
        return;
      }
      try {
        i18n.setLanguage(languageSelect.value === "en" ? "en" : "vi");
      } catch (_) {
      }
    });

    autoRefreshFriendList?.addEventListener("change", syncFriendRefreshSelectState);

    const savePreferences = () => {
      const theme = themeSelect && themeSelect.value === "dark" ? "dark" : "light";
      applyTheme(theme);

      writeBooleanPref(SIDEBAR_DESKTOP_HIDDEN_KEY, !(desktopSidebarVisibleByDefault && desktopSidebarVisibleByDefault.checked));
      writeBooleanPref(SIDEBAR_MOBILE_AUTO_CLOSE_KEY, !!(mobileSidebarAutoClose && mobileSidebarAutoClose.checked));
      writeStorage(MUSIC_KEY, (homeMusicEnabled && homeMusicEnabled.checked) ? "on" : "off");
      writeBooleanPref(TOAST_ENABLED_KEY, !!(toastNotificationsEnabled && toastNotificationsEnabled.checked));
      writeBooleanPref(FRIEND_LIST_SHOW_OFFLINE_KEY, !!(showOfflineFriendsInSidebar && showOfflineFriendsInSidebar.checked));
      writeBooleanPref(FRIEND_LIST_AUTO_REFRESH_KEY, !!(autoRefreshFriendList && autoRefreshFriendList.checked));

      const refreshValue = Number.parseInt(String(friendListRefreshMsSelect?.value || "5000"), 10);
      writeStorage(
        FRIEND_LIST_REFRESH_MS_KEY,
        Number.isFinite(refreshValue) && FRIEND_LIST_ALLOWED_REFRESH_VALUES.includes(refreshValue)
          ? String(refreshValue)
          : "5000"
      );

      setStatus(preferencesStatus, "Da luu tuy chon. Reload trang de cap nhat sidebar/friend list ngay.", true);
      if (toastNotificationsEnabled && toastNotificationsEnabled.checked) {
        notify("Da luu cai dat", "success");
      }
    };

    savePreferencesBtn?.addEventListener("click", savePreferences);

    resetPreferencesBtn?.addEventListener("click", () => {
      if (themeSelect) {
        themeSelect.value = "light";
      }
      applyTheme("light");

      if (languageSelect) {
        languageSelect.value = "vi";
      }
      if (i18n && typeof i18n.setLanguage === "function") {
        try {
          i18n.setLanguage("vi");
        } catch (_) {
        }
      }

      if (desktopSidebarVisibleByDefault) {
        desktopSidebarVisibleByDefault.checked = false;
      }
      if (mobileSidebarAutoClose) {
        mobileSidebarAutoClose.checked = true;
      }
      if (homeMusicEnabled) {
        homeMusicEnabled.checked = true;
      }
      if (toastNotificationsEnabled) {
        toastNotificationsEnabled.checked = true;
      }
      if (showOfflineFriendsInSidebar) {
        showOfflineFriendsInSidebar.checked = true;
      }
      if (autoRefreshFriendList) {
        autoRefreshFriendList.checked = true;
      }
      if (friendListRefreshMsSelect) {
        friendListRefreshMsSelect.value = "5000";
      }
      syncFriendRefreshSelectState();

      writeBooleanPref(SIDEBAR_DESKTOP_HIDDEN_KEY, true);
      writeBooleanPref(SIDEBAR_MOBILE_AUTO_CLOSE_KEY, true);
      writeStorage(MUSIC_KEY, "on");
      writeBooleanPref(TOAST_ENABLED_KEY, true);
      writeBooleanPref(FRIEND_LIST_SHOW_OFFLINE_KEY, true);
      writeBooleanPref(FRIEND_LIST_AUTO_REFRESH_KEY, true);
      writeStorage(FRIEND_LIST_REFRESH_MS_KEY, "5000");

      setStatus(preferencesStatus, "Da dua tat ca tuy chon ve mac dinh.", true);
      notify("Da reset cai dat", "success");
    });

    accountForm?.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (!isAuthenticated || !authUserId) {
        setStatus(accountStatus, "Ban can dang nhap de cap nhat tai khoan.", false);
        return;
      }

      const payload = {
        userId: authUserId,
        displayName: String(displayNameInput?.value || "").trim(),
        email: String(emailInput?.value || "").trim(),
        avatarPath: String(avatarPathInput?.value || "").trim()
      };

      try {
        const res = await fetch(appPath("/account/update-profile"), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload)
        });
        const data = await res.json();
        const ok = !!(data && data.success);
        const message = ok
          ? String(data?.data?.message || "Profile updated")
          : String(data?.error || "Cannot update profile");
        setStatus(accountStatus, message, ok);
        notify(message, ok ? "success" : "danger");
        if (!ok) {
          return;
        }

        const current = window.CaroUser?.get?.() || { userId: authUserId };
        const updated = data.data || {};
        window.CaroUser?.set?.({
          userId: updated.userId || current.userId || authUserId,
          displayName: updated.displayName || payload.displayName || current.displayName || "Player",
          email: updated.email || payload.email || current.email || "",
          role: updated.role || current.role || "User",
          avatarPath: updated.avatarPath || payload.avatarPath || current.avatarPath || "/uploads/avatars/default-avatar.jpg"
        });
      } catch (error) {
        const message = String(error?.message || error || "Cannot update profile");
        setStatus(accountStatus, message, false);
        notify(message, "danger");
      }
    });

    passwordForm?.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (!isAuthenticated || !authUserId) {
        setStatus(passwordStatus, "Ban can dang nhap de doi mat khau.", false);
        return;
      }

      const currentPassword = String(currentPasswordInput?.value || "");
      const newPassword = String(newPasswordInput?.value || "");
      const confirmPassword = String(confirmPasswordInput?.value || "");

      if (!currentPassword || !newPassword) {
        setStatus(passwordStatus, "Vui long nhap day du mat khau.", false);
        return;
      }
      if (newPassword !== confirmPassword) {
        setStatus(passwordStatus, "Nhap lai mat khau moi khong khop.", false);
        return;
      }

      try {
        const res = await fetch(appPath("/account/change-password"), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            userId: authUserId,
            currentPassword: currentPassword,
            newPassword: newPassword
          })
        });
        const data = await res.json();
        const ok = !!(data && data.success);
        const message = ok
          ? String(data?.data?.message || "Password changed")
          : String(data?.error || "Cannot change password");
        setStatus(passwordStatus, message, ok);
        notify(message, ok ? "success" : "danger");
        if (ok) {
          if (currentPasswordInput) currentPasswordInput.value = "";
          if (newPasswordInput) newPasswordInput.value = "";
          if (confirmPasswordInput) confirmPasswordInput.value = "";
        }
      } catch (error) {
        const message = String(error?.message || error || "Cannot change password");
        setStatus(passwordStatus, message, false);
        notify(message, "danger");
      }
    });

    activateAdminFromSettingsBtn?.addEventListener("click", async () => {
      if (!isAuthenticated || !authUserId) {
        setStatus(securityStatus, "Ban can dang nhap de kich hoat admin.", false);
        return;
      }
      const activationCode = String(activateAdminCodeInput?.value || "").trim();
      if (!activationCode) {
        setStatus(securityStatus, "Vui long nhap ma kich hoat admin.", false);
        return;
      }

      activateAdminFromSettingsBtn.disabled = true;
      try {
        const response = await fetch(appPath("/account/activate-admin"), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ userId: authUserId, activationCode: activationCode })
        });
        const payload = await response.json();
        const success = !!(payload && payload.success);
        const message = success
          ? String(payload?.data?.message || "Admin role activated")
          : String(payload?.error || "Cannot activate admin role");

        setStatus(securityStatus, message, success);
        notify(message, success ? "success" : "danger");

        if (!success) {
          return;
        }

        const current = window.CaroUser?.get?.() || { userId: authUserId };
        window.CaroUser?.set?.({
          userId: current.userId || authUserId,
          displayName: payload?.data?.displayName || current.displayName || "Player",
          email: payload?.data?.email || current.email || "",
          role: "Admin",
          avatarPath: payload?.data?.avatarPath || current.avatarPath || "/uploads/avatars/default-avatar.jpg"
        });

        if (activateAdminCodeInput) {
          activateAdminCodeInput.value = "";
        }
      } catch (error) {
        const message = String(error?.message || error || "Cannot activate admin role");
        setStatus(securityStatus, message, false);
        notify(message, "danger");
      } finally {
        activateAdminFromSettingsBtn.disabled = false;
      }
    });
  });
})();
