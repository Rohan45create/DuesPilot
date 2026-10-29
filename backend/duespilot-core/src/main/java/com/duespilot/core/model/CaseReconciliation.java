package com.duespilot.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Result of whole-case reconciliation covering all invoices, payments, credits,
 * and unallocated transactions.
 *
 * Per architecture.md section 10.3 and rules R-1, R-2, R-11, R-12, R-13, R-15.
 */
public record CaseReconciliation(
    @NonNull List<InvoiceReconciliation> invoiceReconciliations,
    @NonNull List<LedgerEntry> unallocatedPayments,
    @NonNull List<LedgerEntry> unallocatedCredits,
    @NonNull List<LedgerEntry> duplicateSuspects,
    @NonNull Paise totalConfirmedPaid,
    @NonNull Paise totalConfirmedCredit,
    @NonNull Paise totalUnverifiedPayments,
    @NonNull Paise totalDisputedAmounts,
    @Nullable Paise totalOutstandingBalance,
    @NonNull Known<Paise> knownOutstandingBalance
) {
    public CaseReconciliation {
        invoiceReconciliations = List.copyOf(invoiceReconciliations != null ? invoiceReconciliations : Collections.emptyList());
        unallocatedPayments = List.copyOf(unallocatedPayments != null ? unallocatedPayments : Collections.emptyList());
        unallocatedCredits = List.copyOf(unallocatedCredits != null ? unallocatedCredits : Collections.emptyList());
        duplicateSuspects = List.copyOf(duplicateSuspects != null ? duplicateSuspects : Collections.emptyList());
        Objects.requireNonNull(totalConfirmedPaid, "totalConfirmedPaid cannot be null");
        Objects.requireNonNull(totalConfirmedCredit, "totalConfirmedCredit cannot be null");
        Objects.requireNonNull(totalUnverifiedPayments, "totalUnverifiedPayments cannot be null");
        Objects.requireNonNull(totalDisputedAmounts, "totalDisputedAmounts cannot be null");
        Objects.requireNonNull(knownOutstandingBalance, "knownOutstandingBalance cannot be null");
    }

    public boolean isFullyReconciled() {
        return totalOutstandingBalance != null
            && totalOutstandingBalance.isZero()
            && invoiceReconciliations.stream().allMatch(ir -> ir.status() == ReconciliationStatus.RECONCILED);
    }
}
