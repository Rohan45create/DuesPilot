package com.duespilot.api.web.dto;

import com.duespilot.api.domain.WorkspaceEntity;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

public record WorkspaceResponse(
    UUID id,
    String name,
    String businessName,
    String supplierSizeClass,
    @Nullable LocalDate udyamRegistrationDate,
    @Nullable String ownerPhone,
    @Nullable String ownerWhatsapp,
    boolean isDemo
) {
    public static WorkspaceResponse fromEntity(WorkspaceEntity w) {
        return new WorkspaceResponse(
            w.id(),
            w.name(),
            w.name(),
            w.supplierSizeClass().name(),
            w.udyamRegistrationDate(),
            w.ownerPhone(),
            w.ownerWhatsapp(),
            w.isDemo()
        );
    }
}
