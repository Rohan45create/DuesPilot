package com.duespilot.api.repository;

import com.duespilot.api.domain.ReconciliationResultEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ReconciliationResultRepository {

    private final JdbcClient jdbcClient;

    public ReconciliationResultRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<ReconciliationResultEntity> ROW_MAPPER = (rs, rowNum) -> new ReconciliationResultEntity(
        (UUID) rs.getObject("run_id"),
        rs.getObject("invoiced_paise") != null ? rs.getLong("invoiced_paise") : null,
        rs.getObject("confirmed_paid_paise") != null ? rs.getLong("confirmed_paid_paise") : null,
        rs.getObject("confirmed_credit_paise") != null ? rs.getLong("confirmed_credit_paise") : null,
        rs.getObject("disputed_paise") != null ? rs.getLong("disputed_paise") : null,
        rs.getObject("unverified_paise") != null ? rs.getLong("unverified_paise") : null,
        rs.getObject("balance_paise") != null ? rs.getLong("balance_paise") : null,
        rs.getString("balance_status")
    );

    public ReconciliationResultEntity save(ReconciliationResultEntity result) {
        String sql = """
            INSERT INTO reconciliation_results (run_id, invoiced_paise, confirmed_paid_paise, confirmed_credit_paise, disputed_paise, unverified_paise, balance_paise, balance_status)
            VALUES (:run_id, :invoiced_paise, :confirmed_paid_paise, :confirmed_credit_paise, :disputed_paise, :unverified_paise, :balance_paise, :balance_status)
            ON CONFLICT (run_id) DO UPDATE SET
                invoiced_paise = EXCLUDED.invoiced_paise,
                confirmed_paid_paise = EXCLUDED.confirmed_paid_paise,
                confirmed_credit_paise = EXCLUDED.confirmed_credit_paise,
                disputed_paise = EXCLUDED.disputed_paise,
                unverified_paise = EXCLUDED.unverified_paise,
                balance_paise = EXCLUDED.balance_paise,
                balance_status = EXCLUDED.balance_status
            """;

        jdbcClient.sql(sql)
            .param("run_id", result.runId())
            .param("invoiced_paise", result.invoicedPaise())
            .param("confirmed_paid_paise", result.confirmedPaidPaise())
            .param("confirmed_credit_paise", result.confirmedCreditPaise())
            .param("disputed_paise", result.disputedPaise())
            .param("unverified_paise", result.unverifiedPaise())
            .param("balance_paise", result.balancePaise())
            .param("balance_status", result.balanceStatus())
            .update();

        return result;
    }

    public Optional<ReconciliationResultEntity> findByRunId(UUID runId) {
        String sql = "SELECT * FROM reconciliation_results WHERE run_id = :run_id";
        return jdbcClient.sql(sql)
            .param("run_id", runId)
            .query(ROW_MAPPER)
            .optional();
    }
}
