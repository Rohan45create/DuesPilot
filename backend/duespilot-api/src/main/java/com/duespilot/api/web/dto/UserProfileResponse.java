package com.duespilot.api.web.dto;

import org.jspecify.annotations.Nullable;

public record UserProfileResponse(
    String id,
    @Nullable String email,
    boolean isAnonymous
) {}
