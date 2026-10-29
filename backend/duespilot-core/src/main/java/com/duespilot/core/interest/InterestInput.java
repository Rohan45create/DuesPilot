package com.duespilot.core.interest;

import com.duespilot.core.model.Paise;
import com.duespilot.core.rate.RateCandidate;
import com.duespilot.core.statutory.StatutoryParameters;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Input parameters for statutory interest calculation under Section 16 MSMED Act.
 *
 * Per architecture.md section 10.6 and Rule R-19:
 * - Principal in {@link Paise} (R-1).
 * - Multiplier (3x) comes from statutory configuration (R-10).
 * - Bank rate must be confirmed by a human before calculation is permitted (R-19).
 */
public record InterestInput(
    @NonNull Paise principal,
    @NonNull LocalDate from,
    @NonNull LocalDate to,
    int bankRateBps,
    int multiplier,
    boolean isRateConfirmed
) {
    public InterestInput {
        Objects.requireNonNull(principal, "principal cannot be null");
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                String.format("Start date '%s' cannot be after end date '%s'", from, to)
            );
        }
        if (bankRateBps <= 0) {
            throw new IllegalArgumentException("bankRateBps must be positive, got: " + bankRateBps);
        }
        if (multiplier <= 0) {
            throw new IllegalArgumentException("multiplier must be positive, got: " + multiplier);
        }
    }

    public static InterestInput of(
        @NonNull Paise principal,
        @NonNull LocalDate from,
        @NonNull LocalDate to,
        int bankRateBps,
        int multiplier,
        boolean isRateConfirmed
    ) {
        return new InterestInput(principal, from, to, bankRateBps, multiplier, isRateConfirmed);
    }

    public static InterestInput of(
        @NonNull Paise principal,
        @NonNull LocalDate from,
        @NonNull LocalDate to,
        @NonNull RateCandidate candidate,
        @NonNull StatutoryParameters statutoryParameters
    ) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        Objects.requireNonNull(statutoryParameters, "statutoryParameters cannot be null");
        return new InterestInput(
            principal,
            from,
            to,
            candidate.rateBps(),
            statutoryParameters.interestMultiplier(),
            candidate.isConfirmed()
        );
    }
}
