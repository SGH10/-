package com.zijianxin.website.workflow;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class WorkflowSearchService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
    private static final int SEARCH_LIMIT = 10;
    private static final int MAX_CANDIDATE_POOL = 40;
    private static final int MAX_CONTACT_PAGES = 5;
    private static final int FETCH_TIMEOUT_MS = 8000;

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "baidu.com", "duckduckgo.com", "google.com", "bing.com",
            "linkedin.com", "facebook.com", "instagram.com", "youtube.com",
            "twitter.com", "x.com", "wikipedia.org", "zhihu.com", "csdn.net"
    );
    private static final Set<String> FREE_MAIL_DOMAINS = Set.of(
            "qq.com", "163.com", "126.com", "yeah.net", "gmail.com", "hotmail.com", "outlook.com", "yahoo.com"
    );
    private static final Set<String> GENERIC_EMAIL_NAMES = Set.of(
            "info", "sales", "contact", "hello", "support", "office", "team", "service", "business", "admin", "news"
    );
    private static final List<String> HARD_BLOCKED_URL_PATTERNS = List.of("/captcha/", "wappass.baidu.com", "passport.baidu.com");
    private static final List<String> EDITORIAL_URL_PATTERNS = List.of(
            "/news/", "newsdetail", "/article/", "/articles/", "/blog/", "/forum/", "/wiki/", "/baike/", "/zhidao/", "/question/", "/answers/", "/post/"
    );
    private static final List<String> NEGATIVE_CONTENT_HINTS = List.of(
            "news", "article", "blog", "forum", "wiki", "encyclopedia", "verification", "captcha", "dictionary", "translation", "translate", "language"
    );
    private static final List<String> PORTAL_HINTS = List.of("directory", "marketplace", "portal", "catalog", "platform", "b2b");
    private static final List<String> CONTACT_PATH_HINTS = List.of(
            "/contact", "/contact-us", "/contacts", "/about", "/about-us", "/aboutus", "/company", "/company-profile",
            "/support", "/service", "/sales", "/team", "/impressum", "/imprint", "/legal", "/inquiry", "/enquiry", "/kontakt"
    );
    private static final List<String> FALLBACK_CONTACT_PATHS = List.of(
            "/contact", "/contact-us", "/contacts", "/about", "/about-us", "/aboutus", "/company", "/company-profile",
            "/support", "/service", "/sales", "/team", "/impressum", "/imprint"
    );
    private static final List<String> CONTACT_TEXT_HINTS = List.of(
            "contact", "contact us", "contacts", "about", "about us", "support", "sales", "service", "inquiry", "enquiry",
            "team", "company profile", "impressum", "imprint", "kontakt", "about company"
    );
    private static final List<String> COMMON_COMPANY_HINTS = List.of(
            "company", "about us", "products", "services", "contact", "manufacturer", "factory", "supplier", "solutions", "impressum", "kontakt"
    );
    private static final List<String> CONTACT_NAME_STOPWORDS = List.of(
            "contact", "support", "sales", "service", "team", "office", "company", "about", "marketing", "privacy", "cookie", "legal", "newsletter"
    );
    private static final Map<String, String> ENGLISH_ALIASES = Map.of(
            "眼镜", "eyewear glasses",
            "机床", "machine tool",
            "数控机床", "cnc machine tool",
            "机械", "machinery",
            "工业设备", "industrial equipment",
            "医疗器械", "medical device"
    );
    private static final Map<String, List<String>> MARKET_KEYWORDS = Map.ofEntries(
            Map.entry("中国", List.of("china", "cn", "中国", "prc")),
            Map.entry("日本", List.of("japan", "jp", "日本")),
            Map.entry("德国", List.of("germany", "de", "deutschland", "german", "德国")),
            Map.entry("法国", List.of("france", "fr", "french", "francais", "francais", "法国")),
            Map.entry("西班牙", List.of("spain", "es", "espana", "espana", "spanish", "西班牙")),
            Map.entry("美国", List.of("united states", "usa", "us", "america", "american", "美国")),
            Map.entry("意大利", List.of("italy", "it", "italia", "italian", "意大利")),
            Map.entry("英国", List.of("united kingdom", "uk", "britain", "england", "british", "英国")),
            Map.entry("荷兰", List.of("netherlands", "nl", "holland", "dutch", "荷兰")),
            Map.entry("波兰", List.of("poland", "pl", "polish", "波兰")),
            Map.entry("土耳其", List.of("turkey", "tr", "turkiye", "turkish", "土耳其")),
            Map.entry("印度", List.of("india", "in", "indian", "印度")),
            Map.entry("韩国", List.of("korea", "kr", "south korea", "korean", "韩国")),
            Map.entry("越南", List.of("vietnam", "vn", "vietnamese", "越南")),
            Map.entry("泰国", List.of("thailand", "th", "thai", "泰国")),
            Map.entry("印度尼西亚", List.of("indonesia", "id", "indonesian", "印尼", "印度尼西亚")),
            Map.entry("巴西", List.of("brazil", "br", "brazilian", "巴西")),
            Map.entry("墨西哥", List.of("mexico", "mx", "mexican", "墨西哥"))
    );

    private volatile WorkflowModels.CustomerSearchResponse lastSearchResponse;

    public WorkflowModels.CustomerSearchResponse getLastSearchResponse() {
        return lastSearchResponse;
    }

    public WorkflowModels.CustomerSearchResponse searchCustomers(WorkflowModels.CustomerSearchRequest request) {
        SearchSession session = new SearchSession();

        String industry = fallback(request.industry(), "工业设备");
        String market = normalizeMarketLabel(fallback(request.market(), "中国"));
        String keywords = fallback(request.keywords(), "机床");
        String companySize = fallback(request.companySize(), "50-200人");

        List<String> queryVariants = buildSearchQueries(industry, market, keywords, companySize);
        session.log("Received search task.");
        session.log("Search strategy: " + String.join(" | ", queryVariants));

        List<SearchCandidate> candidates = fetchSearchCandidates(queryVariants, market, session);
        List<WorkflowModels.CustomerLead> liveLeads = inspectCandidates(candidates, industry, market, keywords, session);

        int emailCount = (int) liveLeads.stream().filter(lead -> !lead.email().isBlank()).count();
        int highMatchCount = (int) liveLeads.stream()
                .filter(lead -> lead.fitNote().contains("email") || lead.fitNote().contains("contact page") || lead.fitNote().contains("company signals"))
                .count();
        int marketCoverage = (int) liveLeads.stream().map(WorkflowModels.CustomerLead::country).distinct().count();

        WorkflowModels.SearchStats stats = new WorkflowModels.SearchStats(
                liveLeads.size(),
                emailCount,
                highMatchCount,
                marketCoverage
        );

        String summary = liveLeads.isEmpty()
                ? "No matching customer results were found from live public web search."
                : "Collected customer leads from live public web sources.";

        session.log("Search finished with " + liveLeads.size() + " leads.");

        WorkflowModels.CustomerSearchResponse response = new WorkflowModels.CustomerSearchResponse(
                summary,
                stats,
                List.copyOf(session.logs()),
                liveLeads
        );
        lastSearchResponse = response;
        return response;
    }

    private List<String> buildSearchQueries(String industry, String market, String keywords, String companySize) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        List<String> keywordVariants = prioritizedVariants(keywords, market);
        List<String> industryVariants = prioritizedVariants(industry, market);
        String companyWords = localizedCompanyWords(market);
        String contactWords = localizedContactWords(market);
        String marketQuery = marketQueryWords(market);
        String marketSite = marketSiteFilter(market);

        String primaryKeyword = keywordVariants.isEmpty() ? keywords : keywordVariants.get(0);
        String secondaryKeyword = keywordVariants.size() > 1 ? keywordVariants.get(1) : primaryKeyword;
        String primaryIndustry = industryVariants.isEmpty() ? industry : industryVariants.get(0);

        if (!marketSite.isBlank()) {
            queries.add(joinQuery(marketSite, primaryKeyword, primaryIndustry, companyWords));
            queries.add(joinQuery(marketSite, primaryKeyword, contactWords));
        }

        queries.add(joinQuery(marketQuery, primaryKeyword, primaryIndustry, companyWords));
        queries.add(joinQuery(marketQuery, primaryKeyword, contactWords));
        queries.add(joinQuery(market, primaryKeyword, primaryIndustry, companyWords));
        queries.add(joinQuery(market, primaryKeyword, contactWords));
        queries.add(joinQuery(primaryKeyword, primaryIndustry, companySize, companyWords));
        queries.add(joinQuery(secondaryKeyword, "manufacturer"));
        return new ArrayList<>(queries);
    }

    private List<SearchCandidate> fetchSearchCandidates(List<String> queryVariants, String market, SearchSession session) {
        List<SearchCandidate> candidates = new ArrayList<>();
        Set<String> seenHosts = new LinkedHashSet<>();
        boolean chineseMarket = "中国".equals(market);

        for (String query : queryVariants) {
            if (candidates.size() >= MAX_CANDIDATE_POOL) {
                break;
            }

            session.log("Trying query: " + query);
            collectCandidatesFromBingRss(query, candidates, seenHosts);
            collectCandidatesFromBingHtml(query, candidates, seenHosts);
            collectCandidatesFromDuckDuckGo(query, candidates, seenHosts);
            if (chineseMarket) {
                collectCandidatesFromBaidu(query, candidates, seenHosts);
            }
        }

        session.log("Collected " + candidates.size() + " candidate websites.");
        return candidates;
    }

    private void collectCandidatesFromBaidu(String query, List<SearchCandidate> candidates, Set<String> seenHosts) {
        if (candidates.size() >= MAX_CANDIDATE_POOL) {
            return;
        }

        String searchUrl = "https://www.baidu.com/s?wd=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            Document document = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .referrer("https://www.baidu.com/")
                    .timeout(FETCH_TIMEOUT_MS)
                    .get();

            if (isVerificationDocument(document)) {
                return;
            }

            for (Element link : document.select("h3 a")) {
                String resolvedUrl = followRedirectUrl(link.absUrl("href"));
                String snippet = cleanText(link.closest("div") == null ? "" : link.closest("div").text());
                addCandidate(candidates, seenHosts, cleanText(link.text()), resolvedUrl, snippet, "Baidu");
                if (candidates.size() >= MAX_CANDIDATE_POOL) {
                    break;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void collectCandidatesFromBingRss(String query, List<SearchCandidate> candidates, Set<String> seenHosts) {
        if (candidates.size() >= MAX_CANDIDATE_POOL) {
            return;
        }

        String searchUrl = "https://www.bing.com/search?format=rss&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            Document document = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .referrer("https://www.bing.com/")
                    .timeout(FETCH_TIMEOUT_MS)
                    .parser(Parser.xmlParser())
                    .get();

            for (Element item : document.select("item")) {
                Element linkElement = item.selectFirst("link");
                if (linkElement == null) {
                    continue;
                }
                addCandidate(
                        candidates,
                        seenHosts,
                        cleanText(item.selectFirst("title") == null ? "" : item.selectFirst("title").text()),
                        cleanText(linkElement.text()),
                        cleanText(item.selectFirst("description") == null ? "" : item.selectFirst("description").text()),
                        "Bing RSS"
                );
                if (candidates.size() >= MAX_CANDIDATE_POOL) {
                    break;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void collectCandidatesFromBingHtml(String query, List<SearchCandidate> candidates, Set<String> seenHosts) {
        if (candidates.size() >= MAX_CANDIDATE_POOL) {
            return;
        }

        String searchUrl = "https://www.bing.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            Document document = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .referrer("https://www.bing.com/")
                    .timeout(FETCH_TIMEOUT_MS)
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
                if (candidates.size() >= MAX_CANDIDATE_POOL) {
                    break;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void collectCandidatesFromDuckDuckGo(String query, List<SearchCandidate> candidates, Set<String> seenHosts) {
        if (candidates.size() >= MAX_CANDIDATE_POOL) {
            return;
        }

        String searchUrl = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            Document document = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .referrer("https://duckduckgo.com/")
                    .timeout(FETCH_TIMEOUT_MS)
                    .get();

            for (Element link : document.select("a.result__a, a.result-link")) {
                String snippet = cleanText(link.closest(".result") == null ? "" : link.closest(".result").select(".result__snippet").text());
                addCandidate(
                        candidates,
                        seenHosts,
                        cleanText(link.text()),
                        resolveDuckDuckGoUrl(link.attr("abs:href"), link.attr("href")),
                        snippet,
                        "DuckDuckGo HTML"
                );
                if (candidates.size() >= MAX_CANDIDATE_POOL) {
                    break;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void addCandidate(List<SearchCandidate> candidates, Set<String> seenHosts, String title, String url, String snippet, String source) {
        if (!isUsefulCandidateUrl(url)) {
            return;
        }
        String host = normalizeHost(hostOf(url));
        if (host.isBlank() || !seenHosts.add(host)) {
            return;
        }
        candidates.add(new SearchCandidate(title, url, snippet, source));
    }

    private List<WorkflowModels.CustomerLead> inspectCandidates(List<SearchCandidate> candidates, String industry, String market, String keywords, SearchSession session) {
        List<WorkflowModels.CustomerLead> leads = new ArrayList<>();
        Set<String> seenDomains = new LinkedHashSet<>();
        int sequence = 1;

        for (SearchCandidate candidate : candidates) {
            if (leads.size() >= SEARCH_LIMIT) {
                break;
            }
            String domain = normalizeHost(hostOf(candidate.url()));
            if (domain.isBlank() || !seenDomains.add(domain)) {
                continue;
            }

            PageScanResult scanResult = inspectWebsite(candidate, industry, market, keywords);
            if (scanResult == null) {
                continue;
            }

            leads.add(new WorkflowModels.CustomerLead(
                    "lead-" + sequence++,
                    scanResult.companyName(),
                    scanResult.website(),
                    scanResult.country(),
                    scanResult.contactName(),
                    scanResult.email(),
                    scanResult.channel(),
                    scanResult.fitNote()
            ));
        }

        return leads;
    }

    private PageScanResult inspectWebsite(SearchCandidate candidate, String industry, String market, String keywords) {
        try {
            Document candidateDocument = fetchDocument(candidate.url());
            if (candidateDocument == null) {
                return null;
            }

            String candidateUrl = candidateDocument.location().isBlank() ? candidate.url() : candidateDocument.location();
            String homepageUrl = rootUrlOf(candidateUrl);
            Document companyDocument = candidateDocument;
            String companyUrl = candidateUrl;

            if (looksLikeNonCompanyContent(candidateUrl, candidateDocument, candidate.title(), candidate.snippet())
                    && !sameUrlIgnoringSlash(candidateUrl, homepageUrl)) {
                Document rootDocument = fetchDocument(homepageUrl);
                if (rootDocument != null && !isVerificationDocument(rootDocument)) {
                    companyDocument = rootDocument;
                    companyUrl = rootDocument.location().isBlank() ? homepageUrl : rootDocument.location();
                }
            }

            if (isVerificationDocument(companyDocument)) {
                return null;
            }

            String companyName = extractCompanyName(companyDocument, candidate);
            List<String> contactPageUrls = findContactPages(companyUrl, companyDocument);
            Set<String> scanUrls = new LinkedHashSet<>();
            scanUrls.add(companyUrl);
            scanUrls.addAll(contactPageUrls);
            addFallbackContactPages(scanUrls, companyUrl);

            Set<String> emails = new LinkedHashSet<>(extractEmails(companyDocument.html()));
            scanCandidatePages(companyUrl, companyDocument, scanUrls, emails);

            String contactPageUrl = findContactPage(companyUrl, companyDocument);
            String email = chooseBestEmail(emails, companyUrl);
            CompanyJudgement judgement = judgeCompanySite(companyUrl, companyName, companyDocument, candidate, market, industry, keywords, email, contactPageUrl);
            if (!judgement.accepted()) {
                return null;
            }

            String contactName = findContactName(companyDocument, emails);
            if (contactName.isBlank()) {
                contactName = deriveContactName(email);
            }
            if (contactName.isBlank()) {
                contactName = "Business Contact";
            }

            return new PageScanResult(
                    companyName,
                    homepageUrl,
                    inferCountry(homepageUrl, companyDocument, market),
                    contactName,
                    email,
                    "Search engine + website",
                    String.join("; ", judgement.reasons())
            );
        } catch (IOException ignored) {
            return null;
        }
    }

    private CompanyJudgement judgeCompanySite(
            String companyUrl,
            String companyName,
            Document document,
            SearchCandidate candidate,
            String market,
            String industry,
            String keywords,
            String email,
            String contactPageUrl
    ) {
        List<String> reasons = new ArrayList<>();
        int score = 0;

        String combinedText = String.join(" ",
                companyName,
                cleanText(document.title()).toLowerCase(Locale.ROOT),
                cleanText(document.text()).toLowerCase(Locale.ROOT),
                cleanText(candidate.title()).toLowerCase(Locale.ROOT),
                cleanText(candidate.snippet()).toLowerCase(Locale.ROOT)
        );
        String host = normalizeHost(hostOf(companyUrl));

        if (host.isBlank()) {
            return new CompanyJudgement(false, List.of("invalid host"));
        }
        if (!matchesMarketSignal(companyUrl, combinedText, market)) {
            return new CompanyJudgement(false, List.of("market mismatch"));
        }

        boolean keywordMatched = containsAny(combinedText, expandTextVariants(keywords, market));
        boolean industryMatched = containsAny(combinedText, expandTextVariants(industry, market));
        if (!keywordMatched && !industryMatched) {
            return new CompanyJudgement(false, List.of("keywords not matched"));
        }

        if (isCompanyLikeName(companyName)) {
            score += 1;
            reasons.add("company name detected");
        }
        if (containsAny(combinedText, companyIdentityWordsForMarket(market))) {
            score += 2;
            reasons.add("company signals");
        }
        if (keywordMatched) {
            score += 2;
            reasons.add("keyword matched");
        }
        if (industryMatched) {
            score += 1;
            reasons.add("industry matched");
        }
        if (contactPageUrl != null) {
            score += 1;
            reasons.add("contact page");
        }
        if (!email.isBlank()) {
            score += isSameDomainEmail(email, host) ? 2 : 1;
            reasons.add("email found");
        }
        if (looksLikeNonCompanyContent(companyUrl, document, candidate.title(), candidate.snippet())) {
            score -= 1;
        }

        return new CompanyJudgement(score >= 2, reasons);
    }

    private Document fetchDocument(String url) throws IOException {
        Connection.Response response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer("https://www.bing.com/")
                .timeout(FETCH_TIMEOUT_MS)
                .followRedirects(true)
                .ignoreContentType(false)
                .ignoreHttpErrors(true)
                .execute();
        if (response.statusCode() >= 400) {
            return null;
        }
        return response.parse();
    }

    private List<String> extractEmails(String html) {
        Set<String> emails = new LinkedHashSet<>();
        if (html == null || html.isBlank()) {
            return List.of();
        }

        Matcher matcher = EMAIL_PATTERN.matcher(html);
        while (matcher.find()) {
            addEmailIfValid(emails, matcher.group());
        }

        Matcher mailtoMatcher = Pattern.compile("mailto:([^\"'?#\\s>]+)", Pattern.CASE_INSENSITIVE).matcher(html);
        while (mailtoMatcher.find()) {
            addEmailIfValid(emails, mailtoMatcher.group(1));
        }

        return new ArrayList<>(emails);
    }

    private String chooseBestEmail(Set<String> emails, String websiteUrl) {
        if (emails.isEmpty()) {
            return "";
        }
        String domain = normalizeHost(hostOf(websiteUrl));
        return emails.stream()
                .sorted(Comparator.comparingInt((String email) -> scoreEmail(email, domain)).reversed())
                .findFirst()
                .orElse("");
    }

    private int scoreEmail(String email, String domain) {
        int score = 0;
        if (email.endsWith("@" + domain)) {
            score += 10;
        }
        String localPart = email.substring(0, email.indexOf('@'));
        if (!GENERIC_EMAIL_NAMES.contains(localPart)) {
            score += 3;
        }
        if (isFreeMail(email)) {
            score -= 3;
        }
        return score;
    }

    private List<String> findContactPages(String baseUrl, Document document) {
        Set<String> matches = new LinkedHashSet<>();
        for (Element link : document.select("a[href]")) {
            String href = link.attr("abs:href");
            String text = cleanText(link.text()).toLowerCase(Locale.ROOT);
            String lowerHref = cleanText(href).toLowerCase(Locale.ROOT);
            if (href.isBlank() || !sameHost(baseUrl, href)) {
                continue;
            }

            boolean hrefMatched = CONTACT_PATH_HINTS.stream().anyMatch(lowerHref::contains);
            boolean textMatched = CONTACT_TEXT_HINTS.stream().anyMatch(text::contains) || text.contains("联系") || text.contains("关于");
            if (hrefMatched || textMatched) {
                matches.add(href);
            }
        }
        return new ArrayList<>(matches);
    }

    private String findContactPage(String baseUrl, Document document) {
        List<String> contactPages = findContactPages(baseUrl, document);
        return contactPages.isEmpty() ? null : contactPages.get(0);
    }

    private void addFallbackContactPages(Set<String> scanUrls, String baseUrl) {
        String rootUrl = rootUrlOf(baseUrl);
        for (String path : FALLBACK_CONTACT_PATHS) {
            scanUrls.add(joinRootUrl(rootUrl, path));
        }
    }

    private void scanCandidatePages(String companyUrl, Document companyDocument, Set<String> scanUrls, Set<String> emails) throws IOException {
        int scanned = 0;
        Set<String> visited = new LinkedHashSet<>();
        for (String scanUrl : scanUrls) {
            if (scanned >= MAX_CONTACT_PAGES) {
                break;
            }
            if (scanUrl == null || scanUrl.isBlank() || !visited.add(scanUrl)) {
                continue;
            }

            Document pageDocument = sameUrlIgnoringSlash(scanUrl, companyUrl) ? companyDocument : fetchDocument(scanUrl);
            scanned++;
            if (pageDocument == null || isVerificationDocument(pageDocument)) {
                continue;
            }
            String actualPageUrl = pageDocument.location().isBlank() ? scanUrl : pageDocument.location();
            if (!sameHost(companyUrl, actualPageUrl)) {
                continue;
            }
            emails.addAll(extractEmails(pageDocument.html()));
        }
    }

    private String joinRootUrl(String rootUrl, String path) {
        if (rootUrl.endsWith("/") && path.startsWith("/")) {
            return rootUrl.substring(0, rootUrl.length() - 1) + path;
        }
        if (!rootUrl.endsWith("/") && !path.startsWith("/")) {
            return rootUrl + "/" + path;
        }
        return rootUrl + path;
    }

    private String extractCompanyName(Document document, SearchCandidate candidate) {
        List<String> options = List.of(
                metaContent(document, "meta[property=og:site_name]"),
                metaContent(document, "meta[name=application-name]"),
                simplifyTitle(document.title()),
                simplifyTitle(candidate.title())
        );
        for (String option : options) {
            if (!option.isBlank()) {
                return option;
            }
        }
        String host = normalizeHost(hostOf(candidate.url()));
        return host.isBlank() ? "Unknown Company" : host;
    }

    private List<String> expandTextVariants(String text, String market) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        String normalized = cleanText(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        variants.add(normalized);
        addAliasVariants(variants, normalized, ENGLISH_ALIASES);
        return new ArrayList<>(variants);
    }

    private List<String> prioritizedVariants(String text, String market) {
        return expandTextVariants(text, market);
    }

    private void addAliasVariants(Set<String> variants, String sourceText, Map<String, String> aliasMap) {
        for (Map.Entry<String, String> entry : aliasMap.entrySet()) {
            if (sourceText.contains(entry.getKey())) {
                variants.add(entry.getValue());
            }
        }
    }

    private String companyQueryWords(String market) {
        return "manufacturer official website supplier";
    }

    private String contactQueryWords(String market) {
        return "contact about us supplier";
    }

    private String localizedCompanyWords(String market) {
        return switch (market) {
            case "法国" -> "entreprise fabricant site officiel";
            case "西班牙" -> "empresa fabricante sitio oficial";
            case "意大利" -> "azienda produttore sito ufficiale";
            case "英国", "美国" -> "company manufacturer official website";
            case "德国" -> "hersteller unternehmen offizielle website";
            case "日本" -> "manufacturer official company website";
            case "中国" -> "company official website manufacturer";
            default -> companyQueryWords(market);
        };
    }

    private String localizedContactWords(String market) {
        return switch (market) {
            case "法国" -> "contact a propos fabricant";
            case "西班牙" -> "contacto sobre nosotros fabricante";
            case "意大利" -> "contatti chi siamo produttore";
            case "英国", "美国" -> "contact about us supplier";
            case "德国" -> "kontakt impressum hersteller";
            default -> contactQueryWords(market);
        };
    }

    private String marketQueryWords(String market) {
        List<String> keywords = MARKET_KEYWORDS.get(market);
        return keywords == null || keywords.isEmpty() ? market : keywords.get(0);
    }

    private String marketSiteFilter(String market) {
        return switch (market) {
            case "中国" -> "site:.cn";
            case "日本" -> "site:.jp";
            case "德国" -> "site:.de";
            case "法国" -> "site:.fr";
            case "西班牙" -> "site:.es";
            case "意大利" -> "site:.it";
            case "英国" -> "site:.uk";
            case "美国" -> "site:.us";
            case "荷兰" -> "site:.nl";
            case "波兰" -> "site:.pl";
            case "土耳其" -> "site:.tr";
            case "印度" -> "site:.in";
            case "韩国" -> "site:.kr";
            case "越南" -> "site:.vn";
            case "泰国" -> "site:.th";
            case "印度尼西亚" -> "site:.id";
            case "巴西" -> "site:.br";
            case "墨西哥" -> "site:.mx";
            default -> "";
        };
    }

    private List<String> companyIdentityWordsForMarket(String market) {
        return List.of("company", "manufacturer", "factory", "products", "about us", "contact us", "company profile");
    }

    private String joinQuery(String... parts) {
        return List.of(parts).stream()
                .map(this::cleanText)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" "));
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

    private String followRedirectUrl(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute();
            return response.url().toString();
        } catch (IOException exception) {
            return url;
        }
    }

    private boolean isUsefulCandidateUrl(String url) {
        String host = normalizeHost(hostOf(url));
        if (host.isBlank()) {
            return false;
        }
        for (String blockedHost : BLOCKED_HOSTS) {
            if (host.equals(blockedHost) || host.endsWith("." + blockedHost)) {
                return false;
            }
        }
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        for (String pattern : HARD_BLOCKED_URL_PATTERNS) {
            if (lowerUrl.contains(pattern)) {
                return false;
            }
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private boolean looksLikeNonCompanyContent(String url, Document document, String candidateTitle, String snippet) {
        String lowerUrl = cleanText(url).toLowerCase(Locale.ROOT);
        String combinedText = (cleanText(candidateTitle) + " " + cleanText(document.title()) + " " + cleanText(snippet) + " " + cleanText(document.text())).toLowerCase(Locale.ROOT);
        for (String pattern : HARD_BLOCKED_URL_PATTERNS) {
            if (lowerUrl.contains(pattern)) {
                return true;
            }
        }
        boolean editorialUrl = EDITORIAL_URL_PATTERNS.stream().anyMatch(lowerUrl::contains);
        boolean negativeContent = containsAny(combinedText, NEGATIVE_CONTENT_HINTS);
        boolean companySignals = containsAny(combinedText, COMMON_COMPANY_HINTS);
        return (editorialUrl || negativeContent) && !companySignals;
    }

    private boolean isVerificationDocument(Document document) {
        String title = cleanText(document.title()).toLowerCase(Locale.ROOT);
        String text = cleanText(document.text()).toLowerCase(Locale.ROOT);
        return title.contains("captcha") || text.contains("captcha");
    }

    private boolean isCompanyLikeName(String companyName) {
        String normalized = cleanText(companyName);
        if (normalized.isBlank() || normalized.length() > 60) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return !containsAny(lower, NEGATIVE_CONTENT_HINTS) && !containsAny(lower, PORTAL_HINTS);
    }

    private boolean matchesMarketSignal(String url, String text, String market) {
        String host = normalizeHost(hostOf(url));
        String lowerText = text.toLowerCase(Locale.ROOT);
        String siteFilter = marketSiteFilter(market);
        if (!siteFilter.isBlank()) {
            String suffix = siteFilter.replace("site:.", ".");
            if (host.endsWith(suffix)) {
                return true;
            }
        }
        List<String> keywords = MARKET_KEYWORDS.get(market);
        return keywords == null || keywords.stream().anyMatch(lowerText::contains);
    }

    private String inferCountry(String url, Document document, String fallbackMarket) {
        String host = normalizeHost(hostOf(url));
        String text = cleanText(document.text()).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : MARKET_KEYWORDS.entrySet()) {
            String market = entry.getKey();
            String filter = marketSiteFilter(market);
            String suffix = filter.isBlank() ? "" : filter.replace("site:.", ".");
            if (!suffix.isBlank() && host.endsWith(suffix)) {
                return market;
            }
            if (entry.getValue().stream().anyMatch(text::contains)) {
                return market;
            }
        }
        return normalizeMarketLabel(fallbackMarket);
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

    private boolean sameUrlIgnoringSlash(String leftUrl, String rightUrl) {
        return cleanText(leftUrl).replaceAll("/+$", "").equalsIgnoreCase(cleanText(rightUrl).replaceAll("/+$", ""));
    }

    private String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "";
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
    }

    private String normalizeMarketLabel(String market) {
        if (market == null || market.isBlank()) {
            return "中国";
        }
        String trimmed = market.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : MARKET_KEYWORDS.entrySet()) {
            if (entry.getKey().equals(trimmed) || entry.getValue().contains(lower)) {
                return entry.getKey();
            }
        }
        return trimmed;
    }

    private String deriveContactName(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "";
        }
        String localPart = email.substring(0, email.indexOf('@'));
        if (GENERIC_EMAIL_NAMES.contains(localPart) || localPart.matches("\\d+")) {
            return "Business Contact";
        }
        return List.of(localPart.split("[._-]+")).stream()
                .filter(part -> !part.isBlank())
                .map(this::capitalize)
                .collect(Collectors.joining(" "));
    }

    private String findContactName(Document document, Set<String> emails) {
        if (document == null) {
            return "";
        }
        for (Element link : document.select("a[href^=mailto:]")) {
            String label = cleanText(link.text());
            if (looksLikeContactName(label)) {
                return label;
            }
        }
        for (String email : emails) {
            String derived = deriveContactName(email);
            if (!derived.isBlank() && !"Business Contact".equals(derived)) {
                return derived;
            }
        }
        return "";
    }

    private boolean looksLikeContactName(String value) {
        String normalized = cleanText(value);
        if (normalized.isBlank() || normalized.length() > 40 || normalized.contains("@")) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (CONTACT_NAME_STOPWORDS.stream().anyMatch(lower::contains)) {
            return false;
        }
        long wordCount = List.of(normalized.split("\\s+")).stream()
                .filter(part -> !part.isBlank())
                .count();
        return wordCount >= 1 && wordCount <= 4;
    }

    private void addEmailIfValid(Set<String> emails, String candidate) {
        String email = cleanText(candidate).toLowerCase(Locale.ROOT);
        if (email.startsWith("mailto:")) {
            email = email.substring("mailto:".length());
        }
        int queryIndex = email.indexOf('?');
        if (queryIndex >= 0) {
            email = email.substring(0, queryIndex);
        }
        if (!email.contains("@")) {
            return;
        }
        if (email.endsWith(".png") || email.endsWith(".jpg") || email.endsWith(".jpeg") || email.endsWith(".webp")) {
            return;
        }
        if (email.contains("example.com") || email.contains("yourdomain")) {
            return;
        }
        emails.add(email);
    }

    private boolean isSameDomainEmail(String email, String host) {
        return !email.isBlank() && email.endsWith("@" + host);
    }

    private boolean isFreeMail(String email) {
        if (email.isBlank() || !email.contains("@")) {
            return false;
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        return FREE_MAIL_DOMAINS.contains(domain);
    }

    private boolean containsAny(String source, List<String> keywords) {
        String normalized = source == null ? "" : source.toLowerCase(Locale.ROOT);
        return keywords.stream()
                .map(keyword -> keyword == null ? "" : keyword.toLowerCase(Locale.ROOT))
                .filter(keyword -> !keyword.isBlank())
                .anyMatch(normalized::contains);
    }

    private String metaContent(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : cleanText(element.attr("content"));
    }

    private String simplifyTitle(String rawTitle) {
        String normalized = cleanText(rawTitle);
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.split("\\s*[\\-|—|–|｜]\\s*")[0].trim();
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

    private String fallback(String value, String fallbackValue) {
        return value == null || value.isBlank() ? fallbackValue : value.trim();
    }

    private record SearchCandidate(String title, String url, String snippet, String source) {
    }

    private record PageScanResult(String companyName, String website, String country, String contactName, String email, String channel, String fitNote) {
    }

    private record CompanyJudgement(boolean accepted, List<String> reasons) {
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
