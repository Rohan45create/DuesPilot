package com.duespilot.api.domain;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DocumentEntity(
    UUID id,
    UUID caseId,
    UUID workspaceId,
    DocType docType,
    String fileName,
    String mimeType,
    String sha256,
    @Nullable String storagePath,
    int pageCount,
    @Nullable LocalDate docDate,
    Instant createdAt,
    int fileSizeBytes,
    byte @Nullable [] contentBytes,
    @Nullable String blobSha256
) {
    public static final UUID DEMO_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public DocumentEntity(
        UUID id,
        UUID caseId,
        UUID workspaceId,
        DocType docType,
        String fileName,
        String mimeType,
        String sha256,
        @Nullable String storagePath,
        int pageCount,
        @Nullable LocalDate docDate,
        Instant createdAt,
        int fileSizeBytes,
        byte @Nullable [] contentBytes
    ) {
        this(id, caseId, workspaceId, docType, fileName, mimeType, sha256, storagePath, pageCount, docDate, createdAt, fileSizeBytes, contentBytes, sha256);
    }

    public DocumentEntity(
        UUID id,
        UUID caseId,
        DocType docType,
        String fileName,
        String mimeType,
        String sha256,
        @Nullable String storagePath,
        int pageCount,
        @Nullable LocalDate docDate,
        Instant createdAt,
        int fileSizeBytes,
        byte @Nullable [] contentBytes
    ) {
        this(id, caseId, DEMO_WORKSPACE_ID, docType, fileName, mimeType, sha256, storagePath, pageCount, docDate, createdAt, fileSizeBytes, contentBytes, sha256);
    }

    public DocumentEntity(
        UUID id,
        UUID caseId,
        DocType docType,
        String fileName,
        String mimeType,
        String sha256,
        @Nullable String storagePath,
        int pageCount,
        @Nullable LocalDate docDate,
        Instant createdAt
    ) {
        this(id, caseId, DEMO_WORKSPACE_ID, docType, fileName, mimeType, sha256, storagePath, pageCount, docDate, createdAt, 0, null, sha256);
    }
}
