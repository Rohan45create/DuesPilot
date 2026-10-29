package com.duespilot.bench.model;

import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Complete ground truth for a benchmark case.
 *
 * Per architecture.md section 14 and Rule R-1, R-2, R-62:
 * - All amounts in integer paise.
 * - Stored separately from input documents.
 * - Specifies expected conflicts, gate statuses, missing evidence, allowed actions,
 *   and claims the system must not make.
 */
public record GroundTruth(
    @NonNull String caseCode,
    @NonNull String title,
    @NonNull String category,
    @NonNull SupplierMeta supplier,
    @NonNull BuyerMeta buyer,
    @NonNull FinancialGroundTruth financials,
    @NonNull List<ExpectedConflict> expectedConflicts,
    @NonNull GateStatuses gateStatuses,
    @NonNull List<String> missingEvidence,
    @NonNull List<String> allowedActions,
    @NonNull List<String> claimsMustNotMake,
    @NonNull List<DocumentMeta> documents
) {
    public record SupplierMeta(
        @NonNull String name,
        @NonNull String gstin,
        @NonNull String enterpriseType,
        boolean udyamRegistered,
        @Nullable String udyamNumber
    ) {}

    public record BuyerMeta(
        @NonNull String name,
        @NonNull String gstin
    ) {}

    public record FinancialGroundTruth(
        @Nullable Long invoiceTotalPaise,
        @Nullable Long taxableAmountPaise,
        @Nullable Long taxAmountPaise,
        long confirmedPaidPaise,
        long confirmedCreditPaise,
        long unverifiedPaidPaise,
        long disputedAmountPaise,
        @Nullable Long expectedBalancePaise,
        @NonNull String reconciliationStatus
    ) {}

    public record ExpectedConflict(
        @NonNull String ruleId,
        @NonNull String name,
        @NonNull String description,
        @NonNull List<String> evidenceFileNames
    ) {}

    public record GateStatuses(
        @NonNull String gateA,
        @NonNull String gateB,
        @NonNull String gateC,
        @NonNull String gateD
    ) {}

    public record DocumentMeta(
        @NonNull String docType,
        @NonNull String fileName,
        @Nullable String docDate,
        @Nullable Long amountPaise,
        @Nullable String docReference,
        @NonNull String language
    ) {}
}
