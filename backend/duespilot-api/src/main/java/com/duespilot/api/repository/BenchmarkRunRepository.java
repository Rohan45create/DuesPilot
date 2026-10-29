package com.duespilot.api.repository;

import com.duespilot.api.domain.BenchmarkResultEntity;
import com.duespilot.api.domain.BenchmarkRunEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcClient repository for benchmark_runs and benchmark_results tables (architecture §11, §14).
 */
@Repository
public class BenchmarkRunRepository {

    private final JdbcClient jdbcClient;

    public BenchmarkRunRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<BenchmarkRunEntity> RUN_ROW_MAPPER = (rs, rowNum) -> new BenchmarkRunEntity(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("dataset_version"),
        rs.getString("config"),
        rs.getTimestamp("started_at").toInstant(),
        rs.getTimestamp("finished_at") != null ? rs.getTimestamp("finished_at").toInstant() : null
    );

    private static final RowMapper<BenchmarkResultEntity> RESULT_ROW_MAPPER = (rs, rowNum) -> new BenchmarkResultEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("benchmark_run_id"),
        rs.getString("case_code"),
        rs.getString("metrics"),
        rs.getBoolean("passed"),
        rs.getLong("cost_micros"),
        rs.getInt("latency_ms")
    );

    public BenchmarkRunEntity saveRun(BenchmarkRunEntity run) {
        UUID id = run.id() != null ? run.id() : UUID.randomUUID();
        Instant startedAt = run.startedAt() != null ? run.startedAt() : Instant.now();
        String configJson = (run.config() != null && !run.config().isBlank()) ? run.config() : "{}";

        String sql = """
            INSERT INTO benchmark_runs (id, name, dataset_version, config, started_at, finished_at)
            VALUES (:id, :name, :dataset_version, CAST(:config AS JSONB), :started_at, :finished_at)
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("name", run.name())
            .param("dataset_version", run.datasetVersion())
            .param("config", configJson)
            .param("started_at", Timestamp.from(startedAt))
            .param("finished_at", run.finishedAt() != null ? Timestamp.from(run.finishedAt()) : null)
            .update();

        return findRunById(id).orElseThrow();
    }

    public Optional<BenchmarkRunEntity> findRunById(UUID id) {
        return jdbcClient.sql("SELECT * FROM benchmark_runs WHERE id = :id")
            .param("id", id)
            .query(RUN_ROW_MAPPER)
            .optional();
    }

    public Optional<BenchmarkRunEntity> findLatestRun() {
        String sql = "SELECT * FROM benchmark_runs ORDER BY started_at DESC LIMIT 1";
        return jdbcClient.sql(sql)
            .query(RUN_ROW_MAPPER)
            .optional();
    }

    public List<BenchmarkRunEntity> listRuns() {
        return jdbcClient.sql("SELECT * FROM benchmark_runs ORDER BY started_at DESC")
            .query(RUN_ROW_MAPPER)
            .list();
    }

    public boolean finishRun(UUID id, Instant finishedAt) {
        String sql = "UPDATE benchmark_runs SET finished_at = :finished_at WHERE id = :id";
        return jdbcClient.sql(sql)
            .param("id", id)
            .param("finished_at", Timestamp.from(finishedAt))
            .update() > 0;
    }

    public BenchmarkResultEntity saveResult(BenchmarkResultEntity result) {
        UUID id = result.id() != null ? result.id() : UUID.randomUUID();
        String metricsJson = (result.metrics() != null && !result.metrics().isBlank()) ? result.metrics() : "{}";

        String sql = """
            INSERT INTO benchmark_results (id, benchmark_run_id, case_code, metrics, passed, cost_micros, latency_ms)
            VALUES (:id, :benchmark_run_id, :case_code, CAST(:metrics AS JSONB), :passed, :cost_micros, :latency_ms)
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("benchmark_run_id", result.benchmarkRunId())
            .param("case_code", result.caseCode())
            .param("metrics", metricsJson)
            .param("passed", result.passed())
            .param("cost_micros", result.costMicros())
            .param("latency_ms", result.latencyMs())
            .update();

        return findResultById(id).orElseThrow();
    }

    public void saveResults(List<BenchmarkResultEntity> results) {
        for (BenchmarkResultEntity r : results) {
            saveResult(r);
        }
    }

    public Optional<BenchmarkResultEntity> findResultById(UUID id) {
        return jdbcClient.sql("SELECT * FROM benchmark_results WHERE id = :id")
            .param("id", id)
            .query(RESULT_ROW_MAPPER)
            .optional();
    }

    public List<BenchmarkResultEntity> findResultsByRunId(UUID benchmarkRunId) {
        return jdbcClient.sql("SELECT * FROM benchmark_results WHERE benchmark_run_id = :benchmark_run_id ORDER BY case_code ASC")
            .param("benchmark_run_id", benchmarkRunId)
            .query(RESULT_ROW_MAPPER)
            .list();
    }

    public Optional<BenchmarkResultEntity> findResultByCase(UUID benchmarkRunId, String caseCode) {
        String sql = "SELECT * FROM benchmark_results WHERE benchmark_run_id = :benchmark_run_id AND case_code = :case_code";
        return jdbcClient.sql(sql)
            .param("benchmark_run_id", benchmarkRunId)
            .param("case_code", caseCode)
            .query(RESULT_ROW_MAPPER)
            .optional();
    }
}
