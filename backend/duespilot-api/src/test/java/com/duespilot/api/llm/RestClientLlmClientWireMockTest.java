package com.duespilot.api.llm;

import com.duespilot.api.domain.LlmCallEntity;
import com.duespilot.api.llm.error.CaseBudgetExceededException;
import com.duespilot.api.llm.error.DailyBudgetExceededException;
import com.duespilot.api.repository.LlmCallRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestClientLlmClient WireMock Integration Tests (Section 8 & Rules R-21 to R-30)")
class RestClientLlmClientWireMockTest {

    private WireMockServer wireMockServer;

    @Mock
    private LlmCallRepository llmCallRepository;

    private LlmProperties properties;
    private RestClientLlmClient llmClient;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();

        properties = new LlmProperties();
        properties.setBaseUrl(wireMockServer.baseUrl());
        properties.setApiKey("test-key-mock");
        properties.setMaxRetries(3);
        properties.setTimeoutMs(1000);
        properties.setMaxInflight(5);
        properties.setDailyCostCapMicros(50_000_000L);
        properties.setPerCaseCostCapMicros(2_000_000L);

        llmClient = new RestClientLlmClient(properties, llmCallRepository, RestClient.builder());
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Successful chat response: parses content, tokens, latency, cost, and logs to llm_calls")
    void successfulChat() {
        String responseJson = """
            {
              "id": "chatcmpl-test-001",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "{\\"invoice_no\\": \\"INV-100\\", \\"amount\\": 45000}"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 80,
                "completion_tokens": 40,
                "total_tokens": 120,
                "completion_tokens_details": {
                  "reasoning_tokens": 15
                }
              }
            }
            """;

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseJson)));

        var request = LlmClient.LlmRequest.builder()
            .task("extract")
            .model("nvidia/nemotron-nano-3-30b-a3b")
            .messages(List.of(
                LlmClient.Message.system("Extract fields."),
                LlmClient.Message.user("Invoice INV-100 for Rs. 45000.")
            ))
            .maxTokens(512)
            .build();

        var response = llmClient.chat(request);

        assertThat(response.outcome()).isEqualTo(LlmClient.LlmOutcome.OK);
        assertThat(response.content()).contains("INV-100");
        assertThat(response.usage().inputTokens()).isEqualTo(80);
        assertThat(response.usage().outputTokens()).isEqualTo(40);
        assertThat(response.usage().reasoningTokens()).isEqualTo(15);
        assertThat(response.promptSha256()).hasSize(64);

        ArgumentCaptor<LlmCallEntity> captor = ArgumentCaptor.forClass(LlmCallEntity.class);
        verify(llmCallRepository).save(captor.capture());
        LlmCallEntity saved = captor.getValue();
        assertThat(saved.model()).isEqualTo("nvidia/nemotron-nano-3-30b-a3b");
        assertThat(saved.outcome()).isEqualTo("OK");
        assertThat(saved.inputTokens()).isEqualTo(80);
    }

    @Test
    @DisplayName("Empty or null content treated as EMPTY_CONTENT and logged (Rule R-23)")
    void emptyContentTreatedAsEmptyContent() {
        // Reasoning consumed entire budget, returning null/empty content
        String responseJson = """
            {
              "id": "chatcmpl-test-002",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": null,
                    "reasoning_content": "Thinking trace consumed tokens..."
                  },
                  "finish_reason": "length"
                }
              ],
              "usage": {
                "prompt_tokens": 100,
                "completion_tokens": 256,
                "total_tokens": 356
              }
            }
            """;

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseJson)));

        var request = LlmClient.LlmRequest.builder()
            .task("extract")
            .model("nvidia/nemotron-nano-3-30b-a3b")
            .messages(List.of(LlmClient.Message.user("Test doc")))
            .maxTokens(256)
            .build();

        var response = llmClient.chat(request);

        assertThat(response.outcome()).isEqualTo(LlmClient.LlmOutcome.EMPTY_CONTENT);
        assertThat(response.content()).isNull();

        ArgumentCaptor<LlmCallEntity> captor = ArgumentCaptor.forClass(LlmCallEntity.class);
        verify(llmCallRepository).save(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo("EMPTY_CONTENT");
    }

    @Test
    @DisplayName("429 Rate Limit: retries with backoff and succeeds on subsequent attempt")
    void retryOn429TooManyRequests() {
        String successJson = """
            {
              "id": "chatcmpl-test-003",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "{\\"status\\": \\"success\\"}"
                  }
                }
              ],
              "usage": {"prompt_tokens": 10, "completion_tokens": 10}
            }
            """;

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .inScenario("Retry 429")
            .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
            .willReturn(aResponse().withStatus(429).withBody("Too Many Requests"))
            .willSetStateTo("First 429"));

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .inScenario("Retry 429")
            .whenScenarioStateIs("First 429")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successJson)));

        var request = LlmClient.LlmRequest.builder()
            .task("extract")
            .model("nvidia/nemotron-nano-3-30b-a3b")
            .messages(List.of(LlmClient.Message.user("Short prompt")))
            .build();

        var response = llmClient.chat(request);

        assertThat(response.outcome()).isEqualTo(LlmClient.LlmOutcome.OK);
        wireMockServer.verify(2, postRequestedFor(urlEqualTo("/chat/completions")));
    }

    @Test
    @DisplayName("5xx Server Error: bounded retries exhausted throws error and logs HTTP_ERROR")
    void boundedRetryExhaustedOn500() {
        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

        var request = LlmClient.LlmRequest.builder()
            .task("extract")
            .model("nvidia/nemotron-nano-3-30b-a3b")
            .messages(List.of(LlmClient.Message.user("Failing prompt")))
            .build();

        assertThatThrownBy(() -> llmClient.chat(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("LLM HTTP error 500");

        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/chat/completions")));

        ArgumentCaptor<LlmCallEntity> captor = ArgumentCaptor.forClass(LlmCallEntity.class);
        verify(llmCallRepository).save(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo("HTTP_ERROR");
    }

    @Test
    @DisplayName("Timeout: bounded retry on read timeout and logs TIMEOUT")
    void boundedRetryOnTimeout() {
        properties.setTimeoutMs(100); // 100ms timeout
        llmClient = new RestClientLlmClient(properties, llmCallRepository, RestClient.builder());

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(500) // delay > timeout
                .withBody("{}")));

        var request = LlmClient.LlmRequest.builder()
            .task("extract")
            .model("nvidia/nemotron-nano-3-30b-a3b")
            .messages(List.of(LlmClient.Message.user("Timing out")))
            .build();

        assertThatThrownBy(() -> llmClient.chat(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("timed out");

        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/chat/completions")));

        ArgumentCaptor<LlmCallEntity> captor = ArgumentCaptor.forClass(LlmCallEntity.class);
        verify(llmCallRepository).save(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo("TIMEOUT");
    }

    @Test
    @DisplayName("Daily cost cap exceeded prevents remote HTTP call (Rule R-29)")
    void dailyCostCapExceeded() {
        when(llmCallRepository.findTotalCostToday()).thenReturn(50_000_000L); // at cap

        var request = LlmClient.LlmRequest.builder()
            .task("extract")
            .model("nvidia/nemotron-nano-3-30b-a3b")
            .messages(List.of(LlmClient.Message.user("Hello")))
            .build();

        assertThatThrownBy(() -> llmClient.chat(request))
            .isInstanceOf(DailyBudgetExceededException.class)
            .hasMessageContaining("Daily LLM budget cap");

        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/chat/completions")));
    }

    @Test
    @DisplayName("Per-case cost cap exceeded prevents remote HTTP call (Rule R-29)")
    void perCaseCostCapExceeded() {
        UUID runId = UUID.randomUUID();
        when(llmCallRepository.findTotalCostToday()).thenReturn(1_000_000L);
        when(llmCallRepository.findTotalCostByRunId(runId)).thenReturn(2_000_000L); // at case cap

        var request = LlmClient.LlmRequest.builder()
            .task("extract")
            .model("nvidia/nemotron-nano-3-30b-a3b")
            .messages(List.of(LlmClient.Message.user("Hello")))
            .runId(runId)
            .build();

        assertThatThrownBy(() -> llmClient.chat(request))
            .isInstanceOf(CaseBudgetExceededException.class)
            .hasMessageContaining("Per-case LLM budget cap");

        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/chat/completions")));
    }
}
