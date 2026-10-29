package com.duespilot.core.gate;

/**
 * Enterprise classification category under the MSMED Act 2006.
 *
 * Section 15-24 of the Act applies only to Micro and Small enterprises.
 * Medium enterprises are outside the statutory delayed-payment remedy.
 */
public enum EnterpriseCategory {
    MICRO,
    SMALL,
    MEDIUM,
    UNKNOWN;

    public boolean isEligibleMsme() {
        return this == MICRO || this == SMALL;
    }

    public boolean isMedium() {
        return this == MEDIUM;
    }
}
