package com.duespilot.core.interest;

import com.duespilot.core.model.Paise;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Result of statutory interest computation under Convention C-1.
 *
 * Per architecture.md section 10.6 and rules R-1, R-7, R-20:
 * - Output values are in {@link Paise} (R-1).
 * - Output is always explicitly labelled "PROVISIONAL" (R-7, R-20).
 * - Documents convention C-1, all compounding rests, and effective rate.
 */
public record InterestResult(
    @NonNull Paise principal,
    @NonNull Paise interest,
    @NonNull Paise closingBalance,
    @NonNull List<MonthRest> rests,
    @NonNull String convention,
    @NonNull String statusLabel,
    int effectiveAnnualRateBps,
    @NonNull LocalDate from,
    @NonNull LocalDate to
) {
    public static final String CONVENTION_C1 = "C-1";
    public static final String STATUS_PROVISIONAL = "PROVISIONAL";

    public InterestResult {
        Objects.requireNonNull(principal, "principal cannot be null");
        Objects.requireNonNull(interest, "interest cannot be null");
        Objects.requireNonNull(closingBalance, "closingBalance cannot be null");
        rests = List.copyOf(rests != null ? rests : Collections.emptyList());
        Objects.requireNonNull(convention, "convention cannot be null");
        Objects.requireNonNull(statusLabel, "statusLabel cannot be null");
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
    }

    public boolean isProvisional() {
        return STATUS_PROVISIONAL.equals(statusLabel);
    }
}
