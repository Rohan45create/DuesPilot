package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;
import java.time.Instant;

public record UserEntity(
    String id,                     // Supabase auth.users UUID (token sub)
    @Nullable String email,
    Instant createdAt
) {}
