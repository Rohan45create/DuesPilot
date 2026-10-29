package com.duespilot.api.repository;

import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentRepository {

    private final JdbcClient jdbcClient;

    public DocumentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<DocumentEntity> ROW_MAPPER = (rs, rowNum) -> {
        String blobSha = rs.getString("blob_sha256");
        String sha256 = rs.getString("sha256");
        return new DocumentEntity(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("case_id"),
            rs.getObject("workspace_id") != null ? (UUID) rs.getObject("workspace_id") : DocumentEntity.DEMO_WORKSPACE_ID,
            DocType.fromString(rs.getString("doc_type")),
            rs.getString("file_name"),
            rs.getString("mime_type"),
            sha256 != null ? sha256.trim() : "",
            rs.getString("storage_path"),
            rs.getInt("page_count"),
            rs.getDate("doc_date") != null ? rs.getDate("doc_date").toLocalDate() : null,
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now(),
            rs.getInt("file_size_bytes"),
            rs.getBytes("content_bytes"),
            blobSha != null ? blobSha.trim() : (sha256 != null ? sha256.trim() : null)
        );
    };

    public DocumentEntity save(DocumentEntity doc) {
        UUID id = doc.id() != null ? doc.id() : UUID.randomUUID();
        UUID workspaceId = doc.workspaceId() != null ? doc.workspaceId() : DocumentEntity.DEMO_WORKSPACE_ID;
        Instant createdAt = doc.createdAt() != null ? doc.createdAt() : Instant.now();
        int sizeBytes = doc.fileSizeBytes() > 0 ? doc.fileSizeBytes()
            : (doc.contentBytes() != null ? doc.contentBytes().length : 0);
        String storagePath = doc.storagePath() != null ? doc.storagePath() : "db_blob";
        String blobSha = doc.blobSha256() != null ? doc.blobSha256().trim() : (doc.sha256() != null ? doc.sha256().trim() : null);

        String sql = """
            INSERT INTO documents (id, case_id, workspace_id, doc_type, file_name, mime_type, sha256, storage_path, page_count, doc_date, created_at, file_size_bytes, content_bytes, blob_sha256)
            VALUES (:id, :case_id, :workspace_id, :doc_type, :file_name, :mime_type, :sha256, :storage_path, :page_count, :doc_date, :created_at, :file_size_bytes, :content_bytes, :blob_sha256)
            ON CONFLICT (id) DO UPDATE SET
                case_id = EXCLUDED.case_id,
                workspace_id = EXCLUDED.workspace_id,
                doc_type = EXCLUDED.doc_type,
                file_name = EXCLUDED.file_name,
                mime_type = EXCLUDED.mime_type,
                sha256 = EXCLUDED.sha256,
                storage_path = EXCLUDED.storage_path,
                page_count = EXCLUDED.page_count,
                doc_date = EXCLUDED.doc_date,
                file_size_bytes = EXCLUDED.file_size_bytes,
                content_bytes = EXCLUDED.content_bytes,
                blob_sha256 = EXCLUDED.blob_sha256
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("case_id", doc.caseId())
            .param("workspace_id", workspaceId)
            .param("doc_type", doc.docType().name())
            .param("file_name", doc.fileName())
            .param("mime_type", doc.mimeType())
            .param("sha256", doc.sha256() != null ? doc.sha256().trim() : "")
            .param("storage_path", storagePath)
            .param("page_count", doc.pageCount())
            .param("doc_date", doc.docDate())
            .param("created_at", Timestamp.from(createdAt))
            .param("file_size_bytes", sizeBytes)
            .param("content_bytes", doc.contentBytes())
            .param("blob_sha256", blobSha)
            .update();

        return findById(id).orElseThrow();
    }

    public Optional<DocumentEntity> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM documents WHERE id = :id")
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }

    public List<DocumentEntity> findByCaseId(UUID caseId) {
        return jdbcClient.sql("SELECT * FROM documents WHERE case_id = :caseId ORDER BY created_at ASC")
            .param("caseId", caseId)
            .query(ROW_MAPPER)
            .list();
    }

    public long countByCaseId(UUID caseId) {
        Long cnt = jdbcClient.sql("SELECT count(*) FROM documents WHERE case_id = :caseId")
            .param("caseId", caseId)
            .query(Long.class)
            .single();
        return cnt != null ? cnt : 0L;
    }

    public boolean deleteById(UUID id) {
        int rows = jdbcClient.sql("DELETE FROM documents WHERE id = :id")
            .param("id", id)
            .update();
        return rows > 0;
    }
}
