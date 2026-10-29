package com.duespilot.bench.scorer;

import com.duespilot.bench.model.AggregateBenchmarkScore;
import com.duespilot.bench.model.BenchmarkConfig;
import com.duespilot.bench.model.CaseExecutionResult;
import com.duespilot.bench.model.CaseExecutionResult.SemanticClaim;
import com.duespilot.bench.model.CaseScore;
import com.duespilot.bench.model.GroundTruth;
import com.duespilot.core.extract.FieldStatus;
import com.duespilot.core.extract.VerifiedField;
import com.duespilot.core.model.Known;
import com.duespilot.core.model.Paise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Deterministic Benchmark Scorer Tests (Architecture §14 & Rules R-32, R-62 to R-64)")
class BenchmarkCaseScorerTest {

    private GroundTruth createSampleGroundTruth(
            Long expectedBalance,
            List<String> expectedConflicts,
            String gateDStatus,
            List<String> prohibitedClaims) {

        List<GroundTruth.ExpectedConflict> conflicts = expectedConflicts.stream()
            .map(c -> new GroundTruth.ExpectedConflict(c, "Name-" + c, "Desc-" + c, List.of("INV.pdf")))
            .toList();

        return new GroundTruth(
            "TEST-001",
            "Hand-made test case",
            "FINANCIAL_CONFLICT",
            new GroundTruth.SupplierMeta("Supplier Corp", "27AAAAA0000A1Z5", "MICRO", true, "UDYAM-MH-01-12345"),
            new GroundTruth.BuyerMeta("Buyer Ltd", "27BBBBB1111B1Z6"),
            new GroundTruth.FinancialGroundTruth(
                10000000L, 8000000L, 2000000L, 5000000L, 0L, 0L, 0L,
                expectedBalance, "PARTIAL"
            ),
            conflicts,
            new GroundTruth.GateStatuses("ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", "ELIGIBLE_FOR_REVIEW", gateDStatus),
            Collections.emptyList(),
            List.of("SEND_REMINDER"),
            prohibitedClaims,
            List.of(
                new GroundTruth.DocumentMeta("INVOICE", "INV-101.pdf", "2024-01-15", 10000000L, "INV-101", "EN"),
                new GroundTruth.DocumentMeta("PAYMENT", "PAY-201.pdf", "2024-01-20", 5000000L, "UTR-9999", "EN")
            )
        );
    }

    @Test
    @DisplayName("Reconciled-balance exact match succeeds on exact Paise match")
    void testReconciledBalanceExactMatch_Success() {
        GroundTruth gt = createSampleGroundTruth(5000000L, List.of("C1"), "ELIGIBLE_FOR_REVIEW", List.of());

        CaseExecutionResult execution = new CaseExecutionResult(
            "TEST-001",
            5000000L, // Exact match
            "PARTIAL",
            List.of("C1"),
            Map.of("gate_a", "ELIGIBLE_FOR_REVIEW", "gate_b", "ELIGIBLE_FOR_REVIEW", "gate_c", "ELIGIBLE_FOR_REVIEW", "gate_d", "ELIGIBLE_FOR_REVIEW"),
            true,
            75000L,
            null,
            List.of(
                VerifiedField.verifiedText("INVOICE_NUMBER", "INV-101", 1, 0, 10, "INV-101"),
                VerifiedField.verifiedPaise("TOTAL_AMOUNT", "10000000", Paise.ofPaise(10000000L), 1, 12, 20, "10000000"),
                VerifiedField.verifiedDate("INVOICE_DATE", "2024-01-15", LocalDate.of(2024, 1, 15), 1, 22, 32, "2024-01-15"),
                VerifiedField.verifiedText("UTR_NUMBER", "UTR-9999", 1, 0, 10, "UTR-9999"),
                VerifiedField.verifiedPaise("PAYMENT_AMOUNT", "5000000", Paise.ofPaise(5000000L), 1, 12, 20, "5000000")
            ),
            List.of(new SemanticClaim("Valid reconciliation statement", "INV-101", 1, 0, 10, true)),
            500L, 120, 400, 100, 0, "EN", "tiered"
        );

        CaseScore score = BenchmarkCaseScorer.score(execution, gt);

        assertThat(score.balanceMatch()).isTrue();
        assertThat(score.conflictRecall()).isEqualTo(1.0);
        assertThat(score.falseConflictRate()).isEqualTo(0.0);
        assertThat(score.unsupportedClaimRate()).isEqualTo(0.0);
        assertThat(score.abstentionAccuracy()).isEqualTo(1.0);
        assertThat(score.fieldAccuracy()).isEqualTo(1.0);
        assertThat(score.passed()).isTrue();
    }

