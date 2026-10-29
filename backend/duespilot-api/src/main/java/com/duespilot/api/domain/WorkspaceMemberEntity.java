package com.duespilot.api.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberEntity(
    UUID id,
    UUID workspaceId,
    String userId,
    String role,                   // OWNER, MEMBER, VIEWER
    Instant createdAt
) {}
