package com.duespilot.core.conflict;

/**
 * Deterministic conflict types defined in docs/architecture.md section 10.4.
 */
public enum ConflictType {
    /** C1: Partial payment (0 < Σ confirmed payments < invoice total). */
    C1("Partial payment"),

    /** C2: Credit note unapplied (Credit note references invoice; no matching adjustment). */
    C2("Credit note unapplied"),

    /** C3: PO/invoice amount mismatch (Totals differ and no change-order document). */
    C3("PO/invoice amount mismatch"),

    /** C4: Terms conflict (≥ 2 distinct payment-term values across PO/invoice/contract). */
    C4("Terms conflict"),

    /** C5: Delivery evidence missing (Invoice present; no delivery/acceptance document). */
    C5("Delivery evidence missing"),

    /** C6: Duplicate suspect (Same supplier, invoice no, amount or same bank txn id twice). */
    C6("Duplicate suspect"),

    /** C7: Quantity/quality dispute (Quantity delta from documents; quality claim from message). */
    C7("Quantity/quality dispute"),

    /** C8: Date contradiction (Order violations among document dates and message-stated dates). */
    C8("Date contradiction"),

    /** C9: Short-payment deduction (Payment < expected and remittance advice itemises deduction). */
    C9("Short-payment deduction"),

    /** C10: Unallocated lump-sum payment (One payment references several invoices or none). */
    C10("Unallocated lump-sum payment");

    private final String title;

    ConflictType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
