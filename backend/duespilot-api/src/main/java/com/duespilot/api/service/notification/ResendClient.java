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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ResendClient {

    private static final Logger log = LoggerFactory.getLogger(ResendClient.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String fromEmail;
    private final String webhookSecret;

    public record SendEmailRequest(
        String recipient,
        String replyTo,
        String subject,
        String textBody,
        String idempotencyKey,
        @Nullable byte[] pdfAttachment,
        @Nullable String attachmentFilename
    ) {}

    public record SendEmailResult(
        boolean success,
        @Nullable String messageId,
        @Nullable String error
    ) {}

    public ResendClient(
            RestClient.Builder restClientBuilder,
            @Value("${duespilot.notifications.resend.base-url:https://api.resend.com}") String baseUrl,
            @Value("${duespilot.notifications.resend.api-key:${RESEND_API_KEY:}}") String apiKey,
            @Value("${duespilot.notifications.resend.from-email:${RESEND_FROM_EMAIL:onboarding@resend.dev}}") String fromEmail,
            @Value("${duespilot.notifications.resend.webhook-secret:${RESEND_WEBHOOK_SECRET:}}") String webhookSecret) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.fromEmail = fromEmail != null ? fromEmail.trim() : "onboarding@resend.dev";
        this.webhookSecret = webhookSecret != null ? webhookSecret.trim() : "";
    }

    public SendEmailResult sendEmail(SendEmailRequest request) {
        if (apiKey.isBlank() || "mock".equalsIgnoreCase(apiKey)) {
            log.info("[MOCK RESEND] Sending email to {} with subject '{}'", request.recipient(), request.subject());
            return new SendEmailResult(true, "resend_mock_" + UUID.randomUUID(), null);
        }

        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("from", fromEmail);
            body.put("to", List.of(request.recipient()));
            if (request.replyTo() != null && !request.replyTo().isBlank()) {
                body.put("reply_to", request.replyTo());
            }
            body.put("subject", request.subject());
            body.put("text", request.textBody());

            if (request.pdfAttachment() != null && request.pdfAttachment().length > 0) {
                String filename = request.attachmentFilename() != null ? request.attachmentFilename() : "statement.pdf";
                String base64Content = Base64.getEncoder().encodeToString(request.pdfAttachment());
                body.put("attachments", List.of(Map.of(
                    "filename", filename,
                    "content", base64Content
                )));
            }

            var response = restClient.post()
                .uri("/emails")
                .header("Authorization", "Bearer " + apiKey)
                .header("Idempotency-Key", request.idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

            String messageId = response != null && response.get("id") != null
                ? response.get("id").toString()
                : "resend_" + UUID.randomUUID();

            return new SendEmailResult(true, messageId, null);
        } catch (Exception e) {
            log.error("Failed to send email via Resend API", e);
            return new SendEmailResult(false, null, e.getMessage());
        }
    }

    /**
     * Verifies Svix webhook signatures sent by Resend (svix-id, svix-timestamp, svix-signature).
     */
    public boolean verifyWebhookSignature(String payload, String svixId, String svixTimestamp, String svixSignature) {
        if (webhookSecret.isBlank()) {
            log.warn("RESEND_WEBHOOK_SECRET is empty; rejecting webhook in secure mode");
            return false;
        }
        if (svixId == null || svixTimestamp == null || svixSignature == null) {
            return false;
        }

        try {
            // Check timestamp is not older than 5 minutes
            long timestamp = Long.parseLong(svixTimestamp);
            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - timestamp) > 300) {
                log.warn("Svix webhook timestamp expired: {} vs now {}", timestamp, now);
                return false;
            }

            String toSign = svixId + "." + svixTimestamp + "." + payload;
            String secretPart = webhookSecret.startsWith("whsec_")
                ? webhookSecret.substring("whsec_".length())
                : webhookSecret;

            byte[] keyBytes = Base64.getDecoder().decode(secretPart);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] signatureBytes = mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(signatureBytes);

            // svix-signature header format: "v1,<sig1> v1,<sig2>"
            String[] parts = svixSignature.split(" ");
            for (String part : parts) {
                String[] vAndSig = part.split(",");
                if (vAndSig.length == 2 && "v1".equals(vAndSig[0])) {
                    if (MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), vAndSig[1].getBytes(StandardCharsets.UTF_8))) {
                        return true;
                    }
                }
            }

            log.warn("Svix webhook signature mismatch");
            return false;
        } catch (Exception e) {
            log.error("Error verifying Svix webhook signature", e);
            return false;
        }
    }
}
