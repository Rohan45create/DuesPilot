package com.duespilot.api.domain;

import com.duespilot.core.extract.FieldStatus;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain entity mapping to table 'extracted_fields' per architecture.md section 11.
 */
public record ExtractedFieldEntity(
    @Nullable UUID id,
    UUID documentId,
    UUID runId,
    String fieldName,
    @Nullable String valueText,
    @Nullable Long valuePaise,
    @Nullable LocalDate valueDate,
    FieldStatus status,
    @Nullable Integer pageNo,
    @Nullable Integer charStart,
    @Nullable Integer charEnd,
    @Nullable String quote,
    String model
) {
    public ExtractedFieldEntity {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId cannot be null");
        }
        if (runId == null) {
            throw new IllegalArgumentException("runId cannot be null");
        }
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be null or blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model cannot be null or blank");
        }
    }

    public boolean isVerified() {
        return status == FieldStatus.VERIFIED;
    }
}
