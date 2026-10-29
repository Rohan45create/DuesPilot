package com.duespilot.core.model;

/**
 * Status resulting from deterministic reconciliation of an invoice.
 *
 * Per architecture.md section 10.3 step 5 & 6:
 * - RECONCILED: balance is zero.
 * - PARTIAL: balance > 0 and at least some payment or credit has been confirmed.
 * - UNRECONCILED: balance equals the original invoice total (no verified payments/credits).
 * - UNKNOWN: balance is null / unknown because a required input amount is unknown or unverified.
 * - OVERPAID: payments + credit exceed the invoice total.
 */
public enum ReconciliationStatus {
    RECONCILED,
    PARTIAL,
    UNRECONCILED,
    UNKNOWN,
    OVERPAID
}
