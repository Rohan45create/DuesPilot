package com.duespilot.api.llm;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface LlmClient {

    record Message(String role, String content) {
        public static Message system(String content) { return new Message("system", content); }
        public static Message user(String content) { return new Message("user", content); }
        public static Message assistant(String content) { return new Message("assistant", content); }
    }

    record LlmRequest(
        String task,
        String model,
        List<Message> messages,
        int maxTokens,
        double temperature,
        @Nullable String responseFormatJsonType,
        @Nullable UUID runId,
        @Nullable UUID benchmarkRunId
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String task = "extract";
            private String model;
            private List<Message> messages = List.of();
            private int maxTokens = 1024;
            private double temperature = 0.0;
            private String responseFormatJsonType = "json_object";
            private UUID runId;
            private UUID benchmarkRunId;

            public Builder task(String task) { this.task = task; return this; }
            public Builder model(String model) { this.model = model; return this; }
            public Builder messages(List<Message> messages) { this.messages = messages; return this; }
            public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
            public Builder temperature(double temperature) { this.temperature = temperature; return this; }
            public Builder responseFormatJsonType(String format) { this.responseFormatJsonType = format; return this; }
            public Builder runId(UUID runId) { this.runId = runId; return this; }
            public Builder benchmarkRunId(UUID benchmarkRunId) { this.benchmarkRunId = benchmarkRunId; return this; }

            public LlmRequest build() {
                return new LlmRequest(task, model, messages, maxTokens, temperature, responseFormatJsonType, runId, benchmarkRunId);
            }
        }
    }

    record Usage(
        int inputTokens,
        int outputTokens,
        int reasoningTokens,
        long costMicros
    ) {}

    record LlmResponse(
        String model,
        @Nullable String content,
        Usage usage,
        int latencyMs,
        String promptSha256,
        LlmOutcome outcome
    ) {
        public boolean hasContent() {
            return content != null && !content.isBlank();
        }
    }

    enum LlmOutcome {
        OK,
        EMPTY_CONTENT,
        INVALID_JSON,
        HTTP_ERROR,
        TIMEOUT
    }

    LlmResponse chat(LlmRequest request);
}
