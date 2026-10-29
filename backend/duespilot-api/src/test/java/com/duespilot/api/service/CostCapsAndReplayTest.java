package com.duespilot.api.service;

import com.duespilot.api.config.DuesPilotProperties;
import com.duespilot.api.domain.AnalysisRunEntity;
import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.ExtractedFieldEntity;
import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.llm.LlmClient;
import com.duespilot.api.llm.LlmProperties;
import com.duespilot.api.llm.ModelRouter;
import com.duespilot.api.llm.RestClientLlmClient;
import com.duespilot.api.llm.error.CaseBudgetExceededException;
import com.duespilot.api.llm.error.DailyBudgetExceededException;
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
import com.duespilot.core.extract.FieldStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Cost Caps, Rate Limits, and Replay Mode Tests (Rule R-29, R-54, Sections 15 & 16)")
class CostCapsAndReplayTest {

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
    @Mock private ModelRouter modelRouter;
    @Mock private AnalysisEventService eventService;

    private ActionPlanner actionPlanner;
    private LlmProperties llmProperties;
    private DuesPilotProperties duesPilotProperties;
    private AnalysisOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        llmProperties = new LlmProperties();
        llmProperties.setDailyCostCapMicros(50_000_000L); // $50.00
        llmProperties.setPerCaseCostCapMicros(2_000_000L); // $2.00

