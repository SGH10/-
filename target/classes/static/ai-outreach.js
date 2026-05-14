(() => {
  const selectedCustomersKey = "leadflow-selected-customers";
  const searchStorageKey = "leadflow-search-response";

  const composeForm = document.querySelector("#compose-form");
  const recipientList = document.querySelector("#recipient-list");
  const recipientCount = document.querySelector("#recipient-count");
  const draftSubject = document.querySelector("#draft-subject");
  const draftBody = document.querySelector("#draft-body");
  const analysisText = document.querySelector("#analysis-text");
  const followUpList = document.querySelector("#follow-up-list");
  const sendButton = document.querySelector("#send-email");
  const sendResult = document.querySelector("#send-result");

  const outreachState = {
    recipients: [],
    selectedIds: new Set()
  };

  hydrateRecipients();
  renderRecipients();

  composeForm?.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (getSelectedRecipients().length === 0) {
      showResult("请先从客户搜索页选择至少一个客户。", "warning");
      return;
    }

    const generateButton = document.querySelector("#generate-draft");
    if (!generateButton) {
      return;
    }

    generateButton.disabled = true;
    generateButton.textContent = "生成中...";

    try {
      const payload = Object.fromEntries(new FormData(composeForm).entries());
      payload.recipients = getSelectedRecipients();

      const response = await fetch("/api/outreach/draft", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        throw new Error("生成开发信失败");
      }

      const data = await response.json();
      draftSubject.value = data.subject || "";
      draftBody.value = data.body || "";
      analysisText.textContent = data.analysis || "AI 已完成草稿生成。";
      followUpList.innerHTML = (data.followUpTips || [])
        .map((tip) => `<li>${escapeHtml(tip)}</li>`)
        .join("");

      sendButton.disabled = false;
      showResult("AI 草稿已生成，可以继续手动修改后发送。", "success");
    } catch (error) {
      console.error("Draft generation failed:", error);
      showResult("AI 草稿生成失败，请稍后再试。", "warning");
    } finally {
      generateButton.disabled = false;
      generateButton.textContent = "AI 生成开发信";
      updateRecipientCount();
    }
  });

  sendButton?.addEventListener("click", async () => {
    if (!draftSubject.value.trim() || !draftBody.value.trim()) {
      showResult("请先生成开发信内容，再执行发送。", "warning");
      return;
    }

    if (getSelectedRecipients().length === 0) {
      showResult("当前没有可发送的目标客户。", "warning");
      return;
    }

    sendButton.disabled = true;
    sendButton.textContent = "发送中...";

    try {
      const payload = Object.fromEntries(new FormData(composeForm).entries());
      payload.subject = draftSubject.value.trim();
      payload.body = draftBody.value.trim();
      payload.recipients = getSelectedRecipients();

      const response = await fetch("/api/outreach/send", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        throw new Error("发送失败");
      }

      const data = await response.json();
      const nextSteps = (data.nextSteps || []).map((step) => `- ${step}`).join("\n");
      showResult(
        `已向 ${data.sentCount || 0} 个客户发起演示发送，批次号 ${data.batchId || "--"}。\n${data.message || ""}\n${nextSteps}`,
        "success"
      );
    } catch (error) {
      console.error("Send email failed:", error);
      showResult("邮件发送失败，请检查服务状态后重试。", "warning");
    } finally {
      sendButton.disabled = false;
      sendButton.textContent = "发送给已选客户";
      updateRecipientCount();
    }
  });

  recipientList?.addEventListener("change", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) {
      return;
    }

    if (target.matches("[data-recipient-id]")) {
      if (target.checked) {
        outreachState.selectedIds.add(target.dataset.recipientId);
      } else {
        outreachState.selectedIds.delete(target.dataset.recipientId);
      }
      updateRecipientCount();
    }
  });

  function hydrateRecipients() {
    const selectedCustomers = readJsonStorage(selectedCustomersKey);
    const cachedSearch = readJsonStorage(searchStorageKey);

    if (Array.isArray(selectedCustomers) && selectedCustomers.length > 0) {
      outreachState.recipients = selectedCustomers;
    } else if (cachedSearch && Array.isArray(cachedSearch.customers)) {
      outreachState.recipients = cachedSearch.customers;
    } else {
      outreachState.recipients = [];
    }

    outreachState.selectedIds = new Set(outreachState.recipients.map((recipient) => recipient.id));
  }

  function renderRecipients() {
    if (outreachState.recipients.length === 0) {
      recipientList.innerHTML = `
        <div class="results-empty">
          还没有接收到客户，请先到客户搜索页运行一次搜索并选择客户。
        </div>
      `;
      updateRecipientCount();
      return;
    }

    recipientList.innerHTML = outreachState.recipients
      .map(
        (recipient) => `
          <label class="recipient-card">
            <input type="checkbox" data-recipient-id="${escapeHtml(recipient.id)}" ${outreachState.selectedIds.has(recipient.id) ? "checked" : ""} />
            <div class="recipient-body">
              <strong>${escapeHtml(recipient.companyName)}</strong>
              <span class="recipient-meta">${escapeHtml(displayValue(recipient.country, "待确认"))} | ${escapeHtml(displayValue(recipient.contactName, "待人工确认"))}</span>
              <span class="recipient-meta">${escapeHtml(displayValue(recipient.email, "未找到公开邮箱"))} | ${escapeHtml(recipient.channel || "官网")}</span>
            </div>
          </label>
        `
      )
      .join("");

    updateRecipientCount();
  }

  function updateRecipientCount() {
    const count = getSelectedRecipients().length;
    recipientCount.textContent = count;
    sendButton.disabled = count === 0 || !draftSubject.value.trim() || !draftBody.value.trim();
  }

  function getSelectedRecipients() {
    return outreachState.recipients.filter((recipient) => outreachState.selectedIds.has(recipient.id));
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

  function showResult(message, type) {
    sendResult.textContent = message;
    sendResult.classList.remove("is-success", "is-warning");
    if (type === "success") {
      sendResult.classList.add("is-success");
    } else {
      sendResult.classList.add("is-warning");
    }
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
