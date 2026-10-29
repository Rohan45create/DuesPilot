package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkspaceEntity(
    UUID id,
    String name,
    SupplierCategory supplierSizeClass,
    @Nullable LocalDate udyamRegistrationDate,
    @Nullable String ownerPhone,
    @Nullable String ownerWhatsapp,
    boolean isDemo,
    Instant createdAt,
    int version
) {}
