package com.duespilot.core.extract;

import com.duespilot.core.model.Known;
import com.duespilot.core.model.Paise;
import com.duespilot.core.parser.AmountParser;
import com.duespilot.core.parser.DateParser;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic span verifier and field normalizer.
 *
 * Implements docs/architecture.md section 8.4 and rules R-4, R-11, R-26, R-27:
 * - Quote must exist at the stated location (page and character offsets).
 * - Quote must contain the extracted value.
 * - Money and dates must re-parse to the exact same normalised value.
 * - Any failure marks the field as UNVERIFIED.
 * - Pure Java with NO I/O or Spring dependencies (Rule R-33).
 */
public final class SpanVerifier {

    private static final Set<String> CRITICAL_KEYWORDS = Set.of(
        "AMOUNT", "TOTAL", "PRICE", "PAISE", "DATE", "INVOICE", "NUMBER", "REF", "UTR", "SUPPLIER", "BUYER", "PARTY"
    );

    private SpanVerifier() {
    }

    /**
     * Verifies a raw field extraction against page text.
     *
     * @param raw the raw extraction candidate from the model
     * @param pageText text of the page where the field was claimed to be found
     * @param referenceDate optional reference date for relative dates
     * @return {@link VerifiedField} with status {@link FieldStatus#VERIFIED} or {@link FieldStatus#UNVERIFIED}
     */
    public static VerifiedField verify(
            RawExtraction raw,
            @Nullable String pageText,
            @Nullable LocalDate referenceDate) {
        Objects.requireNonNull(raw, "raw extraction cannot be null");

        // 1. Check if page text exists
        if (pageText == null || pageText.isBlank()) {
            return VerifiedField.unverified(
                raw.fieldName(),
                raw.value(),
                raw.pageNo(),
                raw.charStart(),
                raw.charEnd(),
                raw.quote(),
                "Page " + raw.pageNo() + " text is empty or missing"
            );
        }

        // 2. Check if span and quote are present
        if (raw.charStart() == null || raw.charEnd() == null || raw.quote() == null || raw.quote().isBlank()) {
            return VerifiedField.unverified(
                raw.fieldName(),
                raw.value(),
                raw.pageNo(),
                raw.charStart(),
                raw.charEnd(),
                raw.quote(),
                "Missing span coordinates or quote"
            );
        }

        int start = raw.charStart();
        int end = raw.charEnd();

        // 3. Check bounds within page text
        if (start < 0 || end > pageText.length() || start >= end) {
            return VerifiedField.unverified(
                raw.fieldName(),
                raw.value(),
                raw.pageNo(),
                start,
                end,
                raw.quote(),
                "Span [" + start + ", " + end + "] is out of bounds for page length " + pageText.length()
            );
        }

        // 4. Check that quote exists verbatim at stated coordinates
        String actualSlice = pageText.substring(start, end);
        if (!actualSlice.equals(raw.quote()) && !actualSlice.trim().equals(raw.quote().trim())) {
            return VerifiedField.unverified(
                raw.fieldName(),
                raw.value(),
                raw.pageNo(),
                start,
                end,
                raw.quote(),
                "Quote mismatch at span [" + start + ", " + end + "]: expected '"
                    + raw.quote() + "' but found '" + actualSlice + "'"
            );
        }

        // 5. Check value presence
        if (raw.value() == null || raw.value().isBlank()) {
            return VerifiedField.unverified(
                raw.fieldName(),
                null,
                raw.pageNo(),
                start,
                end,
                raw.quote(),
                "Extracted value is null or blank"
            );
        }

        // 6. Verify and re-parse Money fields
        if (isMoneyField(raw.fieldName(), raw.value())) {
            Known<Paise> valPaise = AmountParser.parse(raw.value());
            Known<Paise> quotePaise = AmountParser.parse(raw.quote());

            if (valPaise instanceof Known.Unknown<Paise> unk) {
                return VerifiedField.unverified(
                    raw.fieldName(),
                    raw.value(),
                    raw.pageNo(),
                    start,
                    end,
                    raw.quote(),
                    "Cannot parse money amount from value '" + raw.value() + "': " + unk.reason()
                );
            }
            if (quotePaise instanceof Known.Unknown<Paise> unk) {
                return VerifiedField.unverified(
                    raw.fieldName(),
                    raw.value(),
                    raw.pageNo(),
                    start,
                    end,
                    raw.quote(),
                    "Cannot parse money amount from quote '" + raw.quote() + "': " + unk.reason()
                );
            }

            Paise vp = valPaise.getOrThrow();
            Paise qp = quotePaise.getOrThrow();
            if (!vp.equals(qp)) {
                return VerifiedField.unverified(
                    raw.fieldName(),
                    raw.value(),
                    raw.pageNo(),
                    start,
                    end,
                    raw.quote(),
                    "Amount mismatch: value parses to " + vp.value()
                        + " paise, but quote parses to " + qp.value() + " paise"
                );
            }

            return VerifiedField.verifiedPaise(
                raw.fieldName(),
                raw.value(),
                vp,
                raw.pageNo(),
                start,
                end,
                raw.quote()
            );
        }

        // 7. Verify and re-parse Date fields
        if (isDateField(raw.fieldName(), raw.value(), referenceDate)) {
            Known<LocalDate> valDate = DateParser.parse(raw.value(), referenceDate);
            Known<LocalDate> quoteDate = DateParser.parse(raw.quote(), referenceDate);

            if (valDate instanceof Known.Unknown<LocalDate> unk) {
                return VerifiedField.unverified(
                    raw.fieldName(),
                    raw.value(),
                    raw.pageNo(),
                    start,
                    end,
                    raw.quote(),
                    "Cannot parse date from value '" + raw.value() + "': " + unk.reason()
                );
            }
            if (quoteDate instanceof Known.Unknown<LocalDate> unk) {
                return VerifiedField.unverified(
                    raw.fieldName(),
                    raw.value(),
                    raw.pageNo(),
                    start,
                    end,
                    raw.quote(),
                    "Cannot parse date from quote '" + raw.quote() + "': " + unk.reason()
                );
            }

            LocalDate vd = valDate.getOrThrow();
            LocalDate qd = quoteDate.getOrThrow();
            if (!vd.equals(qd)) {
                return VerifiedField.unverified(
                    raw.fieldName(),
                    raw.value(),
                    raw.pageNo(),
                    start,
                    end,
                    raw.quote(),
                    "Date mismatch: value parses to " + vd
                        + ", but quote parses to " + qd
                );
            }

            return VerifiedField.verifiedDate(
                raw.fieldName(),
                raw.value(),
                vd,
                raw.pageNo(),
                start,
                end,
                raw.quote()
            );
        }

        // 8. General text fields (party names, IDs, GSTIN, numbers)
        String cleanQuote = raw.quote().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        String cleanVal = raw.value().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);