    @Test
    @DisplayName("Reconciled-balance mismatch fails scoring and case passes is false")
    void testReconciledBalanceMismatch_Fails() {
        GroundTruth gt = createSampleGroundTruth(5000000L, List.of("C1"), "ELIGIBLE_FOR_REVIEW", List.of());

        CaseExecutionResult execution = new CaseExecutionResult(
            "TEST-001",
            4000000L, // Mismatched balance (off by 10,000 Rs)
            "PARTIAL",
            List.of("C1"),
            Map.of("gate_a", "ELIGIBLE_FOR_REVIEW", "gate_b", "ELIGIBLE_FOR_REVIEW", "gate_c", "ELIGIBLE_FOR_REVIEW", "gate_d", "ELIGIBLE_FOR_REVIEW"),
            true,
            75000L,
            null,
            List.of(),
            List.of(),
            500L, 120, 400, 100, 0, "EN", "tiered"
        );

        CaseScore score = BenchmarkCaseScorer.score(execution, gt);

        assertThat(score.balanceMatch()).isFalse();
        assertThat(score.passed()).isFalse();
    }

    @Test
    @DisplayName("Reconciled-balance matches when expected and actual are both null (deliberate abstention)")
    void testReconciledBalanceNullAbstention_Success() {
        GroundTruth gt = createSampleGroundTruth(null, List.of("C5"), "NOT_ESTABLISHED", List.of());

        CaseExecutionResult execution = new CaseExecutionResult(
            "TEST-001",
            null, // Deliberately uncomputed / unknown
            "UNKNOWN",
            List.of("C5"),
            Map.of("gate_a", "ELIGIBLE_FOR_REVIEW", "gate_b", "NOT_ESTABLISHED", "gate_c", "ELIGIBLE_FOR_REVIEW", "gate_d", "NOT_ESTABLISHED"),
            false, // Interest abstained
            null,
            "Gate D not established",
            List.of(),
            List.of(),
            400L, 90, 300, 80, 0, "EN", "tiered"
        );

        CaseScore score = BenchmarkCaseScorer.score(execution, gt);

        assertThat(score.balanceMatch()).isTrue();
        assertThat(score.conflictRecall()).isEqualTo(1.0);
        assertThat(score.gateMatches().get("interest_abstention")).isTrue();
    }

    @Test
    @DisplayName("Conflict recall and false-conflict rate computed accurately against expected conflicts")
    void testConflictRecallAndFalseConflictRate() {
        GroundTruth gt = createSampleGroundTruth(5000000L, List.of("C1", "C5"), "ELIGIBLE_FOR_REVIEW", List.of());

        // Detected C1 and C7 (C5 missing, C7 spurious)
        CaseExecutionResult execution = new CaseExecutionResult(
            "TEST-001",
            5000000L,
            "PARTIAL",
            List.of("C1", "C7"),
            Map.of("gate_a", "ELIGIBLE_FOR_REVIEW", "gate_b", "ELIGIBLE_FOR_REVIEW", "gate_c", "ELIGIBLE_FOR_REVIEW", "gate_d", "ELIGIBLE_FOR_REVIEW"),
            true, 50000L, null,
            List.of(), List.of(),
            500L, 100, 400, 100, 0, "EN", "tiered"
        );

        CaseScore score = BenchmarkCaseScorer.score(execution, gt);

        // Expected 2 (C1, C5), detected 1 of them (C1) -> recall 0.5 (50%)
        assertThat(score.conflictRecall()).isEqualTo(0.5);
        assertThat(score.missingConflicts()).containsExactly("C5");

        // Detected 2 (C1, C7), 1 of them spurious (C7) -> false-conflict rate 0.5 (50%)
        assertThat(score.falseConflictRate()).isEqualTo(0.5);
        assertThat(score.spuriousConflicts()).containsExactly("C7");
    }

