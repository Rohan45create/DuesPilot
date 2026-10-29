package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record LlmCallEntity(
    UUID id,
    @Nullable UUID runId,
    @Nullable UUID benchmarkRunId,
    String task,
    String model,
    String promptSha256,
    @Nullable Integer inputTokens,
    @Nullable Integer outputTokens,
    @Nullable Integer reasoningTokens,
    int latencyMs,
    long costMicros,
    String outcome,
    Instant createdAt
) {}
