package com.duespilot.api.rate;

import com.duespilot.api.domain.RateReferenceEntity;
import com.duespilot.api.llm.LlmClient;
import com.duespilot.api.llm.LlmProperties;
import com.duespilot.api.repository.RateReferenceRepository;
import com.duespilot.api.web.error.ApiException;
import com.duespilot.core.rate.RateCandidate;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TavilyRateProvider WireMock & Logic Tests (Architecture §10.7 & Rules R-19, R-57)")
class TavilyRateProviderTest {

    private WireMockServer wireMockServer;

    @Mock
    private RateReferenceRepository rateReferenceRepository;

    @Mock
    private LlmClient llmClient;

    private TavilyProperties properties;
    private LlmProperties llmProperties;
    private TavilyRateProvider rateProvider;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();

        properties = new TavilyProperties();
        properties.setBaseUrl(wireMockServer.baseUrl());
        properties.setApiKey("test-tavily-key");
        properties.setTimeoutMs(2000);
        properties.setIncludeDomains(List.of("rbi.org.in"));
        properties.setSearchDepth("advanced");
        properties.setMaxResults(5);

        llmProperties = new LlmProperties();

        rateProvider = new TavilyRateProvider(
            properties,
            rateReferenceRepository,
            llmClient,
            llmProperties,
            RestClient.builder()
        );
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Regex extraction succeeds on RBI snippet without invoking LLM")
    void testExtractViaRegexSuccess() {
        when(rateReferenceRepository.findLatest()).thenReturn(Optional.empty());
        when(rateReferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String tavilyJson = """
            {
              "results": [
                {
                  "title": "Monetary Policy Statement 2023-24",
                  "url": "https://www.rbi.org.in/scripts/BS_PressReleaseDisplay.aspx?prid=57291",
                  "content": "Reserve Bank of India Monetary Policy Committee. The Bank Rate is 6.50% effective February 8, 2024.",
                  "score": 0.98
                }
              ]
            }
            """;

        wireMockServer.stubFor(post(urlEqualTo("/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(tavilyJson)));

        RateCandidate candidate = rateProvider.getLatestBankRate();

        assertThat(candidate).isNotNull();
        assertThat(candidate.rateBps()).isEqualTo(650);
        assertThat(candidate.effectiveDate()).isEqualTo(LocalDate.of(2024, 2, 8));
        assertThat(candidate.sourceUrl()).isEqualTo("https://www.rbi.org.in/scripts/BS_PressReleaseDisplay.aspx?prid=57291");
        assertThat(candidate.isConfirmed()).isFalse();

        // Verify entity persisted
        ArgumentCaptor<RateReferenceEntity> captor = ArgumentCaptor.forClass(RateReferenceEntity.class);
        verify(rateReferenceRepository).save(captor.capture());
        assertThat(captor.getValue().rateBps()).isEqualTo(650);
        assertThat(captor.getValue().effectiveOn()).isEqualTo(LocalDate.of(2024, 2, 8));

        // Verify Nano LLM was not called
        verify(llmClient, never()).chat(any());
    }

    @Test
    @DisplayName("Nano fallback is invoked when regex cannot parse date from snippet")
    void testExtractViaNanoFallbackWhenRegexFails() {
        when(rateReferenceRepository.findLatest()).thenReturn(Optional.empty());
        when(rateReferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String tavilyJson = """
            {
              "results": [
                {
                  "title": "RBI Policy Resolution Update",
                  "url": "https://rbi.org.in/press/rates",
                  "content": "RBI MPC outcome: Bank Rate stands revised at 6.75% decided during the monetary meeting.",
                  "score": 0.91
                }
              ]
            }
            """;

        wireMockServer.stubFor(post(urlEqualTo("/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(tavilyJson)));

        // Mock Nano returning JSON
        String llmContent = """
            ```json
            {
              "rate_percent": 6.75,
              "effective_date": "2024-04-05",
              "confidence": 0.95
            }
            ```
            """;
        when(llmClient.chat(any())).thenReturn(new LlmClient.LlmResponse(
            "nvidia/nemotron-nano-3-30b-a3b",
            llmContent,
            new LlmClient.Usage(100, 30, 0, 150),
            150,
            "sha256-mock",
            LlmClient.LlmOutcome.OK
        ));

        RateCandidate candidate = rateProvider.getLatestBankRate();

        assertThat(candidate).isNotNull();
        assertThat(candidate.rateBps()).isEqualTo(675);
        assertThat(candidate.effectiveDate()).isEqualTo(LocalDate.of(2024, 4, 5));
        assertThat(candidate.sourceUrl()).isEqualTo("https://rbi.org.in/press/rates");

        verify(llmClient).chat(any());
        verify(rateReferenceRepository).save(any());
    }

    @Test
    @DisplayName("Domain allow-list filters non-RBI domains (Rule R-57)")
    void testDomainAllowListFiltersDisallowedDomains() {
        when(rateReferenceRepository.findLatest()).thenReturn(Optional.empty());
        when(rateReferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String tavilyJson = """
            {
              "results": [
                {
                  "title": "Third-Party Financial Blog",
                  "url": "https://unauthorized-finance-blog.com/rbi-bank-rate",
                  "content": "Bank Rate is 6.50% effective February 8, 2024.",
                  "score": 0.99
                },
                {
                  "title": "Official Reserve Bank of India",
                  "url": "https://rbi.org.in/press/official-rate",
                  "content": "Bank Rate is 6.50% effective February 8, 2024.",
                  "score": 0.85
                }
              ]
            }
            """;

        wireMockServer.stubFor(post(urlEqualTo("/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(tavilyJson)));

        RateCandidate candidate = rateProvider.getLatestBankRate();

        assertThat(candidate).isNotNull();
        assertThat(candidate.sourceUrl()).isEqualTo("https://rbi.org.in/press/official-rate");
    }

    @Test
    @DisplayName("Candidate with rate outside plausible range (200-2000 bps) is rejected")
    void testPlausibleRangeRejectsOutOfRangeRate() {
        when(rateReferenceRepository.findLatest()).thenReturn(Optional.empty());

        // Rate is 25.00% (2500 bps) - outside 2000 bps ceiling
        String tavilyJson = """
            {
              "results": [
                {
                  "title": "Historical Outlier Data",
                  "url": "https://rbi.org.in/old-rates",
                  "content": "Bank Rate is 25.00% effective February 8, 2024.",
                  "score": 0.95
                }
              ]
            }
            """;

        wireMockServer.stubFor(post(urlEqualTo("/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(tavilyJson)));

        assertThatThrownBy(() -> rateProvider.getLatestBankRate())
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(apiEx.getCode()).isEqualTo("RATE_LOOKUP_FAILED");
            });
    }

    @Test
    @DisplayName("Candidate with future effective date is rejected")
    void testFutureDateIsRejected() {
        when(rateReferenceRepository.findLatest()).thenReturn(Optional.empty());

        String tavilyJson = """
            {
              "results": [
                {
                  "title": "Speculative Future Projections",
                  "url": "https://rbi.org.in/future-projection",
                  "content": "Bank Rate is 6.50% effective December 31, 2099.",
                  "score": 0.95
                }
              ]
            }
            """;

        wireMockServer.stubFor(post(urlEqualTo("/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(tavilyJson)));

        assertThatThrownBy(() -> rateProvider.getLatestBankRate())
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(apiEx.getCode()).isEqualTo("RATE_LOOKUP_FAILED");
            });
    }

    @Test
    @DisplayName("24-hour cache: returns cached candidate without invoking Tavily")
    void test24HourCacheHit() {
        RateReferenceEntity cached = new RateReferenceEntity(
            UUID.randomUUID(),
            "https://www.rbi.org.in/cached-rates",
            "Bank Rate is 6.50%",
            650,
            LocalDate.of(2024, 2, 8),
            Instant.now().minus(Duration.ofHours(3)), // 3 hours ago (< 24h)
            null,
            null
        );

        when(rateReferenceRepository.findLatest()).thenReturn(Optional.of(cached));

        RateCandidate candidate = rateProvider.getLatestBankRate();

        assertThat(candidate).isNotNull();
        assertThat(candidate.rateBps()).isEqualTo(650);
        assertThat(candidate.sourceUrl()).isEqualTo("https://www.rbi.org.in/cached-rates");

        // Verify Tavily HTTP endpoint was not called
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/search")));
    }

    @Test
    @DisplayName("24-hour cache: refreshes candidate when cache is older than 24 hours")
    void test24HourCacheExpired() {
        RateReferenceEntity expired = new RateReferenceEntity(
            UUID.randomUUID(),
            "https://www.rbi.org.in/old-rates",
            "Old Bank Rate 6.50%",
            650,
            LocalDate.of(2024, 2, 8),
            Instant.now().minus(Duration.ofHours(26)), // 26 hours ago (> 24h)
            null,
            null
        );

        when(rateReferenceRepository.findLatest()).thenReturn(Optional.of(expired));
        when(rateReferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String tavilyJson = """
            {
              "results": [
                {
                  "title": "Fresh RBI Rate",
                  "url": "https://rbi.org.in/fresh-rate",
                  "content": "Bank Rate is 6.75% effective October 10, 2024.",
                  "score": 0.99
                }
              ]
            }
            """;

        wireMockServer.stubFor(post(urlEqualTo("/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(tavilyJson)));

        RateCandidate candidate = rateProvider.getLatestBankRate();

        assertThat(candidate).isNotNull();
        assertThat(candidate.rateBps()).isEqualTo(675);
        assertThat(candidate.sourceUrl()).isEqualTo("https://rbi.org.in/fresh-rate");

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/search")));
    }

    @Test
    @DisplayName("Tavily HTTP failure returns 502 RATE_LOOKUP_FAILED")
    void testTavilyHttpFailureThrows502() {
        when(rateReferenceRepository.findLatest()).thenReturn(Optional.empty());

        wireMockServer.stubFor(post(urlEqualTo("/search"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        assertThatThrownBy(() -> rateProvider.getLatestBankRate())
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(apiEx.getCode()).isEqualTo("RATE_LOOKUP_FAILED");
            });
    }

    @Test
    @DisplayName("Empty search results returns 502 RATE_LOOKUP_FAILED")
    void testEmptySearchResultsThrows502() {
        when(rateReferenceRepository.findLatest()).thenReturn(Optional.empty());

        wireMockServer.stubFor(post(urlEqualTo("/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"results\": []}")));

        assertThatThrownBy(() -> rateProvider.getLatestBankRate())
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(apiEx.getCode()).isEqualTo("RATE_LOOKUP_FAILED");
            });
    }

    @Test
    @DisplayName("Official guidance links include msme.gov.in and indiacode references")
    void testOfficialGuidanceLinks() {
        List<OfficialGuidanceLink> links = rateProvider.getOfficialGuidanceLinks();
        assertThat(links).isNotEmpty();
        assertThat(links).anyMatch(l -> l.url().contains("indiacode.nic.in"));
        assertThat(links).anyMatch(l -> l.url().contains("msme.gov.in"));
        assertThat(links).anyMatch(l -> l.url().contains("rbi.org.in"));
    }
}
