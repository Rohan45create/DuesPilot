package com.duespilot.api.llm;

import com.duespilot.api.repository.LlmCallRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModelRouter Escalation & WireMock Tests (Section 8.2 & Rules R-23, R-24, R-28)")
class ModelRouterEscalationWireMockTest {

    private WireMockServer wireMockServer;

    @Mock
    private LlmCallRepository llmCallRepository;

    private LlmProperties properties;
    private RestClientLlmClient llmClient;
    private JsonValidator jsonValidator;
    private ModelRouter router;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();

        properties = new LlmProperties();
        properties.setBaseUrl(wireMockServer.baseUrl());
        properties.setApiKey("test-key-mock");
        properties.setMaxRetries(1);
        properties.setTimeoutMs(2000);
        properties.setMaxInflight(10);
        properties.setDailyCostCapMicros(50_000_000L);
        properties.setPerCaseCostCapMicros(5_000_000L);

        llmClient = new RestClientLlmClient(properties, llmCallRepository, RestClient.builder());
        jsonValidator = new JsonValidator();

        ExtractionPolicy extractionPolicy = new ExtractionPolicy(llmClient, properties, llmCallRepository, jsonValidator);
        SemanticConflictPolicy semanticConflictPolicy = new SemanticConflictPolicy(llmClient, properties, llmCallRepository, jsonValidator);
        DraftActionPolicy draftActionPolicy = new DraftActionPolicy(llmClient, properties, jsonValidator);

