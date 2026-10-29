package com.duespilot.api.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

public record CreateCaseRequest(
    @Nullable String code,
    @NotBlank(message = "Title is required") String title,
    @JsonProperty("supplier_name") @JsonAlias("supplierName") @Nullable String supplierName,
    @JsonProperty("buyer_name") @JsonAlias("buyerName") @Nullable String buyerName,
    @JsonProperty("supplier_category") @JsonAlias("supplierCategory") @Nullable String supplierCategory,
    @JsonProperty("udyam_registered_on") @JsonAlias("udyamRegisteredOn") @Nullable LocalDate udyamRegisteredOn
) {}
