package com.duespilot.api.service.notification;

import com.duespilot.api.domain.AuditEventEntity;
import com.duespilot.api.domain.CaseEntity;
import com.duespilot.api.domain.ContactEntity;
import com.duespilot.api.domain.DocType;
import com.duespilot.api.domain.DocumentEntity;
import com.duespilot.api.domain.NotificationOutboxEntity;
import com.duespilot.api.domain.ProposedActionEntity;
import com.duespilot.api.domain.SupplierCategory;
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
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationService Guardrails and Providers Test (R-72 to R-78)")
class NotificationServiceTest {

    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private ProposedActionRepository proposedActionRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private AuditEventRepository auditEventRepository;
    @Mock private ResendClient resendClient;
    @Mock private WhatsAppClient whatsAppClient;
    @Mock private StatementOfAccountPdfService pdfService;
    @Mock private CurrentUserService currentUserService;

    private ObjectMapper objectMapper;
    private ZoneId istZone;
    private UUID testWorkspaceId;
    private UUID testCaseId;
    private UUID testActionId;
    private CaseEntity testCase;
    private WorkspaceEntity testWorkspace;
    private ProposedActionEntity approvedAction;
    private ContactEntity activeContact;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        istZone = ZoneId.of("Asia/Kolkata");
        testWorkspaceId = UUID.randomUUID();
        testCaseId = UUID.randomUUID();
        testActionId = UUID.randomUUID();

        testWorkspace = new WorkspaceEntity(
            testWorkspaceId,
            "Acme Enterprises",
            SupplierCategory.MICRO,
            LocalDate.of(2021, 1, 1),
            "+919876543210",
            "+919876543210",
            false,
            Instant.now(),
            0
        );

        testCase = new CaseEntity(
            testCaseId,
            testWorkspaceId,
            "CASE-101",
            "Disputed Dues for Invoice 101",
            "Acme Enterprises",
            "Buyer Corp",
            SupplierCategory.MICRO,
            LocalDate.of(2021, 1, 1),
            false,
            Instant.now(),
            1
        );

        approvedAction = new ProposedActionEntity(
            testActionId,
            UUID.randomUUID(),
            "DELIVERY_PROOF_REQUEST",
            "en",
            "Please share the signed delivery challan for account 123456789012 and PAN ABCDE1234F.",
            List.of(),
            "APPROVED",
            "owner@example.com",
            Instant.now()
        );

