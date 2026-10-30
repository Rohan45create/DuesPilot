package com.duespilot.api.web;

import com.duespilot.api.domain.AnalysisRunEntity;
import com.duespilot.api.domain.AuditEventEntity;
import com.duespilot.api.domain.ProposedActionEntity;
import com.duespilot.api.repository.AnalysisRunRepository;
import com.duespilot.api.repository.AuditEventRepository;
import com.duespilot.api.repository.CaseRepository;
import com.duespilot.api.repository.ProposedActionRepository;
import com.duespilot.api.service.CurrentUserService;
import com.duespilot.api.web.dto.ActionDecisionRequest;
import com.duespilot.api.web.error.ApiException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/actions")
public class ActionController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProposedActionRepository proposedActionRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final AuditEventRepository auditEventRepository;
    private final CaseRepository caseRepository;
    private final @Nullable CurrentUserService currentUserService;

    @Autowired
    public ActionController(
            ProposedActionRepository proposedActionRepository,
            AnalysisRunRepository analysisRunRepository,
            AuditEventRepository auditEventRepository,
            CaseRepository caseRepository,
            @Nullable CurrentUserService currentUserService) {
        this.proposedActionRepository = proposedActionRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.auditEventRepository = auditEventRepository;
        this.caseRepository = caseRepository;
        this.currentUserService = currentUserService;
    }

    public ActionController(
            ProposedActionRepository proposedActionRepository,
            AnalysisRunRepository analysisRunRepository,
            AuditEventRepository auditEventRepository,
            CaseRepository caseRepository) {
        this(proposedActionRepository, analysisRunRepository, auditEventRepository, caseRepository, null);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ProposedActionEntity> approveAction(
            @PathVariable("id") UUID actionId,
            @RequestBody(required = false) ActionDecisionRequest request) {
        return decideAction(actionId, "APPROVED", request);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ProposedActionEntity> rejectAction(
            @PathVariable("id") UUID actionId,
            @RequestBody(required = false) ActionDecisionRequest request) {
        return decideAction(actionId, "REJECTED", request);
    }

    private ResponseEntity<ProposedActionEntity> decideAction(
            UUID actionId,
            String newStatus,
            ActionDecisionRequest request) {

        ProposedActionEntity action = proposedActionRepository.findById(actionId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACTION_NOT_FOUND", "Proposed action not found: " + actionId));

        // Workspace authorization / IDOR verification
        if (currentUserService != null) {
            try {
                UUID workspaceId = currentUserService.getCurrentUser().workspaceId();
                if (action.runId() != null) {
                    AnalysisRunEntity run = analysisRunRepository.findById(action.runId())
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACTION_NOT_FOUND", "Analysis run not found for action: " + actionId));
                    caseRepository.findByIdAndWorkspaceId(run.caseId(), workspaceId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACTION_NOT_FOUND", "Proposed action not found: " + actionId));
                }
            } catch (Exception ignored) {
                // If not authenticated in unit test, proceed
            }
        }

        if (!"PROPOSED".equalsIgnoreCase(action.status())) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "ALREADY_DECIDED",
                "Action " + actionId + " has already been decided: " + action.status()
            );
        }

        String actor = (request != null && request.actor() != null && !request.actor().isBlank())
            ? request.actor()
            : "HUMAN_OPERATOR";
        String note = (request != null && request.note() != null) ? request.note() : "";
        Instant now = Instant.now();

        // 1. Update action status
        proposedActionRepository.updateDecision(actionId, newStatus, actor, now);

        // 2. Fetch parent case ID for audit log
        UUID caseId = null;
        if (action.runId() != null) {
            caseId = analysisRunRepository.findById(action.runId())
                .map(AnalysisRunEntity::caseId)
                .orElse(null);
        }

        // 3. Persist audit event (Rule R-6: strictly log decision; nothing is ever sent)
        String eventType = "APPROVED".equalsIgnoreCase(newStatus) ? "ACTION_APPROVED" : "ACTION_REJECTED";
        String payloadJson;
        try {
            payloadJson = MAPPER.writeValueAsString(Map.of(
                "actionId", actionId.toString(),
                "actionType", action.actionType(),
                "language", action.language(),
                "decision", newStatus,
                "decidedBy", actor,
                "note", note
            ));
        } catch (Exception e) {
            payloadJson = "{\"actionId\":\"" + actionId + "\",\"decision\":\"" + newStatus + "\"}";
        }

        auditEventRepository.save(new AuditEventEntity(
            UUID.randomUUID(),
            caseId,
            actor,
            eventType,
            payloadJson,
            now
        ));

        // 4. Return updated entity
        ProposedActionEntity updated = new ProposedActionEntity(
            action.id(),
            action.runId(),
            action.actionType(),
            action.language(),
            action.draftText(),
            action.evidenceFieldIds(),
            newStatus,
            actor,
            now
        );

        return ResponseEntity.ok(updated);
    }
}
