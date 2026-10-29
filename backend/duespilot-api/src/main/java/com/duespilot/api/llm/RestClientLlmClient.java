package com.duespilot.api.llm;

import com.duespilot.api.domain.LlmCallEntity;
import com.duespilot.api.llm.error.CaseBudgetExceededException;
import com.duespilot.api.llm.error.DailyBudgetExceededException;
import com.duespilot.api.repository.LlmCallRepository;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
public class RestClientLlmClient implements LlmClient {

    private final LlmProperties properties;
    private final LlmCallRepository llmCallRepository;
    private final RestClient restClient;
    private final Semaphore inFlightSemaphore;
    private final ObjectMapper objectMapper;
    private final com.duespilot.api.config.DuesPilotProperties duesPilotProperties;

    public RestClientLlmClient(
            LlmProperties properties,
            LlmCallRepository llmCallRepository,
            RestClient.Builder restClientBuilder) {
        this(properties, llmCallRepository, restClientBuilder, new com.duespilot.api.config.DuesPilotProperties());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RestClientLlmClient(
            LlmProperties properties,
            LlmCallRepository llmCallRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RestClient.Builder restClientBuilder,
            com.duespilot.api.config.DuesPilotProperties duesPilotProperties) {
        this.properties = properties;
        this.llmCallRepository = llmCallRepository;
        this.duesPilotProperties = duesPilotProperties;
        this.inFlightSemaphore = new Semaphore(properties.getMaxInflight());
        this.objectMapper = JsonMapper.builder().build();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());

        String baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        RestClient.Builder builder = (restClientBuilder != null ? restClientBuilder : RestClient.builder())
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getApiKey());
        }

        this.restClient = builder.build();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        // Enforce replay mode: never make LLM calls in DEMO_MODE=replay (Rule R-54)
        if (duesPilotProperties != null && duesPilotProperties.isReplayMode()) {
            throw new IllegalStateException("LLM calls are forbidden in DEMO_MODE=replay");
        }

        // Enforce daily cost cap (Rule R-29, R-54)
        long currentDailyCost = llmCallRepository.findTotalCostToday();
        if (currentDailyCost >= properties.getDailyCostCapMicros()) {
            throw new DailyBudgetExceededException(
                "Daily LLM budget cap reached ($" + String.format(java.util.Locale.US, "%.2f", properties.getDailyCostCapMicros() / 1_000_000.0) + "). Live analysis is disabled; replay mode remains active."
            );
        }

        // Enforce per-case cost cap (Rule R-29, R-54)
        if (request.runId() != null) {
            long currentCaseCost = llmCallRepository.findTotalCostByRunId(request.runId());
            if (currentCaseCost >= properties.getPerCaseCostCapMicros()) {
                throw new CaseBudgetExceededException(
                    "Per-case LLM budget cap reached ($" + String.format(java.util.Locale.US, "%.2f", properties.getPerCaseCostCapMicros() / 1_000_000.0) + "). Analysis was stopped to prevent excess spending."
                );
            }
        }

        String promptSha256 = calculatePromptHash(request.messages());
        long startTime = System.currentTimeMillis();

        // Enforce global in-flight concurrency semaphore (Rule R-29)
        boolean acquired = false;
        try {
            acquired = inFlightSemaphore.tryAcquire(properties.getTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                int latency = (int) (System.currentTimeMillis() - startTime);
                recordCall(request, promptSha256, null, null, null, latency, 0L, LlmOutcome.TIMEOUT);
                throw new RuntimeException("Global in-flight semaphore timeout waiting for concurrency slot");
            }

            return executeWithRetry(request, promptSha256, startTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for in-flight semaphore", e);
        } finally {
            if (acquired) {
                inFlightSemaphore.release();
            }
        }
    }

    private LlmResponse executeWithRetry(LlmRequest request, String promptSha256, long startTime) {
        int maxAttempts = Math.max(1, properties.getMaxRetries());
        int attempt = 0;
        Exception lastException = null;

        Map<String, Object> payload = buildPayload(request);

        while (attempt < maxAttempts) {
            attempt++;
            long attemptStart = System.currentTimeMillis();

            try {
                String responseBody = restClient.post()
                    .uri("/chat/completions")
                    .body(payload)
                    .retrieve()
                    .body(String.class);

                int latency = (int) (System.currentTimeMillis() - startTime);
                return parseSuccessResponse(request, promptSha256, responseBody, latency);

            } catch (HttpStatusCodeException ex) {
                lastException = ex;
                int statusCode = ex.getStatusCode().value();
                boolean isRetryable = (statusCode == 429) || (statusCode >= 500 && statusCode < 600);

                if (isRetryable && attempt < maxAttempts) {
                    backoffSleep(attempt);
                    continue;
                }

                int latency = (int) (System.currentTimeMillis() - startTime);
                recordCall(request, promptSha256, null, null, null, latency, 0L, LlmOutcome.HTTP_ERROR);
                throw new RuntimeException("LLM HTTP error " + statusCode + ": " + ex.getResponseBodyAsString(), ex);

            } catch (Exception ex) {
                lastException = ex;
                if (isTimeout(ex)) {
                    if (attempt < maxAttempts) {
                        backoffSleep(attempt);
                        continue;
                    }
                    int latency = (int) (System.currentTimeMillis() - startTime);
                    recordCall(request, promptSha256, null, null, null, latency, 0L, LlmOutcome.TIMEOUT);
                    throw new RuntimeException("LLM call timed out after " + attempt + " attempts: " + ex.getMessage(), ex);
                }

                int latency = (int) (System.currentTimeMillis() - startTime);
                recordCall(request, promptSha256, null, null, null, latency, 0L, LlmOutcome.HTTP_ERROR);
                throw new RuntimeException("LLM call failed: " + ex.getMessage(), ex);
            }
        }

        int latency = (int) (System.currentTimeMillis() - startTime);
        recordCall(request, promptSha256, null, null, null, latency, 0L, LlmOutcome.HTTP_ERROR);
        throw new RuntimeException("LLM call exhausted all " + maxAttempts + " retries", lastException);
    }

    private boolean isTimeout(Throwable t) {
        Throwable curr = t;
        while (curr != null) {
            if (curr instanceof java.net.SocketTimeoutException
                || curr instanceof java.util.concurrent.TimeoutException
                || (curr.getMessage() != null && curr.getMessage().toLowerCase().contains("timed out"))
                || (curr.getMessage() != null && curr.getMessage().toLowerCase().contains("timeout"))) {
                return true;
            }
            curr = curr.getCause();
        }
        return false;
    }

    private LlmResponse parseSuccessResponse(
            LlmRequest request,
            String promptSha256,
            String responseBody,
            int latencyMs) {

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String content = null;
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode messageNode = choices.get(0).get("message");
                if (messageNode != null) {
                    JsonNode contentNode = messageNode.get("content");
                    if (contentNode != null && !contentNode.isNull()) {
                        content = contentNode.asText();
                    }
                }
            }

            int promptTokens = 0;
            int completionTokens = 0;
            int reasoningTokens = 0;

            JsonNode usageNode = root.get("usage");
            if (usageNode != null) {
                if (usageNode.has("prompt_tokens")) {
                    promptTokens = usageNode.get("prompt_tokens").asInt();
                }
                if (usageNode.has("completion_tokens")) {
                    completionTokens = usageNode.get("completion_tokens").asInt();
                }
                if (usageNode.has("reasoning_tokens")) {
                    reasoningTokens = usageNode.get("reasoning_tokens").asInt();
                } else if (usageNode.has("completion_tokens_details")) {
                    JsonNode details = usageNode.get("completion_tokens_details");
                    if (details.has("reasoning_tokens")) {
                        reasoningTokens = details.get("reasoning_tokens").asInt();
                    }
                }
            }

            long costMicros = calculateCost(request.model(), promptTokens, completionTokens);
            LlmOutcome outcome = (content == null || content.isBlank()) ? LlmOutcome.EMPTY_CONTENT : LlmOutcome.OK;

            recordCall(
                request,
                promptSha256,
                promptTokens,
                completionTokens,
                reasoningTokens,
                latencyMs,
                costMicros,
                outcome
            );

            Usage usage = new Usage(promptTokens, completionTokens, reasoningTokens, costMicros);
            return new LlmResponse(request.model(), content, usage, latencyMs, promptSha256, outcome);

        } catch (Exception e) {
            recordCall(request, promptSha256, null, null, null, latencyMs, 0L, LlmOutcome.HTTP_ERROR);
            throw new RuntimeException("Failed to parse LLM response JSON", e);
        }
    }

    private void recordCall(
            LlmRequest request,
            String promptSha256,
            Integer inputTokens,
            Integer outputTokens,
            Integer reasoningTokens,
            int latencyMs,
            long costMicros,
            LlmOutcome outcome) {

        LlmCallEntity entity = new LlmCallEntity(
            UUID.randomUUID(),
            request.runId(),
            request.benchmarkRunId(),
            request.task(),
            request.model(),
            promptSha256,
            inputTokens,
            outputTokens,
            reasoningTokens,
            latencyMs,
            costMicros,
            outcome.name(),
            Instant.now()
        );

        try {
            llmCallRepository.save(entity);
        } catch (Exception e) {
            // Keep going even if DB logging fails so we don't break the client run
        }
    }

    private long calculateCost(String modelId, int inputTokens, int outputTokens) {
        LlmProperties.ModelConfig config = null;
        for (var entry : properties.getModels().values()) {
            if (entry.id().equals(modelId)) {
                config = entry;
                break;
            }
        }
        if (config == null) {
            config = properties.getModel(modelId);
        }

        long inputCost = ((long) inputTokens * config.inputCostPerMillionMicros()) / 1_000_000L;
        long outputCost = ((long) outputTokens * config.outputCostPerMillionMicros()) / 1_000_000L;
        return inputCost + outputCost;
    }

    private Map<String, Object> buildPayload(LlmRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", request.model());

        List<Map<String, String>> messagesList = new ArrayList<>();
        for (Message msg : request.messages()) {
            messagesList.add(Map.of("role", msg.role(), "content", msg.content()));
        }
        payload.put("messages", messagesList);
        payload.put("max_tokens", request.maxTokens());
        payload.put("temperature", request.temperature());

        if (request.responseFormatJsonType() != null) {
            payload.put("response_format", Map.of("type", request.responseFormatJsonType()));
        }

        return payload;
    }

    private void backoffSleep(int attempt) {
        // Exponential backoff with jitter (Rule R-17/architecture §17)
        long baseDelay = (long) Math.min(2000, Math.pow(2, attempt) * 100);
        long jitter = ThreadLocalRandom.current().nextLong(20, 100);
        try {
            Thread.sleep(baseDelay + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static String calculatePromptHash(List<Message> messages) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Message m : messages) {
                if (m.content() != null) {
                    digest.update(m.role().getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) ':');
                    digest.update(m.content().getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
