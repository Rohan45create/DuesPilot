package com.duespilot.api.web.dto;

import com.duespilot.api.domain.RateReferenceEntity;
import com.duespilot.api.rate.OfficialGuidanceLink;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RateResponse(
    UUID id,
    int rateBps,
    double ratePercent,
    LocalDate effectiveOn,
    String sourceUrl,
    String rawExcerpt,
    Instant retrievedAt,
    @Nullable Instant confirmedAt,
    @Nullable String confirmedBy,
    @JsonProperty("isConfirmed") boolean isConfirmed,
    List<OfficialGuidanceLink> officialGuidanceLinks
) {
    public static RateResponse fromEntity(RateReferenceEntity entity, List<OfficialGuidanceLink> guidanceLinks) {
        double ratePct = ((double) entity.rateBps()) / 100.0;
        return new RateResponse(
            entity.id(),
            entity.rateBps(),
            ratePct,
            entity.effectiveOn(),
            entity.sourceUrl(),
            entity.rawExcerpt(),
            entity.retrievedAt(),
            entity.confirmedAt(),
            entity.confirmedBy(),
            entity.isConfirmed(),
            guidanceLinks
        );
    }
}
