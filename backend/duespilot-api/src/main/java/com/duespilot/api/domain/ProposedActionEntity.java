package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProposedActionEntity(
    @Nullable UUID id,
    UUID runId,
    String actionType,
    String language,
    String draftText,
    List<UUID> evidenceFieldIds,
    String status,
    @Nullable String decidedBy,
    @Nullable Instant decidedAt
) {}