    @Test
    @DisplayName("Unsupported-claim rate catches invalid spans and prohibited claims")
    void testUnsupportedClaimRateCatchesViolations() {
        GroundTruth gt = createSampleGroundTruth(
            5000000L, List.of("C1"), "ELIGIBLE_FOR_REVIEW",
            List.of("WhatsApp message payment is verified")
        );

        List<SemanticClaim> claims = List.of(
            new SemanticClaim("Valid invoice claim", "INV-101", 1, 0, 10, true),
            new SemanticClaim("Hallucinated claim with invalid span", "Missing text", 1, 50, 60, false), // Invalid span
            new SemanticClaim("WhatsApp message payment is verified without bank proof", "msg", 1, 0, 5, true) // Prohibited claim
        );

        CaseExecutionResult execution = new CaseExecutionResult(
            "TEST-001",
            5000000L,
            "PARTIAL",
            List.of("C1"),
            Map.of("gate_a", "ELIGIBLE_FOR_REVIEW", "gate_b", "ELIGIBLE_FOR_REVIEW", "gate_c", "ELIGIBLE_FOR_REVIEW", "gate_d", "ELIGIBLE_FOR_REVIEW"),
            true, 50000L, null,
            List.of(), claims,
            500L, 100, 400, 100, 0, "EN", "tiered"
        );

        CaseScore score = BenchmarkCaseScorer.score(execution, gt);

        // 2 of 3 claims unsupported -> rate = 2/3 (66.7%)
        assertThat(score.unsupportedClaimRate()).isCloseTo(2.0 / 3.0, within(0.001));
        assertThat(score.unsupportedClaims()).hasSize(2);
        assertThat(score.passed()).isFalse();
    }

    @Test
    @DisplayName("Abstention accuracy reflects correct gate and interest decisions")
    void testAbstentionAccuracy() {
        GroundTruth gt = createSampleGroundTruth(null, List.of(), "NOT_ESTABLISHED", List.of());

        // System erroneously computes interest when Gate D was NOT_ESTABLISHED
        CaseExecutionResult execution = new CaseExecutionResult(
            "TEST-001",
            null,
            "UNKNOWN",
            List.of(),
            Map.of("gate_a", "ELIGIBLE_FOR_REVIEW", "gate_b", "ELIGIBLE_FOR_REVIEW", "gate_c", "ELIGIBLE_FOR_REVIEW", "gate_d", "NOT_ESTABLISHED"),
            true, // Error: should have abstained
            15000L,
            null,
            List.of(), List.of(),
            500L, 100, 400, 100, 0, "EN", "tiered"
        );

        CaseScore score = BenchmarkCaseScorer.score(execution, gt);

        // 4 gates matched, but interest_abstention failed -> 4/5 = 0.80
        assertThat(score.abstentionAccuracy()).isEqualTo(0.80);
        assertThat(score.gateMatches().get("interest_abstention")).isFalse();
    }

