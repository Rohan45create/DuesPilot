package com.duespilot.core.conflict;

import com.duespilot.core.conflict.ConflictEvidence.ChangeOrder;
import com.duespilot.core.conflict.ConflictEvidence.DeliveryDocument;
import com.duespilot.core.conflict.ConflictEvidence.PurchaseOrder;
import com.duespilot.core.conflict.ConflictEvidence.RemittanceAdvice;
import com.duespilot.core.conflict.ConflictEvidence.RemittanceDeduction;
import com.duespilot.core.model.Allegation;
import com.duespilot.core.model.Known;
import com.duespilot.core.model.LedgerEntry;
import com.duespilot.core.model.LedgerEntryType;
import com.duespilot.core.model.Paise;
import com.duespilot.core.model.VerificationStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

/**
 * Deterministic conflict detector implementing architecture.md section 10.4 (C1 to C10).
 *
 * Rules:
 * - R-17: Conflicting payment terms are presented side by side; never pick the most favourable one.
 * - R-18: C9 (short-payment deduction) is classified from document evidence only; never compute tax.
 * - R-59: Every conflict rule has a unit test (positive and negative).
 * - Each rule emits evidence IDs.
 */
public final class ConflictDetector {

    private ConflictDetector() {
    }

    /**
     * Runs all deterministic conflict rules C1 to C10 against the provided context.
     */
    public static List<Conflict> detectAll(@NonNull ConflictContext context) {
        Objects.requireNonNull(context, "context cannot be null");
        List<Conflict> conflicts = new ArrayList<>();

        detectC1(context).ifPresent(conflicts::add);
        detectC2(context).ifPresent(conflicts::add);
        detectC3(context).ifPresent(conflicts::add);
        detectC4(context).ifPresent(conflicts::add);
        detectC5(context).ifPresent(conflicts::add);
        detectC6(context).ifPresent(conflicts::add);
        detectC7(context).ifPresent(conflicts::add);
        detectC8(context).ifPresent(conflicts::add);
        detectC9(context).ifPresent(conflicts::add);
        detectC10(context).ifPresent(conflicts::add);

        return conflicts;
    }

    /**
     * C1: Partial payment (0 < Σ confirmed payments < invoice total).
     */
    public static Optional<Conflict> detectC1(@NonNull ConflictContext context) {
        LedgerEntry inv = context.invoice();
        if (inv.amount().isUnknown()) {
            return Optional.empty();
        }

        Paise invTotal = inv.amount().getOrThrow();
        Paise confirmedSum = Paise.ZERO;
        List<String> paymentEvidenceIds = new ArrayList<>();

        for (LedgerEntry e : context.entries()) {
            if (e.isConfirmedPayment() && e.amount().isValue()) {
                confirmedSum = confirmedSum.plus(e.amount().getOrThrow());
                paymentEvidenceIds.add(e.id());
            }
        }

        if (confirmedSum.isPositive() && confirmedSum.compareTo(invTotal) < 0) {
            List<String> evidence = new ArrayList<>();
            evidence.add(inv.id());
            evidence.addAll(paymentEvidenceIds);

            String summary = String.format(
                "Partial payment: confirmed payments sum to %s, which is less than invoice total %s",
                confirmedSum,
                invTotal
            );

            Map<String, String> details = Map.of(
                "invoiceTotal", invTotal.toString(),
                "confirmedPaid", confirmedSum.toString(),
                "shortfall", invTotal.minus(confirmedSum).toString()
            );

            return Optional.of(Conflict.of("CONF-C1-" + inv.id(), ConflictType.C1, summary, evidence, details));
        }

        return Optional.empty();
    }

