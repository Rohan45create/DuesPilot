package com.duespilot.api.rate;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "duespilot.tavily")
public class TavilyProperties {

    private String baseUrl = "https://api.tavily.com";
    private String apiKey = "";
    private int timeoutMs = 15000;
    private String searchDepth = "advanced";
    private List<String> includeDomains = List.of("rbi.org.in");
    private List<String> guidanceDomains = List.of("msme.gov.in", "indiacode.nic.in");
    private int maxResults = 5;

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

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getSearchDepth() {
        return searchDepth;
    }

    public void setSearchDepth(String searchDepth) {
        this.searchDepth = searchDepth;
    }

    public List<String> getIncludeDomains() {
        return includeDomains;
    }

    public void setIncludeDomains(List<String> includeDomains) {
        this.includeDomains = includeDomains;
    }

    public List<String> getGuidanceDomains() {
        return guidanceDomains;
    }

    public void setGuidanceDomains(List<String> guidanceDomains) {
        this.guidanceDomains = guidanceDomains;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }
}
