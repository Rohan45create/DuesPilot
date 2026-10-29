package com.duespilot.api.repository;

import com.duespilot.api.domain.ExtractedFieldEntity;
import com.duespilot.core.extract.FieldStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class ExtractedFieldRepository {

    private final JdbcClient jdbcClient;

    public ExtractedFieldRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<ExtractedFieldEntity> ROW_MAPPER = (rs, rowNum) -> new ExtractedFieldEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("document_id"),
        (UUID) rs.getObject("run_id"),
        rs.getString("field_name"),
        rs.getString("value_text"),
        rs.getObject("value_paise") != null ? rs.getLong("value_paise") : null,
        rs.getDate("value_date") != null ? rs.getDate("value_date").toLocalDate() : null,
        FieldStatus.valueOf(rs.getString("status")),
        rs.getObject("page_no") != null ? rs.getInt("page_no") : null,
        rs.getObject("char_start") != null ? rs.getInt("char_start") : null,
        rs.getObject("char_end") != null ? rs.getInt("char_end") : null,
        rs.getString("quote"),
        rs.getString("model")
    );

    public ExtractedFieldEntity save(ExtractedFieldEntity entity) {
        UUID id = entity.id() != null ? entity.id() : UUID.randomUUID();

        String sql = """
            INSERT INTO extracted_fields (id, document_id, run_id, field_name, value_text, value_paise, value_date, status, page_no, char_start, char_end, quote, model)
            VALUES (:id, :document_id, :run_id, :field_name, :value_text, :value_paise, :value_date, :status, :page_no, :char_start, :char_end, :quote, :model)
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("document_id", entity.documentId())
            .param("run_id", entity.runId())
            .param("field_name", entity.fieldName())
            .param("value_text", entity.valueText())
            .param("value_paise", entity.valuePaise())
            .param("value_date", entity.valueDate() != null ? Date.valueOf(entity.valueDate()) : null)
            .param("status", entity.status().name())
            .param("page_no", entity.pageNo())
            .param("char_start", entity.charStart())
            .param("char_end", entity.charEnd())
            .param("quote", entity.quote())
            .param("model", entity.model())
            .update();

        return new ExtractedFieldEntity(
            id,
            entity.documentId(),
            entity.runId(),
            entity.fieldName(),
            entity.valueText(),
            entity.valuePaise(),
            entity.valueDate(),
            entity.status(),
            entity.pageNo(),
            entity.charStart(),
            entity.charEnd(),
            entity.quote(),
            entity.model()
        );
    }

    public List<ExtractedFieldEntity> saveAll(List<ExtractedFieldEntity> entities) {
        List<ExtractedFieldEntity> saved = new ArrayList<>(entities.size());
        for (ExtractedFieldEntity e : entities) {
            saved.add(save(e));
        }
        return saved;
    }

    public List<ExtractedFieldEntity> findByDocumentIdAndRunId(UUID documentId, UUID runId) {
        String sql = """
            SELECT * FROM extracted_fields
            WHERE document_id = :document_id AND run_id = :run_id
            ORDER BY page_no ASC, char_start ASC
            """;

        return jdbcClient.sql(sql)
            .param("document_id", documentId)
            .param("run_id", runId)
            .query(ROW_MAPPER)
            .list();
    }

    public List<ExtractedFieldEntity> findByRunId(UUID runId) {
        String sql = """
            SELECT * FROM extracted_fields
            WHERE run_id = :run_id
            ORDER BY document_id ASC, page_no ASC, char_start ASC
            """;

        return jdbcClient.sql(sql)
            .param("run_id", runId)
            .query(ROW_MAPPER)
            .list();
    }

    public List<ExtractedFieldEntity> findVerifiedByRunId(UUID runId) {
        String sql = """
            SELECT * FROM extracted_fields
            WHERE run_id = :run_id AND status = 'VERIFIED'
            ORDER BY document_id ASC, page_no ASC, char_start ASC
            """;

        return jdbcClient.sql(sql)
            .param("run_id", runId)
            .query(ROW_MAPPER)
            .list();
    }

    public List<ExtractedFieldEntity> findByDocumentId(UUID documentId) {
        String sql = """
            SELECT * FROM extracted_fields
            WHERE document_id = :document_id
            ORDER BY page_no ASC, char_start ASC
            """;

        return jdbcClient.sql(sql)
            .param("document_id", documentId)
            .query(ROW_MAPPER)
            .list();
    }
}
