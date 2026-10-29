package com.duespilot.api.repository;

import com.duespilot.api.domain.AuditEventEntity;
import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.DocumentPageEntity;
import com.duespilot.api.domain.SupplierCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Flyway Migrations & JdbcClient Repositories Integration Tests with Testcontainers PostgreSQL")
class CaseRepositoryTestcontainersTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentPageRepository documentPageRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    @DisplayName("Verify CaseRepository save, findById, findByCode, count, and nextCaseCode")
    void testCaseRepositoryOperations() {
        String code = "DP-TEST-" + UUID.randomUUID().toString().substring(0, 8);
        CaseEntity newCase = new CaseEntity(
            UUID.randomUUID(),
            code,
            "Reconciliation Test Case",
            "Shree Krishna Precision",
            "Tata Motors Ltd",
            SupplierCategory.MICRO,
            LocalDate.of(2021, 8, 14),
            false,
            Instant.now(),
            0
        );

        CaseEntity saved = caseRepository.save(newCase);
        assertThat(saved.id()).isNotNull();
        assertThat(saved.code()).isEqualTo(code);
        assertThat(saved.title()).isEqualTo("Reconciliation Test Case");
        assertThat(saved.supplierCategory()).isEqualTo(SupplierCategory.MICRO);

        Optional<CaseEntity> foundById = caseRepository.findById(saved.id());
        assertThat(foundById).isPresent();
        assertThat(foundById.get().supplierName()).isEqualTo("Shree Krishna Precision");

        Optional<CaseEntity> foundByCode = caseRepository.findByCode(code);
        assertThat(foundByCode).isPresent();

        long count = caseRepository.count();
        assertThat(count).isGreaterThanOrEqualTo(1L);

        String nextCode = caseRepository.nextCaseCode();
        assertThat(nextCode).startsWith("DP-");
    }

    @Test
    @DisplayName("Verify DocumentRepository and DocumentPageRepository persistence and cascade")
    void testDocumentAndPageRepositories() {
        CaseEntity caseEntity = caseRepository.save(new CaseEntity(
            UUID.randomUUID(),
            "DP-DOC-" + UUID.randomUUID().toString().substring(0, 8),
            "Document Storage Case",
            "Supplier X",
            "Buyer Y",
            SupplierCategory.SMALL,
            null,
            false,
            Instant.now(),
            0
        ));

        DocumentEntity doc = new DocumentEntity(
            UUID.randomUUID(),
            caseEntity.id(),
            DocType.INVOICE,
            "INV-2024-001.pdf",
            "application/pdf",
            "4a5a4e39b49e5d39f4088a5316498ea322e720875e533c66f507b9da4e101ff6",
            "/storage/INV-2024-001.pdf",
            2,
            LocalDate.of(2024, 2, 1),
            Instant.now()
        );

        DocumentEntity savedDoc = documentRepository.save(doc);
        assertThat(savedDoc.id()).isEqualTo(doc.id());

        List<DocumentEntity> caseDocs = documentRepository.findByCaseId(caseEntity.id());
        assertThat(caseDocs).hasSize(1);
        assertThat(caseDocs.get(0).fileName()).isEqualTo("INV-2024-001.pdf");
        assertThat(documentRepository.countByCaseId(caseEntity.id())).isEqualTo(1L);

        // Save pages
        DocumentPageEntity page1 = new DocumentPageEntity(UUID.randomUUID(), savedDoc.id(), 1, "Page 1: Invoiced ₹1,00,000");
        DocumentPageEntity page2 = new DocumentPageEntity(UUID.randomUUID(), savedDoc.id(), 2, "Page 2: Terms 45 days");
        documentPageRepository.saveAll(List.of(page1, page2));

        List<DocumentPageEntity> pages = documentPageRepository.findByDocumentId(savedDoc.id());
        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).pageNo()).isEqualTo(1);
        assertThat(pages.get(0).text()).contains("Invoiced ₹1,00,000");

        Optional<DocumentPageEntity> singlePage = documentPageRepository.findByDocumentIdAndPageNo(savedDoc.id(), 2);
        assertThat(singlePage).isPresent();
        assertThat(singlePage.get().text()).contains("Terms 45 days");
    }

    @Test
    @DisplayName("Verify AuditEventRepository JSONB persistence and case query")
    void testAuditEventRepository() {
        UUID caseId = UUID.randomUUID();
        String jsonPayload = "{\"action\":\"CREATED\",\"initiator\":\"system_admin\",\"detail\":{\"amount\":50000}}";

        AuditEventEntity event = new AuditEventEntity(
            UUID.randomUUID(),
            caseId,
            "TEST_RUNNER",
            "CASE_AUDIT",
            jsonPayload,
            Instant.now()
        );

        AuditEventEntity savedEvent = auditEventRepository.save(event);
        assertThat(savedEvent.id()).isEqualTo(event.id());
        assertThat(savedEvent.eventType()).isEqualTo("CASE_AUDIT");
        assertThat(savedEvent.payload()).contains("initiator");

        List<AuditEventEntity> events = auditEventRepository.findByCaseId(caseId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).actor()).isEqualTo("TEST_RUNNER");
    }
}
