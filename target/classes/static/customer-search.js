(() => {
  const searchStorageKey = "leadflow-search-response";
  const selectedCustomersKey = "leadflow-selected-customers";
  const outreachImportFlagKey = "leadflow-outreach-import-pending";
  const searchHistoryKey = "leadflow-search-history";
  const maxHistoryItems = 20;

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
  const searchTime = document.querySelector("#search-time");
  const searchStatusChip = document.querySelector("#search-status-chip");
  const resultsBody = document.querySelector("#results-body");
  const exportButton = document.querySelector("#export-results");
  const pushButton = document.querySelector("#push-to-outreach");
  const selectedCount = document.querySelector("#selected-count");
  const selectAll = document.querySelector("#select-all");
  const searchResultCount = document.querySelector("#search-result-count");

  const historyToggle = document.querySelector("#history-toggle");
  const historyPanel = document.querySelector("#history-panel");
  const historyList = document.querySelector("#history-list");
  const historyEmpty = document.querySelector("#history-empty");
  const historyClear = document.querySelector("#history-clear");

  const statTotal = document.querySelector("#stat-total");
  const statEmail = document.querySelector("#stat-email");
  const statMatch = document.querySelector("#stat-match");
  const statMarket = document.querySelector("#stat-market");

  const searchState = {
    customers: [],
    selectedIds: new Set(),
    activeController: null,
    requestedLimit: 50,
    startTime: 0,
    timerInterval: null
  };

  const progressTrack = document.querySelector(".progress-track");

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

    bindHistoryPanel();
    renderSearchHistory();
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
    const timeoutId = window.setTimeout(() => controller.abort("timeout"), 300000);

    // Build payload first so searchState.requestedLimit is updated before rendering stats
    const payload = buildSearchPayload();

    resetSearchViewForPending();
    setSearchStatus("搜索中...", "running");
    submitButton.disabled = true;
    submitButton.textContent = "搜索中...";

    // Start real timer and indeterminate progress bar
    searchState.startTime = Date.now();
    clearSearchTimer();
    searchState.timerInterval = window.setInterval(updateRunningTimer, 100);
    setProgressBar(null, true);

    renderLogs([
      { time: "进行中", message: "正在根据左侧配置组装真实搜索请求..." },
      { time: "进行中", message: "随后会过滤站点、提取邮箱并整理匹配客户..." }
    ]);

    try {
      const response = await fetch("/api/customers/search", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload),
        signal: controller.signal
      });

      if (!response.ok) {
        const errorText = await response.text().catch(() => "");
        throw new Error(errorText || "Search API returned a non-OK response.");
      }

      const data = await response.json();
      data.timestamp = Date.now();
      applySearchResponse(data);
      cacheSearchResponse(data);
      saveSearchHistory(data, payload);
      setSearchStatus(data.customers?.length ? "已完成" : "无结果", data.customers?.length ? "complete" : "error");
    } catch (error) {
      console.error("Customer search failed:", error);
      const message = error?.name === "AbortError"
        ? "搜索耗时较长，请稍后刷新页面查看结果。"
        : "客户搜索失败，请检查服务状态后重试。";
      clearSearchResults();
      renderLogs([{ time: "失败", message }]);
      searchSummary.textContent = message;
      if (searchTime) searchTime.textContent = "";
      setSearchStatus("搜索失败", "error");
    } finally {
      window.clearTimeout(timeoutId);
      clearSearchTimer();
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
      resolvedKeywords = resolvedKeywords || description;
      if (description.includes("中国")) {
        resolvedMarket = "中国";
      } else if (description.includes("美国")) {
        resolvedMarket = "美国";
      } else if (description.includes("德国")) {
        resolvedMarket = "德国";
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

  function cacheSearchResponse(data) {
    try {
      localStorage.setItem(searchStorageKey, JSON.stringify(data));
    } catch (error) {
      console.error("Failed to cache search response:", error);
    }
  }

  function hydrateSearchResult() {
    const cached = localStorage.getItem(searchStorageKey);
    if (!cached) {
      setSearchStatus("等待开始", "pending");
      if (searchTime) searchTime.textContent = "";
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
      if (searchTime) searchTime.textContent = "";
      if (searchResultCount) {
        searchResultCount.textContent = "0";
      }
    }
  }

  function applySearchResponse(data) {
    searchState.customers = Array.isArray(data.customers) ? data.customers : [];
    searchState.selectedIds = new Set(searchState.customers.map((customer) => customer.id));

    searchSummary.textContent = data.summary || "已完成搜索。";
    if (searchTime) {
      if (data.timestamp) {
        const locale = typeof window.leadflowLocale?.locale === "string" ? window.leadflowLocale.locale : "zh-CN";
        const label = locale === "en" ? "Search time: " : "搜索时间：";
        searchTime.textContent = label + formatFullTime(new Date(data.timestamp));
      } else {
        searchTime.textContent = "";
      }
    }
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
    searchState.startTime = Date.now();
    clearSearchTimer();
    setProgressBar(null, true);
    renderStats({
      totalCustomers: 0,
      emailCount: 0,
      highMatchCount: 0,
      marketCoverage: 0
    });
    renderResults([]);
    refreshSelectionState();
    searchSummary.textContent = "正在抓取客户线索，请稍候...";
    if (searchTime) searchTime.textContent = "";
    if (searchResultCount) {
      searchResultCount.textContent = "0";
    }
  }

  function clearSearchResults() {
    searchState.customers = [];
    searchState.selectedIds = new Set();
    localStorage.removeItem(searchStorageKey);
    clearSearchTimer();
    searchState.startTime = 0;
    setProgressBar(0, false);
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
    const target = searchState.requestedLimit || 1;
    const found = stats.totalCustomers || 0;

    statTotal.textContent = target;
    statEmail.textContent = found;
    statMatch.textContent = stats.highMatchCount || 0;

    // Calculate actual elapsed time
    let elapsedMs = 0;
    if (searchState.startTime > 0) {
      elapsedMs = Date.now() - searchState.startTime;
    }
    statMarket.textContent = formatElapsedMs(elapsedMs);

    // Update progress bar with real percentage
    const progressPercent = Math.min(100, Math.round((found / target) * 100));
    setProgressBar(progressPercent, false);
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

  function updateRunningTimer() {
    const elapsed = Date.now() - searchState.startTime;
    if (statMarket) {
      statMarket.textContent = formatElapsedMs(elapsed);
    }
  }

  function clearSearchTimer() {
    if (searchState.timerInterval) {
      window.clearInterval(searchState.timerInterval);
      searchState.timerInterval = null;
    }
  }

  function setProgressBar(percent, indeterminate) {
    if (!progressTrack) {
      return;
    }
    if (indeterminate) {
      progressTrack.classList.add("is-indeterminate");
      progressTrack.style.setProperty("--progress-width", "30%");
    } else {
      progressTrack.classList.remove("is-indeterminate");
      if (percent !== null) {
        progressTrack.style.setProperty("--progress-width", percent + "%");
      }
    }
  }

  function formatElapsedMs(ms) {
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return String(minutes).padStart(2, "0") + ":" + String(seconds).padStart(2, "0");
  }

  /* ── Search History ── */

  function bindHistoryPanel() {
    if (!historyToggle || !historyPanel) return;

    historyToggle.addEventListener("click", (event) => {
      event.stopPropagation();
      const isHidden = historyPanel.classList.contains("is-hidden");
      if (isHidden) {
        renderSearchHistory();
        historyPanel.classList.remove("is-hidden");
        historyToggle.setAttribute("aria-expanded", "true");
      } else {
        historyPanel.classList.add("is-hidden");
        historyToggle.setAttribute("aria-expanded", "false");
      }
    });

    historyClear?.addEventListener("click", (event) => {
      event.stopPropagation();
      localStorage.removeItem(searchHistoryKey);
      renderSearchHistory();
    });

    historyPanel.addEventListener("click", (event) => {
      event.stopPropagation();
    });

    document.addEventListener("click", () => {
      if (!historyPanel.classList.contains("is-hidden")) {
        historyPanel.classList.add("is-hidden");
        historyToggle?.setAttribute("aria-expanded", "false");
      }
    });
  }

  function saveSearchHistory(data, payload) {
    try {
      const raw = localStorage.getItem(searchHistoryKey);
      let list = raw ? JSON.parse(raw) : [];
      if (!Array.isArray(list)) list = [];

      const item = {
        id: String(Date.now()) + "-" + Math.random().toString(36).slice(2, 7),
        timestamp: Date.now(),
        summary: buildHistorySummary(payload),
        config: {
          industry: payload.industry,
          market: payload.market,
          keywords: payload.keywords,
          companySize: payload.companySize,
          requestedLimit: payload.requestedLimit
        },
        resultCount: data.customers?.length || 0,
        customers: data.customers || []
      };

      list.unshift(item);
      if (list.length > maxHistoryItems) {
        list = list.slice(0, maxHistoryItems);
      }

      localStorage.setItem(searchHistoryKey, JSON.stringify(list));
    } catch (error) {
      console.error("Failed to save search history:", error);
    }
  }

  function buildHistorySummary(payload) {
    const parts = [];
    if (payload.industry) parts.push(payload.industry);
    if (payload.market && payload.market !== "全部") parts.push(payload.market);
    if (parts.length === 0) return payload.keywords || "AI搜索";
    return parts.join(" · ");
  }

  function renderSearchHistory() {
    if (!historyList || !historyEmpty) return;

    let list = [];
    try {
      const raw = localStorage.getItem(searchHistoryKey);
      if (raw) list = JSON.parse(raw);
    } catch {
      list = [];
    }
    if (!Array.isArray(list)) list = [];

    const locale = typeof window.leadflowLocale?.locale === "string"
      ? window.leadflowLocale.locale
      : "zh-CN";
    const countLabel = locale === "en"
      ? (count) => `${count} leads`
      : (count) => `${count} 位客户`;

    if (list.length === 0) {
      historyList.innerHTML = "";
      historyList.classList.add("is-hidden");
      historyEmpty.classList.remove("is-hidden");
      return;
    }

    historyEmpty.classList.add("is-hidden");
    historyList.classList.remove("is-hidden");

    historyList.innerHTML = list
      .map((item) => {
        const date = new Date(item.timestamp);
        const timeText = formatFullTime(date);
        const countText = countLabel(item.resultCount || 0);
        const tagsHtml = buildHistoryTagsHtml(item.config);
        return `
          <li class="history-item" data-history-id="${escapeHtml(item.id)}">
            <div class="history-item-row">
              <span class="history-time">${escapeHtml(timeText)}</span>
              <button type="button" class="history-restore" data-history-id="${escapeHtml(item.id)}" data-i18n="search.history.restore">恢复</button>
            </div>
            <div class="history-item-row">
              <div class="history-item-tags">${tagsHtml}</div>
              <span class="history-tag orange">${escapeHtml(countText)}</span>
            </div>
          </li>
        `;
      })
      .join("");

    historyList.querySelectorAll(".history-item").forEach((el) => {
      el.addEventListener("click", (event) => {
        const button = event.target.closest(".history-restore");
        if (!button) {
          const id = el.dataset.historyId;
          restoreSearchHistory(id);
        }
      });
    });

    historyList.querySelectorAll(".history-restore").forEach((btn) => {
      btn.addEventListener("click", (event) => {
        event.stopPropagation();
        restoreSearchHistory(btn.dataset.historyId);
      });
    });

    if (typeof window.applyPageTranslations === "function") {
      window.applyPageTranslations(locale);
    }
  }

  function buildHistoryTagsHtml(config) {
    const tags = [];
    if (config.industry) tags.push(config.industry);
    if (config.market && config.market !== "全部") tags.push(config.market);
    if (config.companySize) tags.push(config.companySize);
    if (!tags.length && config.keywords) tags.push(config.keywords.slice(0, 20));
    return tags
      .map((t) => `<span class="history-tag">${escapeHtml(t)}</span>`)
      .join("");
  }

  function formatHistoryTime(date) {
    const now = new Date();
    const isToday =
      date.getFullYear() === now.getFullYear() &&
      date.getMonth() === now.getMonth() &&
      date.getDate() === now.getDate();

    const pad = (n) => String(n).padStart(2, "0");
    const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`;

    if (isToday) return time;

    const month = pad(date.getMonth() + 1);
    const day = pad(date.getDate());
    return `${month}-${day} ${time}`;
  }

  function formatFullTime(date) {
    const pad = (n) => String(n).padStart(2, "0");
    const year = date.getFullYear();
    const month = pad(date.getMonth() + 1);
    const day = pad(date.getDate());
    const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    return `${year}-${month}-${day} ${time}`;
  }

  function restoreSearchHistory(id) {
    let list = [];
    try {
      const raw = localStorage.getItem(searchHistoryKey);
      if (raw) list = JSON.parse(raw);
    } catch {
      list = [];
    }
    if (!Array.isArray(list)) return;

    const item = list.find((h) => h.id === id);
    if (!item) return;

    const data = {
      summary: item.summary,
      timestamp: item.timestamp,
      stats: {
        totalCustomers: item.resultCount,
        emailCount: item.resultCount,
        highMatchCount: item.resultCount,
        marketCoverage: 0
      },
      logs: [
        {
          time: formatHistoryTime(new Date(item.timestamp)),
          message: `从历史记录恢复：${item.summary}`
        }
      ],
      customers: item.customers || []
    };

    localStorage.setItem(searchStorageKey, JSON.stringify(data));
    applySearchResponse(data);
    const locale = typeof window.leadflowLocale?.locale === "string" ? window.leadflowLocale.locale : "zh-CN";
    const restoredLabel = locale === "en" ? "Restored" : "已恢复";
    const restoredSummary = locale === "en"
      ? `Restored from history: ${item.summary} (${item.resultCount} leads)`
      : `从历史记录恢复：${item.summary}（${item.resultCount} 位客户）`;
    setSearchStatus(restoredLabel, "complete");
    searchSummary.textContent = restoredSummary;

    if (historyPanel && !historyPanel.classList.contains("is-hidden")) {
      historyPanel.classList.add("is-hidden");
      historyToggle?.setAttribute("aria-expanded", "false");
    }
  }
})();
