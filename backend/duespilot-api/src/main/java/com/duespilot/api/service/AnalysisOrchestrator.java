package com.duespilot.api.service;

import com.duespilot.api.domain.AnalysisRunEntity;
import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.ConflictEntity;
import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.DocumentPageEntity;
import com.duespilot.api.domain.EligibilityAssessmentEntity;
import com.duespilot.api.domain.ExtractedFieldEntity;
import com.duespilot.api.domain.InterestEstimateEntity;
import com.duespilot.api.domain.ProposedActionEntity;
import com.duespilot.api.domain.RateReferenceEntity;
import com.duespilot.api.domain.ReconciliationResultEntity;
import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.llm.DraftActionPolicy;
import com.duespilot.api.llm.ExtractionPolicy;
import com.duespilot.api.llm.LlmClient;
import com.duespilot.api.llm.LlmProperties;
import com.duespilot.api.llm.ModelRouter;
import com.duespilot.api.repository.AnalysisRunRepository;
import com.duespilot.api.repository.CaseRepository;
import com.duespilot.api.repository.ConflictRepository;
import com.duespilot.api.repository.DocumentPageRepository;
import com.duespilot.api.repository.DocumentRepository;
import com.duespilot.api.repository.EligibilityAssessmentRepository;
import com.duespilot.api.repository.ExtractedFieldRepository;
import com.duespilot.api.repository.InterestEstimateRepository;
import com.duespilot.api.repository.LedgerEntryRepository;
import com.duespilot.api.repository.LlmCallRepository;
import com.duespilot.api.repository.ProposedActionRepository;
import com.duespilot.api.repository.RateReferenceRepository;
import com.duespilot.api.repository.ReconciliationResultRepository;
import com.duespilot.api.web.error.ApiException;
import com.duespilot.core.conflict.Conflict;
import com.duespilot.core.conflict.ConflictContext;
import com.duespilot.core.conflict.ConflictDetector;
import com.duespilot.core.conflict.ConflictEvidence;
import com.duespilot.core.gate.CaseEligibility;
import com.duespilot.core.gate.EnterpriseCategory;
import com.duespilot.core.gate.GateEvaluator;
import com.duespilot.core.gate.GateInputs;
import com.duespilot.core.gate.GateResult;
import com.duespilot.core.gate.GateStatus;
import com.duespilot.core.interest.InterestEngine;
import com.duespilot.core.interest.InterestResult;
import com.duespilot.core.model.CaseReconciliation;
import com.duespilot.core.model.DocumentSource;
import com.duespilot.core.model.InvoiceReconciliation;
import com.duespilot.core.model.Known;
import com.duespilot.core.model.LedgerEntry;
import com.duespilot.core.model.LedgerEntryType;
import com.duespilot.core.model.Paise;
import com.duespilot.core.model.VerificationStatus;
import com.duespilot.core.rate.RateCandidate;
import com.duespilot.core.reconcile.Reconciler;
import com.duespilot.core.statutory.StatutoryParameters;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Service
public class AnalysisOrchestrator implements AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestrator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CaseRepository caseRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final ExtractedFieldRepository extractedFieldRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final ConflictRepository conflictRepository;
    private final EligibilityAssessmentRepository eligibilityAssessmentRepository;
    private final RateReferenceRepository rateReferenceRepository;
    private final InterestEstimateRepository interestEstimateRepository;
    private final ProposedActionRepository proposedActionRepository;
    private final LlmCallRepository llmCallRepository;
    private final ModelRouter modelRouter;
    private final ActionPlanner actionPlanner;
    private final AnalysisEventService eventService;
    private final LlmProperties llmProperties;
    private final com.duespilot.api.config.DuesPilotProperties duesPilotProperties;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.duespilot.api.repository.WorkspaceRepository workspaceRepository;

    private final ExecutorService analysisExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AnalysisOrchestrator(
            CaseRepository caseRepository,
            DocumentRepository documentRepository,
            DocumentPageRepository documentPageRepository,
            ExtractedFieldRepository extractedFieldRepository,
            AnalysisRunRepository analysisRunRepository,
            LedgerEntryRepository ledgerEntryRepository,
            ReconciliationResultRepository reconciliationResultRepository,
            ConflictRepository conflictRepository,
            EligibilityAssessmentRepository eligibilityAssessmentRepository,
            RateReferenceRepository rateReferenceRepository,
            InterestEstimateRepository interestEstimateRepository,
            ProposedActionRepository proposedActionRepository,
            LlmCallRepository llmCallRepository,
            ModelRouter modelRouter,
            ActionPlanner actionPlanner,
            AnalysisEventService eventService,
            LlmProperties llmProperties) {
        this(caseRepository, documentRepository, documentPageRepository, extractedFieldRepository,
             analysisRunRepository, ledgerEntryRepository, reconciliationResultRepository, conflictRepository,
             eligibilityAssessmentRepository, rateReferenceRepository, interestEstimateRepository,
             proposedActionRepository, llmCallRepository, modelRouter, actionPlanner, eventService,
             llmProperties, new com.duespilot.api.config.DuesPilotProperties());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AnalysisOrchestrator(
            CaseRepository caseRepository,
            DocumentRepository documentRepository,
            DocumentPageRepository documentPageRepository,
            ExtractedFieldRepository extractedFieldRepository,
            AnalysisRunRepository analysisRunRepository,
            LedgerEntryRepository ledgerEntryRepository,
            ReconciliationResultRepository reconciliationResultRepository,
            ConflictRepository conflictRepository,
            EligibilityAssessmentRepository eligibilityAssessmentRepository,
            RateReferenceRepository rateReferenceRepository,
            InterestEstimateRepository interestEstimateRepository,
            ProposedActionRepository proposedActionRepository,
            LlmCallRepository llmCallRepository,
            ModelRouter modelRouter,
            ActionPlanner actionPlanner,
            AnalysisEventService eventService,
            LlmProperties llmProperties,
            com.duespilot.api.config.DuesPilotProperties duesPilotProperties) {
        this.caseRepository = caseRepository;
        this.documentRepository = documentRepository;
        this.documentPageRepository = documentPageRepository;
        this.extractedFieldRepository = extractedFieldRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.reconciliationResultRepository = reconciliationResultRepository;
        this.conflictRepository = conflictRepository;
        this.eligibilityAssessmentRepository = eligibilityAssessmentRepository;
        this.rateReferenceRepository = rateReferenceRepository;
        this.interestEstimateRepository = interestEstimateRepository;
        this.proposedActionRepository = proposedActionRepository;
        this.llmCallRepository = llmCallRepository;
        this.modelRouter = modelRouter;
        this.actionPlanner = actionPlanner;
        this.eventService = eventService;
        this.llmProperties = llmProperties;
        this.duesPilotProperties = duesPilotProperties;
    }

    public UUID startAnalysis(UUID caseId) {
        CaseEntity c = caseRepository.findById(caseId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Case not found: " + caseId));

        // 1. Check if a run is already in progress
        Optional<AnalysisRunEntity> running = analysisRunRepository.findRunningByCaseId(caseId);
        if (running.isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "RUN_IN_PROGRESS", "An analysis run is already in progress for case " + caseId);
        }

        // 2. Check daily budget cap (Rule R-29, R-54) and demo live analysis quota in live mode
        if (duesPilotProperties.isLiveMode()) {
            // Abuse control: maximum 3 live analyses per demo workspace (analyses of seeded cases replay without model calls)
            if (workspaceRepository != null && !c.isSeed() && !c.isDemoData() && c.workspaceId() != null) {
                workspaceRepository.findById(c.workspaceId()).ifPresent(ws -> {
                    if (ws.isDemo()) {
                        long liveRuns = analysisRunRepository.countLiveRunsByWorkspaceId(ws.id());
                        if (liveRuns >= 3) {
                            throw new ApiException(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "DEMO_QUOTA_EXCEEDED",
                                "Demo workspace live analysis quota reached (maximum 3 live runs). Create a free account to run unlimited analyses."
                            );
                        }
                    }
                });
            }

            long totalCostToday = llmCallRepository.findTotalCostToday();
            if (totalCostToday >= llmProperties.getDailyCostCapMicros()) {
                throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "DAILY_BUDGET_EXCEEDED",
                    "Daily LLM budget cap reached ($" + String.format(java.util.Locale.US, "%.2f", llmProperties.getDailyCostCapMicros() / 1_000_000.0) + "). Live analysis is disabled; replay mode remains active."
                );
            }
        }

        // 3. Create analysis run (status = RUNNING)
        UUID runId = UUID.randomUUID();
        String routerPolicyJson = "{\"extract\":\"nano\",\"reason\":\"super\",\"adjudicate\":\"ultra\"}";
        AnalysisRunEntity run = new AnalysisRunEntity(
            runId,
            caseId,
            "RUNNING",
            routerPolicyJson,
            0L,
            Instant.now(),
            null
        );
        analysisRunRepository.save(run);

        // 4. Submit pipeline to virtual thread
        analysisExecutor.submit(() -> executePipeline(c, runId));

        return runId;
    }

    public void executePipeline(CaseEntity c, UUID runId) {
        Instant startTime = Instant.now();
        UUID caseId = c.id();
        log.info("Starting analysis run {} for case {}", runId, caseId);

        try {
            // Stage 0: Notify run started
            eventService.sendRunStarted(caseId, runId);

            // Stage 1: Parallel capped extraction
            eventService.sendRunStage(caseId, runId, "EXTRACTION", "Starting parallel document field extraction");
            List<DocumentEntity> docs = documentRepository.findByCaseId(caseId);
            extractAllDocuments(docs, runId, caseId);

            // Fetch all verified fields for this run
            List<ExtractedFieldEntity> verifiedFields = extractedFieldRepository.findVerifiedByRunId(runId);
            List<ExtractedFieldEntity> allFields = extractedFieldRepository.findByRunId(runId);

            // Stage 2: Core reconciliation
            eventService.sendRunStage(caseId, runId, "RECONCILIATION", "Running core ledger reconciliation");
            List<LedgerEntry> ledgerEntries = buildLedgerEntries(docs, allFields);
            CaseReconciliation recon = Reconciler.reconcileCase(ledgerEntries);

            // Save ledger entries
            saveLedgerEntries(ledgerEntries, runId, allFields);

            // Save reconciliation result
            saveReconciliationResult(recon, runId, ledgerEntries);

            // Stage 3: Rule conflicts (C1 to C10)
            eventService.sendRunStage(caseId, runId, "RULE_CONFLICTS", "Evaluating deterministic conflict rules C1-C10");
            ConflictContext conflictContext = buildConflictContext(ledgerEntries, docs, allFields);
            List<Conflict> detectedRuleConflicts = ConflictDetector.detectAll(conflictContext);
            List<ConflictEntity> savedConflicts = saveRuleConflicts(detectedRuleConflicts, runId, allFields);

            // Stage 4: LLM semantic conflicts with evidence check (Super, optional Ultra)
            eventService.sendRunStage(caseId, runId, "SEMANTIC_CONFLICTS", "Analyzing semantic conflicts via LLM");
            List<ConflictEntity> llmConflicts = runSemanticConflicts(c, docs, allFields, verifiedFields, runId);
            savedConflicts.addAll(llmConflicts);

            // Stage 5: Eligibility gates A to D
            eventService.sendRunStage(caseId, runId, "GATES", "Evaluating statutory eligibility gates A to D");
            GateInputs gateInputs = buildGateInputs(c, docs, ledgerEntries, recon, savedConflicts);
            CaseEligibility eligibility = GateEvaluator.evaluateAll(gateInputs);
            saveEligibilityAssessments(eligibility, runId);

            // Stage 6: Interest calculation (only if Gate D allows & confirmed rate exists)
            eventService.sendRunStage(caseId, runId, "INTEREST", "Evaluating statutory interest eligibility");
            InterestEstimateEntity interestEstimate = maybeComputeInterest(c, eligibility, recon, runId);

            // Stage 7: Action planner
            eventService.sendRunStage(caseId, runId, "ACTION_PLANNING", "Planning proposed next actions in English and Hindi");
            List<ProposedActionEntity> actions = actionPlanner.planActions(
                c, recon, savedConflicts, eligibility, interestEstimate, verifiedFields, runId
            );
            proposedActionRepository.saveAll(actions);

            // Stage 8: Finish run
            long totalCostMicros = llmCallRepository.findTotalCostByRunId(runId);
            Instant finishedAt = Instant.now();
            long durationMs = Duration.between(startTime, finishedAt).toMillis();

            analysisRunRepository.updateStatus(runId, "COMPLETED", finishedAt, totalCostMicros);
            eventService.sendRunCompleted(caseId, runId, durationMs, totalCostMicros);
            log.info("Analysis run {} completed in {} ms with cost {} micros", runId, durationMs, totalCostMicros);

        } catch (Throwable e) {
            log.error("Analysis run {} failed for case {}", runId, caseId, e);
            long totalCostMicros = llmCallRepository.findTotalCostByRunId(runId);
            analysisRunRepository.updateStatus(runId, "FAILED", Instant.now(), totalCostMicros);
            eventService.sendRunFailed(caseId, runId, e.getMessage() != null ? e.getMessage() : "Internal analysis failure");
        }
    }

    private void extractAllDocuments(List<DocumentEntity> docs, UUID runId, UUID caseId) {
        if (duesPilotProperties.isReplayMode()) {
            for (DocumentEntity doc : docs) {
                List<ExtractedFieldEntity> existing = extractedFieldRepository.findByDocumentId(doc.id());
                if (!existing.isEmpty()) {
                    List<ExtractedFieldEntity> replayed = existing.stream()
                        .filter(f -> !runId.equals(f.runId()))
                        .map(vf -> new ExtractedFieldEntity(
                            null,
                            doc.id(),
                            runId,
                            vf.fieldName(),
                            vf.valueText(),
                            vf.valuePaise(),
                            vf.valueDate(),
                            vf.status(),
                            vf.pageNo(),
                            vf.charStart(),
                            vf.charEnd(),
                            vf.quote(),
                            vf.model()
                        ))
                        .toList();
                    if (!replayed.isEmpty()) {
                        extractedFieldRepository.saveAll(replayed);
                    }
                }
                int extractedCount = existing.size();
                int verifiedCount = (int) existing.stream().filter(ExtractedFieldEntity::isVerified).count();
                eventService.sendDocExtracted(caseId, runId, doc.id(), doc.fileName(), extractedCount, verifiedCount);
            }
            return;
        }

        int cap = Math.min(Math.max(llmProperties.getMaxInflight(), 1), 5);
        Semaphore semaphore = new Semaphore(cap);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (DocumentEntity doc : docs) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();
                    try {
                        List<DocumentPageEntity> pages = documentPageRepository.findByDocumentId(doc.id());
                        Map<Integer, String> pageMap = pages.stream()
                            .collect(Collectors.toMap(DocumentPageEntity::pageNo, DocumentPageEntity::text));

                        ExtractionPolicy.ExtractionPolicyResult result = modelRouter.extractDocument(
                            doc.docType(),
                            doc.fileName(),
                            pageMap,
                            doc.docDate(),
                            runId
                        );

                        // Save extracted fields to DB
                        List<ExtractedFieldEntity> fieldsToSave = result.verifiedFields().stream()
                            .map(vf -> new ExtractedFieldEntity(
                                null,
                                doc.id(),
                                runId,
                                vf.fieldName(),
                                vf.valueText(),
                                vf.valuePaise() != null && vf.valuePaise().isValue() ? vf.valuePaise().getOrThrow().value() : null,
                                vf.valueDate() != null && vf.valueDate().isValue() ? vf.valueDate().getOrThrow() : null,
                                vf.status(),
                                vf.pageNo(),
                                vf.charStart(),
                                vf.charEnd(),
                                vf.quote(),
                                result.finalModel()
                            ))
                            .toList();

                        extractedFieldRepository.saveAll(fieldsToSave);

                        long verifiedCount = fieldsToSave.stream()
                            .filter(f -> f.status() == com.duespilot.core.extract.FieldStatus.VERIFIED)
                            .count();

                        eventService.sendDocExtracted(
                            caseId, runId, doc.id(), doc.fileName(), fieldsToSave.size(), (int) verifiedCount
                        );

                    } finally {
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Document extraction interrupted", e);
                } catch (Exception e) {
                    log.error("Failed to extract document {}", doc.fileName(), e);
                }
            }, analysisExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private List<LedgerEntry> buildLedgerEntries(List<DocumentEntity> docs, List<ExtractedFieldEntity> allFields) {
        List<LedgerEntry> entries = new ArrayList<>();
        Map<UUID, List<ExtractedFieldEntity>> fieldsByDoc = allFields.stream()
            .collect(Collectors.groupingBy(ExtractedFieldEntity::documentId));

        for (DocumentEntity doc : docs) {
            List<ExtractedFieldEntity> docFields = fieldsByDoc.getOrDefault(doc.id(), Collections.emptyList());
            ExtractedFieldEntity totalAmountField = findField(docFields, "total_amount", "grand_total", "invoice_amount", "amount");
            ExtractedFieldEntity dateField = findField(docFields, "invoice_date", "document_date", "date", "payment_date");
            ExtractedFieldEntity numberField = findField(docFields, "invoice_number", "document_number", "ref_number", "utr_number");

            Paise amount = totalAmountField != null && totalAmountField.valuePaise() != null
                ? Paise.ofPaise(totalAmountField.valuePaise())
                : null;
            LocalDate date = dateField != null && dateField.valueDate() != null
                ? dateField.valueDate()
                : doc.docDate();
            String ref = numberField != null && numberField.valueText() != null
                ? numberField.valueText()
                : doc.fileName();

            VerificationStatus status = (totalAmountField != null && totalAmountField.isVerified())
                ? VerificationStatus.VERIFIED
                : VerificationStatus.UNVERIFIED;

            switch (doc.docType()) {
                case INVOICE -> {
                    entries.add(new LedgerEntry(
                        doc.id().toString(),
                        LedgerEntryType.INVOICE,
                        ref != null ? Known.value(ref) : Known.unknown("Missing invoice number"),
                        ref != null ? Known.value(ref) : Known.unknown("Missing invoice number"),
                        Known.unknown("No PO on invoice"),
                        date != null ? Known.value(date) : Known.unknown("Missing invoice date"),
                        amount != null ? Known.value(amount) : Known.unknown("Missing invoice amount"),
                        status,
                        DocumentSource.TAX_INVOICE,
                        false,
                        null,
                        totalAmountField != null ? totalAmountField.quote() : null
                    ));
                }
                case PAYMENT -> {
                    entries.add(new LedgerEntry(
                        doc.id().toString(),
                        LedgerEntryType.PAYMENT,
                        ref != null ? Known.value(ref) : Known.unknown("Missing payment ref"),
                        Known.unknown("Unallocated payment"),
                        Known.unknown("Not a PO"),
                        date != null ? Known.value(date) : Known.unknown("Missing payment date"),
                        amount != null ? Known.value(amount) : Known.unknown("Missing payment amount"),
                        status,
                        DocumentSource.BANK_STATEMENT,
                        false,
                        null,
                        totalAmountField != null ? totalAmountField.quote() : null
                    ));
                }
                case CREDIT_NOTE -> {
                    entries.add(new LedgerEntry(
                        doc.id().toString(),
                        LedgerEntryType.CREDIT_NOTE,
                        ref != null ? Known.value(ref) : Known.unknown("Missing credit note ref"),
                        Known.unknown("Unallocated credit"),
                        Known.unknown("Not a PO"),
                        date != null ? Known.value(date) : Known.unknown("Missing credit note date"),
                        amount != null ? Known.value(amount) : Known.unknown("Missing credit note amount"),
                        status,
                        DocumentSource.CREDIT_NOTE_DOC,
                        false,
                        null,
                        totalAmountField != null ? totalAmountField.quote() : null
                    ));
                }
                case MESSAGE -> {
                    // Rule R-12: payments known only from buyer messages are UNVERIFIED and not subtracted
                    if (amount != null) {
                        entries.add(new LedgerEntry(
                            doc.id().toString(),
                            LedgerEntryType.PAYMENT,
                            ref != null ? Known.value(ref) : Known.unknown("Message ref"),
                            Known.unknown("Unallocated payment"),
                            Known.unknown("Not a PO"),
                            date != null ? Known.value(date) : Known.unknown("Missing message date"),
                            Known.value(amount),
                            VerificationStatus.UNVERIFIED,
                            DocumentSource.BUYER_MESSAGE,
                            false,
                            null,
                            totalAmountField != null ? totalAmountField.quote() : null
                        ));
                    }
                }
                default -> {}
            }
        }

        return entries;
    }

    private void saveLedgerEntries(List<LedgerEntry> entries, UUID runId, List<ExtractedFieldEntity> allFields) {
        Map<String, UUID> fieldIdByDocId = allFields.stream()
            .filter(f -> f.id() != null)
            .collect(Collectors.toMap(f -> f.documentId().toString(), ExtractedFieldEntity::id, (a, b) -> a));

        List<com.duespilot.api.domain.LedgerEntryEntity> toSave = entries.stream()
            .map(entry -> {
                UUID fieldId = fieldIdByDocId.get(entry.id());
                List<UUID> sourceFieldIds = fieldId != null ? List.of(fieldId) : List.of();
                Long amt = entry.amount().isValue() ? entry.amount().getOrThrow().value() : 0L;
                LocalDate dt = entry.date().orNull();
                String kind = switch (entry.type()) {
                    case INVOICE -> "INVOICE";
                    case PAYMENT -> "PAYMENT";
                    case CREDIT_NOTE -> "CREDIT_NOTE";
                    case ADJUSTMENT -> "ADJUSTMENT";
                };
                String status = entry.isUnverified() ? "UNVERIFIED" : (entry.isDisputed() ? "DISPUTED" : "CONFIRMED");

                return new com.duespilot.api.domain.LedgerEntryEntity(
                    null,
                    runId,
                    kind,
                    entry.referenceNumber().orNull(),
                    amt,
                    dt,
                    status,
                    sourceFieldIds
                );
            })
            .toList();

        ledgerEntryRepository.saveAll(toSave);
    }

    private void saveReconciliationResult(CaseReconciliation recon, UUID runId, List<LedgerEntry> entries) {
        long invoicedPaise = entries.stream()
            .filter(e -> e.type() == LedgerEntryType.INVOICE && e.amount().isValue())
            .mapToLong(e -> e.amount().getOrThrow().value())
            .sum();

        Long balancePaise = recon.totalOutstandingBalance() != null
            ? recon.totalOutstandingBalance().value()
            : null;

        String balanceStatus;
        if (balancePaise == null) {
            balanceStatus = "UNRECONCILED";
        } else if (balancePaise == 0L) {
            balanceStatus = "RECONCILED";
        } else if (recon.totalConfirmedPaid().value() > 0 || recon.totalConfirmedCredit().value() > 0) {
            balanceStatus = "PARTIAL";
        } else {
            balanceStatus = "UNRECONCILED";
        }

        ReconciliationResultEntity result = new ReconciliationResultEntity(
            runId,
            invoicedPaise,
            recon.totalConfirmedPaid().value(),
            recon.totalConfirmedCredit().value(),
            recon.totalDisputedAmounts().value(),
            recon.totalUnverifiedPayments().value(),
            balancePaise,
            balanceStatus
        );

        reconciliationResultRepository.save(result);
    }

    private ConflictContext buildConflictContext(
            List<LedgerEntry> ledgerEntries,
            List<DocumentEntity> docs,
            List<ExtractedFieldEntity> allFields) {

        LedgerEntry primaryInvoice = ledgerEntries.stream()
            .filter(e -> e.type() == LedgerEntryType.INVOICE)
            .findFirst()
            .orElseGet(() -> LedgerEntry.invoice(
                UUID.randomUUID().toString(), "DUMMY", LocalDate.now(), Paise.ZERO, VerificationStatus.UNVERIFIED
            ));

        ConflictContext.Builder builder = ConflictContext.builder(primaryInvoice)
            .entries(ledgerEntries);

        // Check for Delivery documents
        List<ConflictEvidence.DeliveryDocument> deliveryDocs = docs.stream()
            .filter(d -> d.docType() == DocType.DELIVERY || d.docType() == DocType.ACCEPTANCE)
            .map(d -> new ConflictEvidence.DeliveryDocument(
                d.id().toString(),
                d.fileName(),
                primaryInvoice.referenceNumber().orElse(""),
                d.docDate() != null ? Known.value(d.docDate()) : Known.unknown("Missing delivery date"),
                d.docDate() != null ? Known.value(d.docDate()) : Known.unknown("Missing acceptance date"),
                Known.unknown("No qty ordered"),
                Known.unknown("No qty delivered"),
                true,
                false
            ))
            .toList();
        builder.deliveryDocuments(deliveryDocs);

        // Check for Purchase Orders
        List<ConflictEvidence.PurchaseOrder> pos = docs.stream()
            .filter(d -> d.docType() == DocType.PO)
            .map(d -> {
                ExtractedFieldEntity amtField = allFields.stream()
                    .filter(f -> f.documentId().equals(d.id()) && f.valuePaise() != null)
                    .findFirst()
                    .orElse(null);
                Known<Paise> poAmt = amtField != null ? Known.value(Paise.ofPaise(amtField.valuePaise())) : Known.unknown("Missing PO amount");
                return new ConflictEvidence.PurchaseOrder(
                    d.id().toString(),
                    d.fileName(),
                    poAmt,
                    Known.value(30),
                    d.docDate() != null ? Known.value(d.docDate()) : Known.unknown("Missing PO date")
                );
            })
            .toList();
        builder.purchaseOrders(pos);

        // Check for Remittance deductions (C9)
        List<ConflictEvidence.RemittanceAdvice> remittances = new ArrayList<>();
        for (DocumentEntity d : docs) {
            if (d.docType() == DocType.PAYMENT) {
                // Check if any deduction field exists
                List<ExtractedFieldEntity> deductionFields = allFields.stream()
                    .filter(f -> f.documentId().equals(d.id()) && f.fieldName().toLowerCase().contains("deduction"))
                    .toList();
                if (!deductionFields.isEmpty()) {
                    List<ConflictEvidence.RemittanceDeduction> deds = deductionFields.stream()
                        .map(df -> new ConflictEvidence.RemittanceDeduction(
                            df.valueText() != null ? df.valueText() : "Deduction",
                            df.valuePaise() != null ? Known.value(Paise.ofPaise(df.valuePaise())) : Known.unknown("Missing deduction amount"),
                            "TDS / Withholding"
                        ))
                        .toList();
                    remittances.add(new ConflictEvidence.RemittanceAdvice(
                        d.id().toString(),
                        d.fileName(),
                        deds
                    ));
                }
            }
        }
        builder.remittanceAdvices(remittances);

        return builder.build();
    }

    private List<ConflictEntity> saveRuleConflicts(
            List<Conflict> conflicts,
            UUID runId,
            List<ExtractedFieldEntity> allFields) {

        List<UUID> fallbackFieldIds = allFields.stream()
            .map(ExtractedFieldEntity::id)
            .filter(id -> id != null)
            .limit(1)
            .toList();

        List<ConflictEntity> entities = conflicts.stream()
            .map(c -> {
                String typeStr = c.type().name();
                String severity = switch (c.type()) {
                    case C5, C6 -> "BLOCKING";
                    default -> "WARN";
                };
                List<UUID> evidenceUuids = c.evidenceIds().stream()
                    .map(id -> {
                        try { return UUID.fromString(id); }
                        catch (Exception e) { return null; }
                    })
                    .filter(id -> id != null)
                    .toList();
                if (evidenceUuids.isEmpty()) {
                    evidenceUuids = fallbackFieldIds;
                }

                return new ConflictEntity(
                    null,
                    runId,
                    typeStr,
                    "RULE",
                    severity,
                    c.summary(),
                    null,
                    evidenceUuids,
                    "OPEN"
                );
            })
            .toList();

        return new ArrayList<>(conflictRepository.saveAll(entities));
    }

    private List<ConflictEntity> runSemanticConflicts(
            CaseEntity c,
            List<DocumentEntity> docs,
            List<ExtractedFieldEntity> allFields,
            List<ExtractedFieldEntity> verifiedFields,
            UUID runId) {

        if (duesPilotProperties.isReplayMode()) {
            return new ArrayList<>();
        }

        List<ConflictEntity> savedLlmConflicts = new ArrayList<>();
        Set<UUID> verifiedFieldIds = verifiedFields.stream()
            .map(ExtractedFieldEntity::id)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        try {
            // Build prompt
            StringBuilder contextBuilder = new StringBuilder("<case_documents>\n");
            for (DocumentEntity doc : docs) {
                contextBuilder.append(String.format("Document: %s (Type: %s, Date: %s)\n", doc.fileName(), doc.docType(), doc.docDate()));
            }
            contextBuilder.append("</case_documents>\n<verified_fields>\n");
            for (ExtractedFieldEntity vf : verifiedFields) {
                contextBuilder.append(String.format("Field ID: %s, Name: %s, Value: %s, Quote: \"%s\"\n",
                    vf.id(), vf.fieldName(), vf.valueText(), vf.quote() != null ? vf.quote() : ""));
            }
            contextBuilder.append("</verified_fields>\n");

            var messages = List.of(
                new LlmClient.Message("system", """
                    You are a commercial dispute analyzer.
                    Detect any semantic contradictions or terms disputes (C1 to C10).
                    Rule R-4: Every claim MUST cite an existing field ID from the verified fields.
                    Output JSON with schema:
                    {
                      "conflicts": [
                        {
                          "type": "C4",
                          "severity": "WARN",
                          "summary": "...",
                          "amount_paise": null,
                          "evidence_field_ids": ["uuid"]
                        }
                      ],
                      "needs_adjudication": false
                    }
                    """),
                new LlmClient.Message("user", contextBuilder.toString() + "\nIdentify semantic conflicts.")
            );

            ModelRouter.RouterResult routerResult = modelRouter.analyzeSemanticConflicts(messages, runId);
            if (routerResult.isSuccess() && routerResult.parsedJson() != null) {
                JsonNode json = routerResult.parsedJson();
                if (json.has("conflicts") && json.get("conflicts").isArray()) {
                    for (JsonNode confNode : json.get("conflicts")) {
                        String type = confNode.has("type") ? confNode.get("type").asText() : "C4";
                        String severity = confNode.has("severity") ? confNode.get("severity").asText() : "WARN";
                        String summary = confNode.has("summary") ? confNode.get("summary").asText() : "Semantic conflict";
                        Long amountPaise = confNode.has("amount_paise") && !confNode.get("amount_paise").isNull()
                            ? confNode.get("amount_paise").asLong() : null;

                        List<UUID> citedIds = new ArrayList<>();
                        if (confNode.has("evidence_field_ids") && confNode.get("evidence_field_ids").isArray()) {
                            for (JsonNode idNode : confNode.get("evidence_field_ids")) {
                                try {
                                    citedIds.add(UUID.fromString(idNode.asText()));
                                } catch (Exception ignored) {}
                            }
                        }

                        // Rule R-4 & Architecture Section 8.4: EVIDENCE CHECK
                        // Drop any claim whose cited span / evidence ID does not exist in verified fields
                        if (citedIds.isEmpty() || !verifiedFieldIds.containsAll(citedIds)) {
                            log.warn("Dropping LLM conflict {} per Rule R-4: cited evidence IDs {} not present in verified fields",
                                type, citedIds);
                            continue;
                        }

                        ConflictEntity entity = new ConflictEntity(
                            null,
                            runId,
                            type,
                            "LLM",
                            severity,
                            summary,
                            amountPaise,
                            citedIds,
                            "OPEN"
                        );
                        savedLlmConflicts.add(conflictRepository.save(entity));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("LLM semantic conflict analysis failed or unavailable; proceeding with rule conflicts only", e);
        }

        return savedLlmConflicts;
    }

    private GateInputs buildGateInputs(
            CaseEntity c,
            List<DocumentEntity> docs,
            List<LedgerEntry> entries,
            CaseReconciliation recon,
            List<ConflictEntity> conflicts) {

        // Gate A: Enterprise Category & Udyam
        boolean hasUdyamDoc = docs.stream().anyMatch(d -> d.docType() == DocType.UDYAM) || c.udyamRegisteredOn() != null;
        EnterpriseCategory category = switch (c.supplierCategory()) {
            case MICRO -> EnterpriseCategory.MICRO;
            case SMALL -> EnterpriseCategory.SMALL;
            case MEDIUM -> EnterpriseCategory.MEDIUM;
            case UNKNOWN -> EnterpriseCategory.UNKNOWN;
        };
        Known<LocalDate> udyamDate = c.udyamRegisteredOn() != null
            ? Known.value(c.udyamRegisteredOn())
            : Known.unknown("No Udyam registration date recorded");

        Optional<LedgerEntry> inv = entries.stream().filter(e -> e.type() == LedgerEntryType.INVOICE).findFirst();
        Known<LocalDate> invDate = inv.map(LedgerEntry::date).orElse(Known.unknown("No invoice date"));

        // Gate B: Delivery & Acceptance
        boolean hasDelivery = docs.stream().anyMatch(d -> d.docType() == DocType.DELIVERY || d.docType() == DocType.ACCEPTANCE);
        boolean isDeliveryDisputed = conflicts.stream().anyMatch(conf -> "C5".equalsIgnoreCase(conf.type()) || "C7".equalsIgnoreCase(conf.type()));
        boolean isDeemedAccepted = hasDelivery && !isDeliveryDisputed;
        Known<LocalDate> deliveryDate = hasDelivery && invDate.isValue()
            ? invDate
            : Known.unknown("No delivery date evidenced");

        // Gate C: Payment terms
        boolean hasTermsConflict = conflicts.stream().anyMatch(conf -> "C4".equalsIgnoreCase(conf.type()));
        Known<Integer> agreedTerms = hasTermsConflict
            ? Known.unknown("Conflicting payment terms across commercial documents")
            : Known.value(30);

        // Gate D: Principal & Bank rate
        Known<Paise> principalAmount = recon.totalOutstandingBalance() != null
            ? Known.value(recon.totalOutstandingBalance())
            : Known.unknown("Reconciliation balance is unknown");
        boolean isPrincipalDisputed = conflicts.stream().anyMatch(conf -> "C3".equalsIgnoreCase(conf.type()) || "C9".equalsIgnoreCase(conf.type()));

        Optional<RateReferenceEntity> latestConfirmedRate = rateReferenceRepository.findLatestConfirmed();
        Known<Integer> bankRateBps = latestConfirmedRate
            .map(r -> Known.value(r.rateBps()))
            .orElse(Known.unknown("bank rate not verified"));
        boolean isBankRateConfirmed = latestConfirmedRate.isPresent();

        return GateInputs.builder()
            .udyam(hasUdyamDoc, category, udyamDate, invDate)
            .delivery(hasDelivery, isDeliveryDisputed, isDeemedAccepted, deliveryDate)
            .terms(agreedTerms, hasTermsConflict, Map.of("Invoice", 30))
            .statutoryParameters(StatutoryParameters.defaultParameters())
            .interestInputs(principalAmount, isPrincipalDisputed, bankRateBps, isBankRateConfirmed)
            .build();
    }

    private void saveEligibilityAssessments(CaseEligibility eligibility, UUID runId) {
        List<EligibilityAssessmentEntity> assessments = List.of(
            toAssessment(eligibility.gateA(), "A", runId),
            toAssessment(eligibility.gateB(), "B", runId),
            toAssessment(eligibility.gateC(), "C", runId),
            toAssessment(eligibility.gateD(), "D", runId)
        );
        eligibilityAssessmentRepository.saveAll(assessments);
    }

    private EligibilityAssessmentEntity toAssessment(GateResult result, String gate, UUID runId) {
        String reasonsJson;
        try {
            reasonsJson = MAPPER.writeValueAsString(result.reasons());
        } catch (Exception e) {
            reasonsJson = "[]";
        }
        return new EligibilityAssessmentEntity(
            null,
            runId,
            gate,
            result.status().name(),
            reasonsJson
        );
    }

    private @Nullable InterestEstimateEntity maybeComputeInterest(
            CaseEntity c,
            CaseEligibility eligibility,
            CaseReconciliation recon,
            UUID runId) {

        // Rule R-19 & Gate D requirement:
        // Interest runs ONLY when Gate D is ELIGIBLE_FOR_REVIEW and a confirmed rate exists
        if (eligibility.gateD().status() != GateStatus.ELIGIBLE_FOR_REVIEW) {
            log.info("Abstaining from interest calculation for run {}: Gate D status is {} (bank rate not verified)", runId, eligibility.gateD().status());
            return null;
        }

        if (recon.totalOutstandingBalance() == null || recon.totalOutstandingBalance().isZero()) {
            log.info("Abstaining from interest calculation for run {}: Outstanding balance is zero or null", runId);
            return null;
        }

        Optional<RateReferenceEntity> rateRefOpt = rateReferenceRepository.findLatestConfirmed();
        if (rateRefOpt.isEmpty()) {
            log.info("Abstaining from interest calculation for run {}: bank rate not verified (no confirmed bank rate in rate_references)", runId);
            return null;
        }

        RateReferenceEntity rateRef = rateRefOpt.get();
        Paise principal = recon.totalOutstandingBalance();

        LocalDate fromDate = LocalDate.now().minusDays(60);
        LocalDate toDate = LocalDate.now();

        RateCandidate candidate = RateCandidate.confirmed(
            rateRef.rateBps(),
            rateRef.effectiveOn(),
            rateRef.sourceUrl(),
            rateRef.rawExcerpt()
        );

        InterestResult interestResult = InterestEngine.compute(
            principal,
            fromDate,
            toDate,
            candidate
        );

        InterestEstimateEntity entity = new InterestEstimateEntity(
            null,
            runId,
            principal.value(),
            rateRef.id(),
            3,
            fromDate,
            toDate,
            interestResult.interest().value(),
            interestResult.convention(),
            "PROVISIONAL"
        );

        return interestEstimateRepository.save(entity);
    }

    private static @Nullable ExtractedFieldEntity findField(List<ExtractedFieldEntity> fields, String... names) {
        for (String name : names) {
            for (ExtractedFieldEntity f : fields) {
                if (name.equalsIgnoreCase(f.fieldName())) {
                    return f;
                }
            }
        }
        return null;
    }
}