        activeContact = new ContactEntity(
            UUID.randomUUID(),
            testWorkspaceId,
            testCaseId,
            "Buyer Corp",
            "buyer@example.com",
            "+919811111111",
            "+919811111111",
            true,
            "INVOICE",
            Instant.now(),
            true,
            "COMMERCIAL",
            Instant.now(),
            null,
            Instant.now(),
            Instant.now()
        );
    }

    private NotificationService createService(boolean notifySandbox, LocalTime mockIstTime) {
        // Fix clock to mock IST time (e.g. 11:00 AM IST for inside window, or 21:00 PM for outside)
        LocalDate date = LocalDate.of(2026, 10, 26);
        ZonedDateTime zdt = ZonedDateTime.of(date, mockIstTime, istZone);
        Clock fixedClock = Clock.fixed(zdt.toInstant(), istZone);
        SendingWindowChecker windowChecker = new SendingWindowChecker(fixedClock);

        return new NotificationService(
            outboxRepository,
            contactRepository,
            caseRepository,
            workspaceRepository,
            proposedActionRepository,
            documentRepository,
            auditEventRepository,
            resendClient,
            whatsAppClient,
            pdfService,
            windowChecker,
            currentUserService,
            objectMapper,
            notifySandbox,
            "sandbox-owner@duespilot.local",
            "+919999999999"
        );
    }

    private void stubStandardUserAndCase() {
        when(currentUserService.getCurrentUser()).thenReturn(new CurrentUserService.UserContext(
            "user-1",
            "owner@example.com",
            false,
            testWorkspaceId
        ));
        when(workspaceRepository.findById(testWorkspaceId)).thenReturn(Optional.of(testWorkspace));
        when(caseRepository.findById(testCaseId)).thenReturn(Optional.of(testCase));
        when(proposedActionRepository.findById(testActionId)).thenReturn(Optional.of(approvedAction));
        when(contactRepository.findByWorkspaceAndCaseId(testWorkspaceId, testCaseId)).thenReturn(Optional.of(activeContact));
        when(outboxRepository.findRecentSentByCaseAndChannel(eq(testCaseId), anyString(), any(Instant.class))).thenReturn(List.of());
        when(outboxRepository.countSentByCase(testCaseId)).thenReturn(0);
        when(outboxRepository.save(any(NotificationOutboxEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("R-72 / F7: Demo workspace users produce SIMULATED notifications without calling external providers")
    void testDemoUsersProduceSimulatedNotifications() {
        when(currentUserService.getCurrentUser()).thenReturn(new CurrentUserService.UserContext(
            "demo-user",
            "demo@duespilot.local",
            true,
            testWorkspaceId
        ));
        when(workspaceRepository.findById(testWorkspaceId)).thenReturn(Optional.of(
            new WorkspaceEntity(testWorkspaceId, "Demo Workspace", com.duespilot.api.domain.SupplierCategory.MICRO, null, null, null, true, java.time.Instant.now(), 0)
        ));
        when(caseRepository.findById(testCaseId)).thenReturn(Optional.of(testCase));
        when(proposedActionRepository.findById(testActionId)).thenReturn(Optional.of(approvedAction));
        when(contactRepository.findByWorkspaceAndCaseId(testWorkspaceId, testCaseId)).thenReturn(Optional.of(activeContact));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationService service = createService(false, LocalTime.of(11, 0));

        NotificationService.SendNotificationCommand cmd = new NotificationService.SendNotificationCommand(
            testActionId, "EMAIL", null, null, null, null, false, null
        );

        NotificationService.NotificationResult result = service.sendNotification(testCaseId, cmd);

        org.assertj.core.api.Assertions.assertThat(result.outbox().status()).isEqualTo("SIMULATED");
        verify(outboxRepository).save(org.mockito.ArgumentMatchers.argThat(outbox -> "SIMULATED".equals(outbox.status())));
        verify(resendClient, never()).sendEmail(any());
        verify(whatsAppClient, never()).sendTemplateMessage(any(), any(), any());
    }

    @Test
    @DisplayName("R-72: Only APPROVED actions can be sent")
    void testOnlyApprovedActionsCanBeSent() {
        when(currentUserService.getCurrentUser()).thenReturn(new CurrentUserService.UserContext(
            "user-1", "owner@example.com", false, testWorkspaceId
        ));
        when(workspaceRepository.findById(testWorkspaceId)).thenReturn(Optional.of(testWorkspace));
        when(caseRepository.findById(testCaseId)).thenReturn(Optional.of(testCase));

        ProposedActionEntity pendingAction = new ProposedActionEntity(
            testActionId, UUID.randomUUID(), "DELIVERY_PROOF_REQUEST", "en", "Draft text", List.of(),
            "PROPOSED", null, null
        );
        when(proposedActionRepository.findById(testActionId)).thenReturn(Optional.of(pendingAction));

        NotificationService service = createService(false, LocalTime.of(11, 0));

        NotificationService.SendNotificationCommand cmd = new NotificationService.SendNotificationCommand(
            testActionId, "EMAIL", null, null, null, null, false, null
        );

        assertThatThrownBy(() -> service.sendNotification(testCaseId, cmd))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "ACTION_NOT_APPROVED")
            .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

        verify(resendClient, never()).sendEmail(any());
    }

    @Test
    @DisplayName("R-74: Opt-out blocking when contact is unsubscribed or channel not opted-in")
    void testOptOutBlocking() {
        stubStandardUserAndCase();
        // Set contact as unsubscribed
        ContactEntity unsubscribedContact = new ContactEntity(
            activeContact.id(), testWorkspaceId, testCaseId, "Buyer Corp",
            "buyer@example.com", "+919811111111", "+919811111111",
            false, "INVOICE", Instant.now(), false, "COMMERCIAL", Instant.now(),
            Instant.now(), Instant.now(), Instant.now()
        );
        when(contactRepository.findByWorkspaceAndCaseId(testWorkspaceId, testCaseId)).thenReturn(Optional.of(unsubscribedContact));

        NotificationService service = createService(false, LocalTime.of(11, 0));

        NotificationService.SendNotificationCommand cmd = new NotificationService.SendNotificationCommand(
            testActionId, "EMAIL", null, null, null, null, false, null
        );

        assertThatThrownBy(() -> service.sendNotification(testCaseId, cmd))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "OPTED_OUT");

        verify(resendClient, never()).sendEmail(any());
    }

    @Test
    @DisplayName("R-73: WhatsApp template allow-list rejection")
    void testWhatsAppTemplateAllowListRejection() {
        stubStandardUserAndCase();
        NotificationService service = createService(false, LocalTime.of(11, 0));

        NotificationService.SendNotificationCommand cmd = new NotificationService.SendNotificationCommand(
            testActionId, "WHATSAPP_LINK", "+919811111111", "invalid_marketing_promo", null, null, false, null
        );

        assertThatThrownBy(() -> service.sendNotification(testCaseId, cmd))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TEMPLATE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("R-73: Prohibited content rejection on WhatsApp (legal notice / statutory penalty / court)")
    void testProhibitedWhatsAppContentRejection() {
        stubStandardUserAndCase();
        NotificationService service = createService(false, LocalTime.of(11, 0));

        NotificationService.SendNotificationCommand cmd = new NotificationService.SendNotificationCommand(
            testActionId, "WHATSAPP_LINK", "+919811111111",
            WhatsAppTemplateCatalogue.DELIVERY_PROOF_REQUEST,
            "Notice: We will file a lawsuit in court with statutory penalty interest.",
            null, false, null
        );

        assertThatThrownBy(() -> service.sendNotification(testCaseId, cmd))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "PROHIBITED_WHATSAPP_CONTENT");
    }

    @Test
    @DisplayName("R-75: 48-hour cool-down rejection")
    void testCoolDownRejection() {
        stubStandardUserAndCase();
        // Return existing dispatch within 48h
        NotificationOutboxEntity prior = new NotificationOutboxEntity(
            UUID.randomUUID(), testWorkspaceId, testCaseId, testActionId, "EMAIL",
            "buyer@example.com", "delivery_proof_request", "body", "SENT", "msg-1", null, null, Instant.now(), "key-1", Instant.now()
        );
        when(outboxRepository.findRecentSentByCaseAndChannel(eq(testCaseId), eq("EMAIL"), any(Instant.class)))
            .thenReturn(List.of(prior));

        NotificationService service = createService(false, LocalTime.of(11, 0));

        NotificationService.SendNotificationCommand cmd = new NotificationService.SendNotificationCommand(
            testActionId, "EMAIL", null, null, null, null, false, null
        );

        assertThatThrownBy(() -> service.sendNotification(testCaseId, cmd))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "COOL_DOWN_ACTIVE")
            .hasFieldOrPropertyWithValue("status", HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("R-75: Max 3 follow-ups limit rejection")
    void testMaxFollowUpsRejection() {
        stubStandardUserAndCase();
        when(outboxRepository.countSentByCase(testCaseId)).thenReturn(3);

        NotificationService service = createService(false, LocalTime.of(11, 0));

        NotificationService.SendNotificationCommand cmd = new NotificationService.SendNotificationCommand(
            testActionId, "EMAIL", null, null, null, null, false, null
        );

        assertThatThrownBy(() -> service.sendNotification(testCaseId, cmd))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "MAX_FOLLOW_UPS_EXCEEDED");
    }

    @Test
    @DisplayName("R-75: Sending window queueing outside 09:00-19:00 IST")
    void testSendingWindowQueueingOutsideWindow() {
        stubStandardUserAndCase();
        // Time is 21:30 IST (outside 09:00-19:00)
        NotificationService service = createService(false, LocalTime.of(21, 30));

        NotificationService.SendNotificationCommand cmd = new NotificationService.SendNotificationCommand(
            testActionId, "EMAIL", null, null, null, null, false, null
        );

        NotificationService.NotificationResult result = service.sendNotification(testCaseId, cmd);

        assertThat(result.outbox().status()).isEqualTo("QUEUED");
        assertThat(result.outbox().scheduledFor()).isNotNull();

        // Ensure ResendClient was NOT called yet
        verify(resendClient, never()).sendEmail(any());
        verify(outboxRepository).save(any(NotificationOutboxEntity.class));
    }

    @Test
    @DisplayName("R-76 & R-77: Sandbox rerouting and sensitive data masking")
    void testSandboxReroutingAndDataMasking() {
        stubStandardUserAndCase();
        // Enable sandbox, time inside window (14:00 IST)
        NotificationService service = createService(true, LocalTime.of(14, 0));

        when(resendClient.sendEmail(any())).thenReturn(new ResendClient.SendEmailResult(true, "resend-msg-123", null));

        NotificationService.SendNotificationCommand cmd = new NotificationService.SendNotificationCommand(
            testActionId, "EMAIL", "buyer@example.com", null,
            "Please pay bank account 123456789012 and PAN ABCDE1234F.", null, false, "Reconciliation Notice"
        );

        NotificationService.NotificationResult result = service.sendNotification(testCaseId, cmd);

        assertThat(result.reroutedToSandbox()).isTrue();
        assertThat(result.originalRecipient()).isEqualTo("buyer@example.com");

        ArgumentCaptor<ResendClient.SendEmailRequest> captor = ArgumentCaptor.forClass(ResendClient.SendEmailRequest.class);
        verify(resendClient).sendEmail(captor.capture());

        ResendClient.SendEmailRequest captured = captor.getValue();
        // Recipient rerouted to owner
        assertThat(captured.recipient()).isEqualTo("owner@example.com");
        // Sensitive data masked
        assertThat(captured.textBody()).contains("••••9012");
        assertThat(captured.textBody()).contains("••••234F");
        assertThat(captured.textBody()).doesNotContain("123456789012");
        assertThat(captured.textBody()).doesNotContain("ABCDE1234F");
        assertThat(captured.textBody()).startsWith("[SANDBOX - Intended recipient: buyer@example.com]");
    }

    @Test
    @DisplayName("R-78: Resend webhook signature verification failure returns 401")
    void testResendWebhookSignatureFailure() {
        NotificationService service = createService(false, LocalTime.of(11, 0));
        when(resendClient.verifyWebhookSignature(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.processResendWebhook("{}", "svix-1", "123456", "v1,bad-sig"))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "INVALID_WEBHOOK_SIGNATURE")
            .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("R-78: WhatsApp webhook signature verification failure returns 401")
    void testWhatsAppWebhookSignatureFailure() {
        NotificationService service = createService(false, LocalTime.of(11, 0));
        when(whatsAppClient.verifyWebhookSignature(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.processWhatsAppWebhook("{}", "sha256=invalidsig"))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "INVALID_WEBHOOK_SIGNATURE")
            .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("R-78: Inbound WhatsApp message creates UNVERIFIED document and audit event")
    void testInboundWhatsAppMessageCreatesDocument() {
        NotificationService service = createService(false, LocalTime.of(11, 0));
        when(whatsAppClient.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        when(contactRepository.findByWhatsappNumber("+919811111111")).thenReturn(List.of(activeContact));

        String inboundJson = """
            {
              "entry": [{
                "changes": [{
                  "value": {
                    "messages": [{
                      "id": "wamid.inbound.999",
                      "from": "+919811111111",
                      "text": { "body": "We approved the delivery challan on Oct 20." }
                    }]
                  }
                }]
              }]
            }
            """;

        service.processWhatsAppWebhook(inboundJson, "sha256=validsig");

        ArgumentCaptor<DocumentEntity> docCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(docCaptor.capture());

        DocumentEntity savedDoc = docCaptor.getValue();
        assertThat(savedDoc.caseId()).isEqualTo(testCaseId);
        assertThat(savedDoc.docType()).isEqualTo(DocType.MESSAGE);
        assertThat(savedDoc.storagePath()).isEqualTo("inbound-whatsapp");
        assertThat(new String(savedDoc.contentBytes())).isEqualTo("We approved the delivery challan on Oct 20.");

        ArgumentCaptor<AuditEventEntity> auditCaptor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("INBOUND_MESSAGE_RECEIVED");
        assertThat(auditCaptor.getValue().payload()).contains("UNVERIFIED");
    }

    @Test
    @DisplayName("R-74: Inbound STOP message unsubscribes contact")
    void testInboundStopUnsubscribesContact() {
        NotificationService service = createService(false, LocalTime.of(11, 0));
        when(whatsAppClient.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
        when(contactRepository.findByWhatsappNumber("+919811111111")).thenReturn(List.of(activeContact));

        String stopJson = """
            {
              "entry": [{
                "changes": [{
                  "value": {
                    "messages": [{
                      "id": "wamid.stop.111",
                      "from": "+919811111111",
                      "text": { "body": "STOP" }
                    }]
                  }
                }]
              }]
            }
            """;

        service.processWhatsAppWebhook(stopJson, "sha256=validsig");

        verify(contactRepository).updateUnsubscribed(eq(activeContact.id()), any(Instant.class));
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("R-72: WHATSAPP_LINK builds wa.me URL and marks MANUAL_PENDING, then mark-manual-sent transitions to MANUAL_SENT")
    void testWhatsAppLinkFlowAndMarkManualSent() {
        stubStandardUserAndCase();
        NotificationService service = createService(false, LocalTime.of(11, 0));

        when(whatsAppClient.buildClickToChatUrl(anyString(), anyString()))
            .thenReturn("https://wa.me/919811111111?text=Dear%20Buyer");

        NotificationService.SendNotificationCommand cmd = new NotificationService.SendNotificationCommand(
            testActionId, "WHATSAPP_LINK", "+919811111111",
            WhatsAppTemplateCatalogue.DELIVERY_PROOF_REQUEST,
            null, Map.of("buyer_name", "Buyer Corp"), false, null
        );

        NotificationService.NotificationResult result = service.sendNotification(testCaseId, cmd);

        assertThat(result.outbox().status()).isEqualTo("MANUAL_PENDING");
        assertThat(result.clickToChatUrl()).isEqualTo("https://wa.me/919811111111?text=Dear%20Buyer");

        // Mark manual sent
        NotificationOutboxEntity pending = result.outbox();
        when(outboxRepository.findByWorkspaceAndId(testWorkspaceId, pending.id())).thenReturn(Optional.of(pending));
        when(outboxRepository.findById(pending.id())).thenReturn(Optional.of(
            new NotificationOutboxEntity(pending.id(), pending.workspaceId(), pending.caseId(), pending.actionId(),
                pending.channel(), pending.recipient(), pending.templateKey(), pending.renderedBody(),
                "MANUAL_SENT", null, null, null, Instant.now(), pending.idempotencyKey(), pending.createdAt())
        ));

        NotificationOutboxEntity updated = service.markManualSent(testCaseId, pending.id());
        assertThat(updated.status()).isEqualTo("MANUAL_SENT");
        verify(outboxRepository).updateStatus(eq(pending.id()), eq("MANUAL_SENT"), any(), any(), any(Instant.class));
    }
}
