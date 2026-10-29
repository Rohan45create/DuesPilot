package com.duespilot.api.repository;

import com.duespilot.api.domain.DocumentPageEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentPageRepository {

    private final JdbcClient jdbcClient;

    public DocumentPageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<DocumentPageEntity> ROW_MAPPER = (rs, rowNum) -> new DocumentPageEntity(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("document_id"),
        rs.getInt("page_no"),
        rs.getString("text")
    );

    public DocumentPageEntity save(DocumentPageEntity page) {
        UUID id = page.id() != null ? page.id() : UUID.randomUUID();

        String sql = """
            INSERT INTO document_pages (id, document_id, page_no, text)
            VALUES (:id, :document_id, :page_no, :text)
            ON CONFLICT (document_id, page_no) DO UPDATE SET
                text = EXCLUDED.text
            """;

        jdbcClient.sql(sql)
            .param("id", id)
            .param("document_id", page.documentId())
            .param("page_no", page.pageNo())
            .param("text", page.text())
            .update();

        return findById(id).orElseThrow();
    }

    public void saveAll(List<DocumentPageEntity> pages) {
        for (DocumentPageEntity page : pages) {
            save(page);
        }
    }

    public Optional<DocumentPageEntity> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM document_pages WHERE id = :id")
            .param("id", id)
            .query(ROW_MAPPER)
            .optional();
    }

    public List<DocumentPageEntity> findByDocumentId(UUID documentId) {
        return jdbcClient.sql("SELECT * FROM document_pages WHERE document_id = :documentId ORDER BY page_no ASC")
            .param("documentId", documentId)
            .query(ROW_MAPPER)
            .list();
    }

    public Optional<DocumentPageEntity> findByDocumentIdAndPageNo(UUID documentId, int pageNo) {
        return jdbcClient.sql("SELECT * FROM document_pages WHERE document_id = :documentId AND page_no = :pageNo")
            .param("documentId", documentId)
            .param("pageNo", pageNo)
            .query(ROW_MAPPER)
            .optional();
    }
}
