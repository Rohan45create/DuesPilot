package com.duespilot.core.model;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Sealed interface representing a domain value that is either definitively known
 * or explicitly unknown with an auditable reason.
 *
 * Per Rule R-2 and architecture.md section 10.1:
 * - null ≠ zero.
 * - Missing amounts and dates are Known.Unknown, never 0 or a default date.
 */
public sealed interface Known<T> permits Known.Value, Known.Unknown {

    record Value<T>(@NonNull T v) implements Known<T> {
        public Value {
            Objects.requireNonNull(v, "Value cannot be null. Use Known.Unknown when absent.");
        }

        @Override
        public String toString() {
            return "Value[" + v + "]";
        }
    }

    record Unknown<T>(@NonNull String reason) implements Known<T> {
        public Unknown {
            Objects.requireNonNull(reason, "reason cannot be null");
        }

        @Override
        public String toString() {
            return "Unknown[\"" + reason + "\"]";
        }
    }

    static <T> Known<T> of(@Nullable T val) {
        return val != null ? new Value<>(val) : new Unknown<>("Missing value");
    }

    static <T> Known<T> of(@Nullable T val, @NonNull String unknownReason) {
        return val != null ? new Value<>(val) : new Unknown<>(unknownReason);
    }

    static <T> Known<T> value(@NonNull T val) {
        return new Value<>(val);
    }

    static <T> Known<T> unknown(@NonNull String reason) {
        return new Unknown<>(reason);
    }

    default boolean isValue() {
        return this instanceof Value<T>;
    }

    default boolean isUnknown() {
        return this instanceof Unknown<T>;
    }

    default T getOrThrow() {
        if (this instanceof Value<T>(T v)) {
            return v;
        }
        throw new NoSuchElementException("Value is unknown: " + ((Unknown<T>) this).reason());
    }

    default @Nullable T orNull() {
        return this instanceof Value<T>(T v) ? v : null;
    }

    default T orElse(T other) {
        return this instanceof Value<T>(T v) ? v : other;
    }

    default Optional<T> toOptional() {
        return this instanceof Value<T>(T v) ? Optional.of(v) : Optional.empty();
    }

    default <R> Known<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper cannot be null");
        if (this instanceof Value<T>(T v)) {
            return new Value<>(mapper.apply(v));
        }
        return new Unknown<>(((Unknown<T>) this).reason());
    }

    default <R> Known<R> flatMap(Function<? super T, Known<R>> mapper) {
        Objects.requireNonNull(mapper, "mapper cannot be null");
        if (this instanceof Value<T>(T v)) {
            return mapper.apply(v);
        }
        return new Unknown<>(((Unknown<T>) this).reason());
    }

    default void ifValue(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action cannot be null");
        if (this instanceof Value<T>(T v)) {
            action.accept(v);
        }
    }
}
