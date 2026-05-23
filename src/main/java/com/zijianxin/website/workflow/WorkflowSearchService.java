package com.zijianxin.website.workflow;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class WorkflowSearchService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSearchService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "baidu.com",
            "bing.com",
            "duckduckgo.com",
            "google.com",
            "steampowered.com",
            "mdpi.com",
            "ithome.com",
            "eduease.com",
            "thepaper.cn",
            "dictionary.cambridge.org",
            "iciba.com",
            "dict.cn",
            "dict.eudic.net",
            "engoo.cn.com",
            "hopenglish.com",
            "zhihu.com",
            "wikipedia.org",
            "baike.sogou.com",
            "sogou.com",
            "jc35.com",
            "jdzj.com",
            "made-in-china.com",
            "alibaba.com",
            "1688.com",
            "qcc.com"
    );

    private static final List<String> CONTACT_PATH_HINTS = List.of(
            "/contact", "/contact-us", "/about", "/about-us", "/company", "/company-profile", "/support",
            "/sales", "/team", "/impressum", "/imprint", "/legal", "/enquiry", "/inquiry", "/kontakt"
    );

    private static final List<String> CONTACT_PAGE_FALLBACKS = List.of(
            "/contact",
            "/contact-us",
            "/about",
            "/about-us",
            "/company",
            "/company-profile",
            "/support"
    );

    private static final List<String> CONTACT_TEXT_HINTS = List.of(
            "contact", "contact us", "about", "about us", "support", "sales", "team", "impressum", "imprint", "kontakt"
    );

    private static final List<String> NON_COMPANY_TEXT_HINTS = List.of(
            "dictionary", "translation", "translate", "encyclopedia", "news", "article", "blog", "forum", "wiki",
            "journal", "open access", "kids", "game", "gaming", "magazine", "paper", "research", "download"
    );

    private static final List<String> EDITORIAL_URL_PATTERNS = List.of(
            "/news/", "newsdetail", "/article/", "/articles/", "/blog/", "/forum/", "/wiki/",
            "/baike/", "/zhidao/", "/question/", "/answers/", "/post/"
    );

    private static final List<String> FREE_MAIL_DOMAINS = List.of(
            "qq.com", "163.com", "126.com", "gmail.com", "hotmail.com", "outlook.com", "yahoo.com"
    );

    private static final List<String> COMPANY_TEXT_HINTS = List.of(
            "company", "manufacturer", "factory", "supplier", "products", "solutions", "contact us", "about us",
            "co., ltd", "limited", "corporation", "inc", "llc", "gmbh",
            "\u6709\u9650\u516c\u53f8", "\u96c6\u56e2", "\u5de5\u5382", "\u5236\u9020", "\u4ea7\u54c1",
            "\u8054\u7cfb\u6211\u4eec", "\u5173\u4e8e\u6211\u4eec", "\u5b98\u7f51", "\u5b98\u65b9\u7f51\u7ad9"
    );

    private static final List<String> STRONG_COMPANY_HINTS = List.of(
            "co., ltd", "co ltd", "limited", "corporation", "inc", "llc", "gmbh", "official website", "official site",
            "\u6709\u9650\u516c\u53f8", "\u80a1\u4efd\u6709\u9650\u516c\u53f8", "\u96c6\u56e2", "\u5b98\u7f51", "\u5b98\u65b9\u7f51\u7ad9",
            "\u8054\u7cfb\u6211\u4eec", "\u5173\u4e8e\u6211\u4eec"
    );

    private static final List<String> REFERENCE_HOST_HINTS = List.of(
            "dict", "dictionary", "translate", "translation", "wiki", "baike", "news", "blog", "forum",
            "zhidao", "csdn", "bilibili", "hujiang", "zhuaniao", "yingyuqiao", "cqvip", "sciencedirect", "ieee",
            "sohu", "sina", "xueqiu", "ncss", "wenku", "36kr", "toutiao", "baijiahao", "ifeng", "eastmoney",
            "cailian", "yicai", "jiemian"
    );

    private static final List<String> PERSON_TITLE_HINTS = List.of(
            "manager", "director", "sales", "contact"
    );

    private static final Set<String> SEARCH_STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "from", "this", "into", "your", "their", "mainly",
            "主要", "需要", "寻找", "查找", "目标", "客户", "公司", "企业", "行业", "市场", "地区"
    );

    private static final int SEARCH_ENGINE_PAGE_LIMIT = 2;
    private static final int MAX_PARALLEL_INSPECTIONS = 8;
    private static final int GOOGLE_FALLBACK_QUERY_LIMIT = 2;
    private static final int GOOGLE_FALLBACK_PAGE_LIMIT = 1;
    private static final int GOOGLE_FALLBACK_TIMEOUT_MS = 5000;
    private static final String SERPAPI_BASE_URL = "https://serpapi.com/search";

    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile WorkflowModels.CustomerSearchResponse lastSearchResponse;

    public WorkflowSearchService(SettingsService settingsService, ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public WorkflowModels.CustomerSearchResponse getLastSearchResponse() {
        return lastSearchResponse;
    }

    public WorkflowModels.CustomerSearchResponse searchCustomers(WorkflowModels.CustomerSearchRequest request) {
        SearchSession session = new SearchSession();
        SettingsModels.AppSettings settings = settingsService.getSettings();
        SettingsModels.SearchSettings searchSettings = settings.search();
        SettingsModels.CrawlerSettings crawlerSettings = settings.crawler();

        String industry = normalizeInput(fallback(request.industry(), "industrial equipment"));
        String market = normalizeMarket(fallback(request.market(), "China"));
        String keywords = normalizeInput(fallback(request.keywords(), ""));
        String companySize = normalizeInput(fallback(request.companySize(), "50-200"));

        int leadLimit = normalizePositive(request.requestedLimit(), searchSettings.resultsPerPage(), 10);
        int candidatePoolLimit = Math.max(
                Math.max(leadLimit * 8, 80),
                normalizePositive(crawlerSettings.candidateLimit(), 50, 50)
        );
        int timeoutMs = normalizePositive(crawlerSettings.requestTimeoutMs(), 8000, 8000);

        List<String> queries = buildSearchQueries(industry, market, keywords, companySize);
        session.log("Received search task.");
        session.log("Search strategy: " + String.join(" | ", queries));
        session.log("Runtime config: limit=" + leadLimit + ", pool=" + candidatePoolLimit + ", timeout=" + timeoutMs + "ms");

        List<SearchCandidate> candidates = fetchCandidates(queries, market, session, leadLimit, candidatePoolLimit, timeoutMs, searchSettings);
        List<WorkflowModels.CustomerLead> leads = inspectCandidates(
                candidates,
                industry,
                market,
                keywords,
                leadLimit,
                timeoutMs,
                crawlerSettings,
                session
        );

        WorkflowModels.SearchStats stats = new WorkflowModels.SearchStats(
                leads.size(),
                (int) leads.stream().filter(lead -> !lead.email().isBlank()).count(),
                (int) leads.stream().filter(lead -> lead.fitNote().contains("email") || lead.fitNote().contains("contact page")).count(),
                (int) Math.max(0, leads.stream().map(WorkflowModels.CustomerLead::country).distinct().count())
        );

        String summary = leads.isEmpty()
                ? "No matching company websites with useful contact data were found from live public web search."
                : "Collected " + leads.size() + " live company leads from public search results.";

        session.log("Search finished with " + leads.size() + " leads.");

        WorkflowModels.CustomerSearchResponse response = new WorkflowModels.CustomerSearchResponse(
                summary,
                stats,
                List.copyOf(session.logs()),
                leads
        );
        lastSearchResponse = response;
        return response;
    }

    private List<String> buildSearchQueries(String industry, String market, String keywords, String companySize) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        List<String> industryHints = buildSearchHints(industry);
        List<String> keywordHints = buildSearchHints(keywords);
        boolean hasKeywordHints = !keywordHints.isEmpty();
        String primaryIndustryHint = firstQueryHint(industryHints, toEnglishHint(industry));
        String primaryKeywordHint = hasKeywordHints
                ? firstQueryHint(keywordHints, toEnglishHint(keywords))
                : "";
        String nativeIndustryHint = firstNativeHint(industryHints, industry);
        String nativeKeywordHint = hasKeywordHints
                ? firstNativeHint(keywordHints, keywords)
                : "";

        if ("China".equalsIgnoreCase(market)) {
            queries.add(joinQuery("site:.cn", primaryKeywordHint, primaryIndustryHint, "manufacturer", "official website"));
            queries.add(joinQuery("site:.com.cn", primaryKeywordHint, primaryIndustryHint, "company"));
            queries.add(joinQuery(primaryKeywordHint, primaryIndustryHint, "manufacturer", "contact email"));
            queries.add(joinQuery(primaryKeywordHint, primaryIndustryHint, "factory", "contact us"));
            queries.add(joinQuery(primaryKeywordHint, primaryIndustryHint, "supplier", "sales email"));
            queries.add(joinQuery(primaryKeywordHint, primaryIndustryHint, "company profile"));
            queries.add(joinQuery(nativeKeywordHint, nativeIndustryHint, "官网"));
            queries.add(joinQuery(nativeKeywordHint, nativeIndustryHint, "厂家", "联系方式"));
            queries.add(joinQuery(nativeKeywordHint, nativeIndustryHint, "公司", "邮箱"));
            queries.add(joinQuery(nativeKeywordHint, nativeIndustryHint, "有限公司"));
            if (!"ALL".equalsIgnoreCase(companySize)) {
                queries.add(joinQuery(primaryKeywordHint, primaryIndustryHint, companySize, "company"));
            }
            return new ArrayList<>(queries);
        }

        if ("ALL".equalsIgnoreCase(market)) {
            queries.add(joinQuery(primaryKeywordHint, primaryIndustryHint, "manufacturer", "official website"));
            queries.add(joinQuery(primaryKeywordHint, primaryIndustryHint, "supplier", "contact"));
            queries.add(joinQuery(primaryKeywordHint, primaryIndustryHint, "factory", "email"));
            queries.add(joinQuery(primaryKeywordHint, primaryIndustryHint, "company profile"));
            queries.add(joinQuery(primaryKeywordHint, primaryIndustryHint, "sales email"));
            if (!"ALL".equalsIgnoreCase(companySize)) {
                queries.add(joinQuery(primaryKeywordHint, primaryIndustryHint, companySize, "company"));
            }
            return new ArrayList<>(queries);
        }

        String marketAlias = marketAlias(market);
        String marketSite = marketSite(market);
        queries.add(joinQuery(marketSite, primaryKeywordHint, primaryIndustryHint, "manufacturer", "official website"));
        queries.add(joinQuery(marketAlias, primaryKeywordHint, primaryIndustryHint, "supplier", "contact"));
        queries.add(joinQuery(marketAlias, primaryKeywordHint, primaryIndustryHint, "factory", "email"));
        queries.add(joinQuery(marketAlias, primaryKeywordHint, primaryIndustryHint, "company profile"));
        queries.add(joinQuery(marketAlias, primaryKeywordHint, primaryIndustryHint, "sales email"));
        if (!"ALL".equalsIgnoreCase(companySize)) {
            queries.add(joinQuery(marketAlias, primaryKeywordHint, primaryIndustryHint, companySize, "company"));
        }
        return new ArrayList<>(queries);
    }

    private List<SearchCandidate> fetchCandidates(
            List<String> queries,
            String market,
            SearchSession session,
            int leadLimit,
            int candidatePoolLimit,
            int timeoutMs,
            SettingsModels.SearchSettings searchSettings
    ) {
        String serpApiKey = searchSettings.serpApiKey();
        if (serpApiKey != null && !serpApiKey.isBlank()) {
            session.log("Mode: SerpAPI (engine=" + searchSettings.defaultEngine() + ")");
            return fetchCandidatesFromSerpApi(queries, session, candidatePoolLimit, serpApiKey, searchSettings.defaultEngine());
        }

        session.log("Mode: Direct web scraping (Bing / DuckDuckGo / Baidu)");
        List<SearchCandidate> candidates = new ArrayList<>();
        Set<String> seenHosts = new LinkedHashSet<>();

        for (String query : queries) {
            if (candidates.size() >= candidatePoolLimit) {
                break;
            }
            session.log("Trying query: " + query);
            collectFromBingRss(query, candidates, seenHosts, candidatePoolLimit, timeoutMs);
            collectFromBingHtml(query, candidates, seenHosts, candidatePoolLimit, timeoutMs);
            collectFromDuckDuckGo(query, candidates, seenHosts, candidatePoolLimit, timeoutMs);
            if ("China".equalsIgnoreCase(market)) {
                collectFromBaidu(query, candidates, seenHosts, candidatePoolLimit, timeoutMs);
            }
        }

        if (candidates.size() < leadLimit) {
            session.log("Google fallback enabled because primary sources returned only " + candidates.size() + " candidates.");
            collectGoogleFallback(queries, candidates, seenHosts, candidatePoolLimit, timeoutMs);
        }

        session.log("Collected " + candidates.size() + " candidate websites.");
        return candidates;
    }

    private void collectGoogleFallback(
            List<String> queries,
            List<SearchCandidate> candidates,
            Set<String> seenHosts,
            int candidatePoolLimit,
            int timeoutMs
    ) {
        int queryLimit = Math.min(queries.size(), GOOGLE_FALLBACK_QUERY_LIMIT);
        int googleTimeoutMs = Math.min(timeoutMs, GOOGLE_FALLBACK_TIMEOUT_MS);
        for (int index = 0; index < queryLimit; index++) {
            if (candidates.size() >= candidatePoolLimit) {
                return;
            }
            collectFromGoogle(queries.get(index), candidates, seenHosts, candidatePoolLimit, googleTimeoutMs, GOOGLE_FALLBACK_PAGE_LIMIT);
        }
    }

    private List<SearchCandidate> fetchCandidatesFromSerpApi(
            List<String> queries,
            SearchSession session,
            int candidatePoolLimit,
            String serpApiKey,
            String engineName
    ) {
        List<SearchCandidate> candidates = new ArrayList<>();
        Set<String> seenHosts = new LinkedHashSet<>();
        String serpEngine = mapEngineName(engineName);
        int resultsPerQuery = Math.min(candidatePoolLimit, 10);

        for (String query : queries) {
            if (candidates.size() >= candidatePoolLimit) {
                break;
            }
            session.log("SerpAPI query: " + query);
            try {
                String apiUrl = SERPAPI_BASE_URL
                        + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&api_key=" + serpApiKey
                        + "&engine=" + serpEngine
                        + "&num=" + resultsPerQuery;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Accept", "application/json")
                        .timeout(java.time.Duration.ofSeconds(30))
                        .GET()
                        .build();

                log.info("Crawler fetch: {}", apiUrl);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    session.log("SerpAPI returned HTTP " + response.statusCode() + " for query: " + query);
                    continue;
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode organicResults = root.get("organic_results");
                if (organicResults == null || !organicResults.isArray()) {
                    session.log("SerpAPI response has no organic_results for query: " + query);
                    continue;
                }

                for (JsonNode result : organicResults) {
                    String title = safeText(result, "title");
                    String link = safeText(result, "link");
                    String snippet = safeText(result, "snippet");
                    addCandidate(candidates, seenHosts, title, link, snippet, "SerpAPI/" + serpEngine);
                    if (candidates.size() >= candidatePoolLimit) {
                        break;
                    }
                }
                session.log("SerpAPI returned " + organicResults.size() + " results for: " + query);

            } catch (Exception e) {
                session.log("SerpAPI call failed for query '" + query + "': " + e.getMessage());
                // Continue to next query instead of failing entirely
            }
        }

        session.log("SerpAPI collected " + candidates.size() + " candidates.");
        return candidates;
    }

    private String mapEngineName(String name) {
        if (name == null) {
            return "google";
        }
        return switch (name.trim().toLowerCase()) {
            case "bing" -> "bing";
            case "duckduckgo" -> "duckduckgo";
            case "baidu" -> "baidu";
            default -> "google"; // SerpAPI defaults to Google
        };
    }

    private String safeText(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child == null || child.isNull()) ? "" : cleanText(child.asText());
    }

    private void collectFromBingRss(
            String query,
            List<SearchCandidate> candidates,
            Set<String> seenHosts,
            int candidatePoolLimit,
            int timeoutMs
    ) {
        if (candidates.size() >= candidatePoolLimit) {
            return;
        }

        String searchUrl = "https://www.bing.com/search?format=rss&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            log.info("Crawler fetch: {}", searchUrl);
            Document document = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .referrer("https://www.bing.com/")
                    .timeout(timeoutMs)
                    .parser(Parser.xmlParser())
                    .get();

            for (Element item : document.select("item")) {
                Element titleElement = item.selectFirst("title");
                Element linkElement = item.selectFirst("link");
                Element descriptionElement = item.selectFirst("description");
                if (linkElement == null) {
                    continue;
                }
                addCandidate(
                        candidates,
                        seenHosts,
                        cleanText(titleElement == null ? "" : titleElement.text()),
                        cleanText(linkElement.text()),
                        cleanText(descriptionElement == null ? "" : descriptionElement.text()),
                        "Bing RSS"
                );
                if (candidates.size() >= candidatePoolLimit) {
                    break;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void collectFromGoogle(
            String query,
            List<SearchCandidate> candidates,
            Set<String> seenHosts,
            int candidatePoolLimit,
            int timeoutMs,
            int pageLimit
    ) {
        for (int pageIndex = 0; pageIndex < pageLimit; pageIndex++) {
            if (candidates.size() >= candidatePoolLimit) {
                return;
            }

            int offset = pageIndex * 10;
            String searchUrl = "https://www.google.com/search?num=10&hl=en&filter=0&q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&start=" + offset;
            try {
                log.info("Crawler fetch: {}", searchUrl);
                Document document = Jsoup.connect(searchUrl)
                        .userAgent(USER_AGENT)
                        .referrer("https://www.google.com/")
                        .timeout(timeoutMs)
                        .get();

                for (Element link : document.select("a[href*='/url?']:has(h3), a[href^='https://www.google.com/url?']:has(h3)")) {
                    Element titleElement = link.selectFirst("h3");
                    if (titleElement == null) {
                        continue;
                    }
                    String resolved = resolveGoogleUrl(link.absUrl("href"), link.attr("href"));
                    String snippet = cleanText(link.parent() == null ? "" : link.parent().text());
                    addCandidate(
                            candidates,
                            seenHosts,
                            cleanText(titleElement.text()),
                            resolved,
                            snippet,
                            "Google"
                    );
                    if (candidates.size() >= candidatePoolLimit) {
                        break;
                    }
                }
            } catch (IOException ignored) {
            } catch (Exception exception) {
                log.info("Google query failed for: {} ({})", query, exception.getMessage());
            }
        }
    }

    private void collectFromBingHtml(
            String query,
            List<SearchCandidate> candidates,
            Set<String> seenHosts,
            int candidatePoolLimit,
            int timeoutMs
    ) {
        for (int pageIndex = 0; pageIndex < SEARCH_ENGINE_PAGE_LIMIT; pageIndex++) {
            if (candidates.size() >= candidatePoolLimit) {
                return;
            }

            int offset = pageIndex * 10;
            String searchUrl = "https://www.bing.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&first=" + (offset + 1);
            try {
                log.info("Crawler fetch: {}", searchUrl);
                Document document = Jsoup.connect(searchUrl)
                        .userAgent(USER_AGENT)
                        .referrer("https://www.bing.com/")
                        .timeout(timeoutMs)
                        .get();

                for (Element result : document.select("li.b_algo")) {
                    Element link = result.selectFirst("h2 a");
                    if (link == null) {
                        continue;
                    }
                    addCandidate(
                            candidates,
                            seenHosts,
                            cleanText(link.text()),
                            cleanText(link.attr("abs:href")),
                            cleanText(result.select(".b_caption").text()),
                            "Bing HTML"
                    );
                    if (candidates.size() >= candidatePoolLimit) {
                        break;
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void collectFromDuckDuckGo(
            String query,
            List<SearchCandidate> candidates,
            Set<String> seenHosts,
            int candidatePoolLimit,
            int timeoutMs
    ) {
        if (candidates.size() >= candidatePoolLimit) {
            return;
        }

        String searchUrl = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            log.info("Crawler fetch: {}", searchUrl);
            Document document = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .referrer("https://duckduckgo.com/")
                    .timeout(timeoutMs)
                    .get();

            for (Element link : document.select("a.result__a, a.result-link")) {
                String resolved = resolveDuckDuckGoUrl(link.attr("abs:href"), link.attr("href"));
                String snippet = cleanText(link.closest(".result") == null ? "" : link.closest(".result").select(".result__snippet").text());
                addCandidate(candidates, seenHosts, cleanText(link.text()), resolved, snippet, "DuckDuckGo HTML");
                if (candidates.size() >= candidatePoolLimit) {
                    break;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void collectFromBaidu(
            String query,
            List<SearchCandidate> candidates,
            Set<String> seenHosts,
            int candidatePoolLimit,
            int timeoutMs
    ) {
        for (int pageIndex = 0; pageIndex < SEARCH_ENGINE_PAGE_LIMIT; pageIndex++) {
            if (candidates.size() >= candidatePoolLimit) {
                return;
            }

            int offset = pageIndex * 10;
            String searchUrl = "https://www.baidu.com/s?wd=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&pn=" + offset;
            try {
                log.info("Crawler fetch: {}", searchUrl);
                Document document = Jsoup.connect(searchUrl)
                        .userAgent(USER_AGENT)
                        .referrer("https://www.baidu.com/")
                        .timeout(timeoutMs)
                        .get();

                for (Element link : document.select("h3 a")) {
                    String resolved = followRedirectUrl(link.absUrl("href"), timeoutMs);
                    String snippet = cleanText(link.closest("div") == null ? "" : link.closest("div").text());
                    addCandidate(candidates, seenHosts, cleanText(link.text()), resolved, snippet, "Baidu");
                    if (candidates.size() >= candidatePoolLimit) {
                        break;
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void addCandidate(List<SearchCandidate> candidates, Set<String> seenHosts, String title, String url, String snippet, String source) {
        String normalizedUrl = cleanText(url);
        if (normalizedUrl.isBlank() || !normalizedUrl.startsWith("http")) {
            return;
        }

        String host = normalizeHost(hostOf(normalizedUrl));
        String combined = (cleanText(title) + " " + cleanText(snippet)).toLowerCase(Locale.ROOT);
        if (host.isBlank() || !seenHosts.add(host) || isBlockedHost(host) || looksLikeReferenceHost(host) || looksLikeBlockedContent(normalizedUrl, combined)) {
            return;
        }

        candidates.add(new SearchCandidate(title, rootUrlOf(normalizedUrl), snippet, source));
    }

    private List<WorkflowModels.CustomerLead> inspectCandidates(
            List<SearchCandidate> candidates,
            String industry,
            String market,
            String keywords,
            int searchLimit,
            int timeoutMs,
            SettingsModels.CrawlerSettings crawlerSettings,
            SearchSession session
    ) {
        List<WorkflowModels.CustomerLead> leads = new ArrayList<>();
        Set<String> acceptedHosts = new LinkedHashSet<>();

        List<SearchCandidate> sortedCandidates = candidates.stream()
                .sorted(Comparator.comparingInt((SearchCandidate candidate) -> scoreCandidate(candidate, market, industry, keywords)).reversed())
                .toList();

        int inspectionLimit = Math.min(sortedCandidates.size(), Math.max(searchLimit * 6, 30));
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(MAX_PARALLEL_INSPECTIONS, Math.max(2, inspectionLimit)));
        CompletionService<InspectedCandidate> completionService = new ExecutorCompletionService<>(executor);
        int submitted = 0;

        try {
            for (int index = 0; index < inspectionLimit; index++) {
                SearchCandidate candidate = sortedCandidates.get(index);
                completionService.submit(() -> new InspectedCandidate(
                        candidate,
                        inspectWebsite(candidate, industry, market, keywords, timeoutMs, crawlerSettings)
                ));
                submitted++;
            }

            for (int index = 0; index < submitted; index++) {
                if (leads.size() >= searchLimit) {
                    break;
                }

                InspectedCandidate inspectedCandidate = completionService.take().get();
                if (inspectedCandidate == null || inspectedCandidate.scanResult() == null) {
                    continue;
                }

                PageScanResult scanResult = inspectedCandidate.scanResult();
                String host = normalizeHost(hostOf(scanResult.website()));
                if (host.isBlank() || !acceptedHosts.add(host)) {
                    continue;
                }

                leads.add(toLead(scanResult));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignored) {
            // Keep best-effort search behavior even if some pages fail.
        } finally {
            executor.shutdownNow();
        }

        if (leads.size() < searchLimit) {
            fillWithFallbackCandidates(
                    sortedCandidates,
                    acceptedHosts,
                    leads,
                    industry,
                    market,
                    keywords,
                    searchLimit
            );
        }

        leads.sort(Comparator.comparingInt((WorkflowModels.CustomerLead lead) -> lead.email().isBlank() ? 0 : 1).reversed());
        session.log("Filtered down to " + leads.size() + " company leads after website inspection.");
        return leads;
    }

    private int scoreCandidate(SearchCandidate candidate, String market, String industry, String keywords) {
        String host = normalizeHost(hostOf(candidate.url()));
        String combined = (cleanText(candidate.title()) + " " + cleanText(candidate.snippet())).toLowerCase(Locale.ROOT);
        int score = 0;

        if (looksLikeCompanyCandidate(host, combined)) {
            score += 10;
        }
        if (matchesMarketSignal(host, combined, market)) {
            score += 10;
        }
        if (matchesKeywords(industry, keywords, combined)) {
            score += 12;
        }
        if (combined.contains("contact") || combined.contains("email")) {
            score += 6;
        }
        if (combined.contains("manufacturer") || combined.contains("factory") || combined.contains("company")) {
            score += 6;
        }
        return score;
    }

    private PageScanResult inspectWebsite(
            SearchCandidate candidate,
            String industry,
            String market,
            String keywords,
            int timeoutMs,
            SettingsModels.CrawlerSettings crawlerSettings
    ) {
        try {
            String homepageCandidate = rootUrlOf(candidate.url());
            Document document = fetchDocument(homepageCandidate, timeoutMs);
            if (document == null) {
                return null;
            }

            String finalUrl = document.location().isBlank() ? homepageCandidate : document.location();
            String host = normalizeHost(hostOf(finalUrl));
            String title = cleanText(document.title());
            String text = cleanText(document.text());
            String combined = String.join(" ", title, text, candidate.title(), candidate.snippet()).toLowerCase(Locale.ROOT);

            if (host.isBlank() || isBlockedHost(host)) {
                return null;
            }
            if (looksLikeBlockedContent(finalUrl, combined)) {
                return null;
            }
            if (!looksLikeCompanyCandidate(host, combined)) {
                return null;
            }
            if (!matchesMarketSignal(host, combined, market)) {
                return null;
            }
            if (!matchesKeywords(industry, keywords, combined)) {
                return null;
            }

            String homepageUrl = rootUrlOf(finalUrl);
            Set<String> emails = new LinkedHashSet<>(extractEmails(document.html()));
            String contactPageUrl = null;
            Document contactDocument = null;

            if (!"HOME_ONLY".equalsIgnoreCase(crawlerSettings.emailExtractionDepth())) {
                contactPageUrl = findContactPage(homepageUrl, document);
                if (contactPageUrl == null) {
                    contactPageUrl = probeContactPage(homepageUrl, timeoutMs);
                }
                if (contactPageUrl != null) {
                    contactDocument = fetchDocument(contactPageUrl, timeoutMs);
                    if (contactDocument != null) {
                        emails.addAll(extractEmails(contactDocument.html()));
                    }
                }
            }

            String email = chooseBestEmail(emails, host);
            String companyName = extractCompanyName(document, candidate, host);
            String contactName = extractContactName(document, contactDocument, email);

            List<String> notes = new ArrayList<>();
            if (contactPageUrl != null) {
                notes.add("contact page");
            }
            if (!email.isBlank()) {
                notes.add("email found");
            }

            return new PageScanResult(
                    companyName,
                    homepageUrl,
                    inferCountry(host, market),
                    contactName.isBlank() ? "Business Contact" : contactName,
                    email,
                    "Search engine + website",
                    notes.isEmpty() ? "company website" : String.join("; ", notes)
            );
        } catch (IOException ignored) {
            return null;
        }
    }

    private WorkflowModels.CustomerLead toLead(PageScanResult scanResult) {
        return new WorkflowModels.CustomerLead(
                "lead-" + UUID.randomUUID(),
                scanResult.companyName(),
                scanResult.website(),
                scanResult.country(),
                scanResult.contactName(),
                scanResult.email(),
                scanResult.channel(),
                scanResult.fitNote()
        );
    }

    private void fillWithFallbackCandidates(
            List<SearchCandidate> sortedCandidates,
            Set<String> acceptedHosts,
            List<WorkflowModels.CustomerLead> leads,
            String industry,
            String market,
            String keywords,
            int searchLimit
    ) {
        for (SearchCandidate candidate : sortedCandidates) {
            if (leads.size() >= searchLimit) {
                break;
            }

            String host = normalizeHost(hostOf(candidate.url()));
            String combined = (cleanText(candidate.title()) + " " + cleanText(candidate.snippet())).toLowerCase(Locale.ROOT);
            if (host.isBlank() || acceptedHosts.contains(host) || isBlockedHost(host)) {
                continue;
            }
            if (!looksLikeCompanyCandidate(host, combined)) {
                continue;
            }
            if (!matchesMarketSignal(host, combined, market)) {
                continue;
            }
            if (!matchesKeywords(industry, keywords, combined)) {
                continue;
            }
            if (!hasStrongCompanySignal(host, combined)) {
                continue;
            }

            acceptedHosts.add(host);
            leads.add(new WorkflowModels.CustomerLead(
                    "lead-" + UUID.randomUUID(),
                    simplifyTitle(candidate.title()).isBlank() ? host : simplifyTitle(candidate.title()),
                    rootUrlOf(candidate.url()),
                    inferCountry(host, market),
                    "Business Contact",
                    "",
                    candidate.source(),
                    "candidate website; manual review suggested"
            ));
        }
    }

    private Document fetchDocument(String url, int timeoutMs) throws IOException {
        log.info("Crawler fetch: {}", url);
        Connection.Response response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(timeoutMs)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .execute();
        if (response.statusCode() >= 400) {
            return null;
        }
        return response.parse();
    }

    private List<String> extractEmails(String html) {
        Set<String> emails = new LinkedHashSet<>();
        Matcher matcher = EMAIL_PATTERN.matcher(html == null ? "" : html);
        while (matcher.find()) {
            String email = cleanText(matcher.group()).toLowerCase(Locale.ROOT);
            if (email.contains("@")) {
                emails.add(email);
            }
        }

        Matcher mailtoMatcher = Pattern.compile("mailto:([^\"'?#\\s>]+)", Pattern.CASE_INSENSITIVE).matcher(html == null ? "" : html);
        while (mailtoMatcher.find()) {
            String email = cleanText(mailtoMatcher.group(1)).toLowerCase(Locale.ROOT);
            if (email.contains("@")) {
                emails.add(email);
            }
        }
        return new ArrayList<>(emails);
    }

    private String chooseBestEmail(Set<String> emails, String host) {
        String bestEmail = "";
        int bestScore = Integer.MIN_VALUE;
        for (String email : emails) {
            int score = scoreEmail(email, host);
            if (score > bestScore) {
                bestScore = score;
                bestEmail = email;
            }
        }
        return bestEmail;
    }

    private int scoreEmail(String email, String host) {
        int score = 0;
        if (email.endsWith("@" + host)) {
            score += 10;
        }
        if (!isFreeMail(email)) {
            score += 3;
        }
        String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        if (!List.of("info", "contact", "sales", "support", "service").contains(local)) {
            score += 2;
        }
        return score;
    }

    private String findContactPage(String baseUrl, Document document) {
        for (Element link : document.select("a[href]")) {
            String href = cleanText(link.attr("abs:href"));
            String text = cleanText(link.text()).toLowerCase(Locale.ROOT);
            if (href.isBlank() || !sameHost(baseUrl, href)) {
                continue;
            }
            if (CONTACT_PATH_HINTS.stream().anyMatch(href.toLowerCase(Locale.ROOT)::contains)
                    || CONTACT_TEXT_HINTS.stream().anyMatch(text::contains)) {
                return href;
            }
        }
        return null;
    }

    private String probeContactPage(String baseUrl, int timeoutMs) {
        for (String path : CONTACT_PAGE_FALLBACKS) {
            String candidate = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + path : baseUrl + path;
            try {
                Document document = fetchDocument(candidate, timeoutMs);
                if (document != null) {
                    return candidate;
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private String extractCompanyName(Document document, SearchCandidate candidate, String host) {
        String ogName = metaContent(document, "meta[property=og:site_name]");
        if (!ogName.isBlank()) {
            return ogName;
        }
        String appName = metaContent(document, "meta[name=application-name]");
        if (!appName.isBlank()) {
            return appName;
        }
        String title = simplifyTitle(document.title());
        if (!title.isBlank()) {
            return title;
        }
        String candidateTitle = simplifyTitle(candidate.title());
        if (!candidateTitle.isBlank()) {
            return candidateTitle;
        }
        return host;
    }

    private String extractContactName(Document document, Document contactDocument, String email) {
        String fromContactPage = extractVisibleContactName(contactDocument);
        if (!fromContactPage.isBlank()) {
            return fromContactPage;
        }
        String fromPage = extractVisibleContactName(document);
        if (!fromPage.isBlank()) {
            return fromPage;
        }
        return deriveContactName(email);
    }

    private String extractVisibleContactName(Document document) {
        if (document == null) {
            return "";
        }
        for (Element element : document.select("p, li, div, span, strong")) {
            String text = cleanText(element.text());
            if (looksLikePersonName(text)) {
                return text;
            }
        }
        return "";
    }

    private boolean looksLikePersonName(String text) {
        String normalized = cleanText(text);
        if (normalized.isBlank() || normalized.length() > 40 || normalized.contains("@")) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        boolean titleLike = PERSON_TITLE_HINTS.stream().anyMatch(lower::contains);
        boolean englishName = normalized.matches("(?i)(mr\\.?|ms\\.?|mrs\\.?|dr\\.?)?\\s*[A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,2}");
        return titleLike || englishName;
    }

    private String deriveContactName(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "";
        }
        String local = email.substring(0, email.indexOf('@'));
        if (List.of("info", "contact", "sales", "support", "service").contains(local)) {
            return "";
        }
        return List.of(local.split("[._-]+")).stream()
                .filter(part -> !part.isBlank())
                .map(this::capitalize)
                .collect(Collectors.joining(" "));
    }

    private boolean matchesKeywords(String industry, String keywords, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> keywordHints = buildSearchHints(keywords);
        List<String> industryHints = buildSearchHints(industry);
        boolean hasKeywordHints = !keywordHints.isEmpty();

        boolean industryMatched = industryHints.stream().anyMatch(hint -> containsMeaningfulPhrase(lower, hint));
        if (!hasKeywordHints) {
            return industryMatched || isBroadIndustry(industry);
        }

        boolean keywordMatched = keywordHints.stream().anyMatch(hint -> containsMeaningfulPhrase(lower, hint));

        if (keywordMatched && (industryMatched || isBroadIndustry(industry))) {
            return true;
        }

        return industryMatched && keywordHints.stream().anyMatch(hint -> containsLooseKeywordToken(lower, hint));
    }

    private boolean matchesMarketSignal(String host, String text, String market) {
        if ("ALL".equalsIgnoreCase(market)) {
            return true;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if ("China".equalsIgnoreCase(market) && containsChineseScript(text)) {
            return true;
        }
        return host.endsWith(marketSite(market).replace("site:.", "."))
                || lower.contains(market.toLowerCase(Locale.ROOT))
                || lower.contains(marketAlias(market));
    }

    private boolean looksLikeBlockedContent(String url, String combined) {
        String lowerUrl = cleanText(url).toLowerCase(Locale.ROOT);
        return looksLikeReferenceHost(hostOf(url))
                || EDITORIAL_URL_PATTERNS.stream().anyMatch(lowerUrl::contains)
                || NON_COMPANY_TEXT_HINTS.stream().anyMatch(combined::contains);
    }

    private boolean looksLikeCompanyCandidate(String host, String combined) {
        if (host.isBlank()) {
            return false;
        }
        if (looksLikeReferenceHost(host)) {
            return false;
        }
        boolean companyHint = COMPANY_TEXT_HINTS.stream().anyMatch(combined::contains);
        boolean corporateDomain = host.endsWith(".cn") || host.endsWith(".com.cn") || host.endsWith(".de") || host.endsWith(".us");
        return companyHint || (corporateDomain && hasStrongCompanySignal(host, combined));
    }

    private boolean hasStrongCompanySignal(String host, String combined) {
        if (host.isBlank()) {
            return false;
        }
        if (looksLikeReferenceHost(host)) {
            return false;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        String lowerCombined = combined.toLowerCase(Locale.ROOT);
        return STRONG_COMPANY_HINTS.stream().anyMatch(lowerCombined::contains)
                || lowerHost.contains("-auto")
                || lowerHost.contains("automation")
                || lowerHost.contains("medical")
                || lowerHost.contains("machinery")
                || lowerHost.contains("robot")
                || lowerHost.contains("tech");
    }

    private boolean looksLikeReferenceHost(String host) {
        String normalizedHost = normalizeHost(host);
        if (normalizedHost.isBlank()) {
            return false;
        }
        return REFERENCE_HOST_HINTS.stream().anyMatch(normalizedHost::contains);
    }

    private boolean isBlockedHost(String host) {
        return BLOCKED_HOSTS.stream().anyMatch(blocked -> host.equals(blocked) || host.endsWith("." + blocked));
    }

    private boolean isFreeMail(String email) {
        if (!email.contains("@")) {
            return false;
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        return FREE_MAIL_DOMAINS.contains(domain);
    }

    private boolean isBroadIndustry(String industry) {
        String normalized = normalizeSearchPhrase(industry);
        return normalized.contains("industrial equipment")
                || normalized.contains("\u5de5\u4e1a\u8bbe\u5907")
                || normalized.contains("machinery");
    }

    private String inferCountry(String host, String fallbackMarket) {
        if (host.endsWith(".cn")) {
            return "China";
        }
        if (host.endsWith(".de")) {
            return "Germany";
        }
        if (host.endsWith(".us")) {
            return "USA";
        }
        return fallbackMarket;
    }

    private String normalizeMarket(String market) {
        String trimmed = market.trim();
        if (trimmed.equalsIgnoreCase("all") || "\u5168\u90e8".equals(trimmed)) {
            return "ALL";
        }
        if (trimmed.equalsIgnoreCase("china") || trimmed.equalsIgnoreCase("cn") || "\u4e2d\u56fd".equals(trimmed)) {
            return "China";
        }
        if (trimmed.equalsIgnoreCase("usa") || trimmed.equalsIgnoreCase("us") || trimmed.equalsIgnoreCase("united states") || "\u7f8e\u56fd".equals(trimmed)) {
            return "USA";
        }
        if (trimmed.equalsIgnoreCase("germany") || trimmed.equalsIgnoreCase("de") || "\u5fb7\u56fd".equals(trimmed)) {
            return "Germany";
        }
        return trimmed;
    }

    private String marketAlias(String market) {
        return switch (market) {
            case "China" -> "china";
            case "USA" -> "usa";
            case "Germany" -> "germany";
            default -> market.toLowerCase(Locale.ROOT);
        };
    }

    private String marketSite(String market) {
        return switch (market) {
            case "China" -> "site:.cn";
            case "USA" -> "site:.us";
            case "Germany" -> "site:.de";
            default -> "";
        };
    }

    private String toEnglishHint(String value) {
        String normalized = normalizeInput(value).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.contains("cnc") || normalized.contains("\u673a\u5e8a") || normalized.contains("\u91d1\u5c5e\u52a0\u5de5")) {
            return "cnc machining";
        }
        if (normalized.contains("\u5de5\u4e1a\u81ea\u52a8\u5316")) {
            return "industrial automation";
        }
        if (normalized.contains("\u5de5\u4e1a\u96f6\u90e8\u4ef6") || normalized.contains("\u6736\u4ef6")) {
            return "industrial components";
        }
        if (normalized.contains("\u901a\u7528\u673a\u68b0") || normalized.contains("\u8bbe\u5907")) {
            return "industrial machinery";
        }
        if (normalized.contains("\u6570\u63a7")) {
            return "cnc";
        }
        if (normalized.contains("\u7535\u5b50\u5236\u9020")) {
            return "electronics manufacturing";
        }
        if (normalized.contains("\u533b\u7597\u5668\u68b0")) {
            return "medical equipment";
        }
        if (normalized.contains("\u4f9b\u5e94\u5546")) {
            return "supplier";
        }
        if (normalized.contains("\u8fdb\u53e3\u5546")) {
            return "importer";
        }
        if (normalized.contains("\u7ecf\u9500\u5546")) {
            return "distributor";
        }
        if (normalized.contains("\u5236\u9020\u5546") || normalized.contains("\u5382")) {
            return "manufacturer";
        }
        return normalized;
    }

    private String rootUrlOf(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return url;
            }
            return scheme + "://" + host + "/";
        } catch (IllegalArgumentException exception) {
            return url;
        }
    }

    private String hostOf(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private boolean sameHost(String leftUrl, String rightUrl) {
        return normalizeHost(hostOf(leftUrl)).equals(normalizeHost(hostOf(rightUrl)));
    }

    private String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "";
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
    }

    private String resolveDuckDuckGoUrl(String absoluteHref, String rawHref) {
        String candidate = absoluteHref == null || absoluteHref.isBlank() ? rawHref : absoluteHref;
        if (candidate == null || candidate.isBlank()) {
            return "";
        }

        String query = "";
        try {
            URI uri = URI.create(candidate);
            query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
        } catch (IllegalArgumentException ignored) {
            int queryStart = candidate.indexOf('?');
            if (queryStart >= 0) {
                query = candidate.substring(queryStart + 1);
            }
        }

        for (String part : query.split("&")) {
            if (part.startsWith("uddg=")) {
                return URLDecoder.decode(part.substring(5), StandardCharsets.UTF_8);
            }
        }
        return candidate;
    }

    private String resolveGoogleUrl(String absoluteHref, String rawHref) {
        String candidate = absoluteHref == null || absoluteHref.isBlank() ? rawHref : absoluteHref;
        if (candidate == null || candidate.isBlank()) {
            return "";
        }

        try {
            URI uri = URI.create(candidate);
            String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
            for (String part : query.split("&")) {
                int equalsIndex = part.indexOf('=');
                if (equalsIndex <= 0) {
                    continue;
                }
                String key = part.substring(0, equalsIndex);
                String value = part.substring(equalsIndex + 1);
                if ("q".equalsIgnoreCase(key) || "url".equalsIgnoreCase(key)) {
                    String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
                    if (!decoded.isBlank()) {
                        return decoded;
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
        }

        return candidate;
    }

    private String followRedirectUrl(String url, int timeoutMs) {
        try {
            log.info("Crawler fetch: {}", url);
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .execute();
            return response.url().toString();
        } catch (IOException exception) {
            return url;
        }
    }

    private String metaContent(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : cleanText(element.attr("content"));
    }

    private String simplifyTitle(String title) {
        String normalized = cleanText(title);
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.split("\\s*[\\-|/|｜|–|—]\\s*")[0].trim();
    }

    private String normalizeInput(String value) {
        return fallback(value, "").replaceAll("\\s+", " ").trim();
    }

    private String normalizeSearchPhrase(String value) {
        return normalizeInput(value).toLowerCase(Locale.ROOT);
    }

    private boolean containsMeaningfulPhrase(String haystack, String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return false;
        }

        if (haystack.contains(phrase)) {
            return true;
        }

        String[] tokens = phrase.split("\\s+");
        if (tokens.length <= 1) {
            return haystack.contains(phrase);
        }

        int matchedTokens = 0;
        int expectedTokens = 0;
        for (String token : tokens) {
            if (token.length() < 3) {
                continue;
            }
            expectedTokens++;
            if (haystack.contains(token)) {
                matchedTokens++;
            }
        }

        if (expectedTokens == 0) {
            return false;
        }
        if (expectedTokens <= 2) {
            return matchedTokens >= 1;
        }
        if (expectedTokens <= 5) {
            return matchedTokens >= 2;
        }
        return matchedTokens >= Math.max(2, Math.min(4, (int) Math.ceil(expectedTokens * 0.5)));
    }

    private List<String> buildSearchHints(String value) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        String normalized = normalizeInput(value);
        extractKnownBusinessPhrases(normalized).forEach(hints::add);

        String alias = normalizeInput(toEnglishHint(value));
        if (!alias.isBlank()) {
            hints.add(alias);
        }

        if (!normalized.isBlank() && looksUsefulHint(normalized)) {
            hints.add(normalized);
        }

        for (String token : normalized.split("[\\s,，;；/|]+")) {
            String cleaned = cleanText(token);
            if (cleaned.length() < 2 || SEARCH_STOP_WORDS.contains(cleaned.toLowerCase(Locale.ROOT))) {
                continue;
            }
            hints.add(cleaned);
            if (hints.size() >= 8) {
                break;
            }
        }
        return new ArrayList<>(hints);
    }

    private boolean looksUsefulHint(String normalized) {
        if (normalized.isBlank()) {
            return false;
        }
        if (containsChineseScript(normalized)) {
            return normalized.length() <= 18;
        }
        return normalized.length() <= 40 && normalized.split("\\s+").length <= 4;
    }

    private List<String> extractKnownBusinessPhrases(String text) {
        String normalized = normalizeInput(text).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> phrases = new LinkedHashSet<>();

        if (normalized.contains("机床") || normalized.contains("machine tool")) {
            phrases.add("machine tool");
            phrases.add("机床");
            phrases.add("cnc machine");
        }
        if (normalized.contains("工业设备") || normalized.contains("industrial equipment")) {
            phrases.add("industrial equipment");
            phrases.add("工业设备");
            phrases.add("machinery");
        }
        if (normalized.contains("工业自动化") || normalized.contains("industrial automation")) {
            phrases.add("industrial automation");
            phrases.add("工业自动化");
        }
        if (normalized.contains("自动化设备") || normalized.contains("automation equipment")) {
            phrases.add("automation equipment");
            phrases.add("自动化设备");
        }
        if (normalized.contains("电子制造") || normalized.contains("electronics manufacturing")) {
            phrases.add("electronics manufacturing");
            phrases.add("电子制造");
        }
        if (normalized.contains("电子")) {
            phrases.add("electronics");
            phrases.add("电子");
        }
        if (normalized.contains("手机") || normalized.contains("smartphone")) {
            phrases.add("smartphone");
            phrases.add("手机");
        }
        if (normalized.contains("手表") || normalized.contains("watch")) {
            phrases.add("watch");
            phrases.add("手表");
        }
        if (normalized.contains("配件") || normalized.contains("accessories")) {
            phrases.add("accessories");
            phrases.add("配件");
        }
        if (normalized.contains("智能制造") || normalized.contains("smart manufacturing")) {
            phrases.add("smart manufacturing");
            phrases.add("智能制造");
        }
        if (normalized.contains("数控") || normalized.contains("cnc")) {
            phrases.add("cnc");
            phrases.add("数控");
        }
        if (normalized.contains("制造商") || normalized.contains("manufacturer")) {
            phrases.add("manufacturer");
            phrases.add("制造商");
        }
        if (normalized.contains("供应商") || normalized.contains("supplier")) {
            phrases.add("supplier");
            phrases.add("供应商");
        }

        return new ArrayList<>(phrases);
    }

    private String firstHint(List<String> hints, String fallbackValue) {
        return hints.stream().filter(hint -> !hint.isBlank()).findFirst().orElse(cleanText(fallbackValue));
    }

    private String firstQueryHint(List<String> hints, String fallbackValue) {
        return hints.stream()
                .filter(this::looksUsefulHint)
                .findFirst()
                .orElse(firstHint(hints, fallbackValue));
    }

    private String firstNativeHint(List<String> hints, String fallbackValue) {
        return hints.stream()
                .filter(hint -> !hint.isBlank() && containsChineseScript(hint))
                .findFirst()
                .orElse(cleanText(fallbackValue));
    }

    private boolean containsLooseKeywordToken(String haystack, String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return false;
        }
        for (String token : phrase.split("\\s+")) {
            String normalized = cleanText(token).toLowerCase(Locale.ROOT);
            if (normalized.length() < 3 || SEARCH_STOP_WORDS.contains(normalized)) {
                continue;
            }
            if (haystack.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsChineseScript(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private record InspectedCandidate(SearchCandidate candidate, PageScanResult scanResult) {
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String capitalize(String part) {
        if (part.isBlank()) {
            return part;
        }
        return Character.toUpperCase(part.charAt(0)) + part.substring(1);
    }

    private String joinQuery(String... parts) {
        return List.of(parts).stream()
                .map(this::cleanText)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String fallback(String value, String fallbackValue) {
        return value == null || value.isBlank() ? fallbackValue : value.trim();
    }

    private int normalizePositive(Integer value, int fallbackValue, int minValue) {
        int resolved = value == null || value <= 0 ? fallbackValue : value;
        return Math.max(resolved, minValue);
    }

    private record SearchCandidate(String title, String url, String snippet, String source) {
    }

    private record PageScanResult(String companyName, String website, String country, String contactName, String email, String channel, String fitNote) {
    }

    private static final class SearchSession {
        private final List<WorkflowModels.SearchLogEntry> logs = new ArrayList<>();

        private void log(String message) {
            logs.add(new WorkflowModels.SearchLogEntry(LocalTime.now().format(TIME_FORMATTER), message));
        }

        private List<WorkflowModels.SearchLogEntry> logs() {
            return logs;
        }
    }
}
