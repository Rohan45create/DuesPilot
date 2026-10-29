package com.duespilot.core.gate;

import com.duespilot.core.gate.GateResult.Gate;
import com.duespilot.core.model.Known;
import com.duespilot.core.model.Paise;
import com.duespilot.core.statutory.StatutoryParameters;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * State machine evaluator for statutory eligibility gates A to D.
 *
 * Per architecture.md section 10.5 and rules R-7, R-10, R-17, R-19:
 * - Workflow states: ELIGIBLE_FOR_REVIEW, NOT_ESTABLISHED, HUMAN_REVIEW_REQUIRED, OUT_OF_SCOPE.
 * - Gate D must abstain when any input is unknown.
 */
public final class GateEvaluator {

    private GateEvaluator() {
    }

    /**
     * Evaluates all gates A through D in sequence.
     */
    public static CaseEligibility evaluateAll(@NonNull GateInputs inputs) {
        Objects.requireNonNull(inputs, "inputs cannot be null");

        GateResult a = evaluateGateA(inputs);
        GateResult b = evaluateGateB(inputs);
        GateResult c = evaluateGateC(inputs);
        GateResult d = evaluateGateD(inputs, a, b, c);

        return new CaseEligibility(a, b, c, d);
    }

    /**
     * Gate A: Is the supplier a micro/small enterprise with evidence?
     * Inputs: Udyam document, category, registration date.
     * Abstains when: No evidence, medium enterprise, or unclear timing.
     */
    public static GateResult evaluateGateA(@NonNull GateInputs inputs) {
        if (!inputs.hasUdyamDocument()) {
            return GateResult.abstained(
                Gate.GATE_A,
                GateStatus.NOT_ESTABLISHED,
                "No Udyam registration document or certificate provided"
            );
        }

        EnterpriseCategory category = inputs.enterpriseCategory();
        if (category == EnterpriseCategory.MEDIUM) {
            return GateResult.abstained(
                Gate.GATE_A,
                GateStatus.OUT_OF_SCOPE,
                "Supplier is registered as a MEDIUM enterprise; delayed payment remedies under Chapter V of MSMED Act apply only to Micro and Small enterprises"
            );
        }

        if (category == EnterpriseCategory.UNKNOWN) {
            return GateResult.abstained(
                Gate.GATE_A,
                GateStatus.NOT_ESTABLISHED,
                "Enterprise category could not be established from Udyam certificate"
            );
        }

        // Timing check: Udyam registration date vs invoice date
        Known<LocalDate> regDate = inputs.udyamRegistrationDate();
        Known<LocalDate> invDate = inputs.invoiceDate();

        if (regDate.isUnknown()) {
            return GateResult.abstained(
                Gate.GATE_A,
                GateStatus.HUMAN_REVIEW_REQUIRED,
                "Udyam registration date is unknown; registration timing relative to invoice is unclear"
            );
        }

        if (invDate.isValue()) {
            LocalDate reg = regDate.getOrThrow();
            LocalDate inv = invDate.getOrThrow();
            if (reg.isAfter(inv)) {
                return GateResult.abstained(
                    Gate.GATE_A,
                    GateStatus.HUMAN_REVIEW_REQUIRED,
                    String.format("Udyam registration date (%s) is after invoice date (%s); registration timing requires human verification", reg, inv)
                );
            }
        }

        Map<String, String> details = Map.of(
            "category", category.name(),
            "registrationDate", regDate.map(LocalDate::toString).orElse("unknown")
        );

        return GateResult.eligible(
            Gate.GATE_A,
            "Supplier verified as " + category + " enterprise with valid Udyam registration",
            details
        );
    }

    /**
     * Gate B: Are delivery and acceptance (or deemed acceptance) evidenced?
     * Inputs: Delivery/acceptance docs, buyer objections.
     * Abstains when: Missing or disputed.
     */
    public static GateResult evaluateGateB(@NonNull GateInputs inputs) {
        if (!inputs.hasDeliveryOrAcceptanceDoc() && !inputs.isDeemedAccepted()) {
            return GateResult.abstained(
                Gate.GATE_B,
                GateStatus.NOT_ESTABLISHED,
                "Missing delivery or acceptance documentation"
            );
        }

        if (inputs.isDeliveryDisputed()) {
            return GateResult.abstained(
                Gate.GATE_B,
                GateStatus.HUMAN_REVIEW_REQUIRED,
                "Delivery or acceptance is actively disputed by buyer (objection or defect claim present)"
            );
        }

        if (inputs.isDeemedAccepted()) {
            return GateResult.eligible(
                Gate.GATE_B,
                "Deemed acceptance evidenced (delivery documented with no buyer objection raised within statutory 15-day objection window)"
            );
        }

        return GateResult.eligible(
            Gate.GATE_B,
            "Delivery and acceptance evidenced by primary document"
        );
    }

