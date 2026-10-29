package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Entity representing an individual case result within a benchmark run per architecture §11 and §14.
 */
public record BenchmarkResultEntity(
    @Nullable UUID id,
    UUID benchmarkRunId,
    String caseCode,
    String metrics,
    boolean passed,
    long costMicros,
    int latencyMs
) {}
