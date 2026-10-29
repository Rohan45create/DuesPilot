package com.duespilot.api.service;

import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.repository.AuditEventRepository;
import com.duespilot.api.repository.CaseRepository;
import com.duespilot.api.repository.DocumentPageRepository;
import com.duespilot.api.repository.DocumentRepository;
import com.duespilot.api.web.dto.CaseDetailResponse;
import com.duespilot.api.web.dto.CaseResponse;
import com.duespilot.api.web.dto.CreateCaseRequest;
import com.duespilot.api.web.dto.DocumentResponse;
import com.duespilot.api.web.error.CaseNotFoundException;
import com.duespilot.api.web.error.TooManyDocumentsException;
import com.duespilot.api.web.error.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaseService Business Logic & Rule R-53 Verification")
class CaseServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentPageRepository documentPageRepository;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private TextExtractionService textExtractionService;

    private CaseService caseService;

    @BeforeEach
    void setUp() {
        caseService = new CaseService(
            caseRepository,
            documentRepository,
            documentPageRepository,
            auditEventRepository,
            fileStorageService,
            textExtractionService
        );
    }

    @Test
    @DisplayName("createCase: auto-generates next case code when code is omitted")
    void createCaseAutoCode() {
        when(caseRepository.nextCaseCode(any())).thenReturn("DP-005");
        when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateCaseRequest request = new CreateCaseRequest(
            null, "Fabrication Invoice Unpaid", "Sharma Industries", "L&T Heavy", "SMALL", null
        );

        CaseResponse response = caseService.createCase(request);

        assertThat(response.code()).isEqualTo("DP-005");
        assertThat(response.title()).isEqualTo("Fabrication Invoice Unpaid");
        assertThat(response.supplierCategory()).isEqualTo("SMALL");
        verify(auditEventRepository).save(any());
    }

    @Test
    @DisplayName("createCase: throws ValidationException when title is blank")
    void createCaseBlankTitle() {
        CreateCaseRequest request = new CreateCaseRequest(
            "DP-001", "   ", "Supplier", "Buyer", "UNKNOWN", null
        );

        assertThatThrownBy(() -> caseService.createCase(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Title is required");
    }

    @Test
    @DisplayName("createCase: throws ValidationException when code already exists")
    void createCaseDuplicateCode() {
        when(caseRepository.findByCodeAndWorkspaceId(org.mockito.ArgumentMatchers.eq("DP-DUP"), any())).thenReturn(Optional.of(new CaseEntity(
            UUID.randomUUID(), "DP-DUP", "Existing", null, null, SupplierCategory.UNKNOWN, null, false, Instant.now(), 0
        )));

        CreateCaseRequest request = new CreateCaseRequest(
            "DP-DUP", "Another title", "Supplier", "Buyer", "UNKNOWN", null
        );

        assertThatThrownBy(() -> caseService.createCase(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("uploadDocuments: enforces 25 files per case limit per Rule R-53")
    void uploadDocumentsExceeds25FilesLimit() {
        UUID caseId = UUID.randomUUID();
        when(caseRepository.findByIdAndWorkspaceId(org.mockito.ArgumentMatchers.eq(caseId), any())).thenReturn(Optional.of(new CaseEntity(
            caseId, "DP-001", "Case", null, null, SupplierCategory.UNKNOWN, null, false, Instant.now(), 0
        )));
        when(documentRepository.countByCaseId(caseId)).thenReturn(24L);

        List<MockMultipartFile> files = List.of(
            new MockMultipartFile("files", "doc1.pdf", "application/pdf", "1".getBytes()),
            new MockMultipartFile("files", "doc2.pdf", "application/pdf", "2".getBytes())
        );

        assertThatThrownBy(() -> caseService.uploadDocuments(caseId, (List) files, "INVOICE", null))
            .isInstanceOf(TooManyDocumentsException.class)
            .hasMessageContaining("Case cannot exceed 25 documents");
    }

    @Test
    @DisplayName("uploadDocuments: stores file, extracts pages, persists entity and audit event")
    void uploadDocumentsSuccess() {
        UUID caseId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(caseRepository.findByIdAndWorkspaceId(org.mockito.ArgumentMatchers.eq(caseId), any())).thenReturn(Optional.of(new CaseEntity(
            caseId, "DP-001", "Case", null, null, SupplierCategory.UNKNOWN, null, false, Instant.now(), 0
        )));
        when(documentRepository.countByCaseId(caseId)).thenReturn(5L);

        when(fileStorageService.storeFile(any(), any(), any())).thenReturn(
            new FileStorageService.StoredFileInfo(
                docId, "clean_inv.pdf", "application/pdf", "hash123", "/path/clean_inv.pdf", 500
            )
        );
        when(textExtractionService.extractPages(any(), any(), any())).thenReturn(
            List.of(new TextExtractionService.ExtractedPage(1, "Invoice text"))
        );
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "inv.pdf", "application/pdf", "dummy".getBytes());

        List<DocumentResponse> result = caseService.uploadDocuments(caseId, List.of(file), "INVOICE", LocalDate.of(2024, 1, 1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(docId);
        assertThat(result.get(0).pageCount()).isEqualTo(1);
        verify(documentPageRepository).saveAll(anyList());
        verify(auditEventRepository).save(any());
    }

    @Test
    @DisplayName("getCase: returns case details and associated documents")
    void getCaseDetails() {
        UUID caseId = UUID.randomUUID();
        CaseEntity caseEntity = new CaseEntity(
            caseId, "DP-001", "Title", "Supplier", "Buyer", SupplierCategory.MICRO, null, false, Instant.now(), 0
        );
        when(caseRepository.findByIdAndWorkspaceId(org.mockito.ArgumentMatchers.eq(caseId), any())).thenReturn(Optional.of(caseEntity));
        when(documentRepository.findByCaseId(caseId)).thenReturn(List.of(
            new DocumentEntity(UUID.randomUUID(), caseId, DocType.PO, "po.pdf", "application/pdf", "sha", "/path", 1, null, Instant.now())
        ));

        CaseDetailResponse response = caseService.getCase(caseId);

        assertThat(response.id()).isEqualTo(caseId);
        assertThat(response.documents()).hasSize(1);
        assertThat(response.documents().get(0).docType()).isEqualTo("PO");
    }
}
