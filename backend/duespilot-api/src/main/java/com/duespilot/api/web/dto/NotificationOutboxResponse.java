package com.duespilot.api.web.dto;

import com.duespilot.api.domain.NotificationOutboxEntity;
import com.duespilot.api.service.notification.NotificationService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record NotificationOutboxResponse(
    UUID id,
    @JsonProperty("workspace_id") UUID workspaceId,
    @JsonProperty("case_id") UUID caseId,
    @JsonProperty("action_id") @Nullable UUID actionId,
    String channel,
    String recipient,
    @JsonProperty("template_key") String templateKey,
    @JsonProperty("rendered_body") String renderedBody,
    String status,
    @JsonProperty("provider_message_id") @Nullable String providerMessageId,
    @Nullable String error,
    @JsonProperty("scheduled_for") @Nullable Instant scheduledFor,
    @JsonProperty("sent_at") @Nullable Instant sentAt,
    @JsonProperty("idempotency_key") @Nullable String idempotencyKey,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("click_to_chat_url") @Nullable String clickToChatUrl,
    @JsonProperty("rerouted_to_sandbox") boolean reroutedToSandbox,
    @JsonProperty("original_recipient") @Nullable String originalRecipient
) {
    public static NotificationOutboxResponse from(NotificationService.NotificationResult result) {
        NotificationOutboxEntity o = result.outbox();
        return new NotificationOutboxResponse(
            o.id(),
            o.workspaceId(),
            o.caseId(),
            o.actionId(),
            o.channel(),
            o.recipient(),
            o.templateKey(),
            o.renderedBody(),
            o.status(),
            o.providerMessageId(),
            o.error(),
            o.scheduledFor(),
            o.sentAt(),
            o.idempotencyKey(),
            o.createdAt(),
            result.clickToChatUrl(),
            result.reroutedToSandbox(),
            result.originalRecipient()
        );
    }

    public static NotificationOutboxResponse from(NotificationOutboxEntity o) {
        return new NotificationOutboxResponse(
            o.id(),
            o.workspaceId(),
            o.caseId(),
            o.actionId(),
            o.channel(),
            o.recipient(),
            o.templateKey(),
            o.renderedBody(),
            o.status(),
            o.providerMessageId(),
            o.error(),
            o.scheduledFor(),
            o.sentAt(),
            o.idempotencyKey(),
            o.createdAt(),
            null,
            false,
            null
        );
    }
}
