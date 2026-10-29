package com.duespilot.api.repository;

import com.duespilot.api.domain.FileBlobEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class FileBlobRepository {

    private final JdbcClient jdbcClient;

    public FileBlobRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<FileBlobEntity> ROW_MAPPER = (rs, rowNum) -> new FileBlobEntity(
        rs.getString("sha256").trim(),
        rs.getBytes("bytes"),
        rs.getString("mime_type"),
        rs.getLong("size_bytes"),
        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
    );

    public void save(@NonNull String sha256, byte @NonNull [] bytes, @NonNull String mimeType, long sizeBytes) {
        String cleanSha = sha256.trim();
        String sql = """
            INSERT INTO app.file_blobs (sha256, bytes, mime_type, size_bytes, created_at)
            VALUES (:sha256, :bytes, :mime_type, :size_bytes, :created_at)
            ON CONFLICT (sha256) DO NOTHING
            """;
        jdbcClient.sql(sql)
            .param("sha256", cleanSha)
            .param("bytes", bytes)
            .param("mime_type", mimeType)
            .param("size_bytes", sizeBytes)
            .param("created_at", Timestamp.from(Instant.now()))
            .update();
    }

    public Optional<FileBlobEntity> findBySha256(@NonNull String sha256) {
        String cleanSha = sha256.trim();
        return jdbcClient.sql("SELECT sha256, bytes, mime_type, size_bytes, created_at FROM app.file_blobs WHERE sha256 = :sha256")
            .param("sha256", cleanSha)
            .query(ROW_MAPPER)
            .optional();
    }

    public Optional<byte[]> findBytesBySha256(@NonNull String sha256) {
        String cleanSha = sha256.trim();
        return jdbcClient.sql("SELECT bytes FROM app.file_blobs WHERE sha256 = :sha256")
            .param("sha256", cleanSha)
            .query((rs, rowNum) -> rs.getBytes("bytes"))
            .optional();
    }

    public long getTotalStorageBytes() {
        Long sum = jdbcClient.sql("SELECT COALESCE(SUM(size_bytes), 0) FROM app.file_blobs")
            .query(Long.class)
            .single();
        return sum != null ? sum : 0L;
    }

    public long getTotalBlobCount() {
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM app.file_blobs")
            .query(Long.class)
            .single();
        return count != null ? count : 0L;
    }

    public int deleteOrphanedBlobs() {
        String sql = """
            DELETE FROM app.file_blobs b
            WHERE NOT EXISTS (SELECT 1 FROM app.documents d WHERE d.blob_sha256 = b.sha256)
              AND NOT EXISTS (SELECT 1 FROM app.benchmark_case_files bf WHERE bf.blob_sha256 = b.sha256)
              AND NOT EXISTS (SELECT 1 FROM app.seed_template_documents st WHERE st.blob_sha256 = b.sha256)
            """;
        return jdbcClient.sql(sql).update();
    }
}
