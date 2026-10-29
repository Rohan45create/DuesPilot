package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record ConflictEntity(
    @Nullable UUID id,
    UUID runId,
    String type,
    String detectedBy,
    String severity,
    String summary,
    @Nullable Long amountPaise,
    List<UUID> evidenceFieldIds,
    String status
) {}
