package com.zijianxin.website.workflow;

public final class SettingsModels {

    private SettingsModels() {
    }

    public record AppSettings(
            AiSettings ai,
            SearchSettings search,
            CrawlerSettings crawler,
            MailSettings mail,
            GeneralSettings general
    ) {
        public AppSettings {
            ai = ai == null ? AiSettings.defaults() : ai;
            search = search == null ? SearchSettings.defaults() : search;
            crawler = crawler == null ? CrawlerSettings.defaults() : crawler;
            mail = mail == null ? MailSettings.defaults() : mail;
            general = general == null ? GeneralSettings.defaults() : general;
        }

        public static AppSettings defaults() {
            return new AppSettings(
                    AiSettings.defaults(),
                    SearchSettings.defaults(),
                    CrawlerSettings.defaults(),
                    MailSettings.defaults(),
                    GeneralSettings.defaults()
            );
        }

        public AppSettings withAi(AiSettings value) {
            return new AppSettings(value, search, crawler, mail, general);
        }

        public AppSettings withSearch(SearchSettings value) {
            return new AppSettings(ai, value, crawler, mail, general);
        }

        public AppSettings withCrawler(CrawlerSettings value) {
            return new AppSettings(ai, search, value, mail, general);
        }

        public AppSettings withMail(MailSettings value) {
            return new AppSettings(ai, search, crawler, value, general);
        }

        public AppSettings withGeneral(GeneralSettings value) {
            return new AppSettings(ai, search, crawler, mail, value);
        }
    }

    public record AiSettings(
            String provider,
            String apiKey,
            String model,
            String baseUrl,
            String defaultCompanyName,
            String defaultProductName,
            String defaultValueProposition,
            String defaultLanguage,
            String defaultTone,
            String defaultCallToAction
    ) {
        public AiSettings {
            provider = provider == null || provider.isBlank() ? "Qwen" : provider;
            apiKey = apiKey == null ? "" : apiKey;
            model = model == null || model.isBlank() ? "qwen-max" : model;
            baseUrl = baseUrl == null ? "" : baseUrl;
            defaultCompanyName = defaultCompanyName == null || defaultCompanyName.isBlank() ? "One Minute Tech" : defaultCompanyName;
            defaultProductName = defaultProductName == null || defaultProductName.isBlank() ? "AI Lead Generation Workflow" : defaultProductName;
            defaultValueProposition = defaultValueProposition == null ? "" : defaultValueProposition;
            defaultLanguage = defaultLanguage == null || defaultLanguage.isBlank() ? "zh-CN" : defaultLanguage;
            defaultTone = defaultTone == null || defaultTone.isBlank() ? "professional" : defaultTone;
            defaultCallToAction = defaultCallToAction == null ? "" : defaultCallToAction;
        }

        public static AiSettings defaults() {
            return new AiSettings(
                    "Qwen",
                    "",
                    "qwen-plus",
                    "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    "One Minute Tech",
                    "AI Lead Generation Workflow",
                    "We help trade teams combine customer search, outreach draft generation, and follow-up management in one workflow.",
                    "zh-CN",
                    "professional",
                    "If it is convenient, we can arrange a short 15-minute call to see whether this workflow fits your team."
            );
        }
    }

    public record SearchSettings(
            String serpApiKey,
            String defaultEngine,
            int resultsPerPage,
            String linkedinApiKey
    ) {
        public SearchSettings {
            serpApiKey = serpApiKey == null ? "" : serpApiKey;
            defaultEngine = defaultEngine == null || defaultEngine.isBlank() ? "Google" : defaultEngine;
            resultsPerPage = resultsPerPage <= 0 ? 12 : resultsPerPage;
            linkedinApiKey = linkedinApiKey == null ? "" : linkedinApiKey;
        }

        public static SearchSettings defaults() {
            return new SearchSettings("", "Google", 12, "");
        }
    }

    public record CrawlerSettings(
            int requestTimeoutMs,
            int candidateLimit,
            String emailExtractionDepth,
            String logMode,
            int sameDomainWeight,
            int marketWeight,
            int keywordWeight,
            int companySignalWeight
    ) {
        public static CrawlerSettings defaults() {
            return new CrawlerSettings(8000, 36, "HOME_AND_CONTACT", "summary", 10, 8, 6, 8);
        }
    }

    public record MailSettings(
            String senderName,
            String senderEmail,
            String replyToEmail,
            String signature,
            String smtpProvider,
            String smtpHost,
            int smtpPort,
            String smtpSecurity,
            String smtpUsername,
            String smtpPassword,
            int batchLimit,
            int hourlyLimit,
            int retryCount,
            String sendMode
    ) {
        public static MailSettings defaults() {
            return new MailSettings(
                    "Zijian",
                    "zijian@example.com",
                    "reply@example.com",
                    "Zijian | One Minute Tech",
                    "Custom SMTP",
                    "smtp.example.com",
                    587,
                    "STARTTLS",
                    "zijian@example.com",
                    "",
                    50,
                    120,
                    2,
                    "smtp"
            );
        }
    }

    public record GeneralSettings(
            String language,
            String timezone,
            String dateFormat,
            String numberFormat,
            String defaultLandingPage,
            boolean localCacheEnabled,
            boolean autoSelectLeads,
            boolean requireReviewBeforeSend,
            int logRetentionDays,
            boolean debugMode
    ) {
        public static GeneralSettings defaults() {
            return new GeneralSettings(
                    "zh-CN",
                    "Asia/Shanghai",
                    "YYYY-MM-DD",
                    "1,234.56",
                    "/customer-search",
                    true,
                    true,
                    true,
                    30,
                    false
            );
        }
    }
}
