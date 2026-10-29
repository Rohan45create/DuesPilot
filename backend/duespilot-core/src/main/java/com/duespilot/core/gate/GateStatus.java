package com.duespilot.core.gate;

/**
 * Workflow eligibility states defined in docs/architecture.md section 10.5.
 *
 * Per Rule R-7: These are workflow states, NOT legal findings or conclusions.
 */
public enum GateStatus {
    /** Case satisfies workflow evidentiary requirements for this gate. */
    ELIGIBLE_FOR_REVIEW,

    /** Primary documentary evidence is missing or has not been established. */
    NOT_ESTABLISHED,

    /** Ambiguity, active dispute, or conflicting terms require human review. */
    HUMAN_REVIEW_REQUIRED,

    /** Beyond statutory scope (e.g. Medium enterprise outside MSMED Act remedies). */
    OUT_OF_SCOPE
}
