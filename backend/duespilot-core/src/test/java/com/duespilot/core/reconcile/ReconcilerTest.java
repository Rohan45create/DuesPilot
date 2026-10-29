package com.duespilot.core.reconcile;

import com.duespilot.core.model.CaseReconciliation;
import com.duespilot.core.model.DocumentSource;
import com.duespilot.core.model.InvoiceReconciliation;
import com.duespilot.core.model.Known;
import com.duespilot.core.model.LedgerEntry;
import com.duespilot.core.model.LedgerEntryType;
import com.duespilot.core.model.Paise;
import com.duespilot.core.model.ReconciliationStatus;
import com.duespilot.core.model.VerificationStatus;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Reconciliation Engine Tests (Section 10.3 & Rules R-1, R-2, R-11 to R-16)")
class ReconcilerTest {

    private final LocalDate invDate = LocalDate.of(2024, 3, 1);
    private final LocalDate payDate = LocalDate.of(2024, 3, 15);

    @Test
    @DisplayName("Full payment: invoice total equals confirmed paid -> RECONCILED with zero balance")
    void fullPaymentReconciles() {
        LedgerEntry inv = LedgerEntry.invoice("E-1", "INV-101", invDate, Paise.ofRupees(100000), VerificationStatus.VERIFIED);
        LedgerEntry pay = LedgerEntry.payment("E-2", "TXN-901", "INV-101", payDate, Paise.ofRupees(100000), VerificationStatus.VERIFIED, DocumentSource.BANK_STATEMENT);

        InvoiceReconciliation result = Reconciler.reconcileInvoice(inv, List.of(pay));

        assertThat(result.status()).isEqualTo(ReconciliationStatus.RECONCILED);
        assertThat(result.balance()).isEqualTo(Paise.ZERO);
        assertThat(result.hasKnownBalance()).isTrue();
        assertThat(result.confirmedPaid()).isEqualTo(Paise.ofRupees(100000));
        assertThat(result.confirmedCredit()).isEqualTo(Paise.ZERO);
        assertThat(result.appliedEntries()).containsExactly(pay);
    }

    @Test
    @DisplayName("Partial payment: 0 < confirmed paid < invoice total -> PARTIAL balance")
    void partialPaymentReconciles() {
        LedgerEntry inv = LedgerEntry.invoice("E-1", "INV-102", invDate, Paise.ofRupees(100000), VerificationStatus.VERIFIED);
        LedgerEntry pay = LedgerEntry.payment("E-2", "TXN-902", "INV-102", payDate, Paise.ofRupees(40000), VerificationStatus.VERIFIED, DocumentSource.BANK_STATEMENT);

        InvoiceReconciliation result = Reconciler.reconcileInvoice(inv, List.of(pay));

        assertThat(result.status()).isEqualTo(ReconciliationStatus.PARTIAL);
        assertThat(result.balance()).isEqualTo(Paise.ofRupees(60000));
        assertThat(result.confirmedPaid()).isEqualTo(Paise.ofRupees(40000));
        assertThat(result.confirmedCredit()).isEqualTo(Paise.ZERO);
    }

    @Test
    @DisplayName("Credit note: verified credit note reduces balance and combines with payment")
    void creditNoteReconciles() {
        LedgerEntry inv = LedgerEntry.invoice("E-1", "INV-103", invDate, Paise.ofRupees(100000), VerificationStatus.VERIFIED);
        LedgerEntry cn = LedgerEntry.creditNote("E-2", "CN-001", "INV-103", payDate, Paise.ofRupees(15000), VerificationStatus.VERIFIED);
        LedgerEntry pay = LedgerEntry.payment("E-3", "TXN-903", "INV-103", payDate, Paise.ofRupees(85000), VerificationStatus.VERIFIED, DocumentSource.BANK_STATEMENT);

        InvoiceReconciliation result = Reconciler.reconcileInvoice(inv, List.of(cn, pay));

        assertThat(result.status()).isEqualTo(ReconciliationStatus.RECONCILED);
        assertThat(result.balance()).isEqualTo(Paise.ZERO);
        assertThat(result.confirmedCredit()).isEqualTo(Paise.ofRupees(15000));
        assertThat(result.confirmedPaid()).isEqualTo(Paise.ofRupees(85000));
        assertThat(result.appliedEntries()).containsExactly(cn, pay);
    }

