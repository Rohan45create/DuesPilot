package com.duespilot.bench.scorer;

import com.duespilot.bench.model.CaseExecutionResult;
import com.duespilot.bench.model.CaseExecutionResult.SemanticClaim;
import com.duespilot.bench.model.CaseScore;
import com.duespilot.bench.model.GroundTruth;
import com.duespilot.core.extract.VerifiedField;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic scorer for individual benchmark cases against ground truth.
 * Rules R-32, R-62: Deterministic scoring only; no model grades another model.
 */
public final class BenchmarkCaseScorer {

    public static @NonNull CaseScore score(@NonNull CaseExecutionResult execution, @NonNull GroundTruth groundTruth) {
        // 1. Reconciled-balance exact match (Paise exact match, null if uncomputable)
        Long expectedBal = groundTruth.financials().expectedBalancePaise();
        Long actualBal = execution.actualBalancePaise();
        boolean balanceMatch = (expectedBal == null && actualBal == null)
            || (expectedBal != null && actualBal != null && expectedBal.longValue() == actualBal.longValue());

        // 2. Conflict Recall and False-Conflict Rate
        List<String> expectedConflicts = groundTruth.expectedConflicts().stream()
            .map(GroundTruth.ExpectedConflict::ruleId)
            .map(s -> s.toUpperCase(Locale.ROOT))
            .distinct()
            .toList();

        List<String> detectedConflicts = execution.detectedConflicts().stream()
            .map(s -> s.toUpperCase(Locale.ROOT))
            .distinct()
            .toList();

        Set<String> expectedSet = new HashSet<>(expectedConflicts);
        Set<String> detectedSet = new HashSet<>(detectedConflicts);

        List<String> matchedConflicts = expectedConflicts.stream()
            .filter(detectedSet::contains)
            .toList();
        List<String> missingConflicts = expectedConflicts.stream()
            .filter(c -> !detectedSet.contains(c))
            .toList();
        List<String> spuriousConflicts = detectedConflicts.stream()
            .filter(c -> !expectedSet.contains(c))
            .toList();

        double conflictRecall = expectedConflicts.isEmpty() ? 1.0 : ((double) matchedConflicts.size() / expectedConflicts.size());
        double falseConflictRate = detectedConflicts.isEmpty() ? 0.0 : ((double) spuriousConflicts.size() / detectedConflicts.size());

        // 3. Unsupported-claim rate
        List<String> unsupportedClaims = new ArrayList<>();
        List<String> prohibitedPatterns = groundTruth.claimsMustNotMake();

        for (SemanticClaim claim : execution.claimsMade()) {
            boolean prohibited = isProhibited(claim.text(), prohibitedPatterns);
            if (!claim.spanValid() || prohibited) {
                unsupportedClaims.add(claim.text() + (prohibited ? " [PROHIBITED]" : " [INVALID_SPAN]"));
            }
        }
        double unsupportedClaimRate = execution.claimsMade().isEmpty()
            ? 0.0
            : ((double) unsupportedClaims.size() / execution.claimsMade().size());

        // 4. Abstention Accuracy across Gate A, B, C, D and Interest
        Map<String, Boolean> gateMatches = new LinkedHashMap<>();
        gateMatches.put("gate_a", checkGateStatus(groundTruth.gateStatuses().gateA(), execution.gateStatuses().get("gate_a")));
        gateMatches.put("gate_b", checkGateStatus(groundTruth.gateStatuses().gateB(), execution.gateStatuses().get("gate_b")));
        gateMatches.put("gate_c", checkGateStatus(groundTruth.gateStatuses().gateC(), execution.gateStatuses().get("gate_c")));
        gateMatches.put("gate_d", checkGateStatus(groundTruth.gateStatuses().gateD(), execution.gateStatuses().get("gate_d")));

        boolean expectedInterestAbstention = !"ELIGIBLE_FOR_REVIEW".equalsIgnoreCase(groundTruth.gateStatuses().gateD());
        boolean actualInterestAbstention = !execution.interestComputed();
        gateMatches.put("interest_abstention", expectedInterestAbstention == actualInterestAbstention);

        long matchedGatesCount = gateMatches.values().stream().filter(Boolean::booleanValue).count();
        double abstentionAccuracy = (double) matchedGatesCount / gateMatches.size();

        // 5. Field Accuracy
        Map<String, VerifiedField> extractedMap = new HashMap<>();
        for (VerifiedField vf : execution.verifiedFields()) {
            extractedMap.put(vf.fieldName().toUpperCase(Locale.ROOT), vf);
        }

        int totalFields = 0;
        int matchedFields = 0;

        for (GroundTruth.DocumentMeta doc : groundTruth.documents()) {
            String docType = doc.docType().toUpperCase(Locale.ROOT);
            if ("INVOICE".equals(docType)) {
                totalFields += 3;
                if (doc.docReference() != null && checkField(extractedMap.get("INVOICE_NUMBER"), doc.docReference())) matchedFields++;
                if (doc.amountPaise() != null && checkFieldAmount(extractedMap.get("TOTAL_AMOUNT"), doc.amountPaise())) matchedFields++;
                if (doc.docDate() != null && checkFieldDate(extractedMap.get("INVOICE_DATE"), doc.docDate())) matchedFields++;
            } else if ("PO".equals(docType)) {
                totalFields += 2;
                if (doc.docReference() != null && checkField(extractedMap.get("PO_NUMBER"), doc.docReference())) matchedFields++;
                if (doc.amountPaise() != null && checkFieldAmount(extractedMap.get("TOTAL_AMOUNT"), doc.amountPaise())) matchedFields++;
            } else if ("PAYMENT".equals(docType)) {
                totalFields += 2;
                if (doc.docReference() != null && checkField(extractedMap.get("UTR_NUMBER"), doc.docReference())) matchedFields++;
                if (doc.amountPaise() != null && checkFieldAmount(extractedMap.get("PAYMENT_AMOUNT"), doc.amountPaise())) matchedFields++;
            } else if ("DELIVERY".equals(docType)) {
                totalFields += 1;
                if (doc.docReference() != null && checkField(extractedMap.get("DELIVERY_CHALLAN"), doc.docReference())) matchedFields++;
            }
        }
        double fieldAccuracy = totalFields > 0 ? ((double) matchedFields / totalFields) : 1.0;

        // 6. Tiered vs All-Ultra Hypothetical Cost
        // Ultra rates: $1.00 / 1M input tokens (= 1 micro/token), $3.00 / 1M output tokens (= 3 micros/token)
        long allUltraCostMicros = ((long) execution.inputTokens() * 1L)
            + (((long) execution.outputTokens() + execution.reasoningTokens()) * 3L);
        double costSavingsMultiplier = execution.costMicros() > 0
            ? ((double) allUltraCostMicros / execution.costMicros())
            : 1.0;

        // 7. Overall Passed status
        boolean passed = balanceMatch
            && conflictRecall >= 0.8
            && unsupportedClaimRate == 0.0
            && abstentionAccuracy >= 0.8;

        return new CaseScore(
            execution.caseCode(),
            groundTruth.category(),
            execution.language(),
            balanceMatch,
            expectedBal,
            actualBal,
            conflictRecall,
            falseConflictRate,
            expectedConflicts,
            detectedConflicts,
            missingConflicts,
            spuriousConflicts,
            unsupportedClaimRate,
            unsupportedClaims,
            abstentionAccuracy,
            gateMatches,
            fieldAccuracy,
            matchedFields,
            totalFields,
            execution.costMicros(),
            execution.latencyMs(),
            allUltraCostMicros,
            costSavingsMultiplier,
            passed
        );
    }

