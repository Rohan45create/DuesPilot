package com.duespilot.api.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record DemoDataPreviewResponse(
    @JsonProperty("caseCount") @JsonAlias("case_count") int caseCount,
    @JsonProperty("documentCount") @JsonAlias("document_count") int documentCount,
    @JsonProperty("contactCount") @JsonAlias("contact_count") int contactCount,
    @JsonProperty("approximateStorageKb") @JsonAlias("approximate_storage_kb") long approximateStorageKb,
    @JsonProperty("alreadySeeded") @JsonAlias("already_seeded") boolean alreadySeeded
) {}
