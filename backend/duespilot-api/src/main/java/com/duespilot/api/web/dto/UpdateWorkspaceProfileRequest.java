package com.duespilot.api.web.dto;

import org.jspecify.annotations.Nullable;
import java.time.LocalDate;

public record UpdateWorkspaceProfileRequest(
    @Nullable String businessName,
    @Nullable String supplierSizeClass,
    @Nullable LocalDate udyamRegistrationDate,
    @Nullable String ownerPhone,
    @Nullable String ownerWhatsapp
) {}
