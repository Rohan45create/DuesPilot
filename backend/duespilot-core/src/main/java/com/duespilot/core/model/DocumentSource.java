package com.duespilot.core.model;

/**
 * Originating document source type for evidence and ledger entries.
 *
 * Per Rule R-12: A payment known only from a buyer's message is UNVERIFIED and not subtracted.
 */
public enum DocumentSource {
    /** Primary bank statement or net banking transaction advice. */
    BANK_STATEMENT,

    /** Official payment receipt or formal remittance advice from a financial institution. */
    PAYMENT_RECEIPT,

    /** Supplier tax invoice. */
    TAX_INVOICE,

    /** Formal credit note document issued by the supplier. */
    CREDIT_NOTE_DOC,

    /** Formal debit note or mutually agreed adjustment document. */
    ADJUSTMENT_DOC,

    /** Purchase order. */
    PURCHASE_ORDER,

    /** Proof of delivery / delivery challan / lorry receipt (LR). */
    DELIVERY_CHALLAN,

    /** WhatsApp message, email, or letter from buyer (communication only). */
    BUYER_MESSAGE,

    /** WhatsApp message, email, or letter from supplier. */
    SUPPLIER_MESSAGE,

    /** Other or unspecified document source. */
    OTHER;

    /**
     * Checks if this source is considered an authoritative payment document
     * (e.g. bank statement or payment receipt) as required by Section 10.3 step 3.
     */
    public boolean isPaymentDocument() {
        return this == BANK_STATEMENT || this == PAYMENT_RECEIPT;
    }

    /**
     * Checks if this source is an informal communication (buyer/supplier message).
     */
    public boolean isMessageSource() {
        return this == BUYER_MESSAGE || this == SUPPLIER_MESSAGE;
    }
}
