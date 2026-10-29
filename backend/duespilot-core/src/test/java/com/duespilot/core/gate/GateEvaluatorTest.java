package com.duespilot.core.gate;

import com.duespilot.core.gate.GateResult.Gate;
import com.duespilot.core.model.Known;
import com.duespilot.core.model.Paise;
import com.duespilot.core.statutory.StatutoryParameters;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Statutory Eligibility Gates Tests: Gates A to D (Section 10.5 & Rules R-7, R-17, R-19, R-59)")
class GateEvaluatorTest {

    private final LocalDate regDate = LocalDate.of(2023, 1, 1);
    private final LocalDate invDate = LocalDate.of(2024, 3, 15);
    private final LocalDate delDate = LocalDate.of(2024, 3, 10);

    private final StatutoryParameters params = StatutoryParameters.defaultParameters();

    // ==========================================
    // Happy Path: All Gates A to D Eligible
    // ==========================================
    @Test
    @DisplayName("Happy path: all gates A to D are ELIGIBLE_FOR_REVIEW -> canEstimateInterest is true")
    void allGatesEligible() {
        GateInputs inputs = GateInputs.builder()
            .udyam(true, EnterpriseCategory.SMALL, Known.value(regDate), Known.value(invDate))
            .delivery(true, false, false, Known.value(delDate))
            .terms(Known.value(30), false, Map.of("PO", 30))
            .statutoryParameters(params)
            .interestInputs(Known.value(Paise.ofRupees(100000)), false, Known.value(650), true)
            .build();

        CaseEligibility eligibility = GateEvaluator.evaluateAll(inputs);

        assertThat(eligibility.gateA().isEligible()).isTrue();
        assertThat(eligibility.gateB().isEligible()).isTrue();
        assertThat(eligibility.gateC().isEligible()).isTrue();
        assertThat(eligibility.gateD().isEligible()).isTrue();
        assertThat(eligibility.canEstimateInterest()).isTrue();
    }

    // ==========================================
    // Gate A Abstentions
    // ==========================================
    @Test
    @DisplayName("Gate A abstention: no Udyam evidence -> NOT_ESTABLISHED")
    void gateAAbstainsOnNoUdyamEvidence() {
        GateInputs inputs = GateInputs.builder()
            .udyam(false, EnterpriseCategory.SMALL, Known.value(regDate), Known.value(invDate))
            .build();

        GateResult result = GateEvaluator.evaluateGateA(inputs);

        assertThat(result.status()).isEqualTo(GateStatus.NOT_ESTABLISHED);
        assertThat(result.isAbstaining()).isTrue();
        assertThat(result.reasons()).anyMatch(r -> r.contains("No Udyam registration document"));
    }

    @Test
    @DisplayName("Gate A abstention: medium enterprise -> OUT_OF_SCOPE")
    void gateAAbstainsOnMediumEnterprise() {
        GateInputs inputs = GateInputs.builder()
            .udyam(true, EnterpriseCategory.MEDIUM, Known.value(regDate), Known.value(invDate))
            .build();

        GateResult result = GateEvaluator.evaluateGateA(inputs);

        assertThat(result.status()).isEqualTo(GateStatus.OUT_OF_SCOPE);
        assertThat(result.isAbstaining()).isTrue();
        assertThat(result.reasons()).anyMatch(r -> r.contains("MEDIUM enterprise"));
    }

    @Test
    @DisplayName("Gate A abstention: Udyam registered after invoice date -> HUMAN_REVIEW_REQUIRED")
    void gateAAbstainsOnUdyamDateAfterInvoice() {
        LocalDate postInvoiceReg = invDate.plusMonths(1);

        GateInputs inputs = GateInputs.builder()
            .udyam(true, EnterpriseCategory.MICRO, Known.value(postInvoiceReg), Known.value(invDate))
            .build();

        GateResult result = GateEvaluator.evaluateGateA(inputs);

        assertThat(result.status()).isEqualTo(GateStatus.HUMAN_REVIEW_REQUIRED);
        assertThat(result.isAbstaining()).isTrue();
        assertThat(result.reasons()).anyMatch(r -> r.contains("registration timing requires human verification"));
    }

    // ==========================================
    // Gate B Abstentions
    // ==========================================
    @Test
    @DisplayName("Gate B abstention: missing delivery and acceptance documentation -> NOT_ESTABLISHED")
    void gateBAbstainsOnMissingAcceptance() {
        GateInputs inputs = GateInputs.builder()
            .delivery(false, false, false, Known.unknown("No delivery date"))
            .build();

        GateResult result = GateEvaluator.evaluateGateB(inputs);

        assertThat(result.status()).isEqualTo(GateStatus.NOT_ESTABLISHED);
        assertThat(result.isAbstaining()).isTrue();
        assertThat(result.reasons()).anyMatch(r -> r.contains("Missing delivery or acceptance documentation"));
    }

