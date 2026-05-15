(() => {
  const searchStorageKey = "leadflow-search-response";
  const selectedCustomersKey = "leadflow-selected-customers";
  const outreachImportFlagKey = "leadflow-outreach-import-pending";

  const searchForm = document.querySelector("#search-form");
  const targetDescription = document.querySelector("#target-description");
  const industryPreset = document.querySelector("#industry-preset");
  const industryCustomGroup = document.querySelector("#industry-custom-group");
  const industryCustom = document.querySelector("#industry-custom");
  const marketPreset = document.querySelector("#market-preset");
  const keywordsPreset = document.querySelector("#keywords-preset");
  const keywordsCustomGroup = document.querySelector("#keywords-custom-group");
  const keywordsCustom = document.querySelector("#keywords-custom");
  const searchDepth = document.querySelector("#search-depth");
  const requestedLimitInput = document.querySelector("#requested-limit");

  const searchLogs = document.querySelector("#search-logs");
  const searchSummary = document.querySelector("#search-summary");
  const searchStatusChip = document.querySelector("#search-status-chip");
  const resultsBody = document.querySelector("#results-body");
  const exportButton = document.querySelector("#export-results");
  const pushButton = document.querySelector("#push-to-outreach");
  const selectedCount = document.querySelector("#selected-count");
  const selectAll = document.querySelector("#select-all");
  const searchResultCount = document.querySelector("#search-result-count");

  const statTotal = document.querySelector("#stat-total");
  const statEmail = document.querySelector("#stat-email");
  const statMatch = document.querySelector("#stat-match");
  const statMarket = document.querySelector("#stat-market");

  const searchState = {
    customers: [],
    selectedIds: new Set(),
    activeController: null,
    requestedLimit: 50
  };

  init().catch((error) => {
    console.error("Customer search bootstrap failed:", error);
  });

  async function init() {
    await hydrateSettings();
    hydrateSearchResult();
    bindFormBehavior();

    searchForm?.addEventListener("submit", handleSearchSubmit);
    exportButton?.addEventListener("click", exportSelectedCustomers);
    pushButton?.addEventListener("click", pushSelectedCustomers);
    resultsBody?.addEventListener("change", handleResultSelection);
    selectAll?.addEventListener("change", handleSelectAll);
  }

  async function hydrateSettings() {
    try {
      const response = await fetch("/api/settings", { cache: "no-store" });
      if (!response.ok) {
        return;
      }
      const settings = await response.json();
      searchState.requestedLimit = Number(settings.search?.resultsPerPage || 50);
      if (requestedLimitInput) {
        requestedLimitInput.value = String(searchState.requestedLimit);
      }
    } catch (error) {
      console.error("Failed to load search settings:", error);
    }
  }

  function bindFormBehavior() {
    toggleCustomField(industryPreset, industryCustomGroup, industryCustom);
    if (keywordsPreset || keywordsCustomGroup || keywordsCustom) {
      toggleCustomField(keywordsPreset, keywordsCustomGroup, keywordsCustom);
    }

    industryPreset?.addEventListener("change", () => {
      toggleCustomField(industryPreset, industryCustomGroup, industryCustom);
    });

    keywordsPreset?.addEventListener("change", () => {
      toggleCustomField(keywordsPreset, keywordsCustomGroup, keywordsCustom);
    });
  }

  function toggleCustomField(select, group, input) {
    if (!select && !group && !input) {
      return;
    }

    const isCustom = select?.value === "custom";
    group?.classList.toggle("is-hidden", !isCustom);
    if (input) {
      input.required = Boolean(isCustom);
      if (!isCustom) {
        input.value = "";
      }
    }
  }

  async function handleSearchSubmit(event) {
    event.preventDefault();

    if (!searchForm) {
      return;
    }

    const submitButton = document.querySelector("#search-submit");
    if (!submitButton) {
      return;
    }

    if (searchState.activeController) {
      searchState.activeController.abort();
    }

    const controller = new AbortController();
    searchState.activeController = controller;
    const timeoutId = window.setTimeout(() => controller.abort("timeout"), 180000);

    resetSearchViewForPending();
    setSearchStatus("搜索中...", "running");
    submitButton.disabled = true;
    submitButton.textContent = "搜索中...";

    renderLogs([
      { time: "进行中", message: "正在根据左侧配置组装真实搜索请求..." },
      { time: "进行中", message: "随后会过滤站点、提取邮箱并整理匹配客户..." }
    ]);

    try {
      const payload = buildSearchPayload();
      const response = await fetch("/api/customers/search", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload),
        signal: controller.signal
      });

      if (!response.ok) {
        throw new Error("Search API returned a non-OK response.");
      }

      const data = await response.json();
      localStorage.setItem(searchStorageKey, JSON.stringify(data));
      applySearchResponse(data);
      setSearchStatus(data.customers?.length ? "已完成" : "无结果", data.customers?.length ? "complete" : "error");
    } catch (error) {
      console.error("Customer search failed:", error);
      const message = error?.name === "AbortError"
        ? "搜索耗时较长，请稍后刷新页面查看结果。"
        : "客户搜索失败，请检查服务状态后重试。";
      clearSearchResults();
      renderLogs([{ time: "失败", message }]);
      searchSummary.textContent = message;
      setSearchStatus("搜索失败", "error");
    } finally {
      window.clearTimeout(timeoutId);
      if (searchState.activeController === controller) {
        searchState.activeController = null;
      }
      submitButton.disabled = false;
      submitButton.textContent = "开始AI搜索";
    }
  }

  function buildSearchPayload() {
    const companySize = String(new FormData(searchForm).get("companySize") || "").trim();
    const industry = industryPreset?.value === "custom"
      ? String(industryCustom?.value || "").trim()
      : String(industryPreset?.value || "").trim();
    const keywords = keywordsPreset
      ? (keywordsPreset?.value === "custom"
        ? String(keywordsCustom?.value || "").trim()
        : String(keywordsPreset?.value || "").trim())
      : "";
    const market = String(marketPreset?.value || "").trim();
    const description = String(targetDescription?.value || "").trim();
    const requestedLimit = Number(requestedLimitInput?.value || searchState.requestedLimit || 50);
    const depth = String(searchDepth?.value || "standard");

    let resolvedIndustry = industry;
    let resolvedKeywords = keywords;
    let resolvedMarket = market;

    if (description) {
      resolvedKeywords = description;
      if (description.includes("中国")) {
        resolvedMarket = "中国";
      } else if (description.includes("美国")) {
        resolvedMarket = "美国";
      } else if (description.includes("德国")) {
        resolvedMarket = "德国";
      }

      if (description.includes("机床")) {
        resolvedIndustry = "机床制造";
      } else if (description.includes("自动化")) {
        resolvedIndustry = "工业自动化";
      } else if (description.includes("电子")) {
        resolvedIndustry = "电子制造";
      }
    }

    searchState.requestedLimit = requestedLimit;

    return {
      industry: resolvedIndustry,
      market: resolvedMarket,
      keywords: resolvedKeywords,
      companySize,
      requestedLimit
    };
  }

  function hydrateSearchResult() {
    const cached = localStorage.getItem(searchStorageKey);
    if (!cached) {
      setSearchStatus("等待开始", "pending");
      if (searchResultCount) {
        searchResultCount.textContent = "0";
      }
      return;
    }

    try {
      const data = JSON.parse(cached);
      applySearchResponse(data);
      setSearchStatus(data.customers?.length ? "已完成" : "无结果", data.customers?.length ? "complete" : "error");
    } catch (error) {
      localStorage.removeItem(searchStorageKey);
      renderLogs([]);
      setSearchStatus("等待开始", "pending");
      if (searchResultCount) {
        searchResultCount.textContent = "0";
      }
    }
  }

  function applySearchResponse(data) {
    searchState.customers = Array.isArray(data.customers) ? data.customers : [];
    searchState.selectedIds = new Set(searchState.customers.map((customer) => customer.id));

    searchSummary.textContent = data.summary || "已完成搜索。";
    renderLogs(compactLogs(data.logs || [], data.summary || "已完成搜索。"));
    renderStats(data.stats || {});
    renderResults(searchState.customers);
    refreshSelectionState();
    if (searchResultCount) {
      searchResultCount.textContent = String(searchState.customers.length);
    }
  }

  function resetSearchViewForPending() {
    searchState.customers = [];
    searchState.selectedIds = new Set();
    renderStats({
      totalCustomers: 0,
      emailCount: 0,
      highMatchCount: 0,
      marketCoverage: 0
    });
    renderResults([]);
    refreshSelectionState();
    searchSummary.textContent = "正在抓取客户线索，请稍候...";
    if (searchResultCount) {
      searchResultCount.textContent = "0";
    }
  }

  function clearSearchResults() {
    searchState.customers = [];
    searchState.selectedIds = new Set();
    localStorage.removeItem(searchStorageKey);
    renderStats({
      totalCustomers: 0,
      emailCount: 0,
      highMatchCount: 0,
      marketCoverage: 0
    });
    renderResults([]);
    refreshSelectionState();
    if (searchResultCount) {
      searchResultCount.textContent = "0";
    }
  }

  function renderLogs(logs) {
    if (!Array.isArray(logs) || logs.length === 0) {
      searchLogs.innerHTML = `
        <li class="log-item">
          <span class="log-time">--:--:--</span>
          <span class="log-text">当前没有新的运行日志。</span>
        </li>
      `;
      return;
    }

    searchLogs.innerHTML = logs
      .map(
        (log) => `
          <li class="log-item">
            <span class="log-time">${escapeHtml(log.time || "--:--:--")}</span>
            <span class="log-text">${escapeHtml(log.message || "")}</span>
          </li>
        `
      )
      .join("");
  }

  function compactLogs(logs, summaryText) {
    if (!Array.isArray(logs) || logs.length === 0) {
      return [];
    }

    const strategyLog = logs.find((log) => String(log.message || "").includes("Search strategy"));
    const finalLog = logs[logs.length - 1];
    const compacted = [];

    if (strategyLog) {
      compacted.push(strategyLog);
    }
    if (finalLog && finalLog !== strategyLog) {
      compacted.push({
        time: finalLog.time || "--:--:--",
        message: summaryText
      });
    }

    return compacted.length > 0 ? compacted.slice(0, 3) : [logs[0]];
  }

  function renderStats(stats) {
    statTotal.textContent = searchState.requestedLimit || stats.totalCustomers || 0;
    statEmail.textContent = stats.totalCustomers || 0;
    statMatch.textContent = stats.highMatchCount || 0;
    statMarket.textContent = stats.totalCustomers ? "00:32" : "00:00";
  }

  function renderResults(customers) {
    if (!Array.isArray(customers) || customers.length === 0) {
      resultsBody.innerHTML = `
        <tr>
          <td colspan="6">
            <div class="empty-state">
              <span class="empty-icon" aria-hidden="true">
                <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="11" cy="11" r="6"></circle>
                  <path d="m20 20-4.2-4.2"></path>
                </svg>
              </span>
              <h2>开始搜索客户</h2>
              <p class="empty-copy">在左侧配置搜索条件，点击“开始AI搜索”按钮后，客户结果会在下方表格中出现。</p>
            </div>
          </td>
        </tr>
      `;
      return;
    }

    resultsBody.innerHTML = customers
      .map(
        (customer) => `
          <tr>
            <td>
              <input type="checkbox" data-lead-id="${escapeHtml(customer.id)}" ${searchState.selectedIds.has(customer.id) ? "checked" : ""} />
            </td>
            <td>
              <div class="company-cell">
                <strong>${escapeHtml(customer.companyName)}</strong>
                <a class="company-link" href="${escapeHtml(customer.website)}" target="_blank" rel="noreferrer">${escapeHtml(customer.website)}</a>
              </div>
            </td>
            <td>
              ${escapeHtml(displayValue(customer.contactName, "Business Contact"))}<br />
              <span class="table-note">${escapeHtml(displayValue(customer.email, "未找到公开邮箱"))}</span>
            </td>
            <td>${escapeHtml(customer.channel || "官网")}</td>
            <td><span class="table-note">${escapeHtml(customer.fitNote || "candidate website")}</span></td>
            <td><span class="table-note">查看</span></td>
          </tr>
        `
      )
      .join("");
  }

  function refreshSelectionState() {
    const selectedCustomers = getSelectedCustomers();
    if (selectedCount) {
      selectedCount.textContent = selectedCustomers.length;
    }
    if (exportButton) {
      exportButton.disabled = selectedCustomers.length === 0;
    }
    if (pushButton) {
      pushButton.disabled = selectedCustomers.length === 0;
    }

    if (selectAll) {
      selectAll.checked = selectedCustomers.length > 0 && selectedCustomers.length === searchState.customers.length;
    }
  }

  function exportSelectedCustomers() {
    const selectedCustomers = getSelectedCustomers();
    if (selectedCustomers.length === 0) {
      return;
    }

    const lines = [["公司名称", "联系方式", "社交媒体", "匹配度", "官网"].join(",")];
    selectedCustomers.forEach((customer) => {
      lines.push([
        csvEscape(customer.companyName),
        csvEscape(`${displayValue(customer.contactName, "Business Contact")} / ${displayValue(customer.email, "未找到公开邮箱")}`),
        csvEscape(customer.channel || "官网"),
        csvEscape(customer.fitNote || "candidate website"),
        csvEscape(customer.website)
      ].join(","));
    });

    const blob = new Blob(["\uFEFF" + lines.join("\n")], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "customer-search-results.csv";
    link.click();
    URL.revokeObjectURL(url);
  }

  function pushSelectedCustomers() {
    const selectedCustomers = getSelectedCustomers();
    localStorage.setItem(selectedCustomersKey, JSON.stringify(selectedCustomers));
    localStorage.setItem(outreachImportFlagKey, "1");
    window.location.href = "/ai-outreach?import=1";
  }

  function handleResultSelection(event) {
    const target = event.target;
    if (!(target instanceof HTMLInputElement) || !target.matches("[data-lead-id]")) {
      return;
    }

    if (target.checked) {
      searchState.selectedIds.add(target.dataset.leadId);
    } else {
      searchState.selectedIds.delete(target.dataset.leadId);
    }
    refreshSelectionState();
  }

  function handleSelectAll() {
    if (selectAll.checked) {
      searchState.selectedIds = new Set(searchState.customers.map((customer) => customer.id));
    } else {
      searchState.selectedIds = new Set();
    }
    renderResults(searchState.customers);
    refreshSelectionState();
  }

  function getSelectedCustomers() {
    return searchState.customers.filter((customer) => searchState.selectedIds.has(customer.id));
  }

  function setSearchStatus(label, type) {
    if (!searchStatusChip) {
      return;
    }
    searchStatusChip.textContent = label;
    searchStatusChip.className = `status-chip ${type}`;
  }

  function csvEscape(value) {
    const content = String(value ?? "");
    return `"${content.replaceAll("\"", "\"\"")}"`;
  }

  function displayValue(value, fallbackText) {
    const text = String(value ?? "").trim();
    return text || fallbackText;
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll("\"", "&quot;")
      .replaceAll("'", "&#39;");
  }
})();
