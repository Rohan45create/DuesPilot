package com.duespilot.api.service;

import com.duespilot.api.domain.AnalysisRunEntity;
import com.duespilot.api.domain.AuditEventEntity;
import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.repository.AnalysisRunRepository;
import com.duespilot.api.repository.AuditEventRepository;
import com.duespilot.api.repository.CaseRepository;
import com.duespilot.api.repository.ConflictRepository;
import com.duespilot.api.repository.ContactRepository;
import com.duespilot.api.repository.DocumentPageRepository;
import com.duespilot.api.repository.DocumentRepository;
import com.duespilot.api.repository.EligibilityAssessmentRepository;
import com.duespilot.api.repository.ExtractedFieldRepository;
import com.duespilot.api.repository.InterestEstimateRepository;
import com.duespilot.api.repository.LedgerEntryRepository;
import com.duespilot.api.repository.ProposedActionRepository;
import com.duespilot.api.repository.ReconciliationResultRepository;
import com.duespilot.api.repository.SeedTemplateRepository;
import com.duespilot.api.web.dto.DeleteDemoDataResponse;
import com.duespilot.api.web.dto.DemoDataPreviewResponse;
import com.duespilot.api.web.dto.SeedDemoDataRequest;
import com.duespilot.api.web.dto.SeedDemoDataResponse;
import com.duespilot.api.web.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DemoDataService Unit & Isolation Tests")
class DemoDataServiceTest {

    @Mock private CaseRepository caseRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentPageRepository documentPageRepository;
    @Mock private ExtractedFieldRepository extractedFieldRepository;
    @Mock private AnalysisRunRepository analysisRunRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private ReconciliationResultRepository reconciliationResultRepository;
    @Mock private ConflictRepository conflictRepository;
    @Mock private EligibilityAssessmentRepository eligibilityAssessmentRepository;
    @Mock private InterestEstimateRepository interestEstimateRepository;
    @Mock private ProposedActionRepository proposedActionRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private AuditEventRepository auditEventRepository;
    @Mock private SeedTemplateRepository seedTemplateRepository;
    @Mock private CaseSeedService caseSeedService;

    private DemoDataService demoDataService;

    private final UUID workspaceA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID workspaceB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final CurrentUserService.UserContext userA = new CurrentUserService.UserContext(
        "user-a", "user.a@msme.example", false, workspaceA
    );
    private final CurrentUserService.UserContext userB = new CurrentUserService.UserContext(
        "user-b", "user.b@msme.example", false, workspaceB
    );

    @BeforeEach
    void setUp() {
        demoDataService = new DemoDataService(
            caseRepository,
            documentRepository,
            documentPageRepository,
            extractedFieldRepository,
            analysisRunRepository,
            ledgerEntryRepository,
            reconciliationResultRepository,
            conflictRepository,
            eligibilityAssessmentRepository,
            interestEstimateRepository,
            proposedActionRepository,
            contactRepository,
            auditEventRepository,
            seedTemplateRepository,
            caseSeedService
        );
        demoDataService.clearRateLimits();
    }

    @Test
    @DisplayName("getPreview returns accurate synthetic counts, storage, and seed status")
    void testGetPreview() {
        when(seedTemplateRepository.countTemplates()).thenReturn(12);
        when(seedTemplateRepository.countDocuments()).thenReturn(20);
        when(seedTemplateRepository.getTotalBlobSizeBytes()).thenReturn(24576L);
        when(caseRepository.hasDemoData(workspaceA)).thenReturn(false);

        DemoDataPreviewResponse preview = demoDataService.getPreview(userA);

        assertThat(preview.caseCount()).isEqualTo(12);
        assertThat(preview.documentCount()).isEqualTo(20);
        assertThat(preview.contactCount()).isEqualTo(12);
        assertThat(preview.approximateStorageKb()).isEqualTo(24);
        assertThat(preview.alreadySeeded()).isFalse();
    }

