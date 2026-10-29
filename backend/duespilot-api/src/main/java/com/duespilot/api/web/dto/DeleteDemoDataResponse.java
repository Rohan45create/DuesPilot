package com.duespilot.api.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeleteDemoDataResponse(
    @JsonProperty("success") boolean success,
    @JsonProperty("deletedCount") int deletedCount,
    @JsonProperty("message") String message
) {}