    @Test
    @DisplayName("Gate B abstention: delivery is actively disputed by buyer -> HUMAN_REVIEW_REQUIRED")
    void gateBAbstainsOnDisputedDelivery() {
        GateInputs inputs = GateInputs.builder()
            .delivery(true, true, false, Known.value(delDate)) // isDeliveryDisputed = true
            .build();

        GateResult result = GateEvaluator.evaluateGateB(inputs);

        assertThat(result.status()).isEqualTo(GateStatus.HUMAN_REVIEW_REQUIRED);
        assertThat(result.isAbstaining()).isTrue();
        assertThat(result.reasons()).anyMatch(r -> r.contains("actively disputed by buyer"));
    }

    @Test
    @DisplayName("Gate B eligible on deemed acceptance (statutory 15 days without objection)")
    void gateBEligibleOnDeemedAcceptance() {
        GateInputs inputs = GateInputs.builder()
            .delivery(false, false, true, Known.value(delDate)) // isDeemedAccepted = true
            .build();

        GateResult result = GateEvaluator.evaluateGateB(inputs);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.reasons()).anyMatch(r -> r.contains("Deemed acceptance evidenced"));
    }

    // ==========================================
    // Gate C Abstentions & Capping
    // ==========================================
    @Test
    @DisplayName("Gate C abstention: conflicting payment terms across documents -> HUMAN_REVIEW_REQUIRED per Rule R-17")
    void gateCAbstainsOnConflictingTerms() {
        GateInputs inputs = GateInputs.builder()
            .terms(Known.value(30), true, Map.of("PO", 30, "Invoice", 45)) // hasConflictingTerms = true
            .statutoryParameters(params)
            .build();

        GateResult result = GateEvaluator.evaluateGateC(inputs);

        assertThat(result.status()).isEqualTo(GateStatus.HUMAN_REVIEW_REQUIRED);
        assertThat(result.isAbstaining()).isTrue();
        assertThat(result.reasons()).anyMatch(r -> r.contains("Conflicting payment terms across documents"));
        assertThat(result.details()).containsEntry("PO", "30 days");
        assertThat(result.details()).containsEntry("Invoice", "45 days");
    }

    @Test
    @DisplayName("Gate C: no written agreement with known delivery date -> default 15 days applied under Section 15")
    void gateCApplies15DaysDefaultWithoutWrittenAgreement() {
        GateInputs inputs = GateInputs.builder()
            .delivery(true, false, false, Known.value(delDate))
            .terms(Known.unknown("No written agreement"), false, Map.of())
            .statutoryParameters(params)
            .build();

        GateResult result = GateEvaluator.evaluateGateC(inputs);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.details()).containsEntry("appliedDays", "15");
        assertThat(result.details()).containsEntry("basis", "STATUTORY_DEFAULT");
    }

    @Test
    @DisplayName("Gate C: agreed terms of 60 days are capped at statutory 45 days under Section 15")
    void gateCCapsAgreedTermsAt45Days() {
        GateInputs inputs = GateInputs.builder()
            .terms(Known.value(60), false, Map.of("PO", 60))
            .statutoryParameters(params)
            .build();

        GateResult result = GateEvaluator.evaluateGateC(inputs);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.details()).containsEntry("appliedDays", "45");
        assertThat(result.details()).containsEntry("agreedDays", "60");
        assertThat(result.details()).containsEntry("basis", "STATUTORY_CAP");
    }

    // ==========================================
    // Gate D Abstentions ("must abstain when any input is unknown")
    // ==========================================
    @Test
    @DisplayName("Gate D abstains when Gate A is OUT_OF_SCOPE (medium enterprise)")
    void gateDAbstainsWhenGateAOutOfScope() {
        GateInputs inputs = GateInputs.builder()
            .udyam(true, EnterpriseCategory.MEDIUM, Known.value(regDate), Known.value(invDate))
            .delivery(true, false, false, Known.value(delDate))
            .terms(Known.value(30), false, Map.of())
            .interestInputs(Known.value(Paise.ofRupees(100000)), false, Known.value(650), true)
            .build();

        CaseEligibility eligibility = GateEvaluator.evaluateAll(inputs);

        assertThat(eligibility.gateD().status()).isEqualTo(GateStatus.OUT_OF_SCOPE);
        assertThat(eligibility.canEstimateInterest()).isFalse();
        assertThat(eligibility.gateD().reasons()).anyMatch(r -> r.contains("Gate A not satisfied"));
    }

    @Test
    @DisplayName("Gate D abstains when Gate B is NOT_ESTABLISHED (missing delivery)")
    void gateDAbstainsWhenGateBMissing() {
        GateInputs inputs = GateInputs.builder()
            .udyam(true, EnterpriseCategory.SMALL, Known.value(regDate), Known.value(invDate))
            .delivery(false, false, false, Known.unknown("No delivery"))
            .terms(Known.value(30), false, Map.of())
            .interestInputs(Known.value(Paise.ofRupees(100000)), false, Known.value(650), true)
            .build();

        CaseEligibility eligibility = GateEvaluator.evaluateAll(inputs);

        assertThat(eligibility.gateD().status()).isEqualTo(GateStatus.NOT_ESTABLISHED);
        assertThat(eligibility.canEstimateInterest()).isFalse();
    }

    @Test
    @DisplayName("Gate D abstains when Gate C has conflicting terms")
    void gateDAbstainsWhenGateCConflicting() {
        GateInputs inputs = GateInputs.builder()
            .udyam(true, EnterpriseCategory.SMALL, Known.value(regDate), Known.value(invDate))
            .delivery(true, false, false, Known.value(delDate))
            .terms(Known.value(30), true, Map.of("PO", 30, "Invoice", 45))
            .interestInputs(Known.value(Paise.ofRupees(100000)), false, Known.value(650), true)
            .build();

        CaseEligibility eligibility = GateEvaluator.evaluateAll(inputs);

        assertThat(eligibility.gateD().status()).isEqualTo(GateStatus.HUMAN_REVIEW_REQUIRED);
        assertThat(eligibility.canEstimateInterest()).isFalse();
    }

    @Test
    @DisplayName("Gate D abstains when principal amount is unknown (Section 10.5 & Rule R-2)")
    void gateDAbstainsWhenPrincipalUnknown() {
        GateInputs inputs = GateInputs.builder()
            .udyam(true, EnterpriseCategory.SMALL, Known.value(regDate), Known.value(invDate))
            .delivery(true, false, false, Known.value(delDate))
            .terms(Known.value(30), false, Map.of())
            .interestInputs(Known.unknown("Principal amount unverified"), false, Known.value(650), true)
            .build();

        CaseEligibility eligibility = GateEvaluator.evaluateAll(inputs);

        assertThat(eligibility.gateD().status()).isEqualTo(GateStatus.NOT_ESTABLISHED);
        assertThat(eligibility.canEstimateInterest()).isFalse();
        assertThat(eligibility.gateD().reasons()).anyMatch(r -> r.contains("Principal amount is unknown"));
    }

    @Test
    @DisplayName("Gate D abstains when principal amount is disputed (Rule R-19)")
    void gateDAbstainsWhenPrincipalDisputed() {
        GateInputs inputs = GateInputs.builder()
            .udyam(true, EnterpriseCategory.SMALL, Known.value(regDate), Known.value(invDate))
            .delivery(true, false, false, Known.value(delDate))
            .terms(Known.value(30), false, Map.of())
            .interestInputs(Known.value(Paise.ofRupees(100000)), true, Known.value(650), true) // isPrincipalDisputed = true
            .build();

        CaseEligibility eligibility = GateEvaluator.evaluateAll(inputs);

        assertThat(eligibility.gateD().status()).isEqualTo(GateStatus.HUMAN_REVIEW_REQUIRED);
        assertThat(eligibility.canEstimateInterest()).isFalse();
        assertThat(eligibility.gateD().reasons()).anyMatch(r -> r.contains("Principal amount is actively disputed"));
    }

    @Test
    @DisplayName("Gate D abstains when bank rate is unconfirmed by human (Rule R-19)")
    void gateDAbstainsWhenBankRateUnconfirmed() {
        GateInputs inputs = GateInputs.builder()
            .udyam(true, EnterpriseCategory.SMALL, Known.value(regDate), Known.value(invDate))
            .delivery(true, false, false, Known.value(delDate))
            .terms(Known.value(30), false, Map.of())
            .interestInputs(Known.value(Paise.ofRupees(100000)), false, Known.value(650), false) // isBankRateConfirmed = false
            .build();

        CaseEligibility eligibility = GateEvaluator.evaluateAll(inputs);

        assertThat(eligibility.gateD().status()).isEqualTo(GateStatus.HUMAN_REVIEW_REQUIRED);
        assertThat(eligibility.canEstimateInterest()).isFalse();
        assertThat(eligibility.gateD().reasons()).anyMatch(r -> r.contains("Bank rate has not been confirmed by a human per Rule R-19"));
    }

    @Test
    @DisplayName("Gate D abstains when bank rate is unknown")
    void gateDAbstainsWhenBankRateUnknown() {
        GateInputs inputs = GateInputs.builder()
            .udyam(true, EnterpriseCategory.SMALL, Known.value(regDate), Known.value(invDate))
            .delivery(true, false, false, Known.value(delDate))
            .terms(Known.value(30), false, Map.of())
            .interestInputs(Known.value(Paise.ofRupees(100000)), false, Known.unknown("Tavily rate fetch failed"), true)
            .build();

        CaseEligibility eligibility = GateEvaluator.evaluateAll(inputs);

        assertThat(eligibility.gateD().status()).isEqualTo(GateStatus.NOT_ESTABLISHED);
        assertThat(eligibility.canEstimateInterest()).isFalse();
        assertThat(eligibility.gateD().reasons()).anyMatch(r -> r.contains("Bank rate is unknown"));
    }
}