        duesPilotProperties = new DuesPilotProperties();
        actionPlanner = new ActionPlanner(modelRouter, duesPilotProperties);

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
            llmProperties,
            duesPilotProperties
        );
    }

    @Test
    @DisplayName("Live Mode: Daily budget cap exceeded blocks analysis start with clear ProblemDetail message")
    void testLiveModeDailyCapExceeded() {
        duesPilotProperties.setDemoMode("live");
        UUID caseId = UUID.randomUUID();
        CaseEntity caseEntity = new CaseEntity(
            caseId, "DP-001", "Test Case", "Supplier", "Buyer",
            SupplierCategory.MICRO, LocalDate.now(), false, Instant.now(), 1
        );
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));
        when(analysisRunRepository.findRunningByCaseId(caseId)).thenReturn(Optional.empty());

        // Daily cost spent is at cap ($50.00)
        when(llmCallRepository.findTotalCostToday()).thenReturn(50_000_000L);

        assertThatThrownBy(() -> orchestrator.startAnalysis(caseId))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(apiEx.getCode()).isEqualTo("DAILY_BUDGET_EXCEEDED");
                assertThat(apiEx.getDetail()).contains("Daily LLM budget cap reached");
                assertThat(apiEx.getDetail()).contains("Live analysis is disabled; replay mode remains active.");
            });
    }

    @Test
    @DisplayName("Live Mode: Per-case budget cap exceeded throws CaseBudgetExceededException with clear message")
    void testLiveModePerCaseCapExceeded() {
        duesPilotProperties.setDemoMode("live");
        UUID runId = UUID.randomUUID();

        // Run cost already exceeds per-case cap ($2.00)
        when(llmCallRepository.findTotalCostByRunId(runId)).thenReturn(2_050_000L);

        RestClientLlmClient client = new RestClientLlmClient(
            llmProperties,
            llmCallRepository,
            RestClient.builder(),
            duesPilotProperties
        );

        LlmClient.LlmRequest request = LlmClient.LlmRequest.builder()
            .task("extract")
            .model("nvidia/nemotron-nano-3-30b-a3b")
            .messages(List.of(new LlmClient.Message("user", "Extract fields")))
            .maxTokens(512)
            .temperature(0.1)
            .runId(runId)
            .build();

        assertThatThrownBy(() -> client.chat(request))
            .isInstanceOf(CaseBudgetExceededException.class)
            .hasMessageContaining("Per-case LLM budget cap reached ($2.00). Analysis was stopped to prevent excess spending.");
    }

    @Test
    @DisplayName("Replay Mode: startAnalysis succeeds even when daily budget is exhausted")
    void testReplayModeStartAnalysisAllowedAtCap() {
        duesPilotProperties.setDemoMode("replay");
        UUID caseId = UUID.randomUUID();
        CaseEntity caseEntity = new CaseEntity(
            caseId, "GEN-001", "Seed Case 1", "Precision Engineering", "Apex Industrial",
            SupplierCategory.MICRO, LocalDate.now(), true, Instant.now(), 1
        );
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));
        when(analysisRunRepository.findRunningByCaseId(caseId)).thenReturn(Optional.empty());
        when(llmCallRepository.findTotalCostToday()).thenReturn(50_000_000L);

        UUID runId = orchestrator.startAnalysis(caseId);
        assertThat(runId).isNotNull();
    }

    @Test
    @DisplayName("Replay Mode: Pipeline executes pre-recorded fields and completes with zero LLM calls")
    void testReplayModePipelineExecutesWithoutLlmCalls() {
        duesPilotProperties.setDemoMode("replay");
        UUID caseId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        CaseEntity caseEntity = new CaseEntity(
            caseId, "GEN-001", "Seed Case 1", "Precision Engineering", "Apex Industrial",
            SupplierCategory.MICRO, LocalDate.now(), true, Instant.now(), 1
        );

        DocumentEntity doc = new DocumentEntity(
            UUID.randomUUID(), caseId, DocType.INVOICE, "INV-001.pdf", "application/pdf",
            "sha1", "./data/uploads/INV-001.pdf", 1, LocalDate.of(2024, 2, 1), Instant.now()
        );
        when(documentRepository.findByCaseId(caseId)).thenReturn(List.of(doc));

        ExtractedFieldEntity existingField = new ExtractedFieldEntity(
            UUID.randomUUID(), doc.id(), UUID.randomUUID(), "total_amount", "147500", 14_750_000L,
            LocalDate.of(2024, 2, 1), FieldStatus.VERIFIED, 1, 10, 25, "Total: ₹ 1,47,500.00",
            "nvidia/nemotron-nano-3-30b-a3b"
        );
        when(extractedFieldRepository.findByDocumentId(doc.id())).thenReturn(List.of(existingField));
        when(extractedFieldRepository.findByRunId(any())).thenReturn(List.of(existingField));
        when(extractedFieldRepository.findVerifiedByRunId(any())).thenReturn(List.of(existingField));

        // Execute pipeline synchronously
        orchestrator.executePipeline(caseEntity, runId);

        // Verification: Zero calls to LLM extraction or semantic conflicts
        verify(modelRouter, never()).extractDocument(any(), any(), any(), any(), any());
        verify(modelRouter, never()).analyzeSemanticConflicts(any(), any());
        verify(modelRouter, never()).draftAction(any(), any());

        // Verify pre-recorded fields are replayed and run is marked COMPLETED
        verify(extractedFieldRepository).saveAll(any());
        verify(analysisRunRepository).updateStatus(eq(runId), eq("COMPLETED"), any(), any(Long.class));
    }

    @Test
    @DisplayName("Replay Mode: Outbound LLM chat calls are strictly forbidden and throw IllegalStateException")
    void testReplayModeBlocksLlmClientChat() {
        duesPilotProperties.setDemoMode("replay");

        RestClientLlmClient client = new RestClientLlmClient(
            llmProperties,
            llmCallRepository,
            RestClient.builder(),
            duesPilotProperties
        );

        LlmClient.LlmRequest request = LlmClient.LlmRequest.builder()
            .task("extract")
            .model("nvidia/nemotron-nano-3-30b-a3b")
            .messages(List.of(new LlmClient.Message("user", "test")))
            .maxTokens(512)
            .temperature(0.1)
            .runId(UUID.randomUUID())
            .build();

        assertThatThrownBy(() -> client.chat(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("LLM calls are forbidden in DEMO_MODE=replay");
    }
}
