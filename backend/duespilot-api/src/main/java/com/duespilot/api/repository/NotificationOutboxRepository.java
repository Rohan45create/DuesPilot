package com.duespilot.api.repository;

import com.duespilot.api.domain.NotificationOutboxEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NotificationOutboxRepository {

    private final JdbcClient jdbcClient;

    public NotificationOutboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<NotificationOutboxEntity> ROW_MAPPER = (rs, rowNum) -> new NotificationOutboxEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("workspace_id"),
        (UUID) rs.getObject("case_id"),
        (UUID) rs.getObject("action_id"),
        rs.getString("channel"),
        rs.getString("recipient"),
        rs.getString("template_key"),
        rs.getString("rendered_body"),
        rs.getString("status"),
        rs.getString("provider_message_id"),
        rs.getString("error"),
        rs.getTimestamp("scheduled_for") != null ? rs.getTimestamp("scheduled_for").toInstant() : null,
        rs.getTimestamp("sent_at") != null ? rs.getTimestamp("sent_at").toInstant() : null,
        rs.getString("idempotency_key"),
        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
    );

    public NotificationOutboxEntity save(NotificationOutboxEntity o) {
        UUID id = o.id() != null ? o.id() : UUID.randomUUID();
        Instant createdAt = o.createdAt() != null ? o.createdAt() : Instant.now();

        String sql = """
            INSERT INTO notification_outbox (
                id, workspace_id, case_id, action_id, channel, recipient, template_key,
                rendered_body, status, provider_message_id, error, scheduled_for, sent_at,
                idempotency_key, created_at
            )
            VALUES (
                :id, :workspace_id, :case_id, :action_id, :channel, :recipient, :template_key,
                :rendered_body, :status, :provider_message_id, :error, :scheduled_for, :sent_at,
                :idempotency_key, :created_at
            )
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                provider_message_id = EXCLUDED.provider_message_id,
                error = EXCLUDED.error,
                scheduled_for = EXCLUDED.scheduled_for,
                sent_at = EXCLUDED.sent_at
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("workspace_id", o.workspaceId())
            .param("case_id", o.caseId())
            .param("action_id", o.actionId())
            .param("channel", o.channel())
            .param("recipient", o.recipient())
            .param("template_key", o.templateKey())
            .param("rendered_body", o.renderedBody())
            .param("status", o.status())
            .param("provider_message_id", o.providerMessageId())
            .param("error", o.error())
            .param("scheduled_for", o.scheduledFor() != null ? Timestamp.from(o.scheduledFor()) : null)
            .param("sent_at", o.sentAt() != null ? Timestamp.from(o.sentAt()) : null)
            .param("idempotency_key", o.idempotencyKey())
            .param("created_at", Timestamp.from(createdAt))
            .update();

        return new NotificationOutboxEntity(
            id, o.workspaceId(), o.caseId(), o.actionId(), o.channel(), o.recipient(),
            o.templateKey(), o.renderedBody(), o.status(), o.providerMessageId(), o.error(),
            o.scheduledFor(), o.sentAt(), o.idempotencyKey(), createdAt
        );
    }

    public Optional<NotificationOutboxEntity> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM notification_outbox WHERE id = :id")
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }

    public Optional<NotificationOutboxEntity> findByWorkspaceAndId(UUID workspaceId, UUID id) {
        return jdbcClient.sql("SELECT * FROM notification_outbox WHERE workspace_id = :workspaceId AND id = :id")
            .param("workspaceId", workspaceId)
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }

    public List<NotificationOutboxEntity> findByCaseId(UUID caseId) {
        return jdbcClient.sql("SELECT * FROM notification_outbox WHERE case_id = :caseId ORDER BY created_at DESC")
            .param("caseId", caseId)
            .query(ROW_MAPPER)
            .list();
    }

    public List<NotificationOutboxEntity> findByWorkspaceAndCaseId(UUID workspaceId, UUID caseId) {
        return jdbcClient.sql("""
            SELECT * FROM notification_outbox
            WHERE workspace_id = :workspaceId AND case_id = :caseId
            ORDER BY created_at DESC
            """)
            .param("workspaceId", workspaceId)
            .param("caseId", caseId)
            .query(ROW_MAPPER)
            .list();
    }

    /**
     * Checks if a notification was sent on the specified case and channel within the given cool-down window.
     */
    public List<NotificationOutboxEntity> findRecentSentByCaseAndChannel(UUID caseId, String channelPrefix, Instant since) {
        // Group WHATSAPP_LINK and WHATSAPP_API into WhatsApp channel for cool-down
        List<String> channels = channelPrefix.startsWith("WHATSAPP")
            ? List.of("WHATSAPP_LINK", "WHATSAPP_API")
            : List.of(channelPrefix);

        String sql = """
            SELECT * FROM notification_outbox
            WHERE case_id = :caseId
              AND channel IN (:channels)
              AND status IN ('SENT', 'DELIVERED', 'MANUAL_PENDING', 'MANUAL_SENT')
              AND (sent_at >= :since OR (sent_at IS NULL AND created_at >= :since))
            ORDER BY created_at DESC
            """;

        return jdbcClient.sql(sql)
            .param("caseId", caseId)
            .param("channels", channels)
            .param("since", Timestamp.from(since))
            .query(ROW_MAPPER).list();
    }

    /**
     * Counts the total number of notifications sent for a case (R-75: max 3 follow-ups).
     */
    public int countSentByCase(UUID caseId) {
        Integer count = jdbcClient.sql("""
            SELECT COUNT(*) FROM notification_outbox
            WHERE case_id = :caseId
              AND status IN ('SENT', 'DELIVERED', 'MANUAL_SENT', 'MANUAL_PENDING')
            """)
            .param("caseId", caseId)
            .query(Integer.class)
            .single();
        return count != null ? count : 0;
    }

    public void updateStatus(UUID id, String status, @Nullable String providerMessageId, @Nullable String error, @Nullable Instant sentAt) {
        jdbcClient.sql("""
            UPDATE notification_outbox
            SET status = :status,
                provider_message_id = COALESCE(:providerMessageId, provider_message_id),
                error = :error,
                sent_at = COALESCE(:sentAt, sent_at)
            WHERE id = :id
            """)
            .param("id", id)
            .param("status", status)
            .param("providerMessageId", providerMessageId)
            .param("error", error)
            .param("sentAt", sentAt != null ? Timestamp.from(sentAt) : null)
            .update();
    }

    public Optional<NotificationOutboxEntity> findByProviderMessageId(String providerMessageId) {
        return jdbcClient.sql("SELECT * FROM notification_outbox WHERE provider_message_id = :providerMessageId LIMIT 1")
            .param("providerMessageId", providerMessageId)
            .query(ROW_MAPPER)
            .optional();
    }
}
