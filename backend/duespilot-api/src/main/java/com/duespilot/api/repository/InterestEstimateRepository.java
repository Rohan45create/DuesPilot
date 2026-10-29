package com.duespilot.api.repository;

import com.duespilot.api.domain.InterestEstimateEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InterestEstimateRepository {

    private final JdbcClient jdbcClient;

    public InterestEstimateRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<InterestEstimateEntity> ROW_MAPPER = (rs, rowNum) -> new InterestEstimateEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("run_id"),
        rs.getLong("principal_paise"),
        (UUID) rs.getObject("rate_ref_id"),
        rs.getInt("multiplier"),
        rs.getDate("from_date").toLocalDate(),
        rs.getDate("to_date").toLocalDate(),
        rs.getLong("interest_paise"),
        rs.getString("convention"),
        rs.getString("status")
    );

    public InterestEstimateEntity save(InterestEstimateEntity estimate) {
        UUID id = estimate.id() != null ? estimate.id() : UUID.randomUUID();

        String sql = """
            INSERT INTO interest_estimates (id, run_id, principal_paise, rate_ref_id, multiplier, from_date, to_date, interest_paise, convention, status)
            VALUES (:id, :run_id, :principal_paise, :rate_ref_id, :multiplier, :from_date, :to_date, :interest_paise, :convention, :status)
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("run_id", estimate.runId())
            .param("principal_paise", estimate.principalPaise())
            .param("rate_ref_id", estimate.rateRefId())
            .param("multiplier", estimate.multiplier())
            .param("from_date", Date.valueOf(estimate.fromDate()))
            .param("to_date", Date.valueOf(estimate.toDate()))
            .param("interest_paise", estimate.interestPaise())
            .param("convention", estimate.convention())
            .param("status", estimate.status() != null ? estimate.status() : "PROVISIONAL")
            .update();

        return new InterestEstimateEntity(
            id,
            estimate.runId(),
            estimate.principalPaise(),
            estimate.rateRefId(),
            estimate.multiplier(),
            estimate.fromDate(),
            estimate.toDate(),
            estimate.interestPaise(),
            estimate.convention(),
            estimate.status() != null ? estimate.status() : "PROVISIONAL"
        );
    }

    public Optional<InterestEstimateEntity> findByRunId(UUID runId) {
        String sql = "SELECT * FROM interest_estimates WHERE run_id = :run_id";
        return jdbcClient.sql(sql)
            .param("run_id", runId)
            .query(ROW_MAPPER)
            .optional();
    }
}
