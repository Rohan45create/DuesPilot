package com.duespilot.api.repository;

import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.SupplierCategory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CaseRepository {

    private final JdbcClient jdbcClient;

    public CaseRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<CaseEntity> ROW_MAPPER = (rs, rowNum) -> new CaseEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("workspace_id"),
        rs.getString("code"),
        rs.getString("title"),
        rs.getString("supplier_name"),
        rs.getString("buyer_name"),
        SupplierCategory.fromString(rs.getString("supplier_category")),
        rs.getDate("udyam_registered_on") != null ? rs.getDate("udyam_registered_on").toLocalDate() : null,
        rs.getBoolean("is_seed"),
        rs.getBoolean("is_demo_data"),
        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now(),
        rs.getInt("version")
    );

    public CaseEntity save(CaseEntity c) {
        UUID id = c.id() != null ? c.id() : UUID.randomUUID();
        UUID workspaceId = c.workspaceId();
        if (workspaceId == null) {
            throw new IllegalArgumentException("Case workspace_id must not be null");
        }
        Instant createdAt = c.createdAt() != null ? c.createdAt() : Instant.now();

        String sql = """
            INSERT INTO cases (id, workspace_id, code, title, supplier_name, buyer_name, supplier_category, udyam_registered_on, is_seed, is_demo_data, created_at, version)
            VALUES (:id, :workspace_id, :code, :title, :supplier_name, :buyer_name, :supplier_category, :udyam_registered_on, :is_seed, :is_demo_data, :created_at, :version)
            ON CONFLICT (id) DO UPDATE SET
                workspace_id = EXCLUDED.workspace_id,
                code = EXCLUDED.code,
                title = EXCLUDED.title,
                supplier_name = EXCLUDED.supplier_name,
                buyer_name = EXCLUDED.buyer_name,
                supplier_category = EXCLUDED.supplier_category,
                udyam_registered_on = EXCLUDED.udyam_registered_on,
                is_seed = EXCLUDED.is_seed,
                is_demo_data = EXCLUDED.is_demo_data,
                version = cases.version + 1
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("workspace_id", workspaceId)
            .param("code", c.code())
            .param("title", c.title())
            .param("supplier_name", c.supplierName())
            .param("buyer_name", c.buyerName())
            .param("supplier_category", c.supplierCategory().name())
            .param("udyam_registered_on", c.udyamRegisteredOn())
            .param("is_seed", c.isSeed())
            .param("is_demo_data", c.isDemoData())
            .param("created_at", Timestamp.from(createdAt))
            .param("version", c.version())
            .update();

        return findById(id).orElseThrow();
    }

    public Optional<CaseEntity> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM cases WHERE id = :id")
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }

    public Optional<CaseEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId) {
        return jdbcClient.sql("SELECT * FROM cases WHERE id = :id AND workspace_id = :workspaceId")
            .param("id", id)
            .param("workspaceId", workspaceId)
            .query(ROW_MAPPER)
            .optional();
    }

    public Optional<CaseEntity> findByCode(String code) {
        return jdbcClient.sql("SELECT * FROM cases WHERE code = :code")
            .param("code", code)
            .query(ROW_MAPPER)
            .optional();
    }

    public Optional<CaseEntity> findByCodeAndWorkspaceId(String code, UUID workspaceId) {
        return jdbcClient.sql("SELECT * FROM cases WHERE code = :code AND workspace_id = :workspaceId")
            .param("code", code)
            .param("workspaceId", workspaceId)
            .query(ROW_MAPPER)
            .optional();
    }

    public List<CaseEntity> findAll() {
        return jdbcClient.sql("SELECT * FROM cases ORDER BY created_at DESC")
            .query(ROW_MAPPER)
            .list();
    }

    public List<CaseEntity> findAllByWorkspaceId(UUID workspaceId) {
        return jdbcClient.sql("SELECT * FROM cases WHERE workspace_id = :workspaceId ORDER BY created_at DESC")
            .param("workspaceId", workspaceId)
            .query(ROW_MAPPER)
            .list();
    }

    public long count() {
        Long cnt = jdbcClient.sql("SELECT count(*) FROM cases")
            .query(Long.class)
            .single();
        return cnt != null ? cnt : 0L;
    }

    public long countByWorkspaceId(UUID workspaceId) {
        Long cnt = jdbcClient.sql("SELECT count(*) FROM cases WHERE workspace_id = :workspaceId")
            .param("workspaceId", workspaceId)
            .query(Long.class)
            .single();
        return cnt != null ? cnt : 0L;
    }

    public String nextCaseCode() {
        long count = count();
        return String.format("DP-%03d", count + 1);
    }

    public String nextCaseCode(UUID workspaceId) {
        long count = countByWorkspaceId(workspaceId);
        return String.format("DP-%03d", count + 1);
    }

    public boolean deleteById(UUID id) {
        int rows = jdbcClient.sql("DELETE FROM cases WHERE id = :id")
            .param("id", id)
            .update();
        return rows > 0;
    }

    public boolean deleteByIdAndWorkspaceId(UUID id, UUID workspaceId) {
        int rows = jdbcClient.sql("DELETE FROM cases WHERE id = :id AND workspace_id = :workspaceId")
            .param("id", id)
            .param("workspaceId", workspaceId)
            .update();
        return rows > 0;
    }

    public List<CaseEntity> findNonSeedOlderThan(Instant cutoff) {
        return jdbcClient.sql("SELECT * FROM cases WHERE is_seed = false AND created_at < :cutoff ORDER BY created_at ASC")
            .param("cutoff", Timestamp.from(cutoff))
            .query(ROW_MAPPER)
            .list();
    }

    public int deleteNonSeedOlderThan(Instant cutoff) {
        return jdbcClient.sql("DELETE FROM cases WHERE is_seed = false AND created_at < :cutoff")
            .param("cutoff", Timestamp.from(cutoff))
            .update();
    }

    public List<CaseEntity> findSeedCases() {
        return jdbcClient.sql("SELECT * FROM cases WHERE is_seed = true ORDER BY code ASC")
            .query(ROW_MAPPER)
            .list();
    }

    public boolean hasDemoData(UUID workspaceId) {
        Long cnt = jdbcClient.sql("SELECT count(*) FROM cases WHERE workspace_id = :workspaceId AND is_demo_data = true")
            .param("workspaceId", workspaceId)
            .query(Long.class)
            .single();
        return cnt != null && cnt > 0;
    }

    public long countDemoData(UUID workspaceId) {
        Long cnt = jdbcClient.sql("SELECT count(*) FROM cases WHERE workspace_id = :workspaceId AND is_demo_data = true")
            .param("workspaceId", workspaceId)
            .query(Long.class)
            .single();
        return cnt != null ? cnt : 0L;
    }

    public List<CaseEntity> findDemoCasesByWorkspaceId(UUID workspaceId) {
        return jdbcClient.sql("SELECT * FROM cases WHERE workspace_id = :workspaceId AND is_demo_data = true ORDER BY code ASC")
            .param("workspaceId", workspaceId)
            .query(ROW_MAPPER)
            .list();
    }

    public int deleteDemoDataByWorkspaceId(UUID workspaceId) {
        return jdbcClient.sql("DELETE FROM cases WHERE workspace_id = :workspaceId AND is_demo_data = true")
            .param("workspaceId", workspaceId)
            .update();
    }

    public int deleteByWorkspaceId(UUID workspaceId) {
        return jdbcClient.sql("DELETE FROM cases WHERE workspace_id = :workspaceId")
            .param("workspaceId", workspaceId)
            .update();
    }
}
