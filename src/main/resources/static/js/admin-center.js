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

  async function readJsonResponse(response) {
    const raw = await response.text();
    if (!raw) {
      return {};
    }
    try {
      return JSON.parse(raw);
    } catch (_error) {
      return { message: raw };
    }
  }

  function roleLabel(value) {
    const role = normalizeText(value).toLowerCase();
    if (role === "admin") return "Quan tri";
    if (role === "manager") return "Quan ly";
    if (role === "user") return "Nguoi choi";
    return value ? String(value) : "-";
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
      modules: [],
      previewModules: [],
      previewSource: "",
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
    const moduleCountBadge = document.getElementById("adminCenterModuleCount");
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

    const modulesStatus = document.getElementById("adminGameModulesStatus");
    const modulesTableBody = document.getElementById("adminGameModulesTableBody");
    const modulesImportUrlBtn = document.getElementById("adminGameModuleImportUrlBtn");
    const modulesPreviewUrlBtn = document.getElementById("adminGameModulePreviewUrlBtn");
    const modulesImportUrlInput = document.getElementById("adminGameModuleManifestUrl");
    const moduleReplaceAll = document.getElementById("adminGameModuleReplaceAll");
    const modulePreviewOverridePolicy = document.getElementById("adminGameModulePreviewOverridePolicy");
    const moduleForm = document.getElementById("adminGameModuleForm");
    const moduleResetBtn = document.getElementById("adminGameModuleResetBtn");
    const modulePreviewBtn = document.getElementById("adminGameModulePreviewBtn");
    const moduleImportPreviewBtn = document.getElementById("adminGameModuleImportPreviewBtn");
    const moduleClearPreviewBtn = document.getElementById("adminGameModuleClearPreviewBtn");
    const modulesPreviewMeta = document.getElementById("adminGameModulePreviewMeta");
    const modulesPreviewSummary = document.getElementById("adminGameModulePreviewSummary");
    const modulesPreviewTableBody = document.getElementById("adminGameModulePreviewTableBody");
    const moduleCode = document.getElementById("adminGameModuleCode");
    const moduleDisplayName = document.getElementById("adminGameModuleDisplayName");
    const moduleShortLabel = document.getElementById("adminGameModuleShortLabel");
    const moduleRuntime = document.getElementById("adminGameModuleRuntime");
    const moduleIconClass = document.getElementById("adminGameModuleIconClass");
    const moduleSourceType = document.getElementById("adminGameModuleSourceType");
    const moduleDetailMode = document.getElementById("adminGameModuleDetailMode");
    const modulePrimaryActionLabel = document.getElementById("adminGameModulePrimaryActionLabel");
    const moduleManifestUrl = document.getElementById("adminGameModuleManifestUrlField");
    const modulePrimaryActionUrl = document.getElementById("adminGameModulePrimaryActionUrl");
    const moduleEmbedUrl = document.getElementById("adminGameModuleEmbedUrl");
    const moduleApiBaseUrl = document.getElementById("adminGameModuleApiBaseUrl");
    const moduleDescription = document.getElementById("adminGameModuleDescription");
    const moduleRoadmap = document.getElementById("adminGameModuleRoadmap");
    const moduleAvailableNow = document.getElementById("adminGameModuleAvailableNow");
    const moduleSupportsOnline = document.getElementById("adminGameModuleSupportsOnline");
    const moduleSupportsOffline = document.getElementById("adminGameModuleSupportsOffline");
    const moduleSupportsGuest = document.getElementById("adminGameModuleSupportsGuest");
    const moduleOverrideExisting = document.getElementById("adminGameModuleOverrideExisting");

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
        userCountBadge.textContent = state.users.length + " tai khoan";
      }
      if (moduleCountBadge) {
        moduleCountBadge.textContent = state.modules.length + " module";
      }
      if (noticeCountBadge) {
        noticeCountBadge.textContent = state.notices.length + " thong bao";
      }
      if (logCountBadge) {
        logCountBadge.textContent = state.logs.totalItems + " log";
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
        usersPagerText.textContent = "Trang " + (state.usersPage + 1) + "/" + state.usersTotalPages;
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
        usersTableBody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">Khong co tai khoan phu hop.</td></tr>';
        buildUsersExportLinks();
        updateTopBadges();
        return;
      }

      usersTableBody.innerHTML = pageItems.map((u) => {
        const id = escapeHtml(u.id);
        const email = escapeHtml(u.email);
        const displayName = escapeHtml(u.displayName || "");
        const role = escapeHtml(roleLabel(u.role || "User"));
        const score = escapeHtml(String(u.score ?? 0));
        const banned = !!u.banned || !!u.isBanned || !!u.bannedUntil;
        const banLabel = banned ? "Da khoa" : "Dang hoat dong";
        const banBtn = banned
          ? '<button type="button" class="btn btn-sm btn-outline-success" data-user-action="unban" data-user-id="' + id + '">Mo khoa</button>'
          : '<button type="button" class="btn btn-sm btn-outline-warning" data-user-action="ban60" data-user-id="' + id + '">Khoa 60 phut</button>';
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
          "<button type=\"button\" class=\"btn btn-sm btn-outline-danger\" data-user-action=\"delete\" data-user-id=\"" + id + "\">Xoa</button>" +
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
        setStatus(usersStatus, String(error?.message || error || "Khong tai duoc danh sach tai khoan"), false);
        if (usersTableBody) {
          usersTableBody.innerHTML = '<tr><td colspan="7" class="text-center text-danger">Khong tai duoc danh sach tai khoan.</td></tr>';
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

    function readCheckbox(el, fallback) {
      if (!el) {
        return !!fallback;
      }
      return !!el.checked;
    }

    function splitLines(value) {
      return String(value || "")
        .split(/\r?\n/)
        .map((item) => normalizeText(item))
        .filter(Boolean);
    }

    function isExternalHttpUrl(value) {
      const normalized = normalizeText(value).toLowerCase();
      return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    function previewOverridePolicyLabel(value) {
      const normalized = normalizeText(value);
      if (normalized === "force-on") {
        return "bat override cho tat ca";
      }
      if (normalized === "force-off") {
        return "tat override cho tat ca";
      }
      return "giu theo module";
    }

    function previewImportOptions() {
      return {
        replaceAll: readCheckbox(moduleReplaceAll, false),
        overridePolicy: normalizeText(modulePreviewOverridePolicy?.value) || "keep"
      };
    }

    function applyOverridePolicyToModules(modules, overridePolicy) {
      const items = Array.isArray(modules) ? modules : [];
      return items.map((module) => {
        const next = Object.assign({}, module);
        if (overridePolicy === "force-on") {
          next.overrideExisting = true;
        } else if (overridePolicy === "force-off") {
          next.overrideExisting = false;
        }
        return next;
      });
    }

    function normalizeCodeKey(value) {
      return normalizeText(value).toLowerCase();
    }

    function computePreviewPlan() {
      const options = previewImportOptions();
      const previewItems = applyOverridePolicyToModules(state.previewModules, options.overridePolicy);
      const currentRegistry = new Map();
      (Array.isArray(state.modules) ? state.modules : []).forEach((module) => {
        const key = normalizeCodeKey(module.code);
        if (key) {
          currentRegistry.set(key, module);
        }
      });

      let newCount = 0;
      let updateCount = 0;
      let nativeOverrideCount = 0;
      const previewRows = previewItems.map((module) => {
        const key = normalizeCodeKey(module.code);
        const existing = currentRegistry.get(key);
        const registryImpact = existing ? "Cap nhat module dang co" : "Them moi vao registry";
        if (existing) {
          updateCount += 1;
        } else {
          newCount += 1;
        }
        const catalogImpact = module.overrideExisting
          ? "Co the ghi de game native trung code"
          : "Khong ghi de game native";
        if (module.overrideExisting) {
          nativeOverrideCount += 1;
        }
        return {
          module,
          registryImpact,
          catalogImpact,
          existing
        };
      });

      const previewKeys = new Set(previewRows.map((row) => normalizeCodeKey(row.module.code)).filter(Boolean));
      const removedRows = options.replaceAll
        ? (Array.isArray(state.modules) ? state.modules : []).filter((module) => !previewKeys.has(normalizeCodeKey(module.code)))
        : [];

      return {
        options,
        previewRows,
        newCount,
        updateCount,
        nativeOverrideCount,
        removedCount: removedRows.length,
        removedRows
      };
    }

    function buildReplaceAllConfirmationMessage(plan) {
      if (!plan || !plan.options || !plan.options.replaceAll) {
        return "";
      }
      const removedCodes = Array.isArray(plan.removedRows)
        ? plan.removedRows.map((item) => normalizeText(item.code)).filter(Boolean)
        : [];
      const removedPreview = removedCodes.slice(0, 12).join(", ");
      let message =
        "Ban dang bat che do thay toan bo registry.\n\n" +
        "- Module moi: " + plan.newCount + "\n" +
        "- Module cap nhat: " + plan.updateCount + "\n" +
        "- Module co override native: " + plan.nativeOverrideCount + "\n" +
        "- Module se bi bo khoi registry: " + plan.removedCount + "\n";
      if (removedPreview) {
        message += "\nMa se bi bo:\n" + removedPreview;
        if (removedCodes.length > 12) {
          message += "\n...";
        }
      }
      message += "\n\nXac nhan tiep tuc import?";
      return message;
    }

    function confirmPreviewImport(plan) {
      if (!plan || !plan.options || !plan.options.replaceAll) {
        return true;
      }
      return window.confirm(buildReplaceAllConfirmationMessage(plan));
    }

    function resetModuleForm() {
      moduleForm?.reset();
      if (moduleSourceType) moduleSourceType.value = "external-module";
      if (moduleDetailMode) moduleDetailMode.value = "redirect";
      if (moduleIconClass) moduleIconClass.value = "bi-controller";
      if (moduleRuntime) moduleRuntime.value = "external";
      if (moduleAvailableNow) moduleAvailableNow.checked = true;
      if (moduleSupportsGuest) moduleSupportsGuest.checked = true;
      if (moduleSupportsOnline) moduleSupportsOnline.checked = false;
      if (moduleSupportsOffline) moduleSupportsOffline.checked = false;
      if (moduleOverrideExisting) moduleOverrideExisting.checked = false;
    }

    function modulePayloadFromForm() {
      return {
        code: normalizeText(moduleCode?.value).toLowerCase(),
        displayName: normalizeText(moduleDisplayName?.value),
        shortLabel: normalizeText(moduleShortLabel?.value),
        description: normalizeText(moduleDescription?.value),
        iconClass: normalizeText(moduleIconClass?.value) || "bi-controller",
        availableNow: readCheckbox(moduleAvailableNow, true),
        supportsOnline: readCheckbox(moduleSupportsOnline, false),
        supportsOffline: readCheckbox(moduleSupportsOffline, false),
        supportsGuest: readCheckbox(moduleSupportsGuest, true),
        primaryActionLabel: normalizeText(modulePrimaryActionLabel?.value),
        primaryActionUrl: normalizeText(modulePrimaryActionUrl?.value),
        roadmapItems: splitLines(moduleRoadmap?.value),
        sourceType: normalizeText(moduleSourceType?.value) || "external-module",
        runtime: normalizeText(moduleRuntime?.value) || "external",
        detailMode: normalizeText(moduleDetailMode?.value) || "redirect",
        embedUrl: normalizeText(moduleEmbedUrl?.value),
        apiBaseUrl: normalizeText(moduleApiBaseUrl?.value),
        manifestUrl: normalizeText(moduleManifestUrl?.value),
        overrideExisting: readCheckbox(moduleOverrideExisting, false)
      };
    }

    function fillModuleForm(module) {
      if (!module) {
        resetModuleForm();
        return;
      }
      if (moduleCode) moduleCode.value = normalizeText(module.code);
      if (moduleDisplayName) moduleDisplayName.value = normalizeText(module.displayName);
      if (moduleShortLabel) moduleShortLabel.value = normalizeText(module.shortLabel);
      if (moduleDescription) moduleDescription.value = normalizeText(module.description);
      if (moduleIconClass) moduleIconClass.value = normalizeText(module.iconClass) || "bi-controller";
      if (moduleSourceType) moduleSourceType.value = normalizeText(module.sourceType) || "external-module";
      if (moduleRuntime) moduleRuntime.value = normalizeText(module.runtime) || "external";
      if (moduleDetailMode) moduleDetailMode.value = normalizeText(module.detailMode) || "redirect";
      if (modulePrimaryActionLabel) modulePrimaryActionLabel.value = normalizeText(module.primaryActionLabel);
      if (modulePrimaryActionUrl) modulePrimaryActionUrl.value = normalizeText(module.primaryActionUrl);
      if (moduleEmbedUrl) moduleEmbedUrl.value = normalizeText(module.embedUrl);
      if (moduleApiBaseUrl) moduleApiBaseUrl.value = normalizeText(module.apiBaseUrl);
      if (moduleManifestUrl) moduleManifestUrl.value = normalizeText(module.manifestUrl);
      if (moduleAvailableNow) moduleAvailableNow.checked = !!module.availableNow;
      if (moduleSupportsOnline) moduleSupportsOnline.checked = !!module.supportsOnline;
      if (moduleSupportsOffline) moduleSupportsOffline.checked = !!module.supportsOffline;
      if (moduleSupportsGuest) moduleSupportsGuest.checked = !!module.supportsGuest;
      if (moduleOverrideExisting) moduleOverrideExisting.checked = !!module.overrideExisting;
      if (moduleRoadmap) {
        const roadmapItems = Array.isArray(module.roadmapItems) ? module.roadmapItems : [];
        moduleRoadmap.value = roadmapItems.join("\n");
      }
    }

    function renderModules() {
      if (!modulesTableBody) {
        updateTopBadges();
        return;
      }
      if (!Array.isArray(state.modules) || state.modules.length === 0) {
        modulesTableBody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">Chua co module nao.</td></tr>';
        updateTopBadges();
        return;
      }
      modulesTableBody.innerHTML = state.modules.map((module) => {
        const code = escapeHtml(module.code);
        const rawCode = normalizeText(module.code);
        const displayName = escapeHtml(module.displayName);
        const sourceType = escapeHtml(module.sourceType || "external-module");
        const runtime = escapeHtml(module.runtime || "external");
        const actionUrl = normalizeText(module.primaryActionUrl);
        const apiBaseUrl = normalizeText(module.apiBaseUrl);
        const proxyApiBasePath = apiBaseUrl ? appPath("/games/external/" + encodeURIComponent(rawCode) + "/api") : "";
        const statusParts = [];
        if (module.availableNow) statusParts.push("San sang");
        if (module.supportsOnline) statusParts.push("Online");
        if (module.supportsOffline) statusParts.push("Cung may");
        if (module.supportsGuest) statusParts.push("Khach");
        const statusLabel = escapeHtml(statusParts.join(" / ") || "Chua ro");
        const actionLinks = [];
        if (isExternalHttpUrl(actionUrl)) {
          actionLinks.push('<a class="btn btn-sm btn-outline-secondary" href="' + escapeHtml(actionUrl) + '" target="_blank" rel="noopener">Mo</a>');
        }
        if (proxyApiBasePath) {
          actionLinks.push('<a class="btn btn-sm btn-outline-secondary" href="' + escapeHtml(proxyApiBasePath) + '" target="_blank" rel="noopener">Gateway</a>');
        } else if (apiBaseUrl) {
          actionLinks.push('<span class="small text-muted">API goc: ' + escapeHtml(apiBaseUrl) + "</span>");
        }
        return "" +
          "<tr>" +
          "<td>" + code + "</td>" +
          "<td><strong>" + displayName + "</strong><div class=\"small text-muted\">" + escapeHtml(module.description || "") + "</div></td>" +
          "<td><div>" + sourceType + "</div><small class=\"text-muted\">" + runtime + "</small></td>" +
          "<td><div class=\"d-flex flex-wrap gap-1\">" + actionLinks.join("") + "</div></td>" +
          "<td>" + statusLabel + "</td>" +
          "<td class=\"d-flex flex-wrap gap-1\">" +
          "<button type=\"button\" class=\"btn btn-sm btn-outline-primary\" data-module-action=\"edit\" data-module-code=\"" + code + "\">Sua</button>" +
          "<button type=\"button\" class=\"btn btn-sm btn-outline-danger\" data-module-action=\"delete\" data-module-code=\"" + code + "\">Xoa</button>" +
          "</td>" +
          "</tr>";
      }).join("");
      updateTopBadges();
    }

    function renderModulePreview() {
      const plan = computePreviewPlan();
      if (moduleImportPreviewBtn) {
        moduleImportPreviewBtn.disabled = !Array.isArray(state.previewModules) || state.previewModules.length === 0;
      }
      if (modulesPreviewMeta) {
        if (!Array.isArray(state.previewModules) || state.previewModules.length === 0) {
          modulesPreviewMeta.textContent = "Chua co du lieu preview.";
        } else {
          const importMode = plan.options.replaceAll ? "thay toan bo registry" : "gop vao registry hien tai";
          modulesPreviewMeta.textContent =
            state.previewModules.length +
            " module se duoc import tu " + (state.previewSource || "form hien tai") +
            ". Che do: " + importMode +
            ". Override: " + previewOverridePolicyLabel(plan.options.overridePolicy) + ".";
        }
      }
      if (modulesPreviewSummary) {
        if (!Array.isArray(state.previewModules) || state.previewModules.length === 0) {
          modulesPreviewSummary.textContent = "Chua co ke hoach import.";
        } else {
          let summary = "Them moi: " + plan.newCount +
            ". Cap nhat: " + plan.updateCount +
            ". Override native: " + plan.nativeOverrideCount + ".";
          if (plan.options.replaceAll) {
            summary += " Se bo " + plan.removedCount + " module dang co khong nam trong preview.";
            if (plan.removedRows.length > 0) {
              summary += " Ma bi bo: " + plan.removedRows.slice(0, 6).map((item) => item.code).join(", ");
              if (plan.removedRows.length > 6) {
                summary += ", ...";
              }
            }
          }
          modulesPreviewSummary.textContent = summary;
        }
      }
      if (!modulesPreviewTableBody) {
        return;
      }
      if (!Array.isArray(state.previewModules) || state.previewModules.length === 0) {
        modulesPreviewTableBody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">Chua co du lieu preview.</td></tr>';
        return;
      }

      modulesPreviewTableBody.innerHTML = plan.previewRows.map((row) => {
        const module = row.module;
        const code = escapeHtml(module.code);
        const sourceType = escapeHtml(module.sourceType || "external-module");
        const actionTarget = escapeHtml(module.primaryActionUrl || module.apiBaseUrl || module.embedUrl || "-");
        const statusParts = [];
        if (module.availableNow) statusParts.push("San sang");
        if (module.supportsOnline) statusParts.push("Online");
        if (module.supportsOffline) statusParts.push("Cung may");
        if (module.supportsGuest) statusParts.push("Khach");
        const impactBadges = [];
        impactBadges.push("<div>" + escapeHtml(row.registryImpact) + "</div>");
        impactBadges.push("<small class=\"text-muted\">" + escapeHtml(row.catalogImpact) + "</small>");
        if (row.existing) {
          impactBadges.push("<small class=\"text-muted\">Dang co: " + escapeHtml(row.existing.displayName || row.existing.code || "") + "</small>");
        }
        return "" +
          "<tr>" +
          "<td>" + code + "</td>" +
          "<td><strong>" + escapeHtml(module.displayName || "") + "</strong><div class=\"small text-muted\">" + escapeHtml(module.description || "") + "</div></td>" +
          "<td><div>" + sourceType + "</div><small class=\"text-muted\">" + escapeHtml(module.runtime || "external") + "</small></td>" +
          "<td><small class=\"text-muted\">" + actionTarget + "</small></td>" +
          "<td>" + escapeHtml(statusParts.join(" / ") || "Chua ro") + "</td>" +
          "<td>" + impactBadges.join("") + "</td>" +
          "<td><button type=\"button\" class=\"btn btn-sm btn-outline-primary\" data-preview-action=\"load\" data-preview-code=\"" + code + "\">Nap vao form</button></td>" +
          "</tr>";
      }).join("");
    }

    async function loadModules() {
      try {
        const response = await fetch(appPath("/admin/game-modules/api"), { cache: "no-store" });
        const data = await readJsonResponse(response);
        if (!response.ok) {
          throw new Error(normalizeText(data?.message || data?.error) || "Khong tai duoc danh sach module");
        }
        state.modules = Array.isArray(data?.modules) ? data.modules : [];
        renderModules();
      } catch (error) {
        setStatus(modulesStatus, String(error?.message || error || "Khong tai duoc danh sach module"), false);
        if (modulesTableBody) {
          modulesTableBody.innerHTML = '<tr><td colspan="6" class="text-center text-danger">Khong tai duoc danh sach module.</td></tr>';
        }
      }
    }

    async function saveModule(payload) {
      const response = await fetch(appPath("/admin/game-modules/api/import"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ replaceAll: false, modules: [payload] })
      });
      const data = await readJsonResponse(response);
      if (!response.ok) {
        throw new Error(normalizeText(data?.message || data?.error) || "Khong luu duoc module");
      }
      return data;
    }

    async function importPreviewModules(modules, options) {
      const safeOptions = options || previewImportOptions();
      const importModules = applyOverridePolicyToModules(modules, safeOptions.overridePolicy);
      const response = await fetch(appPath("/admin/game-modules/api/import"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ replaceAll: !!safeOptions.replaceAll, modules: importModules })
      });
      const data = await readJsonResponse(response);
      if (!response.ok) {
        throw new Error(normalizeText(data?.message || data?.error) || "Khong nhap duoc preview");
      }
      return data;
    }

    async function importModulesFromUrl(manifestUrl) {
      const previewData = await previewModulesFromUrl(manifestUrl);
      const options = previewImportOptions();
      state.previewModules = Array.isArray(previewData?.modules) ? previewData.modules : [];
      state.previewSource = normalizeText(previewData?.manifestUrl) || manifestUrl;
      renderModulePreview();
      const plan = computePreviewPlan();
      if (!confirmPreviewImport(plan)) {
        throw new Error("Da huy import replaceAll.");
      }
      return importPreviewModules(state.previewModules, options);
    }

    async function previewModule(payload) {
      const response = await fetch(appPath("/admin/game-modules/api/preview"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ replaceAll: false, modules: [payload] })
      });
      const data = await readJsonResponse(response);
      if (!response.ok) {
        throw new Error(normalizeText(data?.message || data?.error) || "Khong preview duoc module");
      }
      return data;
    }

    async function previewModulesFromUrl(manifestUrl) {
      const response = await fetch(appPath("/admin/game-modules/api/preview-url"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ manifestUrl: manifestUrl, replaceAll: false })
      });
      const data = await readJsonResponse(response);
      if (!response.ok) {
        throw new Error(normalizeText(data?.message || data?.error) || "Khong preview duoc manifest");
      }
      return data;
    }

    async function deleteModule(code) {
      const response = await fetch(appPath("/admin/game-modules/api/" + encodeURIComponent(code)), {
        method: "DELETE"
      });
      const data = await readJsonResponse(response);
      if (!response.ok) {
        throw new Error(normalizeText(data?.message || data?.error) || "Khong xoa duoc module");
      }
      return data;
    }

    function renderNotices() {
      if (!noticesList) {
        return;
      }
      if (!Array.isArray(state.notices) || state.notices.length === 0) {
        noticesList.innerHTML = '<li class="list-group-item text-muted">Chua co thong bao.</li>';
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
          '<button type="button" class="btn btn-sm btn-outline-danger" data-notice-action="delete" data-notice-id="' + id + '">Xoa</button>' +
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
        setStatus(noticesStatus, String(error?.message || error || "Khong tai duoc thong bao"), false);
        if (noticesList) {
          noticesList.innerHTML = '<li class="list-group-item text-danger">Khong tai duoc thong bao.</li>';
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
        logsPagerText.textContent = "Trang " + (page + 1) + "/" + totalPages;
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
            const userDisplay = escapeHtml(log.userDisplayName || "Khach");
            const userId = escapeHtml(log.userId || "-");
            const role = escapeHtml(roleLabel(log.userRole || "-"));
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
        setStatus(logsStatus, String(error?.message || error || "Khong tai duoc log"), false);
        if (logsTableBody) {
          logsTableBody.innerHTML = '<tr><td colspan="7" class="text-center text-danger">Khong tai duoc log.</td></tr>';
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
        setStatus(usersStatus, ok ? "Tao tai khoan thanh cong" : String(data?.error || "Tao tai khoan that bai"), ok);
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
        setStatus(usersStatus, String(error?.message || error || "Tao tai khoan that bai"), false);
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
      if (action === "delete" && !window.confirm("Xoa tai khoan nay?")) {
        return;
      }
      button.disabled = true;
      try {
        const data = await userAction(userId, action);
        const ok = !!(data && data.success);
        const successMessage = action === "delete"
          ? "Da xoa tai khoan"
          : (action === "ban60" ? "Da khoa tai khoan 60 phut" : "Da mo khoa tai khoan");
        setStatus(usersStatus, ok ? successMessage : String(data?.error || "Khong the cap nhat tai khoan"), ok);
        if (ok) {
          await loadUsers(false);
        } else {
          button.disabled = false;
        }
      } catch (error) {
        setStatus(usersStatus, String(error?.message || error || "Khong the cap nhat tai khoan"), false);
        button.disabled = false;
      }
    });

    moduleForm?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const payload = modulePayloadFromForm();
      if (!payload.code || !payload.displayName) {
        setStatus(modulesStatus, "Code va ten hien thi la bat buoc", false);
        return;
      }
      try {
        const data = await saveModule(payload);
        const ok = !!(data && data.success);
        setStatus(modulesStatus, ok ? "Da luu module game ngoai" : String(data?.error || "Khong luu duoc module"), ok);
        if (ok) {
          await loadModules();
        }
      } catch (error) {
        setStatus(modulesStatus, String(error?.message || error || "Khong luu duoc module"), false);
      }
    });

    moduleResetBtn?.addEventListener("click", () => {
      resetModuleForm();
      setStatus(modulesStatus, "", true);
    });

    moduleReplaceAll?.addEventListener("change", () => {
      renderModulePreview();
    });

    modulePreviewOverridePolicy?.addEventListener("change", () => {
      renderModulePreview();
    });

    moduleClearPreviewBtn?.addEventListener("click", () => {
      state.previewModules = [];
      state.previewSource = "";
      renderModulePreview();
      setStatus(modulesStatus, "Da xoa du lieu preview", true);
    });

    modulePreviewBtn?.addEventListener("click", async () => {
      const payload = modulePayloadFromForm();
      if (!payload.code || !payload.displayName) {
        setStatus(modulesStatus, "Code va ten hien thi la bat buoc", false);
        return;
      }
      modulePreviewBtn.disabled = true;
      try {
        const data = await previewModule(payload);
        const ok = !!(data && data.success);
        state.previewModules = Array.isArray(data?.modules) ? data.modules : [];
        state.previewSource = "form hien tai";
        renderModulePreview();
        setStatus(modulesStatus, ok ? "Da kiem tra form module" : String(data?.error || "Khong preview duoc module"), ok);
      } catch (error) {
        setStatus(modulesStatus, String(error?.message || error || "Khong preview duoc module"), false);
      } finally {
        modulePreviewBtn.disabled = false;
      }
    });

    modulesImportUrlBtn?.addEventListener("click", async () => {
      const manifestUrl = normalizeText(modulesImportUrlInput?.value);
      if (!manifestUrl) {
        setStatus(modulesStatus, "Manifest URL la bat buoc", false);
        return;
      }
      modulesImportUrlBtn.disabled = true;
      try {
        const data = await importModulesFromUrl(manifestUrl);
        const ok = !!(data && data.success);
        setStatus(modulesStatus, ok ? "Da nhap module tu manifest URL" : String(data?.error || "Khong nhap duoc manifest"), ok);
        if (ok) {
          state.previewModules = [];
          state.previewSource = "";
          await loadModules();
        }
      } catch (error) {
        const message = String(error?.message || error || "Khong nhap duoc manifest");
        setStatus(modulesStatus, message === "Da huy import replaceAll." ? "Da huy import sau khi xem danh sach module bi bo." : message, false);
      } finally {
        renderModulePreview();
        modulesImportUrlBtn.disabled = false;
      }
    });

    modulesPreviewUrlBtn?.addEventListener("click", async () => {
      const manifestUrl = normalizeText(modulesImportUrlInput?.value);
      if (!manifestUrl) {
        setStatus(modulesStatus, "Manifest URL la bat buoc", false);
        return;
      }
      modulesPreviewUrlBtn.disabled = true;
      try {
        const data = await previewModulesFromUrl(manifestUrl);
        const ok = !!(data && data.success);
        state.previewModules = Array.isArray(data?.modules) ? data.modules : [];
        state.previewSource = normalizeText(data?.manifestUrl) || manifestUrl;
        renderModulePreview();
        setStatus(modulesStatus, ok ? "Da preview manifest URL" : String(data?.error || "Khong preview duoc manifest"), ok);
      } catch (error) {
        setStatus(modulesStatus, String(error?.message || error || "Khong preview duoc manifest"), false);
      } finally {
        modulesPreviewUrlBtn.disabled = false;
      }
    });

    moduleImportPreviewBtn?.addEventListener("click", async () => {
      if (!Array.isArray(state.previewModules) || state.previewModules.length === 0) {
        setStatus(modulesStatus, "Chua co du lieu preview de import", false);
        return;
      }
      moduleImportPreviewBtn.disabled = true;
      try {
        const options = previewImportOptions();
        const plan = computePreviewPlan();
        if (!confirmPreviewImport(plan)) {
          setStatus(modulesStatus, "Da huy import sau khi xem danh sach module bi bo.", false);
          return;
        }
        const data = await importPreviewModules(state.previewModules, options);
        const ok = !!(data && data.success);
        setStatus(
          modulesStatus,
          ok ? ("Da import " + Number(data?.importedModules || state.previewModules.length) + " module tu preview") : String(data?.error || "Khong nhap duoc preview"),
          ok
        );
        if (ok) {
          state.previewModules = [];
          state.previewSource = "";
          await loadModules();
        }
      } catch (error) {
        setStatus(modulesStatus, String(error?.message || error || "Khong nhap duoc preview"), false);
      } finally {
        renderModulePreview();
      }
    });

    modulesTableBody?.addEventListener("click", async (event) => {
      const button = event.target.closest("button[data-module-action][data-module-code]");
      if (!button) {
        return;
      }
      const action = button.getAttribute("data-module-action");
      const code = button.getAttribute("data-module-code");
      if (!action || !code) {
        return;
      }

      if (action === "edit") {
        const found = state.modules.find((item) => normalizeText(item.code).toLowerCase() === normalizeText(code).toLowerCase());
        if (!found) {
          setStatus(modulesStatus, "Khong tim thay module de nap vao form", false);
          return;
        }
        fillModuleForm(found);
        setStatus(modulesStatus, "Da nap module vao form de chinh sua", true);
        moduleCode?.focus();
        return;
      }

      if (action === "delete" && !window.confirm("Xoa module game ngoai nay?")) {
        return;
      }

      button.disabled = true;
      try {
        const data = await deleteModule(code);
        const ok = !!(data && data.success);
        setStatus(modulesStatus, ok ? "Da xoa module game ngoai" : String(data?.error || "Khong xoa duoc module"), ok);
        if (ok) {
          await loadModules();
          if (normalizeText(moduleCode?.value).toLowerCase() === normalizeText(code).toLowerCase()) {
            resetModuleForm();
          }
        } else {
          button.disabled = false;
        }
      } catch (error) {
        setStatus(modulesStatus, String(error?.message || error || "Khong xoa duoc module"), false);
        button.disabled = false;
      }
    });

    modulesPreviewTableBody?.addEventListener("click", (event) => {
      const button = event.target.closest("button[data-preview-action='load'][data-preview-code]");
      if (!button) {
        return;
      }
      const code = normalizeText(button.getAttribute("data-preview-code")).toLowerCase();
      if (!code) {
        return;
      }
      const found = state.previewModules.find((item) => normalizeText(item.code).toLowerCase() === code);
      if (!found) {
        setStatus(modulesStatus, "Khong tim thay module preview de nap vao form", false);
        return;
      }
      fillModuleForm(found);
      setStatus(modulesStatus, "Da nap module preview vao form", true);
      moduleCode?.focus();
    });

    noticesForm?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const content = normalizeText(noticesContent?.value);
      if (!content) {
        setStatus(noticesStatus, "Noi dung la bat buoc", false);
        return;
      }
      try {
        const data = await createNotice(content);
        const ok = !!(data && data.success);
        setStatus(noticesStatus, ok ? "Da tao thong bao" : String(data?.error || "Khong the tao thong bao"), ok);
        if (ok) {
          if (noticesContent) {
            noticesContent.value = "";
          }
          await loadNotices();
        }
      } catch (error) {
        setStatus(noticesStatus, String(error?.message || error || "Khong the tao thong bao"), false);
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
        setStatus(noticesStatus, ok ? "Da xoa thong bao" : String(data?.error || "Khong the xoa thong bao"), ok);
        if (ok) {
          await loadNotices();
        } else {
          button.disabled = false;
        }
      } catch (error) {
        setStatus(noticesStatus, String(error?.message || error || "Khong the xoa thong bao"), false);
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

    resetModuleForm();
    renderModulePreview();

    void Promise.all([
      loadUsers(true),
      loadModules(),
      loadNotices(),
      loadLogs(true)
    ]);
  });
})();

