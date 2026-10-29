package com.duespilot.api.web;

import com.duespilot.api.config.SecurityHeadersFilter;
import com.duespilot.api.domain.AnalysisRunEntity;
import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.ConflictEntity;
import com.duespilot.api.domain.EligibilityAssessmentEntity;
import com.duespilot.api.domain.InterestEstimateEntity;
import com.duespilot.api.domain.LedgerEntryEntity;
import com.duespilot.api.domain.ProposedActionEntity;
import com.duespilot.api.domain.ReconciliationResultEntity;
import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.repository.AnalysisRunRepository;
import com.duespilot.api.repository.AuditEventRepository;
import com.duespilot.api.repository.CaseRepository;
import com.duespilot.api.repository.ConflictRepository;
import com.duespilot.api.repository.EligibilityAssessmentRepository;
import com.duespilot.api.repository.InterestEstimateRepository;
import com.duespilot.api.repository.LedgerEntryRepository;
import com.duespilot.api.repository.ProposedActionRepository;
import com.duespilot.api.repository.ReconciliationResultRepository;
import com.duespilot.api.service.AnalysisEventService;
import com.duespilot.api.service.AnalysisOrchestrator;
import com.duespilot.api.web.error.ApiException;
import com.duespilot.api.web.error.ApiExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AnalysisController.class, ActionController.class})
@Import({ApiExceptionHandler.class, SecurityHeadersFilter.class})
@DisplayName("Analysis & Action REST Controllers Unit Tests (Section 12)")
class AnalysisControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CaseRepository caseRepository;

    @MockitoBean
    private AnalysisRunRepository analysisRunRepository;

    @MockitoBean
    private com.duespilot.api.service.AnalysisService analysisService;

    @MockitoBean
    private com.duespilot.api.service.EventStreamService eventService;

    @MockitoBean
    private ReconciliationResultRepository reconciliationResultRepository;

    @MockitoBean
    private LedgerEntryRepository ledgerEntryRepository;

    @MockitoBean
    private ConflictRepository conflictRepository;

    @MockitoBean
    private EligibilityAssessmentRepository eligibilityAssessmentRepository;

    @MockitoBean
    private InterestEstimateRepository interestEstimateRepository;

    @MockitoBean
    private ProposedActionRepository proposedActionRepository;

    @MockitoBean
    private AuditEventRepository auditEventRepository;

    @Test
    @DisplayName("POST /api/v1/cases/{id}/analyze returns 202 Accepted with runId")
    void startAnalyzeReturns202() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(analysisService.startAnalysis(caseId)).thenReturn(runId);

        mockMvc.perform(post("/api/v1/cases/{id}/analyze", caseId))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.runId").value(runId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/cases/{id}/analyze returns 409 RUN_IN_PROGRESS when run active")
    void startAnalyzeConflictReturns409() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(analysisService.startAnalysis(caseId))
            .thenThrow(new ApiException(HttpStatus.CONFLICT, "RUN_IN_PROGRESS", "An analysis run is already in progress"));

        mockMvc.perform(post("/api/v1/cases/{id}/analyze", caseId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RUN_IN_PROGRESS"));
    }

    @Test
    @DisplayName("POST /api/v1/cases/{id}/analyze returns 429 DAILY_BUDGET_EXCEEDED when cap reached")
    void startAnalyzeBudgetExceededReturns429() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(analysisService.startAnalysis(caseId))
            .thenThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "DAILY_BUDGET_EXCEEDED", "Daily cost cap exceeded"));

        mockMvc.perform(post("/api/v1/cases/{id}/analyze", caseId))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("DAILY_BUDGET_EXCEEDED"));
    }

    @Test
    @DisplayName("GET /api/v1/cases/{id}/events returns SSE text/event-stream")
    void streamEventsReturnsOk() throws Exception {
        UUID caseId = UUID.randomUUID();
        CaseEntity caseEntity = new CaseEntity(
            caseId, "DP-001", "Test Case", "Supplier", "Buyer",
            SupplierCategory.MICRO, LocalDate.now(), false, Instant.now(), 0
        );
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));
        when(eventService.subscribe(caseId)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/v1/cases/{id}/events", caseId))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/runs/{runId} returns complete run summary")
    void getRunSummaryReturnsFullPayload() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        AnalysisRunEntity run = new AnalysisRunEntity(
            runId, caseId, "COMPLETED", "{}", 1200L, Instant.now(), Instant.now()
        );
        ReconciliationResultEntity recon = new ReconciliationResultEntity(
            runId, 100000L, 50000L, 0L, 0L, 0L, 50000L, "PARTIAL"
        );
        LedgerEntryEntity entry = new LedgerEntryEntity(
            UUID.randomUUID(), runId, "INVOICE", "INV-001", 100000L, LocalDate.now(), "CONFIRMED", List.of()
        );
        ConflictEntity conflict = new ConflictEntity(
            UUID.randomUUID(), runId, "C1", "RULE", "WARN", "Partial payment", 50000L, List.of(), "OPEN"
        );
        EligibilityAssessmentEntity gate = new EligibilityAssessmentEntity(
            UUID.randomUUID(), runId, "A", "ELIGIBLE_FOR_REVIEW", "[\"Valid\"]"
        );
        ProposedActionEntity action = new ProposedActionEntity(
            UUID.randomUUID(), runId, "PROPOSE_PAYMENT_PLAN", "en", "Draft", List.of(), "PROPOSED", null, null
        );

        when(analysisRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(reconciliationResultRepository.findByRunId(runId)).thenReturn(Optional.of(recon));
        when(ledgerEntryRepository.findByRunId(runId)).thenReturn(List.of(entry));
        when(conflictRepository.findByRunId(runId)).thenReturn(List.of(conflict));
        when(eligibilityAssessmentRepository.findByRunId(runId)).thenReturn(List.of(gate));
        when(interestEstimateRepository.findByRunId(runId)).thenReturn(Optional.empty());
        when(proposedActionRepository.findByRunId(runId)).thenReturn(List.of(action));

        mockMvc.perform(get("/api/v1/runs/{runId}", runId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.run.id").value(runId.toString()))
            .andExpect(jsonPath("$.reconciliation.balanceStatus").value("PARTIAL"))
            .andExpect(jsonPath("$.ledgerEntries[0].kind").value("INVOICE"))
            .andExpect(jsonPath("$.conflicts[0].type").value("C1"))
            .andExpect(jsonPath("$.gates[0].gate").value("A"))
            .andExpect(jsonPath("$.proposedActions[0].actionType").value("PROPOSE_PAYMENT_PLAN"));
    }

    @Test
    @DisplayName("POST /api/v1/actions/{id}/approve updates status to APPROVED and persists audit event")
    void approveActionSuccess() throws Exception {
        UUID actionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ProposedActionEntity action = new ProposedActionEntity(
            actionId, runId, "REQUEST_DELIVERY_PROOF", "en", "Draft", List.of(), "PROPOSED", null, null
        );
        when(proposedActionRepository.findById(actionId)).thenReturn(Optional.of(action));
        when(proposedActionRepository.updateDecision(eq(actionId), eq("APPROVED"), any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/actions/{id}/approve", actionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actor\":\"accountant@duespilot.com\",\"note\":\"Approved by finance\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.decidedBy").value("accountant@duespilot.com"));

        verify(auditEventRepository).save(any());
    }

    @Test
    @DisplayName("POST /api/v1/actions/{id}/reject updates status to REJECTED and persists audit event")
    void rejectActionSuccess() throws Exception {
        UUID actionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ProposedActionEntity action = new ProposedActionEntity(
            actionId, runId, "REQUEST_DELIVERY_PROOF", "en", "Draft", List.of(), "PROPOSED", null, null
        );
        when(proposedActionRepository.findById(actionId)).thenReturn(Optional.of(action));
        when(proposedActionRepository.updateDecision(eq(actionId), eq("REJECTED"), any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/actions/{id}/reject", actionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actor\":\"manager@duespilot.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.decidedBy").value("manager@duespilot.com"));

        verify(auditEventRepository).save(any());
    }

    @Test
    @DisplayName("POST /api/v1/actions/{id}/approve on already decided action returns 409 ALREADY_DECIDED")
    void approveAlreadyDecidedActionReturns409() throws Exception {
        UUID actionId = UUID.randomUUID();
        ProposedActionEntity action = new ProposedActionEntity(
            actionId, UUID.randomUUID(), "REQUEST_DELIVERY_PROOF", "en", "Draft", List.of(), "APPROVED", "someone", Instant.now()
        );
        when(proposedActionRepository.findById(actionId)).thenReturn(Optional.of(action));

        mockMvc.perform(post("/api/v1/actions/{id}/approve", actionId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ALREADY_DECIDED"));
    }
}
