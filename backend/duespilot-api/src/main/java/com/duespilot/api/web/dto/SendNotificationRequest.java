package com.duespilot.api.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public record SendNotificationRequest(
    @JsonProperty("action_id") @Nullable UUID actionId,
    @JsonProperty("channel") String channel,
    @JsonProperty("recipient") @Nullable String recipient,
    @JsonProperty("template_key") @Nullable String templateKey,
    @JsonProperty("body") @Nullable String body,
    @JsonProperty("template_variables") @Nullable Map<String, String> templateVariables,
    @JsonProperty("include_statement_pdf") boolean includeStatementPdf,
    @JsonProperty("subject") @Nullable String subject
) {}
