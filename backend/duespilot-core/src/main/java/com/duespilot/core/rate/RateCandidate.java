package com.duespilot.core.rate;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Candidate Bank Rate reference extracted from official sources (e.g. RBI via Tavily).
 *
 * Per architecture.md section 10.7 and Rule R-19:
 * - Tracks rate in basis points (e.g. 650 for 6.50%), effective date, and source URL.
 * - Human must confirm the candidate before it is marked confirmed.
 * - Interest engine refuses unconfirmed rates.
 */
public record RateCandidate(
    int rateBps,
    @NonNull LocalDate effectiveDate,
    @NonNull String sourceUrl,
    boolean isConfirmed,
    @Nullable String excerpt
) {
    public RateCandidate {
        Objects.requireNonNull(effectiveDate, "effectiveDate cannot be null");
        Objects.requireNonNull(sourceUrl, "sourceUrl cannot be null");
        if (rateBps <= 0) {
            throw new IllegalArgumentException("Bank rate must be positive, got: " + rateBps + " bps");
        }
    }

    public static RateCandidate unconfirmed(
        int rateBps,
        @NonNull LocalDate effectiveDate,
        @NonNull String sourceUrl,
        @Nullable String excerpt
    ) {
        return new RateCandidate(rateBps, effectiveDate, sourceUrl, false, excerpt);
    }

    public static RateCandidate confirmed(
        int rateBps,
        @NonNull LocalDate effectiveDate,
        @NonNull String sourceUrl,
        @Nullable String excerpt
    ) {
        return new RateCandidate(rateBps, effectiveDate, sourceUrl, true, excerpt);
    }

    public RateCandidate withConfirmation(boolean confirmed) {
        return new RateCandidate(rateBps, effectiveDate, sourceUrl, confirmed, excerpt);
    }
}
