package com.duespilot.api.repository;

import com.duespilot.api.domain.UserEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<UserEntity> ROW_MAPPER = (rs, rowNum) -> new UserEntity(
        rs.getString("id"),
        rs.getString("email"),
        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
    );

    public UserEntity save(UserEntity user) {
        Instant createdAt = user.createdAt() != null ? user.createdAt() : Instant.now();

        String sql = """
            INSERT INTO users (id, email, created_at)
            VALUES (:id, :email, :created_at)
            ON CONFLICT (id) DO UPDATE SET
                email = EXCLUDED.email
            """;

        jdbcClient.sql(sql)
            .param("id", user.id())
            .param("email", user.email())
            .param("created_at", Timestamp.from(createdAt))
            .update();

        return findById(user.id()).orElseThrow();
    }

    public Optional<UserEntity> findById(String id) {
        return jdbcClient.sql("SELECT * FROM users WHERE id = :id")
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }
}