    private static boolean checkGateStatus(String expected, String actual) {
        if (expected == null && actual == null) return true;
        if (expected == null || actual == null) return false;
        return expected.trim().equalsIgnoreCase(actual.trim());
    }

    private static boolean isProhibited(String claimText, List<String> prohibitedPatterns) {
        if (claimText == null || prohibitedPatterns == null) return false;
        String lowerClaim = claimText.toLowerCase(Locale.ROOT);
        for (String pattern : prohibitedPatterns) {
            if (pattern != null && !pattern.isBlank() && lowerClaim.contains(pattern.toLowerCase(Locale.ROOT).trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkField(VerifiedField vf, String expectedText) {
        if (vf == null || !vf.isVerified() || vf.valueText() == null) return false;
        return vf.valueText().toLowerCase(Locale.ROOT).contains(expectedText.toLowerCase(Locale.ROOT));
    }

    private static boolean checkFieldAmount(VerifiedField vf, long expectedPaise) {
        if (vf == null || !vf.isVerified() || !vf.valuePaise().isValue()) return false;
        return vf.valuePaise().getOrThrow().value() == expectedPaise;
    }

    private static boolean checkFieldDate(VerifiedField vf, String expectedDateIso) {
        if (vf == null || !vf.isVerified() || !vf.valueDate().isValue()) return false;
        return vf.valueDate().getOrThrow().toString().equals(expectedDateIso);
    }
}
