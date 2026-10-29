package com.duespilot.core.gate;

import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Composite evaluation of all statutory eligibility gates A to D for a case.
 */
public record CaseEligibility(
    @NonNull GateResult gateA,
    @NonNull GateResult gateB,
    @NonNull GateResult gateC,
    @NonNull GateResult gateD
) {
    public CaseEligibility {
        Objects.requireNonNull(gateA, "gateA cannot be null");
        Objects.requireNonNull(gateB, "gateB cannot be null");
        Objects.requireNonNull(gateC, "gateC cannot be null");
        Objects.requireNonNull(gateD, "gateD cannot be null");
    }

    /**
     * True if all preliminary gates A, B, C are eligible for review.
     */
    public boolean arePreliminaryGatesSatisfied() {
        return gateA.isEligible() && gateB.isEligible() && gateC.isEligible();
    }

    /**
     * True if Gate D is satisfied, permitting interest calculation.
     */
    public boolean canEstimateInterest() {
        return gateD.isEligible();
    }
}
