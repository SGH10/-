(() => {
  const LOCALE_KEY = "leadflow-locale";
  const normalizedPath = window.location.pathname.replace(/\/+$/, "") || "/";

  const sharedMessages = {
    "zh-CN": {
      "shared.brand.name": "一分钟科技",
      "shared.brand.subtitle": "AI 外贸获客工作台",
      "shared.nav.home": "首页",
      "shared.nav.search": "客户搜索",
      "shared.nav.outreach": "开发信",
      "shared.systemSettings": "系统配置",
      "shared.backHome": "返回首页"
    },
    en: {
      "shared.brand.name": "One Minute Tech",
      "shared.brand.subtitle": "AI Lead Generation Workspace",
      "shared.nav.home": "Home",
      "shared.nav.search": "Customer Search",
      "shared.nav.outreach": "Outreach",
      "shared.systemSettings": "System Settings",
      "shared.backHome": "Back Home"
    }
  };

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

  const locale = readLocale();
  applyLocaleState(locale);
  bindLocaleSwitches();

  window.leadflowLocale = {
    get locale() {
      return readLocale();
    },
    setLocale(localeValue) {
      setLocale(localeValue);
    }
  };

  window.applyPageTranslations = applyPageTranslations;

  function bindLocaleSwitches() {
    document.querySelectorAll(".locale-switch [data-locale]").forEach((button) => {
      button.addEventListener("click", () => {
        setLocale(button.getAttribute("data-locale"));
      });
    });
  }

  function setLocale(localeValue) {
    if (localeValue !== "zh-CN" && localeValue !== "en") {
      return;
    }

    localStorage.setItem(LOCALE_KEY, localeValue);
    applyLocaleState(localeValue);
    window.dispatchEvent(new CustomEvent("leadflow:locale-changed", { detail: { locale: localeValue } }));
  }

  function applyLocaleState(localeValue) {
    document.documentElement.lang = localeValue;
    document.querySelectorAll(".locale-switch").forEach((switchElement) => {
      switchElement.querySelectorAll("[data-locale]").forEach((button) => {
        button.classList.toggle("is-active", button.getAttribute("data-locale") === localeValue);
      });
    });

    applySharedTranslations(localeValue);
    applyPageTranslations(localeValue);
  }

  function applySharedTranslations(localeValue) {
    const dict = sharedMessages[localeValue] || sharedMessages["zh-CN"];

    document.querySelectorAll("[data-i18n-shared]").forEach((element) => {
      const key = element.getAttribute("data-i18n-shared");
      if (dict[key]) {
        element.textContent = dict[key];
      }
    });
  }

  function applyPageTranslations(localeValue) {
    const pageMessages = window.pageTranslations;
    if (!pageMessages) {
      return;
    }

    const dict = pageMessages[localeValue] || pageMessages["zh-CN"];
    if (!dict) {
      return;
    }

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

    if (dict.__title) {
      document.title = dict.__title;
    }
  }

  function readLocale() {
    const saved = localStorage.getItem(LOCALE_KEY);
    return saved === "en" ? "en" : "zh-CN";
  }
})();
