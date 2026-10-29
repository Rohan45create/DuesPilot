package com.duespilot.api.rate;

import com.duespilot.api.domain.RateReferenceEntity;
import com.duespilot.api.llm.LlmClient;
import com.duespilot.api.llm.LlmProperties;
import com.duespilot.api.repository.RateReferenceRepository;
import com.duespilot.api.web.error.ApiException;
import com.duespilot.core.parser.DateParser;
import com.duespilot.core.rate.RateCandidate;
import com.duespilot.core.rate.RateProvider;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tavily-backed RateProvider implementation per architecture §10.7 and rules R-19, R-57:
 * 1. Queries Tavily with include_domains=["rbi.org.in"] for the current Bank Rate.
 * 2. Extracts candidate (percentage + effective date) with regex first, Nano as fallback.
 * 3. Validates domain allow-list, plausible range (200-2000 bps), and not-in-the-future date.
 * 4. Caches candidates for 24 hours.
 * 5. Failure or low confidence throws RATE_LOOKUP_FAILED (502).
 */
@Service
public class TavilyRateProvider implements RateProvider {

    private static final Logger log = LoggerFactory.getLogger(TavilyRateProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern RATE_REGEX = Pattern.compile(
        "Bank\\s+Rate(?:\\s*[:\\-–—]|\\s+is|\\s+at|\\s+stands\\s+at)?\\s*(\\d{1,2}(?:\\.\\d{1,2})?)\\s*(?:%|percent)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DATE_REGEX = Pattern.compile(
        "(?:effective|with\\s+effect\\s+from|w\\.e\\.f\\.?|dated?|as\\s+on)\\s*([A-Za-z]+\\s+\\d{1,2},\\s*\\d{4}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}|\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4})",
        Pattern.CASE_INSENSITIVE
    );

    public static final List<OfficialGuidanceLink> OFFICIAL_GUIDANCE_LINKS = List.of(
        new OfficialGuidanceLink(
            "MSMED Act, 2006 (Sections 15–25)",
            "https://www.indiacode.nic.in/handle/123456789/2013",
            "indiacode.nic.in",
            "Statutory framework governing mandatory buyer payment periods and 3x Bank Rate compound interest."
        ),
        new OfficialGuidanceLink(
            "MSME Samadhaan Delayed Payment Portal",
            "https://samadhaan.msme.gov.in/",
            "msme.gov.in",
            "Official Ministry of MSME monitoring system for filing delayed payment claims to MSEFC."
        ),
        new OfficialGuidanceLink(
            "Ministry of Micro, Small and Medium Enterprises",
            "https://msme.gov.in/",
            "msme.gov.in",
            "Official portal of the Ministry of MSME, Government of India."
        ),
        new OfficialGuidanceLink(
            "Reserve Bank of India — Current Policy Rates",
            "https://www.rbi.org.in/",
            "rbi.org.in",
            "Official Reserve Bank of India policy rates display."
        )
    );

    private final TavilyProperties properties;
    private final RateReferenceRepository rateReferenceRepository;
    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final RestClient restClient;

    public TavilyRateProvider(
            TavilyProperties properties,
            RateReferenceRepository rateReferenceRepository,
            LlmClient llmClient,
            LlmProperties llmProperties,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.rateReferenceRepository = rateReferenceRepository;
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());

        String baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        this.restClient = restClientBuilder
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public @NonNull RateCandidate getLatestBankRate() {
        // Step 1: Check 24-hour cache
        Optional<RateReferenceEntity> latest = rateReferenceRepository.findLatest();
        if (latest.isPresent()) {
            RateReferenceEntity entity = latest.get();
            Duration age = Duration.between(entity.retrievedAt(), Instant.now());
            if (age.compareTo(Duration.ofHours(24)) < 0) {
                log.info("Returning cached Bank Rate candidate from {} (age: {} hours, confirmed: {})",
                    entity.sourceUrl(), age.toHours(), entity.isConfirmed());
                return new RateCandidate(
                    entity.rateBps(),
                    entity.effectiveOn(),
                    entity.sourceUrl(),
                    entity.isConfirmed(),
                    entity.rawExcerpt()
                );
            }
        }

        // Step 2: Fetch candidate from Tavily Search
        RateCandidate candidate = searchAndExtractCandidate();

        // Step 3: Persist unconfirmed candidate
        RateReferenceEntity entityToSave = new RateReferenceEntity(
            null,
            candidate.sourceUrl(),
            candidate.excerpt() != null ? candidate.excerpt() : "",
            candidate.rateBps(),
            candidate.effectiveDate(),
            Instant.now(),
            null,
            null
        );
        rateReferenceRepository.save(entityToSave);
        log.info("Persisted new Bank Rate candidate: {} bps (effective {}) from {}",
            candidate.rateBps(), candidate.effectiveDate(), candidate.sourceUrl());

        return candidate;
    }

    @Override
    public @NonNull Optional<RateCandidate> getBankRateOn(@NonNull LocalDate date) {
        Optional<RateReferenceEntity> confirmed = rateReferenceRepository.findLatestConfirmed();
        return confirmed.map(e -> new RateCandidate(
            e.rateBps(),
            e.effectiveOn(),
            e.sourceUrl(),
            e.isConfirmed(),
            e.rawExcerpt()
        ));
    }

    public List<OfficialGuidanceLink> getOfficialGuidanceLinks() {
        return OFFICIAL_GUIDANCE_LINKS;
    }

    private RateCandidate searchAndExtractCandidate() {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Tavily API key is missing; cannot perform rate lookup");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "RATE_LOOKUP_FAILED", "Tavily API key is not configured for bank rate lookup");
        }

        String searchUrl = "/search";
        Map<String, Object> requestBody = Map.of(
            "api_key", apiKey,
            "query", "current Bank Rate Reserve Bank of India",
            "include_domains", properties.getIncludeDomains(),
            "search_depth", properties.getSearchDepth(),
            "max_results", properties.getMaxResults()
        );

        String responseBody;
        try {
            responseBody = restClient.post()
                .uri(searchUrl)
                .body(requestBody)
                .retrieve()
                .body(String.class);
        } catch (Exception e) {
            log.error("Tavily search HTTP call failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "RATE_LOOKUP_FAILED", "Tavily search HTTP call failed: " + e.getMessage(), e.getMessage(), e);
        }

        if (responseBody == null || responseBody.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "RATE_LOOKUP_FAILED", "Empty response from Tavily search");
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (Exception e) {
            log.error("Failed to parse Tavily search response JSON", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "RATE_LOOKUP_FAILED", "Invalid JSON from Tavily search", e.getMessage(), e);
        }

        JsonNode resultsNode = root.get("results");
        if (resultsNode == null || !resultsNode.isArray() || resultsNode.isEmpty()) {
            log.warn("Tavily search returned zero results for RBI Bank Rate");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "RATE_LOOKUP_FAILED", "No results returned from rbi.org.in search");
        }

        // Iterate through results to find first valid candidate
        for (JsonNode item : resultsNode) {
            String url = item.has("url") ? item.get("url").asText() : "";
            String content = item.has("content") ? item.get("content").asText() : "";
            String title = item.has("title") ? item.get("title").asText() : "";
            String fullSnippet = (title + " " + content).trim();

            // 1. Validate domain allow-list (Rule R-57)
            if (!isDomainAllowed(url)) {
                log.warn("Skipping Tavily result with non-allowed domain: {}", url);
                continue;
            }

            // 2. Try Regex Extraction first
            ExtractionCandidate extracted = extractViaRegex(fullSnippet);

            // 3. If Regex did not find both rate and date, try Nano fallback
            if (extracted == null) {
                extracted = extractViaNano(fullSnippet);
            }

            if (extracted != null) {
                // 4. Validate plausible range and date not in future
                if (validateCandidate(extracted.rateBps(), extracted.effectiveDate())) {
                    return RateCandidate.unconfirmed(
                        extracted.rateBps(),
                        extracted.effectiveDate(),
                        url,
                        extracted.excerpt() != null ? extracted.excerpt() : fullSnippet.substring(0, Math.min(250, fullSnippet.length()))
                    );
                }
            }
        }

        log.warn("All Tavily results failed rate/date extraction or validation");
        throw new ApiException(HttpStatus.BAD_GATEWAY, "RATE_LOOKUP_FAILED", "Unable to extract a verified RBI Bank Rate candidate from search results");
    }

