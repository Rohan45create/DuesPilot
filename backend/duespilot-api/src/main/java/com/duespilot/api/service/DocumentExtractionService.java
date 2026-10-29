package com.duespilot.api.service;

import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.ExtractedFieldEntity;
import com.duespilot.api.llm.ExtractionPolicy;
import com.duespilot.api.llm.ModelRouter;
import com.duespilot.api.repository.ExtractedFieldRepository;
import com.duespilot.core.extract.VerifiedField;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service orchestrating document field extraction, span verification, and persistence.
 *
 * Implements architecture.md sections 8.4 and 9:
 * - Dispatches document pages to model router with prompt templates.
 * - Enforces span verification and critical field escalation ladder.
 * - Persists verified and unverified fields to 'extracted_fields' with the final model used.
 */
@Service
public class DocumentExtractionService {

    private final ModelRouter modelRouter;
    private final ExtractedFieldRepository extractedFieldRepository;

    public DocumentExtractionService(
            ModelRouter modelRouter,
            ExtractedFieldRepository extractedFieldRepository) {
        this.modelRouter = modelRouter;
        this.extractedFieldRepository = extractedFieldRepository;
    }

    public List<ExtractedFieldEntity> extractAndPersist(
            UUID runId,
            DocumentEntity document,
            Map<Integer, String> pages,
            @Nullable LocalDate referenceDate) {
        ExtractionPolicy.ExtractionPolicyResult result = modelRouter.extractDocument(
            document.docType(),
            document.fileName(),
            pages,
            referenceDate,
            runId
        );

        List<ExtractedFieldEntity> entities = new ArrayList<>(result.verifiedFields().size());
        for (VerifiedField vf : result.verifiedFields()) {
            Long paise = vf.valuePaise().isValue() ? vf.valuePaise().getOrThrow().value() : null;
            LocalDate date = vf.valueDate().isValue() ? vf.valueDate().getOrThrow() : null;

            entities.add(new ExtractedFieldEntity(
                UUID.randomUUID(),
                document.id(),
                runId,
                vf.fieldName(),
                vf.valueText(),
                paise,
                date,
                vf.status(),
                vf.pageNo(),
                vf.charStart(),
                vf.charEnd(),
                vf.quote(),
                result.finalModel()
            ));
        }

        return extractedFieldRepository.saveAll(entities);
    }
}
