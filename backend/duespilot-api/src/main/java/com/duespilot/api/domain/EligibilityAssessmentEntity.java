package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record EligibilityAssessmentEntity(
    @Nullable UUID id,
    UUID runId,
    String gate,
    String status,
    String reasonsJson
) {}
