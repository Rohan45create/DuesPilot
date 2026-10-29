package com.duespilot.api.web.dto;

import org.jspecify.annotations.Nullable;

public record ConfirmRateRequest(
    @Nullable String confirmedBy
) {}
