package com.duespilot.api.repository;

import com.duespilot.api.domain.LlmCallEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LlmCallRepository {

    private final JdbcClient jdbcClient;

    public LlmCallRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<LlmCallEntity> ROW_MAPPER = (rs, rowNum) -> new LlmCallEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("run_id"),
        (UUID) rs.getObject("benchmark_run_id"),
        rs.getString("task"),
        rs.getString("model"),
        rs.getString("prompt_sha256"),
        rs.getObject("input_tokens") != null ? rs.getInt("input_tokens") : null,
        rs.getObject("output_tokens") != null ? rs.getInt("output_tokens") : null,
        rs.getObject("reasoning_tokens") != null ? rs.getInt("reasoning_tokens") : null,
        rs.getInt("latency_ms"),
        rs.getLong("cost_micros"),
        rs.getString("outcome"),
        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
    );

    public LlmCallEntity save(LlmCallEntity call) {
        UUID id = call.id() != null ? call.id() : UUID.randomUUID();
        Instant createdAt = call.createdAt() != null ? call.createdAt() : Instant.now();

        String sql = """
            INSERT INTO llm_calls (id, run_id, benchmark_run_id, task, model, prompt_sha256, input_tokens, output_tokens, reasoning_tokens, latency_ms, cost_micros, outcome, created_at)
            VALUES (:id, :run_id, :benchmark_run_id, :task, :model, :prompt_sha256, :input_tokens, :output_tokens, :reasoning_tokens, :latency_ms, :cost_micros, :outcome, :created_at)
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("run_id", call.runId())
            .param("benchmark_run_id", call.benchmarkRunId())
            .param("task", call.task())
            .param("model", call.model())
            .param("prompt_sha256", call.promptSha256())
            .param("input_tokens", call.inputTokens())
            .param("output_tokens", call.outputTokens())
            .param("reasoning_tokens", call.reasoningTokens())
            .param("latency_ms", call.latencyMs())
            .param("cost_micros", call.costMicros())
            .param("outcome", call.outcome())
            .param("created_at", Timestamp.from(createdAt))
            .update();

        return findById(id).orElse(call);
    }

    public Optional<LlmCallEntity> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM llm_calls WHERE id = :id")
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }

    public List<LlmCallEntity> findByRunId(UUID runId) {
        return jdbcClient.sql("SELECT * FROM llm_calls WHERE run_id = :runId ORDER BY created_at ASC")
            .param("runId", runId)
            .query(ROW_MAPPER)
            .list();
    }

    public long findTotalCostToday() {
        Long sum = jdbcClient.sql("SELECT COALESCE(SUM(cost_micros), 0) FROM llm_calls WHERE created_at >= CURRENT_DATE")
            .query(Long.class)
            .single();
        return sum != null ? sum : 0L;
    }

    public long findTotalCostByRunId(UUID runId) {
        Long sum = jdbcClient.sql("SELECT COALESCE(SUM(cost_micros), 0) FROM llm_calls WHERE run_id = :runId")
            .param("runId", runId)
            .query(Long.class)
            .single();
        return sum != null ? sum : 0L;
    }

    public long countSuccessfulCallsByRunIdAndModel(UUID runId, String model) {
        Long count = jdbcClient.sql("SELECT count(*) FROM llm_calls WHERE run_id = :runId AND model = :model AND outcome = 'OK'")
            .param("runId", runId)
            .param("model", model)
            .query(Long.class)
            .single();
        return count != null ? count : 0L;
    }

    public record ModelUsageRecord(
        String model,
        long calls,
        long inputTokens,
        long outputTokens,
        long reasoningTokens,
        long totalCostMicros,
        int avgLatencyMs
    ) {}

    public List<ModelUsageRecord> findUsageSummaryByModel() {
        String sql = """
            SELECT
                model,
                COUNT(*) AS calls,
                COALESCE(SUM(input_tokens), 0) AS input_tokens,
                COALESCE(SUM(output_tokens), 0) AS output_tokens,
                COALESCE(SUM(reasoning_tokens), 0) AS reasoning_tokens,
                COALESCE(SUM(cost_micros), 0) AS total_cost_micros,
                COALESCE(AVG(latency_ms), 0)::INTEGER AS avg_latency_ms
            FROM llm_calls
            GROUP BY model
            ORDER BY total_cost_micros DESC
            """;

        return jdbcClient.sql(sql)
            .query((rs, rowNum) -> new ModelUsageRecord(
                rs.getString("model"),
                rs.getLong("calls"),
                rs.getLong("input_tokens"),
                rs.getLong("output_tokens"),
                rs.getLong("reasoning_tokens"),
                rs.getLong("total_cost_micros"),
                rs.getInt("avg_latency_ms")
            ))
            .list();
    }

    public long findTotalCostAllTime() {
        Long sum = jdbcClient.sql("SELECT COALESCE(SUM(cost_micros), 0) FROM llm_calls")
            .query(Long.class)
            .single();
        return sum != null ? sum : 0L;
    }

    public long findTotalCallsAllTime() {
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM llm_calls")
            .query(Long.class)
            .single();
        return count != null ? count : 0L;
    }
}
