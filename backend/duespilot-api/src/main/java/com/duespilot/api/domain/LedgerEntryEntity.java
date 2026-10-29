package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LedgerEntryEntity(
    @Nullable UUID id,
    UUID runId,
    String kind,
    @Nullable String ref,
    long amountPaise,
    @Nullable LocalDate entryDate,
    String status,
    List<UUID> sourceFieldIds
) {}
