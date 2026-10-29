package com.duespilot.api.web.dto;

import com.duespilot.api.domain.CaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CaseResponse(
    UUID id,
    String code,
    String title,
    @JsonProperty("supplier_name") @Nullable String supplierName,
    @JsonProperty("buyer_name") @Nullable String buyerName,
    @JsonProperty("supplier_category") String supplierCategory,
    @JsonProperty("udyam_registered_on") @Nullable LocalDate udyamRegisteredOn,
    @JsonProperty("is_seed") boolean isSeed,
    @JsonProperty("is_demo_data") boolean isDemoData,
    @JsonProperty("created_at") Instant createdAt,
    int version
) {
    public CaseResponse(
        UUID id,
        String code,
        String title,
        @Nullable String supplierName,
        @Nullable String buyerName,
        String supplierCategory,
        @Nullable LocalDate udyamRegisteredOn,
        boolean isSeed,
        Instant createdAt,
        int version
    ) {
        this(id, code, title, supplierName, buyerName, supplierCategory, udyamRegisteredOn, isSeed, false, createdAt, version);
    }

    public static CaseResponse fromEntity(CaseEntity entity) {
        return new CaseResponse(
            entity.id(),
            entity.code(),
            entity.title(),
            entity.supplierName(),
            entity.buyerName(),
            entity.supplierCategory().name(),
            entity.udyamRegisteredOn(),
            entity.isSeed(),
            entity.isDemoData(),
            entity.createdAt(),
            entity.version()
        );
    }
}
