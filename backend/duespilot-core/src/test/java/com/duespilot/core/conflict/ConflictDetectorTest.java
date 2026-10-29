package com.duespilot.core.conflict;

import com.duespilot.core.conflict.ConflictEvidence.ChangeOrder;
import com.duespilot.core.conflict.ConflictEvidence.DeliveryDocument;
import com.duespilot.core.conflict.ConflictEvidence.PurchaseOrder;
import com.duespilot.core.conflict.ConflictEvidence.RemittanceAdvice;
import com.duespilot.core.conflict.ConflictEvidence.RemittanceDeduction;
import com.duespilot.core.model.Allegation;
import com.duespilot.core.model.DocumentSource;
import com.duespilot.core.model.Known;
import com.duespilot.core.model.LedgerEntry;
import com.duespilot.core.model.LedgerEntryType;
import com.duespilot.core.model.Paise;
import com.duespilot.core.model.VerificationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Conflict Detector Tests: Rules C1 to C10 (Section 10.4 & Rules R-17, R-18, R-59)")
class ConflictDetectorTest {

    private final LocalDate invDate = LocalDate.of(2024, 3, 15);
    private final LocalDate payDate = LocalDate.of(2024, 3, 25);
    private final LocalDate poDate = LocalDate.of(2024, 3, 1);
    private final LocalDate delDate = LocalDate.of(2024, 3, 10);

    private final LedgerEntry baseInvoice = LedgerEntry.invoice(
        "INV-101",
        "INV-101",
        invDate,
        Paise.ofRupees(100000),
        VerificationStatus.VERIFIED
    );

