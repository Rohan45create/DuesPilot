package com.duespilot.api.llm;

import com.duespilot.api.domain.DocType;
import com.duespilot.api.repository.LlmCallRepository;
import com.duespilot.core.extract.FieldStatus;
import com.duespilot.core.extract.RawExtraction;
import com.duespilot.core.extract.SpanVerifier;
import com.duespilot.core.extract.VerifiedField;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Extraction router policy implementing architecture.md section 8.2 & 8.4 and rules R-24 to R-28:
 * - Nano with budget B1 (default max tokens)
 * - If schema invalid or any critical field unverified (amount, date, invoice number, party) -> Nano budget B2
 * - If still failing -> Super
 * - If still failing -> Ultra (at most 1 Ultra call per case in demo mode)
 * - If still failing -> mark UNVERIFIED
 */
@Component
public class ExtractionPolicy {

    public record ExtractionPolicyResult(
        String finalModel,
        @Nullable String rawContent,
        List<VerifiedField> verifiedFields,
        boolean isSuccess,
        List<String> escalationHistory
    ) {}

    private record StepEvaluation(
        boolean isValid,
        String failureReason,
        List<VerifiedField> verifiedFields
    ) {}

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final LlmCallRepository llmCallRepository;
    private final JsonValidator jsonValidator;

