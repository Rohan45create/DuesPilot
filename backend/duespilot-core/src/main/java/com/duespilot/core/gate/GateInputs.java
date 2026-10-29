package com.duespilot.core.gate;

import com.duespilot.core.model.Known;
import com.duespilot.core.model.Paise;
import com.duespilot.core.statutory.StatutoryParameters;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Inputs required to evaluate statutory eligibility gates A to D.
 */
public record GateInputs(
    // Gate A inputs
    boolean hasUdyamDocument,
    @NonNull EnterpriseCategory enterpriseCategory,
    @NonNull Known<LocalDate> udyamRegistrationDate,
    @NonNull Known<LocalDate> invoiceDate,

    // Gate B inputs
    boolean hasDeliveryOrAcceptanceDoc,
    boolean isDeliveryDisputed,
    boolean isDeemedAccepted,
    @NonNull Known<LocalDate> deliveryDate,

    // Gate C inputs
    @NonNull Known<Integer> agreedTermsDays,
    boolean hasConflictingTerms,
    @NonNull Map<String, Integer> sideBySideTerms,
    @NonNull StatutoryParameters statutoryParameters,

    // Gate D inputs
    @NonNull Known<Paise> principalAmount,
    boolean isPrincipalDisputed,
    @NonNull Known<Integer> bankRateBps,
    boolean isBankRateConfirmed
) {
    public GateInputs {
        Objects.requireNonNull(enterpriseCategory, "enterpriseCategory cannot be null");
        Objects.requireNonNull(udyamRegistrationDate, "udyamRegistrationDate cannot be null");
        Objects.requireNonNull(invoiceDate, "invoiceDate cannot be null");
        Objects.requireNonNull(deliveryDate, "deliveryDate cannot be null");
        Objects.requireNonNull(agreedTermsDays, "agreedTermsDays cannot be null");
        sideBySideTerms = Map.copyOf(sideBySideTerms != null ? sideBySideTerms : Collections.emptyMap());
        Objects.requireNonNull(statutoryParameters, "statutoryParameters cannot be null");
        Objects.requireNonNull(principalAmount, "principalAmount cannot be null");
        Objects.requireNonNull(bankRateBps, "bankRateBps cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean hasUdyamDocument = false;
        private EnterpriseCategory enterpriseCategory = EnterpriseCategory.UNKNOWN;
        private Known<LocalDate> udyamRegistrationDate = Known.unknown("No Udyam date");
        private Known<LocalDate> invoiceDate = Known.unknown("No invoice date");

        private boolean hasDeliveryOrAcceptanceDoc = false;
        private boolean isDeliveryDisputed = false;
        private boolean isDeemedAccepted = false;
        private Known<LocalDate> deliveryDate = Known.unknown("No delivery date");

        private Known<Integer> agreedTermsDays = Known.unknown("No agreed terms");
        private boolean hasConflictingTerms = false;
        private Map<String, Integer> sideBySideTerms = Collections.emptyMap();
        private StatutoryParameters statutoryParameters = StatutoryParameters.defaultParameters();

        private Known<Paise> principalAmount = Known.unknown("No principal amount");
        private boolean isPrincipalDisputed = false;
        private Known<Integer> bankRateBps = Known.unknown("No bank rate");
        private boolean isBankRateConfirmed = false;

        public Builder udyam(boolean hasDoc, EnterpriseCategory category, Known<LocalDate> regDate, Known<LocalDate> invDate) {
            this.hasUdyamDocument = hasDoc;
            this.enterpriseCategory = category;
            this.udyamRegistrationDate = regDate;
            this.invoiceDate = invDate;
            return this;
        }

        public Builder delivery(boolean hasDoc, boolean disputed, boolean deemedAccepted, Known<LocalDate> deliveryDate) {
            this.hasDeliveryOrAcceptanceDoc = hasDoc;
            this.isDeliveryDisputed = disputed;
            this.isDeemedAccepted = deemedAccepted;
            this.deliveryDate = deliveryDate;
            return this;
        }

        public Builder terms(Known<Integer> agreedDays, boolean conflicting, Map<String, Integer> terms) {
            this.agreedTermsDays = agreedDays;
            this.hasConflictingTerms = conflicting;
            this.sideBySideTerms = terms;
            return this;
        }

        public Builder statutoryParameters(StatutoryParameters params) {
            this.statutoryParameters = params;
            return this;
        }

        public Builder interestInputs(Known<Paise> principal, boolean disputed, Known<Integer> bankRateBps, boolean confirmed) {
            this.principalAmount = principal;
            this.isPrincipalDisputed = disputed;
            this.bankRateBps = bankRateBps;
            this.isBankRateConfirmed = confirmed;
            return this;
        }

        public GateInputs build() {
            return new GateInputs(
                hasUdyamDocument,
                enterpriseCategory,
                udyamRegistrationDate,
                invoiceDate,
                hasDeliveryOrAcceptanceDoc,
                isDeliveryDisputed,
                isDeemedAccepted,
                deliveryDate,
                agreedTermsDays,
                hasConflictingTerms,
                sideBySideTerms,
                statutoryParameters,
                principalAmount,
                isPrincipalDisputed,
                bankRateBps,
                isBankRateConfirmed
            );
        }
    }
}
