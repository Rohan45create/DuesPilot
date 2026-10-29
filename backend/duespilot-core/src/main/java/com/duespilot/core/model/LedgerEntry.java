package com.duespilot.core.model;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * An entry in the financial ledger (invoice, payment, credit note, adjustment).
 *
 * Per architecture.md section 10.3 and rules R-1, R-2, R-11, R-12, R-13, R-15:
 * - All monetary values use {@link Paise} (R-1).
 * - Missing values are {@link Known.Unknown}, never null/zero defaults (R-2).
 * - Verified status and source documents determine eligibility for balance arithmetic (R-11, R-12).
 * - Disputed items are flagged and reported separately (R-13).
 * - Duplicate entries are preserved (R-15).
 */
public record LedgerEntry(
    @NonNull String id,
    @NonNull LedgerEntryType type,
    @NonNull Known<String> referenceNumber,
    @NonNull Known<String> linkedInvoiceNumber,
    @NonNull Known<String> purchaseOrderNumber,
    @NonNull Known<LocalDate> date,
    @NonNull Known<Paise> amount,
    @NonNull VerificationStatus verificationStatus,
    @NonNull DocumentSource source,
    boolean isDisputed,
    @Nullable String disputeReason,
    @Nullable String evidenceQuote
) {
    public LedgerEntry {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(referenceNumber, "referenceNumber cannot be null");
        Objects.requireNonNull(linkedInvoiceNumber, "linkedInvoiceNumber cannot be null");
        Objects.requireNonNull(purchaseOrderNumber, "purchaseOrderNumber cannot be null");
        Objects.requireNonNull(date, "date cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(verificationStatus, "verificationStatus cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
    }

    public boolean isVerified() {
        return verificationStatus == VerificationStatus.VERIFIED;
    }

    public boolean isUnverified() {
        return verificationStatus == VerificationStatus.UNVERIFIED;
    }

    /**
     * Per Rule R-12 & Section 10.3 step 3:
     * A payment is confirmed only if it is verified, undisputed, and backed by a payment document
     * (e.g. bank statement or payment receipt). Payments known only from messages are unverified.
     */
    public boolean isConfirmedPayment() {
        return type == LedgerEntryType.PAYMENT
            && isVerified()
            && !isDisputed
            && source.isPaymentDocument();
    }

    /**
     * Per Section 10.3 step 4:
     * Confirmed credit note present as a verified document.
     */
    public boolean isConfirmedCredit() {
        return (type == LedgerEntryType.CREDIT_NOTE || type == LedgerEntryType.ADJUSTMENT)
            && isVerified()
            && !isDisputed
            && (source == DocumentSource.CREDIT_NOTE_DOC || source == DocumentSource.ADJUSTMENT_DOC || source == DocumentSource.BANK_STATEMENT);
    }

    // Factory methods for concise entry creation

    public static LedgerEntry invoice(
        @NonNull String id,
        @NonNull String invoiceNumber,
        @NonNull LocalDate invoiceDate,
        @NonNull Paise amount,
        @NonNull VerificationStatus status
    ) {
        return new LedgerEntry(
            id,
            LedgerEntryType.INVOICE,
            Known.value(invoiceNumber),
            Known.value(invoiceNumber),
            Known.unknown("No PO specified"),
            Known.value(invoiceDate),
            Known.value(amount),
            status,
            DocumentSource.TAX_INVOICE,
            false,
            null,
            null
        );
    }

    public static LedgerEntry payment(
        @NonNull String id,
        @NonNull String txnId,
        @NonNull String linkedInvoiceNumber,
        @NonNull LocalDate date,
        @NonNull Paise amount,
        @NonNull VerificationStatus status,
        @NonNull DocumentSource source
    ) {
        return new LedgerEntry(
            id,
            LedgerEntryType.PAYMENT,
            Known.value(txnId),
            Known.value(linkedInvoiceNumber),
            Known.unknown("Not a PO"),
            Known.value(date),
            Known.value(amount),
            status,
            source,
            false,
            null,
            null
        );
    }

    public static LedgerEntry creditNote(
        @NonNull String id,
        @NonNull String creditNoteNumber,
        @NonNull String linkedInvoiceNumber,
        @NonNull LocalDate date,
        @NonNull Paise amount,
        @NonNull VerificationStatus status
    ) {
        return new LedgerEntry(
            id,
            LedgerEntryType.CREDIT_NOTE,
            Known.value(creditNoteNumber),
            Known.value(linkedInvoiceNumber),
            Known.unknown("Not a PO"),
            Known.value(date),
            Known.value(amount),
            status,
            DocumentSource.CREDIT_NOTE_DOC,
            false,
            null,
            null
        );
    }
}
