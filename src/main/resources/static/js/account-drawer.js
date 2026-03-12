(function () {
  const USER_CHANGE_EVENT = (window.CaroUser && window.CaroUser.eventName) || "caro:user-changed";

  function appPath(url) {
    if (window.CaroUrl && typeof window.CaroUrl.path === "function") {
      return window.CaroUrl.path(url);
    }
    return url;
  }

  function getCurrentUser() {
    return window.CaroUser && typeof window.CaroUser.get === "function"
      ? window.CaroUser.get()
      : null;
  }

  function toast(message, type) {
    window.CaroUi?.toast?.(message, { type: type || "info" });
  }

  document.addEventListener("DOMContentLoaded", () => {
    const body = document.body;
    const drawer = document.getElementById("accountDrawer");
    const backdrop = document.getElementById("accountDrawerBackdrop");
    if (!drawer || !backdrop) {
      return;
    }

    const openButtons = document.querySelectorAll("[data-account-drawer-open]");
    const closeButtons = document.querySelectorAll("[data-account-drawer-close]");
    const languageButton = document.querySelector("[data-account-language-btn]");
    const languageLabel = document.querySelector("[data-account-language-label]");
    const shareButton = document.querySelector("[data-account-share-btn]");
    const profileLinks = document.querySelectorAll("[data-account-profile-link]");
    const completionLabel = document.querySelector("[data-account-completion]");

    const closeDrawer = () => {
      body.classList.remove("account-drawer-open");
      drawer.setAttribute("aria-hidden", "true");
    };

    const openDrawer = () => {
      const current = getCurrentUser();
      if (!current || !current.userId) {
        window.location.href = appPath("/account/login-page");
        return;
      }
      body.classList.add("account-drawer-open");
      drawer.setAttribute("aria-hidden", "false");
    };

    const syncLanguage = () => {
      const lang = window.CaroI18n?.getLanguage?.() || "vi";
      if (languageLabel) {
        languageLabel.textContent = lang === "vi" ? "Tieng Viet" : "English";
      }
    };

    const syncProfileLinks = () => {
      const current = getCurrentUser();
      const href = current && current.userId
        ? appPath("/profile/" + encodeURIComponent(current.userId))
        : appPath("/account/login-page");
      profileLinks.forEach((link) => {
        link.setAttribute("href", href);
      });
      if (completionLabel) {
        completionLabel.textContent = current && current.onboardingCompleted
          ? "Ho so da hoan thien"
          : "Cap nhat thong tin ho so";
      }
    };

    openButtons.forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        openDrawer();
      });
    });

    closeButtons.forEach((button) => {
      button.addEventListener("click", closeDrawer);
    });

    backdrop.addEventListener("click", closeDrawer);
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && body.classList.contains("account-drawer-open")) {
        closeDrawer();
      }
    });

    shareButton?.addEventListener("click", async () => {
      const current = getCurrentUser();
      if (!current || !current.userId) {
        toast("Ban can dang nhap", "warning");
        return;
      }
      const profileUrl = window.location.origin + appPath("/profile/" + encodeURIComponent(current.userId));
      try {
        if (navigator.share) {
          await navigator.share({
            title: current.displayName || current.username || "Game Hub",
            url: profileUrl
          });
          return;
        }
      } catch (_) {
      }
      try {
        await navigator.clipboard.writeText(profileUrl);
        toast("Da sao chep lien ket ho so", "success");
      } catch (_) {
        toast(profileUrl, "info");
      }
    });

    languageButton?.addEventListener("click", () => {
      if (!window.CaroI18n || typeof window.CaroI18n.getLanguage !== "function" || typeof window.CaroI18n.setLanguage !== "function") {
        return;
      }
      const current = window.CaroI18n.getLanguage() || "vi";
      window.CaroI18n.setLanguage(current === "vi" ? "en" : "vi");
      syncLanguage();
    });

    document.addEventListener(USER_CHANGE_EVENT, syncProfileLinks);
    window.addEventListener(USER_CHANGE_EVENT, syncProfileLinks);
    window.CaroI18n?.onChange?.(syncLanguage);

    syncLanguage();
    syncProfileLinks();
  });
})();
