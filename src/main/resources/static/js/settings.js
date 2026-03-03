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
    const exportPreferencesBtn = document.getElementById("exportPreferencesBtn");
    const importPreferencesBtn = document.getElementById("importPreferencesBtn");
    const importPreferencesFileInput = document.getElementById("importPreferencesFileInput");

    const activateAdminCodeInput = document.getElementById("settingsAdminCode");
    const activateAdminFromSettingsBtn = document.getElementById("activateAdminFromSettingsBtn");

    const i18n = window.CaroI18n;

    const buildPreferencesPayload = () => {
      const refreshValue = Number.parseInt(String(friendListRefreshMsSelect?.value || "5000"), 10);
      return {
        version: 1,
        savedAt: new Date().toISOString(),
        theme: themeSelect && themeSelect.value === "dark" ? "dark" : "light",
        language: languageSelect && languageSelect.value === "en" ? "en" : "vi",
        sidebarDesktopVisibleByDefault: !!(desktopSidebarVisibleByDefault && desktopSidebarVisibleByDefault.checked),
        sidebarMobileAutoClose: !!(mobileSidebarAutoClose && mobileSidebarAutoClose.checked),
        homeMusicEnabled: !!(homeMusicEnabled && homeMusicEnabled.checked),
        toastNotificationsEnabled: !!(toastNotificationsEnabled && toastNotificationsEnabled.checked),
        showOfflineFriendsInSidebar: !!(showOfflineFriendsInSidebar && showOfflineFriendsInSidebar.checked),
        autoRefreshFriendList: !!(autoRefreshFriendList && autoRefreshFriendList.checked),
        friendListRefreshMs:
          Number.isFinite(refreshValue) && FRIEND_LIST_ALLOWED_REFRESH_VALUES.includes(refreshValue)
            ? refreshValue
            : 5000
      };
    };

    const applyPreferencesPayload = (prefs, persistChanges) => {
      const data = prefs || {};
      const theme = data.theme === "dark" ? "dark" : "light";
      if (themeSelect) {
        themeSelect.value = theme;
      }
      applyTheme(theme);

      const language = data.language === "en" ? "en" : "vi";
      if (languageSelect) {
        languageSelect.value = language;
      }
      if (i18n && typeof i18n.setLanguage === "function") {
        try {
          i18n.setLanguage(language);
        } catch (_) {
        }
      }

      if (desktopSidebarVisibleByDefault) {
        desktopSidebarVisibleByDefault.checked = !!data.sidebarDesktopVisibleByDefault;
      }
      if (mobileSidebarAutoClose) {
        mobileSidebarAutoClose.checked = data.sidebarMobileAutoClose !== false;
      }
      if (homeMusicEnabled) {
        homeMusicEnabled.checked = data.homeMusicEnabled !== false;
      }
      if (toastNotificationsEnabled) {
        toastNotificationsEnabled.checked = data.toastNotificationsEnabled !== false;
      }
      if (showOfflineFriendsInSidebar) {
        showOfflineFriendsInSidebar.checked = data.showOfflineFriendsInSidebar !== false;
      }
      if (autoRefreshFriendList) {
        autoRefreshFriendList.checked = data.autoRefreshFriendList !== false;
      }
      if (friendListRefreshMsSelect) {
        const refreshMs = Number.parseInt(String(data.friendListRefreshMs || 5000), 10);
        friendListRefreshMsSelect.value =
          Number.isFinite(refreshMs) && FRIEND_LIST_ALLOWED_REFRESH_VALUES.includes(refreshMs)
            ? String(refreshMs)
            : "5000";
      }
      syncFriendRefreshSelectState();

      if (!persistChanges) {
        return;
      }
      const normalized = buildPreferencesPayload();
      writeBooleanPref(SIDEBAR_DESKTOP_HIDDEN_KEY, !normalized.sidebarDesktopVisibleByDefault);
      writeBooleanPref(SIDEBAR_MOBILE_AUTO_CLOSE_KEY, normalized.sidebarMobileAutoClose);
      writeStorage(MUSIC_KEY, normalized.homeMusicEnabled ? "on" : "off");
      writeBooleanPref(TOAST_ENABLED_KEY, normalized.toastNotificationsEnabled);
      writeBooleanPref(FRIEND_LIST_SHOW_OFFLINE_KEY, normalized.showOfflineFriendsInSidebar);
      writeBooleanPref(FRIEND_LIST_AUTO_REFRESH_KEY, normalized.autoRefreshFriendList);
      writeStorage(FRIEND_LIST_REFRESH_MS_KEY, String(normalized.friendListRefreshMs));
    };

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
      applyPreferencesPayload(buildPreferencesPayload(), true);
      setStatus(preferencesStatus, "Da luu tuy chon. Reload trang de cap nhat sidebar/friend list ngay.", true);
      if (toastNotificationsEnabled && toastNotificationsEnabled.checked) {
        notify("Da luu cai dat", "success");
      }
    };

    savePreferencesBtn?.addEventListener("click", savePreferences);

    resetPreferencesBtn?.addEventListener("click", () => {
      applyPreferencesPayload({
        theme: "light",
        language: "vi",
        sidebarDesktopVisibleByDefault: false,
        sidebarMobileAutoClose: true,
        homeMusicEnabled: true,
        toastNotificationsEnabled: true,
        showOfflineFriendsInSidebar: true,
        autoRefreshFriendList: true,
        friendListRefreshMs: 5000
      }, true);
      setStatus(preferencesStatus, "Da dua tat ca tuy chon ve mac dinh.", true);
      notify("Da reset cai dat", "success");
    });

    exportPreferencesBtn?.addEventListener("click", () => {
      try {
        const payload = buildPreferencesPayload();
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = "caro-settings.json";
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
        setStatus(preferencesStatus, "Da export file cai dat JSON.", true);
      } catch (error) {
        setStatus(preferencesStatus, "Khong the export cai dat.", false);
      }
    });

    importPreferencesBtn?.addEventListener("click", () => {
      importPreferencesFileInput?.click?.();
    });

    importPreferencesFileInput?.addEventListener("change", () => {
      const file = importPreferencesFileInput.files && importPreferencesFileInput.files[0];
      if (!file) {
        return;
      }
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const parsed = JSON.parse(String(reader.result || "{}"));
          applyPreferencesPayload(parsed, true);
          setStatus(preferencesStatus, "Da import cai dat thanh cong.", true);
          notify("Da import cai dat", "success");
        } catch (_) {
          setStatus(preferencesStatus, "File cai dat khong hop le.", false);
        } finally {
          importPreferencesFileInput.value = "";
        }
      };
      reader.onerror = () => {
        setStatus(preferencesStatus, "Khong doc duoc file cai dat.", false);
        importPreferencesFileInput.value = "";
      };
      reader.readAsText(file);
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
