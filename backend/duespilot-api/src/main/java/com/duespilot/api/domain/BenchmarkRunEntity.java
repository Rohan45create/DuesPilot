package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a benchmark evaluation run per architecture §11 and §14.
 */
public record BenchmarkRunEntity(
    @Nullable UUID id,
    String name,
    String datasetVersion,
    String config,
    Instant startedAt,
    @Nullable Instant finishedAt
) {}
