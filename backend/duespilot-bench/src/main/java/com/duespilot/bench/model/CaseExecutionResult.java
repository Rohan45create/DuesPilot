package com.duespilot.bench.model;

import com.duespilot.core.conflict.Conflict;
import com.duespilot.core.extract.VerifiedField;
import com.duespilot.core.gate.GateResult;
import com.duespilot.core.gate.GateStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Output of a case pipeline execution passed to the deterministic scorer.
 */
public record CaseExecutionResult(
    @NonNull String caseCode,
    @Nullable Long actualBalancePaise,
    @NonNull String reconciliationStatus,
    @NonNull List<String> detectedConflicts,
    @NonNull Map<String, String> gateStatuses,
    boolean interestComputed,
    @Nullable Long interestPaise,
    @Nullable String interestRefusalReason,
    @NonNull List<VerifiedField> verifiedFields,
    @NonNull List<SemanticClaim> claimsMade,
    long costMicros,
    int latencyMs,
    int inputTokens,
    int outputTokens,
    int reasoningTokens,
    @NonNull String language,
    @NonNull String modelUsed
) {
    public record SemanticClaim(
        @NonNull String text,
        @NonNull String quote,
        int page,
        int charStart,
        int charEnd,
        boolean spanValid
    ) {}
}
