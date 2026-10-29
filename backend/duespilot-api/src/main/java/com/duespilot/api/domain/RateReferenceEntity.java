package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RateReferenceEntity(
    @Nullable UUID id,
    String sourceUrl,
    String rawExcerpt,
    int rateBps,
    LocalDate effectiveOn,
    Instant retrievedAt,
    @Nullable Instant confirmedAt,
    @Nullable String confirmedBy
) {
    public boolean isConfirmed() {
        return confirmedAt != null;
    }
}
