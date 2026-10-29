package com.duespilot.api.domain;

public enum DocType {
    PO,
    INVOICE,
    DELIVERY,
    ACCEPTANCE,
    CREDIT_NOTE,
    PAYMENT,
    MESSAGE,
    CONTRACT,
    UDYAM;

    public static DocType fromString(String val) {
        if (val == null || val.isBlank()) {
            return INVOICE;
        }
        try {
            return DocType.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return INVOICE;
        }
    }
}
