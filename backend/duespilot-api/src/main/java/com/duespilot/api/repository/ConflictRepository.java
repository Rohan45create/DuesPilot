package com.duespilot.api.repository;

import com.duespilot.api.domain.ConflictEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class ConflictRepository {

    private final JdbcClient jdbcClient;

    public ConflictRepository(JdbcClient jdbcClient) {
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

    private static final RowMapper<ConflictEntity> ROW_MAPPER = (rs, rowNum) -> {
        try {
            return new ConflictEntity(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("run_id"),
                rs.getString("type"),
                rs.getString("detected_by"),
                rs.getString("severity"),
                rs.getString("summary"),
                rs.getObject("amount_paise") != null ? rs.getLong("amount_paise") : null,
                toUuidList(rs.getArray("evidence_field_ids")),
                rs.getString("status")
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to map ConflictEntity", e);
        }
    };

    public ConflictEntity save(ConflictEntity conflict) {
        UUID id = conflict.id() != null ? conflict.id() : UUID.randomUUID();
        UUID[] fieldIds = conflict.evidenceFieldIds().toArray(new UUID[0]);

        String sql = """
            INSERT INTO conflicts (id, run_id, type, detected_by, severity, summary, amount_paise, evidence_field_ids, status)
            VALUES (:id, :run_id, :type, :detected_by, :severity, :summary, :amount_paise, :evidence_field_ids, :status)
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("run_id", conflict.runId())
            .param("type", conflict.type())
            .param("detected_by", conflict.detectedBy())
            .param("severity", conflict.severity())
            .param("summary", conflict.summary())
            .param("amount_paise", conflict.amountPaise())
            .param("evidence_field_ids", fieldIds)
            .param("status", conflict.status() != null ? conflict.status() : "OPEN")
            .update();

        return new ConflictEntity(
            id,
            conflict.runId(),
            conflict.type(),
            conflict.detectedBy(),
            conflict.severity(),
            conflict.summary(),
            conflict.amountPaise(),
            conflict.evidenceFieldIds(),
            conflict.status() != null ? conflict.status() : "OPEN"
        );
    }

    public List<ConflictEntity> saveAll(List<ConflictEntity> conflicts) {
        List<ConflictEntity> saved = new ArrayList<>(conflicts.size());
        for (ConflictEntity conflict : conflicts) {
            saved.add(save(conflict));
        }
        return saved;
    }

    public List<ConflictEntity> findByRunId(UUID runId) {
        String sql = """
            SELECT * FROM conflicts
            WHERE run_id = :run_id
            ORDER BY type ASC, severity DESC
            """;
        return jdbcClient.sql(sql)
            .param("run_id", runId)
            .query(ROW_MAPPER)
            .list();
    }
}
