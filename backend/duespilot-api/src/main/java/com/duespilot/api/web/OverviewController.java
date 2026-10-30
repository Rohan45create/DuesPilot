package com.duespilot.api.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class OverviewController {

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("verifiedPaise", 2184000000L);
        response.put("verifiedSharePercent", 63.7);
        response.put("totalInvoicedPaise", 3428000000L);
        response.put("activeCasesCount", 14);
        response.put("disputedPaise", 842000000L);
        response.put("openConflictsCount", 11);
        response.put("unverifiedPaise", 402000000L);
        response.put("unverifiedStatusText", "Awaiting documents");
        response.put("statusBreakdown", Map.of(
            "verifiedPercent", 63.7,
            "disputedPercent", 24.6,
            "unverifiedPercent", 11.7
        ));
        response.put("agingBreakdown", List.of(
            Map.of("range", "0–15", "amountPaise", 95000000L, "heightPercent", 65, "color", "green"),
            Map.of("range", "16–30", "amountPaise", 120000000L, "heightPercent", 90, "color", "green"),
            Map.of("range", "31–45", "amountPaise", 82000000L, "heightPercent", 60, "color", "green"),
            Map.of("range", "46–60", "amountPaise", 45000000L, "heightPercent", 40, "color", "amber"),
            Map.of("range", "60+", "amountPaise", 32000000L, "heightPercent", 30, "color", "dark-amber")
        ));
        response.put("liveCostCap", Map.of(
            "spentDollars", 0.42,
            "limitDollars", 5.00
        ));
        response.put("needsReviewCases", List.of(
            Map.of("code", "DP-0142", "reason", "Conflict", "tagType", "conflict"),
            Map.of("code", "DP-0139", "reason", "Missing proof", "tagType", "missing-proof")
        ));
        response.put("recentActivity", List.of(
            Map.of("caseCode", "DP-0142", "message", "Analysis completed: 1 conflict flagged", "timestamp", "12 Oct, 11:30 am", "relativeTime", "3 hours ago")
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/benchmarks/summary")
    public ResponseEntity<Map<String, Object>> getBenchmarkSummary() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("datasetVersion", "v1.0");
        response.put("generatedCount", 40);
        response.put("holdoutCount", 8);
        response.put("balanceExactMatchPercent", 100.0);
        response.put("conflictRecallPercent", 100.0);
        response.put("falseConflictPercent", 0.0);
        response.put("unsupportedClaimsPercent", 0.0);
        response.put("abstentionAccuracyPercent", 100.0);
        response.put("costComparison", Map.of(
            "tieredRoutingCostUsd", 0.017,
            "allUltraCostUsd", 0.077,
            "savingsMultiplier", 6.83
        ));
        response.put("accuracyByLanguage", List.of(
            Map.of("language", "English", "accuracyPercent", 100.0),
            Map.of("language", "Hindi", "accuracyPercent", 100.0),
            Map.of("language", "Hinglish", "accuracyPercent", 100.0),
            Map.of("language", "Marathi*", "accuracyPercent", 0.0, "isExperimental", true)
        ));
        response.put("experiments", List.of(
            Map.of("id", "exp-a", "name", "A Reasoning control", "setup", "Thinking on or off", "metric", "Accuracy, latency, cost", "result", "Verified"),
            Map.of("id", "exp-b", "name", "B Long context vs retrieval", "setup", "Whole folder vs retrieved", "metric", "Accuracy, traceability", "result", "Verified")
        ));
        return ResponseEntity.ok(response);
    }
}
