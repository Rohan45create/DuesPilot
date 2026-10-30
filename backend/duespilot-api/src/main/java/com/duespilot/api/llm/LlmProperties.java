package com.duespilot.api.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "duespilot.llm")
public class LlmProperties {

    private String baseUrl = "https://api.tokenfactory.nebius.com/v1";
    private String apiKey = "";
    private int maxInflight = 10;
    private long dailyCostCapMicros = 5_000_000L;
    private long perCaseCostCapMicros = 250_000L;
    private int timeoutMs = 30_000;
    private int maxRetries = 3;
    private Map<String, ModelConfig> models = new HashMap<>();

    public record ModelConfig(
        String id,
        int contextLimit,
        long inputCostPerMillionMicros,
        long outputCostPerMillionMicros,
        int defaultMaxTokens,
        int expandedMaxTokens
    ) {}

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        validateSafeUrl(baseUrl);
        this.baseUrl = baseUrl;
    }

    public static void validateSafeUrl(String url) {
        if (url == null || url.isBlank()) return;
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("169.254.169.254") || lower.contains("metadata.google.internal") || lower.startsWith("file:") || lower.startsWith("ftp:")) {
            throw new IllegalArgumentException("Invalid baseUrl: Cloud metadata or unsafe scheme is rejected to prevent SSRF.");
        }
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getMaxInflight() {
        return maxInflight;
    }

    public void setMaxInflight(int maxInflight) {
        this.maxInflight = maxInflight;
    }

    public long getDailyCostCapMicros() {
        return dailyCostCapMicros;
    }

    public void setDailyCostCapMicros(long dailyCostCapMicros) {
        this.dailyCostCapMicros = dailyCostCapMicros;
    }

    public long getPerCaseCostCapMicros() {
        return perCaseCostCapMicros;
    }

    public void setPerCaseCostCapMicros(long perCaseCostCapMicros) {
        this.perCaseCostCapMicros = perCaseCostCapMicros;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Map<String, ModelConfig> getModels() {
        return models;
    }

    public void setModels(Map<String, ModelConfig> models) {
        this.models = models;
    }

    public ModelConfig getModel(String role) {
        ModelConfig config = models.get(role);
        if (config == null) {
            // Sensible fallback defaults from Section 8 and spike report
            return switch (role) {
                case "extract" -> new ModelConfig("nvidia/nemotron-nano-3-30b-a3b", 32768, 200_000L, 400_000L, 512, 2048);
                case "reason" -> new ModelConfig("nvidia/nemotron-3-super-120b-a12b", 131072, 800_000L, 2_400_000L, 4096, 8192);
                case "adjudicate" -> new ModelConfig("nvidia/nemotron-3-ultra-550b-a55b", 131072, 3_000_000L, 9_000_000L, 4096, 8192);
                default -> new ModelConfig(role, 32768, 1_000_000L, 2_000_000L, 1024, 2048);
            };
        }
        return config;
    }
}
