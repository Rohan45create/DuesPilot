package com.duespilot.api.service;

import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.repository.CaseRepository;
import com.duespilot.api.repository.DocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaseRetentionService Tests (Section 11, 16: Purge after 7 days, retain seed and demo cases)")
class CaseRetentionServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private com.duespilot.api.repository.FileBlobRepository fileBlobRepository;

    @InjectMocks
    private CaseRetentionService retentionService;

    @Test
    @DisplayName("Purges uploaded non-seed cases older than 7 days and deletes stored files")
    void testPurgeExpiredUploadedCases() {
        UUID oldCaseId = UUID.randomUUID();
        CaseEntity oldCase = new CaseEntity(
            oldCaseId, "DP-099", "Expired Uploaded Case", "Small Supplier", "Big Buyer",
            SupplierCategory.SMALL, LocalDate.of(2021, 1, 1), false, // non-seed!
            Instant.now().minus(Duration.ofDays(8)), 1
        );

        DocumentEntity doc = new DocumentEntity(
            UUID.randomUUID(), oldCaseId, DocType.INVOICE, "old_invoice.pdf", "application/pdf",
            "sha256old", "./data/uploads/old_invoice.pdf", 1, LocalDate.of(2024, 1, 1), Instant.now()
        );

        when(caseRepository.findNonSeedOlderThan(any(Instant.class))).thenReturn(List.of(oldCase));
        when(documentRepository.findByCaseId(oldCaseId)).thenReturn(List.of(doc));
        when(caseRepository.deleteById(oldCaseId)).thenReturn(true);

        int purged = retentionService.purgeExpiredCasesOlderThan(Duration.ofDays(7));

        assertThat(purged).isEqualTo(1);
        verify(fileStorageService).deleteFile("./data/uploads/old_invoice.pdf");
        verify(caseRepository).deleteById(oldCaseId);
    }

    @Test
    @DisplayName("Preserves seed cases unconditionally (is_seed = true)")
    void testSeedCasesAreNeverPurged() {
        // findNonSeedOlderThan query specifically filters `WHERE is_seed = false`
        when(caseRepository.findNonSeedOlderThan(any(Instant.class))).thenReturn(List.of());

        int purged = retentionService.purgeExpiredCasesOlderThan(Duration.ofDays(7));

        assertThat(purged).isEqualTo(0);
        verify(caseRepository, never()).deleteById(any());
        verify(fileStorageService, never()).deleteFile(any());
    }

    @Test
    @DisplayName("Safeguard strictly prevents deleting seed cases even if returned by repository")
    void testSafeguardNeverDeletesSeedCases() {
        UUID seedCaseId = UUID.randomUUID();
        CaseEntity seedCase = new CaseEntity(
            seedCaseId, "GEN-001", "Seed Case 1", "Seed Supplier", "Seed Buyer",
            SupplierCategory.MICRO, LocalDate.of(2020, 1, 1), true, // is_seed = true!
            Instant.now().minus(Duration.ofDays(30)), 1
        );

        when(caseRepository.findNonSeedOlderThan(any(Instant.class))).thenReturn(List.of(seedCase));

        int purged = retentionService.purgeExpiredCasesOlderThan(Duration.ofDays(7));

        assertThat(purged).isEqualTo(0);
        verify(caseRepository, never()).deleteById(seedCaseId);
        verify(fileStorageService, never()).deleteFile(any());
    }

    @Test
    @DisplayName("Safeguard strictly prevents deleting demo cases (code starting with DEMO/GEN/DP-DEMO or title containing demo)")
    void testSafeguardNeverDeletesDemoCases() {
        UUID demoCase1Id = UUID.randomUUID();
        CaseEntity demoCase1 = new CaseEntity(
            demoCase1Id, "DP-DEMO-01", "Interactive Demo Replay", "Supplier", "Buyer",
            SupplierCategory.MICRO, LocalDate.of(2021, 5, 1), false,
            Instant.now().minus(Duration.ofDays(20)), 1
        );

        UUID demoCase2Id = UUID.randomUUID();
        CaseEntity demoCase2 = new CaseEntity(
            demoCase2Id, "GEN-029", "Statutory Refuse Benchmark", "Supplier", "Buyer",
            SupplierCategory.MEDIUM, LocalDate.of(2021, 5, 1), false,
            Instant.now().minus(Duration.ofDays(15)), 1
        );

        when(caseRepository.findNonSeedOlderThan(any(Instant.class))).thenReturn(List.of(demoCase1, demoCase2));

        int purged = retentionService.purgeExpiredCasesOlderThan(Duration.ofDays(7));

        assertThat(purged).isEqualTo(0);
        verify(caseRepository, never()).deleteById(demoCase1Id);
        verify(caseRepository, never()).deleteById(demoCase2Id);
        verify(fileStorageService, never()).deleteFile(any());
    }

    @Test
    @DisplayName("Mixed batch: only non-seed non-demo cases are purged, protecting seed and demo data")
    void testMixedBatchPurgeProtection() {
        UUID seedId = UUID.randomUUID();
        CaseEntity seedCase = new CaseEntity(
            seedId, "DP-SEED-01", "Protected Seed", "Sup", "Buy",
            SupplierCategory.MICRO, null, true, Instant.now().minus(Duration.ofDays(20)), 1
        );

        UUID demoId = UUID.randomUUID();
        CaseEntity demoCase = new CaseEntity(
            demoId, "DP-DEMO-99", "Protected Demo", "Sup", "Buy",
            SupplierCategory.SMALL, null, false, Instant.now().minus(Duration.ofDays(20)), 1
        );

        UUID regularUploadId = UUID.randomUUID();
        CaseEntity uploadCase = new CaseEntity(
            regularUploadId, "DP-888", "Regular Expired Upload", "Sup", "Buy",
            SupplierCategory.MICRO, null, false, Instant.now().minus(Duration.ofDays(20)), 1
        );

        when(caseRepository.findNonSeedOlderThan(any(Instant.class)))
            .thenReturn(List.of(seedCase, demoCase, uploadCase));
        when(documentRepository.findByCaseId(regularUploadId)).thenReturn(List.of());
        when(caseRepository.deleteById(regularUploadId)).thenReturn(true);

        int purged = retentionService.purgeExpiredCasesOlderThan(Duration.ofDays(7));

        assertThat(purged).isEqualTo(1);
        verify(caseRepository, never()).deleteById(seedId);
        verify(caseRepository, never()).deleteById(demoId);
        verify(caseRepository).deleteById(regularUploadId);
    }

    @Test
    @DisplayName("Purges orphaned content-addressed blobs unreferenced by any documents or benchmarks")
    void testPurgeOrphanedBlobs() {
        when(fileBlobRepository.deleteOrphanedBlobs()).thenReturn(5);

        int deleted = retentionService.purgeOrphanedBlobs();

        assertThat(deleted).isEqualTo(5);
        verify(fileBlobRepository).deleteOrphanedBlobs();
    }
}
