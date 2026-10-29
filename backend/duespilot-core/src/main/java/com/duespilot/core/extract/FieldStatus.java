package com.duespilot.core.extract;

/**
 * Verification status of an extracted document field per architecture.md section 8.4 and rule R-4.
 *
 * Maps to database check constraint: status IN ('VERIFIED', 'UNVERIFIED', 'REJECTED')
 */
public enum FieldStatus {
    VERIFIED,
    UNVERIFIED,
    REJECTED
}
