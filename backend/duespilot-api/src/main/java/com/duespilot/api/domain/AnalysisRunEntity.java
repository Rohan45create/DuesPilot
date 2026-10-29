package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record AnalysisRunEntity(
    @Nullable UUID id,
    UUID caseId,
    String status,
    String routerPolicy,
    long totalCostMicros,
    Instant startedAt,
    @Nullable Instant finishedAt
) {}
