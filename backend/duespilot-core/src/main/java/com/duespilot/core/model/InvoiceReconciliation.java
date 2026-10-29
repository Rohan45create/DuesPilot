package com.duespilot.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Result of deterministic reconciliation for a single invoice.
 *
 * Per architecture.md section 10.3:
 * - confirmed_paid = payments with a bank/payment document (not buyer messages).
 * - confirmed_credit = credit notes referencing the invoice and present as documents.
 * - balance = invoice_total − confirmed_paid − confirmed_credit.
 * - Disputed and unverified amounts are reported separately, never netted silently (R-13).
 * - A balance is null when any required input is unknown (R-2, section 10.3 step 6).
 */
public record InvoiceReconciliation(
    @NonNull String invoiceNumber,
    @NonNull Known<Paise> invoiceTotal,
    @NonNull Paise confirmedPaid,
    @NonNull Paise confirmedCredit,
    @NonNull Paise unverifiedPayments,
    @NonNull Paise disputedAmounts,
    @Nullable Paise balance,
    @NonNull Known<Paise> knownBalance,
    @NonNull ReconciliationStatus status,
    @NonNull List<LedgerEntry> appliedEntries,
    @NonNull List<LedgerEntry> unverifiedEntries,
    @NonNull List<LedgerEntry> disputedEntries,
    @NonNull List<String> notes
) {
    public InvoiceReconciliation {
        Objects.requireNonNull(invoiceNumber, "invoiceNumber cannot be null");
        Objects.requireNonNull(invoiceTotal, "invoiceTotal cannot be null");
        Objects.requireNonNull(confirmedPaid, "confirmedPaid cannot be null");
        Objects.requireNonNull(confirmedCredit, "confirmedCredit cannot be null");
        Objects.requireNonNull(unverifiedPayments, "unverifiedPayments cannot be null");
        Objects.requireNonNull(disputedAmounts, "disputedAmounts cannot be null");
        Objects.requireNonNull(knownBalance, "knownBalance cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        appliedEntries = List.copyOf(appliedEntries != null ? appliedEntries : Collections.emptyList());
        unverifiedEntries = List.copyOf(unverifiedEntries != null ? unverifiedEntries : Collections.emptyList());
        disputedEntries = List.copyOf(disputedEntries != null ? disputedEntries : Collections.emptyList());
        notes = List.copyOf(notes != null ? notes : Collections.emptyList());
    }

    /**
     * Checks if the balance is fully known.
     */
    public boolean hasKnownBalance() {
        return balance != null && knownBalance.isValue();
    }
}
