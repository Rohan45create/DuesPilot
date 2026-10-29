package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;

public record AuditEventEntity(
    UUID id,
    @Nullable UUID caseId,
    String actor,
    String eventType,
    String payload,
    Instant createdAt
) {}
