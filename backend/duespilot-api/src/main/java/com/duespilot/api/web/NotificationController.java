package com.duespilot.api.web;

import com.duespilot.api.domain.ContactEntity;
import com.duespilot.api.domain.NotificationOutboxEntity;
import com.duespilot.api.service.notification.NotificationService;
import com.duespilot.api.service.notification.WhatsAppClient;
import com.duespilot.api.web.dto.ContactResponse;
import com.duespilot.api.web.dto.NotificationOutboxResponse;
import com.duespilot.api.web.dto.SendNotificationRequest;
import com.duespilot.api.web.dto.UnsubscribeRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService notificationService;
    private final WhatsAppClient whatsAppClient;

    public NotificationController(NotificationService notificationService, WhatsAppClient whatsAppClient) {
        this.notificationService = notificationService;
        this.whatsAppClient = whatsAppClient;
    }

    @PostMapping("/cases/{caseId}/notifications/send")
    public ResponseEntity<NotificationOutboxResponse> sendNotification(
            @PathVariable UUID caseId,
            @RequestBody SendNotificationRequest request) {
        NotificationService.NotificationResult result = notificationService.sendNotification(
            caseId,
            new NotificationService.SendNotificationCommand(
                request.actionId(),
                request.channel(),
                request.recipient(),
                request.templateKey(),
                request.body(),
                request.templateVariables(),
                request.includeStatementPdf(),
                request.subject()
            )
        );
        return ResponseEntity.ok(NotificationOutboxResponse.from(result));
    }

    @GetMapping("/cases/{caseId}/notifications")
    public ResponseEntity<List<NotificationOutboxResponse>> listNotifications(@PathVariable UUID caseId) {
        List<NotificationOutboxEntity> list = notificationService.listNotifications(caseId);
        List<NotificationOutboxResponse> dtos = list.stream()
            .map(NotificationOutboxResponse::from)
            .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/cases/{caseId}/notifications/{id}/resend")
    public ResponseEntity<NotificationOutboxResponse> resendNotification(
            @PathVariable UUID caseId,
            @PathVariable UUID id) {
        NotificationOutboxEntity updated = notificationService.resendNotification(caseId, id);
        return ResponseEntity.ok(NotificationOutboxResponse.from(updated));
    }

    @PostMapping("/cases/{caseId}/notifications/{id}/cancel")
    public ResponseEntity<NotificationOutboxResponse> cancelNotification(
            @PathVariable UUID caseId,
            @PathVariable UUID id) {
        NotificationOutboxEntity cancelled = notificationService.cancelQueued(caseId, id);
        return ResponseEntity.ok(NotificationOutboxResponse.from(cancelled));
    }

    @PostMapping("/cases/{caseId}/notifications/{id}/mark-manual-sent")
    public ResponseEntity<NotificationOutboxResponse> markManualSent(
            @PathVariable UUID caseId,
            @PathVariable UUID id) {
        NotificationOutboxEntity marked = notificationService.markManualSent(caseId, id);
        return ResponseEntity.ok(NotificationOutboxResponse.from(marked));
    }

    @PostMapping("/cases/{caseId}/contacts/unsubscribe")
    public ResponseEntity<ContactResponse> unsubscribeContact(
            @PathVariable UUID caseId,
            @RequestBody(required = false) UnsubscribeRequest request) {
        String reason = request != null ? request.reason() : null;
        ContactEntity updated = notificationService.unsubscribe(caseId, reason);
        return ResponseEntity.ok(ContactResponse.from(updated));
    }

    @GetMapping("/notifications/whatsapp/setup-guide")
    public ResponseEntity<WhatsAppClient.WhatsAppSetupGuide> getWhatsAppSetupGuide() {
        return ResponseEntity.ok(whatsAppClient.getSetupGuide());
    }
}
