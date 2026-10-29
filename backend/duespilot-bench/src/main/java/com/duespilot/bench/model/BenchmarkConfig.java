package com.duespilot.bench.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for a benchmark execution run per architecture §14.
 */
public record BenchmarkConfig(
    @NonNull Path datasetPath,
    @NonNull String datasetName,
    @NonNull String datasetVersion,
    @NonNull String routerPolicy,
    @Nullable Boolean experimentAThinkingEnabled,
    boolean experimentBWholeFolder,
    boolean mock,
    @Nullable String filterCategory,
    @NonNull Map<String, Object> extraFlags
) {
    public static BenchmarkConfig defaults() {
        return new BenchmarkConfig(
            Paths.get("benchmark-data", "generated"),
            "generated",
            "v1.0",
            "tiered",
            null,
            false,
            false,
            null,
            new HashMap<>()
        );
    }

    public static BenchmarkConfig holdout() {
        return new BenchmarkConfig(
            Paths.get("benchmark-data", "holdout"),
            "holdout",
            "v1.0",
            "tiered",
            null,
            false,
            false,
            null,
            new HashMap<>()
        );
    }
}
