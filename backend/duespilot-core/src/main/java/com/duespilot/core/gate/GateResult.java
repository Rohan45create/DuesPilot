package com.duespilot.core.gate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Result of evaluating an individual eligibility gate (A, B, C, or D).
 *
 * Per architecture.md section 10.5:
 * Returns a status plus reasons.
 */
public record GateResult(
    @NonNull Gate gate,
    @NonNull GateStatus status,
    @NonNull String question,
    @NonNull List<String> reasons,
    @NonNull Map<String, String> details
) {
    public GateResult {
        Objects.requireNonNull(gate, "gate cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(question, "question cannot be null");
        reasons = List.copyOf(reasons != null ? reasons : Collections.emptyList());
        details = Map.copyOf(details != null ? details : Collections.emptyMap());
    }

    public enum Gate {
        GATE_A("Is the supplier a micro/small enterprise with evidence?"),
        GATE_B("Are delivery and acceptance (or deemed acceptance) evidenced?"),
        GATE_C("What payment period applies?"),
        GATE_D("Can interest be estimated?");

        private final String question;

        Gate(String question) {
            this.question = question;
        }

        public String question() {
            return question;
        }
    }

    public boolean isEligible() {
        return status == GateStatus.ELIGIBLE_FOR_REVIEW;
    }

    public boolean isAbstaining() {
        return status != GateStatus.ELIGIBLE_FOR_REVIEW;
    }

    public static GateResult eligible(Gate gate, String reason) {
        return new GateResult(gate, GateStatus.ELIGIBLE_FOR_REVIEW, gate.question(), List.of(reason), Collections.emptyMap());
    }

    public static GateResult eligible(Gate gate, String reason, Map<String, String> details) {
        return new GateResult(gate, GateStatus.ELIGIBLE_FOR_REVIEW, gate.question(), List.of(reason), details);
    }

    public static GateResult abstained(Gate gate, GateStatus status, String reason) {
        return new GateResult(gate, status, gate.question(), List.of(reason), Collections.emptyMap());
    }

    public static GateResult abstained(Gate gate, GateStatus status, List<String> reasons) {
        return new GateResult(gate, status, gate.question(), reasons, Collections.emptyMap());
    }
}
