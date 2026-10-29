package com.duespilot.api.web.dto;

import com.duespilot.api.domain.ContactEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ContactResponse(
    UUID id,
    @JsonProperty("workspace_id") UUID workspaceId,
    @JsonProperty("case_id") @Nullable UUID caseId,
    @JsonProperty("buyer_name") String buyerName,
    @Nullable String email,
    @Nullable String phone,
    @JsonProperty("whatsapp_number") @Nullable String whatsappNumber,
    @JsonProperty("email_opt_in") boolean emailOptIn,
    @JsonProperty("whatsapp_opt_in") boolean whatsappOptIn,
    @JsonProperty("unsubscribed_at") @Nullable Instant unsubscribedAt
) {
    public static ContactResponse from(ContactEntity c) {
        return new ContactResponse(
            c.id(),
            c.workspaceId(),
            c.caseId(),
            c.buyerName(),
            c.email(),
            c.phone(),
            c.whatsappNumber(),
            c.emailOptIn(),
            c.whatsappOptIn(),
            c.unsubscribedAt()
        );
    }
}
