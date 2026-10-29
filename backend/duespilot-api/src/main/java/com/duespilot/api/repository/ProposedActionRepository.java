package com.duespilot.api.repository;

import com.duespilot.api.domain.ProposedActionEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProposedActionRepository {

    private final JdbcClient jdbcClient;

    public ProposedActionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static List<UUID> toUuidList(Array array) throws SQLException {
        if (array == null) return List.of();
        Object[] objArr = (Object[]) array.getArray();
        List<UUID> list = new ArrayList<>(objArr.length);
        for (Object o : objArr) {
            if (o instanceof UUID u) {
                list.add(u);
            } else if (o != null) {
                list.add(UUID.fromString(o.toString()));
            }
        }
        return list;
    }

    private static final RowMapper<ProposedActionEntity> ROW_MAPPER = (rs, rowNum) -> {
        try {
            return new ProposedActionEntity(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("run_id"),
                rs.getString("action_type"),
                rs.getString("language"),
                rs.getString("draft_text"),
                toUuidList(rs.getArray("evidence_field_ids")),
                rs.getString("status"),
                rs.getString("decided_by"),
                rs.getTimestamp("decided_at") != null ? rs.getTimestamp("decided_at").toInstant() : null
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to map ProposedActionEntity", e);
        }
    };

    public ProposedActionEntity save(ProposedActionEntity action) {
        UUID id = action.id() != null ? action.id() : UUID.randomUUID();
        UUID[] fieldIds = action.evidenceFieldIds().toArray(new UUID[0]);

        String sql = """
            INSERT INTO proposed_actions (id, run_id, action_type, language, draft_text, evidence_field_ids, status, decided_by, decided_at)
            VALUES (:id, :run_id, :action_type, :language, :draft_text, :evidence_field_ids, :status, :decided_by, :decided_at)
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("run_id", action.runId())
            .param("action_type", action.actionType())
            .param("language", action.language())
            .param("draft_text", action.draftText())
            .param("evidence_field_ids", fieldIds)
            .param("status", action.status() != null ? action.status() : "PROPOSED")
            .param("decided_by", action.decidedBy())
            .param("decided_at", action.decidedAt() != null ? Timestamp.from(action.decidedAt()) : null)
            .update();

        return new ProposedActionEntity(
            id,
            action.runId(),
            action.actionType(),
            action.language(),
            action.draftText(),
            action.evidenceFieldIds(),
            action.status() != null ? action.status() : "PROPOSED",
            action.decidedBy(),
            action.decidedAt()
        );
    }

    public List<ProposedActionEntity> saveAll(List<ProposedActionEntity> actions) {
        List<ProposedActionEntity> saved = new ArrayList<>(actions.size());
        for (ProposedActionEntity action : actions) {
            saved.add(save(action));
        }
        return saved;
    }

    public Optional<ProposedActionEntity> findById(UUID id) {
        String sql = "SELECT * FROM proposed_actions WHERE id = :id";
        return jdbcClient.sql(sql)
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }

    public List<ProposedActionEntity> findByRunId(UUID runId) {
        String sql = """
            SELECT * FROM proposed_actions
            WHERE run_id = :run_id
            ORDER BY action_type ASC, language ASC
            """;
        return jdbcClient.sql(sql)
            .param("run_id", runId)
            .query(ROW_MAPPER)
            .list();
    }

    public boolean updateDecision(UUID id, String status, String decidedBy, Instant decidedAt) {
        String sql = """
            UPDATE proposed_actions
            SET status = :status,
                decided_by = :decided_by,
                decided_at = :decided_at
            WHERE id = :id
            """;
        int rows = jdbcClient.sql(sql)
            .param("id", id)
            .param("status", status)
            .param("decided_by", decidedBy)
            .param("decided_at", Timestamp.from(decidedAt))
            .update();
        return rows > 0;
    }
}
