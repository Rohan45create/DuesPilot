package com.duespilot.api.repository;

import com.duespilot.api.domain.ContactEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ContactRepository {

    private final JdbcClient jdbcClient;

    public ContactRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<ContactEntity> ROW_MAPPER = (rs, rowNum) -> new ContactEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("workspace_id"),
        (UUID) rs.getObject("case_id"),
        rs.getString("buyer_name"),
        rs.getString("email"),
        rs.getString("phone"),
        rs.getString("whatsapp_number"),
        rs.getBoolean("email_opt_in"),
        rs.getString("email_opt_in_source"),
        rs.getTimestamp("email_opt_in_at") != null ? rs.getTimestamp("email_opt_in_at").toInstant() : null,
        rs.getBoolean("whatsapp_opt_in"),
        rs.getString("whatsapp_opt_in_source"),
        rs.getTimestamp("whatsapp_opt_in_at") != null ? rs.getTimestamp("whatsapp_opt_in_at").toInstant() : null,
        rs.getTimestamp("unsubscribed_at") != null ? rs.getTimestamp("unsubscribed_at").toInstant() : null,
        rs.getBoolean("is_demo_data"),
        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now(),
        rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : Instant.now()
    );

    public ContactEntity save(ContactEntity c) {
        UUID id = c.id() != null ? c.id() : UUID.randomUUID();
        Instant now = Instant.now();
        Instant createdAt = c.createdAt() != null ? c.createdAt() : now;
        Instant updatedAt = now;

        String sql = """
            INSERT INTO contacts (
                id, workspace_id, case_id, name, buyer_name, email, phone, whatsapp_number,
                email_opt_in, email_opt_in_source, email_opt_in_at,
                whatsapp_opt_in, whatsapp_opt_in_source, whatsapp_opt_in_at,
                unsubscribed_at, is_demo_data, created_at, updated_at
            )
            VALUES (
                :id, :workspace_id, :case_id, :name, :buyer_name, :email, :phone, :whatsapp_number,
                :email_opt_in, :email_opt_in_source, :email_opt_in_at,
                :whatsapp_opt_in, :whatsapp_opt_in_source, :whatsapp_opt_in_at,
                :unsubscribed_at, :is_demo_data, :created_at, :updated_at
            )
            ON CONFLICT (id) DO UPDATE SET
                case_id = EXCLUDED.case_id,
                name = EXCLUDED.name,
                buyer_name = EXCLUDED.buyer_name,
                email = EXCLUDED.email,
                phone = EXCLUDED.phone,
                whatsapp_number = EXCLUDED.whatsapp_number,
                email_opt_in = EXCLUDED.email_opt_in,
                email_opt_in_source = EXCLUDED.email_opt_in_source,
                email_opt_in_at = EXCLUDED.email_opt_in_at,
                whatsapp_opt_in = EXCLUDED.whatsapp_opt_in,
                whatsapp_opt_in_source = EXCLUDED.whatsapp_opt_in_source,
                whatsapp_opt_in_at = EXCLUDED.whatsapp_opt_in_at,
                unsubscribed_at = EXCLUDED.unsubscribed_at,
                is_demo_data = EXCLUDED.is_demo_data,
                updated_at = EXCLUDED.updated_at
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("workspace_id", c.workspaceId())
            .param("case_id", c.caseId())
            .param("name", c.buyerName())
            .param("buyer_name", c.buyerName())
            .param("email", c.email())
            .param("phone", c.phone())
            .param("whatsapp_number", c.whatsappNumber())
            .param("email_opt_in", c.emailOptIn())
            .param("email_opt_in_source", c.emailOptInSource())
            .param("email_opt_in_at", c.emailOptInAt() != null ? Timestamp.from(c.emailOptInAt()) : null)
            .param("whatsapp_opt_in", c.whatsappOptIn())
            .param("whatsapp_opt_in_source", c.whatsappOptInSource())
            .param("whatsapp_opt_in_at", c.whatsappOptInAt() != null ? Timestamp.from(c.whatsappOptInAt()) : null)
            .param("unsubscribed_at", c.unsubscribedAt() != null ? Timestamp.from(c.unsubscribedAt()) : null)
            .param("is_demo_data", c.isDemoData())
            .param("created_at", Timestamp.from(createdAt))
            .param("updated_at", Timestamp.from(updatedAt))
            .update();

        return new ContactEntity(
            id, c.workspaceId(), c.caseId(), c.buyerName(), c.email(), c.phone(), c.whatsappNumber(),
            c.emailOptIn(), c.emailOptInSource(), c.emailOptInAt(),
            c.whatsappOptIn(), c.whatsappOptInSource(), c.whatsappOptInAt(),
            c.unsubscribedAt(), c.isDemoData(), createdAt, updatedAt
        );
    }

    public Optional<ContactEntity> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM contacts WHERE id = :id")
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }

    public Optional<ContactEntity> findByCaseId(UUID caseId) {
        return jdbcClient.sql("SELECT * FROM contacts WHERE case_id = :caseId ORDER BY created_at DESC LIMIT 1")
            .param("caseId", caseId)
            .query(ROW_MAPPER)
            .optional();
    }

    public Optional<ContactEntity> findByWorkspaceAndCaseId(UUID workspaceId, UUID caseId) {
        return jdbcClient.sql("SELECT * FROM contacts WHERE workspace_id = :workspaceId AND case_id = :caseId ORDER BY created_at DESC LIMIT 1")
            .param("workspaceId", workspaceId)
            .param("caseId", caseId)
            .query(ROW_MAPPER)
            .optional();
    }

    public List<ContactEntity> findByWhatsappNumber(String whatsappNumber) {
        // Match exact or cleaned phone number
        String clean = whatsappNumber.replaceAll("[^0-9]", "");
        return jdbcClient.sql("""
            SELECT * FROM contacts
            WHERE whatsapp_number = :exact
               OR REPLACE(REPLACE(whatsapp_number, '+', ''), ' ', '') = :clean
               OR phone = :exact
            """)
            .param("exact", whatsappNumber)
            .param("clean", clean)
            .query(ROW_MAPPER)
            .list();
    }

    public void updateUnsubscribed(UUID contactId, Instant unsubscribedAt) {
        jdbcClient.sql("""
            UPDATE contacts
            SET unsubscribed_at = :unsubscribedAt,
                email_opt_in = FALSE,
                whatsapp_opt_in = FALSE,
                updated_at = NOW()
            WHERE id = :id
            """)
            .param("id", contactId)
            .param("unsubscribedAt", Timestamp.from(unsubscribedAt))
            .update();
    }

    public int deleteDemoContactsByWorkspaceId(UUID workspaceId) {
        return jdbcClient.sql("DELETE FROM contacts WHERE workspace_id = :workspaceId AND is_demo_data = true")
            .param("workspaceId", workspaceId)
            .update();
    }

    public long countDemoContactsByWorkspaceId(UUID workspaceId) {
        Long cnt = jdbcClient.sql("SELECT count(*) FROM contacts WHERE workspace_id = :workspaceId AND is_demo_data = true")
            .param("workspaceId", workspaceId)
            .query(Long.class)
            .single();
        return cnt != null ? cnt : 0L;
    }
}