        router = new ModelRouter(extractionPolicy, semanticConflictPolicy, draftActionPolicy);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Escalation order: Nano B1 (empty) -> Nano B2 (invalid JSON) -> Super (invalid) -> Ultra (success)")
    void escalationOrderNanoToUltra() {
        UUID runId = UUID.randomUUID();
        when(llmCallRepository.countSuccessfulCallsByRunIdAndModel(eq(runId), eq("nvidia/nemotron-3-ultra-550b-a55b")))
            .thenReturn(0L);

        // Call 1: Nano B1 returns empty content
        String nanoB1Empty = """
            {"choices":[{"message":{"content":""}}],"usage":{"prompt_tokens":50,"completion_tokens":512}}
            """;
        // Call 2: Nano B2 returns invalid JSON
        String nanoB2Invalid = """
            {"choices":[{"message":{"content":"Not valid json at all"}}],"usage":{"prompt_tokens":50,"completion_tokens":200}}
            """;
        // Call 3: Super returns broken JSON
        String superInvalid = """
            {"choices":[{"message":{"content":"{ broken: true"}}],"usage":{"prompt_tokens":50,"completion_tokens":100}}
            """;
        // Call 4: Ultra returns valid JSON
        String ultraSuccess = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\\"claims\\": [{\\"id\\": \\"CL-1\\", \\"statement\\": \\"Paid ₹50,000\\"}]}"
                  }
                }
              ],
              "usage": {"prompt_tokens":50, "completion_tokens":150}
            }
            """;

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .inScenario("Escalation")
            .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json; charset=UTF-8")
                .withBody(nanoB1Empty))
            .willSetStateTo("NANO_B2"));

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .inScenario("Escalation")
            .whenScenarioStateIs("NANO_B2")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json; charset=UTF-8")
                .withBody(nanoB2Invalid))
            .willSetStateTo("SUPER"));

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .inScenario("Escalation")
            .whenScenarioStateIs("SUPER")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json; charset=UTF-8")
                .withBody(superInvalid))
            .willSetStateTo("ULTRA"));

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .inScenario("Escalation")
            .whenScenarioStateIs("ULTRA")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json; charset=UTF-8")
                .withBody(ultraSuccess)));

        var result = router.extract(List.of(LlmClient.Message.user("Parse invoice")), runId);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.finalModel()).isEqualTo("nvidia/nemotron-3-ultra-550b-a55b");
        assertThat(result.parsedJson()).isNotNull();
        assertThat(result.parsedJson().get("claims").get(0).get("statement").asText()).isEqualTo("Paid ₹50,000");

        assertThat(result.escalationHistory()).hasSize(4);
        assertThat(result.escalationHistory().get(0)).contains("Nano B1");
        assertThat(result.escalationHistory().get(1)).contains("Nano B1 failed (EMPTY_CONTENT) -> Escalating to Nano B2");
        assertThat(result.escalationHistory().get(2)).contains("Nano B2 failed (INVALID_JSON) -> Escalating to Super");
        assertThat(result.escalationHistory().get(3)).contains("Super failed (INVALID_JSON) -> Escalating to Ultra");
    }

    @Test
    @DisplayName("Enforces one Ultra call per case limit (Rule R-28): blocks second Ultra call")
    void enforcesOneUltraCallPerCaseLimit() {
        UUID runId = UUID.randomUUID();
        // 1 Ultra call already succeeded in this run
        when(llmCallRepository.countSuccessfulCallsByRunIdAndModel(eq(runId), eq("nvidia/nemotron-3-ultra-550b-a55b")))
            .thenReturn(1L);

        // Nano B1 returns empty
        String emptyResponse = """
            {"choices":[{"message":{"content":""}}],"usage":{"prompt_tokens":10,"completion_tokens":10}}
            """;
        // Nano B2 returns empty
        // Super returns empty
        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse().withStatus(200).withBody(emptyResponse)));

        var result = router.extract(List.of(LlmClient.Message.user("Extract")), runId);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.finalModel()).isEqualTo("nvidia/nemotron-3-super-120b-a12b");
        assertThat(result.escalationHistory()).anyMatch(msg -> msg.contains("Ultra limit reached (max 1 per case)"));
    }

    @Test
    @DisplayName("Semantic conflicts: Super triggers Ultra escalation when needs_adjudication is true")
    void semanticConflictEscalatesToUltraWhenAdjudicationNeeded() {
        UUID runId = UUID.randomUUID();
        when(llmCallRepository.countSuccessfulCallsByRunIdAndModel(eq(runId), eq("nvidia/nemotron-3-ultra-550b-a55b")))
            .thenReturn(0L);

        String superNeedsAdjudication = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\\"conflicts\\": [{\\"type\\": \\"C3\\", \\"summary\\": \\"PO terms disputed\\"}], \\"needs_adjudication\\": true}"
                  }
                }
              ],
              "usage": {"prompt_tokens":100, "completion_tokens":100}
            }
            """;

        String ultraResolved = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\\"conflicts\\": [{\\"type\\": \\"C3\\", \\"summary\\": \\"PO terms 45-day statutory limit applies\\"}], \\"needs_adjudication\\": false}"
                  }
                }
              ],
              "usage": {"prompt_tokens":150, "completion_tokens":120}
            }
            """;

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .inScenario("SemanticConflicts")
            .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
            .willReturn(aResponse().withStatus(200).withBody(superNeedsAdjudication))
            .willSetStateTo("ULTRA_ADJUDICATE"));

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .inScenario("SemanticConflicts")
            .whenScenarioStateIs("ULTRA_ADJUDICATE")
            .willReturn(aResponse().withStatus(200).withBody(ultraResolved)));

        var result = router.analyzeSemanticConflicts(List.of(LlmClient.Message.user("Analyze case conflicts")), runId);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.finalModel()).isEqualTo("nvidia/nemotron-3-ultra-550b-a55b");
        assertThat(result.parsedJson().get("needs_adjudication").asBoolean()).isFalse();
        assertThat(result.escalationHistory()).anyMatch(msg -> msg.contains("Adjudication needed -> Escalating to Ultra"));
    }

    @Test
    @DisplayName("JsonValidator: cleans markdown fences and parses JSON (Rule R-24)")
    void jsonValidatorCleansMarkdownFences() {
        String fencedJson = """
            ```json
            {
              "invoice_no": "INV-2024-998",
              "amount": 45000
            }
            ```
            """;

        var node = jsonValidator.validateAndParse(fencedJson);
        assertThat(node).isNotNull();
        assertThat(node.get("invoice_no").asText()).isEqualTo("INV-2024-998");
        assertThat(node.get("amount").asInt()).isEqualTo(45000);
    }
}
