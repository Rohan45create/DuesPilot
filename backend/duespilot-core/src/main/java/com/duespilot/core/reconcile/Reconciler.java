package com.duespilot.core.reconcile;

import com.duespilot.core.model.CaseReconciliation;
import com.duespilot.core.model.InvoiceReconciliation;
import com.duespilot.core.model.Known;
import com.duespilot.core.model.LedgerEntry;
import com.duespilot.core.model.LedgerEntryType;
import com.duespilot.core.model.Paise;
import com.duespilot.core.model.ReconciliationStatus;
import com.duespilot.core.model.VerificationStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NonNull;

/**
 * Deterministic reconciliation engine implementing Section 10.3 and Rules R-1, R-2, R-11, R-12, R-13, R-15.
 *
 * Principles:
 * 1. Build ledger entries from verified fields only.
 * 2. Link by identifiers (invoice no., PO no., txn id).
 * 3. confirmed_paid = payments with a bank/payment document; payments known only from buyer messages
 *    are UNVERIFIED and NOT subtracted (R-12).
 * 4. confirmed_credit = credit notes referencing the invoice and present as documents.
 * 5. balance = invoice_total − confirmed_paid − confirmed_credit.
 *    Disputed and unverified amounts are reported separately, never netted silently (R-13).
 * 6. A balance is null when any required input is unknown (R-2, section 10.3 step 6).
 * 7. Duplicate suspects keep both source records (R-15).
 */
public final class Reconciler {

    private Reconciler() {
    }