    /**
     * C2: Credit note unapplied (Credit note references invoice; no matching adjustment).
     */
    public static Optional<Conflict> detectC2(@NonNull ConflictContext context) {
        LedgerEntry inv = context.invoice();
        String invNo = inv.referenceNumber().orElse(inv.id());

        for (LedgerEntry e : context.entries()) {
            if (e.type() == LedgerEntryType.CREDIT_NOTE) {
                String linked = e.linkedInvoiceNumber().orNull();
                if (invNo.equals(linked) || inv.id().equals(linked)) {
                    // Check if an adjustment document or applied record matches
                    boolean hasMatchingAdjustment = context.entries().stream().anyMatch(adj ->
                        adj.type() == LedgerEntryType.ADJUSTMENT
                            && e.referenceNumber().orElse("").equals(adj.referenceNumber().orNull())
                    );

                    if (!hasMatchingAdjustment) {
                        List<String> evidence = List.of(e.id(), inv.id());
                        String summary = String.format(
                            "Credit note %s (%s) references invoice %s but has no matching ledger adjustment",
                            e.referenceNumber().orElse(e.id()),
                            e.amount().map(Paise::toString).orElse("unknown amount"),
                            invNo
                        );
                        return Optional.of(Conflict.of("CONF-C2-" + e.id(), ConflictType.C2, summary, evidence));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * C3: PO/invoice amount mismatch (Totals differ and no change-order document).
     */
    public static Optional<Conflict> detectC3(@NonNull ConflictContext context) {
        LedgerEntry inv = context.invoice();
        if (inv.amount().isUnknown() || context.purchaseOrders().isEmpty()) {
            return Optional.empty();
        }

        Paise invTotal = inv.amount().getOrThrow();

        for (PurchaseOrder po : context.purchaseOrders()) {
            if (po.amount().isValue()) {
                Paise poTotal = po.amount().getOrThrow();
                if (!poTotal.equals(invTotal)) {
                    // Check if change-orders reconcile the delta
                    Paise delta = invTotal.minus(poTotal);
                    boolean coveredByChangeOrder = context.changeOrders().stream().anyMatch(co ->
                        co.poNumber().equals(po.poNumber())
                            && co.adjustedAmount().map(amt -> amt.equals(invTotal) || amt.equals(delta)).orElse(false)
                    );

                    if (!coveredByChangeOrder) {
                        List<String> evidence = List.of(po.id(), inv.id());
                        String summary = String.format(
                            "Purchase order %s amount %s differs from invoice %s amount %s with no change-order document",
                            po.poNumber(),
                            poTotal,
                            inv.referenceNumber().orElse(inv.id()),
                            invTotal
                        );
                        Map<String, String> details = Map.of(
                            "poNumber", po.poNumber(),
                            "poAmount", poTotal.toString(),
                            "invoiceAmount", invTotal.toString(),
                            "difference", delta.toString()
                        );
                        return Optional.of(Conflict.of("CONF-C3-" + po.id(), ConflictType.C3, summary, evidence, details));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * C4: Terms conflict (≥ 2 distinct payment-term values across PO/invoice/contract).
     * Rule R-17: Conflicting payment terms are presented side by side; never pick the most favourable one.
     */
    public static Optional<Conflict> detectC4(@NonNull ConflictContext context) {
        Map<String, Integer> termSources = new LinkedHashMap<>();
        List<String> evidence = new ArrayList<>();

        context.invoicePaymentTermsDays().ifValue(days -> {
            termSources.put("Invoice (" + context.invoice().referenceNumber().orElse("INV") + ")", days);
            evidence.add(context.invoice().id());
        });

        for (PurchaseOrder po : context.purchaseOrders()) {
            po.paymentTermsDays().ifValue(days -> {
                termSources.put("Purchase Order (" + po.poNumber() + ")", days);
                evidence.add(po.id());
            });
        }

        context.contractPaymentTermsDays().ifValue(days -> {
            termSources.put("Contract", days);
        });

        // Check if there are >= 2 distinct day values
        long distinctDayCount = termSources.values().stream().distinct().count();
        if (distinctDayCount >= 2) {
            Map<String, String> sideBySideTerms = new LinkedHashMap<>();
            termSources.forEach((source, days) -> sideBySideTerms.put(source, days + " days"));

            String summary = "Payment terms conflict: multiple conflicting terms found: " + sideBySideTerms;
            return Optional.of(Conflict.of("CONF-C4-" + context.invoice().id(), ConflictType.C4, summary, evidence, sideBySideTerms));
        }

        return Optional.empty();
    }

    /**
     * C5: Delivery evidence missing (Invoice present; no delivery/acceptance document).
     */
    public static Optional<Conflict> detectC5(@NonNull ConflictContext context) {
        if (context.deliveryDocuments().isEmpty()) {
            List<String> evidence = List.of(context.invoice().id());
            String summary = "Invoice present but no delivery challan, transport receipt, or proof of acceptance found";
            return Optional.of(Conflict.of("CONF-C5-" + context.invoice().id(), ConflictType.C5, summary, evidence));
        }
        return Optional.empty();
    }

    /**
     * C6: Duplicate suspect (Same supplier, invoice no, amount or same bank txn id twice).
     */
    public static Optional<Conflict> detectC6(@NonNull ConflictContext context) {
        Map<String, LedgerEntry> seenTxns = new HashMap<>();

        for (LedgerEntry e : context.entries()) {
            String ref = e.referenceNumber().orNull();
            if (ref != null && !ref.isBlank() && e.type() == LedgerEntryType.PAYMENT) {
                if (seenTxns.containsKey(ref)) {
                    LedgerEntry original = seenTxns.get(ref);
                    List<String> evidence = List.of(original.id(), e.id());
                    String summary = String.format(
                        "Duplicate suspect: payment transaction reference '%s' appears on both %s and %s",
                        ref,
                        original.id(),
                        e.id()
                    );
                    Map<String, String> details = Map.of(
                        "reference", ref,
                        "entry1", original.id(),
                        "entry2", e.id()
                    );
                    return Optional.of(Conflict.of("CONF-C6-" + ref, ConflictType.C6, summary, evidence, details));
                }
                seenTxns.put(ref, e);
            }
        }
        return Optional.empty();
    }

    /**
     * C7: Quantity/quality dispute (Quantity delta from documents; quality claim from message).
     */
    public static Optional<Conflict> detectC7(@NonNull ConflictContext context) {
        // 1. Check quantity delta in delivery documents
        for (DeliveryDocument del : context.deliveryDocuments()) {
            if (del.quantityOrdered().isValue() && del.quantityDelivered().isValue()) {
                int ordered = del.quantityOrdered().getOrThrow();
                int delivered = del.quantityDelivered().getOrThrow();
                if (delivered < ordered) {
                    List<String> evidence = List.of(del.id(), context.invoice().id());
                    String summary = String.format(
                        "Quantity shortage dispute: ordered %d units, delivered %d units (shortfall %d) on document %s",
                        ordered,
                        delivered,
                        ordered - delivered,
                        del.documentNumber()
                    );
                    return Optional.of(Conflict.of("CONF-C7-QTY-" + del.id(), ConflictType.C7, summary, evidence));
                }
            }
            if (del.isDisputed()) {
                List<String> evidence = List.of(del.id());
                String summary = "Delivery document " + del.documentNumber() + " is marked disputed";
                return Optional.of(Conflict.of("CONF-C7-DEL-" + del.id(), ConflictType.C7, summary, evidence));
            }
        }

        // 2. Check quality/defect claims from buyer communications
        for (Allegation a : context.allegations()) {
            if (a.type() == Allegation.AllegationType.QUALITY_DEFECT
                || a.type() == Allegation.AllegationType.QUANTITY_SHORTAGE) {
                List<String> evidence = List.of(a.id());
                String summary = String.format("Buyer quality/defect allegation: %s (%s)", a.description(), a.type());
                return Optional.of(Conflict.of("CONF-C7-ALG-" + a.id(), ConflictType.C7, summary, evidence));
            }
        }

        return Optional.empty();
    }

    /**
     * C8: Date contradiction (Order violations among document dates and message-stated dates).
     */
    public static Optional<Conflict> detectC8(@NonNull ConflictContext context) {
        LedgerEntry inv = context.invoice();
        LocalDate invDate = inv.date().orNull();

        // Violation 1: Payment date strictly before invoice date
        if (invDate != null) {
            for (LedgerEntry e : context.entries()) {
                if (e.type() == LedgerEntryType.PAYMENT && e.date().isValue()) {
                    LocalDate payDate = e.date().getOrThrow();
                    if (payDate.isBefore(invDate)) {
                        List<String> evidence = List.of(e.id(), inv.id());
                        String summary = String.format(
                            "Date contradiction: payment %s dated %s is before invoice %s dated %s",
                            e.id(),
                            payDate,
                            inv.referenceNumber().orElse(inv.id()),
                            invDate
                        );
                        return Optional.of(Conflict.of("CONF-C8-PAY-" + e.id(), ConflictType.C8, summary, evidence));
                    }
                }
            }
        }

        // Violation 2: Delivery date before PO date
        for (DeliveryDocument del : context.deliveryDocuments()) {
            LocalDate delDate = del.deliveryDate().orNull();
            if (delDate != null) {
                for (PurchaseOrder po : context.purchaseOrders()) {
                    LocalDate poDate = po.date().orNull();
                    if (poDate != null && delDate.isBefore(poDate)) {
                        List<String> evidence = List.of(del.id(), po.id());
                        String summary = String.format(
                            "Date contradiction: delivery %s dated %s is prior to purchase order %s dated %s",
                            del.documentNumber(),
                            delDate,
                            po.poNumber(),
                            poDate
                        );
                        return Optional.of(Conflict.of("CONF-C8-DEL-" + del.id(), ConflictType.C8, summary, evidence));
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * C9: Short-payment deduction (Payment < expected and remittance advice itemises deduction).
     * Rule R-18: C9 is classified from document evidence only; the system never computes tax amounts or rates.
     */
    public static Optional<Conflict> detectC9(@NonNull ConflictContext context) {
        for (RemittanceAdvice ra : context.remittanceAdvices()) {
            if (!ra.deductions().isEmpty()) {
                List<String> evidence = List.of(ra.id(), ra.paymentId());
                Map<String, String> itemizedDeductions = new LinkedHashMap<>();

                for (RemittanceDeduction d : ra.deductions()) {
                    // Record strictly as stated in documentary evidence; NO tax rate or amount computation (R-18)
                    itemizedDeductions.put(d.description(), d.amount().map(Paise::toString).orElse("unknown"));
                }

                String summary = String.format(
                    "Short-payment deduction itemized in remittance advice %s: %s",
                    ra.id(),
                    itemizedDeductions
                );

                return Optional.of(Conflict.of("CONF-C9-" + ra.id(), ConflictType.C9, summary, evidence, itemizedDeductions));
            }
        }
        return Optional.empty();
    }

    /**
     * C10: Unallocated lump-sum payment (One payment references several invoices or none).
     */
    public static Optional<Conflict> detectC10(@NonNull ConflictContext context) {
        for (LedgerEntry e : context.entries()) {
            if (e.type() == LedgerEntryType.PAYMENT) {
                if (e.linkedInvoiceNumber().isUnknown()) {
                    List<String> evidence = List.of(e.id());
                    String summary = String.format(
                        "Unallocated lump-sum payment: payment %s (%s) references no invoice",
                        e.referenceNumber().orElse(e.id()),
                        e.amount().map(Paise::toString).orElse("unknown amount")
                    );
                    return Optional.of(Conflict.of("CONF-C10-NONE-" + e.id(), ConflictType.C10, summary, evidence));
                }

                String linked = e.linkedInvoiceNumber().getOrThrow();
                // Check if multiple invoices are referenced (e.g. contains comma or semicolon)
                if (linked.contains(",") || linked.contains(";") || linked.contains(" and ") || linked.contains("&")) {
                    List<String> evidence = List.of(e.id());
                    String summary = String.format(
                        "Unallocated lump-sum payment: payment %s references multiple invoices: '%s'",
                        e.referenceNumber().orElse(e.id()),
                        linked
                    );
                    return Optional.of(Conflict.of("CONF-C10-MULTI-" + e.id(), ConflictType.C10, summary, evidence));
                }
            }
        }
        return Optional.empty();
    }
}
