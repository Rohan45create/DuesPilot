package com.duespilot.api.web;

import com.duespilot.api.domain.AnalysisRunEntity;
import com.duespilot.api.domain.ConflictEntity;
import com.duespilot.api.domain.EligibilityAssessmentEntity;
import com.duespilot.api.domain.InterestEstimateEntity;
import com.duespilot.api.domain.LedgerEntryEntity;
import com.duespilot.api.domain.ProposedActionEntity;
import com.duespilot.api.domain.ReconciliationResultEntity;
import com.duespilot.api.repository.AnalysisRunRepository;
import com.duespilot.api.repository.CaseRepository;
import com.duespilot.api.repository.ConflictRepository;
import com.duespilot.api.repository.EligibilityAssessmentRepository;
import com.duespilot.api.repository.InterestEstimateRepository;
import com.duespilot.api.repository.LedgerEntryRepository;
import com.duespilot.api.repository.ProposedActionRepository;
import com.duespilot.api.repository.ReconciliationResultRepository;
import com.duespilot.api.service.AnalysisEventService;
import com.duespilot.api.service.AnalysisOrchestrator;
import com.duespilot.api.web.dto.AnalyzeResponse;
import com.duespilot.api.web.dto.RunSummaryResponse;
import com.duespilot.api.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private final CaseRepository caseRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final com.duespilot.api.service.AnalysisService analysisService;
    private final com.duespilot.api.service.EventStreamService eventService;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ConflictRepository conflictRepository;
    private final EligibilityAssessmentRepository eligibilityAssessmentRepository;
    private final InterestEstimateRepository interestEstimateRepository;
    private final ProposedActionRepository proposedActionRepository;

    public AnalysisController(
            CaseRepository caseRepository,
            AnalysisRunRepository analysisRunRepository,
            com.duespilot.api.service.AnalysisService analysisService,
            com.duespilot.api.service.EventStreamService eventService,
            ReconciliationResultRepository reconciliationResultRepository,
            LedgerEntryRepository ledgerEntryRepository,
            ConflictRepository conflictRepository,
            EligibilityAssessmentRepository eligibilityAssessmentRepository,
            InterestEstimateRepository interestEstimateRepository,
            ProposedActionRepository proposedActionRepository) {
        this.caseRepository = caseRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.analysisService = analysisService;
        this.eventService = eventService;
        this.reconciliationResultRepository = reconciliationResultRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.conflictRepository = conflictRepository;
        this.eligibilityAssessmentRepository = eligibilityAssessmentRepository;
        this.interestEstimateRepository = interestEstimateRepository;
        this.proposedActionRepository = proposedActionRepository;
    }

    @PostMapping("/cases/{id}/analyze")
    public ResponseEntity<AnalyzeResponse> startAnalyze(@PathVariable("id") UUID caseId) {
        UUID runId = analysisService.startAnalysis(caseId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new AnalyzeResponse(runId));
    }

    @GetMapping(value = "/cases/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable("id") UUID caseId) {
        if (caseRepository.findById(caseId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Case not found: " + caseId);
        }
        return eventService.subscribe(caseId);
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<RunSummaryResponse> getRunSummary(@PathVariable("runId") UUID runId) {
        AnalysisRunEntity run = analysisRunRepository.findById(runId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Analysis run not found: " + runId));

        ReconciliationResultEntity reconciliation = reconciliationResultRepository.findByRunId(runId).orElse(null);
        List<LedgerEntryEntity> ledgerEntries = ledgerEntryRepository.findByRunId(runId);
        List<ConflictEntity> conflicts = conflictRepository.findByRunId(runId);
        List<EligibilityAssessmentEntity> gates = eligibilityAssessmentRepository.findByRunId(runId);
        InterestEstimateEntity interest = interestEstimateRepository.findByRunId(runId).orElse(null);
        List<ProposedActionEntity> proposedActions = proposedActionRepository.findByRunId(runId);

        return ResponseEntity.ok(new RunSummaryResponse(
            run,
            reconciliation,
            ledgerEntries,
            conflicts,
            gates,
            interest,
            proposedActions
        ));
    }
}
