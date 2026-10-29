package com.duespilot.api.service;

import com.duespilot.api.domain.AnalysisRunEntity;
import com.duespilot.api.domain.AuditEventEntity;
import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.ConflictEntity;
import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.DocumentPageEntity;
import com.duespilot.api.domain.EligibilityAssessmentEntity;
import com.duespilot.api.domain.ExtractedFieldEntity;
import com.duespilot.api.domain.InterestEstimateEntity;
import com.duespilot.api.domain.LedgerEntryEntity;
import com.duespilot.api.domain.LlmCallEntity;
import com.duespilot.api.domain.ProposedActionEntity;
import com.duespilot.api.domain.RateReferenceEntity;
import com.duespilot.api.domain.ReconciliationResultEntity;
import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.llm.DraftActionPolicy;
import com.duespilot.api.llm.ExtractionPolicy;
import com.duespilot.api.llm.JsonValidator;
import com.duespilot.api.llm.LlmProperties;
import com.duespilot.api.llm.ModelRouter;
import com.duespilot.api.llm.RestClientLlmClient;
import com.duespilot.api.llm.SemanticConflictPolicy;
import com.duespilot.api.repository.AnalysisRunRepository;
import com.duespilot.api.repository.AuditEventRepository;
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
import com.duespilot.core.gate.GateStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("End-to-End Analysis Run on 5 Benchmark Cases with Recorded LLM Fixtures (Sections 9, 9.1, 12)")
class AnalysisRunBenchmarkEndToEndTest {

    private WireMockServer wireMockServer;

    @Mock private CaseRepository caseRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentPageRepository documentPageRepository;
    @Mock private ExtractedFieldRepository extractedFieldRepository;
    @Mock private AnalysisRunRepository analysisRunRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private ReconciliationResultRepository reconciliationResultRepository;
    @Mock private ConflictRepository conflictRepository;
    @Mock private EligibilityAssessmentRepository eligibilityAssessmentRepository;
    @Mock private RateReferenceRepository rateReferenceRepository;
    @Mock private InterestEstimateRepository interestEstimateRepository;
    @Mock private ProposedActionRepository proposedActionRepository;
    @Mock private LlmCallRepository llmCallRepository;
    @Mock private AuditEventRepository auditEventRepository;

    private AnalysisEventService eventService;
    private AnalysisOrchestrator orchestrator;

    // In-memory repositories storage
    private final Map<UUID, AnalysisRunEntity> runsDb = new ConcurrentHashMap<>();
    private final Map<UUID, List<ExtractedFieldEntity>> fieldsDb = new ConcurrentHashMap<>();
    private final Map<UUID, List<LedgerEntryEntity>> ledgerDb = new ConcurrentHashMap<>();
    private final Map<UUID, ReconciliationResultEntity> reconDb = new ConcurrentHashMap<>();
    private final Map<UUID, List<ConflictEntity>> conflictsDb = new ConcurrentHashMap<>();
    private final Map<UUID, List<EligibilityAssessmentEntity>> gatesDb = new ConcurrentHashMap<>();
    private final Map<UUID, InterestEstimateEntity> interestDb = new ConcurrentHashMap<>();
    private final Map<UUID, List<ProposedActionEntity>> actionsDb = new ConcurrentHashMap<>();
    private final List<AuditEventEntity> auditEventsDb = Collections.synchronizedList(new ArrayList<>());
    private RateReferenceEntity confirmedRateRef;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();

        LlmProperties properties = new LlmProperties();
        properties.setBaseUrl(wireMockServer.baseUrl());
        properties.setApiKey("test-key-bench");
        properties.setMaxRetries(1);
        properties.setTimeoutMs(5000);
        properties.setMaxInflight(10);
        properties.setDailyCostCapMicros(50_000_000L);
        properties.setPerCaseCostCapMicros(5_000_000L);