    /**
     * Gate C: What payment period applies?
     * Inputs: Written terms; config for statutory limits.
     * Abstains when: Terms conflict or no written agreement and facts unclear.
     */
    public static GateResult evaluateGateC(@NonNull GateInputs inputs) {
        StatutoryParameters params = inputs.statutoryParameters();
        int cap = params.agreedCapDays();
        int defaultDays = params.defaultPeriodDays();

        // Conflicting terms across documents (Rule R-17)
        if (inputs.hasConflictingTerms()) {
            Map<String, String> details = new LinkedHashMap<>();
            inputs.sideBySideTerms().forEach((src, days) -> details.put(src, days + " days"));
            return new GateResult(
                Gate.GATE_C,
                GateStatus.HUMAN_REVIEW_REQUIRED,
                Gate.GATE_C.question(),
                List.of("Conflicting payment terms across documents; cannot determine agreed period without human review per Rule R-17"),
                details
            );
        }

        // No written agreement
        if (inputs.agreedTermsDays().isUnknown()) {
            if (inputs.deliveryDate().isUnknown()) {
                return GateResult.abstained(
                    Gate.GATE_C,
                    GateStatus.HUMAN_REVIEW_REQUIRED,
                    "No written agreement and delivery/acceptance dates are unclear; cannot compute 15-day statutory default"
                );
            }

            Map<String, String> details = Map.of(
                "appliedDays", String.valueOf(defaultDays),
                "basis", "STATUTORY_DEFAULT",
                "source", params.defaultPeriodSource()
            );

            return GateResult.eligible(
                Gate.GATE_C,
                String.format("No written agreement; default statutory period of %d days applies under Section 15 of MSMED Act", defaultDays),
                details
            );
        }

        // Written agreement exists
        int agreed = inputs.agreedTermsDays().getOrThrow();
        if (agreed <= cap) {
            Map<String, String> details = Map.of(
                "appliedDays", String.valueOf(agreed),
                "basis", "AGREED_TERMS"
            );
            return GateResult.eligible(
                Gate.GATE_C,
                String.format("Agreed terms of %d days are within statutory %d-day cap", agreed, cap),
                details
            );
        } else {
            Map<String, String> details = Map.of(
                "appliedDays", String.valueOf(cap),
                "agreedDays", String.valueOf(agreed),
                "basis", "STATUTORY_CAP",
                "source", params.agreedCapSource()
            );
            return GateResult.eligible(
                Gate.GATE_C,
                String.format("Agreed terms of %d days exceed statutory cap; capped at %d days under Section 15 of MSMED Act", agreed, cap),
                details
            );
        }
    }

    /**
     * Gate D: Can interest be estimated?
     * Inputs: Gates A–C, verified principal, confirmed bank rate.
     * Abstains when: Any input unknown, principal disputed, or rate unconfirmed.
     */
    public static GateResult evaluateGateD(
        @NonNull GateInputs inputs,
        @NonNull GateResult gateA,
        @NonNull GateResult gateB,
        @NonNull GateResult gateC
    ) {
        List<String> abstentionReasons = new ArrayList<>();
        GateStatus targetStatus = GateStatus.NOT_ESTABLISHED;

        // Condition 1: Gate A must be ELIGIBLE_FOR_REVIEW
        if (gateA.isAbstaining()) {
            abstentionReasons.add("Gate A not satisfied: " + String.join("; ", gateA.reasons()));
            if (gateA.status() == GateStatus.OUT_OF_SCOPE) {
                targetStatus = GateStatus.OUT_OF_SCOPE;
            } else if (targetStatus != GateStatus.OUT_OF_SCOPE) {
                targetStatus = gateA.status();
            }
        }

        // Condition 2: Gate B must be ELIGIBLE_FOR_REVIEW
        if (gateB.isAbstaining()) {
            abstentionReasons.add("Gate B not satisfied: " + String.join("; ", gateB.reasons()));
            if (targetStatus != GateStatus.OUT_OF_SCOPE) {
                targetStatus = gateB.status();
            }
        }

        // Condition 3: Gate C must be ELIGIBLE_FOR_REVIEW
        if (gateC.isAbstaining()) {
            abstentionReasons.add("Gate C not satisfied: " + String.join("; ", gateC.reasons()));
            if (targetStatus != GateStatus.OUT_OF_SCOPE) {
                targetStatus = gateC.status();
            }
        }

        // Condition 4: Principal amount must be known (Gate D must abstain when any input is unknown)
        if (inputs.principalAmount().isUnknown()) {
            abstentionReasons.add("Principal amount is unknown (" + ((Known.Unknown<Paise>) inputs.principalAmount()).reason() + ")");
            if (targetStatus != GateStatus.OUT_OF_SCOPE) {
                targetStatus = GateStatus.NOT_ESTABLISHED;
            }
        }

        // Condition 5: Principal must be undisputed
        if (inputs.isPrincipalDisputed()) {
            abstentionReasons.add("Principal amount is actively disputed; interest estimation withheld per Rule R-19");
            if (targetStatus != GateStatus.OUT_OF_SCOPE) {
                targetStatus = GateStatus.HUMAN_REVIEW_REQUIRED;
            }
        }

        // Condition 6: Bank rate must be known
        if (inputs.bankRateBps().isUnknown()) {
            abstentionReasons.add("Bank rate is unknown (" + ((Known.Unknown<Integer>) inputs.bankRateBps()).reason() + ")");
            if (targetStatus != GateStatus.OUT_OF_SCOPE) {
                targetStatus = GateStatus.NOT_ESTABLISHED;
            }
        } else if (!inputs.isBankRateConfirmed()) {
            // Condition 7: Bank rate must be confirmed by human (Rule R-19)
            abstentionReasons.add("Bank rate has not been confirmed by a human per Rule R-19");
            if (targetStatus != GateStatus.OUT_OF_SCOPE) {
                targetStatus = GateStatus.HUMAN_REVIEW_REQUIRED;
            }
        }

        if (!abstentionReasons.isEmpty()) {
            return GateResult.abstained(Gate.GATE_D, targetStatus, abstentionReasons);
        }

        Map<String, String> details = Map.of(
            "principal", inputs.principalAmount().getOrThrow().toString(),
            "bankRateBps", inputs.bankRateBps().getOrThrow().toString(),
            "multiplier", String.valueOf(inputs.statutoryParameters().interestMultiplier()),
            "compounding", inputs.statutoryParameters().compoundingRest()
        );

        return GateResult.eligible(
            Gate.GATE_D,
            "All statutory gates A-C satisfied, principal verified and undisputed, bank rate confirmed by human",
            details
        );
    }
}
