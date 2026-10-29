package com.duespilot.core.interest;

import com.duespilot.core.model.Paise;
import com.duespilot.core.rate.RateCandidate;
import com.duespilot.core.statutory.StatutoryParameters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Deterministic interest engine implementing Section 10.6 Convention C-1.
 *
 * Principles:
 * - BigDecimal inside for intermediate compounding math.
 * - {@link Paise} out; no double/float anywhere (Rule R-1).
 * - Convention C-1:
 *     - Full month interest = balance × (multiplier × bank rate) / 12, added at month end.
 *     - Final partial month interest = balance × annual rate × days / 365.
 *     - Rounding HALF_UP to the paisa (scale 0 in paise) at each rest.
 * - Result is always explicitly labelled "PROVISIONAL" (Rules R-7, R-20).
 * - Statutory multiplier comes from statutory.yml only (Rule R-10).
 * - Refuses execution if bank rate is unconfirmed (Rule R-19).
 */
public final class InterestEngine {

    private static final BigDecimal BASIS_POINTS_DIVISOR = BigDecimal.valueOf(10000);
    private static final BigDecimal MONTHS_IN_YEAR = BigDecimal.valueOf(12);
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);

    private InterestEngine() {
    }

    /**
     * Computes provisional statutory interest using statutory parameters loaded from statutory.yml.
     *
     * @param principal undisputed principal amount in Paise
     * @param from interest start date (appointed day / day following due date)
     * @param to interest calculation cutoff / settlement date
     * @param rateCandidate bank rate candidate from an official provider
     * @return provisional {@link InterestResult}
     * @throws IllegalStateException if the bank rate is unconfirmed
     */
    public static InterestResult compute(
        @NonNull Paise principal,
        @NonNull LocalDate from,
        @NonNull LocalDate to,
        @NonNull RateCandidate rateCandidate
    ) {
        StatutoryParameters statutory = StatutoryParameters.loadFromClasspath();
        InterestInput input = InterestInput.of(principal, from, to, rateCandidate, statutory);
        return compute(input);
    }

    /**
     * Computes provisional statutory interest for the given {@link InterestInput}.
     *
     * @param input interest calculation inputs
     * @return provisional {@link InterestResult}
     * @throws IllegalStateException if the bank rate is unconfirmed (Rule R-19)
     */
    public static InterestResult compute(@NonNull InterestInput input) {
        Objects.requireNonNull(input, "input cannot be null");

        // Rule R-19: Interest runs only when bank rate has been confirmed by a human
        if (!input.isRateConfirmed()) {
            throw new IllegalStateException(
                "Refusing interest computation: Bank rate is unconfirmed. "
                + "Rule R-19 requires human confirmation of the RBI Bank Rate before calculating interest."
            );
        }

        LocalDate from = input.from();
        LocalDate to = input.to();
        Paise principal = input.principal();

        int multiplier = input.multiplier();
        int bankRateBps = input.bankRateBps();
        int effectiveRateBps = Math.multiplyExact(multiplier, bankRateBps);

        if (from.equals(to) || principal.isZero()) {
            return new InterestResult(
                principal,
                Paise.ZERO,
                principal,
                List.of(),
                InterestResult.CONVENTION_C1,
                InterestResult.STATUS_PROVISIONAL,
                effectiveRateBps,
                from,
                to
            );
        }

        // Annual rate = (multiplier * bankRateBps) / 10000
        BigDecimal annualRate = BigDecimal.valueOf(effectiveRateBps)
            .divide(BASIS_POINTS_DIVISOR, 10, RoundingMode.HALF_UP);

        // Monthly rate = annualRate / 12 = effectiveRateBps / 120000
        BigDecimal monthlyRate = annualRate
            .divide(MONTHS_IN_YEAR, 16, RoundingMode.HALF_UP);

        LocalDate current = from;
        Paise currentBalance = principal;
        List<MonthRest> rests = new ArrayList<>();
        int restIndex = 1;

        // Step 1: Iterate over full months
        while (true) {
            LocalDate nextMonth = current.plusMonths(1);
            if (!nextMonth.isAfter(to)) {
                // Full month rest
                BigDecimal balanceBd = BigDecimal.valueOf(currentBalance.value());
                BigDecimal fullMonthInterestBd = balanceBd
                    .multiply(monthlyRate)
                    .setScale(0, RoundingMode.HALF_UP);

                long interestPaiseVal = fullMonthInterestBd.longValueExact();
                Paise interestPaise = Paise.ofPaise(interestPaiseVal);
                Paise newBalance = currentBalance.plus(interestPaise);
                int days = (int) ChronoUnit.DAYS.between(current, nextMonth);

                rests.add(new MonthRest(
                    restIndex++,
                    current,
                    nextMonth,
                    currentBalance,
                    interestPaise,
                    newBalance,
                    false,
                    days
                ));

                currentBalance = newBalance;
                current = nextMonth;
            } else {
                break;
            }
        }

        // Step 2: Final partial month if days remain
        if (current.isBefore(to)) {
            int partialDays = (int) ChronoUnit.DAYS.between(current, to);
            if (partialDays > 0) {
                // final partial month = balance × annual rate × days / 365
                BigDecimal balanceBd = BigDecimal.valueOf(currentBalance.value());
                BigDecimal partialInterestBd = balanceBd
                    .multiply(annualRate)
                    .multiply(BigDecimal.valueOf(partialDays))
                    .divide(DAYS_IN_YEAR, 0, RoundingMode.HALF_UP);

                long partialInterestVal = partialInterestBd.longValueExact();
                Paise partialInterestPaise = Paise.ofPaise(partialInterestVal);
                Paise newBalance = currentBalance.plus(partialInterestPaise);

                rests.add(new MonthRest(
                    restIndex,
                    current,
                    to,
                    currentBalance,
                    partialInterestPaise,
                    newBalance,
                    true,
                    partialDays
                ));

                currentBalance = newBalance;
            }
        }

        Paise totalInterest = currentBalance.minus(principal);

        return new InterestResult(
            principal,
            totalInterest,
            currentBalance,
            rests,
            InterestResult.CONVENTION_C1,
            InterestResult.STATUS_PROVISIONAL,
            effectiveRateBps,
            from,
            to
        );
    }
}
