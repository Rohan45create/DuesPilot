package com.duespilot.core.extract;

import com.duespilot.core.model.Paise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpanVerifier Unit Tests (Section 8.4 and Rules R-4, R-11, R-26, R-27)")
class SpanVerifierTest {

    private static final String SAMPLE_INVOICE_PAGE = """
        TAX INVOICE
        Invoice No: INV-2024-001
        Date: 15/02/2024
        Supplier: Acme Precision Components Pvt Ltd
        GSTIN: 27AABCA1234F1Z1
        Buyer: Global Machining Ltd
        Description: CNC Turned Steel Pins
        Total Amount: ₹ 1,47,500.00
        Authorized Signatory
        """;

    @Test
    @DisplayName("Verifies valid text field when quote and coordinates match page text")
    void verifiesValidTextField() {
        int start = SAMPLE_INVOICE_PAGE.indexOf("INV-2024-001");
        int end = start + "INV-2024-001".length();

        RawExtraction raw = new RawExtraction(
            "INVOICE_NUMBER",
            "INV-2024-001",
            1,
            start,
            end,
            "INV-2024-001",
            "HIGH"
        );

        VerifiedField verified = SpanVerifier.verify(raw, SAMPLE_INVOICE_PAGE, null);

        assertThat(verified.isVerified()).isTrue();
        assertThat(verified.status()).isEqualTo(FieldStatus.VERIFIED);
        assertThat(verified.valueText()).isEqualTo("INV-2024-001");
        assertThat(verified.failureReason()).isNull();
    }

    @Test
    @DisplayName("Verifies valid money field and normalises to Paise")
    void verifiesValidMoneyField() {
        String quote = "Total Amount: ₹ 1,47,500.00";
        int start = SAMPLE_INVOICE_PAGE.indexOf(quote);
        int end = start + quote.length();

        RawExtraction raw = new RawExtraction(
            "TOTAL_AMOUNT",
            "₹1,47,500.00",
            1,
            start,
            end,
            quote,
            "HIGH"
        );

        VerifiedField verified = SpanVerifier.verify(raw, SAMPLE_INVOICE_PAGE, null);

        assertThat(verified.isVerified()).isTrue();
        assertThat(verified.valuePaise().isValue()).isTrue();
        assertThat(verified.valuePaise().getOrThrow()).isEqualTo(new Paise(14750000L));
    }

    @Test
    @DisplayName("Verifies valid date field and normalises to ISO LocalDate")
    void verifiesValidDateField() {
        String quote = "Date: 15/02/2024";
        int start = SAMPLE_INVOICE_PAGE.indexOf(quote);
        int end = start + quote.length();

        RawExtraction raw = new RawExtraction(
            "INVOICE_DATE",
            "15/02/2024",
            1,
            start,
            end,
            quote,
            "HIGH"
        );

        VerifiedField verified = SpanVerifier.verify(raw, SAMPLE_INVOICE_PAGE, null);

        assertThat(verified.isVerified()).isTrue();
        assertThat(verified.valueDate().isValue()).isTrue();
        assertThat(verified.valueDate().getOrThrow()).isEqualTo(LocalDate.of(2024, 2, 15));
    }

    @Test
    @DisplayName("Rejects when model invents a value not present in the cited quote")
    void rejectsInventedValue() {
        int start = SAMPLE_INVOICE_PAGE.indexOf("INV-2024-001");
        int end = start + "INV-2024-001".length();

        RawExtraction raw = new RawExtraction(
            "INVOICE_NUMBER",
            "INV-999-HALLUCINATED",
            1,
            start,
            end,
            "INV-2024-001",
            "HIGH"
        );

        VerifiedField verified = SpanVerifier.verify(raw, SAMPLE_INVOICE_PAGE, null);

        assertThat(verified.isVerified()).isFalse();
        assertThat(verified.status()).isEqualTo(FieldStatus.UNVERIFIED);
        assertThat(verified.failureReason()).contains("does not contain value");
    }

    @Test
    @DisplayName("Rejects when model cites a wrong span where text differs from quote")
    void rejectsWrongSpanCoordinates() {
        // Point to "Supplier: Acme..." instead of "INV-2024-001"
        int start = SAMPLE_INVOICE_PAGE.indexOf("Supplier:");
        int end = start + 15;

        RawExtraction raw = new RawExtraction(
            "INVOICE_NUMBER",
            "INV-2024-001",
            1,
            start,
            end,
            "INV-2024-001",
            "HIGH"
        );

        VerifiedField verified = SpanVerifier.verify(raw, SAMPLE_INVOICE_PAGE, null);

        assertThat(verified.isVerified()).isFalse();
        assertThat(verified.status()).isEqualTo(FieldStatus.UNVERIFIED);
        assertThat(verified.failureReason()).contains("Quote mismatch at span");
    }

