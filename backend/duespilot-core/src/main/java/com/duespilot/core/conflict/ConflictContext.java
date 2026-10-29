package com.duespilot.core.conflict;

import com.duespilot.core.conflict.ConflictEvidence.ChangeOrder;
import com.duespilot.core.conflict.ConflictEvidence.DeliveryDocument;
import com.duespilot.core.conflict.ConflictEvidence.PurchaseOrder;
import com.duespilot.core.conflict.ConflictEvidence.RemittanceAdvice;
import com.duespilot.core.model.Allegation;
import com.duespilot.core.model.Known;
import com.duespilot.core.model.LedgerEntry;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Context container holding all documentary and evidentiary inputs for conflict detection.
 */
public record ConflictContext(
    @NonNull LedgerEntry invoice,
    @NonNull List<LedgerEntry> entries,
    @NonNull List<PurchaseOrder> purchaseOrders,
    @NonNull List<DeliveryDocument> deliveryDocuments,
    @NonNull List<ChangeOrder> changeOrders,
    @NonNull List<RemittanceAdvice> remittanceAdvices,
    @NonNull List<Allegation> allegations,
    @NonNull Known<Integer> invoicePaymentTermsDays,
    @NonNull Known<Integer> contractPaymentTermsDays
) {
    public ConflictContext {
        Objects.requireNonNull(invoice, "invoice cannot be null");
        entries = List.copyOf(entries != null ? entries : Collections.emptyList());
        purchaseOrders = List.copyOf(purchaseOrders != null ? purchaseOrders : Collections.emptyList());
        deliveryDocuments = List.copyOf(deliveryDocuments != null ? deliveryDocuments : Collections.emptyList());
        changeOrders = List.copyOf(changeOrders != null ? changeOrders : Collections.emptyList());
        remittanceAdvices = List.copyOf(remittanceAdvices != null ? remittanceAdvices : Collections.emptyList());
        allegations = List.copyOf(allegations != null ? allegations : Collections.emptyList());
        Objects.requireNonNull(invoicePaymentTermsDays, "invoicePaymentTermsDays cannot be null");
        Objects.requireNonNull(contractPaymentTermsDays, "contractPaymentTermsDays cannot be null");
    }

    public static Builder builder(@NonNull LedgerEntry invoice) {
        return new Builder(invoice);
    }

    public static class Builder {
        private final LedgerEntry invoice;
        private List<LedgerEntry> entries = Collections.emptyList();
        private List<PurchaseOrder> purchaseOrders = Collections.emptyList();
        private List<DeliveryDocument> deliveryDocuments = Collections.emptyList();
        private List<ChangeOrder> changeOrders = Collections.emptyList();
        private List<RemittanceAdvice> remittanceAdvices = Collections.emptyList();
        private List<Allegation> allegations = Collections.emptyList();
        private Known<Integer> invoicePaymentTermsDays = Known.unknown("Not specified on invoice");
        private Known<Integer> contractPaymentTermsDays = Known.unknown("No written contract terms");

        public Builder(@NonNull LedgerEntry invoice) {
            this.invoice = Objects.requireNonNull(invoice, "invoice cannot be null");
        }

        public Builder entries(List<LedgerEntry> entries) {
            this.entries = entries;
            return this;
        }

        public Builder purchaseOrders(List<PurchaseOrder> purchaseOrders) {
            this.purchaseOrders = purchaseOrders;
            return this;
        }

        public Builder deliveryDocuments(List<DeliveryDocument> deliveryDocuments) {
            this.deliveryDocuments = deliveryDocuments;
            return this;
        }

        public Builder changeOrders(List<ChangeOrder> changeOrders) {
            this.changeOrders = changeOrders;
            return this;
        }

        public Builder remittanceAdvices(List<RemittanceAdvice> remittanceAdvices) {
            this.remittanceAdvices = remittanceAdvices;
            return this;
        }

        public Builder allegations(List<Allegation> allegations) {
            this.allegations = allegations;
            return this;
        }

        public Builder invoicePaymentTermsDays(Known<Integer> invoicePaymentTermsDays) {
            this.invoicePaymentTermsDays = invoicePaymentTermsDays;
            return this;
        }

        public Builder contractPaymentTermsDays(Known<Integer> contractPaymentTermsDays) {
            this.contractPaymentTermsDays = contractPaymentTermsDays;
            return this;
        }

        public ConflictContext build() {
            return new ConflictContext(
                invoice,
                entries,
                purchaseOrders,
                deliveryDocuments,
                changeOrders,
                remittanceAdvices,
                allegations,
                invoicePaymentTermsDays,
                contractPaymentTermsDays
            );
        }
    }
}
