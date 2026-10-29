package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

public record InterestEstimateEntity(
    @Nullable UUID id,
    UUID runId,
    long principalPaise,
    UUID rateRefId,
    int multiplier,
    LocalDate fromDate,
    LocalDate toDate,
    long interestPaise,
    String convention,
    String status
) {}
