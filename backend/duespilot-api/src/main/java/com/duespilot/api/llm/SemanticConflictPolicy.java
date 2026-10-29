package com.duespilot.api.llm;

import com.duespilot.api.repository.LlmCallRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class SemanticConflictPolicy {

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final LlmCallRepository llmCallRepository;
    private final JsonValidator jsonValidator;

    public SemanticConflictPolicy(
            LlmClient llmClient,
            LlmProperties properties,
            LlmCallRepository llmCallRepository,
            JsonValidator jsonValidator) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.llmCallRepository = llmCallRepository;
        this.jsonValidator = jsonValidator;
    }

    public ModelRouter.RouterResult execute(List<LlmClient.Message> messages, @Nullable UUID runId) {
        List<String> history = new ArrayList<>();
        var superConfig = properties.getModel("reason");
        var ultraConfig = properties.getModel("adjudicate");

        // Step 1: Call Super for whole-case context
        history.add("Calling Super for semantic conflicts (" + superConfig.id() + ")");
        var req1 = LlmClient.LlmRequest.builder()
            .task("semantic_conflicts")
            .model(superConfig.id())
            .messages(messages)
            .maxTokens(superConfig.defaultMaxTokens())
            .temperature(0.0)
            .runId(runId)
            .build();

        var res1 = llmClient.chat(req1);
        JsonNode json1 = res1.hasContent() ? jsonValidator.validateAndParse(res1.content()) : null;

        boolean needsAdjudication = false;
        if (json1 != null && json1.has("needs_adjudication") && json1.get("needs_adjudication").asBoolean()) {
            needsAdjudication = true;
        }

        // If Super succeeded and does not require Ultra adjudication, return result
        if (json1 != null && !needsAdjudication) {
            return new ModelRouter.RouterResult(superConfig.id(), res1.content(), json1, true, history);
        }

        // Step 2: Escalate to Ultra if adjudication is needed
        long ultraCalls = runId != null ? llmCallRepository.countSuccessfulCallsByRunIdAndModel(runId, ultraConfig.id()) : 0L;
        if (ultraCalls < 1L) {
            history.add("Adjudication needed -> Escalating to Ultra (" + ultraConfig.id() + ")");
            var req2 = LlmClient.LlmRequest.builder()
                .task("adjudicate")
                .model(ultraConfig.id())
                .messages(messages)
                .maxTokens(ultraConfig.defaultMaxTokens())
                .temperature(0.0)
                .runId(runId)
                .build();

            var res2 = llmClient.chat(req2);
            JsonNode json2 = res2.hasContent() ? jsonValidator.validateAndParse(res2.content()) : null;
            return new ModelRouter.RouterResult(ultraConfig.id(), res2.content(), json2, json2 != null, history);
        } else {
            history.add("Adjudication needed but Ultra call limit reached (max 1 per case) -> Retaining Super result");
            return new ModelRouter.RouterResult(superConfig.id(), res1.content(), json1, json1 != null, history);
        }
    }
}
