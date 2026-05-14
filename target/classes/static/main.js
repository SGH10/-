(() => {
  const normalizedPath = window.location.pathname.replace(/\/+$/, "") || "/";
  const searchStorageKey = "leadflow-search-response";

  document.querySelectorAll("[data-nav]").forEach((link) => {
    if (link.getAttribute("data-nav") === normalizedPath) {
      link.classList.add("is-active");
    }
  });

  document.querySelectorAll("[data-settings-path]").forEach((link) => {
    const isActive = link.getAttribute("data-settings-path") === normalizedPath;
    link.classList.toggle("is-active", isActive);
    if (isActive) {
      link.setAttribute("aria-current", "page");
    } else {
      link.removeAttribute("aria-current");
    }
  });

  const statusText = document.querySelector("#status-text");
  if (statusText) {
    statusText.textContent = "界面已完成初始化，可以直接进入客户搜索或开发信系统继续体验完整流程。";
  }

  if (normalizedPath === "/") {
    loadEntrySearchLogs();
  }

  async function loadEntrySearchLogs() {
    const summaryElement = document.querySelector("#entry-search-summary");
    const logListElement = document.querySelector("#entry-log-list");
    const totalElement = document.querySelector("#entry-total-count");
    const emailElement = document.querySelector("#entry-email-count");
    const matchElement = document.querySelector("#entry-match-count");

    if (!summaryElement || !logListElement || !totalElement || !emailElement || !matchElement) {
      return;
    }

    try {
      const response = await fetch("/api/customers/last-search");
      if (response.status === 204) {
        const cached = readJsonStorage(searchStorageKey);
        if (cached) {
          renderEntrySearchLogs(cached, summaryElement, logListElement, totalElement, emailElement, matchElement);
        }
        return;
      }

      if (!response.ok) {
        throw new Error("Failed to load latest search logs");
      }

      const data = await response.json();
      renderEntrySearchLogs(data, summaryElement, logListElement, totalElement, emailElement, matchElement);
    } catch (error) {
      const cached = readJsonStorage(searchStorageKey);
      if (cached) {
        renderEntrySearchLogs(cached, summaryElement, logListElement, totalElement, emailElement, matchElement);
        return;
      }

      summaryElement.textContent = "最近一次搜索摘要暂时不可用，请稍后再试。";
    }
  }

  function renderEntrySearchLogs(data, summaryElement, logListElement, totalElement, emailElement, matchElement) {
    summaryElement.textContent = data.summary || "最近一次搜索已完成。";
    totalElement.textContent = data.stats?.totalCustomers || 0;
    emailElement.textContent = data.stats?.emailCount || 0;
    matchElement.textContent = data.stats?.highMatchCount || 0;

    const logs = Array.isArray(data.logs) ? data.logs : [];
    if (logs.length === 0) {
      logListElement.innerHTML = `
        <li class="log-item">
          <span class="log-time">--:--:--</span>
          <span class="log-text">最近还没有可展示的抓取日志。</span>
        </li>
      `;
      return;
    }

    logListElement.innerHTML = logs
      .slice(0, 6)
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

  function readJsonStorage(key) {
    const raw = localStorage.getItem(key);
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw);
    } catch (error) {
      localStorage.removeItem(key);
      return null;
    }
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
