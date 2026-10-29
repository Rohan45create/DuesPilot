package com.duespilot.api.repository;

import com.duespilot.api.domain.LedgerEntryEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class LedgerEntryRepository {

    private final JdbcClient jdbcClient;

    public LedgerEntryRepository(JdbcClient jdbcClient) {
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

    private static final RowMapper<LedgerEntryEntity> ROW_MAPPER = (rs, rowNum) -> {
        try {
            return new LedgerEntryEntity(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("run_id"),
                rs.getString("kind"),
                rs.getString("ref"),
                rs.getLong("amount_paise"),
                rs.getDate("entry_date") != null ? rs.getDate("entry_date").toLocalDate() : null,
                rs.getString("status"),
                toUuidList(rs.getArray("source_field_ids"))
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to map LedgerEntryEntity", e);
        }
    };

    public LedgerEntryEntity save(LedgerEntryEntity entry) {
        UUID id = entry.id() != null ? entry.id() : UUID.randomUUID();
        UUID[] fieldIds = entry.sourceFieldIds().toArray(new UUID[0]);

        String sql = """
            INSERT INTO ledger_entries (id, run_id, kind, ref, amount_paise, entry_date, status, source_field_ids)
            VALUES (:id, :run_id, :kind, :ref, :amount_paise, :entry_date, :status, :source_field_ids)
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("run_id", entry.runId())
            .param("kind", entry.kind())
            .param("ref", entry.ref())
            .param("amount_paise", entry.amountPaise())
            .param("entry_date", entry.entryDate() != null ? Date.valueOf(entry.entryDate()) : null)
            .param("status", entry.status())
            .param("source_field_ids", fieldIds)
            .update();

        return new LedgerEntryEntity(
            id,
            entry.runId(),
            entry.kind(),
            entry.ref(),
            entry.amountPaise(),
            entry.entryDate(),
            entry.status(),
            entry.sourceFieldIds()
        );
    }

    public List<LedgerEntryEntity> saveAll(List<LedgerEntryEntity> entries) {
        List<LedgerEntryEntity> saved = new ArrayList<>(entries.size());
        for (LedgerEntryEntity entry : entries) {
            saved.add(save(entry));
        }
        return saved;
    }

    public List<LedgerEntryEntity> findByRunId(UUID runId) {
        String sql = """
            SELECT * FROM ledger_entries
            WHERE run_id = :run_id
            ORDER BY entry_date ASC NULLS LAST, id ASC
            """;
        return jdbcClient.sql(sql)
            .param("run_id", runId)
            .query(ROW_MAPPER)
            .list();
    }
}
