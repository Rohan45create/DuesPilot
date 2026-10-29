package com.duespilot.core.model;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a buyer allegation (e.g. defective goods, quantity shortage, delivery dispute, alleged payment).
 *
 * Per Rule R-14:
 * A buyer's allegation is stored as an allegation (quality claim, promised date);
 * it is never promoted to fact.
 */
public record Allegation(
    @NonNull String id,
    @NonNull String buyerParty,
    @NonNull AllegationType type,
    @NonNull String description,
    @NonNull Known<LocalDate> allegedDate,
    @NonNull Known<Paise> disputedAmount,
    @Nullable String evidenceQuote
) {
    public Allegation {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(buyerParty, "buyerParty cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(allegedDate, "allegedDate cannot be null");
        Objects.requireNonNull(disputedAmount, "disputedAmount cannot be null");
    }

    public enum AllegationType {
        QUALITY_DEFECT,
        QUANTITY_SHORTAGE,
        DELAYED_DELIVERY,
        PAYMENT_PROMISE,
        PRICING_DISPUTE,
        OTHER
    }
}