    @Test
    @DisplayName("Rejects out of bounds coordinates")
    void rejectsOutOfBoundsSpan() {
        RawExtraction raw = new RawExtraction(
            "INVOICE_NUMBER",
            "INV-2024-001",
            1,
            5000,
            5020,
            "INV-2024-001",
            "HIGH"
        );

        VerifiedField verified = SpanVerifier.verify(raw, SAMPLE_INVOICE_PAGE, null);

        assertThat(verified.isVerified()).isFalse();
        assertThat(verified.status()).isEqualTo(FieldStatus.UNVERIFIED);
        assertThat(verified.failureReason()).contains("out of bounds");
    }

    @Test
    @DisplayName("Rejects money amount mismatch between value and quote")
    void rejectsMoneyAmountMismatch() {
        String quote = "Total Amount: ₹ 1,47,500.00";
        int start = SAMPLE_INVOICE_PAGE.indexOf(quote);
        int end = start + quote.length();

        RawExtraction raw = new RawExtraction(
            "TOTAL_AMOUNT",
            "₹ 2,00,000.00", // Mismatched amount
            1,
            start,
            end,
            quote,
            "HIGH"
        );

        VerifiedField verified = SpanVerifier.verify(raw, SAMPLE_INVOICE_PAGE, null);

        assertThat(verified.isVerified()).isFalse();
        assertThat(verified.status()).isEqualTo(FieldStatus.UNVERIFIED);
        assertThat(verified.failureReason()).contains("Amount mismatch");
    }

    @Test
    @DisplayName("Rejects date mismatch between value and quote")
    void rejectsDateMismatch() {
        String quote = "Date: 15/02/2024";
        int start = SAMPLE_INVOICE_PAGE.indexOf(quote);
        int end = start + quote.length();

        RawExtraction raw = new RawExtraction(
            "INVOICE_DATE",
            "2024-12-31", // Mismatched date
            1,
            start,
            end,
            quote,
            "HIGH"
        );

        VerifiedField verified = SpanVerifier.verify(raw, SAMPLE_INVOICE_PAGE, null);

        assertThat(verified.isVerified()).isFalse();
        assertThat(verified.status()).isEqualTo(FieldStatus.UNVERIFIED);
        assertThat(verified.failureReason()).contains("Date mismatch");
    }

    @Test
    @DisplayName("Rejects when page text is missing or page number not found")
    void rejectsMissingPageText() {
        RawExtraction raw = new RawExtraction(
            "INVOICE_NUMBER",
            "INV-2024-001",
            2, // page 2 does not exist
            10,
            22,
            "INV-2024-001",
            "HIGH"
        );

        VerifiedField verified = SpanVerifier.verify(raw, null, null);

        assertThat(verified.isVerified()).isFalse();
        assertThat(verified.status()).isEqualTo(FieldStatus.UNVERIFIED);
        assertThat(verified.failureReason()).contains("Page 2 text is empty or missing");
    }

    @Test
    @DisplayName("Batch verifyAll correctly handles multiple fields across pages")
    void batchVerifyAllAcrossPages() {
        String page1 = "INV-001 dated 2024-01-10 Total ₹50,000";
        Map<Integer, String> pages = Map.of(1, page1);

        var list = java.util.List.of(
            new RawExtraction("INVOICE_NUMBER", "INV-001", 1, 0, 7, "INV-001", "HIGH"),
            new RawExtraction("TOTAL_AMOUNT", "₹50,000", 1, 31, 38, "₹50,000", "HIGH"),
            new RawExtraction("BUYER_NAME", "Acme", 1, 0, 7, "INV-001", "HIGH") // quote doesn't have Acme
        );

        var results = SpanVerifier.verifyAll(list, pages, null);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).isVerified()).isTrue();
        assertThat(results.get(1).isVerified()).isTrue();
        assertThat(results.get(1).valuePaise().getOrThrow()).isEqualTo(new Paise(5000000L));
        assertThat(results.get(2).isVerified()).isFalse();
    }

    @Test
    @DisplayName("Correctly identifies critical fields per section 8.2")
    void identifiesCriticalFields() {
        assertThat(SpanVerifier.isCriticalField("TOTAL_AMOUNT")).isTrue();
        assertThat(SpanVerifier.isCriticalField("invoice_amount")).isTrue();
        assertThat(SpanVerifier.isCriticalField("INVOICE_DATE")).isTrue();
        assertThat(SpanVerifier.isCriticalField("doc_date")).isTrue();
        assertThat(SpanVerifier.isCriticalField("invoice_number")).isTrue();
        assertThat(SpanVerifier.isCriticalField("supplier_name")).isTrue();
        assertThat(SpanVerifier.isCriticalField("buyer_name")).isTrue();
        assertThat(SpanVerifier.isCriticalField("random_note")).isFalse();
    }
}
