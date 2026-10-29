package com.duespilot.bench;

import com.duespilot.bench.model.BenchmarkConfig;
import com.duespilot.bench.runner.BenchmarkPipelineRunner;

import java.nio.file.Paths;
import java.util.HashMap;

/**
 * Main CLI entry point for duespilot-bench.
 * Supports:
 *   bench [--dataset generated|holdout|all] [--policy tiered|all_ultra] [--thinking true|false] [--whole-folder]
 *   generate --seed N --count 40 --out benchmark-data/generated
 *   extract --case GEN-001
 */
public final class DuesPilotBench {

    public static void main(String[] args) {
        if (args.length > 0) {
            String cmd = args[0].toLowerCase();
            if ("extract".equals(cmd)) {
                com.duespilot.bench.runner.CaseExtractionBenchmarkRunner.main(args);
                return;
            } else if ("generate".equals(cmd)) {
                com.duespilot.bench.generator.BenchmarkGeneratorCli.main(args);
                return;
            } else if ("bench".equals(cmd) || "run".equals(cmd)) {
                runCliBenchmark(args);
                return;
            }
        }
        runCliBenchmark(args);
    }

    private static void runCliBenchmark(String[] args) {
        String dataset = "all";
        String policy = "tiered";
        Boolean thinking = null;
        boolean wholeFolder = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if ("--dataset".equals(a) && i + 1 < args.length) {
                dataset = args[++i].toLowerCase();
            } else if ("--policy".equals(a) && i + 1 < args.length) {
                policy = args[++i].toLowerCase();
            } else if ("--thinking".equals(a) && i + 1 < args.length) {
                thinking = Boolean.parseBoolean(args[++i]);
            } else if ("--whole-folder".equals(a)) {
                wholeFolder = true;
            }
        }

        BenchmarkPipelineRunner runner = new BenchmarkPipelineRunner();

        if ("generated".equals(dataset) || "all".equals(dataset)) {
            BenchmarkConfig gen = new BenchmarkConfig(
                Paths.get("benchmark-data", "generated"),
                "generated", "v1.0", policy, thinking, wholeFolder, false, null, new HashMap<>()
            );
            runner.runBenchmark(gen);
        }

        if ("holdout".equals(dataset) || "all".equals(dataset)) {
            BenchmarkConfig holdout = new BenchmarkConfig(
                Paths.get("benchmark-data", "holdout"),
                "holdout", "v1.0", policy, thinking, wholeFolder, false, null, new HashMap<>()
            );
            runner.runBenchmark(holdout);
        }
    }

    public static String name() {
        return "duespilot-bench";
    }
}