    @Test
    @DisplayName("Unverified payment from message: treated as UNVERIFIED and NOT subtracted (Rules R-11, R-12, R-13)")
    void unverifiedBuyerMessagePaymentExcludedFromArithmetic() {
        LedgerEntry inv = LedgerEntry.invoice("E-1", "INV-104", invDate, Paise.ofRupees(100000), VerificationStatus.VERIFIED);

        // Buyer WhatsApp message alleging payment was sent
        LedgerEntry msgPayment = new LedgerEntry(
            "E-MSG-1",
            LedgerEntryType.PAYMENT,
            Known.unknown("No bank ref in message"),
            Known.value("INV-104"),
            Known.unknown("No PO"),
            Known.value(payDate),
            Known.value(Paise.ofRupees(50000)),
            VerificationStatus.UNVERIFIED,
            DocumentSource.BUYER_MESSAGE,
            false,
            null,
            "Buyer message: 'We paid 50,000 today via NEFT'"
        );

        InvoiceReconciliation result = Reconciler.reconcileInvoice(inv, List.of(msgPayment));

        // Must remain UNRECONCILED with full ₹1,00,000 balance!
        assertThat(result.status()).isEqualTo(ReconciliationStatus.UNRECONCILED);
        assertThat(result.balance()).isEqualTo(Paise.ofRupees(100000));
        assertThat(result.confirmedPaid()).isEqualTo(Paise.ZERO);
        assertThat(result.unverifiedPayments()).isEqualTo(Paise.ofRupees(50000));
        assertThat(result.unverifiedEntries()).containsExactly(msgPayment);
        assertThat(result.appliedEntries()).isEmpty();
    }

    @Test
    @DisplayName("Buyer message even if flagged VERIFIED is still excluded per Rule R-12")
    void buyerMessageNeverSubtractedEvenIfMarkedVerified() {
        LedgerEntry inv = LedgerEntry.invoice("E-1", "INV-105", invDate, Paise.ofRupees(100000), VerificationStatus.VERIFIED);

        LedgerEntry msgPayment = new LedgerEntry(
            "E-MSG-2",
            LedgerEntryType.PAYMENT,
            Known.value("MSG-REF-42"),
            Known.value("INV-105"),
            Known.unknown("No PO"),
            Known.value(payDate),
            Known.value(Paise.ofRupees(40000)),
            VerificationStatus.VERIFIED, // mistakenly marked verified, but source is message!
            DocumentSource.BUYER_MESSAGE,
            false,
            null,
            null
        );

        InvoiceReconciliation result = Reconciler.reconcileInvoice(inv, List.of(msgPayment));

        assertThat(result.confirmedPaid()).isEqualTo(Paise.ZERO);
        assertThat(result.balance()).isEqualTo(Paise.ofRupees(100000));
        assertThat(result.unverifiedPayments()).isEqualTo(Paise.ofRupees(40000));
        assertThat(result.unverifiedEntries()).containsExactly(msgPayment);
    }

    @Test
    @DisplayName("Disputed amounts are reported separately and NEVER netted silently (Rule R-13)")
    void disputedAmountsReportedSeparately() {
        LedgerEntry inv = LedgerEntry.invoice("E-1", "INV-106", invDate, Paise.ofRupees(100000), VerificationStatus.VERIFIED);

        LedgerEntry verifiedPay = LedgerEntry.payment("E-2", "TXN-904", "INV-106", payDate, Paise.ofRupees(50000), VerificationStatus.VERIFIED, DocumentSource.BANK_STATEMENT);

        LedgerEntry disputedDebit = new LedgerEntry(
            "E-DISP-1",
            LedgerEntryType.ADJUSTMENT,
            Known.value("DISP-01"),
            Known.value("INV-106"),
            Known.unknown("No PO"),
            Known.value(payDate),
            Known.value(Paise.ofRupees(20000)),
            VerificationStatus.DISPUTED,
            DocumentSource.ADJUSTMENT_DOC,
            true,
            "Buyer claims goods defective (C7)",
            "Email: 'We are deducting 20k for defective batch'"
        );

        InvoiceReconciliation result = Reconciler.reconcileInvoice(inv, List.of(verifiedPay, disputedDebit));

        // Balance must be 1,00,000 - 50,000 = 50,000 (disputed 20,000 is NOT subtracted!)
        assertThat(result.balance()).isEqualTo(Paise.ofRupees(50000));
        assertThat(result.confirmedPaid()).isEqualTo(Paise.ofRupees(50000));
        assertThat(result.disputedAmounts()).isEqualTo(Paise.ofRupees(20000));
        assertThat(result.disputedEntries()).containsExactly(disputedDebit);
        assertThat(result.appliedEntries()).containsExactly(verifiedPay);
    }

    @Test
    @DisplayName("Unknown invoice total gives null balance and UNKNOWN status (Rule R-2, Section 10.3 step 6)")
    void unknownInvoiceAmountYieldsNullBalance() {
        LedgerEntry inv = new LedgerEntry(
            "E-1",
            LedgerEntryType.INVOICE,
            Known.value("INV-UNKNOWN"),
            Known.value("INV-UNKNOWN"),
            Known.unknown("No PO"),
            Known.value(invDate),
            Known.unknown("Invoice amount unreadable on scan"),
            VerificationStatus.VERIFIED,
            DocumentSource.TAX_INVOICE,
            false,
            null,
            null
        );

        LedgerEntry pay = LedgerEntry.payment("E-2", "TXN-905", "INV-UNKNOWN", payDate, Paise.ofRupees(50000), VerificationStatus.VERIFIED, DocumentSource.BANK_STATEMENT);

        InvoiceReconciliation result = Reconciler.reconcileInvoice(inv, List.of(pay));

        assertThat(result.balance()).isNull();
        assertThat(result.hasKnownBalance()).isFalse();
        assertThat(result.knownBalance().isUnknown()).isTrue();
        assertThat(result.status()).isEqualTo(ReconciliationStatus.UNKNOWN);
        assertThat(result.notes()).anyMatch(n -> n.contains("Invoice INV-UNKNOWN total amount is unknown"));
    }

