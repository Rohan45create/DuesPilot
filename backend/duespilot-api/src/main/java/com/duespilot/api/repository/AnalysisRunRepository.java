package com.duespilot.api.repository;

import com.duespilot.api.domain.AnalysisRunEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AnalysisRunRepository {

    private final JdbcClient jdbcClient;

    public AnalysisRunRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<AnalysisRunEntity> ROW_MAPPER = (rs, rowNum) -> new AnalysisRunEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("case_id"),
        rs.getString("status"),
        rs.getString("router_policy"),
        rs.getLong("total_cost_micros"),
        rs.getTimestamp("started_at").toInstant(),
        rs.getTimestamp("finished_at") != null ? rs.getTimestamp("finished_at").toInstant() : null
    );

    public AnalysisRunEntity save(AnalysisRunEntity run) {
        UUID id = run.id() != null ? run.id() : UUID.randomUUID();
        Instant startedAt = run.startedAt() != null ? run.startedAt() : Instant.now();
        String routerPolicy = (run.routerPolicy() != null && !run.routerPolicy().isBlank())
            ? run.routerPolicy() : "{}";

        String sql = """
            INSERT INTO analysis_runs (id, case_id, status, router_policy, total_cost_micros, started_at, finished_at)
            VALUES (:id, :case_id, :status, CAST(:router_policy AS JSONB), :total_cost_micros, :started_at, :finished_at)
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("case_id", run.caseId())
            .param("status", run.status())
            .param("router_policy", routerPolicy)
            .param("total_cost_micros", run.totalCostMicros())
            .param("started_at", Timestamp.from(startedAt))
            .param("finished_at", run.finishedAt() != null ? Timestamp.from(run.finishedAt()) : null)
            .update();

        return findById(id).orElseThrow();
    }

    public Optional<AnalysisRunEntity> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM analysis_runs WHERE id = :id")
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }

    public Optional<AnalysisRunEntity> findRunningByCaseId(UUID caseId) {
        String sql = """
            SELECT * FROM analysis_runs
            WHERE case_id = :caseId AND status = 'RUNNING'
            ORDER BY started_at DESC
            LIMIT 1
            """;
        return jdbcClient.sql(sql)
            .param("caseId", caseId)
            .query(ROW_MAPPER)
            .optional();
    }

    public Optional<AnalysisRunEntity> findLatestByCaseId(UUID caseId) {
        String sql = """
            SELECT * FROM analysis_runs
            WHERE case_id = :caseId
            ORDER BY started_at DESC
            LIMIT 1
            """;
        return jdbcClient.sql(sql)
            .param("caseId", caseId)
            .query(ROW_MAPPER)
            .optional();
    }

    public List<AnalysisRunEntity> findByCaseId(UUID caseId) {
        String sql = """
            SELECT * FROM analysis_runs
            WHERE case_id = :caseId
            ORDER BY started_at DESC
            """;
        return jdbcClient.sql(sql)
            .param("caseId", caseId)
            .query(ROW_MAPPER)
            .list();
    }

    public void updateStatus(UUID id, String status, Instant finishedAt, long totalCostMicros) {
        String sql = """
            UPDATE analysis_runs
            SET status = :status,
                finished_at = :finished_at,
                total_cost_micros = :total_cost_micros
            WHERE id = :id
            """;
        jdbcClient.sql(sql)
            .param("id", id)
            .param("status", status)
            .param("finished_at", finishedAt != null ? Timestamp.from(finishedAt) : null)
            .param("total_cost_micros", totalCostMicros)
            .update();
    }
}
