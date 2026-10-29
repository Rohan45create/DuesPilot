package com.duespilot.api.service;

import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.repository.CaseRepository;
import com.duespilot.api.repository.DocumentRepository;
import com.duespilot.api.repository.FileBlobRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Service to purge uploaded non-seed cases after 7 days and delete orphaned blobs from app.file_blobs
 * per Architecture section 11 & 16.
 */
@Service
public class CaseRetentionService {

    private static final Logger log = LoggerFactory.getLogger(CaseRetentionService.class);

    private final CaseRepository caseRepository;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final @Nullable FileBlobRepository fileBlobRepository;

    @Autowired
    public CaseRetentionService(
            CaseRepository caseRepository,
            DocumentRepository documentRepository,
            FileStorageService fileStorageService,
            @Nullable FileBlobRepository fileBlobRepository) {
        this.caseRepository = caseRepository;
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.fileBlobRepository = fileBlobRepository;
    }

    public CaseRetentionService(
            CaseRepository caseRepository,
            DocumentRepository documentRepository,
            FileStorageService fileStorageService) {
        this(caseRepository, documentRepository, fileStorageService, null);
    }

    /**
     * Daily scheduled retention purge at configured cron (default 2 AM).
     */
    @Scheduled(cron = "${duespilot.retention.cron:0 0 2 * * *}")
    public int purgeExpiredCases() {
        return purgeExpiredCasesOlderThan(Duration.ofDays(7));
    }

    /**
     * Purges uploaded cases older than the specified retention period,
     * and prunes any unreferenced orphaned blobs from app.file_blobs.
     * Seed cases (is_seed = true) are strictly preserved forever.
     */
    @Transactional
    public int purgeExpiredCasesOlderThan(Duration retentionPeriod) {
        Instant cutoff = Instant.now().minus(retentionPeriod);
        List<CaseEntity> expiredCases = caseRepository.findNonSeedOlderThan(cutoff);

        int purgedCount = 0;
        if (!expiredCases.isEmpty()) {
            log.info("Found {} non-seed cases older than {} to purge (retention: {})", expiredCases.size(), cutoff, retentionPeriod);

            for (CaseEntity c : expiredCases) {
                // Invariant: Never delete seed or demo cases
                if (c.isSeed() || isDemoCase(c)) {
                    log.warn("Purge safeguard: skipping protected seed/demo case {}", c.code());
                    continue;
                }

                // Delete associated documents
                List<DocumentEntity> docs = documentRepository.findByCaseId(c.id());
                for (DocumentEntity doc : docs) {
                    fileStorageService.deleteFile(doc.storagePath());
                }

                // Delete case from database (FK cascades remove child records)
                boolean deleted = caseRepository.deleteById(c.id());
                if (deleted) {
                    purgedCount++;
                }
            }

            log.info("Successfully purged {} expired uploaded cases", purgedCount);
        }

        // Delete orphaned blobs no longer referenced anywhere
        purgeOrphanedBlobs();

        return purgedCount;
    }

    public int purgeOrphanedBlobs() {
        if (fileBlobRepository != null) {
            int deletedBlobs = fileBlobRepository.deleteOrphanedBlobs();
            if (deletedBlobs > 0) {
                log.info("Purged {} orphaned content-addressed blobs from app.file_blobs", deletedBlobs);
            }
            return deletedBlobs;
        }
        return 0;
    }

    public static boolean isDemoCase(CaseEntity c) {
        if (c.isSeed()) {
            return true;
        }
        if (c.code() != null) {
            String code = c.code().toUpperCase(java.util.Locale.ROOT);
            if (code.startsWith("DEMO") || code.startsWith("DP-DEMO") || code.startsWith("GEN-") || code.startsWith("DP-SEED")) {
                return true;
            }
        }
        if (c.title() != null && c.title().toLowerCase(java.util.Locale.ROOT).contains("demo")) {
            return true;
        }
        return false;
    }
}
