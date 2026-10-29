package com.duespilot.core.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Value object representing money strictly in integer paise (1/100th of an Indian Rupee).
 * Per Rule R-1 and architecture.md section 10.1:
 * - NEVER use float/double for money, interest or rates.
 * - Never BigDecimal in storage.
 * - Rounding/overflow checks use explicit integer math.
 */
public record Paise(long value) implements Comparable<Paise> {

    public static final Paise ZERO = new Paise(0L);

    public static Paise ofPaise(long paise) {
        return new Paise(paise);
    }

    public static Paise ofRupees(long rupees) {
        return new Paise(Math.multiplyExact(rupees, 100L));
    }

    public static Paise ofRupees(@NonNull BigDecimal rupees) {
        Objects.requireNonNull(rupees, "rupees cannot be null");
        return new Paise(rupees.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact());
    }

    public Paise plus(@NonNull Paise other) {
        Objects.requireNonNull(other, "other cannot be null");
        return new Paise(Math.addExact(this.value, other.value));
    }

    public Paise minus(@NonNull Paise other) {
        Objects.requireNonNull(other, "other cannot be null");
        return new Paise(Math.subtractExact(this.value, other.value));
    }

    public Paise multiply(long multiplier) {
        return new Paise(Math.multiplyExact(this.value, multiplier));
    }

    public Paise abs() {
        return new Paise(Math.abs(this.value));
    }

    public boolean isPositive() {
        return value > 0L;
    }

    public boolean isZero() {
        return value == 0L;
    }

    public boolean isNegative() {
        return value < 0L;
    }

    @Override
    public int compareTo(@NonNull Paise other) {
        Objects.requireNonNull(other, "other cannot be null");
        return Long.compare(this.value, other.value);
    }

    /**
     * Formats this amount using standard Indian numbering system grouping:
     * e.g. "1,18,000.00" or "-50,000.00".
     */
    public String toIndianFormat() {
        long absVal = Math.abs(value);
        long rupees = absVal / 100L;
        long paiseRem = absVal % 100L;

        String rStr = Long.toString(rupees);
        StringBuilder sb = new StringBuilder();
        int len = rStr.length();
        if (len <= 3) {
            sb.append(rStr);
        } else {
            int firstGroup = len - 3;
            int remainder = firstGroup % 2;
            int idx = 0;
            if (remainder > 0) {
                sb.append(rStr, 0, remainder);
                sb.append(',');
                idx = remainder;
            }
            while (idx < firstGroup) {
                sb.append(rStr, idx, idx + 2);
                sb.append(',');
                idx += 2;
            }
            sb.append(rStr.substring(firstGroup));
        }

        String formatted = sb.toString() + "." + (paiseRem < 10 ? "0" + paiseRem : Long.toString(paiseRem));
        return value < 0 ? "-" + formatted : formatted;
    }

    /**
     * Plain decimal string representation in rupees: e.g. "118000.00" or "-50000.00".
     */
    public String toPlainRupeesString() {
        long absVal = Math.abs(value);
        long r = absVal / 100L;
        long p = absVal % 100L;
        String sign = value < 0L ? "-" : "";
        return String.format("%s%d.%02d", sign, r, p);
    }

    @Override
    public String toString() {
        return "₹" + toIndianFormat();
    }
}
