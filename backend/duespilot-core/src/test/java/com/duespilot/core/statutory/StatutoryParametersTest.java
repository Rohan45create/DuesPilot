package com.duespilot.core.statutory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Statutory Parameters Tests (Section 10.5 & Rule R-10)")
class StatutoryParametersTest {

    @Test
    @DisplayName("Loads statutory.yml from classpath with all required fields")
    void loadsFromClasspath() {
        StatutoryParameters params = StatutoryParameters.loadFromClasspath();

        assertThat(params.agreedCapDays()).isEqualTo(45);
        assertThat(params.defaultPeriodDays()).isEqualTo(15);
        assertThat(params.interestMultiplier()).isEqualTo(3);
        assertThat(params.compoundingRest()).isEqualTo("MONTHLY");

        // Per Rule R-10 & Section 10.5:
        assertThat(params.amendmentBillInForce()).isFalse();
        assertThat(params.legalReviewStatus()).isEqualTo("PENDING");

        // Sources and timestamps tracked
        assertThat(params.agreedCapSource()).contains("Section 15");
        assertThat(params.defaultPeriodSource()).contains("Section 15");
        assertThat(params.interestMultiplierSource()).contains("Section 16");
        assertThat(params.amendmentBillSource()).isNotBlank();
        assertThat(params.lastChecked()).isNotBlank();
    }

    @Test
    @DisplayName("Default parameters fallback matches MSMED Act 2006")
    void defaultParametersMatchAct() {
        StatutoryParameters params = StatutoryParameters.defaultParameters();

        assertThat(params.agreedCapDays()).isEqualTo(45);
        assertThat(params.defaultPeriodDays()).isEqualTo(15);
        assertThat(params.interestMultiplier()).isEqualTo(3);
        assertThat(params.compoundingRest()).isEqualTo("MONTHLY");
        assertThat(params.amendmentBillInForce()).isFalse();
        assertThat(params.legalReviewStatus()).isEqualTo("PENDING");
    }
}
