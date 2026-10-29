package com.duespilot.api.llm;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Component
public class JsonValidator {

    private final ObjectMapper objectMapper;

    public JsonValidator() {
        this.objectMapper = JsonMapper.builder().build();
    }

    public @Nullable JsonNode validateAndParse(@Nullable String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String cleaned = cleanMarkdownCodeBlocks(content);
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    public <T> @Nullable T parseDto(@Nullable String content, Class<T> clazz) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String cleaned = cleanMarkdownCodeBlocks(content);
        try {
            return objectMapper.readValue(cleaned, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    public @Nullable JsonNode repairAndParse(String invalidContent, LlmClient llmClient, String model) {
        String repairPrompt = """
            You are a JSON syntax repair agent.
            The following text was supposed to be valid JSON, but has formatting or syntax errors.
            Fix the JSON so that it is 100% syntactically valid JSON.
            Return ONLY the raw JSON with no explanation and no markdown fences.
            
            Invalid text:
            """ + invalidContent;

        try {
            var repairRequest = LlmClient.LlmRequest.builder()
                .task("repair_json")
                .model(model)
                .messages(List.of(
                    LlmClient.Message.system("You are a strict JSON repair utility. Return valid JSON only."),
                    LlmClient.Message.user(repairPrompt)
                ))
                .maxTokens(2048)
                .temperature(0.0)
                .responseFormatJsonType("json_object")
                .build();

            var repairResponse = llmClient.chat(repairRequest);
            if (repairResponse.hasContent()) {
                String cleaned = cleanMarkdownCodeBlocks(repairResponse.content());
                return objectMapper.readTree(cleaned);
            }
        } catch (Exception e) {
            // Repair failed, fail closed
        }
        return null;
    }

    public static String cleanMarkdownCodeBlocks(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
