package com.duespilot.api.web;

import com.duespilot.api.config.DuesPilotProperties;
import com.duespilot.api.llm.LlmProperties;
import com.duespilot.api.repository.LlmCallRepository;
import com.duespilot.api.repository.LlmCallRepository.ModelUsageRecord;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller providing model usage statistics and cost meter metrics per Architecture section 12 & 18.
 * Endpoint: GET /api/v1/usage/models
 */
@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final LlmCallRepository llmCallRepository;
    private final LlmProperties llmProperties;
    private final DuesPilotProperties duesPilotProperties;

    public UsageController(
            LlmCallRepository llmCallRepository,
            LlmProperties llmProperties,
            DuesPilotProperties duesPilotProperties) {
        this.llmCallRepository = llmCallRepository;
        this.llmProperties = llmProperties;
        this.duesPilotProperties = duesPilotProperties;
    }

    public record ModelUsageResponse(
        @JsonProperty("model") String model,
        @JsonProperty("calls") long calls,
        @JsonProperty("input_tokens") long inputTokens,
        @JsonProperty("output_tokens") long outputTokens,
        @JsonProperty("reasoning_tokens") long reasoningTokens,
        @JsonProperty("total_cost_micros") long totalCostMicros,
        @JsonProperty("avg_latency_ms") int avgLatencyMs
    ) {
        public static ModelUsageResponse fromRecord(ModelUsageRecord r) {
            return new ModelUsageResponse(
                r.model(),
                r.calls(),
                r.inputTokens(),
                r.outputTokens(),
                r.reasoningTokens(),
                r.totalCostMicros(),
                r.avgLatencyMs()
            );
        }
    }

    public record UsageSummaryResponse(
        @JsonProperty("models") List<ModelUsageResponse> models,
        @JsonProperty("total_calls") long totalCalls,
        @JsonProperty("total_cost_micros") long totalCostMicros,
        @JsonProperty("daily_cost_micros") long dailyCostMicros,
        @JsonProperty("daily_cost_cap_micros") long dailyCostCapMicros,
        @JsonProperty("per_case_cost_cap_micros") long perCaseCostCapMicros,
        @JsonProperty("demo_mode") String demoMode
    ) {}

    @GetMapping(value = "/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UsageSummaryResponse> getModelUsage() {
        List<ModelUsageRecord> records = llmCallRepository.findUsageSummaryByModel();
        List<ModelUsageResponse> modelResponses = records.stream()
            .map(ModelUsageResponse::fromRecord)
            .toList();

        long totalCalls = llmCallRepository.findTotalCallsAllTime();
        long totalCostMicros = llmCallRepository.findTotalCostAllTime();
        long dailyCostMicros = llmCallRepository.findTotalCostToday();

        UsageSummaryResponse response = new UsageSummaryResponse(
            modelResponses,
            totalCalls,
            totalCostMicros,
            dailyCostMicros,
            llmProperties.getDailyCostCapMicros(),
            llmProperties.getPerCaseCostCapMicros(),
            duesPilotProperties.getDemoMode()
        );

        return ResponseEntity.ok(response);
    }
}