    @Test
    @DisplayName("Unverified invoice gives null balance and UNKNOWN status (Rule R-11)")
    void unverifiedInvoiceYieldsNullBalance() {
        LedgerEntry inv = LedgerEntry.invoice("E-1", "INV-UNVERIFIED", invDate, Paise.ofRupees(100000), VerificationStatus.UNVERIFIED);

        InvoiceReconciliation result = Reconciler.reconcileInvoice(inv, Collections.emptyList());

        assertThat(result.balance()).isNull();
        assertThat(result.hasKnownBalance()).isFalse();
        assertThat(result.status()).isEqualTo(ReconciliationStatus.UNKNOWN);
        assertThat(result.notes()).anyMatch(n -> n.contains("UNVERIFIED"));
    }

    @Test
    @DisplayName("Applied verified payment with unknown amount gives null balance (Rule R-2, Section 10.3 step 6)")
    void appliedPaymentWithUnknownAmountYieldsNullBalance() {
        LedgerEntry inv = LedgerEntry.invoice("E-1", "INV-107", invDate, Paise.ofRupees(100000), VerificationStatus.VERIFIED);

        LedgerEntry payWithUnknownAmt = new LedgerEntry(
            "E-2",
            LedgerEntryType.PAYMENT,
            Known.value("TXN-UNKNOWN-AMT"),
            Known.value("INV-107"),
            Known.unknown("No PO"),
            Known.value(payDate),
            Known.unknown("Cheque amount cut off on scan"),
            VerificationStatus.VERIFIED,
            DocumentSource.BANK_STATEMENT,
            false,
            null,
            null
        );

        InvoiceReconciliation result = Reconciler.reconcileInvoice(inv, List.of(payWithUnknownAmt));

        assertThat(result.balance()).isNull();
        assertThat(result.hasKnownBalance()).isFalse();
        assertThat(result.status()).isEqualTo(ReconciliationStatus.UNKNOWN);
    }

    @Test
    @DisplayName("Overpayment: confirmed paid > invoice total -> OVERPAID status with negative balance")
    void overpaymentStatus() {
        LedgerEntry inv = LedgerEntry.invoice("E-1", "INV-108", invDate, Paise.ofRupees(100000), VerificationStatus.VERIFIED);
        LedgerEntry pay = LedgerEntry.payment("E-2", "TXN-906", "INV-108", payDate, Paise.ofRupees(120000), VerificationStatus.VERIFIED, DocumentSource.BANK_STATEMENT);

        InvoiceReconciliation result = Reconciler.reconcileInvoice(inv, List.of(pay));

        assertThat(result.status()).isEqualTo(ReconciliationStatus.OVERPAID);
        assertThat(result.balance()).isEqualTo(Paise.ofRupees(-20000));
    }

    @Test
    @DisplayName("Whole-case reconciliation with multiple invoices, unallocated payments, and duplicate suspect (Rule R-15)")
    void caseReconciliationWithDuplicatesAndUnallocated() {
        LedgerEntry inv1 = LedgerEntry.invoice("INV-1", "INV-001", invDate, Paise.ofRupees(100000), VerificationStatus.VERIFIED);
        LedgerEntry inv2 = LedgerEntry.invoice("INV-2", "INV-002", invDate, Paise.ofRupees(200000), VerificationStatus.VERIFIED);

        // Payment for inv1
        LedgerEntry pay1 = LedgerEntry.payment("P-1", "TXN-100", "INV-001", payDate, Paise.ofRupees(100000), VerificationStatus.VERIFIED, DocumentSource.BANK_STATEMENT);

        // Duplicate suspect payment sharing TXN-100 (Rule R-15 keeps both records)
        LedgerEntry pay1Duplicate = LedgerEntry.payment("P-1-DUP", "TXN-100", "INV-001", payDate, Paise.ofRupees(100000), VerificationStatus.VERIFIED, DocumentSource.BANK_STATEMENT);

        // Unallocated payment not specifying any invoice
        LedgerEntry unallocatedPay = new LedgerEntry(
            "P-UNALLOC",
            LedgerEntryType.PAYMENT,
            Known.value("TXN-999"),
            Known.unknown("No invoice cited in remittance"),
            Known.unknown("No PO"),
            Known.value(payDate),
            Known.value(Paise.ofRupees(50000)),
            VerificationStatus.VERIFIED,
            DocumentSource.BANK_STATEMENT,
            false,
            null,
            null
        );

        CaseReconciliation caseResult = Reconciler.reconcileCase(List.of(inv1, inv2, pay1, pay1Duplicate, unallocatedPay));

        assertThat(caseResult.invoiceReconciliations()).hasSize(2);
        assertThat(caseResult.unallocatedPayments()).containsExactly(unallocatedPay);
        // Per Rule R-15: both records kept for duplicate suspect
        assertThat(caseResult.duplicateSuspects()).contains(pay1, pay1Duplicate);
    }
}
