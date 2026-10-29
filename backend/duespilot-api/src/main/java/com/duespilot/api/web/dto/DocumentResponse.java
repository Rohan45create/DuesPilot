package com.duespilot.api.web.dto;

import com.duespilot.api.domain.DocumentEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    @JsonProperty("case_id") UUID caseId,
    @JsonProperty("doc_type") String docType,
    @JsonProperty("file_name") String fileName,
    @JsonProperty("mime_type") String mimeType,
    String sha256,
    @JsonProperty("page_count") int pageCount,
    @JsonProperty("doc_date") @Nullable LocalDate docDate,
    @JsonProperty("created_at") Instant createdAt
) {
    public static DocumentResponse fromEntity(DocumentEntity entity) {
        return new DocumentResponse(
            entity.id(),
            entity.caseId(),
            entity.docType().name(),
            entity.fileName(),
            entity.mimeType(),
            entity.sha256(),
            entity.pageCount(),
            entity.docDate(),
            entity.createdAt()
        );
    }
}
