package com.zijianxin.website.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiCompletionService {

    private static final Logger log = LoggerFactory.getLogger(AiCompletionService.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiCompletionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public String complete(SettingsModels.AiSettings settings, String systemPrompt, String userPrompt) {
        validateConfiguration(settings);

        String requestUrl = resolveRequestUrl(settings);
        Map<String, Object> payload = buildPayload(settings, requestUrl, systemPrompt, userPrompt);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(requestUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json");

        if (isAzureEndpoint(settings, requestUrl)) {
            requestBuilder.header("api-key", settings.apiKey().trim());
        } else {
            requestBuilder.header("Authorization", "Bearer " + settings.apiKey().trim());
        }

        try {
            String body = objectMapper.writeValueAsString(payload);
            log.info("AI request -> provider={}, model={}, url={}", settings.provider(), settings.model(), requestUrl);

            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("AI response <- status={}", response.statusCode());
            log.debug("AI raw response: {}", response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(extractErrorMessage(response.body(), response.statusCode()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                contentNode = root.path("choices").path(0).path("text");
            }
            if ((contentNode.isMissingNode() || contentNode.isNull()) && root.hasNonNull("output_text")) {
                contentNode = root.get("output_text");
            }
            String content = extractContent(contentNode);
            if (content.isBlank()) {
                throw new IllegalStateException("AI returned an empty response.");
            }
            return content.trim();
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new IllegalStateException("AI request timed out. Please check whether the current machine can reach the AI gateway.", exception);
        } catch (java.net.ConnectException exception) {
            throw new IllegalStateException("Failed to connect to the AI gateway. Please check the configured Base URL and network connectivity.", exception);
        } catch (IOException exception) {
            log.error("Failed to parse AI response payload", exception);
            throw new IllegalStateException("Failed to parse AI response.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI request was interrupted.", exception);
        } catch (Exception exception) {
            log.error("Unexpected AI request error", exception);
            throw exception;
        }
    }

    private void validateConfiguration(SettingsModels.AiSettings settings) {
        if (settings == null) {
            throw new IllegalStateException("AI settings are missing. Please configure AI first.");
        }
        if (settings.apiKey() == null || settings.apiKey().isBlank()) {
            throw new IllegalStateException("Please configure API KEY on the AI settings page.");
        }
        if ((settings.baseUrl() == null || settings.baseUrl().isBlank()) && defaultBaseUrl(settings.provider()).isBlank()) {
            throw new IllegalStateException("Please configure Base URL on the AI settings page.");
        }
        if (settings.model() == null || settings.model().isBlank()) {
            throw new IllegalStateException("Please configure model on the AI settings page.");
        }
    }

    private String resolveRequestUrl(SettingsModels.AiSettings settings) {
        String raw = settings.baseUrl() == null || settings.baseUrl().isBlank()
                ? defaultBaseUrl(settings.provider())
                : settings.baseUrl().trim();

        if (raw.isBlank()) {
            return raw;
        }

        if (raw.contains("/chat/completions")) {
            return raw;
        }

        if (raw.contains("/deployments/") && raw.contains("api-version=")) {
            return raw;
        }

        if (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return raw + "/chat/completions";
    }

    private boolean isAzureEndpoint(SettingsModels.AiSettings settings, String requestUrl) {
        String provider = settings.provider() == null ? "" : settings.provider().trim();
        return "Azure OpenAI".equalsIgnoreCase(provider)
                || requestUrl.contains(".openai.azure.com")
                || requestUrl.contains("/openai/deployments/");
    }

    private boolean isQueqiaoEndpoint(String requestUrl) {
        return requestUrl.contains("queqiao.online");
    }

    private Map<String, Object> buildPayload(
            SettingsModels.AiSettings settings,
            String requestUrl,
            String systemPrompt,
            String userPrompt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();

        if (isQueqiaoEndpoint(requestUrl)) {
            payload.put("model", settings.model());
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", List.of(Map.of("type", "text", "text", systemPrompt))),
                    Map.of("role", "user", "content", List.of(Map.of("type", "text", "text", userPrompt)))
            ));
            payload.put("max_tokens", 1200);
            return payload;
        }

        if (!isAzureEndpoint(settings, requestUrl)) {
            payload.put("model", settings.model());
        }
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        payload.put("temperature", 0.7);
        return payload;
    }

    private String defaultBaseUrl(String provider) {
        if (provider == null || provider.isBlank()) {
            return "";
        }
        return switch (provider.trim()) {
            case "OpenAI" -> "https://api.openai.com/v1";
            case "Qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "DeepSeek" -> "https://api.deepseek.com/v1";
            case "GLM" -> "https://open.bigmodel.cn/api/paas/v4";
            default -> "";
        };
    }

    private String extractContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            List<String> fragments = new ArrayList<>();
            contentNode.forEach(item -> {
                if (item.isTextual()) {
                    fragments.add(item.asText());
                } else if (item.hasNonNull("text")) {
                    fragments.add(item.get("text").asText());
                }
            });
            return String.join("", fragments);
        }
        return contentNode.toString();
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("message")) {
                return "AI request failed (" + statusCode + "): " + root.get("message").asText();
            }
            JsonNode errorNode = root.path("error");
            if (errorNode.hasNonNull("message")) {
                return "AI request failed (" + statusCode + "): " + errorNode.get("message").asText();
            }
        } catch (Exception ignored) {
            // Fall back to raw body.
        }
        String fallback = responseBody == null ? "" : responseBody.trim();
        if (fallback.isEmpty()) {
            fallback = "Unknown error";
        }
        return "AI request failed (" + statusCode + "): " + fallback;
    }
}
