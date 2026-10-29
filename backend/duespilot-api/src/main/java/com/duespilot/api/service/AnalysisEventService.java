package com.duespilot.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AnalysisEventService implements EventStreamService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisEventService.class);
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L; // 10 minutes

    private final Map<UUID, List<SseEmitter>> emittersByCaseId = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID caseId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emittersByCaseId.computeIfAbsent(caseId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> {
            List<SseEmitter> list = emittersByCaseId.get(caseId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    emittersByCaseId.remove(caseId);
                }
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("caseId", caseId.toString(), "status", "CONNECTED")));
        } catch (IOException e) {
            log.debug("Failed to send initial SSE connected event", e);
            cleanup.run();
        }

        return emitter;
    }

    public void sendRunStarted(UUID caseId, UUID runId) {
        broadcast(caseId, "run.started", Map.of(
            "runId", runId.toString(),
            "caseId", caseId.toString()
        ));
    }

    public void sendDocExtracted(UUID caseId, UUID runId, UUID documentId, String fileName, int fieldsExtracted, int fieldsVerified) {
        broadcast(caseId, "doc.extracted", Map.of(
            "runId", runId.toString(),
            "documentId", documentId.toString(),
            "fileName", fileName,
            "fieldsExtracted", fieldsExtracted,
            "fieldsVerified", fieldsVerified
        ));
    }

    public void sendRunStage(UUID caseId, UUID runId, String stage, String description) {
        broadcast(caseId, "run.stage", Map.of(
            "runId", runId.toString(),
            "stage", stage,
            "description", description
        ));
    }

    public void sendRunCompleted(UUID caseId, UUID runId, long durationMs, long costMicros) {
        broadcast(caseId, "run.completed", Map.of(
            "runId", runId.toString(),
            "durationMs", durationMs,
            "costMicros", costMicros
        ));
    }

    public void sendRunFailed(UUID caseId, UUID runId, String errorMessage) {
        broadcast(caseId, "run.failed", Map.of(
            "runId", runId.toString(),
            "error", errorMessage
        ));
    }

    private void broadcast(UUID caseId, String eventName, Object data) {
        List<SseEmitter> emitters = emittersByCaseId.get(caseId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                log.debug("Error delivering SSE event {} to emitter for case {}", eventName, caseId);
                emitters.remove(emitter);
            }
        }
    }
}
