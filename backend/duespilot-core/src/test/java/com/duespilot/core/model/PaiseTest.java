package com.duespilot.core.model;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Paise Value Type Tests (Rule R-1 & Section 10.1)")
class PaiseTest {

    @Test
    @DisplayName("Creates paise from integer paise and rupees")
    void createsPaiseCorrectly() {
        Paise fromPaise = Paise.ofPaise(11800000L);
        Paise fromRupees = Paise.ofRupees(118000L);
        Paise fromBigDecimal = Paise.ofRupees(new BigDecimal("118000.00"));

        assertThat(fromPaise.value()).isEqualTo(11800000L);
        assertThat(fromRupees.value()).isEqualTo(11800000L);
        assertThat(fromBigDecimal.value()).isEqualTo(11800000L);
        assertThat(fromPaise).isEqualTo(fromRupees).isEqualTo(fromBigDecimal);
    }

    @Test
    @DisplayName("Throws when ofRupees BigDecimal has unnecessary decimal places beyond 2")
    void throwsOnFractionalPaiseBigDecimal() {
        assertThatThrownBy(() -> Paise.ofRupees(new BigDecimal("100.125")))
            .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("Basic arithmetic: plus, minus, multiply, abs")
    void arithmeticOperations() {
        Paise p1 = Paise.ofRupees(50000);
        Paise p2 = Paise.ofRupees(30000);

        assertThat(p1.plus(p2)).isEqualTo(Paise.ofRupees(80000));
        assertThat(p1.minus(p2)).isEqualTo(Paise.ofRupees(20000));
        assertThat(p2.minus(p1)).isEqualTo(Paise.ofPaise(-2000000L));
        assertThat(p2.minus(p1).abs()).isEqualTo(Paise.ofRupees(20000));
        assertThat(p2.multiply(3)).isEqualTo(Paise.ofRupees(90000));
    }

    @Test
    @DisplayName("Checks positive, zero, and negative predicates")
    void predicates() {
        assertThat(Paise.ofRupees(100).isPositive()).isTrue();
        assertThat(Paise.ofRupees(100).isZero()).isFalse();
        assertThat(Paise.ofRupees(100).isNegative()).isFalse();

        assertThat(Paise.ZERO.isPositive()).isFalse();
        assertThat(Paise.ZERO.isZero()).isTrue();
        assertThat(Paise.ZERO.isNegative()).isFalse();

        assertThat(Paise.ofPaise(-100).isPositive()).isFalse();
        assertThat(Paise.ofPaise(-100).isZero()).isFalse();
        assertThat(Paise.ofPaise(-100).isNegative()).isTrue();
    }

    @Test
    @DisplayName("Formats amounts using standard Indian numbering system grouping")
    void indianNumberFormatting() {
        assertThat(Paise.ofRupees(0).toIndianFormat()).isEqualTo("0.00");
        assertThat(Paise.ofRupees(5).toIndianFormat()).isEqualTo("5.00");
        assertThat(Paise.ofPaise(50).toIndianFormat()).isEqualTo("0.50");
        assertThat(Paise.ofPaise(5).toIndianFormat()).isEqualTo("0.05");
        assertThat(Paise.ofRupees(100).toIndianFormat()).isEqualTo("100.00");
        assertThat(Paise.ofRupees(1000).toIndianFormat()).isEqualTo("1,000.00");
        assertThat(Paise.ofRupees(10000).toIndianFormat()).isEqualTo("10,000.00");
        assertThat(Paise.ofRupees(100000).toIndianFormat()).isEqualTo("1,00,000.00");
        assertThat(Paise.ofRupees(118000).toIndianFormat()).isEqualTo("1,18,000.00");
        assertThat(Paise.ofPaise(1234567850L).toIndianFormat()).isEqualTo("1,23,45,678.50");
        assertThat(Paise.ofPaise(-5000000L).toIndianFormat()).isEqualTo("-50,000.00");
        assertThat(Paise.ofRupees(118000).toString()).isEqualTo("₹1,18,000.00");
    }

    @Test
    @DisplayName("Plain rupees string representation without commas")
    void plainRupeesString() {
        assertThat(Paise.ofRupees(118000).toPlainRupeesString()).isEqualTo("118000.00");
        assertThat(Paise.ofPaise(50).toPlainRupeesString()).isEqualTo("0.50");
        assertThat(Paise.ofPaise(-5000000L).toPlainRupeesString()).isEqualTo("-50000.00");
    }

    @Test
    @DisplayName("Comparable implementation")
    void comparison() {
        Paise p1 = Paise.ofRupees(100);
        Paise p2 = Paise.ofRupees(200);

        assertThat(p1.compareTo(p2)).isNegative();
        assertThat(p2.compareTo(p1)).isPositive();
        assertThat(p1.compareTo(Paise.ofRupees(100))).isZero();
    }
}