    // ==========================================
    // C1: Partial payment
    // ==========================================
    @Test
    @DisplayName("C1 positive: confirmed payments sum to less than invoice total -> C1 emitted with evidence IDs")
    void c1PositivePartialPayment() {
        LedgerEntry pay = LedgerEntry.payment(
            "PAY-1",
            "TXN-1",
            "INV-101",
            payDate,
            Paise.ofRupees(40000),
            VerificationStatus.VERIFIED,
            DocumentSource.BANK_STATEMENT
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .entries(List.of(pay))
            .build();

        Optional<Conflict> conflict = ConflictDetector.detectC1(ctx);

        assertThat(conflict).isPresent();
        assertThat(conflict.get().type()).isEqualTo(ConflictType.C1);
        assertThat(conflict.get().evidenceIds()).containsExactly("INV-101", "PAY-1");
        assertThat(conflict.get().details()).containsEntry("invoiceTotal", "₹1,00,000.00");
        assertThat(conflict.get().details()).containsEntry("confirmedPaid", "₹40,000.00");
    }

    @Test
    @DisplayName("C1 negative: confirmed payments equal invoice total -> no C1 conflict")
    void c1NegativeFullPayment() {
        LedgerEntry pay = LedgerEntry.payment(
            "PAY-1",
            "TXN-1",
            "INV-101",
            payDate,
            Paise.ofRupees(100000),
            VerificationStatus.VERIFIED,
            DocumentSource.BANK_STATEMENT
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .entries(List.of(pay))
            .build();

        assertThat(ConflictDetector.detectC1(ctx)).isEmpty();
    }

    // ==========================================
    // C2: Credit note unapplied
    // ==========================================
    @Test
    @DisplayName("C2 positive: credit note references invoice with no matching adjustment -> C2 emitted")
    void c2PositiveCreditNoteUnapplied() {
        LedgerEntry cn = LedgerEntry.creditNote(
            "CN-1",
            "CN-2024-01",
            "INV-101",
            payDate,
            Paise.ofRupees(10000),
            VerificationStatus.VERIFIED
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .entries(List.of(cn))
            .build();

        Optional<Conflict> conflict = ConflictDetector.detectC2(ctx);

        assertThat(conflict).isPresent();
        assertThat(conflict.get().type()).isEqualTo(ConflictType.C2);
        assertThat(conflict.get().evidenceIds()).contains("CN-1", "INV-101");
    }

    @Test
    @DisplayName("C2 negative: matching adjustment exists in ledger -> no C2 conflict")
    void c2NegativeCreditNoteApplied() {
        LedgerEntry cn = LedgerEntry.creditNote(
            "CN-1",
            "CN-2024-01",
            "INV-101",
            payDate,
            Paise.ofRupees(10000),
            VerificationStatus.VERIFIED
        );

        LedgerEntry adj = new LedgerEntry(
            "ADJ-1",
            LedgerEntryType.ADJUSTMENT,
            Known.value("CN-2024-01"),
            Known.value("INV-101"),
            Known.unknown("No PO"),
            Known.value(payDate),
            Known.value(Paise.ofRupees(10000)),
            VerificationStatus.VERIFIED,
            DocumentSource.ADJUSTMENT_DOC,
            false,
            null,
            null
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .entries(List.of(cn, adj))
            .build();

        assertThat(ConflictDetector.detectC2(ctx)).isEmpty();
    }

    // ==========================================
    // C3: PO/invoice amount mismatch
    // ==========================================
    @Test
    @DisplayName("C3 positive: PO total differs from invoice total without change order -> C3 emitted")
    void c3PositivePoMismatch() {
        PurchaseOrder po = new PurchaseOrder(
            "PO-1",
            "PO-998",
            Known.value(Paise.ofRupees(80000)),
            Known.value(30),
            Known.value(poDate)
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .purchaseOrders(List.of(po))
            .build();

        Optional<Conflict> conflict = ConflictDetector.detectC3(ctx);

        assertThat(conflict).isPresent();
        assertThat(conflict.get().type()).isEqualTo(ConflictType.C3);
        assertThat(conflict.get().evidenceIds()).containsExactly("PO-1", "INV-101");
        assertThat(conflict.get().details()).containsEntry("difference", "₹20,000.00");
    }

    @Test
    @DisplayName("C3 negative: change order covers the PO difference -> no C3 conflict")
    void c3NegativePoMismatchCoveredByChangeOrder() {
        PurchaseOrder po = new PurchaseOrder(
            "PO-1",
            "PO-998",
            Known.value(Paise.ofRupees(80000)),
            Known.value(30),
            Known.value(poDate)
        );

        ChangeOrder co = new ChangeOrder(
            "CO-1",
            "PO-998",
            Known.value(Paise.ofRupees(100000)), // amended PO total matches invoice
            "Scope expansion agreed in writing"
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .purchaseOrders(List.of(po))
            .changeOrders(List.of(co))
            .build();

        assertThat(ConflictDetector.detectC3(ctx)).isEmpty();
    }

    // ==========================================
    // C4: Terms conflict (Rule R-17)
    // ==========================================
    @Test
    @DisplayName("C4 positive: PO says 30 days, invoice says 45 days -> C4 side-by-side terms emitted per Rule R-17")
    void c4PositiveTermsConflict() {
        PurchaseOrder po = new PurchaseOrder(
            "PO-1",
            "PO-998",
            Known.value(Paise.ofRupees(100000)),
            Known.value(30),
            Known.value(poDate)
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .purchaseOrders(List.of(po))
            .invoicePaymentTermsDays(Known.value(45))
            .build();

        Optional<Conflict> conflict = ConflictDetector.detectC4(ctx);

        assertThat(conflict).isPresent();
        assertThat(conflict.get().type()).isEqualTo(ConflictType.C4);
        assertThat(conflict.get().evidenceIds()).contains("INV-101", "PO-1");
        // Rule R-17: Conflicting payment terms presented side by side
        assertThat(conflict.get().details()).containsEntry("Invoice (INV-101)", "45 days");
        assertThat(conflict.get().details()).containsEntry("Purchase Order (PO-998)", "30 days");
    }

    @Test
    @DisplayName("C4 negative: PO and invoice agree on 30 days -> no C4 conflict")
    void c4NegativeTermsAgree() {
        PurchaseOrder po = new PurchaseOrder(
            "PO-1",
            "PO-998",
            Known.value(Paise.ofRupees(100000)),
            Known.value(30),
            Known.value(poDate)
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .purchaseOrders(List.of(po))
            .invoicePaymentTermsDays(Known.value(30))
            .build();

        assertThat(ConflictDetector.detectC4(ctx)).isEmpty();
    }

    // ==========================================
    // C5: Delivery evidence missing
    // ==========================================
    @Test
    @DisplayName("C5 positive: invoice present but no delivery documents -> C5 emitted")
    void c5PositiveMissingDelivery() {
        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .deliveryDocuments(List.of())
            .build();

        Optional<Conflict> conflict = ConflictDetector.detectC5(ctx);

        assertThat(conflict).isPresent();
        assertThat(conflict.get().type()).isEqualTo(ConflictType.C5);
        assertThat(conflict.get().evidenceIds()).containsExactly("INV-101");
    }

    @Test
    @DisplayName("C5 negative: delivery challan present -> no C5 conflict")
    void c5NegativeDeliveryPresent() {
        DeliveryDocument del = new DeliveryDocument(
            "DEL-1",
            "DC-501",
            "INV-101",
            Known.value(delDate),
            Known.value(delDate),
            Known.value(100),
            Known.value(100),
            true,
            false
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .deliveryDocuments(List.of(del))
            .build();

        assertThat(ConflictDetector.detectC5(ctx)).isEmpty();
    }

    // ==========================================
    // C6: Duplicate suspect (Rule R-15)
    // ==========================================
    @Test
    @DisplayName("C6 positive: two payments share identical bank transaction reference -> C6 emitted with both IDs (R-15)")
    void c6PositiveDuplicateBankTxn() {
        LedgerEntry pay1 = LedgerEntry.payment(
            "PAY-1",
            "TXN-DUP-999",
            "INV-101",
            payDate,
            Paise.ofRupees(50000),
            VerificationStatus.VERIFIED,
            DocumentSource.BANK_STATEMENT
        );

        LedgerEntry pay2 = LedgerEntry.payment(
            "PAY-2",
            "TXN-DUP-999",
            "INV-101",
            payDate,
            Paise.ofRupees(50000),
            VerificationStatus.VERIFIED,
            DocumentSource.BANK_STATEMENT
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .entries(List.of(pay1, pay2))
            .build();

        Optional<Conflict> conflict = ConflictDetector.detectC6(ctx);

        assertThat(conflict).isPresent();
        assertThat(conflict.get().type()).isEqualTo(ConflictType.C6);
        // Rule R-15: keep both source records
        assertThat(conflict.get().evidenceIds()).containsExactly("PAY-1", "PAY-2");
    }

    @Test
    @DisplayName("C6 negative: payments have distinct transaction references -> no C6 conflict")
    void c6NegativeDistinctPayments() {
        LedgerEntry pay1 = LedgerEntry.payment("PAY-1", "TXN-A", "INV-101", payDate, Paise.ofRupees(50000), VerificationStatus.VERIFIED, DocumentSource.BANK_STATEMENT);
        LedgerEntry pay2 = LedgerEntry.payment("PAY-2", "TXN-B", "INV-101", payDate, Paise.ofRupees(50000), VerificationStatus.VERIFIED, DocumentSource.BANK_STATEMENT);

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .entries(List.of(pay1, pay2))
            .build();

        assertThat(ConflictDetector.detectC6(ctx)).isEmpty();
    }

    // ==========================================
    // C7: Quantity / quality dispute
    // ==========================================
    @Test
    @DisplayName("C7 positive: quantity delivered is less than ordered -> C7 emitted")
    void c7PositiveQuantityShortage() {
        DeliveryDocument del = new DeliveryDocument(
            "DEL-1",
            "DC-502",
            "INV-101",
            Known.value(delDate),
            Known.value(delDate),
            Known.value(100), // ordered
            Known.value(80),  // delivered
            true,
            false
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .deliveryDocuments(List.of(del))
            .build();

        Optional<Conflict> conflict = ConflictDetector.detectC7(ctx);

        assertThat(conflict).isPresent();
        assertThat(conflict.get().type()).isEqualTo(ConflictType.C7);
        assertThat(conflict.get().evidenceIds()).contains("DEL-1", "INV-101");
        assertThat(conflict.get().summary()).contains("shortfall 20");
    }

    @Test
    @DisplayName("C7 positive (allegation): buyer quality defect allegation -> C7 emitted")
    void c7PositiveQualityAllegation() {
        Allegation qualityClaim = new Allegation(
            "ALG-1",
            "Acme Corp",
            Allegation.AllegationType.QUALITY_DEFECT,
            "Buyer claims casting batch defective",
            Known.value(payDate),
            Known.value(Paise.ofRupees(20000)),
            "Email: 'Casting rejected due to porosity'"
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .allegations(List.of(qualityClaim))
            .build();

        Optional<Conflict> conflict = ConflictDetector.detectC7(ctx);

        assertThat(conflict).isPresent();
        assertThat(conflict.get().type()).isEqualTo(ConflictType.C7);
        assertThat(conflict.get().evidenceIds()).contains("ALG-1");
    }

    @Test
    @DisplayName("C7 negative: ordered equals delivered and no buyer allegations -> no C7 conflict")
    void c7NegativeNoDispute() {
        DeliveryDocument del = new DeliveryDocument(
            "DEL-1",
            "DC-503",
            "INV-101",
            Known.value(delDate),
            Known.value(delDate),
            Known.value(100),
            Known.value(100),
            true,
            false
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .deliveryDocuments(List.of(del))
            .build();

        assertThat(ConflictDetector.detectC7(ctx)).isEmpty();
    }

    // ==========================================
    // C8: Date contradiction
    // ==========================================
    @Test
    @DisplayName("C8 positive: payment date is before invoice date -> C8 emitted")
    void c8PositivePaymentBeforeInvoice() {
        LedgerEntry earlyPay = LedgerEntry.payment(
            "PAY-EARLY",
            "TXN-EARLY",
            "INV-101",
            LocalDate.of(2024, 3, 1), // 14 days before invoice date (2024-03-15)
            Paise.ofRupees(50000),
            VerificationStatus.VERIFIED,
            DocumentSource.BANK_STATEMENT
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .entries(List.of(earlyPay))
            .build();

        Optional<Conflict> conflict = ConflictDetector.detectC8(ctx);

        assertThat(conflict).isPresent();
        assertThat(conflict.get().type()).isEqualTo(ConflictType.C8);
        assertThat(conflict.get().evidenceIds()).contains("PAY-EARLY", "INV-101");
    }

    @Test
    @DisplayName("C8 negative: proper chronological sequence (PO -> Del -> Inv -> Pay) -> no C8 conflict")
    void c8NegativeChronologicalOrder() {
        PurchaseOrder po = new PurchaseOrder("PO-1", "PO-998", Known.value(Paise.ofRupees(100000)), Known.value(30), Known.value(poDate)); // Mar 1
        DeliveryDocument del = new DeliveryDocument("DEL-1", "DC-1", "INV-101", Known.value(delDate), Known.value(delDate), Known.value(100), Known.value(100), true, false); // Mar 10
        // Invoice is Mar 15
        LedgerEntry pay = LedgerEntry.payment("PAY-1", "TXN-1", "INV-101", payDate, Paise.ofRupees(100000), VerificationStatus.VERIFIED, DocumentSource.BANK_STATEMENT); // Mar 25

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .purchaseOrders(List.of(po))
            .deliveryDocuments(List.of(del))
            .entries(List.of(pay))
            .build();

        assertThat(ConflictDetector.detectC8(ctx)).isEmpty();
    }

    // ==========================================
    // C9: Short-payment deduction (Rule R-18)
    // ==========================================
    @Test
    @DisplayName("C9 positive: remittance advice itemizes deduction -> C9 emitted with evidence and NO tax computation (Rule R-18)")
    void c9PositiveShortPaymentDeduction() {
        RemittanceDeduction deduction = new RemittanceDeduction("TDS under Sec 194C @ 1%", Known.value(Paise.ofRupees(1000)), "TDS-194C");
        RemittanceAdvice ra = new RemittanceAdvice("RA-1", "PAY-1", List.of(deduction));

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .remittanceAdvices(List.of(ra))
            .build();

        Optional<Conflict> conflict = ConflictDetector.detectC9(ctx);

        assertThat(conflict).isPresent();
        assertThat(conflict.get().type()).isEqualTo(ConflictType.C9);
        assertThat(conflict.get().evidenceIds()).contains("RA-1", "PAY-1");
        // Rule R-18: Recorded from document evidence only without tax computation
        assertThat(conflict.get().details()).containsEntry("TDS under Sec 194C @ 1%", "₹1,000.00");
    }

    @Test
    @DisplayName("C9 negative: remittance advice has zero deductions -> no C9 conflict")
    void c9NegativeNoDeductions() {
        RemittanceAdvice ra = new RemittanceAdvice("RA-1", "PAY-1", List.of());

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .remittanceAdvices(List.of(ra))
            .build();

        assertThat(ConflictDetector.detectC9(ctx)).isEmpty();
    }

    // ==========================================
    // C10: Unallocated lump-sum payment
    // ==========================================
    @Test
    @DisplayName("C10 positive: payment references multiple invoices -> C10 emitted")
    void c10PositiveMultipleInvoicesReferenced() {
        LedgerEntry multiPay = new LedgerEntry(
            "PAY-LUMP",
            LedgerEntryType.PAYMENT,
            Known.value("TXN-MULTI"),
            Known.value("INV-101, INV-102, INV-103"), // multiple invoices
            Known.unknown("No PO"),
            Known.value(payDate),
            Known.value(Paise.ofRupees(250000)),
            VerificationStatus.VERIFIED,
            DocumentSource.BANK_STATEMENT,
            false,
            null,
            null
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .entries(List.of(multiPay))
            .build();

        Optional<Conflict> conflict = ConflictDetector.detectC10(ctx);

        assertThat(conflict).isPresent();
        assertThat(conflict.get().type()).isEqualTo(ConflictType.C10);
        assertThat(conflict.get().evidenceIds()).containsExactly("PAY-LUMP");
    }

    @Test
    @DisplayName("C10 positive (no invoice): payment references no invoice -> C10 emitted")
    void c10PositiveNoInvoiceReferenced() {
        LedgerEntry unrefPay = new LedgerEntry(
            "PAY-UNREF",
            LedgerEntryType.PAYMENT,
            Known.value("TXN-UNREF"),
            Known.unknown("No invoice referenced"),
            Known.unknown("No PO"),
            Known.value(payDate),
            Known.value(Paise.ofRupees(50000)),
            VerificationStatus.VERIFIED,
            DocumentSource.BANK_STATEMENT,
            false,
            null,
            null
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .entries(List.of(unrefPay))
            .build();

        Optional<Conflict> conflict = ConflictDetector.detectC10(ctx);

        assertThat(conflict).isPresent();
        assertThat(conflict.get().type()).isEqualTo(ConflictType.C10);
        assertThat(conflict.get().evidenceIds()).containsExactly("PAY-UNREF");
    }

    @Test
    @DisplayName("C10 negative: payment cleanly references single invoice -> no C10 conflict")
    void c10NegativeSingleInvoiceReferenced() {
        LedgerEntry singlePay = LedgerEntry.payment(
            "PAY-1",
            "TXN-1",
            "INV-101",
            payDate,
            Paise.ofRupees(100000),
            VerificationStatus.VERIFIED,
            DocumentSource.BANK_STATEMENT
        );

        ConflictContext ctx = ConflictContext.builder(baseInvoice)
            .entries(List.of(singlePay))
            .build();

        assertThat(ConflictDetector.detectC10(ctx)).isEmpty();
    }
}
