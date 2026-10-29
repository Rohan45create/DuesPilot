package com.duespilot.api.llm;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class DraftActionPolicy {

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final JsonValidator jsonValidator;

    public DraftActionPolicy(
            LlmClient llmClient,
            LlmProperties properties,
            JsonValidator jsonValidator) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.jsonValidator = jsonValidator;
    }

    public ModelRouter.RouterResult execute(List<LlmClient.Message> messages, @Nullable UUID runId) {
        List<String> history = new ArrayList<>();
        var superConfig = properties.getModel("reason");

        history.add("Calling Super for action drafting (" + superConfig.id() + ")");
        var req = LlmClient.LlmRequest.builder()
            .task("draft_action")
            .model(superConfig.id())
            .messages(messages)
            .maxTokens(superConfig.defaultMaxTokens())
            .temperature(0.0)
            .runId(runId)
            .build();

        var res = llmClient.chat(req);
        JsonNode json = res.hasContent() ? jsonValidator.validateAndParse(res.content()) : null;
        return new ModelRouter.RouterResult(superConfig.id(), res.content(), json, json != null, history);
    }
}
