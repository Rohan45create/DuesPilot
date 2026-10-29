package com.duespilot.bench.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

/**
 * Aggregated benchmark evaluation metrics across a dataset run per architecture §14 and rules R-62, R-63.
 */
public record AggregateBenchmarkScore(
    @NonNull String datasetName,
    @NonNull String datasetVersion,
    int totalCases,
    int passedCases,
    double passRate,
    int balanceMatches,
    double balanceMatchRate,
    double meanConflictRecall,
    double meanFalseConflictRate,
    double meanUnsupportedClaimRate,
    double meanAbstentionAccuracy,
    double meanFieldAccuracy,
    long totalCostMicros,
    long avgCostMicrosPerCase,
    long totalAllUltraCostMicros,
    double overallCostSavingsMultiplier,
    int totalLatencyMs,
    int avgLatencyMsPerCase,
    @NonNull Map<String, LanguageMetrics> languageBreakdown,
    @NonNull Map<String, Object> experimentMetrics,
    @NonNull List<CaseScore> caseScores
) {
    public record LanguageMetrics(
        int caseCount,
        int balanceMatches,
        double balanceMatchRate,
        double meanConflictRecall,
        double meanFieldAccuracy
    ) {}
}
