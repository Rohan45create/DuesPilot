package com.duespilot.api.repository;

import com.duespilot.api.domain.AuditEventEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuditEventRepository {

    private final JdbcClient jdbcClient;

    public AuditEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<AuditEventEntity> ROW_MAPPER = (rs, rowNum) -> new AuditEventEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("case_id"),
        rs.getString("actor"),
        rs.getString("event_type"),
        rs.getString("payload"),
        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
    );

    public AuditEventEntity save(AuditEventEntity event) {
        UUID id = event.id() != null ? event.id() : UUID.randomUUID();
        Instant createdAt = event.createdAt() != null ? event.createdAt() : Instant.now();
        String payload = (event.payload() != null && !event.payload().isBlank()) ? event.payload() : "{}";

        String sql = """
            INSERT INTO audit_events (id, case_id, actor, event_type, payload, created_at)
            VALUES (:id, :case_id, :actor, :event_type, CAST(:payload AS JSONB), :created_at)
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("case_id", event.caseId())
            .param("actor", event.actor())
            .param("event_type", event.eventType())
            .param("payload", payload)
            .param("created_at", Timestamp.from(createdAt))
            .update();

        return findById(id).orElseThrow();
    }

    public Optional<AuditEventEntity> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM audit_events WHERE id = :id")
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }

    public List<AuditEventEntity> findByCaseId(UUID caseId) {
        return jdbcClient.sql("SELECT * FROM audit_events WHERE case_id = :caseId ORDER BY created_at ASC")
            .param("caseId", caseId)
            .query(ROW_MAPPER)
            .list();
    }

    public List<AuditEventEntity> findAll() {
        return jdbcClient.sql("SELECT * FROM audit_events ORDER BY created_at DESC")
            .query(ROW_MAPPER)
            .list();
    }
}
