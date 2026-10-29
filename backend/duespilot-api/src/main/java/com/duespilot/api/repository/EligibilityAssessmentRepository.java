package com.duespilot.api.repository;

import com.duespilot.api.domain.EligibilityAssessmentEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class EligibilityAssessmentRepository {

    private final JdbcClient jdbcClient;

    public EligibilityAssessmentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<EligibilityAssessmentEntity> ROW_MAPPER = (rs, rowNum) -> new EligibilityAssessmentEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("run_id"),
        rs.getString("gate"),
        rs.getString("status"),
        rs.getString("reasons")
    );

    public EligibilityAssessmentEntity save(EligibilityAssessmentEntity entity) {
        UUID id = entity.id() != null ? entity.id() : UUID.randomUUID();
        String reasons = (entity.reasonsJson() != null && !entity.reasonsJson().isBlank())
            ? entity.reasonsJson() : "[]";

        String sql = """
            INSERT INTO eligibility_assessments (id, run_id, gate, status, reasons)
            VALUES (:id, :run_id, :gate, :status, CAST(:reasons AS JSONB))
            ON CONFLICT (run_id, gate) DO UPDATE SET
                status = EXCLUDED.status,
                reasons = EXCLUDED.reasons
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("run_id", entity.runId())
            .param("gate", entity.gate())
            .param("status", entity.status())
            .param("reasons", reasons)
            .update();

        return new EligibilityAssessmentEntity(
            id,
            entity.runId(),
            entity.gate(),
            entity.status(),
            reasons
        );
    }

    public List<EligibilityAssessmentEntity> saveAll(List<EligibilityAssessmentEntity> entities) {
        List<EligibilityAssessmentEntity> saved = new ArrayList<>(entities.size());
        for (EligibilityAssessmentEntity entity : entities) {
            saved.add(save(entity));
        }
        return saved;
    }

    public List<EligibilityAssessmentEntity> findByRunId(UUID runId) {
        String sql = """
            SELECT * FROM eligibility_assessments
            WHERE run_id = :run_id
            ORDER BY gate ASC
            """;
        return jdbcClient.sql(sql)
            .param("run_id", runId)
            .query(ROW_MAPPER)
            .list();
    }
}
