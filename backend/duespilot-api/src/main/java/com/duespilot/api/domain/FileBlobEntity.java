package com.duespilot.api.domain;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record FileBlobEntity(
    @NonNull String sha256,
    byte @Nullable [] bytes,
    @NonNull String mimeType,
    long sizeBytes,
    @NonNull Instant createdAt
) {}
