package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record NotificationOutboxEntity(
    UUID id,
    UUID workspaceId,
    UUID caseId,
    @Nullable UUID actionId,
    String channel,
    String recipient,
    String templateKey,
    String renderedBody,
    String status,
    @Nullable String providerMessageId,
    @Nullable String error,
    @Nullable Instant scheduledFor,
    @Nullable Instant sentAt,
    @Nullable String idempotencyKey,
    Instant createdAt
) {}
