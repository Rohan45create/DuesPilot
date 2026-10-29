package com.duespilot.api.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SeedDemoDataResponse(
    @JsonProperty("success") boolean success,
    @JsonProperty("caseCount") int caseCount,
    @JsonProperty("message") String message
) {}