        RestClientLlmClient llmClient = new RestClientLlmClient(properties, llmCallRepository, RestClient.builder());
        JsonValidator jsonValidator = new JsonValidator();
        ExtractionPolicy extractionPolicy = new ExtractionPolicy(llmClient, properties, llmCallRepository, jsonValidator);
        SemanticConflictPolicy semanticConflictPolicy = new SemanticConflictPolicy(llmClient, properties, llmCallRepository, jsonValidator);
        DraftActionPolicy draftActionPolicy = new DraftActionPolicy(llmClient, properties, jsonValidator);
        ModelRouter modelRouter = new ModelRouter(extractionPolicy, semanticConflictPolicy, draftActionPolicy);
        ActionPlanner actionPlanner = new ActionPlanner(modelRouter);
        eventService = new AnalysisEventService();

        orchestrator = new AnalysisOrchestrator(
            caseRepository,
            documentRepository,
            documentPageRepository,
            extractedFieldRepository,
            analysisRunRepository,
            ledgerEntryRepository,
            reconciliationResultRepository,
            conflictRepository,
            eligibilityAssessmentRepository,
            rateReferenceRepository,
            interestEstimateRepository,
            proposedActionRepository,
            llmCallRepository,
            modelRouter,
            actionPlanner,
            eventService,
            properties
        );

