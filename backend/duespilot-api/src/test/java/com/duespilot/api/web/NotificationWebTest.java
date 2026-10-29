package com.duespilot.api.web;

import com.duespilot.api.config.SecurityHeadersFilter;
import com.duespilot.api.domain.ContactEntity;
import com.duespilot.api.domain.NotificationOutboxEntity;
import com.duespilot.api.service.notification.NotificationService;
import com.duespilot.api.service.notification.WhatsAppClient;
import com.duespilot.api.web.error.ApiExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {NotificationController.class, WebhookController.class})
@Import({ApiExceptionHandler.class, SecurityHeadersFilter.class})
@DisplayName("NotificationController & WebhookController Web Tests")
class NotificationWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private WhatsAppClient whatsAppClient;

    @Test
    @DisplayName("POST /api/v1/cases/{caseId}/notifications/send: dispatches notification successfully")
    void testSendNotificationEndpoint() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        NotificationOutboxEntity outbox = new NotificationOutboxEntity(
            outboxId, workspaceId, caseId, actionId, "EMAIL", "buyer@example.com",
            "delivery_proof_request", "Please send delivery challan", "SENT",
            "resend_123", null, null, Instant.now(), "key-1", Instant.now()
        );

        when(notificationService.sendNotification(eq(caseId), any())).thenReturn(
            new NotificationService.NotificationResult(outbox, null, false, "buyer@example.com")
        );

        String payload = """
            {
              "action_id": "%s",
              "channel": "EMAIL",
              "recipient": "buyer@example.com",
              "template_key": "delivery_proof_request",
              "include_statement_pdf": true
            }
            """.formatted(actionId);

        mockMvc.perform(post("/api/v1/cases/{caseId}/notifications/send", caseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(outboxId.toString()))
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.channel").value("EMAIL"))
            .andExpect(jsonPath("$.provider_message_id").value("resend_123"));
    }

    @Test
    @DisplayName("GET /api/v1/cases/{caseId}/notifications: lists communications per case")
    void testListNotificationsEndpoint() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        NotificationOutboxEntity outbox = new NotificationOutboxEntity(
            outboxId, workspaceId, caseId, null, "WHATSAPP_LINK", "+919876543210",
            "delivery_proof_request", "Body", "MANUAL_PENDING",
            null, null, null, null, "key-2", Instant.now()
        );

        when(notificationService.listNotifications(caseId)).thenReturn(List.of(outbox));

        mockMvc.perform(get("/api/v1/cases/{caseId}/notifications", caseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(outboxId.toString()))
            .andExpect(jsonPath("$[0].status").value("MANUAL_PENDING"))
            .andExpect(jsonPath("$[0].channel").value("WHATSAPP_LINK"));
    }

    @Test
    @DisplayName("POST /api/v1/cases/{caseId}/notifications/{id}/mark-manual-sent: transitions to MANUAL_SENT")
    void testMarkManualSentEndpoint() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        NotificationOutboxEntity outbox = new NotificationOutboxEntity(
            outboxId, workspaceId, caseId, null, "WHATSAPP_LINK", "+919876543210",
            "delivery_proof_request", "Body", "MANUAL_SENT",
            null, null, null, Instant.now(), "key-2", Instant.now()
        );

        when(notificationService.markManualSent(caseId, outboxId)).thenReturn(outbox);

        mockMvc.perform(post("/api/v1/cases/{caseId}/notifications/{id}/mark-manual-sent", caseId, outboxId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("MANUAL_SENT"));
    }

    @Test
    @DisplayName("POST /api/v1/cases/{caseId}/contacts/unsubscribe: marks contact unsubscribed")
    void testUnsubscribeContactEndpoint() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        ContactEntity contact = new ContactEntity(
            contactId, workspaceId, caseId, "Buyer Corp", "buyer@example.com",
            "+919876543210", "+919876543210", false, "MANUAL", Instant.now(),
            false, "MANUAL", Instant.now(), Instant.now(), Instant.now(), Instant.now()
        );

        when(notificationService.unsubscribe(eq(caseId), any())).thenReturn(contact);

        mockMvc.perform(post("/api/v1/cases/{caseId}/contacts/unsubscribe", caseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Requested by buyer\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(contactId.toString()))
            .andExpect(jsonPath("$.email_opt_in").value(false))
            .andExpect(jsonPath("$.whatsapp_opt_in").value(false))
            .andExpect(jsonPath("$.unsubscribed_at").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/whatsapp/setup-guide: returns test number guide and templates")
    void testGetWhatsAppSetupGuide() throws Exception {
        when(whatsAppClient.getSetupGuide()).thenReturn(new WhatsAppClient.WhatsAppSetupGuide(
            false,
            "WHATSAPP_LINK (Default)",
            "/api/v1/webhooks/whatsapp",
            List.of("Step 1: Set up Meta App"),
            List.of("delivery_proof_request"),
            "Strictly prohibited: Legal notices"
        ));

        mockMvc.perform(get("/api/v1/notifications/whatsapp/setup-guide"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apiEnabled").value(false))
            .andExpect(jsonPath("$.mode").value("WHATSAPP_LINK (Default)"))
            .andExpect(jsonPath("$.approvedTemplates[0]").value("delivery_proof_request"));
    }

    @Test
    @DisplayName("GET /api/v1/webhooks/whatsapp: Meta webhook challenge verification")
    void testWhatsAppChallengeVerification() throws Exception {
        when(whatsAppClient.verifyWebhookChallenge("subscribe", "mytoken", "challenge12345"))
            .thenReturn("challenge12345");

        mockMvc.perform(get("/api/v1/webhooks/whatsapp")
                .param("hub.mode", "subscribe")
                .param("hub.verify_token", "mytoken")
                .param("hub.challenge", "challenge12345"))
            .andExpect(status().isOk())
            .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).isEqualTo("challenge12345"));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/resend: processes incoming Resend events")
    void testResendWebhookEndpoint() throws Exception {
        String payload = "{\"type\":\"email.delivered\",\"data\":{\"email_id\":\"msg-99\"}}";

        mockMvc.perform(post("/api/v1/webhooks/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .header("svix-id", "msg-99")
                .header("svix-timestamp", "1729900000")
                .header("svix-signature", "v1,signature")
                .content(payload))
            .andExpect(status().isOk());

        verify(notificationService).processResendWebhook(eq(payload), eq("msg-99"), eq("1729900000"), eq("v1,signature"));
    }
}
