package com.duespilot.core.parser;

import com.duespilot.core.model.Known;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DateParser Tests (Section 10.2 & Rule R-16)")
class DateParserTest {

    private final LocalDate refWed = LocalDate.of(2024, 3, 13); // 2024-03-13 is a Wednesday

    @Test
    @DisplayName("Parses standard ISO formats")
    void parsesIsoFormats() {
        assertThat(DateParser.parse("2024-03-12").getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
        assertThat(DateParser.parse("2024/03/12").getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
        assertThat(DateParser.parse("2024.03.12").getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
    }

    @Test
    @DisplayName("Parses common Indian DD/MM/YYYY and DD-MM-YYYY formats")
    void parsesIndianFormats() {
        assertThat(DateParser.parse("12/03/2024").getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
        assertThat(DateParser.parse("12-03-2024").getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
        assertThat(DateParser.parse("12.03.2024").getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
        assertThat(DateParser.parse("5/4/2024").getOrThrow()).isEqualTo(LocalDate.of(2024, 4, 5));
        assertThat(DateParser.parse("12/03/24").getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
    }

    @Test
    @DisplayName("Parses textual month names and ordinals")
    void parsesTextualMonths() {
        assertThat(DateParser.parse("12 March 2024").getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
        assertThat(DateParser.parse("12-Mar-2024").getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
        assertThat(DateParser.parse("March 12, 2024").getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
        assertThat(DateParser.parse("12th March 2024").getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
        assertThat(DateParser.parse("1st April 2024").getOrThrow()).isEqualTo(LocalDate.of(2024, 4, 1));
        assertThat(DateParser.parse("2nd May 2024").getOrThrow()).isEqualTo(LocalDate.of(2024, 5, 2));
        assertThat(DateParser.parse("3rd June 2024").getOrThrow()).isEqualTo(LocalDate.of(2024, 6, 3));
    }

    @Test
    @DisplayName("Parses Devanagari numerals in dates")
    void parsesDevanagariNumerals() {
        assertThat(DateParser.parse("१२/०३/२०२४").getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
        assertThat(DateParser.parse("२०२४-०३-१२").getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
    }

    @Test
    @DisplayName("Relative dates resolve correctly with a known message date (Rule R-16)")
    void resolvesRelativeDatesWithReferenceDate() {
        // refWed is Wednesday, March 13, 2024
        assertThat(DateParser.parse("today", refWed).getOrThrow()).isEqualTo(refWed);
        assertThat(DateParser.parse("yesterday", refWed).getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
        assertThat(DateParser.parse("tomorrow", refWed).getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 14));
        assertThat(DateParser.parse("day after tomorrow", refWed).getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 15));

        // next Friday from Wed Mar 13 -> Fri Mar 15
        assertThat(DateParser.parse("next Friday", refWed).getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(DateParser.parse("this Friday", refWed).getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 15));

        // offsets
        assertThat(DateParser.parse("in 5 days", refWed).getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 18));
        assertThat(DateParser.parse("10 days ago", refWed).getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 3));
        assertThat(DateParser.parse("in 2 weeks", refWed).getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 27));
        assertThat(DateParser.parse("end of month", refWed).getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(DateParser.parse("end of next month", refWed).getOrThrow()).isEqualTo(LocalDate.of(2024, 4, 30));
    }

    @Test
    @DisplayName("Relative dates fail to resolve without reference date (Rule R-16: UNRESOLVED)")
    void relativeDatesFailWithoutReferenceDate() {
        Known<LocalDate> k1 = DateParser.parse("next Friday");
        assertThat(k1.isUnknown()).isTrue();
        assertThat(((Known.Unknown<LocalDate>) k1).reason())
            .contains("UNRESOLVED")
            .contains("requires a known reference date");

        Known<LocalDate> k2 = DateParser.parse("tomorrow", (LocalDate) null);
        assertThat(k2.isUnknown()).isTrue();
        assertThat(((Known.Unknown<LocalDate>) k2).reason())
            .contains("UNRESOLVED");

        Known<LocalDate> k3 = DateParser.parse("in 10 days", Known.unknown("Message date missing"));
        assertThat(k3.isUnknown()).isTrue();
        assertThat(((Known.Unknown<LocalDate>) k3).reason())
            .contains("UNRESOLVED");
    }

    @Test
    @DisplayName("Rejects ambiguous dates (multiple conflicting dates in quote)")
    void rejectsAmbiguousDatesInQuote() {
        Known<LocalDate> k = DateParser.parse("Invoice dated 12/03/2024 or maybe 15/03/2024");
        assertThat(k.isUnknown()).isTrue();
        assertThat(((Known.Unknown<LocalDate>) k).reason())
            .contains("Ambiguous date: multiple conflicting dates found");
    }

    @Test
    @DisplayName("Accepts quote when same date is repeated in text")
    void acceptsIdenticalRepeatedDate() {
        Known<LocalDate> k = DateParser.parse("Delivery on 12/03/2024 confirmed on 12-03-2024");
        assertThat(k.isValue()).isTrue();
        assertThat(k.getOrThrow()).isEqualTo(LocalDate.of(2024, 3, 12));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "N/A", "Unknown", "Pending", "31/02/2024", "invalid-date"})
    @DisplayName("Rejects empty, invalid calendar, or non-date inputs")
    void rejectsInvalidInputs(String invalid) {
        Known<LocalDate> k = DateParser.parse(invalid);
        assertThat(k.isUnknown()).isTrue();
    }

    @Test
    @DisplayName("Handles null input safely")
    void handlesNull() {
        Known<LocalDate> k = DateParser.parse(null);
        assertThat(k.isUnknown()).isTrue();
    }
}