        if (!cleanQuote.contains(cleanVal)) {
            return VerifiedField.unverified(
                raw.fieldName(),
                raw.value(),
                raw.pageNo(),
                start,
                end,
                raw.quote(),
                "Quote '" + raw.quote() + "' does not contain value '" + raw.value() + "'"
            );
        }

        return VerifiedField.verifiedText(
            raw.fieldName(),
            raw.value(),
            raw.pageNo(),
            start,
            end,
            raw.quote()
        );
    }

    /**
     * Batch verification across all pages of a document.
     */
    public static List<VerifiedField> verifyAll(
            List<RawExtraction> extractions,
            Map<Integer, String> pages,
            @Nullable LocalDate referenceDate) {
        List<VerifiedField> result = new ArrayList<>(extractions.size());
        for (RawExtraction raw : extractions) {
            String pageText = pages.get(raw.pageNo());
            result.add(verify(raw, pageText, referenceDate));
        }
        return result;
    }

    /**
     * Checks if a field name is considered a critical field per architecture.md section 8.2:
     * amount, date, invoice number, party.
     */
    public static boolean isCriticalField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String upper = fieldName.toUpperCase(Locale.ROOT);
        for (String kw : CRITICAL_KEYWORDS) {
            if (upper.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMoneyField(String fieldName, String value) {
        String upper = fieldName.toUpperCase(Locale.ROOT);
        if (upper.contains("AMOUNT") || upper.contains("TOTAL") || upper.contains("PRICE")
                || upper.contains("PAISE") || upper.contains("TAX") || upper.contains("TDS")
                || upper.contains("DUE") || upper.contains("BALANCE")) {
            return true;
        }
        return AmountParser.parse(value).isValue();
    }

    private static boolean isDateField(String fieldName, String value, @Nullable LocalDate referenceDate) {
        String upper = fieldName.toUpperCase(Locale.ROOT);
        if (upper.contains("DATE")) {
            return true;
        }
        return DateParser.parse(value, referenceDate).isValue();
    }
}
