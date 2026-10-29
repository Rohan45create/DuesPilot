package com.duespilot.core.interest;

import com.duespilot.core.model.Paise;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * An individual monthly compounding rest calculation in Convention C-1.
 *
 * Per architecture.md section 10.6:
 * - Records opening balance, interest accrued at this rest, and closing balance.
 * - Distinguishes between full calendar/monthly intervals and final partial months.
 */
public record MonthRest(
    int restIndex,
    @NonNull LocalDate periodStart,
    @NonNull LocalDate periodEnd,
    @NonNull Paise openingBalance,
    @NonNull Paise interestAccrued,
    @NonNull Paise closingBalance,
    boolean isPartialMonth,
    int daysInPeriod
) {
    public MonthRest {
        Objects.requireNonNull(periodStart, "periodStart cannot be null");
        Objects.requireNonNull(periodEnd, "periodEnd cannot be null");
        Objects.requireNonNull(openingBalance, "openingBalance cannot be null");
        Objects.requireNonNull(interestAccrued, "interestAccrued cannot be null");
        Objects.requireNonNull(closingBalance, "closingBalance cannot be null");
        if (restIndex < 1) {
            throw new IllegalArgumentException("restIndex must be >= 1, got: " + restIndex);
        }
        if (daysInPeriod < 0) {
            throw new IllegalArgumentException("daysInPeriod cannot be negative, got: " + daysInPeriod);
        }
    }
}
