package com.duespilot.core.parser;

import com.duespilot.core.model.Known;
import com.duespilot.core.model.Paise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AmountParser Tests (Section 10.2 & Rule R-1)")
class AmountParserTest {

    @Test
    @DisplayName("Parses standard Indian rupee notations with ₹ symbol")
    void parsesRupeeSymbol() {
        Known<Paise> p1 = AmountParser.parse("₹1,18,000.00");
        assertThat(p1.isValue()).isTrue();
        assertThat(p1.getOrThrow()).isEqualTo(Paise.ofPaise(11800000L));

        Known<Paise> p2 = AmountParser.parse("₹ 1,18,000.00");
        assertThat(p2.getOrThrow()).isEqualTo(Paise.ofPaise(11800000L));

        Known<Paise> p3 = AmountParser.parse("₹50000");
        assertThat(p3.getOrThrow()).isEqualTo(Paise.ofPaise(5000000L));
    }

    @Test
    @DisplayName("Parses Rs, Rs., RS variations and /- suffix")
    void parsesRsAndSuffix() {
        Known<Paise> p1 = AmountParser.parse("Rs. 50,000/-");
        assertThat(p1.getOrThrow()).isEqualTo(Paise.ofPaise(5000000L));

        Known<Paise> p2 = AmountParser.parse("Rs 50000");
        assertThat(p2.getOrThrow()).isEqualTo(Paise.ofPaise(5000000L));

        Known<Paise> p3 = AmountParser.parse("RS. 1,00,000.00");
        assertThat(p3.getOrThrow()).isEqualTo(Paise.ofPaise(10000000L));

        Known<Paise> p4 = AmountParser.parse("50,000/-");
        assertThat(p4.getOrThrow()).isEqualTo(Paise.ofPaise(5000000L));

        Known<Paise> p5 = AmountParser.parse("50,000 only");
        assertThat(p5.getOrThrow()).isEqualTo(Paise.ofPaise(5000000L));
    }

    @Test
    @DisplayName("Parses INR code and Unicode rupee sign ₨")
    void parsesInrAndUnicodeRupee() {
        Known<Paise> p1 = AmountParser.parse("INR 25,00,000");
        assertThat(p1.getOrThrow()).isEqualTo(Paise.ofPaise(250000000L));

        Known<Paise> p2 = AmountParser.parse("25,00,000 INR");
        assertThat(p2.getOrThrow()).isEqualTo(Paise.ofPaise(250000000L));

        Known<Paise> p3 = AmountParser.parse("₨ 10,000.00");
        assertThat(p3.getOrThrow()).isEqualTo(Paise.ofPaise(1000000L));
    }

    @Test
    @DisplayName("Parses Devanagari numerals and Hindi rupee markers")
    void parsesDevanagariDigits() {
        // १,१८,०००.०० = 1,18,000.00
        Known<Paise> p1 = AmountParser.parse("१,१८,०००.००");
        assertThat(p1.getOrThrow()).isEqualTo(Paise.ofPaise(11800000L));

        // ₹ ५०,००० = ₹ 50,000
        Known<Paise> p2 = AmountParser.parse("₹ ५०,०००");
        assertThat(p2.getOrThrow()).isEqualTo(Paise.ofPaise(5000000L));

        // रु. १,००,०००/- = Rs. 1,00,000/-
        Known<Paise> p3 = AmountParser.parse("रु. १,००,०००/-");
        assertThat(p3.getOrThrow()).isEqualTo(Paise.ofPaise(10000000L));
    }

    @Test
    @DisplayName("Parses Indian digit grouping (Lakhs and Crores)")
    void parsesIndianGrouping() {
        // 1 Lakh
        assertThat(AmountParser.parse("1,00,000.00").getOrThrow()).isEqualTo(Paise.ofPaise(10000000L));
        // 1.23 Crore
        assertThat(AmountParser.parse("1,23,45,678.50").getOrThrow()).isEqualTo(Paise.ofPaise(1234567850L));
        // Western 118,000.00 accepted
        assertThat(AmountParser.parse("118,000.00").getOrThrow()).isEqualTo(Paise.ofPaise(11800000L));
    }

    @Test
    @DisplayName("Parses decimal variations (single digit, double digit, leading dot)")
    void parsesDecimalVariations() {
        assertThat(AmountParser.parse(".50").getOrThrow()).isEqualTo(Paise.ofPaise(50L));
        assertThat(AmountParser.parse("0.50").getOrThrow()).isEqualTo(Paise.ofPaise(50L));
        assertThat(AmountParser.parse("100.5").getOrThrow()).isEqualTo(Paise.ofPaise(10050L));
        assertThat(AmountParser.parse("100.05").getOrThrow()).isEqualTo(Paise.ofPaise(10005L));
        assertThat(AmountParser.parse("100.00").getOrThrow()).isEqualTo(Paise.ofPaise(10000L));
        assertThat(AmountParser.parse("100.000").getOrThrow()).isEqualTo(Paise.ofPaise(10000L));
    }

    @Test
    @DisplayName("Parses negative and accounting parentheses amounts")
    void parsesNegativeAmounts() {
        assertThat(AmountParser.parse("-₹50,000.00").getOrThrow()).isEqualTo(Paise.ofPaise(-5000000L));
        assertThat(AmountParser.parse("₹ -50,000.00").getOrThrow()).isEqualTo(Paise.ofPaise(-5000000L));
        assertThat(AmountParser.parse("(₹50,000.00)").getOrThrow()).isEqualTo(Paise.ofPaise(-5000000L));
    }

    @Test
    @DisplayName("Rejects ambiguous amounts (two different totals in one quote)")
    void rejectsAmbiguousAmountsInQuote() {
        Known<Paise> k1 = AmountParser.parse("Total: ₹50,000 or maybe ₹60,000");
        assertThat(k1.isUnknown()).isTrue();
        assertThat(((Known.Unknown<Paise>) k1).reason())
            .contains("Ambiguous amount: multiple conflicting totals found");

        Known<Paise> k2 = AmountParser.parse("Invoice amount ₹1,00,000 and total ₹1,20,000");
        assertThat(k2.isUnknown()).isTrue();
        assertThat(((Known.Unknown<Paise>) k2).reason())
            .contains("Ambiguous amount");
    }

    @Test
    @DisplayName("Accepts quote when same amount is repeated in different notations")
    void acceptsIdenticalRepeatedAmount() {
        Known<Paise> k = AmountParser.parse("Total: ₹50,000 (Fifty thousand rupees: Rs. 50,000.00)");
        assertThat(k.isValue()).isTrue();
        assertThat(k.getOrThrow()).isEqualTo(Paise.ofRupees(50000));
    }

    @Test
    @DisplayName("Does not misidentify date digits as amounts in quote")
    void doesNotConfuseDatesWithAmounts() {
        Known<Paise> k = AmountParser.parse("Payment of ₹50,000 received on 12/03/2024 against INV-001");
        assertThat(k.isValue()).isTrue();
        assertThat(k.getOrThrow()).isEqualTo(Paise.ofRupees(50000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "N/A", "Unknown", "Pending", "abc", "100.123", "1,,000"})
    @DisplayName("Rejects invalid, empty, or unsupported fractional amounts")
    void rejectsInvalidInputs(String invalid) {
        Known<Paise> k = AmountParser.parse(invalid);
        assertThat(k.isUnknown()).isTrue();
    }

    @Test
    @DisplayName("Handles null input safely")
    void handlesNull() {
        Known<Paise> k = AmountParser.parse(null);
        assertThat(k.isUnknown()).isTrue();
    }
}
