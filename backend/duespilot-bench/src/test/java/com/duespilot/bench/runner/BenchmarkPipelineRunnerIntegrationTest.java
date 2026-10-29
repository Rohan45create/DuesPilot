package com.duespilot.bench.runner;

import com.duespilot.bench.model.AggregateBenchmarkScore;
import com.duespilot.bench.model.BenchmarkConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Benchmark Pipeline Runner Integration Tests (Section 14 & Rules R-62 to R-64)")
class BenchmarkPipelineRunnerIntegrationTest {

    @Test
    @DisplayName("Pipeline executes over benchmark-data/generated (40 synthetic cases) with full deterministic scoring")
    void testRunGeneratedDataset() {
        BenchmarkConfig config = BenchmarkConfig.defaults();
        BenchmarkPipelineRunner runner = new BenchmarkPipelineRunner();

        AggregateBenchmarkScore score = runner.runBenchmark(config);

        assertThat(score.totalCases()).isEqualTo(40);
        assertThat(score.balanceMatches()).isEqualTo(40);
        assertThat(score.balanceMatchRate()).isEqualTo(1.0);
        assertThat(score.meanConflictRecall()).isGreaterThanOrEqualTo(0.9);
        assertThat(score.meanFalseConflictRate()).isLessThanOrEqualTo(0.1);
        assertThat(score.meanUnsupportedClaimRate()).isEqualTo(0.0);
        assertThat(score.meanAbstentionAccuracy()).isGreaterThanOrEqualTo(0.9);
        assertThat(score.meanFieldAccuracy()).isGreaterThanOrEqualTo(0.9);

        // Tiered vs All-Ultra cost comparison
        assertThat(score.totalCostMicros()).isPositive();
        assertThat(score.totalAllUltraCostMicros()).isGreaterThan(score.totalCostMicros());
        assertThat(score.overallCostSavingsMultiplier()).isGreaterThan(1.5);

        // Experiment C: Language groups reported separately
        assertThat(score.languageBreakdown()).containsKeys("EN", "HI", "HINGLISH");
        assertThat(score.languageBreakdown().get("EN").caseCount()).isPositive();
        assertThat(score.languageBreakdown().get("HI").caseCount()).isPositive();
        assertThat(score.languageBreakdown().get("HINGLISH").caseCount()).isPositive();
    }

    @Test
    @DisplayName("Pipeline executes over benchmark-data/holdout (8 hand-written cases) with full deterministic scoring")
    void testRunHoldoutDataset() {
        BenchmarkConfig config = BenchmarkConfig.holdout();
        BenchmarkPipelineRunner runner = new BenchmarkPipelineRunner();

        AggregateBenchmarkScore score = runner.runBenchmark(config);

        assertThat(score.totalCases()).isEqualTo(8);
        assertThat(score.balanceMatches()).isEqualTo(8);
        assertThat(score.balanceMatchRate()).isEqualTo(1.0);
        assertThat(score.meanConflictRecall()).isGreaterThanOrEqualTo(0.9);
        assertThat(score.meanUnsupportedClaimRate()).isEqualTo(0.0);
        assertThat(score.meanAbstentionAccuracy()).isGreaterThanOrEqualTo(0.9);
    }
}
