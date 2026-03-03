(function () {
  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === "function")
    ? window.CaroUrl.path
    : function (value) { return value; };
  const ui = window.CaroUi || {};

  function setStatus(el, message, ok) {
    if (!el) {
      return;
    }
    if (ui.setStatus) {
      ui.setStatus(el, message, ok);
      return;
    }
    el.textContent = String(message || "");
  }

  function toInt(value, fallback) {
    const parsed = Number.parseInt(String(value || ""), 10);
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function normalizeText(value) {
    return String(value == null ? "" : value).trim();
  }

  document.addEventListener("DOMContentLoaded", () => {
    const root = document.getElementById("adminCenterRoot");
    if (!root) {
      return;
    }

    const state = {
      users: [],
      usersPage: 0,
      usersPageSize: 10,
      usersTotalPages: 1,
      notices: [],
      logs: {
        page: 0,
        size: 50,
        totalPages: 1,
        totalItems: 0,
        items: [],
        summary: {}
      }
    };

    const userCountBadge = document.getElementById("adminCenterUserCount");
    const noticeCountBadge = document.getElementById("adminCenterNoticeCount");
    const logCountBadge = document.getElementById("adminCenterLogCount");

    const usersSearch = document.getElementById("adminUsersSearch");
    const usersBanFilter = document.getElementById("adminUsersBanFilter");
    const usersPageSize = document.getElementById("adminUsersPageSize");
    const usersReloadBtn = document.getElementById("adminUsersReloadBtn");
    const usersPrevBtn = document.getElementById("adminUsersPrevBtn");
    const usersNextBtn = document.getElementById("adminUsersNextBtn");
    const usersPagerText = document.getElementById("adminUsersPagerText");
    const usersTableBody = document.getElementById("adminUsersTableBody");
    const usersStatus = document.getElementById("adminUsersStatus");
    const usersExportCsvPage = document.getElementById("adminUsersExportCsvPage");
    const usersExportExcelAll = document.getElementById("adminUsersExportExcelAll");
    const createUserForm = document.getElementById("adminCenterCreateUserForm");

    const noticesForm = document.getElementById("adminNoticeForm");
    const noticesContent = document.getElementById("adminNoticeContent");
    const noticesList = document.getElementById("adminNoticeList");
    const noticesStatus = document.getElementById("adminNoticesStatus");

    const logsFilterForm = document.getElementById("adminLogsFilterForm");
    const logsQ = document.getElementById("adminLogsQ");
    const logsMethod = document.getElementById("adminLogsMethod");
    const logsFromDate = document.getElementById("adminLogsFromDate");
    const logsToDate = document.getElementById("adminLogsToDate");
    const logsSize = document.getElementById("adminLogsSize");
    const logsPrevBtn = document.getElementById("adminLogsPrevBtn");
    const logsNextBtn = document.getElementById("adminLogsNextBtn");
    const logsPagerText = document.getElementById("adminLogsPagerText");
    const logsStatus = document.getElementById("adminLogsStatus");
    const logsTableBody = document.getElementById("adminLogsTableBody");
    const logsExportCsvPage = document.getElementById("adminLogsExportCsvPage");
    const logsExportExcelAll = document.getElementById("adminLogsExportExcelAll");
    const logsTodayCount = document.getElementById("adminLogsTodayCount");
    const logsGuestCount = document.getElementById("adminLogsGuestCount");
    const logsUniqueUsers = document.getElementById("adminLogsUniqueUsers");
    const logsUniqueIps = document.getElementById("adminLogsUniqueIps");
    const logsPostCount = document.getElementById("adminLogsPostCount");
    const logsTotalCount = document.getElementById("adminLogsTotalCount");

    function updateTopBadges() {
      if (userCountBadge) {
        userCountBadge.textContent = state.users.length + " users";
      }
      if (noticeCountBadge) {
        noticeCountBadge.textContent = state.notices.length + " notices";
      }
      if (logCountBadge) {
        logCountBadge.textContent = state.logs.totalItems + " logs";
      }
    }

    function usersQuery() {
      return {
        searchTerm: normalizeText(usersSearch?.value),
        banFilter: normalizeText(usersBanFilter?.value)
      };
    }

    function buildUsersExportLinks() {
      const query = usersQuery();
      const params = new URLSearchParams();
      if (query.searchTerm) {
        params.set("searchTerm", query.searchTerm);
      }
      if (query.banFilter) {
        params.set("banFilter", query.banFilter);
      }
      params.set("page", String(state.usersPage));
      params.set("size", String(state.usersPageSize));

      if (usersExportCsvPage) {
        const csv = new URLSearchParams(params.toString());
        csv.set("scope", "page");
        usersExportCsvPage.href = appPath("/admin/export-users-csv") + "?" + csv.toString();
      }
      if (usersExportExcelAll) {
        const excel = new URLSearchParams(params.toString());
        excel.set("scope", "all");
        usersExportExcelAll.href = appPath("/admin/export-users-excel") + "?" + excel.toString();
      }
    }

    function renderUsers() {
      const total = state.users.length;
      state.usersTotalPages = Math.max(1, Math.ceil(total / state.usersPageSize));
      state.usersPage = Math.max(0, Math.min(state.usersPage, state.usersTotalPages - 1));
      const from = state.usersPage * state.usersPageSize;
      const to = Math.min(from + state.usersPageSize, total);
      const pageItems = state.users.slice(from, to);

      if (usersPagerText) {
        usersPagerText.textContent = "Page " + (state.usersPage + 1) + "/" + state.usersTotalPages;
      }
      if (usersPrevBtn) {
        usersPrevBtn.disabled = state.usersPage <= 0;
      }
      if (usersNextBtn) {
        usersNextBtn.disabled = state.usersPage + 1 >= state.usersTotalPages;
      }

      if (!usersTableBody) {
        return;
      }
      if (pageItems.length === 0) {
        usersTableBody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">Khong co user phu hop.</td></tr>';
        buildUsersExportLinks();
        updateTopBadges();
        return;
      }

      usersTableBody.innerHTML = pageItems.map((u) => {
        const id = escapeHtml(u.id);
        const email = escapeHtml(u.email);
        const displayName = escapeHtml(u.displayName || "");
        const role = escapeHtml(u.role || "User");
        const score = escapeHtml(String(u.score ?? 0));
        const banned = !!u.banned || !!u.isBanned || !!u.bannedUntil;
        const banLabel = banned ? "Banned" : "Active";
        const banBtn = banned
          ? '<button type="button" class="btn btn-sm btn-outline-success" data-user-action="unban" data-user-id="' + id + '">Unban</button>'
          : '<button type="button" class="btn btn-sm btn-outline-warning" data-user-action="ban60" data-user-id="' + id + '">Ban 60m</button>';
        return '' +
          "<tr>" +
          "<td>" + id + "</td>" +
          "<td>" + email + "</td>" +
          "<td>" + displayName + "</td>" +
          "<td>" + score + "</td>" +
          "<td>" + role + "</td>" +
          "<td>" + banLabel + "</td>" +
          "<td class=\"d-flex flex-wrap gap-1\">" +
          banBtn +
          "<button type=\"button\" class=\"btn btn-sm btn-outline-danger\" data-user-action=\"delete\" data-user-id=\"" + id + "\">Delete</button>" +
          "</td>" +
          "</tr>";
      }).join("");

      buildUsersExportLinks();
      updateTopBadges();
    }

    async function loadUsers(resetPage) {
      if (resetPage) {
        state.usersPage = 0;
      }
      state.usersPageSize = Math.max(1, toInt(usersPageSize?.value, 10));
      const query = usersQuery();
      const params = new URLSearchParams();
      if (query.searchTerm) {
        params.set("searchTerm", query.searchTerm);
      }
      if (query.banFilter) {
        params.set("banFilter", query.banFilter);
      }
      try {
        const response = await fetch(appPath("/admin/api/users") + (params.toString() ? ("?" + params.toString()) : ""), {
          cache: "no-store"
        });
        const data = await response.json();
        state.users = Array.isArray(data) ? data : [];
        renderUsers();
      } catch (error) {
        setStatus(usersStatus, String(error?.message || error || "Khong tai duoc danh sach user"), false);
        if (usersTableBody) {
          usersTableBody.innerHTML = '<tr><td colspan="7" class="text-center text-danger">Khong tai duoc danh sach user.</td></tr>';
        }
      }
    }

    async function createUser(payload) {
      const response = await fetch(appPath("/admin/users"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      return response.json();
    }

    async function userAction(userId, action) {
      if (!userId) {
        return;
      }
      let url = appPath("/admin/users/" + encodeURIComponent(userId));
      let method = "DELETE";
      let body = null;

      if (action === "ban60") {
        url = appPath("/admin/users/" + encodeURIComponent(userId) + "/ban");
        method = "POST";
        body = JSON.stringify({ durationMinutes: 60 });
      } else if (action === "unban") {
        url = appPath("/admin/users/" + encodeURIComponent(userId) + "/unban");
        method = "POST";
      }

      const response = await fetch(url, {
        method,
        headers: body ? { "Content-Type": "application/json" } : undefined,
        body
      });
      return response.json();
    }

    function renderNotices() {
      if (!noticesList) {
        return;
      }
      if (!Array.isArray(state.notices) || state.notices.length === 0) {
        noticesList.innerHTML = '<li class="list-group-item text-muted">Chua co notice.</li>';
        updateTopBadges();
        return;
      }
      noticesList.innerHTML = state.notices.map((n) => {
        const id = escapeHtml(n.id);
        const content = escapeHtml(n.content || "");
        const createdAt = escapeHtml(n.createdAt || "");
        return '' +
          '<li class="list-group-item d-flex justify-content-between align-items-center gap-2">' +
          '<span>' + content + (createdAt ? (" - " + createdAt) : "") + '</span>' +
          '<button type="button" class="btn btn-sm btn-outline-danger" data-notice-action="delete" data-notice-id="' + id + '">Delete</button>' +
          "</li>";
      }).join("");
      updateTopBadges();
    }

    async function loadNotices() {
      try {
        const response = await fetch(appPath("/admin/notices/api"), { cache: "no-store" });
        const data = await response.json();
        state.notices = Array.isArray(data) ? data : [];
        renderNotices();
      } catch (error) {
        setStatus(noticesStatus, String(error?.message || error || "Khong tai duoc notices"), false);
        if (noticesList) {
          noticesList.innerHTML = '<li class="list-group-item text-danger">Khong tai duoc notices.</li>';
        }
      }
    }

    async function createNotice(content) {
      const response = await fetch(appPath("/admin/notices"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content })
      });
      return response.json();
    }

    async function deleteNotice(id) {
      const response = await fetch(appPath("/admin/notices/" + encodeURIComponent(id)), {
        method: "DELETE"
      });
      return response.json();
    }

    function logsQuery() {
      return {
        q: normalizeText(logsQ?.value),
        method: normalizeText(logsMethod?.value),
        fromDate: normalizeText(logsFromDate?.value),
        toDate: normalizeText(logsToDate?.value)
      };
    }

    function buildLogsExportLinks() {
      const query = logsQuery();
      const params = new URLSearchParams();
      if (query.q) params.set("q", query.q);
      if (query.method) params.set("method", query.method);
      if (query.fromDate) params.set("fromDate", query.fromDate);
      if (query.toDate) params.set("toDate", query.toDate);
      params.set("page", String(state.logs.page));
      params.set("size", String(state.logs.size));

      if (logsExportCsvPage) {
        const csv = new URLSearchParams(params.toString());
        csv.set("scope", "page");
        logsExportCsvPage.href = appPath("/admin/access-logs/export-csv") + "?" + csv.toString();
      }
      if (logsExportExcelAll) {
        const excel = new URLSearchParams(params.toString());
        excel.set("scope", "all");
        logsExportExcelAll.href = appPath("/admin/access-logs/export-excel") + "?" + excel.toString();
      }
    }

    function renderLogs() {
      const totalPages = Math.max(1, toInt(state.logs.totalPages, 1));
      const page = Math.max(0, Math.min(toInt(state.logs.page, 0), totalPages - 1));
      state.logs.page = page;
      state.logs.totalPages = totalPages;

      if (logsPagerText) {
        logsPagerText.textContent = "Page " + (page + 1) + "/" + totalPages;
      }
      if (logsPrevBtn) {
        logsPrevBtn.disabled = page <= 0;
      }
      if (logsNextBtn) {
        logsNextBtn.disabled = page + 1 >= totalPages;
      }

      const summary = state.logs.summary || {};
      if (logsTodayCount) logsTodayCount.textContent = String(summary.todayCount ?? 0);
      if (logsGuestCount) logsGuestCount.textContent = String(summary.guestCount ?? 0);
      if (logsUniqueUsers) logsUniqueUsers.textContent = String(summary.uniqueUsers ?? 0);
      if (logsUniqueIps) logsUniqueIps.textContent = String(summary.uniqueIps ?? 0);
      if (logsPostCount) logsPostCount.textContent = String(summary.postCount ?? 0);
      if (logsTotalCount) logsTotalCount.textContent = String(state.logs.totalItems ?? 0);

      if (logsTableBody) {
        if (!Array.isArray(state.logs.items) || state.logs.items.length === 0) {
          logsTableBody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">Khong co log phu hop.</td></tr>';
        } else {
          logsTableBody.innerHTML = state.logs.items.map((log) => {
            const visitedAt = escapeHtml(log.visitedAt || "");
            const method = escapeHtml(log.httpMethod || "-");
            const path = escapeHtml(log.requestPath || "-");
            const userDisplay = escapeHtml(log.userDisplayName || "Guest");
            const userId = escapeHtml(log.userId || "-");
            const role = escapeHtml(log.userRole || "-");
            const ip = escapeHtml(log.clientIp || "-");
            const userAgent = escapeHtml(log.userAgent || "-");
            return '' +
              "<tr>" +
              "<td>" + visitedAt + "</td>" +
              "<td><span class=\"badge text-bg-light\">" + method + "</span></td>" +
              "<td>" + path + "</td>" +
              "<td><div>" + userDisplay + "</div><small class=\"text-muted\">" + userId + "</small></td>" +
              "<td>" + role + "</td>" +
              "<td>" + ip + "</td>" +
              "<td><div class=\"text-truncate\" style=\"max-width: 320px;\">" + userAgent + "</div></td>" +
              "</tr>";
          }).join("");
        }
      }

      buildLogsExportLinks();
      updateTopBadges();
    }

    async function loadLogs(resetPage) {
      if (resetPage) {
        state.logs.page = 0;
      }
      state.logs.size = Math.max(1, toInt(logsSize?.value, 50));

      const query = logsQuery();
      const params = new URLSearchParams();
      if (query.q) params.set("q", query.q);
      if (query.method) params.set("method", query.method);
      if (query.fromDate) params.set("fromDate", query.fromDate);
      if (query.toDate) params.set("toDate", query.toDate);
      params.set("page", String(state.logs.page));
      params.set("size", String(state.logs.size));

      try {
        const response = await fetch(appPath("/admin/access-logs/api") + "?" + params.toString(), {
          cache: "no-store"
        });
        const data = await response.json();
        state.logs.items = Array.isArray(data?.items) ? data.items : [];
        state.logs.page = toInt(data?.page, 0);
        state.logs.size = toInt(data?.size, state.logs.size);
        state.logs.totalPages = toInt(data?.totalPages, 1);
        state.logs.totalItems = toInt(data?.totalItems, 0);
        state.logs.summary = data?.summary || {};
        renderLogs();
      } catch (error) {
        setStatus(logsStatus, String(error?.message || error || "Khong tai duoc logs"), false);
        if (logsTableBody) {
          logsTableBody.innerHTML = '<tr><td colspan="7" class="text-center text-danger">Khong tai duoc logs.</td></tr>';
        }
      }
    }

    usersReloadBtn?.addEventListener("click", () => {
      void loadUsers(true);
    });
    usersPageSize?.addEventListener("change", () => {
      void loadUsers(true);
    });
    usersPrevBtn?.addEventListener("click", () => {
      if (state.usersPage <= 0) {
        return;
      }
      state.usersPage -= 1;
      renderUsers();
    });
    usersNextBtn?.addEventListener("click", () => {
      if (state.usersPage + 1 >= state.usersTotalPages) {
        return;
      }
      state.usersPage += 1;
      renderUsers();
    });
    usersSearch?.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        void loadUsers(true);
      }
    });
    usersBanFilter?.addEventListener("change", () => {
      void loadUsers(true);
    });

    createUserForm?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const scoreRaw = normalizeText(document.getElementById("adminCreateScore")?.value);
      const payload = {
        email: normalizeText(document.getElementById("adminCreateEmail")?.value),
        displayName: normalizeText(document.getElementById("adminCreateDisplayName")?.value),
        password: normalizeText(document.getElementById("adminCreatePassword")?.value),
        score: scoreRaw === "" ? 0 : toInt(scoreRaw, 0),
        role: normalizeText(document.getElementById("adminCreateRole")?.value) || "User",
        avatarPath: normalizeText(document.getElementById("adminCreateAvatarPath")?.value)
      };
      try {
        const data = await createUser(payload);
        const ok = !!(data && data.success);
        setStatus(usersStatus, ok ? "Tao nguoi dung thanh cong" : String(data?.error || "Tao nguoi dung that bai"), ok);
        if (ok) {
          createUserForm.reset();
          const defaultRole = document.getElementById("adminCreateRole");
          if (defaultRole) {
            defaultRole.value = "User";
          }
          const defaultScore = document.getElementById("adminCreateScore");
          if (defaultScore) {
            defaultScore.value = "0";
          }
          await loadUsers(false);
        }
      } catch (error) {
        setStatus(usersStatus, String(error?.message || error || "Tao nguoi dung that bai"), false);
      }
    });

    usersTableBody?.addEventListener("click", async (event) => {
      const button = event.target.closest("button[data-user-action][data-user-id]");
      if (!button) {
        return;
      }
      const action = button.getAttribute("data-user-action");
      const userId = button.getAttribute("data-user-id");
      if (!action || !userId) {
        return;
      }
      if (action === "delete" && !window.confirm("Xoa user nay?")) {
        return;
      }
      button.disabled = true;
      try {
        const data = await userAction(userId, action);
        const ok = !!(data && data.success);
        const successMessage = action === "delete"
          ? "Da xoa user"
          : (action === "ban60" ? "Da ban user 60 phut" : "Da unban user");
        setStatus(usersStatus, ok ? successMessage : String(data?.error || "Khong the cap nhat user"), ok);
        if (ok) {
          await loadUsers(false);
        } else {
          button.disabled = false;
        }
      } catch (error) {
        setStatus(usersStatus, String(error?.message || error || "Khong the cap nhat user"), false);
        button.disabled = false;
      }
    });

    noticesForm?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const content = normalizeText(noticesContent?.value);
      if (!content) {
        setStatus(noticesStatus, "Content is required", false);
        return;
      }
      try {
        const data = await createNotice(content);
        const ok = !!(data && data.success);
        setStatus(noticesStatus, ok ? "Da tao notice" : String(data?.error || "Khong the tao notice"), ok);
        if (ok) {
          if (noticesContent) {
            noticesContent.value = "";
          }
          await loadNotices();
        }
      } catch (error) {
        setStatus(noticesStatus, String(error?.message || error || "Khong the tao notice"), false);
      }
    });

    noticesList?.addEventListener("click", async (event) => {
      const button = event.target.closest("button[data-notice-action='delete'][data-notice-id]");
      if (!button) {
        return;
      }
      const id = button.getAttribute("data-notice-id");
      if (!id) {
        return;
      }
      button.disabled = true;
      try {
        const data = await deleteNotice(id);
        const ok = !!(data && data.success);
        setStatus(noticesStatus, ok ? "Da xoa notice" : String(data?.error || "Khong the xoa notice"), ok);
        if (ok) {
          await loadNotices();
        } else {
          button.disabled = false;
        }
      } catch (error) {
        setStatus(noticesStatus, String(error?.message || error || "Khong the xoa notice"), false);
        button.disabled = false;
      }
    });

    logsFilterForm?.addEventListener("submit", (event) => {
      event.preventDefault();
      void loadLogs(true);
    });
    logsSize?.addEventListener("change", () => {
      void loadLogs(true);
    });
    logsPrevBtn?.addEventListener("click", () => {
      if (state.logs.page <= 0) {
        return;
      }
      state.logs.page -= 1;
      void loadLogs(false);
    });
    logsNextBtn?.addEventListener("click", () => {
      if (state.logs.page + 1 >= state.logs.totalPages) {
        return;
      }
      state.logs.page += 1;
      void loadLogs(false);
    });

    void Promise.all([
      loadUsers(true),
      loadNotices(),
      loadLogs(true)
    ]);
  });
})();
