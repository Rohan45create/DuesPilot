package com.duespilot.core.conflict;

import com.duespilot.core.model.Allegation;
import com.duespilot.core.model.Known;
import com.duespilot.core.model.LedgerEntry;
import com.duespilot.core.model.Paise;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Supporting documentary evidence containers for conflict detection.
 */
public final class ConflictEvidence {

    private ConflictEvidence() {
    }

    public record PurchaseOrder(
        @NonNull String id,
        @NonNull String poNumber,
        @NonNull Known<Paise> amount,
        @NonNull Known<Integer> paymentTermsDays,
        @NonNull Known<LocalDate> date
    ) {
        public PurchaseOrder {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(poNumber, "poNumber cannot be null");
            Objects.requireNonNull(amount, "amount cannot be null");
            Objects.requireNonNull(paymentTermsDays, "paymentTermsDays cannot be null");
            Objects.requireNonNull(date, "date cannot be null");
        }
    }

    public record DeliveryDocument(
        @NonNull String id,
        @NonNull String documentNumber,
        @NonNull String linkedInvoiceNumber,
        @NonNull Known<LocalDate> deliveryDate,
        @NonNull Known<LocalDate> acceptanceDate,
        @NonNull Known<Integer> quantityOrdered,
        @NonNull Known<Integer> quantityDelivered,
        boolean isAccepted,
        boolean isDisputed
    ) {
        public DeliveryDocument {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(documentNumber, "documentNumber cannot be null");
            Objects.requireNonNull(linkedInvoiceNumber, "linkedInvoiceNumber cannot be null");
            Objects.requireNonNull(deliveryDate, "deliveryDate cannot be null");
            Objects.requireNonNull(acceptanceDate, "acceptanceDate cannot be null");
            Objects.requireNonNull(quantityOrdered, "quantityOrdered cannot be null");
            Objects.requireNonNull(quantityDelivered, "quantityDelivered cannot be null");
        }
    }

    public record ChangeOrder(
        @NonNull String id,
        @NonNull String poNumber,
        @NonNull Known<Paise> adjustedAmount,
        @Nullable String reason
    ) {
        public ChangeOrder {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(poNumber, "poNumber cannot be null");
            Objects.requireNonNull(adjustedAmount, "adjustedAmount cannot be null");
        }
    }

    public record RemittanceDeduction(
        @NonNull String description,
        @NonNull Known<Paise> amount,
        @Nullable String code
    ) {
        public RemittanceDeduction {
            Objects.requireNonNull(description, "description cannot be null");
            Objects.requireNonNull(amount, "amount cannot be null");
        }
    }

    public record RemittanceAdvice(
        @NonNull String id,
        @NonNull String paymentId,
        @NonNull List<RemittanceDeduction> deductions
    ) {
        public RemittanceAdvice {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(paymentId, "paymentId cannot be null");
            deductions = List.copyOf(deductions != null ? deductions : Collections.emptyList());
        }
    }
}