    @Test
    @DisplayName("Tiered vs All-Ultra cost comparison computes savings multiplier from measured tokens")
    void testTieredVsAllUltraCostComparison() {
        GroundTruth gt = createSampleGroundTruth(5000000L, List.of("C1"), "ELIGIBLE_FOR_REVIEW", List.of());

        // 2,000 input tokens, 400 output tokens, 100 reasoning tokens
        // Tiered cost: 400 micros
        // All-Ultra cost: (2,000 * 1) + ((400 + 100) * 3) = 2,000 + 1,500 = 3,500 micros
        // Multiplier: 3,500 / 400 = 8.75x
        CaseExecutionResult execution = new CaseExecutionResult(
            "TEST-001",
            5000000L,
            "PARTIAL",
            List.of("C1"),
            Map.of("gate_a", "ELIGIBLE_FOR_REVIEW", "gate_b", "ELIGIBLE_FOR_REVIEW", "gate_c", "ELIGIBLE_FOR_REVIEW", "gate_d", "ELIGIBLE_FOR_REVIEW"),
            true, 50000L, null,
            List.of(), List.of(),
            400L, 120, 2000, 400, 100, "EN", "tiered"
        );

        CaseScore score = BenchmarkCaseScorer.score(execution, gt);

        assertThat(score.allUltraCostMicros()).isEqualTo(3500L);
        assertThat(score.costSavingsMultiplier()).isEqualTo(8.75);
    }

    @Test
    @DisplayName("BenchmarkAggregateScorer computes summary rates and Experiment C language breakdown")
    void testAggregateScorerLanguageBreakdown() {
        GroundTruth gtEn = createSampleGroundTruth(5000000L, List.of("C1"), "ELIGIBLE_FOR_REVIEW", List.of());

        CaseExecutionResult resEn = new CaseExecutionResult(
            "GEN-001", 5000000L, "PARTIAL", List.of("C1"),
            Map.of("gate_a", "ELIGIBLE_FOR_REVIEW", "gate_b", "ELIGIBLE_FOR_REVIEW", "gate_c", "ELIGIBLE_FOR_REVIEW", "gate_d", "ELIGIBLE_FOR_REVIEW"),
            true, 50000L, null, List.of(), List.of(), 300L, 100, 1000, 200, 0, "EN", "tiered"
        );

        CaseExecutionResult resHi = new CaseExecutionResult(
            "GEN-002", 5000000L, "PARTIAL", List.of("C1"),
            Map.of("gate_a", "ELIGIBLE_FOR_REVIEW", "gate_b", "ELIGIBLE_FOR_REVIEW", "gate_c", "ELIGIBLE_FOR_REVIEW", "gate_d", "ELIGIBLE_FOR_REVIEW"),
            true, 50000L, null, List.of(), List.of(), 350L, 110, 1100, 220, 0, "HI", "tiered"
        );

        CaseExecutionResult resHinglish = new CaseExecutionResult(
            "GEN-003", 5000000L, "PARTIAL", List.of("C1"),
            Map.of("gate_a", "ELIGIBLE_FOR_REVIEW", "gate_b", "ELIGIBLE_FOR_REVIEW", "gate_c", "ELIGIBLE_FOR_REVIEW", "gate_d", "ELIGIBLE_FOR_REVIEW"),
            true, 50000L, null, List.of(), List.of(), 320L, 105, 1050, 210, 0, "HINGLISH", "tiered"
        );

        CaseScore scoreEn = BenchmarkCaseScorer.score(resEn, gtEn);
        CaseScore scoreHi = BenchmarkCaseScorer.score(resHi, gtEn);
        CaseScore scoreHinglish = BenchmarkCaseScorer.score(resHinglish, gtEn);

        BenchmarkConfig config = BenchmarkConfig.defaults();
        AggregateBenchmarkScore agg = BenchmarkAggregateScorer.aggregate(List.of(scoreEn, scoreHi, scoreHinglish), config);

        assertThat(agg.totalCases()).isEqualTo(3);
        assertThat(agg.balanceMatchRate()).isEqualTo(1.0);
        assertThat(agg.languageBreakdown()).containsKeys("EN", "HI", "HINGLISH");
        assertThat(agg.languageBreakdown().get("EN").caseCount()).isEqualTo(1);
        assertThat(agg.languageBreakdown().get("HI").caseCount()).isEqualTo(1);
        assertThat(agg.languageBreakdown().get("HINGLISH").caseCount()).isEqualTo(1);
        assertThat(agg.overallCostSavingsMultiplier()).isGreaterThan(1.0);
    }
}
