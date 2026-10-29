package com.duespilot.api.service.notification;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppClient.class);

    private final RestClient restClient;
    private final boolean apiEnabled;
    private final String phoneNumberId;
    private final String accessToken;
    private final String verifyToken;
    private final String appSecret;

    public record SendWhatsAppResult(
        boolean success,
        @Nullable String providerMessageId,
        @Nullable String error
    ) {}

    public record WhatsAppSetupGuide(
        boolean apiEnabled,
        String mode,
        String webhookUrl,
        List<String> steps,
        List<String> approvedTemplates,
        String disclaimer
    ) {}

    public WhatsAppClient(
            RestClient.Builder restClientBuilder,
            @Value("${duespilot.notifications.whatsapp.base-url:https://graph.facebook.com}") String baseUrl,
            @Value("${duespilot.notifications.whatsapp.api-enabled:false}") boolean apiEnabled,
            @Value("${duespilot.notifications.whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${duespilot.notifications.whatsapp.access-token:}") String accessToken,
            @Value("${duespilot.notifications.whatsapp.verify-token:}") String verifyToken,
            @Value("${duespilot.notifications.whatsapp.app-secret:}") String appSecret) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiEnabled = apiEnabled;
        this.phoneNumberId = phoneNumberId != null ? phoneNumberId.trim() : "";
        this.accessToken = accessToken != null ? accessToken.trim() : "";
        this.verifyToken = verifyToken != null ? verifyToken.trim() : "";
        this.appSecret = appSecret != null ? appSecret.trim() : "";
    }

    public boolean isApiEnabled() {
        return apiEnabled;
    }

    /**
     * Builds wa.me click-to-chat URL with prefilled text (Rule R-72, R-73).
     */
    public String buildClickToChatUrl(String phoneE164, String text) {
        String cleanPhone = phoneE164.replaceAll("[^0-9]", "");
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
        return "https://wa.me/" + cleanPhone + "?text=" + encoded;
    }

    /**
     * Sends a Cloud API template message when apiEnabled is true.
     */
    public SendWhatsAppResult sendTemplateMessage(String recipientPhone, String templateName, Map<String, String> vars) {
        if (!apiEnabled) {
            return new SendWhatsAppResult(false, null, "WhatsApp Cloud API is disabled. Use WHATSAPP_LINK instead.");
        }

        if (accessToken.isBlank() || "mock".equalsIgnoreCase(accessToken)) {
            log.info("[MOCK WHATSAPP API] Sent template '{}' to recipient {}", templateName, recipientPhone);
            return new SendWhatsAppResult(true, "wamid.mock." + UUID.randomUUID(), null);
        }

        try {
            String cleanPhone = recipientPhone.replaceAll("[^0-9]", "");
            Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", cleanPhone,
                "type", "template",
                "template", Map.of(
                    "name", templateName,
                    "language", Map.of("code", "en")
                )
            );

            var response = restClient.post()
                .uri("/v21.0/" + phoneNumberId + "/messages")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

            String messageId = "wamid." + UUID.randomUUID();
            if (response != null && response.containsKey("messages")) {
                Object msgs = response.get("messages");
                if (msgs instanceof List<?> list && !list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof Map<?, ?> m && m.containsKey("id")) {
                        messageId = m.get("id").toString();
                    }
                }
            }

            return new SendWhatsAppResult(true, messageId, null);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message via Cloud API", e);
            return new SendWhatsAppResult(false, null, e.getMessage());
        }
    }

    /**
     * Verifies the GET webhook subscription challenge from Meta.
     */
    public @Nullable String verifyWebhookChallenge(String mode, String token, String challenge) {
        if ("subscribe".equals(mode) && (verifyToken.isBlank() || verifyToken.equals(token))) {
            return challenge;
        }
        return null;
    }

    /**
     * Verifies the X-Hub-Signature-256 header sent on Meta webhooks.
     */
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        if (appSecret.isBlank()) {
            log.warn("WHATSAPP_APP_SECRET is empty; rejecting webhook in secure mode");
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        try {
            String expectedHex = signatureHeader.substring("sha256=".length()).trim();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String calculatedHex = HexFormat.of().formatHex(hash);

            return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                calculatedHex.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error verifying WhatsApp webhook signature", e);
            return false;
        }
    }

    public WhatsAppSetupGuide getSetupGuide() {
        return new WhatsAppSetupGuide(
            apiEnabled,
            apiEnabled ? "WHATSAPP_API" : "WHATSAPP_LINK (Default)",
            "/api/v1/webhooks/whatsapp",
            List.of(
                "Step 1: Sign in to Meta for Developers (developers.facebook.com) and create a Business App.",
                "Step 2: Add WhatsApp product to your app. Meta grants a Test Phone Number and a temporary access token.",
                "Step 3: Under 'To' field in Meta API Setup, add and verify your destination test phone number.",
                "Step 4: Create pre-approved message templates matching the catalogue (delivery_proof_request, payment_allocation, credit_note_adjustment, reconciled_balance_confirmation, confirmed_date_request, thank_you).",
                "Step 5: Configure Webhook callback URL to https://<your-domain>/api/v1/webhooks/whatsapp and set your Verify Token.",
                "Step 6: Subscribe to the 'messages' field under WhatsApp webhook fields.",
                "Note: WHATSAPP_LINK requires ZERO setup and is the recommended default for MSMEs with full human-in-the-loop control."
            ),
            List.copyOf(WhatsAppTemplateCatalogue.ALLOWED_TEMPLATES),
            "Strictly prohibited: Legal notices, statutory penalties, interest demands, and collection threats cannot be sent on WhatsApp per Rule R-73."
        );
    }
}
