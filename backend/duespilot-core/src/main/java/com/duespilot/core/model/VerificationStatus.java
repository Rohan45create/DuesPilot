package com.duespilot.core.model;

/**
 * Verification state of an extracted entity or transaction.
 *
 * Per Rules R-11, R-12, R-13:
 * - Only VERIFIED fields enter the ledger arithmetic.
 * - UNVERIFIED and DISPUTED values are displayed but excluded from arithmetic.
 */
public enum VerificationStatus {
    /** Confirmed with primary documentary evidence (e.g. bank statement, stamped challan, tax invoice). */
    VERIFIED,

    /** Extracted from communication or secondary evidence without primary document proof. */
    UNVERIFIED,

    /** Subject to an active allegation or commercial dispute. */
    DISPUTED
}
