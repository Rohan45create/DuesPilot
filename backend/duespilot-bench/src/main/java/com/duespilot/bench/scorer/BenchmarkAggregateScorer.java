package com.duespilot.bench.scorer;

import com.duespilot.bench.model.AggregateBenchmarkScore;
import com.duespilot.bench.model.AggregateBenchmarkScore.LanguageMetrics;
import com.duespilot.bench.model.BenchmarkConfig;
import com.duespilot.bench.model.CaseScore;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregates individual CaseScores across a dataset run per architecture §14 and rules R-62, R-63.
 * Computes mean rates, language breakdowns (Experiment C), and tiered vs all-Ultra cost savings.
 */
public final class BenchmarkAggregateScorer {

    public static @NonNull AggregateBenchmarkScore aggregate(
            @NonNull List<CaseScore> scores,
            @NonNull BenchmarkConfig config) {

        int totalCases = scores.size();
        if (totalCases == 0) {
            return new AggregateBenchmarkScore(
                config.datasetName(),
                config.datasetVersion(),
                0, 0, 0.0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0L, 0L, 0L, 1.0, 0, 0,
                Map.of(), Map.of(), List.of()
            );
        }

        int passedCases = (int) scores.stream().filter(CaseScore::passed).count();
        double passRate = (double) passedCases / totalCases;

        int balanceMatches = (int) scores.stream().filter(CaseScore::balanceMatch).count();
        double balanceMatchRate = (double) balanceMatches / totalCases;

        double meanConflictRecall = scores.stream().mapToDouble(CaseScore::conflictRecall).average().orElse(0.0);
        double meanFalseConflictRate = scores.stream().mapToDouble(CaseScore::falseConflictRate).average().orElse(0.0);
        double meanUnsupportedClaimRate = scores.stream().mapToDouble(CaseScore::unsupportedClaimRate).average().orElse(0.0);
        double meanAbstentionAccuracy = scores.stream().mapToDouble(CaseScore::abstentionAccuracy).average().orElse(0.0);
        double meanFieldAccuracy = scores.stream().mapToDouble(CaseScore::fieldAccuracy).average().orElse(0.0);

        long totalCostMicros = scores.stream().mapToLong(CaseScore::costMicros).sum();
        long avgCostMicrosPerCase = totalCostMicros / totalCases;

        long totalAllUltraCostMicros = scores.stream().mapToLong(CaseScore::allUltraCostMicros).sum();
        double overallCostSavingsMultiplier = totalCostMicros > 0
            ? ((double) totalAllUltraCostMicros / totalCostMicros)
            : 1.0;

        int totalLatencyMs = scores.stream().mapToInt(CaseScore::latencyMs).sum();
        int avgLatencyMsPerCase = totalLatencyMs / totalCases;

        // Experiment C: Breakdown by language (EN, HI, HINGLISH)
        Map<String, List<CaseScore>> byLanguage = scores.stream()
            .collect(Collectors.groupingBy(s -> s.language().toUpperCase(Locale.ROOT)));

        Map<String, LanguageMetrics> languageBreakdown = new HashMap<>();
        for (Map.Entry<String, List<CaseScore>> entry : byLanguage.entrySet()) {
            String lang = entry.getKey();
            List<CaseScore> langScores = entry.getValue();
            int langTotal = langScores.size();
            int langBalMatch = (int) langScores.stream().filter(CaseScore::balanceMatch).count();
            double langBalRate = (double) langBalMatch / langTotal;
            double langRecall = langScores.stream().mapToDouble(CaseScore::conflictRecall).average().orElse(0.0);
            double langFieldAcc = langScores.stream().mapToDouble(CaseScore::fieldAccuracy).average().orElse(0.0);

            languageBreakdown.put(lang, new LanguageMetrics(
                langTotal,
                langBalMatch,
                langBalRate,
                langRecall,
                langFieldAcc
            ));
        }

        // Experiment Metrics
        Map<String, Object> experimentMetrics = new HashMap<>();
        if (config.experimentAThinkingEnabled() != null) {
            experimentMetrics.put("experiment_a_thinking_enabled", config.experimentAThinkingEnabled());
        }
        experimentMetrics.put("experiment_b_whole_folder", config.experimentBWholeFolder());
        experimentMetrics.put("router_policy", config.routerPolicy());
        experimentMetrics.put("tiered_cost_micros", totalCostMicros);
        experimentMetrics.put("all_ultra_cost_micros", totalAllUltraCostMicros);
        experimentMetrics.put("all_ultra_cost_savings_multiplier", overallCostSavingsMultiplier);

        return new AggregateBenchmarkScore(
            config.datasetName(),
            config.datasetVersion(),
            totalCases,
            passedCases,
            passRate,
            balanceMatches,
            balanceMatchRate,
            meanConflictRecall,
            meanFalseConflictRate,
            meanUnsupportedClaimRate,
            meanAbstentionAccuracy,
            meanFieldAccuracy,
            totalCostMicros,
            avgCostMicrosPerCase,
            totalAllUltraCostMicros,
            overallCostSavingsMultiplier,
            totalLatencyMs,
            avgLatencyMsPerCase,
            languageBreakdown,
            experimentMetrics,
            scores
        );
    }
}
