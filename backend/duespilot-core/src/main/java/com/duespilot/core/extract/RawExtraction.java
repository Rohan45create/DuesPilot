package com.duespilot.core.extract;

import org.jspecify.annotations.Nullable;

/**
 * Raw field extraction candidate returned by an LLM model before verification.
 *
 * Implements schema per architecture.md section 8.4 and rule R-24:
 * (field name, value, page, char_start, char_end, quote, confidence).
 */
public record RawExtraction(
    String fieldName,
    @Nullable String value,
    int pageNo,
    @Nullable Integer charStart,
    @Nullable Integer charEnd,
    @Nullable String quote,
    @Nullable String confidence
) {
    public RawExtraction {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be null or blank");
        }
    }
}