    public ExtractionPolicy(
            LlmClient llmClient,
            LlmProperties properties,
            LlmCallRepository llmCallRepository,
            JsonValidator jsonValidator) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.llmCallRepository = llmCallRepository;
        this.jsonValidator = jsonValidator;
    }

    public ExtractionPolicyResult executeExtraction(
            List<LlmClient.Message> messages,
            Map<Integer, String> pages,
            DocType docType,
            @Nullable LocalDate referenceDate,
            @Nullable UUID runId) {
        List<String> history = new ArrayList<>();
        var nanoConfig = properties.getModel("extract");
        var superConfig = properties.getModel("reason");
        var ultraConfig = properties.getModel("adjudicate");

        // Step 1: Nano with budget B1
        history.add("Nano B1 (" + nanoConfig.defaultMaxTokens() + " tokens)");
        var req1 = LlmClient.LlmRequest.builder()
            .task("extract")
            .model(nanoConfig.id())
            .messages(messages)
            .maxTokens(nanoConfig.defaultMaxTokens())
            .temperature(0.0)
            .runId(runId)
            .build();

        var res1 = llmClient.chat(req1);
        StepEvaluation eval1 = evaluateResponse(res1, pages, docType, referenceDate);
        if (eval1.isValid()) {
            return new ExtractionPolicyResult(nanoConfig.id(), res1.content(), eval1.verifiedFields(), true, history);
        }

        // Step 2: Nano with expanded budget B2
        history.add("Nano B1 failed (" + eval1.failureReason() + ") -> Escalating to Nano B2 (" + nanoConfig.expandedMaxTokens() + " tokens)");
        var req2 = LlmClient.LlmRequest.builder()
            .task("extract")
            .model(nanoConfig.id())
            .messages(messages)
            .maxTokens(nanoConfig.expandedMaxTokens())
            .temperature(0.0)
            .runId(runId)
            .build();

        var res2 = llmClient.chat(req2);
        StepEvaluation eval2 = evaluateResponse(res2, pages, docType, referenceDate);
        if (eval2.isValid()) {
            return new ExtractionPolicyResult(nanoConfig.id(), res2.content(), eval2.verifiedFields(), true, history);
        }

        // Step 3: Escalate to Super
        history.add("Nano B2 failed (" + eval2.failureReason() + ") -> Escalating to Super (" + superConfig.id() + ")");
        var req3 = LlmClient.LlmRequest.builder()
            .task("extract")
            .model(superConfig.id())
            .messages(messages)
            .maxTokens(superConfig.defaultMaxTokens())
            .temperature(0.0)
            .runId(runId)
            .build();

        var res3 = llmClient.chat(req3);
        StepEvaluation eval3 = evaluateResponse(res3, pages, docType, referenceDate);
        if (eval3.isValid()) {
            return new ExtractionPolicyResult(superConfig.id(), res3.content(), eval3.verifiedFields(), true, history);
        }

        // Step 4: Escalate to Ultra (gated by max 1 Ultra call per case)
        long ultraCalls = runId != null ? llmCallRepository.countSuccessfulCallsByRunIdAndModel(runId, ultraConfig.id()) : 0L;
        if (ultraCalls < 1L) {
            history.add("Super failed (" + eval3.failureReason() + ") -> Escalating to Ultra (" + ultraConfig.id() + ")");
            var req4 = LlmClient.LlmRequest.builder()
                .task("extract")
                .model(ultraConfig.id())
                .messages(messages)
                .maxTokens(ultraConfig.defaultMaxTokens())
                .temperature(0.0)
                .runId(runId)
                .build();

            var res4 = llmClient.chat(req4);
            StepEvaluation eval4 = evaluateResponse(res4, pages, docType, referenceDate);
            if (eval4.isValid()) {
                return new ExtractionPolicyResult(ultraConfig.id(), res4.content(), eval4.verifiedFields(), true, history);
            }
            history.add("Ultra failed (" + eval4.failureReason() + ") -> Marking UNVERIFIED");
            List<VerifiedField> unverifiedList = forceAllUnverified(eval4.verifiedFields());
            return new ExtractionPolicyResult(ultraConfig.id(), res4.content(), unverifiedList, false, history);
        } else {
            history.add("Super failed (" + eval3.failureReason() + ") -> Ultra limit reached (max 1 per case) -> Marking UNVERIFIED");
            List<VerifiedField> unverifiedList = forceAllUnverified(eval3.verifiedFields());
            return new ExtractionPolicyResult(superConfig.id(), res3.content(), unverifiedList, false, history);
        }
    }

    public ModelRouter.RouterResult execute(List<LlmClient.Message> messages, @Nullable UUID runId) {
        List<String> history = new ArrayList<>();
        var nanoConfig = properties.getModel("extract");
        var superConfig = properties.getModel("reason");
        var ultraConfig = properties.getModel("adjudicate");

        // Step 1: Nano B1
        history.add("Nano B1 (" + nanoConfig.defaultMaxTokens() + " tokens)");
        var req1 = LlmClient.LlmRequest.builder()
            .task("extract")
            .model(nanoConfig.id())
            .messages(messages)
            .maxTokens(nanoConfig.defaultMaxTokens())
            .temperature(0.0)
            .runId(runId)
            .build();

        var res1 = llmClient.chat(req1);
        JsonNode json1 = res1.hasContent() ? jsonValidator.validateAndParse(res1.content()) : null;
        if (json1 != null) {
            return new ModelRouter.RouterResult(nanoConfig.id(), res1.content(), json1, true, history);
        }

        // Step 2: Nano B2
        String reason1 = res1.hasContent() ? "INVALID_JSON" : "EMPTY_CONTENT";
        history.add("Nano B1 failed (" + reason1 + ") -> Escalating to Nano B2 (" + nanoConfig.expandedMaxTokens() + " tokens)");
        var req2 = LlmClient.LlmRequest.builder()
            .task("extract")
            .model(nanoConfig.id())
            .messages(messages)
            .maxTokens(nanoConfig.expandedMaxTokens())
            .temperature(0.0)
            .runId(runId)
            .build();

        var res2 = llmClient.chat(req2);
        JsonNode json2 = res2.hasContent() ? jsonValidator.validateAndParse(res2.content()) : null;
        if (json2 != null) {
            return new ModelRouter.RouterResult(nanoConfig.id(), res2.content(), json2, true, history);
        }

        // Step 3: Super
        String reason2 = res2.hasContent() ? "INVALID_JSON" : "EMPTY_CONTENT";
        history.add("Nano B2 failed (" + reason2 + ") -> Escalating to Super (" + superConfig.id() + ")");
        var req3 = LlmClient.LlmRequest.builder()
            .task("extract")
            .model(superConfig.id())
            .messages(messages)
            .maxTokens(superConfig.defaultMaxTokens())
            .temperature(0.0)
            .runId(runId)
            .build();

        var res3 = llmClient.chat(req3);
        JsonNode json3 = res3.hasContent() ? jsonValidator.validateAndParse(res3.content()) : null;
        if (json3 != null) {
            return new ModelRouter.RouterResult(superConfig.id(), res3.content(), json3, true, history);
        }

        // Step 4: Ultra
        String reason3 = res3.hasContent() ? "INVALID_JSON" : "EMPTY_CONTENT";
        long ultraCalls = runId != null ? llmCallRepository.countSuccessfulCallsByRunIdAndModel(runId, ultraConfig.id()) : 0L;
        if (ultraCalls < 1L) {
            history.add("Super failed (" + reason3 + ") -> Escalating to Ultra (" + ultraConfig.id() + ")");
            var req4 = LlmClient.LlmRequest.builder()
                .task("extract")
                .model(ultraConfig.id())
                .messages(messages)
                .maxTokens(ultraConfig.defaultMaxTokens())
                .temperature(0.0)
                .runId(runId)
                .build();

            var res4 = llmClient.chat(req4);
            JsonNode json4 = res4.hasContent() ? jsonValidator.validateAndParse(res4.content()) : null;
            if (json4 != null) {
                return new ModelRouter.RouterResult(ultraConfig.id(), res4.content(), json4, true, history);
            }
            history.add("Ultra failed -> Marking UNVERIFIED");
            return new ModelRouter.RouterResult(ultraConfig.id(), res4.content(), null, false, history);
        } else {
            history.add("Super failed (" + reason3 + ") -> Ultra limit reached (max 1 per case) -> Marking UNVERIFIED");
            return new ModelRouter.RouterResult(superConfig.id(), res3.content(), null, false, history);
        }
    }

    private StepEvaluation evaluateResponse(
            LlmClient.LlmResponse response,
            Map<Integer, String> pages,
            DocType docType,
            @Nullable LocalDate referenceDate) {
        if (!response.hasContent()) {
            return new StepEvaluation(false, "EMPTY_CONTENT", List.of());
        }

        ExtractionResponseDto dto = jsonValidator.parseDto(response.content(), ExtractionResponseDto.class);
        if (dto == null || dto.fields() == null || dto.fields().isEmpty()) {
            return new StepEvaluation(false, "INVALID_JSON", List.of());
        }

        List<RawExtraction> rawList = dto.toRawExtractions();
        List<VerifiedField> verified = SpanVerifier.verifyAll(rawList, pages, referenceDate);

        // Check for any unverified critical fields
        boolean anyCriticalUnverified = verified.stream()
            .filter(f -> SpanVerifier.isCriticalField(f.fieldName()))
            .anyMatch(f -> !f.isVerified());

        if (anyCriticalUnverified) {
            return new StepEvaluation(false, "CRITICAL_FIELD_UNVERIFIED", verified);
        }

        // For document types requiring critical fields, ensure at least one verified critical field is present
        if (requiresCriticalFields(docType)) {
            boolean hasVerifiedCritical = verified.stream()
                .anyMatch(f -> SpanVerifier.isCriticalField(f.fieldName()) && f.isVerified());
            if (!hasVerifiedCritical) {
                return new StepEvaluation(false, "CRITICAL_FIELDS_MISSING", verified);
            }
        }

        return new StepEvaluation(true, null, verified);
    }

    private boolean requiresCriticalFields(DocType docType) {
        return docType == DocType.INVOICE || docType == DocType.PO || docType == DocType.PAYMENT || docType == DocType.CREDIT_NOTE;
    }

    private List<VerifiedField> forceAllUnverified(List<VerifiedField> fields) {
        List<VerifiedField> result = new ArrayList<>(fields.size());
        for (VerifiedField f : fields) {
            if (f.status() == FieldStatus.VERIFIED) {
                result.add(f);
            } else {
                result.add(VerifiedField.unverified(
                    f.fieldName(),
                    f.valueText(),
                    f.pageNo(),
                    f.charStart(),
                    f.charEnd(),
                    f.quote(),
                    f.failureReason() != null ? f.failureReason() : "Escalation exhausted without verification"
                ));
            }
        }
        return result;
    }
}
