package com.duespilot.core.model;

/**
 * Type of entry in the reconciliation ledger.
 *
 * Per architecture.md section 10.3 step 1:
 * Build ledger entries from verified fields only: INVOICE, PAYMENT, CREDIT_NOTE, ADJUSTMENT.
 */
public enum LedgerEntryType {
    INVOICE,
    PAYMENT,
    CREDIT_NOTE,
    ADJUSTMENT
}
