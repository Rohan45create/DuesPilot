package com.duespilot.core.extract;

import com.duespilot.core.model.Known;
import com.duespilot.core.model.Paise;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Result of span verification and deterministic normalization for an extracted field.
 *
 * Per architecture.md section 8.4 and rules R-1, R-2, R-4, R-11:
 * - Verified values enter the ledger and downstream reconciliation.
 * - Unverified values are retained for audit and display, but excluded from arithmetic.
 * - All monetary values are normalised to Paise; all dates are normalised to ISO LocalDate.
 */
public record VerifiedField(
    String fieldName,
    @Nullable String valueText,
    Known<Paise> valuePaise,
    Known<LocalDate> valueDate,
    FieldStatus status,
    int pageNo,
    @Nullable Integer charStart,
    @Nullable Integer charEnd,
    @Nullable String quote,
    @Nullable String failureReason
) {
    public boolean isVerified() {
        return status == FieldStatus.VERIFIED;
    }

    public static VerifiedField verifiedText(
            String fieldName,
            String valueText,
            int pageNo,
            int charStart,
            int charEnd,
            String quote) {
        return new VerifiedField(
            fieldName,
            valueText,
            Known.unknown("Not money"),
            Known.unknown("Not a date"),
            FieldStatus.VERIFIED,
            pageNo,
            charStart,
            charEnd,
            quote,
            null
        );
    }

    public static VerifiedField verifiedPaise(
            String fieldName,
            String valueText,
            Paise paise,
            int pageNo,
            int charStart,
            int charEnd,
            String quote) {
        return new VerifiedField(
            fieldName,
            valueText,
            Known.value(paise),
            Known.unknown("Not a date"),
            FieldStatus.VERIFIED,
            pageNo,
            charStart,
            charEnd,
            quote,
            null
        );
    }

    public static VerifiedField verifiedDate(
            String fieldName,
            String valueText,
            LocalDate date,
            int pageNo,
            int charStart,
            int charEnd,
            String quote) {
        return new VerifiedField(
            fieldName,
            valueText,
            Known.unknown("Not money"),
            Known.value(date),
            FieldStatus.VERIFIED,
            pageNo,
            charStart,
            charEnd,
            quote,
            null
        );
    }

    public static VerifiedField unverified(
            String fieldName,
            @Nullable String valueText,
            int pageNo,
            @Nullable Integer charStart,
            @Nullable Integer charEnd,
            @Nullable String quote,
            String reason) {
        return new VerifiedField(
            fieldName,
            valueText,
            Known.unknown(reason),
            Known.unknown(reason),
            FieldStatus.UNVERIFIED,
            pageNo,
            charStart,
            charEnd,
            quote,
            reason
        );
    }

    public static VerifiedField rejected(
            String fieldName,
            @Nullable String valueText,
            int pageNo,
            @Nullable Integer charStart,
            @Nullable Integer charEnd,
            @Nullable String quote,
            String reason) {
        return new VerifiedField(
            fieldName,
            valueText,
            Known.unknown(reason),
            Known.unknown(reason),
            FieldStatus.REJECTED,
            pageNo,
            charStart,
            charEnd,
            quote,
            reason
        );
    }
}
