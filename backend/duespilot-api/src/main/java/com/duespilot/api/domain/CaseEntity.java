package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CaseEntity(
    UUID id,
    UUID workspaceId,
    String code,
    String title,
    @Nullable String supplierName,
    @Nullable String buyerName,
    SupplierCategory supplierCategory,
    @Nullable LocalDate udyamRegisteredOn,
    boolean isSeed,
    boolean isDemoData,
    Instant createdAt,
    int version
) {
    public static final UUID DEMO_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public CaseEntity(
        UUID id,
        UUID workspaceId,
        String code,
        String title,
        @Nullable String supplierName,
        @Nullable String buyerName,
        SupplierCategory supplierCategory,
        @Nullable LocalDate udyamRegisteredOn,
        boolean isSeed,
        Instant createdAt,
        int version
    ) {
        this(id, workspaceId, code, title, supplierName, buyerName, supplierCategory, udyamRegisteredOn, isSeed, false, createdAt, version);
    }

    public CaseEntity(
        UUID id,
        String code,
        String title,
        @Nullable String supplierName,
        @Nullable String buyerName,
        SupplierCategory supplierCategory,
        @Nullable LocalDate udyamRegisteredOn,
        boolean isSeed,
        Instant createdAt,
        int version
    ) {
        this(id, DEMO_WORKSPACE_ID, code, title, supplierName, buyerName, supplierCategory, udyamRegisteredOn, isSeed, false, createdAt, version);
    }
}
