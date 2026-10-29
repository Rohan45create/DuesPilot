package com.duespilot.core.model;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Known<T> Sealed Interface Tests (Rule R-2 & Section 10.1)")
class KnownTest {

    @Test
    @DisplayName("Known.Value behaves correctly")
    void valueBehaviors() {
        Known<String> k = Known.value("INV-001");

        assertThat(k.isValue()).isTrue();
        assertThat(k.isUnknown()).isFalse();
        assertThat(k.getOrThrow()).isEqualTo("INV-001");
        assertThat(k.orNull()).isEqualTo("INV-001");
        assertThat(k.orElse("DEFAULT")).isEqualTo("INV-001");
        assertThat(k.toOptional()).contains("INV-001");

        AtomicBoolean executed = new AtomicBoolean(false);
        k.ifValue(v -> executed.set(true));
        assertThat(executed).isTrue();

        Known<Integer> mapped = k.map(String::length);
        assertThat(mapped.getOrThrow()).isEqualTo(7);

        Known<String> flatMapped = k.flatMap(v -> Known.value(v + "-CONFIRMED"));
        assertThat(flatMapped.getOrThrow()).isEqualTo("INV-001-CONFIRMED");
    }

    @Test
    @DisplayName("Known.Unknown behaves correctly")
    void unknownBehaviors() {
        Known<String> k = Known.unknown("Unreadable scan");

        assertThat(k.isValue()).isFalse();
        assertThat(k.isUnknown()).isTrue();
        assertThat(k.orNull()).isNull();
        assertThat(k.orElse("DEFAULT")).isEqualTo("DEFAULT");
        assertThat(k.toOptional()).isEmpty();

        assertThatThrownBy(k::getOrThrow)
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Unreadable scan");

        AtomicBoolean executed = new AtomicBoolean(false);
        k.ifValue(v -> executed.set(true));
        assertThat(executed).isFalse();

        Known<Integer> mapped = k.map(String::length);
        assertThat(mapped.isUnknown()).isTrue();
        assertThat(((Known.Unknown<Integer>) mapped).reason()).isEqualTo("Unreadable scan");
    }

    @Test
    @DisplayName("Known.of handles null vs non-null appropriately")
    void ofFactoryMethod() {
        Known<String> nonNull = Known.of("Hello");
        assertThat(nonNull.isValue()).isTrue();

        Known<String> fromNull = Known.of(null);
        assertThat(fromNull.isUnknown()).isTrue();

        Known<String> fromNullWithReason = Known.of(null, "Missing invoice total");
        assertThat(fromNullWithReason.isUnknown()).isTrue();
        assertThat(((Known.Unknown<String>) fromNullWithReason).reason()).isEqualTo("Missing invoice total");
    }
}
