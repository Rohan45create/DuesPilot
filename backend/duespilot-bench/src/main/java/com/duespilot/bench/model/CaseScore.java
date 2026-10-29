package com.duespilot.bench.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Deterministic evaluation score for a single benchmark case against ground truth.
 * Rules R-32, R-62: deterministic, no model grades another model.
 */
public record CaseScore(
    @NonNull String caseCode,
    @NonNull String category,
    @NonNull String language,
    boolean balanceMatch,
    @Nullable Long expectedBalancePaise,
    @Nullable Long actualBalancePaise,
    double conflictRecall,
    double falseConflictRate,
    @NonNull List<String> expectedConflicts,
    @NonNull List<String> detectedConflicts,
    @NonNull List<String> missingConflicts,
    @NonNull List<String> spuriousConflicts,
    double unsupportedClaimRate,
    @NonNull List<String> unsupportedClaims,
    double abstentionAccuracy,
    @NonNull Map<String, Boolean> gateMatches,
    double fieldAccuracy,
    int fieldMatches,
    int totalFields,
    long costMicros,
    int latencyMs,
    long allUltraCostMicros,
    double costSavingsMultiplier,
    boolean passed
) {}
