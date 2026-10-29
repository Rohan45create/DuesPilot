package com.duespilot.bench.model;

import org.jspecify.annotations.NonNull;
import java.util.Map;

/**
 * In-memory representation of a benchmark case with ground truth and rendered document byte buffers.
 */
public record BenchmarkCaseData(
    @NonNull String dataset,
    @NonNull String caseCode,
    @NonNull GroundTruth groundTruth,
    @NonNull Map<String, byte[]> documentFiles
) {}
