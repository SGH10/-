(() => {
  const searchStorageKey = "leadflow-search-response";
  const selectedCustomersKey = "leadflow-selected-customers";

  const searchForm = document.querySelector("#search-form");
  const searchLogs = document.querySelector("#search-logs");
  const searchSummary = document.querySelector("#search-summary");
  const searchStatusChip = document.querySelector("#search-status-chip");
  const resultsBody = document.querySelector("#results-body");
  const exportButton = document.querySelector("#export-results");
  const pushButton = document.querySelector("#push-to-outreach");
  const selectedCount = document.querySelector("#selected-count");
  const selectAll = document.querySelector("#select-all");

  const statTotal = document.querySelector("#stat-total");
  const statEmail = document.querySelector("#stat-email");
  const statMatch = document.querySelector("#stat-match");
  const statMarket = document.querySelector("#stat-market");

  const searchState = {
    customers: [],
    selectedIds: new Set(),
    activeController: null,
    activeSearchStartedAt: 0
  };

  hydrateSearchResult();
  searchForm?.addEventListener("submit", handleSearchSubmit);

  exportButton?.addEventListener("click", () => {
    const selectedCustomers = getSelectedCustomers();
    if (selectedCustomers.length === 0) {
      return;
    }

    const lines = [["公司名称", "国家", "联系人", "邮箱", "官网", "来源", "匹配说明"].join(",")];
    selectedCustomers.forEach((customer) => {
      lines.push([
        csvEscape(customer.companyName),
        csvEscape(displayValue(customer.country, "待确认")),
        csvEscape(displayValue(customer.contactName, "待人工确认")),
        csvEscape(displayValue(customer.email, "未找到公开邮箱")),
        csvEscape(customer.website),
        csvEscape(customer.channel),
        csvEscape(customer.fitNote)
      ].join(","));
    });

    const blob = new Blob(["\uFEFF" + lines.join("\n")], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "customer-search-results.csv";
    link.click();
    URL.revokeObjectURL(url);
  });

  pushButton?.addEventListener("click", () => {
    const selectedCustomers = getSelectedCustomers();
    localStorage.setItem(selectedCustomersKey, JSON.stringify(selectedCustomers));
    window.location.href = "/ai-outreach";
  });

  resultsBody?.addEventListener("change", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) {
      return;
    }

    if (target.matches("[data-lead-id]")) {
      if (target.checked) {
        searchState.selectedIds.add(target.dataset.leadId);
      } else {
        searchState.selectedIds.delete(target.dataset.leadId);
      }
      refreshSelectionState();
    }
  });

  selectAll?.addEventListener("change", () => {
    if (selectAll.checked) {
      searchState.selectedIds = new Set(searchState.customers.map((customer) => customer.id));
    } else {
      searchState.selectedIds = new Set();
    }
    renderResults(searchState.customers);
    refreshSelectionState();
  });

  async function handleSearchSubmit(event) {
    event.preventDefault();

    const submitButton = document.querySelector("#search-submit");
    if (!submitButton) {
      return;
    }

    if (searchState.activeController) {
      searchState.activeController.abort();
    }

    const controller = new AbortController();
    searchState.activeController = controller;
    searchState.activeSearchStartedAt = Date.now();
    const timeoutId = window.setTimeout(() => controller.abort("timeout"), 180000);

    resetSearchViewForPending();
    setSearchStatus("搜索中...", "running");
    submitButton.disabled = true;
    submitButton.textContent = "搜索中...";

    renderLogs([
      { time: "进行中", message: "正在连接公开搜索入口并提取候选官网..." },
      { time: "进行中", message: "随后会检查官网和联系页，尝试提取公开邮箱与业务信息..." }
    ]);

    try {
      const formData = new FormData(searchForm);
      const payload = Object.fromEntries(formData.entries());
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

      if (error?.name === "AbortError") {
        const recovered = await waitForLastSearchResult();
        if (recovered) {
          localStorage.setItem(searchStorageKey, JSON.stringify(recovered));
          applySearchResponse(recovered);
          setSearchStatus(recovered.customers?.length ? "已完成" : "无结果", recovered.customers?.length ? "complete" : "error");
        } else {
          const message = "搜索耗时较长，前端等待超时，但后端可能仍在后台继续执行。请稍后刷新页面查看结果。";
          renderLogs([{ time: "超时", message }]);
          searchSummary.textContent = message;
          setSearchStatus("搜索超时", "error");
        }
      } else {
        const message = "客户搜索没有成功，请检查服务是否正常启动，或稍后再试。";
        clearSearchResults();
        renderLogs([{ time: "失败", message }]);
        searchSummary.textContent = message;
        setSearchStatus("搜索失败", "error");
      }
    } finally {
      window.clearTimeout(timeoutId);
      if (searchState.activeController === controller) {
        searchState.activeController = null;
      }
      submitButton.disabled = false;
      submitButton.textContent = "开始 AI 搜索";
    }
  }

  async function waitForLastSearchResult() {
    const deadline = Date.now() + 90000;

    while (Date.now() < deadline) {
      await delay(3000);

      try {
        const response = await fetch("/api/customers/last-search", {
          cache: "no-store"
        });

        if (response.status === 204 || !response.ok) {
          continue;
        }

        const data = await response.json();
        if (!data || !Array.isArray(data.customers)) {
          continue;
        }

        return data;
      } catch (pollError) {
        console.error("Polling last search failed:", pollError);
      }
    }

    return null;
  }

  function hydrateSearchResult() {
    const cached = localStorage.getItem(searchStorageKey);
    if (!cached) {
      setSearchStatus("等待开始", "pending");
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

    const strategyLog = logs.find((log) => {
      const message = String(log.message || "");
      return message.includes("Search strategy") || message.includes("搜索策略");
    });
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
    statTotal.textContent = stats.totalCustomers || 0;
    statEmail.textContent = stats.emailCount || 0;
    statMatch.textContent = stats.highMatchCount || 0;
    statMarket.textContent = stats.marketCoverage || 0;
  }

  function renderResults(customers) {
    if (!Array.isArray(customers) || customers.length === 0) {
      resultsBody.innerHTML = `
        <tr>
          <td colspan="7">
            <div class="results-empty">
              这次没有搜到符合条件的客户。可能是搜索入口超时、官网不可访问，或关键词与市场条件过严。
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
            <td>${escapeHtml(displayValue(customer.country, "待确认"))}</td>
            <td>${escapeHtml(displayValue(customer.contactName, "待人工确认"))}</td>
            <td>${escapeHtml(displayValue(customer.email, "未找到公开邮箱"))}</td>
            <td>${escapeHtml(customer.channel)}</td>
            <td><span class="table-note">${escapeHtml(customer.fitNote)}</span></td>
          </tr>
        `
      )
      .join("");
  }

  function refreshSelectionState() {
    const selectedCustomers = getSelectedCustomers();
    selectedCount.textContent = selectedCustomers.length;
    exportButton.disabled = selectedCustomers.length === 0;
    pushButton.disabled = selectedCustomers.length === 0;
    if (selectAll) {
      selectAll.checked = selectedCustomers.length > 0 && selectedCustomers.length === searchState.customers.length;
    }
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

  function delay(ms) {
    return new Promise((resolve) => window.setTimeout(resolve, ms));
  }
})();
