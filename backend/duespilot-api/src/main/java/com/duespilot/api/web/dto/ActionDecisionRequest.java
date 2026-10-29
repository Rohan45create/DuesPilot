package com.duespilot.api.web.dto;

import org.jspecify.annotations.Nullable;

public record ActionDecisionRequest(
    @Nullable String actor,
    @Nullable String note
) {}
