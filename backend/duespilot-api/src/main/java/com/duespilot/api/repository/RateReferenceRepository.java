package com.duespilot.api.repository;

import com.duespilot.api.domain.RateReferenceEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RateReferenceRepository {

    private final JdbcClient jdbcClient;

    public RateReferenceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<RateReferenceEntity> ROW_MAPPER = (rs, rowNum) -> new RateReferenceEntity(
        (UUID) rs.getObject("id"),
        rs.getString("source_url"),
        rs.getString("raw_excerpt"),
        rs.getInt("rate_bps"),
        rs.getDate("effective_on").toLocalDate(),
        rs.getTimestamp("retrieved_at").toInstant(),
        rs.getTimestamp("confirmed_at") != null ? rs.getTimestamp("confirmed_at").toInstant() : null,
        rs.getString("confirmed_by")
    );

    public RateReferenceEntity save(RateReferenceEntity rateRef) {
        UUID id = rateRef.id() != null ? rateRef.id() : UUID.randomUUID();
        Instant retrievedAt = rateRef.retrievedAt() != null ? rateRef.retrievedAt() : Instant.now();

        String sql = """
            INSERT INTO rate_references (id, source_url, raw_excerpt, rate_bps, effective_on, retrieved_at, confirmed_at, confirmed_by)
            VALUES (:id, :source_url, :raw_excerpt, :rate_bps, :effective_on, :retrieved_at, :confirmed_at, :confirmed_by)
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("source_url", rateRef.sourceUrl())
            .param("raw_excerpt", rateRef.rawExcerpt())
            .param("rate_bps", rateRef.rateBps())
            .param("effective_on", Date.valueOf(rateRef.effectiveOn()))
            .param("retrieved_at", Timestamp.from(retrievedAt))
            .param("confirmed_at", rateRef.confirmedAt() != null ? Timestamp.from(rateRef.confirmedAt()) : null)
            .param("confirmed_by", rateRef.confirmedBy())
            .update();

        return findById(id).orElseThrow();
    }

    public Optional<RateReferenceEntity> findById(UUID id) {
        String sql = "SELECT * FROM rate_references WHERE id = :id";
        return jdbcClient.sql(sql)
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }

    public Optional<RateReferenceEntity> findLatestConfirmed() {
        String sql = """
            SELECT * FROM rate_references
            WHERE confirmed_at IS NOT NULL
            ORDER BY confirmed_at DESC, effective_on DESC
            LIMIT 1
            """;
        return jdbcClient.sql(sql)
            .query(ROW_MAPPER)
            .optional();
    }

    public Optional<RateReferenceEntity> findLatest() {
        String sql = """
            SELECT * FROM rate_references
            ORDER BY retrieved_at DESC
            LIMIT 1
            """;
        return jdbcClient.sql(sql)
            .query(ROW_MAPPER)
            .optional();
    }

    public boolean confirm(UUID id, String confirmedBy, Instant confirmedAt) {
        String sql = """
            UPDATE rate_references
            SET confirmed_at = :confirmed_at,
                confirmed_by = :confirmed_by
            WHERE id = :id
            """;
        int updated = jdbcClient.sql(sql)
            .param("id", id)
            .param("confirmed_at", Timestamp.from(confirmedAt))
            .param("confirmed_by", confirmedBy)
            .update();
        return updated > 0;
    }
}