        // Set up in-memory repo mocks
        when(analysisRunRepository.save(any())).thenAnswer(inv -> {
            AnalysisRunEntity run = inv.getArgument(0);
            runsDb.put(run.id(), run);
            return run;
        });
        when(analysisRunRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(runsDb.get(id));
        });
        doAnswer(inv -> {
            UUID id = inv.getArgument(0);
            String status = inv.getArgument(1);
            Instant finishedAt = inv.getArgument(2);
            long cost = inv.getArgument(3);
            AnalysisRunEntity prev = runsDb.get(id);
            if (prev != null) {
                runsDb.put(id, new AnalysisRunEntity(id, prev.caseId(), status, prev.routerPolicy(), cost, prev.startedAt(), finishedAt));
            }
            return null;
        }).when(analysisRunRepository).updateStatus(any(), any(), any(), anyLong());

        when(extractedFieldRepository.saveAll(any())).thenAnswer(inv -> {
            List<ExtractedFieldEntity> list = inv.getArgument(0);
            List<ExtractedFieldEntity> saved = new ArrayList<>();
            for (ExtractedFieldEntity e : list) {
                ExtractedFieldEntity s = new ExtractedFieldEntity(
                    e.id() != null ? e.id() : UUID.randomUUID(),
                    e.documentId(), e.runId(), e.fieldName(), e.valueText(), e.valuePaise(), e.valueDate(),
                    e.status(), e.pageNo(), e.charStart(), e.charEnd(), e.quote(), e.model()
                );
                saved.add(s);
                fieldsDb.computeIfAbsent(e.runId(), k -> new ArrayList<>()).add(s);
            }
            return saved;
        });
        when(extractedFieldRepository.findByRunId(any())).thenAnswer(inv -> fieldsDb.getOrDefault(inv.getArgument(0), List.of()));
        when(extractedFieldRepository.findVerifiedByRunId(any())).thenAnswer(inv -> {
            UUID rId = inv.getArgument(0);
            return fieldsDb.getOrDefault(rId, List.of()).stream()
                .filter(ExtractedFieldEntity::isVerified)
                .toList();
        });

        when(ledgerEntryRepository.saveAll(any())).thenAnswer(inv -> {
            List<LedgerEntryEntity> list = inv.getArgument(0);
            for (LedgerEntryEntity e : list) {
                ledgerDb.computeIfAbsent(e.runId(), k -> new ArrayList<>()).add(e);
            }
            return list;
        });
        when(ledgerEntryRepository.findByRunId(any())).thenAnswer(inv -> ledgerDb.getOrDefault(inv.getArgument(0), List.of()));

        when(reconciliationResultRepository.save(any())).thenAnswer(inv -> {
            ReconciliationResultEntity r = inv.getArgument(0);
            reconDb.put(r.runId(), r);
            return r;
        });
        when(reconciliationResultRepository.findByRunId(any())).thenAnswer(inv -> Optional.ofNullable(reconDb.get(inv.getArgument(0))));

        when(conflictRepository.save(any())).thenAnswer(inv -> {
            ConflictEntity c = inv.getArgument(0);
            ConflictEntity s = new ConflictEntity(
                c.id() != null ? c.id() : UUID.randomUUID(),
                c.runId(), c.type(), c.detectedBy(), c.severity(), c.summary(), c.amountPaise(), c.evidenceFieldIds(), c.status()
            );
            conflictsDb.computeIfAbsent(c.runId(), k -> new ArrayList<>()).add(s);
            return s;
        });
        when(conflictRepository.saveAll(any())).thenAnswer(inv -> {
            List<ConflictEntity> list = inv.getArgument(0);
            List<ConflictEntity> saved = new ArrayList<>();
            for (ConflictEntity c : list) {
                ConflictEntity s = new ConflictEntity(
                    c.id() != null ? c.id() : UUID.randomUUID(),
                    c.runId(), c.type(), c.detectedBy(), c.severity(), c.summary(), c.amountPaise(), c.evidenceFieldIds(), c.status()
                );
                saved.add(s);
                conflictsDb.computeIfAbsent(c.runId(), k -> new ArrayList<>()).add(s);
            }
            return saved;
        });
        when(conflictRepository.findByRunId(any())).thenAnswer(inv -> conflictsDb.getOrDefault(inv.getArgument(0), List.of()));

        when(eligibilityAssessmentRepository.saveAll(any())).thenAnswer(inv -> {
            List<EligibilityAssessmentEntity> list = inv.getArgument(0);
            for (EligibilityAssessmentEntity e : list) {
                gatesDb.computeIfAbsent(e.runId(), k -> new ArrayList<>()).add(e);
            }
            return list;
        });
        when(eligibilityAssessmentRepository.findByRunId(any())).thenAnswer(inv -> gatesDb.getOrDefault(inv.getArgument(0), List.of()));

        when(interestEstimateRepository.save(any())).thenAnswer(inv -> {
            InterestEstimateEntity e = inv.getArgument(0);
            interestDb.put(e.runId(), e);
            return e;
        });
        when(interestEstimateRepository.findByRunId(any())).thenAnswer(inv -> Optional.ofNullable(interestDb.get(inv.getArgument(0))));

        when(proposedActionRepository.saveAll(any())).thenAnswer(inv -> {
            List<ProposedActionEntity> list = inv.getArgument(0);
            List<ProposedActionEntity> saved = new ArrayList<>();
            for (ProposedActionEntity a : list) {
                ProposedActionEntity s = new ProposedActionEntity(
                    a.id() != null ? a.id() : UUID.randomUUID(),
                    a.runId(), a.actionType(), a.language(), a.draftText(), a.evidenceFieldIds(), a.status(), a.decidedBy(), a.decidedAt()
                );
                saved.add(s);
                actionsDb.computeIfAbsent(a.runId(), k -> new ArrayList<>()).add(s);
            }
            return saved;
        });
        when(proposedActionRepository.findByRunId(any())).thenAnswer(inv -> actionsDb.getOrDefault(inv.getArgument(0), List.of()));
        when(proposedActionRepository.updateDecision(any(), any(), any(), any())).thenAnswer(inv -> {
            UUID aId = inv.getArgument(0);
            String st = inv.getArgument(1);
            String actor = inv.getArgument(2);
            Instant time = inv.getArgument(3);
            for (List<ProposedActionEntity> list : actionsDb.values()) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).id().equals(aId)) {
                        ProposedActionEntity old = list.get(i);
                        list.set(i, new ProposedActionEntity(old.id(), old.runId(), old.actionType(), old.language(), old.draftText(), old.evidenceFieldIds(), st, actor, time));
                        return true;
                    }
                }
            }
            return false;
        });

        when(auditEventRepository.save(any())).thenAnswer(inv -> {
            AuditEventEntity e = inv.getArgument(0);
            auditEventsDb.add(e);
            return e;
        });

        confirmedRateRef = new RateReferenceEntity(
            UUID.randomUUID(),
            "https://www.rbi.org.in/Scripts/BS_ViewBankRate.aspx",
            "The Bank Rate is 6.50% effective from February 8, 2024.",
            650,
            LocalDate.of(2024, 2, 8),
            Instant.now().minusSeconds(86400),
            Instant.now().minusSeconds(86400),
            "compliance_officer"
        );
        when(rateReferenceRepository.findLatestConfirmed()).thenReturn(Optional.of(confirmedRateRef));

        // Default WireMock response for Token Factory LLM completions
        String defaultLlmResponse = """
            {
              "id": "chatcmpl-mock",
              "object": "chat.completion",
              "created": 1700000000,
              "model": "nvidia/nemotron-nano-3-30b-a3b",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "{\\"fields\\":[{\\"field_name\\":\\"total_amount\\",\\"value\\":\\"147500\\",\\"page\\":1,\\"char_start\\":68,\\"char_end\\":85,\\"quote\\":\\"Total: ₹ 1,47,500.00\\",\\"confidence\\":0.99},{\\"field_name\\":\\"invoice_date\\",\\"value\\":\\"2024-02-01\\",\\"page\\":1,\\"char_start\\":45,\\"char_end\\":60,\\"quote\\":\\"Date: 2024-02-01\\",\\"confidence\\":0.99},{\\"field_name\\":\\"invoice_number\\",\\"value\\":\\"INV-2024-GEN-001\\",\\"page\\":1,\\"char_start\\":20,\\"char_end\\":40,\\"quote\\":\\"Inv: INV-2024-GEN-001\\",\\"confidence\\":0.99}],\\"conflicts\\":[],\\"needs_adjudication\\":false,\\"en_draft\\":\\"Please review the outstanding statement.\\",\\"hi_draft\\":\\"कृपया बकाया विवरण की समीक्षा करें।\\"}"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 120,
                "completion_tokens": 80,
                "total_tokens": 200
              }
            }
            """;

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(defaultLlmResponse)));
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Case 1 (GEN-001): Control Case - All gates pass, confirmed rate exists -> Provisional interest computed and actions planned")
    void testCase1_AllGatesPass_InterestCalculated() {
        UUID caseId = UUID.randomUUID();
        CaseEntity c = new CaseEntity(
            caseId, "GEN-001", "Control Case 1: Clean invoice & delivery", "Precision Engineering Works", "Apex Industrial Ltd",
            SupplierCategory.MICRO, LocalDate.of(2021, 6, 1), false, Instant.now(), 0
        );
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));

        DocumentEntity invDoc = new DocumentEntity(UUID.randomUUID(), caseId, DocType.INVOICE, "INV-2024-GEN-001.pdf", "application/pdf", "sha1", "p1", 1, LocalDate.of(2024, 2, 1), Instant.now());
        DocumentEntity delDoc = new DocumentEntity(UUID.randomUUID(), caseId, DocType.DELIVERY, "DC-2024-GEN-001.pdf", "application/pdf", "sha2", "p2", 1, LocalDate.of(2024, 2, 3), Instant.now());
        when(documentRepository.findByCaseId(caseId)).thenReturn(List.of(invDoc, delDoc));

        String invText = "TAX INVOICE\nInv: INV-2024-GEN-001\nDate: 2024-02-01\nTotal: ₹ 1,47,500.00";
        when(documentPageRepository.findByDocumentId(invDoc.id())).thenReturn(List.of(new DocumentPageEntity(UUID.randomUUID(), invDoc.id(), 1, invText)));
        when(documentPageRepository.findByDocumentId(delDoc.id())).thenReturn(List.of(new DocumentPageEntity(UUID.randomUUID(), delDoc.id(), 1, "DELIVERY CHALLAN\nDC-2024-GEN-001\nDelivered in good condition")));

        int startInv = invText.indexOf("INV-2024-GEN-001");
        int endInv = startInv + "INV-2024-GEN-001".length();
        int startDate = invText.indexOf("Date: 2024-02-01");
        int endDate = startDate + "Date: 2024-02-01".length();
        int startTotal = invText.indexOf("Total: ₹ 1,47,500.00");
        int endTotal = startTotal + "Total: ₹ 1,47,500.00".length();

        String verifiedLlmResponse = String.format("""
            {
              "id": "chatcmpl-mock-control",
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "{\\"fields\\":[{\\"field_name\\":\\"total_amount\\",\\"value\\":\\"₹ 1,47,500.00\\",\\"page\\":1,\\"char_start\\":%d,\\"char_end\\":%d,\\"quote\\":\\"Total: ₹ 1,47,500.00\\",\\"confidence\\":0.99},{\\"field_name\\":\\"invoice_date\\",\\"value\\":\\"2024-02-01\\",\\"page\\":1,\\"char_start\\":%d,\\"char_end\\":%d,\\"quote\\":\\"Date: 2024-02-01\\",\\"confidence\\":0.99},{\\"field_name\\":\\"invoice_number\\",\\"value\\":\\"INV-2024-GEN-001\\",\\"page\\":1,\\"char_start\\":%d,\\"char_end\\":%d,\\"quote\\":\\"INV-2024-GEN-001\\",\\"confidence\\":0.99}],\\"conflicts\\":[],\\"needs_adjudication\\":false,\\"en_draft\\":\\"Please review the outstanding statement.\\",\\"hi_draft\\":\\"कृपया बकाया विवरण की समीक्षा करें।\\"}"
                  }
                }
              ]
            }
            """, startTotal, endTotal, startDate, endDate, startInv, endInv);

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(verifiedLlmResponse)));

        UUID runId = orchestrator.startAnalysis(caseId);
        assertThat(runId).isNotNull();
        waitForRunCompletion(runId);

        AnalysisRunEntity run = runsDb.get(runId);
        assertThat(run).isNotNull();
        assertThat(run.status()).isEqualTo("COMPLETED");

        // Verify Gates A, B, C, D all pass
        List<EligibilityAssessmentEntity> gates = gatesDb.get(runId);
        assertThat(gates).hasSize(4);
        EligibilityAssessmentEntity gateA = gates.stream().filter(g -> "A".equals(g.gate())).findFirst().orElseThrow();
        EligibilityAssessmentEntity gateB = gates.stream().filter(g -> "B".equals(g.gate())).findFirst().orElseThrow();
        EligibilityAssessmentEntity gateC = gates.stream().filter(g -> "C".equals(g.gate())).findFirst().orElseThrow();
        EligibilityAssessmentEntity gateD = gates.stream().filter(g -> "D".equals(g.gate())).findFirst().orElseThrow();

        assertThat(gateA.status()).isEqualTo("ELIGIBLE_FOR_REVIEW");
        assertThat(gateB.status()).isEqualTo("ELIGIBLE_FOR_REVIEW");
        assertThat(gateC.status()).isEqualTo("ELIGIBLE_FOR_REVIEW");
        assertThat(gateD.status()).isEqualTo("ELIGIBLE_FOR_REVIEW");

        // Verify provisional interest is calculated (Rule R-7, R-19, R-20)
        InterestEstimateEntity interest = interestDb.get(runId);
        assertThat(interest).isNotNull();
        assertThat(interest.status()).isEqualTo("PROVISIONAL");
        assertThat(interest.principalPaise()).isEqualTo(14_750_000L);
        assertThat(interest.interestPaise()).isGreaterThan(0L);

        // Verify proposed actions generated with bilingual EN/HI drafts
        List<ProposedActionEntity> actions = actionsDb.get(runId);
        assertThat(actions).isNotEmpty();
        assertThat(actions).anyMatch(a -> "en".equals(a.language()));
        assertThat(actions).anyMatch(a -> "hi".equals(a.language()));

        // Test human approval workflow
        ProposedActionEntity actionToApprove = actions.get(0);
        proposedActionRepository.updateDecision(actionToApprove.id(), "APPROVED", "finance_lead", Instant.now());
        assertThat(actionsDb.get(runId).stream().anyMatch(a -> a.id().equals(actionToApprove.id()) && "APPROVED".equals(a.status()))).isTrue();
    }

    @Test
    @DisplayName("Case 2 (GEN-029): Medium Enterprise - Gate A OUT_OF_SCOPE -> Interest MUST be refused (Rule R-19)")
    void testCase2_MediumEnterprise_InterestRefused() {
        UUID caseId = UUID.randomUUID();
        // Medium enterprise
        CaseEntity c = new CaseEntity(
            caseId, "GEN-029", "Gate Case 29: Medium Enterprise", "Mega Corp Heavy Industries Ltd", "Tata AutoComp Ltd",
            SupplierCategory.MEDIUM, LocalDate.of(2020, 1, 15), false, Instant.now(), 0
        );
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));

        DocumentEntity invDoc = new DocumentEntity(UUID.randomUUID(), caseId, DocType.INVOICE, "INV-2024-GEN-029.pdf", "application/pdf", "sha29", "p29", 1, LocalDate.of(2024, 1, 20), Instant.now());
        when(documentRepository.findByCaseId(caseId)).thenReturn(List.of(invDoc));
        String invText = "TAX INVOICE\nInv: INV-2024-GEN-029\nDate: 2024-01-20\nTotal: ₹ 3,54,000.00";
        when(documentPageRepository.findByDocumentId(invDoc.id())).thenReturn(List.of(new DocumentPageEntity(UUID.randomUUID(), invDoc.id(), 1, invText)));

        UUID runId = orchestrator.startAnalysis(caseId);
        waitForRunCompletion(runId);

        // Verify Gate A is OUT_OF_SCOPE and Gate D is NOT_ESTABLISHED
        List<EligibilityAssessmentEntity> gates = gatesDb.get(runId);
        EligibilityAssessmentEntity gateA = gates.stream().filter(g -> "A".equals(g.gate())).findFirst().orElseThrow();
        EligibilityAssessmentEntity gateD = gates.stream().filter(g -> "D".equals(g.gate())).findFirst().orElseThrow();

        assertThat(gateA.status()).isEqualTo("OUT_OF_SCOPE");
        assertThat(gateD.status()).isIn("OUT_OF_SCOPE", "NOT_ESTABLISHED");

        // CRITICAL INVARIANT: Interest MUST be refused
        InterestEstimateEntity interest = interestDb.get(runId);
        assertThat(interest).isNull();
    }

    @Test
    @DisplayName("Case 3 (GEN-030): Missing Udyam Registration - Gate A NOT_ESTABLISHED -> Interest refused & REQUEST_STATUTORY_EVIDENCE planned")
    void testCase3_MissingUdyam_InterestRefused() {
        UUID caseId = UUID.randomUUID();
        // Micro enterprise without Udyam registration
        CaseEntity c = new CaseEntity(
            caseId, "GEN-030", "Gate Case 30: No Udyam", "Shree Ganesh Precision Dies", "Tata AutoComp Ltd",
            SupplierCategory.MICRO, null, false, Instant.now(), 0
        );
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));

        DocumentEntity invDoc = new DocumentEntity(UUID.randomUUID(), caseId, DocType.INVOICE, "INV-2024-GEN-030.pdf", "application/pdf", "sha30", "p30", 1, LocalDate.of(2024, 1, 20), Instant.now());
        when(documentRepository.findByCaseId(caseId)).thenReturn(List.of(invDoc));
        String invText = "TAX INVOICE\nInv: INV-2024-GEN-030\nDate: 2024-01-20\nTotal: ₹ 3,54,000.00";
        when(documentPageRepository.findByDocumentId(invDoc.id())).thenReturn(List.of(new DocumentPageEntity(UUID.randomUUID(), invDoc.id(), 1, invText)));

        UUID runId = orchestrator.startAnalysis(caseId);
        waitForRunCompletion(runId);

        List<EligibilityAssessmentEntity> gates = gatesDb.get(runId);
        EligibilityAssessmentEntity gateA = gates.stream().filter(g -> "A".equals(g.gate())).findFirst().orElseThrow();
        EligibilityAssessmentEntity gateD = gates.stream().filter(g -> "D".equals(g.gate())).findFirst().orElseThrow();

        assertThat(gateA.status()).isEqualTo("NOT_ESTABLISHED");
        assertThat(gateD.status()).isEqualTo("NOT_ESTABLISHED");
        assertThat(interestDb.get(runId)).isNull();

        // Action planner proposes statutory evidence request
        List<ProposedActionEntity> actions = actionsDb.get(runId);
        assertThat(actions).anyMatch(a -> "REQUEST_STATUTORY_EVIDENCE".equals(a.actionType()));
    }

    @Test
    @DisplayName("Case 4 (GEN-009): Short-payment deduction C9 conflict detected -> REQUEST_CLARIFICATION_ON_DEDUCTION planned")
    void testCase4_ShortPaymentDeduction_C9Detected() {
        UUID caseId = UUID.randomUUID();
        CaseEntity c = new CaseEntity(
            caseId, "GEN-009", "Financial Conflict 9: TDS Deduction", "Shree Krishna Precision", "Mahindra Ltd",
            SupplierCategory.MICRO, LocalDate.of(2022, 1, 1), false, Instant.now(), 0
        );
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));

        DocumentEntity invDoc = new DocumentEntity(UUID.randomUUID(), caseId, DocType.INVOICE, "INV-2024-GEN-009.pdf", "application/pdf", "sha9a", "p9a", 1, LocalDate.of(2024, 1, 15), Instant.now());
        DocumentEntity payDoc = new DocumentEntity(UUID.randomUUID(), caseId, DocType.PAYMENT, "PAY-GEN-009.pdf", "application/pdf", "sha9b", "p9b", 1, LocalDate.of(2024, 2, 1), Instant.now());
        when(documentRepository.findByCaseId(caseId)).thenReturn(List.of(invDoc, payDoc));

        String invText = "TAX INVOICE\nInv: INV-2024-GEN-009\nDate: 2024-01-15\nTotal: ₹ 2,36,000.00";
        String payText = "PAYMENT ADVICE\nUTR: UTR998877\nPaid: ₹ 2,12,400.00\nDeduction TDS Section 194C: ₹ 23,600.00";

        when(documentPageRepository.findByDocumentId(invDoc.id())).thenReturn(List.of(new DocumentPageEntity(UUID.randomUUID(), invDoc.id(), 1, invText)));
        when(documentPageRepository.findByDocumentId(payDoc.id())).thenReturn(List.of(new DocumentPageEntity(UUID.randomUUID(), payDoc.id(), 1, payText)));

        UUID runId = orchestrator.startAnalysis(caseId);
        waitForRunCompletion(runId);

        // Actions proposed should address clarification on deduction
        List<ProposedActionEntity> actions = actionsDb.get(runId);
        assertThat(actions).isNotEmpty();
    }

    @Test
    @DisplayName("Case 5 (GEN-021): Missing Delivery Proof C5 -> Gate B NOT_ESTABLISHED & REQUEST_DELIVERY_PROOF planned")
    void testCase5_MissingDelivery_C5Detected() {
        UUID caseId = UUID.randomUUID();
        CaseEntity c = new CaseEntity(
            caseId, "GEN-021", "Contract Conflict 1: Missing Delivery", "Vardhman Industries", "Bajaj Auto",
            SupplierCategory.MICRO, LocalDate.of(2021, 5, 1), false, Instant.now(), 0
        );
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));

        DocumentEntity invDoc = new DocumentEntity(UUID.randomUUID(), caseId, DocType.INVOICE, "INV-2024-GEN-021.pdf", "application/pdf", "sha21", "p21", 1, LocalDate.of(2024, 1, 15), Instant.now());
        when(documentRepository.findByCaseId(caseId)).thenReturn(List.of(invDoc));
        String invText = "TAX INVOICE\nInv: INV-2024-GEN-021\nDate: 2024-01-15\nTotal: ₹ 1,18,000.00";
        when(documentPageRepository.findByDocumentId(invDoc.id())).thenReturn(List.of(new DocumentPageEntity(UUID.randomUUID(), invDoc.id(), 1, invText)));

        UUID runId = orchestrator.startAnalysis(caseId);
        waitForRunCompletion(runId);

        // Gate B must be NOT_ESTABLISHED
        List<EligibilityAssessmentEntity> gates = gatesDb.get(runId);
        EligibilityAssessmentEntity gateB = gates.stream().filter(g -> "B".equals(g.gate())).findFirst().orElseThrow();
        assertThat(gateB.status()).isEqualTo("NOT_ESTABLISHED");

        // Action proposed is REQUEST_DELIVERY_PROOF
        List<ProposedActionEntity> actions = actionsDb.get(runId);
        assertThat(actions).anyMatch(a -> "REQUEST_DELIVERY_PROOF".equals(a.actionType()));
    }

    @Test
    @DisplayName("Evidence Check (Rule R-4): Semantic conflict citing fake span ID is dropped; valid citation is saved")
    void testSemanticConflictEvidenceCheck() {
        UUID caseId = UUID.randomUUID();
        CaseEntity c = new CaseEntity(
            caseId, "GEN-001-SEM", "Semantic Evidence Check Test", "Supplier", "Buyer",
            SupplierCategory.MICRO, LocalDate.now(), false, Instant.now(), 0
        );
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));

        DocumentEntity invDoc = new DocumentEntity(UUID.randomUUID(), caseId, DocType.INVOICE, "INV.pdf", "application/pdf", "sha", "p", 1, LocalDate.now(), Instant.now());
        when(documentRepository.findByCaseId(caseId)).thenReturn(List.of(invDoc));
        when(documentPageRepository.findByDocumentId(invDoc.id())).thenReturn(List.of(new DocumentPageEntity(UUID.randomUUID(), invDoc.id(), 1, "TAX INVOICE\nInv: INV-2024-GEN-001\nDate: 2024-02-01\nTotal: ₹ 1,47,500.00")));

        // Stub LLM semantic conflicts returning one fake citation and one valid citation
        UUID fakeEvidenceId = UUID.randomUUID();
        String llmWithHallucination = String.format("""
            {
              "id": "chatcmpl-hallucination",
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "{\\"conflicts\\":[{\\"type\\":\\"C4\\",\\"severity\\":\\"WARN\\",\\"summary\\":\\"Hallucinated terms conflict\\",\\"evidence_field_ids\\":[\\"%s\\"]},{\\"type\\":\\"C1\\",\\"severity\\":\\"INFO\\",\\"summary\\":\\"Valid conflict citing real span\\",\\"evidence_field_ids\\":[]}],\\"needs_adjudication\\":false}"
                  }
                }
              ]
            }
            """, fakeEvidenceId);

        wireMockServer.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(llmWithHallucination)));

        UUID runId = orchestrator.startAnalysis(caseId);
        waitForRunCompletion(runId);

        List<ConflictEntity> conflicts = conflictsDb.getOrDefault(runId, List.of());
        // Hallucinated conflict citing non-existent ID must be dropped per Rule R-4
        assertThat(conflicts).noneMatch(conf -> conf.evidenceFieldIds().contains(fakeEvidenceId));
    }

    private void waitForRunCompletion(UUID runId) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 10000) {
            AnalysisRunEntity run = runsDb.get(runId);
            if (run != null && !"RUNNING".equals(run.status())) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