    boolean isDomainAllowed(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase();
            for (String allowed : properties.getIncludeDomains()) {
                String allowedLower = allowed.toLowerCase();
                if (host.equals(allowedLower) || host.endsWith("." + allowedLower)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    boolean validateCandidate(int rateBps, LocalDate effectiveDate) {
        // Plausible range: 200 bps (2.0%) to 2000 bps (20.0%)
        if (rateBps < 200 || rateBps > 2000) {
            log.warn("Bank Rate candidate {} bps outside plausible range [200, 2000]", rateBps);
            return false;
        }
        // Date not in the future
        if (effectiveDate.isAfter(LocalDate.now())) {
            log.warn("Bank Rate effective date {} is in the future", effectiveDate);
            return false;
        }
        return true;
    }

    private ExtractionCandidate extractViaRegex(String text) {
        Matcher rateMatcher = RATE_REGEX.matcher(text);
        if (!rateMatcher.find()) {
            return null;
        }

        double rateVal;
        try {
            rateVal = Double.parseDouble(rateMatcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        int rateBps = (int) Math.round(rateVal * 100);

        Matcher dateMatcher = DATE_REGEX.matcher(text);
        LocalDate effectiveDate = null;
        String dateStr = null;
        if (dateMatcher.find()) {
            dateStr = dateMatcher.group(1);
            effectiveDate = DateParser.parse(dateStr).orNull();
        }

        // If date regex didn't find a date, look for any ISO date or standard format in text
        if (effectiveDate == null) {
            Matcher fallbackDateMatcher = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(text);
            if (fallbackDateMatcher.find()) {
                effectiveDate = DateParser.parse(fallbackDateMatcher.group(1)).orNull();
            }
        }

        if (effectiveDate == null) {
            // Missing effective date -> fallback to Nano
            return null;
        }

        String excerpt = rateMatcher.group(0) + (dateStr != null ? " (" + dateStr + ")" : "");
        return new ExtractionCandidate(rateBps, effectiveDate, excerpt);
    }

    private ExtractionCandidate extractViaNano(String text) {
        try {
            var nanoConfig = llmProperties.getModel("extract");
            var prompt = String.format("""
                Extract the Reserve Bank of India (RBI) Bank Rate percentage and its effective date from this text snippet.
                Text snippet:
                \"\"\"
                %s
                \"\"\"
                Output ONLY a JSON object with this exact schema:
                {
                  "rate_percent": 6.50,
                  "effective_date": "YYYY-MM-DD",
                  "confidence": 0.95
                }
                If the Bank Rate is not clearly stated, return {"confidence": 0.0}.
                """, text.substring(0, Math.min(text.length(), 2000)));

            var req = LlmClient.LlmRequest.builder()
                .task("extract")
                .model(nanoConfig.id())
                .messages(List.of(
                    new LlmClient.Message("system", "You are a financial rate extractor. Output valid JSON only."),
                    new LlmClient.Message("user", prompt)
                ))
                .maxTokens(nanoConfig.defaultMaxTokens())
                .temperature(0.0)
                .build();

            var resp = llmClient.chat(req);
            if (!resp.hasContent()) {
                return null;
            }

            String cleanedJson = com.duespilot.api.llm.JsonValidator.cleanMarkdownCodeBlocks(resp.content());
            JsonNode json = MAPPER.readTree(cleanedJson);
            double confidence = json.has("confidence") ? json.get("confidence").asDouble() : 0.0;
            if (confidence < 0.70 || !json.has("rate_percent") || !json.has("effective_date")) {
                return null;
            }

            double ratePct = json.get("rate_percent").asDouble();
            int rateBps = (int) Math.round(ratePct * 100);
            String dateText = json.get("effective_date").asText();
            LocalDate date = DateParser.parse(dateText).orNull();

            if (date == null) {
                return null;
            }

            return new ExtractionCandidate(rateBps, date, "Extracted by Nano: " + ratePct + "% effective " + date);

        } catch (Exception e) {
            log.warn("Nano extraction fallback failed or encountered error", e);
            return null;
        }
    }

    private record ExtractionCandidate(int rateBps, LocalDate effectiveDate, String excerpt) {}
}
