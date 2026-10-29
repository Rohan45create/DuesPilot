package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ContactEntity(
    UUID id,
    UUID workspaceId,
    @Nullable UUID caseId,
    String buyerName,
    @Nullable String email,
    @Nullable String phone,
    @Nullable String whatsappNumber,
    boolean emailOptIn,
    @Nullable String emailOptInSource,
    @Nullable Instant emailOptInAt,
    boolean whatsappOptIn,
    @Nullable String whatsappOptInSource,
    @Nullable Instant whatsappOptInAt,
    @Nullable Instant unsubscribedAt,
    boolean isDemoData,
    Instant createdAt,
    Instant updatedAt
) {
    public ContactEntity(
        UUID id,
        UUID workspaceId,
        @Nullable UUID caseId,
        String buyerName,
        @Nullable String email,
        @Nullable String phone,
        @Nullable String whatsappNumber,
        boolean emailOptIn,
        @Nullable String emailOptInSource,
        @Nullable Instant emailOptInAt,
        boolean whatsappOptIn,
        @Nullable String whatsappOptInSource,
        @Nullable Instant whatsappOptInAt,
        @Nullable Instant unsubscribedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id, workspaceId, caseId, buyerName, email, phone, whatsappNumber,
            emailOptIn, emailOptInSource, emailOptInAt,
            whatsappOptIn, whatsappOptInSource, whatsappOptInAt,
            unsubscribedAt, false, createdAt, updatedAt
        );
    }
}