    /**
     * Reconciles a single invoice against a set of related payments, credit notes, and adjustments.
     */
    public static InvoiceReconciliation reconcileInvoice(
        @NonNull LedgerEntry invoice,
        @NonNull List<LedgerEntry> relatedEntries
    ) {
        Objects.requireNonNull(invoice, "invoice cannot be null");
        Objects.requireNonNull(relatedEntries, "relatedEntries cannot be null");

        if (invoice.type() != LedgerEntryType.INVOICE) {
            throw new IllegalArgumentException("Expected an INVOICE entry, got: " + invoice.type());
        }

        String invoiceNumber = invoice.referenceNumber().orElse(invoice.id());
        List<String> notes = new ArrayList<>();

        Paise confirmedPaid = Paise.ZERO;
        Paise confirmedCredit = Paise.ZERO;
        Paise unverifiedPayments = Paise.ZERO;
        Paise disputedAmounts = Paise.ZERO;

        List<LedgerEntry> appliedEntries = new ArrayList<>();
        List<LedgerEntry> unverifiedEntries = new ArrayList<>();
        List<LedgerEntry> disputedEntries = new ArrayList<>();

        boolean requiredInputUnknown = false;
        String unknownReason = null;

        // Rule R-11: Only verified fields enter ledger arithmetic
        if (!invoice.isVerified()) {
            requiredInputUnknown = true;
            unknownReason = "Invoice " + invoiceNumber + " is UNVERIFIED; excluded from ledger arithmetic per Rule R-11";
            notes.add(unknownReason);
        }

        // Rule R-2: Unknown is not zero. If invoice total is unknown, balance is null
        if (invoice.amount().isUnknown()) {
            requiredInputUnknown = true;
            String reason = "Invoice " + invoiceNumber + " total amount is unknown: "
                + ((Known.Unknown<Paise>) invoice.amount()).reason();
            if (unknownReason == null) {
                unknownReason = reason;
            }
            notes.add(reason);
        }

        for (LedgerEntry entry : relatedEntries) {
            // Disputed entries: reported separately, NEVER netted into balance (Rule R-13)
            if (entry.isDisputed() || entry.verificationStatus() == VerificationStatus.DISPUTED) {
                disputedEntries.add(entry);
                if (entry.amount().isValue()) {
                    disputedAmounts = disputedAmounts.plus(entry.amount().getOrThrow());
                }
                notes.add("Entry " + entry.id() + " is disputed; excluded from balance per Rule R-13");
                continue;
            }

            // Unverified entries or buyer messages: NEVER netted into balance (Rules R-11, R-12, R-13)
            if (entry.isUnverified() || entry.source().isMessageSource()) {
                unverifiedEntries.add(entry);
                if (entry.type() == LedgerEntryType.PAYMENT && entry.amount().isValue()) {
                    unverifiedPayments = unverifiedPayments.plus(entry.amount().getOrThrow());
                }
                if (entry.source().isMessageSource()) {
                    notes.add("Payment " + entry.id() + " from buyer message treated as UNVERIFIED per Rule R-12");
                }
                continue;
            }

            // Verified entry: check if amount is known
            if (entry.amount().isUnknown()) {
                requiredInputUnknown = true;
                String reason = "Applied " + entry.type() + " (" + entry.id() + ") has unknown amount: "
                    + ((Known.Unknown<Paise>) entry.amount()).reason();
                if (unknownReason == null) {
                    unknownReason = reason;
                }
                notes.add(reason);
                continue;
            }

            Paise entryAmount = entry.amount().getOrThrow();

            if (entry.type() == LedgerEntryType.PAYMENT) {
                if (entry.source().isPaymentDocument()) {
                    confirmedPaid = confirmedPaid.plus(entryAmount);
                    appliedEntries.add(entry);
                } else {
                    unverifiedPayments = unverifiedPayments.plus(entryAmount);
                    unverifiedEntries.add(entry);
                    notes.add("Payment " + entry.id() + " lacks bank/payment document; excluded from confirmed_paid");
                }
            } else if (entry.type() == LedgerEntryType.CREDIT_NOTE || entry.type() == LedgerEntryType.ADJUSTMENT) {
                confirmedCredit = confirmedCredit.plus(entryAmount);
                appliedEntries.add(entry);
            }
        }

        Paise balance = null;
        Known<Paise> knownBalance;
        ReconciliationStatus status;

        if (requiredInputUnknown) {
            balance = null;
            knownBalance = Known.unknown(unknownReason != null ? unknownReason : "Required input is unknown");
            status = ReconciliationStatus.UNKNOWN;
        } else {
            Paise invoiceTotal = invoice.amount().getOrThrow();
            Paise bal = invoiceTotal.minus(confirmedPaid).minus(confirmedCredit);
            balance = bal;
            knownBalance = Known.value(bal);

            if (bal.isZero()) {
                status = ReconciliationStatus.RECONCILED;
            } else if (bal.isPositive()) {
                if (confirmedPaid.plus(confirmedCredit).isPositive()) {
                    status = ReconciliationStatus.PARTIAL;
                } else {
                    status = ReconciliationStatus.UNRECONCILED;
                }
            } else {
                status = ReconciliationStatus.OVERPAID;
            }
        }

        return new InvoiceReconciliation(
            invoiceNumber,
            invoice.amount(),
            confirmedPaid,
            confirmedCredit,
            unverifiedPayments,
            disputedAmounts,
            balance,
            knownBalance,
            status,
            appliedEntries,
            unverifiedEntries,
            disputedEntries,
            notes
        );
    }

