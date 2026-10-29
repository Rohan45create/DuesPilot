package com.duespilot.bench.job;

import com.duespilot.api.repository.BenchmarkRunRepository;
import com.duespilot.bench.model.AggregateBenchmarkScore;
import com.duespilot.bench.model.BenchmarkConfig;
import com.duespilot.bench.runner.BenchmarkPipelineRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;

/**
 * Spring CommandLineRunner activated under the "benchmark" profile.
 * Enables the Docker container to be executed directly as a Nebius Serverless Job
 * per architecture §14, §15, and rules R-62 to R-64.
 */
@Component
@Profile("benchmark")
public class BenchmarkJobRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkJobRunner.class);

    private final BenchmarkRunRepository runRepository;

    @Autowired
    public BenchmarkJobRunner(BenchmarkRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("=== Starting DuesPilot Benchmark Job (Profile: benchmark) ===");

        BenchmarkPipelineRunner runner = new BenchmarkPipelineRunner(runRepository);

        // 1. Run over benchmark-data/generated (synthetic, 40 cases)
        log.info("--- Phase 1: Evaluating generated dataset ---");
        BenchmarkConfig genConfig = BenchmarkConfig.defaults();
        AggregateBenchmarkScore genScore = runner.runBenchmark(genConfig);

        // 2. Run over benchmark-data/holdout (hand-written, 8 cases)
        log.info("--- Phase 2: Evaluating holdout dataset ---");
        BenchmarkConfig holdoutConfig = BenchmarkConfig.holdout();
        AggregateBenchmarkScore holdoutScore = runner.runBenchmark(holdoutConfig);

        log.info("=== DuesPilot Benchmark Job Completed Successfully ===");
        log.info("Generated pass rate: {}/{} ({}%) | Holdout pass rate: {}/{} ({}%)",
            genScore.passedCases(), genScore.totalCases(), String.format("%.1f", genScore.passRate() * 100.0),
            holdoutScore.passedCases(), holdoutScore.totalCases(), String.format("%.1f", holdoutScore.passRate() * 100.0));
    }
}
