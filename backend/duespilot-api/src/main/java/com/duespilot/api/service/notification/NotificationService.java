package com.duespilot.api.service.notification;

import com.duespilot.api.domain.AuditEventEntity;
import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.ContactEntity;
import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.NotificationOutboxEntity;
import com.duespilot.api.domain.ProposedActionEntity;
import com.duespilot.api.domain.WorkspaceEntity;
import com.duespilot.api.repository.AuditEventRepository;
import com.duespilot.api.repository.CaseRepository;
import com.duespilot.api.repository.ContactRepository;
import com.duespilot.api.repository.DocumentRepository;
import com.duespilot.api.repository.NotificationOutboxRepository;
import com.duespilot.api.repository.ProposedActionRepository;
import com.duespilot.api.repository.WorkspaceRepository;
import com.duespilot.api.service.CurrentUserService;
import com.duespilot.api.web.error.ApiException;
import com.duespilot.api.web.error.CaseNotFoundException;
import com.duespilot.api.web.error.DemoReadOnlyException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationOutboxRepository outboxRepository;
    private final ContactRepository contactRepository;
    private final CaseRepository caseRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProposedActionRepository proposedActionRepository;
    private final DocumentRepository documentRepository;
    private final AuditEventRepository auditEventRepository;
    private final ResendClient resendClient;
    private final WhatsAppClient whatsAppClient;
    private final StatementOfAccountPdfService pdfService;
    private final SendingWindowChecker sendingWindowChecker;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    private final boolean notifySandbox;
    private final String sandboxEmail;
    private final String sandboxPhone;

    public NotificationService(
            NotificationOutboxRepository outboxRepository,
            ContactRepository contactRepository,
            CaseRepository caseRepository,
            WorkspaceRepository workspaceRepository,
            ProposedActionRepository proposedActionRepository,
            DocumentRepository documentRepository,
            AuditEventRepository auditEventRepository,
            ResendClient resendClient,
            WhatsAppClient whatsAppClient,
            StatementOfAccountPdfService pdfService,
            SendingWindowChecker sendingWindowChecker,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper,
            @Value("${duespilot.notifications.sandbox:${NOTIFY_SANDBOX:false}}") boolean notifySandbox,
            @Value("${duespilot.notifications.sandbox-email:${NOTIFY_SANDBOX_EMAIL:owner@duespilot.local}}") String sandboxEmail,
            @Value("${duespilot.notifications.sandbox-phone:${NOTIFY_SANDBOX_PHONE:+919999999999}}") String sandboxPhone) {
        this.outboxRepository = outboxRepository;
        this.contactRepository = contactRepository;
        this.caseRepository = caseRepository;
        this.workspaceRepository = workspaceRepository;
        this.proposedActionRepository = proposedActionRepository;
        this.documentRepository = documentRepository;
        this.auditEventRepository = auditEventRepository;
        this.resendClient = resendClient;
        this.whatsAppClient = whatsAppClient;
        this.pdfService = pdfService;
        this.sendingWindowChecker = sendingWindowChecker;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
        this.notifySandbox = notifySandbox;
        this.sandboxEmail = sandboxEmail;
        this.sandboxPhone = sandboxPhone;
    }

    public record SendNotificationCommand(
        @Nullable UUID actionId,
        String channel, // EMAIL, WHATSAPP_LINK, WHATSAPP_API
        @Nullable String recipient,
        @Nullable String templateKey,
        @Nullable String body,
        @Nullable Map<String, String> templateVariables,
        boolean includeStatementPdf,
        @Nullable String subject
    ) {}

    public record NotificationResult(
        NotificationOutboxEntity outbox,
        @Nullable String clickToChatUrl,
        boolean reroutedToSandbox,
        @Nullable String originalRecipient
    ) {}

    @Transactional
    public NotificationResult sendNotification(UUID caseId, SendNotificationCommand command) {
        // 1. Authenticate user & Guardrail: Demo users cannot send notifications (R-72)
        CurrentUserService.UserContext userContext = currentUserService.getCurrentUser();
        if (userContext.isAnonymous() || CurrentUserService.DEMO_WORKSPACE_ID.equals(userContext.workspaceId())) {
            throw new DemoReadOnlyException();
        }

        WorkspaceEntity workspace = workspaceRepository.findById(userContext.workspaceId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WORKSPACE_NOT_FOUND", "Workspace not found"));
        if (workspace.isDemo()) {
            throw new DemoReadOnlyException();
        }

        // 2. Verify Case exists and belongs to current workspace
        CaseEntity caseEntity = caseRepository.findById(caseId)
            .orElseThrow(() -> new CaseNotFoundException(caseId));
        if (caseEntity.workspaceId() != null && !caseEntity.workspaceId().equals(workspace.id()) && !caseEntity.isDemoData()) {
            throw new CaseNotFoundException(caseId);
        }

        // 3. Guardrail: Only APPROVED actions can be sent (Rule R-72)
        if (command.actionId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACTION_REQUIRED", "Action ID is required; only approved actions can be sent (Rule R-72)");
        }
        ProposedActionEntity action = proposedActionRepository.findById(command.actionId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACTION_NOT_FOUND", "Proposed action not found"));
        if (!"APPROVED".equalsIgnoreCase(action.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACTION_NOT_APPROVED", "Only approved actions can be sent (Rule R-72). Current status: " + action.status());
        }

        String channel = command.channel().toUpperCase();
        if (!List.of("EMAIL", "WHATSAPP_LINK", "WHATSAPP_API").contains(channel)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHANNEL", "Channel must be EMAIL, WHATSAPP_LINK, or WHATSAPP_API");
        }

        // 4. Contact & Opt-in Guardrails (Rule R-74)
        Optional<ContactEntity> contactOpt = contactRepository.findByWorkspaceAndCaseId(workspace.id(), caseId);
        ContactEntity contact = contactOpt.orElse(null);

        if (contact != null && contact.unsubscribedAt() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPTED_OUT", "Recipient has unsubscribed from all communications (Rule R-74)");
        }

        if ("EMAIL".equals(channel) && contact != null && !contact.emailOptIn()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPTED_OUT", "Recipient has not opted into Email notifications (Rule R-74)");
        }

        if (channel.startsWith("WHATSAPP") && contact != null && !contact.whatsappOptIn()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPTED_OUT", "Recipient has not opted into WhatsApp notifications (Rule R-74)");
        }

        // Determine destination recipient
        String targetRecipient = command.recipient();
        if (targetRecipient == null || targetRecipient.isBlank()) {
            if ("EMAIL".equals(channel)) {
                targetRecipient = contact != null ? contact.email() : null;
            } else {
                targetRecipient = contact != null ? contact.whatsappNumber() : null;
            }
        }
        if (targetRecipient == null || targetRecipient.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RECIPIENT_REQUIRED", "Recipient address or phone number is required");
        }

        // Determine Template Key and Content
        String templateKey = command.templateKey() != null ? command.templateKey() : action.actionType().toLowerCase();
        Map<String, String> vars = command.templateVariables() != null ? command.templateVariables() : Map.of();

        String rawBody = command.body();
        if (rawBody == null || rawBody.isBlank()) {
            if (channel.startsWith("WHATSAPP")) {
                rawBody = WhatsAppTemplateCatalogue.render(templateKey, vars);
            } else {
                rawBody = action.draftText();
            }
        }

        // 5. WhatsApp Catalogue Guardrails (Rule R-73)
        if (channel.startsWith("WHATSAPP")) {
            if (!WhatsAppTemplateCatalogue.isAllowedTemplate(templateKey)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TEMPLATE_NOT_ALLOWED",
                    "Template key '" + templateKey + "' is not part of the pre-approved WhatsApp catalogue (Rule R-73)");
            }
            if (WhatsAppTemplateCatalogue.containsProhibitedContent(rawBody)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PROHIBITED_WHATSAPP_CONTENT",
                    "WhatsApp messages cannot contain legal notices, interest demands, statutory penalties, or threats (Rule R-73)");
            }
        }

        // 6. Guardrail: 48-Hour Cool-Down per case and channel (Rule R-75)
        Instant coolDownThreshold = Instant.now().minus(48, ChronoUnit.HOURS);
        List<NotificationOutboxEntity> recentDispatches = outboxRepository.findRecentSentByCaseAndChannel(caseId, channel, coolDownThreshold);
        if (!recentDispatches.isEmpty()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "COOL_DOWN_ACTIVE",
                "48-hour cool-down active for this case and channel (Rule R-75). Previous notification was sent at " + recentDispatches.get(0).createdAt());
        }

        // 7. Guardrail: Max 3 follow-ups per case (Rule R-75)
        int priorSentCount = outboxRepository.countSentByCase(caseId);
        if (priorSentCount >= 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MAX_FOLLOW_UPS_EXCEEDED",
                "Maximum 3 follow-ups per case reached (Rule R-75). Current count: " + priorSentCount);
        }

        // 8. Guardrail: Sensitive Data Masking (Rule R-77)
        String maskedBody = SensitiveDataMasker.mask(rawBody);

        // 9. Guardrail: Sandbox Rerouting (Rule R-76)
        boolean isRerouted = false;
        String originalRecipient = targetRecipient;
        String dispatchRecipient = targetRecipient;

        if (notifySandbox) {
            isRerouted = true;
            if ("EMAIL".equals(channel)) {
                dispatchRecipient = (userContext.email() != null && !userContext.email().isBlank())
                    ? userContext.email()
                    : sandboxEmail;
            } else {
                dispatchRecipient = (workspace.ownerWhatsapp() != null && !workspace.ownerWhatsapp().isBlank())
                    ? workspace.ownerWhatsapp()
                    : ((workspace.ownerPhone() != null && !workspace.ownerPhone().isBlank())
                        ? workspace.ownerPhone()
                        : sandboxPhone);
            }
            maskedBody = "[SANDBOX - Intended recipient: " + originalRecipient + "]\n\n" + maskedBody;
            log.info("[SANDBOX ACTIVE] Rerouted notification from {} to {}", maskRecipient(originalRecipient), maskRecipient(dispatchRecipient));
        }

        // 10. Check Sending Window 09:00–19:00 IST (Rule R-75)
        SendingWindowChecker.WindowResult window = sendingWindowChecker.checkCurrentWindow();
        UUID outboxId = UUID.randomUUID();
        String idempotencyKey = "outbox-" + outboxId;

        if (!window.insideWindow()) {
            // Queue for next opening
            NotificationOutboxEntity queued = new NotificationOutboxEntity(
                outboxId,
                workspace.id(),
                caseId,
                action.id(),
                channel,
                dispatchRecipient,
                templateKey,
                maskedBody,
                "QUEUED",
                null,
                null,
                window.nextWindowStart(),
                null,
                idempotencyKey,
                Instant.now()
            );
            queued = outboxRepository.save(queued);

            recordAuditEvent(caseId, userContext.userId(), "NOTIFICATION_QUEUED",
                Map.of("outboxId", outboxId, "channel", channel, "scheduledFor", window.nextWindowStart()));

            return new NotificationResult(queued, null, isRerouted, originalRecipient);
        }

        // 11. Inside window: Dispatch
        String clickToChatUrl = null;
        NotificationOutboxEntity outbox;

        boolean isDemoCase = caseEntity.isDemoData() || caseEntity.isSeed();

        if ("EMAIL".equals(channel)) {
            byte[] pdfBytes = null;
            if (command.includeStatementPdf()) {
                pdfBytes = pdfService.generateStatementPdf(caseEntity);
            }

            String subject = command.subject() != null && !command.subject().isBlank()
                ? command.subject()
                : (isRerouted ? "[SANDBOX] " : "") + "Commercial Account Reconciliation: Case " + caseEntity.code();

            String replyTo = (userContext.email() != null && !userContext.email().isBlank()) ? userContext.email() : null;

            ResendClient.SendEmailResult resendResult;
            if (isDemoCase) {
                // Simulated notification for demo data: never sent to external providers
                resendResult = new ResendClient.SendEmailResult(true, "sim-demo-email-" + outboxId, null);
            } else {
                resendResult = resendClient.sendEmail(new ResendClient.SendEmailRequest(
                    dispatchRecipient,
                    replyTo,
                    subject,
                    maskedBody,
                    idempotencyKey,
                    pdfBytes,
                    "Statement_of_Account_" + caseEntity.code() + ".pdf"
                ));
            }

            String status = resendResult.success() ? "SENT" : "FAILED";
            Instant sentAt = resendResult.success() ? Instant.now() : null;

            outbox = new NotificationOutboxEntity(
                outboxId,
                workspace.id(),
                caseId,
                action.id(),
                channel,
                dispatchRecipient,
                templateKey,
                maskedBody,
                status,
                resendResult.messageId(),
                resendResult.error(),
                null,
                sentAt,
                idempotencyKey,
                Instant.now()
            );
            outbox = outboxRepository.save(outbox);

        } else if ("WHATSAPP_LINK".equals(channel)) {
            clickToChatUrl = whatsAppClient.buildClickToChatUrl(dispatchRecipient, maskedBody);

            outbox = new NotificationOutboxEntity(
                outboxId,
                workspace.id(),
                caseId,
                action.id(),
                channel,
                dispatchRecipient,
                templateKey,
                maskedBody,
                "MANUAL_PENDING",
                null,
                null,
                null,
                null,
                idempotencyKey,
                Instant.now()
            );
            outbox = outboxRepository.save(outbox);

        } else if ("WHATSAPP_API".equals(channel)) {
            WhatsAppClient.SendWhatsAppResult waResult;
            if (isDemoCase) {
                // Simulated notification for demo data: never sent to external providers
                waResult = new WhatsAppClient.SendWhatsAppResult(true, "sim-demo-wa-" + outboxId, null);
            } else {
                waResult = whatsAppClient.sendTemplateMessage(
                    dispatchRecipient,
                    templateKey,
                    vars
                );
            }

            String status = waResult.success() ? "SENT" : "FAILED";
            Instant sentAt = waResult.success() ? Instant.now() : null;

            outbox = new NotificationOutboxEntity(
                outboxId,
                workspace.id(),
                caseId,
                action.id(),
                channel,
                dispatchRecipient,
                templateKey,
                maskedBody,
                status,
                waResult.providerMessageId(),
                waResult.error(),
                null,
                sentAt,
                idempotencyKey,
                Instant.now()
            );
            outbox = outboxRepository.save(outbox);
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHANNEL", "Unsupported channel: " + channel);
        }

        recordAuditEvent(caseId, userContext.userId(), "NOTIFICATION_DISPATCHED",
            Map.of("outboxId", outbox.id(), "channel", channel, "status", outbox.status()));

        return new NotificationResult(outbox, clickToChatUrl, isRerouted, originalRecipient);
    }

    public List<NotificationOutboxEntity> listNotifications(UUID caseId) {
        CurrentUserService.UserContext userContext = currentUserService.getCurrentUser();
        return outboxRepository.findByWorkspaceAndCaseId(userContext.workspaceId(), caseId);
    }

    @Transactional
    public NotificationOutboxEntity resendNotification(UUID caseId, UUID outboxId) {
        CurrentUserService.UserContext userContext = currentUserService.getCurrentUser();
        if (userContext.isAnonymous() || CurrentUserService.DEMO_WORKSPACE_ID.equals(userContext.workspaceId())) {
            throw new DemoReadOnlyException();
        }

        NotificationOutboxEntity existing = outboxRepository.findByWorkspaceAndId(userContext.workspaceId(), outboxId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OUTBOX_NOT_FOUND", "Notification not found"));

        if (!"FAILED".equals(existing.status()) && !"BOUNCED".equals(existing.status()) && !"QUEUED".equals(existing.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_RESEND", "Only FAILED, BOUNCED, or QUEUED notifications can be resent");
        }

        // Re-dispatch
        if ("EMAIL".equals(existing.channel())) {
            ResendClient.SendEmailResult result = resendClient.sendEmail(new ResendClient.SendEmailRequest(
                existing.recipient(),
                userContext.email(),
                "Follow-up: Commercial Account Reconciliation",
                existing.renderedBody(),
                "resend-" + UUID.randomUUID(),
                null,
                null
            ));
            outboxRepository.updateStatus(
                existing.id(),
                result.success() ? "SENT" : "FAILED",
                result.messageId(),
                result.error(),
                result.success() ? Instant.now() : null
            );
        } else if ("WHATSAPP_API".equals(existing.channel())) {
            WhatsAppClient.SendWhatsAppResult waResult = whatsAppClient.sendTemplateMessage(
                existing.recipient(),
                existing.templateKey(),
                Map.of()
            );
            outboxRepository.updateStatus(
                existing.id(),
                waResult.success() ? "SENT" : "FAILED",
                waResult.providerMessageId(),
                waResult.error(),
                waResult.success() ? Instant.now() : null
            );
        }

        return outboxRepository.findById(existing.id()).orElseThrow();
    }

    @Transactional
    public NotificationOutboxEntity cancelQueued(UUID caseId, UUID outboxId) {
        CurrentUserService.UserContext userContext = currentUserService.getCurrentUser();
        if (userContext.isAnonymous() || CurrentUserService.DEMO_WORKSPACE_ID.equals(userContext.workspaceId())) {
            throw new DemoReadOnlyException();
        }

        NotificationOutboxEntity existing = outboxRepository.findByWorkspaceAndId(userContext.workspaceId(), outboxId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OUTBOX_NOT_FOUND", "Notification not found"));

        if (!"QUEUED".equals(existing.status()) && !"MANUAL_PENDING".equals(existing.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_CANCEL", "Only QUEUED or MANUAL_PENDING notifications can be cancelled");
        }

        outboxRepository.updateStatus(existing.id(), "FAILED", existing.providerMessageId(), "Cancelled by user", null);
        return outboxRepository.findById(existing.id()).orElseThrow();
    }

    @Transactional
    public NotificationOutboxEntity markManualSent(UUID caseId, UUID outboxId) {
        CurrentUserService.UserContext userContext = currentUserService.getCurrentUser();
        if (userContext.isAnonymous() || CurrentUserService.DEMO_WORKSPACE_ID.equals(userContext.workspaceId())) {
            throw new DemoReadOnlyException();
        }

        NotificationOutboxEntity existing = outboxRepository.findByWorkspaceAndId(userContext.workspaceId(), outboxId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OUTBOX_NOT_FOUND", "Notification not found"));

        if (!"MANUAL_PENDING".equals(existing.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Only MANUAL_PENDING notifications can be marked manual-sent");
        }

        outboxRepository.updateStatus(existing.id(), "MANUAL_SENT", null, null, Instant.now());
        recordAuditEvent(caseId, userContext.userId(), "NOTIFICATION_MANUAL_SENT", Map.of("outboxId", outboxId));
        return outboxRepository.findById(existing.id()).orElseThrow();
    }

    @Transactional
    public ContactEntity unsubscribe(UUID caseId, @Nullable String reason) {
        CurrentUserService.UserContext userContext = currentUserService.getCurrentUser();
        ContactEntity contact = contactRepository.findByWorkspaceAndCaseId(userContext.workspaceId(), caseId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTACT_NOT_FOUND", "Contact not found for this case"));

        contactRepository.updateUnsubscribed(contact.id(), Instant.now());
        recordAuditEvent(caseId, userContext.userId(), "CONTACT_UNSUBSCRIBED", Map.of("reason", reason != null ? reason : "User request"));
        return contactRepository.findById(contact.id()).orElseThrow();
    }

    // --- Webhook Processing ---

    @Transactional
    public void processResendWebhook(String payload, String svixId, String svixTimestamp, String svixSignature) {
        if (!resendClient.verifyWebhookSignature(payload, svixId, svixTimestamp, svixSignature)) {
            log.warn("Invalid Resend Svix webhook signature");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "Invalid Resend signature");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText();
            JsonNode data = root.path("data");
            String emailId = data.path("email_id").asText();

            log.info("Processing Resend webhook event: {} for emailId: {}", type, emailId);
            Optional<NotificationOutboxEntity> outboxOpt = outboxRepository.findByProviderMessageId(emailId);
            if (outboxOpt.isEmpty()) {
                log.info("No outbox record matching Resend providerMessageId: {}", emailId);
                return;
            }

            NotificationOutboxEntity outbox = outboxOpt.get();
            switch (type) {
                case "email.delivered" -> outboxRepository.updateStatus(outbox.id(), "DELIVERED", emailId, null, null);
                case "email.bounced" -> {
                    String reason = data.path("bounce").path("message").asText("Bounced");
                    outboxRepository.updateStatus(outbox.id(), "BOUNCED", emailId, reason, null);
                }
                case "email.complained" -> {
                    outboxRepository.updateStatus(outbox.id(), "BOUNCED", emailId, "Spam complaint", null);
                    contactRepository.findByWorkspaceAndCaseId(outbox.workspaceId(), outbox.caseId())
                        .ifPresent(c -> contactRepository.updateUnsubscribed(c.id(), Instant.now()));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Resend webhook payload", e);
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYLOAD", "Cannot parse webhook payload");
        }
    }

    @Transactional
    public void processWhatsAppWebhook(String rawPayload, String signatureHeader) {
        if (!whatsAppClient.verifyWebhookSignature(rawPayload, signatureHeader)) {
            log.warn("Invalid WhatsApp webhook signature");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "Invalid WhatsApp signature");
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode entryArray = root.path("entry");
            if (!entryArray.isArray()) return;

            for (JsonNode entry : entryArray) {
                JsonNode changes = entry.path("changes");
                if (!changes.isArray()) continue;

                for (JsonNode change : changes) {
                    JsonNode value = change.path("value");

                    // 1. Delivery status updates
                    JsonNode statuses = value.path("statuses");
                    if (statuses.isArray()) {
                        for (JsonNode statusNode : statuses) {
                            String msgId = statusNode.path("id").asText();
                            String status = statusNode.path("status").asText();

                            outboxRepository.findByProviderMessageId(msgId).ifPresent(outbox -> {
                                if ("delivered".equalsIgnoreCase(status) || "read".equalsIgnoreCase(status)) {
                                    outboxRepository.updateStatus(outbox.id(), "DELIVERED", msgId, null, null);
                                } else if ("failed".equalsIgnoreCase(status)) {
                                    String errorMsg = statusNode.path("errors").toString();
                                    outboxRepository.updateStatus(outbox.id(), "FAILED", msgId, errorMsg, null);
                                }
                            });
                        }
                    }

                    // 2. Inbound messages from buyer (R-74, R-78)
                    JsonNode messages = value.path("messages");
                    if (messages.isArray()) {
                        for (JsonNode msg : messages) {
                            String sender = msg.path("from").asText();
                            String text = msg.path("text").path("body").asText();
                            String msgId = msg.path("id").asText();

                            handleInboundWhatsAppMessage(sender, text, msgId);
                        }
                    }
                }
            }
        } catch (ApiException ae) {
            throw ae;
        } catch (Exception e) {
            log.error("Failed to process WhatsApp webhook payload", e);
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYLOAD", "Cannot process WhatsApp webhook");
        }
    }

    private void handleInboundWhatsAppMessage(String sender, String text, String msgId) {
        log.info("Received inbound WhatsApp message from {} (length: {} chars)", maskRecipient(sender), text != null ? text.length() : 0);

        // Honour STOP / unsubscribe (Rule R-74)
        if (text != null && (text.trim().equalsIgnoreCase("STOP") || text.trim().equalsIgnoreCase("UNSUBSCRIBE"))) {
            List<ContactEntity> contacts = contactRepository.findByWhatsappNumber(sender);
            for (ContactEntity c : contacts) {
                contactRepository.updateUnsubscribed(c.id(), Instant.now());
                if (c.caseId() != null) {
                    recordAuditEvent(c.caseId(), "system", "CONTACT_UNSUBSCRIBED", Map.of("channel", "WHATSAPP", "trigger", "STOP"));
                }
            }
            return;
        }

        // Inbound message creates a new MESSAGE document marked UNVERIFIED (Rule R-78)
        List<ContactEntity> contacts = contactRepository.findByWhatsappNumber(sender);
        if (contacts.isEmpty()) {
            log.warn("Inbound WhatsApp message from unknown contact: {}", maskRecipient(sender));
            return;
        }

        ContactEntity contact = contacts.get(0);
        if (contact.caseId() == null) {
            log.warn("Contact {} has no associated case", contact.id());
            return;
        }

        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        String sha256 = calculateSha256(textBytes);

        DocumentEntity inboundDoc = new DocumentEntity(
            UUID.randomUUID(),
            contact.caseId(),
            contact.workspaceId(),
            DocType.MESSAGE,
            "whatsapp_inbound_" + msgId + ".txt",
            "text/plain",
            sha256,
            "inbound-whatsapp",
            1,
            null,
            Instant.now(),
            textBytes.length,
            textBytes
        );
        documentRepository.save(inboundDoc);

        recordAuditEvent(
            contact.caseId(),
            "system",
            "INBOUND_MESSAGE_RECEIVED",
            Map.of(
                "documentId", inboundDoc.id(),
                "sender", sender,
                "status", "UNVERIFIED",
                "recommendation", "Re-run analysis to incorporate buyer reply"
            )
        );
    }

    private void recordAuditEvent(UUID caseId, String actor, String eventType, Map<String, Object> payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            auditEventRepository.save(new AuditEventEntity(
                UUID.randomUUID(),
                caseId,
                actor,
                eventType,
                jsonPayload,
                Instant.now()
            ));
        } catch (Exception e) {
            log.error("Failed to record audit event", e);
        }
    }

    private static String calculateSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    private static String maskRecipient(String recipient) {
        if (recipient == null) return "null";
        int at = recipient.indexOf('@');
        if (at > 1) {
            return recipient.substring(0, 2) + "***" + recipient.substring(at);
        }
        if (recipient.length() > 4) {
            return recipient.substring(0, 2) + "***" + recipient.substring(recipient.length() - 2);
        }
        return "***";
    }
}