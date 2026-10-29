package com.duespilot.api.web.dto;

public record FlagsResponse(
    boolean demoReadOnly,
    boolean canCreateCases,
    boolean requiresOnboarding
) {}
