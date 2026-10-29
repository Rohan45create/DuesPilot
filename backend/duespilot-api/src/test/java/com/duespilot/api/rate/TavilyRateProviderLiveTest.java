package com.duespilot.api.rate;

import com.duespilot.api.llm.LlmClient;
import com.duespilot.api.llm.LlmProperties;
import com.duespilot.api.repository.RateReferenceRepository;
import com.duespilot.core.rate.RateCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TavilyRateProvider Opt-In Live Integration Test")
class TavilyRateProviderLiveTest {

    @Mock
    private RateReferenceRepository rateReferenceRepository;

    @Mock
    private LlmClient llmClient;

    static boolean isLiveTestEnabled() {
        String prop = System.getProperty("duespilot.live-tests");
        String envKey = System.getenv("TAVILY_API_KEY");
        return "true".equalsIgnoreCase(prop) && (envKey != null && !envKey.isBlank());
    }

    @Test
    @EnabledIf("isLiveTestEnabled")
    @DisplayName("Live Tavily search restricted to rbi.org.in extracts valid RBI Bank Rate candidate")
    void testLiveTavilySearchAgainstRbi() {
        String apiKey = System.getenv("TAVILY_API_KEY");

        TavilyProperties properties = new TavilyProperties();
        properties.setBaseUrl("https://api.tavily.com");
        properties.setApiKey(apiKey);
        properties.setTimeoutMs(15000);
        properties.setIncludeDomains(List.of("rbi.org.in"));
        properties.setSearchDepth("advanced");
        properties.setMaxResults(5);

        LlmProperties llmProperties = new LlmProperties();

        TavilyRateProvider provider = new TavilyRateProvider(
            properties,
            rateReferenceRepository,
            llmClient,
            llmProperties,
            RestClient.builder()
        );

        when(rateReferenceRepository.findLatest()).thenReturn(Optional.empty());
        when(rateReferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RateCandidate candidate = provider.getLatestBankRate();

        assertThat(candidate).isNotNull();
        assertThat(candidate.rateBps()).isBetween(200, 2000);
        assertThat(candidate.effectiveDate()).isNotNull();
        assertThat(candidate.effectiveDate()).isBeforeOrEqualTo(LocalDate.now());
        assertThat(candidate.sourceUrl()).contains("rbi.org.in");
        assertThat(candidate.isConfirmed()).isFalse();
    }
}
