package com.duespilot.api.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

public interface EventStreamService {
    SseEmitter subscribe(UUID caseId);
    void sendRunStarted(UUID caseId, UUID runId);
    void sendDocExtracted(UUID caseId, UUID runId, UUID documentId, String fileName, int fieldsExtracted, int fieldsVerified);
    void sendRunStage(UUID caseId, UUID runId, String stage, String description);
    void sendRunCompleted(UUID caseId, UUID runId, long durationMs, long costMicros);
    void sendRunFailed(UUID caseId, UUID runId, String errorMessage);
}
