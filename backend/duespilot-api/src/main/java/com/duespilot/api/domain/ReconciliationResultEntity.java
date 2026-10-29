package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record ReconciliationResultEntity(
    UUID runId,
    @Nullable Long invoicedPaise,
    @Nullable Long confirmedPaidPaise,
    @Nullable Long confirmedCreditPaise,
    @Nullable Long disputedPaise,
    @Nullable Long unverifiedPaise,
    @Nullable Long balancePaise,
    String balanceStatus
) {}