    @Test
    @DisplayName("seedDemoData rejects null or false acknowledgement with 400 ACKNOWLEDGEMENT_REQUIRED")
    void testSeedRejectsMissingAcknowledgement() {
        assertThatThrownBy(() -> demoDataService.seedDemoData(userA, new SeedDemoDataRequest(null)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "ACKNOWLEDGEMENT_REQUIRED")
            .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> demoDataService.seedDemoData(userA, new SeedDemoDataRequest(false)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "ACKNOWLEDGEMENT_REQUIRED")
            .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

        verify(caseRepository, never()).save(any());
    }

    @Test
    @DisplayName("seedDemoData returns 409 ALREADY_SEEDED if demo data already present in workspace")
    void testSeedRejectsAlreadySeeded() {
        when(caseRepository.hasDemoData(workspaceA)).thenReturn(true);

        assertThatThrownBy(() -> demoDataService.seedDemoData(userA, new SeedDemoDataRequest(true)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "ALREADY_SEEDED")
            .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);

        verify(caseRepository, never()).save(any());
    }

    @Test
    @DisplayName("seedDemoData copies seed cases with is_demo_data=true and writes audit event")
    void testSeedSuccessTransactional() {
        when(caseRepository.hasDemoData(workspaceA)).thenReturn(false);
        when(seedTemplateRepository.countTemplates()).thenReturn(12);

        UUID seedCaseId = UUID.randomUUID();
        CaseEntity seedCase = new CaseEntity(
            seedCaseId,
            DocumentEntity.DEMO_WORKSPACE_ID,
            "GEN-001",
            "Control Case 1",
            "Precision Works",
            "Apex Machinery",
            SupplierCategory.MICRO,
            LocalDate.of(2021, 6, 1),
            true,
            false,
            Instant.now(),
            1
        );
        when(caseRepository.findSeedCases()).thenReturn(List.of(seedCase));

        UUID seedDocId = UUID.randomUUID();
        DocumentEntity seedDoc = new DocumentEntity(
            seedDocId,
            seedCaseId,
            DocumentEntity.DEMO_WORKSPACE_ID,
            DocType.INVOICE,
            "INV-001.pdf",
            "text/plain",
            "sha256-test",
            "db_blob:sha256-test",
            1,
            LocalDate.of(2024, 2, 1),
            Instant.now(),
            120,
            null,
            "sha256-test"
        );
        when(documentRepository.findByCaseId(seedCaseId)).thenReturn(List.of(seedDoc));
        when(documentPageRepository.findByDocumentId(seedDocId)).thenReturn(List.of());
        when(analysisRunRepository.findLatestByCaseId(seedCaseId)).thenReturn(Optional.empty());

        SeedDemoDataResponse response = demoDataService.seedDemoData(userA, new SeedDemoDataRequest(true));

        assertThat(response.success()).isTrue();
        assertThat(response.caseCount()).isEqualTo(1);

        // Verify case created in workspaceA with isDemoData = true
        ArgumentCaptor<CaseEntity> caseCaptor = ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseRepository).save(caseCaptor.capture());
        CaseEntity savedCase = caseCaptor.getValue();
        assertThat(savedCase.workspaceId()).isEqualTo(workspaceA);
        assertThat(savedCase.isDemoData()).isTrue();
        assertThat(savedCase.code()).isEqualTo("GEN-001");

        // Verify contact created with fictional domain
        ArgumentCaptor<com.duespilot.api.domain.ContactEntity> contactCaptor =
            ArgumentCaptor.forClass(com.duespilot.api.domain.ContactEntity.class);
        verify(contactRepository).save(contactCaptor.capture());
        var savedContact = contactCaptor.getValue();
        assertThat(savedContact.workspaceId()).isEqualTo(workspaceA);
        assertThat(savedContact.email()).endsWith(".example");
        assertThat(savedContact.isDemoData()).isTrue();

        // Verify document shared blob
        ArgumentCaptor<DocumentEntity> docCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(docCaptor.capture());
        DocumentEntity savedDoc = docCaptor.getValue();
        assertThat(savedDoc.workspaceId()).isEqualTo(workspaceA);
        assertThat(savedDoc.blobSha256()).isEqualTo("sha256-test");

        // Verify audit event written
        ArgumentCaptor<AuditEventEntity> auditCaptor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("DEMO_DATA_SEEDED");
        assertThat(auditCaptor.getValue().actor()).isEqualTo("user-a");
    }

    @Test
    @DisplayName("deleteDemoData deletes only demo rows for workspace and writes audit event")
    void testDeleteDemoData() {
        when(caseRepository.deleteDemoDataByWorkspaceId(workspaceA)).thenReturn(12);
        when(contactRepository.deleteDemoContactsByWorkspaceId(workspaceA)).thenReturn(12);

        DeleteDemoDataResponse response = demoDataService.deleteDemoData(userA);

        assertThat(response.success()).isTrue();
        assertThat(response.deletedCount()).isEqualTo(12);

        verify(caseRepository).deleteDemoDataByWorkspaceId(workspaceA);
        verify(contactRepository).deleteDemoContactsByWorkspaceId(workspaceA);

        ArgumentCaptor<AuditEventEntity> auditCaptor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("DEMO_DATA_REMOVED");
        assertThat(auditCaptor.getValue().actor()).isEqualTo("user-a");
    }

    @Test
    @DisplayName("Cross-workspace isolation: Workspace A deletion never affects Workspace B")
    void testCrossWorkspaceIsolation() {
        when(caseRepository.deleteDemoDataByWorkspaceId(workspaceA)).thenReturn(12);

        demoDataService.deleteDemoData(userA);

        verify(caseRepository).deleteDemoDataByWorkspaceId(workspaceA);
        verify(caseRepository, never()).deleteDemoDataByWorkspaceId(workspaceB);
    }
}
