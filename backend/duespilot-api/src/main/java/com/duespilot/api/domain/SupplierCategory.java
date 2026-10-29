package com.duespilot.api.domain;

public enum SupplierCategory {
    MICRO,
    SMALL,
    MEDIUM,
    UNKNOWN;

    public static SupplierCategory fromString(String val) {
        if (val == null || val.isBlank()) {
            return UNKNOWN;
        }
        try {
            return SupplierCategory.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