    /**
     * Reconciles an entire case by linking entries across invoices, payments, and credit notes.
     */
    public static CaseReconciliation reconcileCase(@NonNull List<LedgerEntry> allEntries) {
        Objects.requireNonNull(allEntries, "allEntries cannot be null");

        List<LedgerEntry> invoices = new ArrayList<>();
        List<LedgerEntry> nonInvoices = new ArrayList<>();

        for (LedgerEntry entry : allEntries) {
            if (entry.type() == LedgerEntryType.INVOICE) {
                invoices.add(entry);
            } else {
                nonInvoices.add(entry);
            }
        }

        // Duplicate suspect detection per Rule R-15: keep both source records
        List<LedgerEntry> duplicateSuspects = detectDuplicateSuspects(allEntries);

        // Map non-invoice entries to invoices by reference/link
        Map<String, List<LedgerEntry>> linkedByInvoice = new HashMap<>();
        List<LedgerEntry> unallocatedPayments = new ArrayList<>();
        List<LedgerEntry> unallocatedCredits = new ArrayList<>();

        Set<String> knownInvoiceNumbers = new HashSet<>();
        for (LedgerEntry inv : invoices) {
            inv.referenceNumber().ifValue(knownInvoiceNumbers::add);
        }

        for (LedgerEntry entry : nonInvoices) {
            String linked = entry.linkedInvoiceNumber().orNull();
            if (linked != null && knownInvoiceNumbers.contains(linked)) {
                linkedByInvoice.computeIfAbsent(linked, k -> new ArrayList<>()).add(entry);
            } else if (invoices.size() == 1) {
                // If there's only one invoice in the case, associate unlinked entry with it
                String soleInvNo = invoices.get(0).referenceNumber().orElse(invoices.get(0).id());
                linkedByInvoice.computeIfAbsent(soleInvNo, k -> new ArrayList<>()).add(entry);
            } else {
                if (entry.type() == LedgerEntryType.PAYMENT) {
                    unallocatedPayments.add(entry);
                } else {
                    unallocatedCredits.add(entry);
                }
            }
        }

        List<InvoiceReconciliation> invoiceRecons = new ArrayList<>();
        Paise totalConfirmedPaid = Paise.ZERO;
        Paise totalConfirmedCredit = Paise.ZERO;
        Paise totalUnverifiedPayments = Paise.ZERO;
        Paise totalDisputed = Paise.ZERO;

        boolean anyBalanceNull = false;
        Paise totalOutstanding = Paise.ZERO;

        for (LedgerEntry inv : invoices) {
            String invNo = inv.referenceNumber().orElse(inv.id());
            List<LedgerEntry> related = linkedByInvoice.getOrDefault(invNo, Collections.emptyList());
            InvoiceReconciliation ir = reconcileInvoice(inv, related);
            invoiceRecons.add(ir);

            totalConfirmedPaid = totalConfirmedPaid.plus(ir.confirmedPaid());
            totalConfirmedCredit = totalConfirmedCredit.plus(ir.confirmedCredit());
            totalUnverifiedPayments = totalUnverifiedPayments.plus(ir.unverifiedPayments());
            totalDisputed = totalDisputed.plus(ir.disputedAmounts());

            if (ir.balance() == null) {
                anyBalanceNull = true;
            } else if (!anyBalanceNull) {
                totalOutstanding = totalOutstanding.plus(ir.balance());
            }
        }

        Paise totalOutstandingBalance = anyBalanceNull ? null : totalOutstanding;
        Known<Paise> knownOutstanding = totalOutstandingBalance != null
            ? Known.value(totalOutstandingBalance)
            : Known.unknown("Case outstanding balance is null because at least one invoice balance is unknown");

        return new CaseReconciliation(
            invoiceRecons,
            unallocatedPayments,
            unallocatedCredits,
            duplicateSuspects,
            totalConfirmedPaid,
            totalConfirmedCredit,
            totalUnverifiedPayments,
            totalDisputed,
            totalOutstandingBalance,
            knownOutstanding
        );
    }

    /**
     * Identifies duplicate suspects per Rule R-15:
     * Keeps both source records until a human resolves them.
     */
    private static List<LedgerEntry> detectDuplicateSuspects(List<LedgerEntry> entries) {
        List<LedgerEntry> duplicates = new ArrayList<>();
        Map<String, LedgerEntry> seenRefs = new HashMap<>();

        for (LedgerEntry entry : entries) {
            String ref = entry.referenceNumber().orNull();
            if (ref != null && !ref.isBlank()) {
                String key = entry.type() + ":" + ref;
                if (seenRefs.containsKey(key)) {
                    duplicates.add(seenRefs.get(key));
                    duplicates.add(entry);
                } else {
                    seenRefs.put(key, entry);
                }
            }
        }
        return duplicates;
    }
}
