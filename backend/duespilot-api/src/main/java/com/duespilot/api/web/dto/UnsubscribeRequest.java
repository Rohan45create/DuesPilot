package com.duespilot.api.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

public record UnsubscribeRequest(
    @JsonProperty("reason") @Nullable String reason
) {}
