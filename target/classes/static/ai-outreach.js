(() => {
  const selectedCustomersKey = "leadflow-selected-customers";
  const outreachImportFlagKey = "leadflow-outreach-import-pending";
  const pageParams = new URLSearchParams(window.location.search);
  const isImportEntry = pageParams.get("import") === "1";

  const composeForm = document.querySelector("#compose-form");
  const recipientList = document.querySelector("#recipient-list");
  const searchImportButton = document.querySelector("#search-import-button");
  const manualEntryButton = document.querySelector("#manual-entry-button");
  const clearImportedCustomersButton = document.querySelector("#clear-imported-customers");
  const manualCustomerModal = document.querySelector("#manual-customer-modal");
  const manualCustomerClose = document.querySelector("#manual-customer-close");
  const manualCustomerForm = document.querySelector("#manual-customer-form");
  const recipientEmailPreview = document.querySelector("#recipient-email-preview");
  const composeLanguage = document.querySelector("#compose-language");
  const languagePills = Array.from(document.querySelectorAll(".language-pill[data-language]"));
  const sidebarProductName = document.querySelector("#sidebar-product-name");
  const sidebarProductKeywords = document.querySelector("#sidebar-product-keywords");
  const sidebarValueProposition = document.querySelector("#sidebar-value-proposition");

  const hiddenCompanyName = document.querySelector("#hidden-company-name");
  const hiddenProductName = document.querySelector("#hidden-product-name");
  const hiddenValueProposition = document.querySelector("#hidden-value-proposition");
  const hiddenCallToAction = document.querySelector("#hidden-call-to-action");
  const hiddenSenderName = document.querySelector("#hidden-sender-name");
  const hiddenSenderEmail = document.querySelector("#hidden-sender-email");

  const optimizeButton = document.querySelector("#optimize-draft");
  const translateButton = document.querySelector("#translate-email");
  const draftSubject = document.querySelector("#draft-subject");
  const draftBody = document.querySelector("#draft-body");
  const draftEmptyState = document.querySelector(".ref-mail-body-empty");
  const sendButton = document.querySelector("#send-email");
  const sendResult = document.querySelector("#send-result");
  const localeMessages = {
    "zh-CN": {
      "brand.name": "一分钟科技",
      "brand.subtitle": "AI 外贸获客工作台",
      "nav.home": "首页",
      "nav.search": "客户搜索",
      "nav.outreach": "开发信",
      "section.targetCustomers": "目标客户",
      "section.productInfo": "产品信息",
      "section.targetLanguage": "目标语言",
      "section.editor": "开发信编辑",
      "section.templateLibrary": "邮件模板库",
      "field.productName": "产品名称 *",
      "field.productKeywords": "产品关键词 *",
      "field.valueProposition": "产品优势",
      "field.recipient": "收件人：",
      "field.language": "语言：",
      "field.subject": "主题：",
      "field.companyName": "公司名称 *",
      "field.contactName": "联系人",
      "field.email": "邮箱 *",
      "field.country": "国家/地区",
      "field.website": "官网",
      "button.searchImport": "搜索导入",
      "button.manualEntry": "手动输入",
      "button.clearCustomers": "清空客户",
      "button.optimize": "AI优化",
      "button.saveTemplate": "保存模板",
      "button.sendTop": "发送",
      "button.generate": "AI生成开发信",
      "button.sendSelected": "发送给已选客户",
      "button.translate": "翻译邮件",
      "button.newTemplate": "新建模板",
      "button.close": "关闭",
      "button.addCustomer": "添加客户",
      "badge.hot": "热门",
      "template.zh.title": "初次接触（中文）",
      "template.zh.copy": "zh · 初次开发",
      "lang.zh-CN": "中文",
      "lang.en": "英语",
      "lang.de": "德语",
      "lang.fr": "法语",
      "lang.es": "西班牙语",
      "lang.ru": "俄语",
      "lang.ar": "阿拉伯语",
      "lang.pt": "葡萄牙语",
      "empty.noCustomerTitle": "暂无客户",
      "empty.noCustomerCopy": "从客户搜索系统导入，或手动输入",
      "status.pending": "待确认",
      "status.unconfirmed": "待人工确认",
      "status.noEmail": "未找到公开邮箱",
      "empty.draftTitle": "开始生成开发信",
      "empty.draftCopy": "在左侧配置客户信息和产品详情，点击“AI生成开发信”按钮",
      "modal.manualCustomerTitle": "手动输入客户",
      "placeholder.productName": "例如：智能手机配件",
      "placeholder.productKeywords": "例如：充电器,数据线,手机壳",
      "placeholder.valueProposition": "描述您的产品优势和特点...",
      "placeholder.subject": "输入邮件主题...",
      "placeholder.manualCompany": "例如：ABC Trading",
      "placeholder.manualContact": "例如：Alex",
      "placeholder.manualEmail": "例如：alex@example.com",
      "placeholder.manualCountry": "例如：中国",
      "placeholder.manualWebsite": "例如：https://example.com"
    },
    en: {
      "brand.name": "One Minute Tech",
      "brand.subtitle": "AI Lead Generation Workspace",
      "nav.home": "Home",
      "nav.search": "Customer Search",
      "nav.outreach": "Outreach",
      "section.targetCustomers": "Target Customers",
      "section.productInfo": "Product Info",
      "section.targetLanguage": "Target Language",
      "section.editor": "Outreach Editor",
      "section.templateLibrary": "Email Templates",
      "field.productName": "Product Name *",
      "field.productKeywords": "Product Keywords *",
      "field.valueProposition": "Product Advantages",
      "field.recipient": "Recipient:",
      "field.language": "Language:",
      "field.subject": "Subject:",
      "field.companyName": "Company Name *",
      "field.contactName": "Contact Name",
      "field.email": "Email *",
      "field.country": "Country / Region",
      "field.website": "Website",
      "button.searchImport": "Import Search",
      "button.manualEntry": "Manual Entry",
      "button.clearCustomers": "Clear Customers",
      "button.optimize": "AI Optimize",
      "button.saveTemplate": "Save Template",
      "button.sendTop": "Send",
      "button.generate": "Generate Draft",
      "button.sendSelected": "Send to Selected",
      "button.translate": "Translate",
      "button.newTemplate": "New Template",
      "button.close": "Close",
      "button.addCustomer": "Add Customer",
      "badge.hot": "Hot",
      "template.zh.title": "First Contact (Chinese)",
      "template.zh.copy": "zh · Initial outreach",
      "lang.zh-CN": "Chinese",
      "lang.en": "English",
      "lang.de": "German",
      "lang.fr": "French",
      "lang.es": "Spanish",
      "lang.ru": "Russian",
      "lang.ar": "Arabic",
      "lang.pt": "Portuguese",
      "empty.noCustomerTitle": "No Customers Yet",
      "empty.noCustomerCopy": "Import from customer search or add one manually.",
      "status.pending": "Pending",
      "status.unconfirmed": "Unconfirmed",
      "status.noEmail": "No public email found",
      "empty.draftTitle": "Start Generating an Email",
      "empty.draftCopy": "Fill in customer and product details on the left, then click Generate Draft.",
      "modal.manualCustomerTitle": "Manual Customer Entry",
      "placeholder.productName": "e.g. smartphone accessories",
      "placeholder.productKeywords": "e.g. chargers, cables, phone cases",
      "placeholder.valueProposition": "Describe your product strengths and differentiators...",
      "placeholder.subject": "Enter email subject...",
      "placeholder.manualCompany": "e.g. ABC Trading",
      "placeholder.manualContact": "e.g. Alex",
      "placeholder.manualEmail": "e.g. alex@example.com",
      "placeholder.manualCountry": "e.g. China",
      "placeholder.manualWebsite": "e.g. https://example.com"
    }
  };

  const outreachState = {
    recipients: [],
    selectedIds: new Set(),
    settings: null
  };

  init().catch((error) => {
    console.error("Outreach bootstrap failed:", error);
  });

  async function init() {
    await hydrateSettings();
    hydrateRecipients();
    renderRecipients();
    syncDefaultsFromSettings();
    bindSidebarSync();

    composeForm?.addEventListener("submit", handleGenerateDraft);
    optimizeButton?.addEventListener("click", handleOptimizeDraft);
    translateButton?.addEventListener("click", handleTranslateEmail);
    sendButton?.addEventListener("click", handleSendEmail);
    recipientList?.addEventListener("change", handleRecipientSelection);
    searchImportButton?.addEventListener("click", () => {
      window.location.href = "/customer-search";
    });
    manualEntryButton?.addEventListener("click", openManualModal);
    manualCustomerClose?.addEventListener("click", closeManualModal);
    manualCustomerModal?.addEventListener("click", (event) => {
      if (event.target === manualCustomerModal) {
        closeManualModal();
      }
    });
    manualCustomerForm?.addEventListener("submit", handleManualCustomerSubmit);
    clearImportedCustomersButton?.addEventListener("click", clearImportedCustomers);
    composeLanguage?.addEventListener("change", syncLanguagePillsFromSelect);
    draftSubject?.addEventListener("input", updateSendButtonState);
    draftBody?.addEventListener("input", () => {
      toggleDraftEmptyState();
      updateSendButtonState();
    });

    languagePills.forEach((pill) => {
      pill.addEventListener("click", () => {
        selectLanguage(pill.getAttribute("data-language"));
      });
    });

    window.addEventListener("leadflow:locale-changed", (event) => {
      applyPageLocale(event.detail?.locale || "zh-CN");
    });

    syncLanguagePillsFromSelect();
    applyPageLocale(window.leadflowLocale?.locale || "zh-CN");
    toggleDraftEmptyState();
    updateSendButtonState();
  }

  async function hydrateSettings() {
    try {
      const response = await fetch("/api/settings", { cache: "no-store" });
      if (!response.ok) {
        return;
      }
      outreachState.settings = await response.json();
    } catch (error) {
      console.error("Failed to load settings for outreach page:", error);
    }
  }

  function hydrateRecipients() {
    const importPending = localStorage.getItem(outreachImportFlagKey) === "1";
    const selectedCustomers = readJsonStorage(selectedCustomersKey);

    if (isImportEntry && importPending && Array.isArray(selectedCustomers) && selectedCustomers.length > 0) {
      outreachState.recipients = selectedCustomers;
    } else {
      outreachState.recipients = [];
      localStorage.removeItem(selectedCustomersKey);
    }

    localStorage.removeItem(outreachImportFlagKey);
    if (isImportEntry) {
      window.history.replaceState({}, document.title, window.location.pathname);
    }
    outreachState.selectedIds = new Set(outreachState.recipients.map((recipient) => recipient.id));
  }

  function syncDefaultsFromSettings() {
    const ai = outreachState.settings?.ai;
    const mail = outreachState.settings?.mail;

    setValue(hiddenCompanyName, ai?.defaultCompanyName || "一分钟科技");
    setValue(hiddenProductName, ai?.defaultProductName || "AI 外贸获客工作流");
    setValue(hiddenValueProposition, ai?.defaultValueProposition || "");
    setValue(composeLanguage, ai?.defaultLanguage || "zh-CN");
    setValue(hiddenCallToAction, ai?.defaultCallToAction || "");
    setValue(hiddenSenderName, mail?.senderName || "Zijian");
    setValue(hiddenSenderEmail, mail?.senderEmail || "zijian@example.com");
  }

  function bindSidebarSync() {
    sidebarProductName?.addEventListener("input", syncSidebarFieldsToHidden);
    sidebarProductKeywords?.addEventListener("input", syncSidebarFieldsToHidden);
    sidebarValueProposition?.addEventListener("input", syncSidebarFieldsToHidden);
    syncSidebarFieldsToHidden();
  }

  function syncSidebarFieldsToHidden() {
    const defaultProductName = outreachState.settings?.ai?.defaultProductName || "AI 外贸获客工作流";
    const defaultValueProposition = outreachState.settings?.ai?.defaultValueProposition || "";

    const productName = String(sidebarProductName?.value || "").trim();
    const productKeywords = String(sidebarProductKeywords?.value || "").trim();
    const valueProposition = String(sidebarValueProposition?.value || "").trim();

    setValue(hiddenProductName, productName || defaultProductName);

    let mergedValue = valueProposition || defaultValueProposition;
    if (productKeywords) {
      mergedValue = `${mergedValue}\n关键词：${productKeywords}`.trim();
    }
    setValue(hiddenValueProposition, mergedValue);
  }

  function t(key, fallback) {
    const locale = window.leadflowLocale?.locale || "zh-CN";
    const dict = window.pageTranslations?.[locale] || window.pageTranslations?.["zh-CN"] || {};
    return dict[key] || fallback;
  }

  function renderRecipients() {
    if (outreachState.recipients.length === 0) {
      recipientList.className = "outreach-empty-recipient";
      recipientList.innerHTML = `
        <span class="empty-icon" aria-hidden="true">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M16 11c1.66 0 2.99-1.57 2.99-3.5S17.66 4 16 4s-3 1.57-3 3.5 1.34 3.5 3 3.5ZM8 11c1.66 0 2.99-1.57 2.99-3.5S9.66 4 8 4 5 5.57 5 7.5 6.34 11 8 11Zm0 2c-2.33 0-7 1.17-7 3.5V20h14v-3.5C15 14.17 10.33 13 8 13Zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.96 1.97 3.45V20h6v-3.5c0-2.33-4.67-3.5-7-3.5Z" />
          </svg>
        </span>
        <h3>${t("outreach.empty.noCustomerTitle", "暂无客户")}</h3>
        <p>${t("outreach.empty.noCustomerCopy", "从客户搜索系统导入，或手动输入")}</p>
      `;
      recipientEmailPreview.value = "contact@example.com";
      return;
    }

    recipientList.className = "outreach-recipient-list";
    recipientList.innerHTML = outreachState.recipients
      .map((recipient) => `
        <label class="recipient-card">
          <input type="checkbox" data-recipient-id="${escapeHtml(recipient.id)}" ${outreachState.selectedIds.has(recipient.id) ? "checked" : ""} />
          <div class="recipient-body">
            <strong>${escapeHtml(recipient.companyName)}</strong>
            <span class="recipient-meta">${escapeHtml(displayValue(recipient.country, t("outreach.status.pending", "待确认")))} | ${escapeHtml(displayValue(recipient.contactName, t("outreach.status.unconfirmed", "待人工确认")))}</span>
            <span class="recipient-meta">${escapeHtml(displayValue(recipient.email, t("outreach.status.noEmail", "未找到公开邮箱")))}</span>
          </div>
        </label>
      `)
      .join("");

    updateRecipientPreview();
  }

  function openManualModal() {
    manualCustomerModal?.classList.remove("is-hidden");
    manualCustomerModal?.setAttribute("aria-hidden", "false");
    document.querySelector("#manual-company-name")?.focus();
  }

  function closeManualModal() {
    manualCustomerModal?.classList.add("is-hidden");
    manualCustomerModal?.setAttribute("aria-hidden", "true");
    manualCustomerForm?.reset();
  }

  function handleManualCustomerSubmit(event) {
    event.preventDefault();

    const companyName = valueOf("#manual-company-name");
    const contactName = valueOf("#manual-contact-name");
    const email = valueOf("#manual-email");
    const country = valueOf("#manual-country");
    const website = valueOf("#manual-website");

    const customer = {
      id: `manual-${Date.now()}`,
      companyName,
      website,
      country,
      contactName,
      email,
      channel: "Manual Entry",
      fitNote: "Manual input"
    };

    outreachState.recipients.unshift(customer);
    outreachState.selectedIds.add(customer.id);
    localStorage.setItem(selectedCustomersKey, JSON.stringify(outreachState.recipients));
    renderRecipients();
    updateSendButtonState();
    closeManualModal();
    showResult("已添加手动客户，现在可以直接测试 AI 写开发信。", "success");
  }

  async function handleGenerateDraft(event) {
    event.preventDefault();

    if (getSelectedRecipients().length === 0) {
      showResult("请先导入或手动输入至少一个客户。", "warning");
      return;
    }

    const generateButton = document.querySelector("#generate-draft");
    generateButton.disabled = true;
    generateButton.textContent = "生成中...";

    try {
      const payload = buildDraftPayload();
      const response = await fetch("/api/outreach/draft", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        throw new Error(await response.text());
      }

      const data = await response.json();
      draftSubject.value = data.subject || "";
      draftBody.value = data.body || "";
      toggleDraftEmptyState();
      updateSendButtonState();
      showResult(data.analysis || "开发信已通过 AI 生成。", "success");
    } catch (error) {
      console.error("Draft generation failed:", error);
      showResult(normalizeError(error), "warning");
    } finally {
      generateButton.disabled = false;
      generateButton.textContent = "AI生成开发信";
    }
  }

  async function handleOptimizeDraft() {
    if (!draftBody.value.trim()) {
      showResult("请先生成或输入开发信内容，再执行 AI 优化。", "warning");
      return;
    }

    optimizeButton.disabled = true;
    optimizeButton.textContent = "优化中...";

    try {
      const payload = {
        subject: draftSubject.value.trim(),
        body: draftBody.value.trim(),
        companyName: hiddenCompanyName.value || "",
        productName: hiddenProductName.value || "",
        valueProposition: hiddenValueProposition.value || "",
        language: composeLanguage.value || "zh-CN",
        tone: "professional",
        callToAction: hiddenCallToAction.value || "",
        recipients: getSelectedRecipients()
      };

      const response = await fetch("/api/outreach/optimize", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        throw new Error(await response.text());
      }

      const data = await response.json();
      draftSubject.value = data.subject || draftSubject.value;
      draftBody.value = data.body || draftBody.value;
      toggleDraftEmptyState();
      updateSendButtonState();
      showResult(data.analysis || "开发信已通过 AI 优化。", "success");
    } catch (error) {
      console.error("Draft optimization failed:", error);
      showResult(normalizeError(error), "warning");
    } finally {
      optimizeButton.disabled = false;
      optimizeButton.textContent = "AI优化";
    }
  }

  async function handleTranslateEmail() {
    if (!draftBody.value.trim()) {
      showResult("请先生成或输入开发信内容，再执行翻译。", "warning");
      return;
    }

    const targetLanguage = composeLanguage.value || "zh-CN";
    const targetLanguageName = composeLanguage.options[composeLanguage.selectedIndex].text;
    const originalText = translateButton.textContent;

    translateButton.disabled = true;
    translateButton.textContent = `翻译为${targetLanguageName}中...`;

    try {
      const payload = {
        subject: draftSubject.value.trim(),
        body: draftBody.value.trim(),
        targetLanguage: targetLanguage
      };

      const response = await fetch("/api/outreach/translate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        throw new Error(await response.text());
      }

      const data = await response.json();
      draftSubject.value = data.subject || draftSubject.value;
      draftBody.value = data.body || draftBody.value;
      toggleDraftEmptyState();
      updateSendButtonState();
      showResult(`邮件已翻译为 ${targetLanguageName}。`, "success");
    } catch (error) {
      console.error("Email translation failed:", error);
      showResult(normalizeError(error), "warning");
    } finally {
      translateButton.disabled = false;
      translateButton.textContent = originalText;
    }
  }

  async function handleSendEmail() {
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
      const payload = {
        senderName: hiddenSenderName.value || "",
        senderEmail: hiddenSenderEmail.value || "",
        subject: draftSubject.value.trim(),
        body: draftBody.value.trim(),
        recipients: getSelectedRecipients()
      };

      const response = await fetch("/api/outreach/send", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        throw new Error(await response.text());
      }

      const data = await response.json();
      showResult(data.message || `已向 ${data.sentCount || 0} 个客户发送。`, "success");
    } catch (error) {
      console.error("Send email failed:", error);
      showResult(normalizeError(error), "warning");
    } finally {
      updateSendButtonState();
      sendButton.textContent = "发送给已选客户";
    }
  }

  function buildDraftPayload() {
    return {
      companyName: hiddenCompanyName.value || "",
      productName: hiddenProductName.value || "",
      valueProposition: hiddenValueProposition.value || "",
      language: composeLanguage.value || "zh-CN",
      tone: "professional",
      callToAction: hiddenCallToAction.value || "",
      recipients: getSelectedRecipients()
    };
  }

  function clearImportedCustomers() {
    outreachState.recipients = [];
    outreachState.selectedIds = new Set();
    localStorage.removeItem(selectedCustomersKey);
    localStorage.removeItem(outreachImportFlagKey);
    renderRecipients();
    updateSendButtonState();
    showResult("已清空当前客户。", "success");
  }

  function handleRecipientSelection(event) {
    const target = event.target;
    if (!(target instanceof HTMLInputElement) || !target.matches("[data-recipient-id]")) {
      return;
    }

    if (target.checked) {
      outreachState.selectedIds.add(target.dataset.recipientId);
    } else {
      outreachState.selectedIds.delete(target.dataset.recipientId);
    }

    updateRecipientPreview();
    updateSendButtonState();
  }

  function updateRecipientPreview() {
    const firstRecipient = getSelectedRecipients()[0];
    recipientEmailPreview.value = firstRecipient?.email?.trim() || "contact@example.com";
  }

  function updateSendButtonState() {
    const hasRecipients = getSelectedRecipients().length > 0;
    const hasDraft = draftSubject.value.trim() && draftBody.value.trim();
    sendButton.disabled = !(hasRecipients && hasDraft);
  }

  function getSelectedRecipients() {
    return outreachState.recipients.filter((recipient) => outreachState.selectedIds.has(recipient.id));
  }

  function selectLanguage(language) {
    composeLanguage.value = language;
    syncLanguagePillsFromSelect();
  }

  function syncLanguagePillsFromSelect() {
    const current = composeLanguage.value;
    languagePills.forEach((pill) => {
      pill.classList.toggle("is-selected", pill.getAttribute("data-language") === current);
    });
  }

  function applyPageLocale(locale) {
    const dict = localeMessages[locale] || localeMessages["zh-CN"];

    document.querySelectorAll("[data-i18n]").forEach((element) => {
      const key = element.getAttribute("data-i18n");
      if (dict[key]) {
        element.textContent = dict[key];
      }
    });

    document.querySelectorAll("[data-i18n-placeholder]").forEach((element) => {
      const key = element.getAttribute("data-i18n-placeholder");
      if (dict[key]) {
        element.setAttribute("placeholder", dict[key]);
      }
    });

    document.title = locale === "en" ? "Outreach System | One Minute Tech" : "开发信系统 | 一分钟科技";
  }

  function toggleDraftEmptyState() {
    draftEmptyState.style.display = draftBody.value.trim() ? "none" : "grid";
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
    sendResult.classList.add(type === "success" ? "is-success" : "is-warning");
  }

  function setValue(element, value) {
    if (element) {
      element.value = value ?? "";
    }
  }

  function valueOf(selector) {
    return String(document.querySelector(selector)?.value ?? "").trim();
  }

  function displayValue(value, fallbackText) {
    const text = String(value ?? "").trim();
    return text || fallbackText;
  }

  function normalizeError(error) {
    if (!error) {
      return "操作失败，请稍后重试。";
    }
    const message = String(error.message || error).replace(/^Error:\s*/, "").trim();
    return message || "操作失败，请稍后重试。";
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
