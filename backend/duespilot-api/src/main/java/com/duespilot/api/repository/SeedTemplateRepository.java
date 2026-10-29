package com.duespilot.api.repository;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
public class SeedTemplateRepository {

    private final JdbcClient jdbcClient;

    public SeedTemplateRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public record SeedTemplateRecord(
        String templateCode,
        String caseDataJson,
        String recordedAnalysisJson
    ) {}

    public record SeedTemplateDocRecord(
        String templateCode,
        String fileName,
        String docType,
        @Nullable LocalDate docDate,
        @Nullable String docText,
        String blobSha256
    ) {}

    public void saveTemplate(
            @NonNull String templateCode,
            @NonNull String caseDataJson,
            @Nullable String recordedAnalysisJson) {
        String sql = """
            INSERT INTO app.seed_templates (template_code, case_data, recorded_analysis)
            VALUES (:templateCode, :caseData::jsonb, :recordedAnalysis::jsonb)
            ON CONFLICT (template_code) DO UPDATE SET
                case_data = EXCLUDED.case_data,
                recorded_analysis = EXCLUDED.recorded_analysis
            """;
        jdbcClient.sql(sql)
            .param("templateCode", templateCode)
            .param("caseData", caseDataJson)
            .param("recordedAnalysis", recordedAnalysisJson != null ? recordedAnalysisJson : "{}")
            .update();
    }

    public void saveTemplateDocument(
            @NonNull String templateCode,
            @NonNull String fileName,
            @NonNull String docType,
            @Nullable LocalDate docDate,
            @Nullable String docText,
            @NonNull String blobSha256) {
        String sql = """
            INSERT INTO app.seed_template_documents (template_code, file_name, doc_type, doc_date, doc_text, blob_sha256)
            VALUES (:templateCode, :fileName, :docType, :docDate, :docText, :blobSha256)
            ON CONFLICT (template_code, file_name) DO UPDATE SET
                doc_type = EXCLUDED.doc_type,
                doc_date = EXCLUDED.doc_date,
                doc_text = EXCLUDED.doc_text,
                blob_sha256 = EXCLUDED.blob_sha256
            """;
        jdbcClient.sql(sql)
            .param("templateCode", templateCode)
            .param("fileName", fileName)
            .param("docType", docType)
            .param("docDate", docDate != null ? Date.valueOf(docDate) : null)
            .param("docText", docText)
            .param("blobSha256", blobSha256.trim())
            .update();
    }

    public boolean hasTemplate(@NonNull String templateCode) {
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM app.seed_templates WHERE template_code = :templateCode")
            .param("templateCode", templateCode)
            .query(Long.class)
            .single();
        return count != null && count > 0;
    }

    public List<SeedTemplateRecord> findAllTemplates() {
        return jdbcClient.sql("SELECT template_code, case_data::text, recorded_analysis::text FROM app.seed_templates")
            .query((rs, rowNum) -> new SeedTemplateRecord(
                rs.getString("template_code"),
                rs.getString("case_data"),
                rs.getString("recorded_analysis")
            ))
            .list();
    }

    public List<SeedTemplateDocRecord> findDocumentsByTemplate(@NonNull String templateCode) {
        return jdbcClient.sql("SELECT template_code, file_name, doc_type, doc_date, doc_text, blob_sha256 FROM app.seed_template_documents WHERE template_code = :templateCode")
            .param("templateCode", templateCode)
            .query((rs, rowNum) -> new SeedTemplateDocRecord(
                rs.getString("template_code"),
                rs.getString("file_name"),
                rs.getString("doc_type"),
                rs.getDate("doc_date") != null ? rs.getDate("doc_date").toLocalDate() : null,
                rs.getString("doc_text"),
                rs.getString("blob_sha256").trim()
            ))
            .list();
    }

    public int countTemplates() {
        Long cnt = jdbcClient.sql("SELECT COUNT(*) FROM app.seed_templates")
            .query(Long.class)
            .single();
        return cnt != null ? cnt.intValue() : 0;
    }

    public int countDocuments() {
        Long cnt = jdbcClient.sql("SELECT COUNT(*) FROM app.seed_template_documents")
            .query(Long.class)
            .single();
        return cnt != null ? cnt.intValue() : 0;
    }

    public long getTotalBlobSizeBytes() {
        Long total = jdbcClient.sql("""
            SELECT COALESCE(SUM(b.size_bytes), 0)
            FROM app.file_blobs b
            WHERE b.sha256 IN (
                SELECT DISTINCT blob_sha256 FROM app.seed_template_documents
            )
            """)
            .query(Long.class)
            .single();
        return total != null ? total : 0L;
    }
}

