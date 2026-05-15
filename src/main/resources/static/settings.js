(() => {
  const path = window.location.pathname;

  init().catch((error) => {
    console.error("Settings bootstrap failed:", error);
  });

  async function init() {
    const settings = await fetchSettings();
    if (!settings) {
      return;
    }

    if (path === "/ai-settings") {
      bindAiSettings(settings);
      return;
    }

    if (path === "/crawler-settings") {
      bindSearchSettings(settings);
      return;
    }

    if (path === "/crawler-rules") {
      bindCrawlerSettings(settings);
      return;
    }

    if (path === "/mail-settings") {
      bindMailSettings(settings);
      return;
    }

    if (path === "/general-settings") {
      bindGeneralSettings(settings);
    }
  }

  async function fetchSettings() {
    const response = await fetch("/api/settings", { cache: "no-store" });
    if (!response.ok) {
      return null;
    }
    return response.json();
  }

  function bindAiSettings(settings) {
    setValue("#ai-provider", settings.ai.provider);
    setValue("#ai-api-key", settings.ai.apiKey);
    setValue("#ai-model", settings.ai.model);
    setValue("#ai-base-url", settings.ai.baseUrl);
    setValue("#ai-default-company-name", settings.ai.defaultCompanyName);
    setValue("#ai-default-product-name", settings.ai.defaultProductName);
    setValue("#ai-default-language", settings.ai.defaultLanguage);
    setValue("#ai-default-tone", settings.ai.defaultTone);
    setValue("#ai-default-value-proposition", settings.ai.defaultValueProposition);
    setValue("#ai-default-call-to-action", settings.ai.defaultCallToAction);

    document.querySelector("#ai-settings-form")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      await saveSettings(
        "/api/settings/ai",
        {
          provider: valueOf("#ai-provider"),
          apiKey: valueOf("#ai-api-key"),
          model: valueOf("#ai-model"),
          baseUrl: valueOf("#ai-base-url"),
          defaultCompanyName: valueOf("#ai-default-company-name"),
          defaultProductName: valueOf("#ai-default-product-name"),
          defaultValueProposition: valueOf("#ai-default-value-proposition"),
          defaultLanguage: valueOf("#ai-default-language"),
          defaultTone: valueOf("#ai-default-tone"),
          defaultCallToAction: valueOf("#ai-default-call-to-action")
        },
        "#ai-settings-result",
        "AI 配置已保存，开发信页默认值会同步更新。"
      );
    });
  }

  function bindSearchSettings(settings) {
    setValue("#search-serp-api-key", settings.search.serpApiKey);
    setValue("#search-default-engine", settings.search.defaultEngine);
    setValue("#search-results-per-page", settings.search.resultsPerPage);
    setValue("#search-linkedin-api-key", settings.search.linkedinApiKey);

    document.querySelector("#search-settings-form")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      await saveSettings(
        "/api/settings/search",
        {
          serpApiKey: valueOf("#search-serp-api-key"),
          defaultEngine: valueOf("#search-default-engine"),
          resultsPerPage: numberOf("#search-results-per-page", 12),
          linkedinApiKey: valueOf("#search-linkedin-api-key")
        },
        "#search-settings-result",
        "搜索配置已保存，客户搜索页会使用新的默认搜索参数。"
      );
    });
  }

  function bindCrawlerSettings(settings) {
    setValue("#crawler-request-timeout-ms", settings.crawler.requestTimeoutMs);
    setValue("#crawler-candidate-limit", settings.crawler.candidateLimit);
    setValue("#crawler-email-extraction-depth", settings.crawler.emailExtractionDepth);
    setValue("#crawler-log-mode", settings.crawler.logMode);
    setValue("#crawler-same-domain-weight", settings.crawler.sameDomainWeight);
    setValue("#crawler-market-weight", settings.crawler.marketWeight);
    setValue("#crawler-keyword-weight", settings.crawler.keywordWeight);
    setValue("#crawler-company-signal-weight", settings.crawler.companySignalWeight);

    document.querySelector("#crawler-settings-form")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      await saveSettings(
        "/api/settings/crawler",
        {
          requestTimeoutMs: numberOf("#crawler-request-timeout-ms", 8000),
          candidateLimit: numberOf("#crawler-candidate-limit", 36),
          emailExtractionDepth: valueOf("#crawler-email-extraction-depth"),
          logMode: valueOf("#crawler-log-mode"),
          sameDomainWeight: numberOf("#crawler-same-domain-weight", 10),
          marketWeight: numberOf("#crawler-market-weight", 8),
          keywordWeight: numberOf("#crawler-keyword-weight", 6),
          companySignalWeight: numberOf("#crawler-company-signal-weight", 8)
        },
        "#crawler-settings-result",
        "爬虫配置已保存，搜索超时、候选池和评分逻辑会同步生效。"
      );
    });
  }

  function bindMailSettings(settings) {
    setValue("#mail-sender-name", settings.mail.senderName);
    setValue("#mail-sender-email", settings.mail.senderEmail);
    setValue("#mail-reply-to-email", settings.mail.replyToEmail);
    setValue("#mail-signature", settings.mail.signature);
    setValue("#mail-smtp-provider", settings.mail.smtpProvider);
    setValue("#mail-smtp-host", settings.mail.smtpHost);
    setValue("#mail-smtp-port", settings.mail.smtpPort);
    setValue("#mail-smtp-security", settings.mail.smtpSecurity);
    setValue("#mail-smtp-username", settings.mail.smtpUsername);
    setValue("#mail-smtp-password", settings.mail.smtpPassword);
    setValue("#mail-batch-limit", settings.mail.batchLimit);
    setValue("#mail-hourly-limit", settings.mail.hourlyLimit);
    setValue("#mail-retry-count", settings.mail.retryCount);
    setValue("#mail-send-mode", settings.mail.sendMode);

    document.querySelector("#mail-settings-form")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      await saveSettings(
        "/api/settings/mail",
        {
          senderName: valueOf("#mail-sender-name"),
          senderEmail: valueOf("#mail-sender-email"),
          replyToEmail: valueOf("#mail-reply-to-email"),
          signature: valueOf("#mail-signature"),
          smtpProvider: valueOf("#mail-smtp-provider"),
          smtpHost: valueOf("#mail-smtp-host"),
          smtpPort: numberOf("#mail-smtp-port", 587),
          smtpSecurity: valueOf("#mail-smtp-security"),
          smtpUsername: valueOf("#mail-smtp-username"),
          smtpPassword: valueOf("#mail-smtp-password"),
          batchLimit: numberOf("#mail-batch-limit", 50),
          hourlyLimit: numberOf("#mail-hourly-limit", 120),
          retryCount: numberOf("#mail-retry-count", 2),
          sendMode: valueOf("#mail-send-mode")
        },
        "#mail-settings-result",
        "邮箱配置已保存，开发信页和发送接口会使用新的默认发件身份。"
      );
    });
  }

  function bindGeneralSettings(settings) {
    setValue("#general-language", settings.general.language);
    setValue("#general-timezone", settings.general.timezone);
    setValue("#general-date-format", settings.general.dateFormat);
    setValue("#general-number-format", settings.general.numberFormat);
    setValue("#general-default-landing-page", settings.general.defaultLandingPage);
    setValue("#general-log-retention-days", settings.general.logRetentionDays);
    setValue("#general-local-cache-enabled", String(settings.general.localCacheEnabled));
    setValue("#general-auto-select-leads", String(settings.general.autoSelectLeads));
    setValue("#general-require-review-before-send", String(settings.general.requireReviewBeforeSend));
    setValue("#general-debug-mode", String(settings.general.debugMode));

    document.querySelector("#general-settings-form")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      await saveSettings(
        "/api/settings/general",
        {
          language: valueOf("#general-language"),
          timezone: valueOf("#general-timezone"),
          dateFormat: valueOf("#general-date-format"),
          numberFormat: valueOf("#general-number-format"),
          defaultLandingPage: valueOf("#general-default-landing-page"),
          localCacheEnabled: booleanOf("#general-local-cache-enabled", true),
          autoSelectLeads: booleanOf("#general-auto-select-leads", true),
          requireReviewBeforeSend: booleanOf("#general-require-review-before-send", true),
          logRetentionDays: numberOf("#general-log-retention-days", 30),
          debugMode: booleanOf("#general-debug-mode", false)
        },
        "#general-settings-result",
        "通用配置已保存，默认入口和本地缓存行为会同步更新。"
      );
    });
  }

  async function saveSettings(url, payload, resultSelector, successText) {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      setResult(resultSelector, "保存失败，请稍后重试。", false);
      return;
    }

    setResult(resultSelector, successText, true);
  }

  function setResult(selector, message, success) {
    const element = document.querySelector(selector);
    if (!element) {
      return;
    }
    element.textContent = message;
    element.classList.remove("is-success", "is-warning");
    element.classList.add(success ? "is-success" : "is-warning");
  }

  function setValue(selector, value) {
    const element = document.querySelector(selector);
    if (element) {
      element.value = value ?? "";
    }
  }

  function valueOf(selector) {
    return String(document.querySelector(selector)?.value ?? "").trim();
  }

  function numberOf(selector, fallbackValue) {
    const parsed = Number(valueOf(selector));
    return Number.isFinite(parsed) ? parsed : fallbackValue;
  }

  function booleanOf(selector, fallbackValue) {
    const raw = valueOf(selector);
    if (raw === "true") {
      return true;
    }
    if (raw === "false") {
      return false;
    }
    return fallbackValue;
  }
})();
