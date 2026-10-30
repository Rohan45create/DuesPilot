package com.duespilot.api.service;

import com.duespilot.api.domain.AnalysisRunEntity;
import com.duespilot.api.domain.AuditEventEntity;
import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.ConflictEntity;
import com.duespilot.api.domain.ContactEntity;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.DocumentPageEntity;
import com.duespilot.api.domain.EligibilityAssessmentEntity;
import com.duespilot.api.domain.ExtractedFieldEntity;
import com.duespilot.api.domain.InterestEstimateEntity;
import com.duespilot.api.domain.LedgerEntryEntity;
import com.duespilot.api.domain.ProposedActionEntity;
import com.duespilot.api.domain.ReconciliationResultEntity;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DemoDataService {

    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);

    private final CaseRepository caseRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final ExtractedFieldRepository extractedFieldRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final ConflictRepository conflictRepository;
    private final EligibilityAssessmentRepository eligibilityAssessmentRepository;
    private final InterestEstimateRepository interestEstimateRepository;
    private final ProposedActionRepository proposedActionRepository;
    private final ContactRepository contactRepository;
    private final AuditEventRepository auditEventRepository;
    private final SeedTemplateRepository seedTemplateRepository;
    private final CaseSeedService caseSeedService;

    // In-memory rate-limiter per workspace (minute window, max 5 seed operations per minute)
    private static class RateWindow {
        final long minuteTimestamp;
        final AtomicInteger count;

        RateWindow(long minuteTimestamp, int initialCount) {
            this.minuteTimestamp = minuteTimestamp;
            this.count = new AtomicInteger(initialCount);
        }
    }

    private final Map<UUID, RateWindow> seedRateLimitMap = new ConcurrentHashMap<>();

    public DemoDataService(
            CaseRepository caseRepository,
            DocumentRepository documentRepository,
            DocumentPageRepository documentPageRepository,
            ExtractedFieldRepository extractedFieldRepository,
            AnalysisRunRepository analysisRunRepository,
            LedgerEntryRepository ledgerEntryRepository,
            ReconciliationResultRepository reconciliationResultRepository,
            ConflictRepository conflictRepository,
            EligibilityAssessmentRepository eligibilityAssessmentRepository,
            InterestEstimateRepository interestEstimateRepository,
            ProposedActionRepository proposedActionRepository,
            ContactRepository contactRepository,
            AuditEventRepository auditEventRepository,
            SeedTemplateRepository seedTemplateRepository,
            CaseSeedService caseSeedService) {
        this.caseRepository = caseRepository;
        this.documentRepository = documentRepository;
        this.documentPageRepository = documentPageRepository;
        this.extractedFieldRepository = extractedFieldRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.reconciliationResultRepository = reconciliationResultRepository;
        this.conflictRepository = conflictRepository;
        this.eligibilityAssessmentRepository = eligibilityAssessmentRepository;
        this.interestEstimateRepository = interestEstimateRepository;
        this.proposedActionRepository = proposedActionRepository;
        this.contactRepository = contactRepository;
        this.auditEventRepository = auditEventRepository;
        this.seedTemplateRepository = seedTemplateRepository;
        this.caseSeedService = caseSeedService;
    }

    public DemoDataPreviewResponse getPreview(CurrentUserService.UserContext ctx) {
        UUID workspaceId = ctx.workspaceId();
        ensureSeedTemplatesExist();

        int caseCount = seedTemplateRepository.countTemplates();
        if (caseCount == 0) {
            caseCount = 12;
        }

        int docCount = seedTemplateRepository.countDocuments();
        if (docCount == 0) {
            docCount = 20;
        }

        int contactCount = caseCount;
        long blobBytes = seedTemplateRepository.getTotalBlobSizeBytes();
        long approximateStorageKb = Math.max(24L, (blobBytes + 1023) / 1024);

        boolean alreadySeeded = caseRepository.hasDemoData(workspaceId);

        return new DemoDataPreviewResponse(
            caseCount,
            docCount,
            contactCount,
            approximateStorageKb,
            alreadySeeded
        );
    }

    @Transactional
    public void seedWorkspaceForDemoUser(UUID workspaceId, String userId) {
        if (caseRepository.hasDemoData(workspaceId)) {
            return;
        }
        performWorkspaceSeeding(workspaceId, userId);
    }

    @Transactional
    public SeedDemoDataResponse seedDemoData(CurrentUserService.UserContext ctx, SeedDemoDataRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.acknowledged())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "ACKNOWLEDGEMENT_REQUIRED",
                "Explicit consent is required before demo data can be seeded (Rule R-84)"
            );
        }

        UUID workspaceId = ctx.workspaceId();

        if (caseRepository.hasDemoData(workspaceId)) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "ALREADY_SEEDED",
                "Demo data has already been seeded in this workspace"
            );
        }

        enforceRateLimit(workspaceId);

        int count = performWorkspaceSeeding(workspaceId, ctx.userId());
        return new SeedDemoDataResponse(
            true,
            count,
            "Successfully added " + count + " synthetic demo cases to your workspace."
        );
    }

    @Transactional
    public int performWorkspaceSeeding(UUID workspaceId, String userId) {
        ensureSeedTemplatesExist();

        List<CaseEntity> seedCases = caseRepository.findSeedCases();
        if (seedCases.isEmpty()) {
            caseSeedService.seedAll();
            seedCases = caseRepository.findSeedCases();
        }

        for (CaseEntity sc : seedCases) {
            UUID newCaseId = UUID.randomUUID();
            CaseEntity newCase = new CaseEntity(
                newCaseId,
                workspaceId,
                sc.code(),
                sc.title(),
                sc.supplierName(),
                sc.buyerName(),
                sc.supplierCategory(),
                sc.udyamRegisteredOn(),
                false, // not the demo system seed
                true,  // is_demo_data = true
                Instant.now(),
                1
            );
            caseRepository.save(newCase);

            // Create fictional contact for this synthetic case (Rule R-74)
            String buyerName = sc.buyerName() != null ? sc.buyerName() : "Buyer Corp";
            String slug = sc.code().toLowerCase().replace("-", "");
            ContactEntity contact = new ContactEntity(
                UUID.randomUUID(),
                workspaceId,
                newCaseId,
                buyerName,
                "accounts@" + slug + ".fictional-corp.example",
                "+91980000" + String.format("%04d", Math.abs(sc.code().hashCode() % 10000)),
                "+91980000" + String.format("%04d", Math.abs(sc.code().hashCode() % 10000)),
                true,
                "DEMO_SEED",
                Instant.now(),
                true,
                "DEMO_SEED",
                Instant.now(),
                null,
                true, // is_demo_data = true
                Instant.now(),
                Instant.now()
            );
            contactRepository.save(contact);

            // Copy documents referencing shared blobs (zero blob duplication)
            List<DocumentEntity> docs = documentRepository.findByCaseId(sc.id());
            Map<UUID, UUID> oldToNewDocId = new HashMap<>();

            for (DocumentEntity d : docs) {
                UUID newDocId = UUID.randomUUID();
                oldToNewDocId.put(d.id(), newDocId);

                DocumentEntity newDoc = new DocumentEntity(
                    newDocId,
                    newCaseId,
                    workspaceId,
                    d.docType(),
                    d.fileName(),
                    d.mimeType(),
                    d.sha256(),
                    d.storagePath(),
                    d.pageCount(),
                    d.docDate(),
                    Instant.now(),
                    d.fileSizeBytes(),
                    d.contentBytes(),
                    d.blobSha256()
                );
                documentRepository.save(newDoc);

                List<DocumentPageEntity> pages = documentPageRepository.findByDocumentId(d.id());
                for (DocumentPageEntity page : pages) {
                    documentPageRepository.save(new DocumentPageEntity(
                        UUID.randomUUID(), newDocId, page.pageNo(), page.text()
                    ));
                }
            }

            // Copy analysis runs and pre-computed results
            Optional<AnalysisRunEntity> runOpt = analysisRunRepository.findLatestByCaseId(sc.id());
            if (runOpt.isPresent()) {
                AnalysisRunEntity oldRun = runOpt.get();
                UUID newRunId = UUID.randomUUID();
                AnalysisRunEntity newRun = new AnalysisRunEntity(
                    newRunId,
                    newCaseId,
                    oldRun.status(),
                    oldRun.routerPolicy(),
                    oldRun.totalCostMicros(),
                    oldRun.startedAt(),
                    oldRun.finishedAt()
                );
                analysisRunRepository.save(newRun);

                // Copy extracted fields
                Map<UUID, UUID> oldToNewFieldId = new HashMap<>();
                List<ExtractedFieldEntity> fields = extractedFieldRepository.findByRunId(oldRun.id());
                for (ExtractedFieldEntity f : fields) {
                    UUID newFieldId = UUID.randomUUID();
                    oldToNewFieldId.put(f.id(), newFieldId);
                    UUID mappedDocId = oldToNewDocId.getOrDefault(f.documentId(), f.documentId());
                    extractedFieldRepository.save(new ExtractedFieldEntity(
                        newFieldId, mappedDocId, newRunId, f.fieldName(), f.valueText(),
                        f.valuePaise(), f.valueDate(), f.status(), f.pageNo(), f.charStart(),
                        f.charEnd(), f.quote(), f.model()
                    ));
                }

                // Copy ledger entries
                List<LedgerEntryEntity> ledgers = ledgerEntryRepository.findByRunId(oldRun.id());
                for (LedgerEntryEntity l : ledgers) {
                    List<UUID> newFieldIds = l.sourceFieldIds().stream()
                        .map(id -> oldToNewFieldId.getOrDefault(id, id))
                        .toList();
                    ledgerEntryRepository.save(new LedgerEntryEntity(
                        UUID.randomUUID(), newRunId, l.kind(), l.ref(), l.amountPaise(),
                        l.entryDate(), l.status(), newFieldIds
                    ));
                }

                // Copy reconciliation results
                reconciliationResultRepository.findByRunId(oldRun.id()).ifPresent(r -> {
                    reconciliationResultRepository.save(new ReconciliationResultEntity(
                        newRunId, r.invoicedPaise(), r.confirmedPaidPaise(), r.confirmedCreditPaise(),
                        r.disputedPaise(), r.unverifiedPaise(), r.balancePaise(), r.balanceStatus()
                    ));
                });

                // Copy conflicts
                List<ConflictEntity> conflicts = conflictRepository.findByRunId(oldRun.id());
                for (ConflictEntity c : conflicts) {
                    List<UUID> newEvidence = c.evidenceFieldIds().stream()
                        .map(id -> oldToNewFieldId.getOrDefault(id, id))
                        .toList();
                    conflictRepository.save(new ConflictEntity(
                        UUID.randomUUID(), newRunId, c.type(), c.detectedBy(), c.severity(),
                        c.summary(), c.amountPaise(), newEvidence, c.status()
                    ));
                }

                // Copy eligibility assessments
                List<EligibilityAssessmentEntity> assessments = eligibilityAssessmentRepository.findByRunId(oldRun.id());
                for (EligibilityAssessmentEntity a : assessments) {
                    eligibilityAssessmentRepository.save(new EligibilityAssessmentEntity(
                        UUID.randomUUID(), newRunId, a.gate(), a.status(), a.reasonsJson()
                    ));
                }

                // Copy interest estimates
                interestEstimateRepository.findByRunId(oldRun.id()).ifPresent(ie -> {
                    interestEstimateRepository.save(new InterestEstimateEntity(
                        UUID.randomUUID(), newRunId, ie.principalPaise(), ie.rateRefId(),
                        ie.multiplier(), ie.fromDate(), ie.toDate(), ie.interestPaise(),
                        ie.convention(), ie.status()
                    ));
                });

                // Copy proposed actions
                List<ProposedActionEntity> actions = proposedActionRepository.findByRunId(oldRun.id());
                for (ProposedActionEntity pa : actions) {
                    List<UUID> newEvidence = pa.evidenceFieldIds().stream()
                        .map(id -> oldToNewFieldId.getOrDefault(id, id))
                        .toList();
                    proposedActionRepository.save(new ProposedActionEntity(
                        UUID.randomUUID(), newRunId, pa.actionType(), pa.language(), pa.draftText(),
                        newEvidence, pa.status(), pa.decidedBy(), pa.decidedAt()
                    ));
                }
            }
        }

        // Record audit event
        auditEventRepository.save(new AuditEventEntity(
            UUID.randomUUID(),
            null,
            userId,
            "DEMO_DATA_SEEDED",
            String.format("{\"workspaceId\":\"%s\",\"casesSeeded\":%d}", workspaceId, seedCases.size()),
            Instant.now()
        ));

        log.info("Workspace {} successfully seeded with {} demo cases", workspaceId, seedCases.size());
        return seedCases.size();
    }

    @Transactional
    public DeleteDemoDataResponse deleteDemoData(CurrentUserService.UserContext ctx) {
        UUID workspaceId = ctx.workspaceId();

        // Deletes only rows marked is_demo_data = true (cascades to documents, runs, etc.)
        int deletedCases = caseRepository.deleteDemoDataByWorkspaceId(workspaceId);
        contactRepository.deleteDemoContactsByWorkspaceId(workspaceId);

        auditEventRepository.save(new AuditEventEntity(
            UUID.randomUUID(),
            null,
            ctx.userId(),
            "DEMO_DATA_REMOVED",
            String.format("{\"workspaceId\":\"%s\",\"casesRemoved\":%d}", workspaceId, deletedCases),
            Instant.now()
        ));

        log.info("Workspace {} removed {} demo cases", workspaceId, deletedCases);
        return new DeleteDemoDataResponse(
            true,
            deletedCases,
            "Successfully removed " + deletedCases + " demo cases from your workspace."
        );
    }

    private void enforceRateLimit(UUID workspaceId) {
        long currentMinute = System.currentTimeMillis() / 60000;
        RateWindow window = seedRateLimitMap.compute(workspaceId, (id, current) -> {
            if (current == null || current.minuteTimestamp != currentMinute) {
                return new RateWindow(currentMinute, 1);
            }
            current.count.incrementAndGet();
            return current;
        });

        if (window.count.get() > 5) {
            throw new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED",
                "Rate limit exceeded: please wait a minute before seeding demo data again"
            );
        }
    }

    private void ensureSeedTemplatesExist() {
        if (seedTemplateRepository.countTemplates() < 12) {
            caseSeedService.seedAll();
        }
    }

    public void clearRateLimits() {
        seedRateLimitMap.clear();
    }
}
