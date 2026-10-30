package com.duespilot.api.repository;

import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.domain.WorkspaceEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WorkspaceRepository {

    private final JdbcClient jdbcClient;

    public WorkspaceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<WorkspaceEntity> ROW_MAPPER = (rs, rowNum) -> new WorkspaceEntity(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        SupplierCategory.fromString(rs.getString("supplier_size_class")),
        rs.getDate("udyam_registration_date") != null ? rs.getDate("udyam_registration_date").toLocalDate() : null,
        rs.getString("owner_phone"),
        rs.getString("owner_whatsapp"),
        rs.getBoolean("is_demo"),
        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now(),
        rs.getInt("version")
    );

    public WorkspaceEntity save(WorkspaceEntity w) {
        UUID id = w.id() != null ? w.id() : UUID.randomUUID();
        Instant createdAt = w.createdAt() != null ? w.createdAt() : Instant.now();

        String sql = """
            INSERT INTO workspaces (id, name, supplier_size_class, udyam_registration_date, owner_phone, owner_whatsapp, is_demo, created_at, version)
            VALUES (:id, :name, :supplier_size_class, :udyam_registration_date, :owner_phone, :owner_whatsapp, :is_demo, :created_at, :version)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                supplier_size_class = EXCLUDED.supplier_size_class,
                udyam_registration_date = EXCLUDED.udyam_registration_date,
                owner_phone = EXCLUDED.owner_phone,
                owner_whatsapp = EXCLUDED.owner_whatsapp,
                is_demo = EXCLUDED.is_demo,
                version = workspaces.version + 1
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("name", w.name())
            .param("supplier_size_class", w.supplierSizeClass().name())
            .param("udyam_registration_date", w.udyamRegistrationDate())
            .param("owner_phone", w.ownerPhone())
            .param("owner_whatsapp", w.ownerWhatsapp())
            .param("is_demo", w.isDemo())
            .param("created_at", Timestamp.from(createdAt))
            .param("version", w.version())
            .update();

        return findById(id).orElseThrow();
    }

    public Optional<WorkspaceEntity> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM workspaces WHERE id = :id")
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }

    public java.util.List<WorkspaceEntity> findDemoWorkspacesOlderThan(Instant cutoff) {
        return jdbcClient.sql("SELECT * FROM workspaces WHERE is_demo = true AND created_at < :cutoff AND id != '00000000-0000-0000-0000-000000000001'::uuid")
            .param("cutoff", Timestamp.from(cutoff))
            .query(ROW_MAPPER)
            .list();
    }

    public boolean deleteById(UUID id) {
        return jdbcClient.sql("DELETE FROM workspaces WHERE id = :id")
            .param("id", id)
            .update() > 0;
    }
}
