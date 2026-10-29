package com.duespilot.api.web.dto;

public record MeResponse(
    UserProfileResponse user,
    WorkspaceResponse workspace,
    FlagsResponse flags
) {}
