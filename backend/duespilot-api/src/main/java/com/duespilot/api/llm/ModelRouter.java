package com.duespilot.api.llm;

import com.duespilot.api.domain.DocType;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ModelRouter {

    public record RouterResult(
        String finalModel,
        @Nullable String rawContent,
        @Nullable JsonNode parsedJson,
        boolean isSuccess,
        List<String> escalationHistory
    ) {}

    private final ExtractionPolicy extractionPolicy;
    private final SemanticConflictPolicy semanticConflictPolicy;
    private final DraftActionPolicy draftActionPolicy;

    public ModelRouter(
            ExtractionPolicy extractionPolicy,
            SemanticConflictPolicy semanticConflictPolicy,
            DraftActionPolicy draftActionPolicy) {
        this.extractionPolicy = extractionPolicy;
        this.semanticConflictPolicy = semanticConflictPolicy;
        this.draftActionPolicy = draftActionPolicy;
    }

    public RouterResult extract(List<LlmClient.Message> messages, @Nullable UUID runId) {
        return extractionPolicy.execute(messages, runId);
    }

    public ExtractionPolicy.ExtractionPolicyResult extractDocument(
            DocType docType,
            String fileName,
            Map<Integer, String> pages,
            @Nullable LocalDate referenceDate,
            @Nullable UUID runId) {
        var messages = ExtractionPromptTemplate.buildPrompt(docType, fileName, pages);
        return extractionPolicy.executeExtraction(messages, pages, docType, referenceDate, runId);
    }

    public RouterResult analyzeSemanticConflicts(List<LlmClient.Message> messages, @Nullable UUID runId) {
        return semanticConflictPolicy.execute(messages, runId);
    }

    public RouterResult draftAction(List<LlmClient.Message> messages, @Nullable UUID runId) {
        return draftActionPolicy.execute(messages, runId);
    }
}
