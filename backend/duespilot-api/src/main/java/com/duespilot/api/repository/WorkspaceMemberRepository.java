package com.duespilot.api.repository;

import com.duespilot.api.domain.WorkspaceMemberEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WorkspaceMemberRepository {

    private final JdbcClient jdbcClient;

    public WorkspaceMemberRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<WorkspaceMemberEntity> ROW_MAPPER = (rs, rowNum) -> new WorkspaceMemberEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("workspace_id"),
        rs.getString("user_id"),
        rs.getString("role"),
        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
    );

    public WorkspaceMemberEntity save(WorkspaceMemberEntity member) {
        UUID id = member.id() != null ? member.id() : UUID.randomUUID();
        Instant createdAt = member.createdAt() != null ? member.createdAt() : Instant.now();

        String sql = """
            INSERT INTO workspace_members (id, workspace_id, user_id, role, created_at)
            VALUES (:id, :workspace_id, :user_id, :role, :created_at)
            ON CONFLICT (workspace_id, user_id) DO UPDATE SET
                role = EXCLUDED.role
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("workspace_id", member.workspaceId())
            .param("user_id", member.userId())
            .param("role", member.role())
            .param("created_at", Timestamp.from(createdAt))
            .update();

        return findById(id).orElseThrow();
    }

    public Optional<WorkspaceMemberEntity> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM workspace_members WHERE id = :id")
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }

    public Optional<UUID> findWorkspaceIdByUserId(String userId) {
        return jdbcClient.sql("SELECT workspace_id FROM workspace_members WHERE user_id = :userId ORDER BY created_at ASC LIMIT 1")
            .param("userId", userId)
            .query(UUID.class)
            .optional();
    }

    public long countWorkspacesForUser(String userId) {
        Long cnt = jdbcClient.sql("SELECT count(*) FROM workspace_members WHERE user_id = :userId")
            .param("userId", userId)
            .query(Long.class)
            .single();
        return cnt != null ? cnt : 0L;
    }
}
