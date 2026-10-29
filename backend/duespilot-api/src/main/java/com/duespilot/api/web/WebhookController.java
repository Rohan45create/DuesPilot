package com.duespilot.api.web;

import com.duespilot.api.service.notification.NotificationService;
import com.duespilot.api.service.notification.WhatsAppClient;
import com.duespilot.api.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final NotificationService notificationService;
    private final WhatsAppClient whatsAppClient;

    public WebhookController(NotificationService notificationService, WhatsAppClient whatsAppClient) {
        this.notificationService = notificationService;
        this.whatsAppClient = whatsAppClient;
    }

    @PostMapping(value = "/resend", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleResendWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "svix-id", required = false) String svixId,
            @RequestHeader(value = "svix-timestamp", required = false) String svixTimestamp,
            @RequestHeader(value = "svix-signature", required = false) String svixSignature) {
        if (svixId == null || svixTimestamp == null || svixSignature == null) {
            log.warn("Missing Svix signature headers in Resend webhook request");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "Missing Svix signature headers");
        }

        notificationService.processResendWebhook(rawPayload, svixId, svixTimestamp, svixSignature);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/whatsapp", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyWhatsAppWebhookChallenge(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {
        String verifiedChallenge = whatsAppClient.verifyWebhookChallenge(mode, token, challenge);
        if (verifiedChallenge != null) {
            return ResponseEntity.ok(verifiedChallenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification token mismatch");
    }

    @PostMapping(value = "/whatsapp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleWhatsAppWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader) {
        if (signatureHeader == null) {
            log.warn("Missing X-Hub-Signature-256 in WhatsApp webhook request");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "Missing X-Hub-Signature-256 header");
        }

        notificationService.processWhatsAppWebhook(rawPayload, signatureHeader);
        return ResponseEntity.ok().build();
    }
}
