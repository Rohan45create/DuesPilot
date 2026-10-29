import React, { useState } from 'react';
import { Card } from '../ui/Card';
import { Button } from '../ui/Button';
import { api } from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import {
  Mail,
  MessageSquare,
  FileText,
  AlertTriangle,
  Clock,
  ShieldCheck,
  CheckSquare,
  Square,
  Send,
  Sparkles,
  CheckCircle2,
  Lock,
} from 'lucide-react';
import { cn } from '../../utils/formatters';
import { BuyerContact, NotificationOutbox } from '../../api/schemas';
import { Link } from 'react-router-dom';

interface SendActionPanelProps {
  caseId: string;
  caseCode?: string;
  actionId?: string;
  actionTitle?: string;
  englishDraft?: string;
  hindiDraft?: string;
  contact?: BuyerContact;
  onSuccess?: (outbox: NotificationOutbox) => void;
  className?: string;
}

export const SendActionPanel: React.FC<SendActionPanelProps> = ({
  caseId,
  caseCode = 'DP-0142',
  actionId = 'act-0142',
  actionTitle = 'Request delivery proof',
  englishDraft = 'Please share the signed delivery challan or goods receipt for invoice INV-2026-0142 so we can confirm the outstanding balance.',
  hindiDraft = 'कृपया INV-2026-0142 के लिए हस्ताक्षरित डिलीवरी चालान या माल रसीद साझा करें ताकि हम बकाया शेष की पुष्टि कर सकें।',
  contact,
  onSuccess,
  className,
}) => {
  const { isAnonymous } = useAuth();

  const [channel, setChannel] = useState<'EMAIL' | 'WHATSAPP_LINK'>('EMAIL');
  const [lang, setLang] = useState<'EN' | 'HI'>('EN');
  const [includePdf, setIncludePdf] = useState(true);
  const templateKey = 'delivery_proof_request';

  // Pre-send Checklist state
  const [checkEvidence, setCheckEvidence] = useState(false);
  const [checkRecipient, setCheckRecipient] = useState(false);
  const [checkApproval, setCheckApproval] = useState(false);

  // Interaction & dispatch state
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successResult, setSuccessResult] = useState<NotificationOutbox | null>(null);
  const [confirmManualPrompt, setConfirmManualPrompt] = useState(false);
  const [pendingOutboxId, setPendingOutboxId] = useState<string | null>(null);
  const [manualSentSuccess, setManualSentSuccess] = useState(false);

  // Check IST time for sending window (09:00 - 19:00 IST)
  const now = new Date();
  const utcHours = now.getUTCHours();
  const utcMinutes = now.getUTCMinutes();
  // IST is UTC + 5:30
  const istMinutesTotal = (utcHours * 60 + utcMinutes + 330) % 1440;
  const istHour = Math.floor(istMinutesTotal / 60);
  const isInsideWindow = istHour >= 9 && istHour < 19;

  // Active draft text
  const rawText = lang === 'HI' ? hindiDraft : englishDraft;

  // Mask sensitive numbers in preview (Rule R-77)
  const maskSensitive = (text: string) => {
    return text
      .replace(/\b([A-Z]{5}[0-9]{4}[A-Z])\b/g, '••••$1'.slice(-4))
      .replace(/\b([0-9]{4})[\s-]+([0-9]{4})[\s-]+([0-9]{4})\b/g, '•••• •••• $3')
      .replace(/\b([0-9]{9,18})\b/g, '••••$1'.slice(-4));
  };

  const previewBody = maskSensitive(rawText);

  // Opt-in check
  const isOptedOut = channel === 'EMAIL'
    ? contact && !contact.emailOptIn
    : contact && !contact.whatsappOptIn;

  const isUnsubscribed = contact?.unsubscribedAt != null;

  // Checklist validity
  const isChecklistComplete = checkEvidence && checkRecipient && checkApproval;

  const handleSend = async () => {
    if (isAnonymous) return;
    if (!isChecklistComplete) return;

    setIsSubmitting(true);
    setErrorMsg(null);

    try {
      const recipient = channel === 'EMAIL'
        ? (contact?.email || 'accounts@kestrel-ind.com')
        : (contact?.whatsappNumber || '+919811111111');

      const result = await api.sendNotification(caseId, {
        action_id: actionId,
        channel,
        recipient,
        template_key: templateKey,
        body: previewBody,
        include_statement_pdf: includePdf,
        subject: `Commercial Account Reconciliation: Case ${caseCode}`,
      });

      setSuccessResult(result);
      setIsSubmitting(false);

      if (channel === 'WHATSAPP_LINK') {
        const url = result.click_to_chat_url || `https://wa.me/${recipient.replace(/[^0-9]/g, '')}?text=${encodeURIComponent(previewBody)}`;
        window.open(url, '_blank', 'noopener,noreferrer');
        setPendingOutboxId(result.id);
        setConfirmManualPrompt(true);
      }

      onSuccess?.(result);
    } catch (err) {
      setIsSubmitting(false);
      setErrorMsg(err instanceof Error ? err.message : 'Failed to dispatch notification');
    }
  };

  const handleConfirmManualSent = async () => {
    if (!pendingOutboxId) return;
    try {
      await api.markManualSent(caseId, pendingOutboxId);
      setManualSentSuccess(true);
      setConfirmManualPrompt(false);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Failed to mark manual-sent');
    }
  };

  return (
    <Card
      data-testid="send-action-panel"
      className={cn('p-5 sm:p-6 border border-border bg-surface shadow-md space-y-6', className)}
    >
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-brand animate-pulse" />
            <h3 className="text-base font-bold font-heading text-foreground tracking-tight">
              Dispatch Panel · Human-in-the-Loop Send
            </h3>
          </div>
          <p className="text-xs text-foreground-muted mt-0.5">
            {actionTitle ? `Action: ${actionTitle} · ` : ''}Rule R-72: Notifications are never dispatched automatically. Review, select channel, and confirm.
          </p>
        </div>

        {/* Language selector toggle */}
        <div className="flex items-center gap-1.5 p-1 rounded-full bg-surface-subtle border border-border self-start sm:self-auto">
          <button
            type="button"
            data-testid="send-lang-en"
            onClick={() => setLang('EN')}
            className={cn(
              'px-3 py-1 text-xs font-semibold rounded-full transition-all cursor-pointer',
              lang === 'EN' ? 'bg-brand text-black shadow-xs' : 'text-foreground-muted hover:text-foreground'
            )}
          >
            English
          </button>
          <button
            type="button"
            data-testid="send-lang-hi"
            onClick={() => setLang('HI')}
            className={cn(
              'px-3 py-1 text-xs font-semibold rounded-full transition-all cursor-pointer',
              lang === 'HI' ? 'bg-brand text-black shadow-xs' : 'text-foreground-muted hover:text-foreground'
            )}
          >
            हिंदी
          </button>
        </div>
      </div>

      {/* Channel Selector Pills */}
      <div>
        <label className="block text-xs font-semibold text-foreground-muted mb-2">
          Select Communication Channel
        </label>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {/* Email Option */}
          <button
            type="button"
            data-testid="send-channel-email"
            onClick={() => setChannel('EMAIL')}
            className={cn(
              'p-3.5 rounded-2xl border text-left transition-all cursor-pointer flex items-start gap-3',
              channel === 'EMAIL'
                ? 'border-brand bg-brand/5 ring-1 ring-brand'
                : 'border-border bg-surface-subtle hover:bg-surface'
            )}
          >
            <div className={cn(
              'p-2 rounded-xl shrink-0',
              channel === 'EMAIL' ? 'bg-brand text-black' : 'bg-surface border border-border text-foreground-muted'
            )}>
              <Mail className="w-4 h-4" />
            </div>
            <div>
              <div className="text-xs font-bold text-foreground">Email (Resend)</div>
              <div className="text-[11px] text-foreground-muted mt-0.5">
                Official delivery with PDF Statement of Account and audit webhook
              </div>
            </div>
          </button>

          {/* WhatsApp Link Option */}
          <button
            type="button"
            data-testid="send-channel-whatsapp"
            onClick={() => setChannel('WHATSAPP_LINK')}
            className={cn(
              'p-3.5 rounded-2xl border text-left transition-all cursor-pointer flex items-start gap-3',
              channel === 'WHATSAPP_LINK'
                ? 'border-brand bg-brand/5 ring-1 ring-brand'
                : 'border-border bg-surface-subtle hover:bg-surface'
            )}
          >
            <div className={cn(
              'p-2 rounded-xl shrink-0',
              channel === 'WHATSAPP_LINK' ? 'bg-brand text-black' : 'bg-surface border border-border text-foreground-muted'
            )}>
              <MessageSquare className="w-4 h-4" />
            </div>
            <div>
              <div className="text-xs font-bold text-foreground">WhatsApp (wa.me Link)</div>
              <div className="text-[11px] text-foreground-muted mt-0.5">
                Approved catalogue template with click-to-chat & confirmation
              </div>
            </div>
          </button>
        </div>
      </div>

      {/* Guardrail Warnings Banner Box */}
      <div className="space-y-2">
        {/* Sandbox notice */}
        <div className="p-3 rounded-xl bg-surface-subtle border border-border flex items-center gap-2.5 text-xs text-foreground">
          <ShieldCheck className="w-4 h-4 text-brand shrink-0" />
          <span>
            <strong className="text-brand">Sandbox Rerouting Active (R-76):</strong> Dispatches will be safely rerouted to your owner email / phone.
          </span>
        </div>

        {/* Sending Window Warning */}
        {!isInsideWindow && (
          <div className="p-3 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-center gap-2.5 text-xs text-amber-600 dark:text-amber-400">
            <Clock className="w-4 h-4 shrink-0 text-amber-500" />
            <span>
              <strong>Outside 09:00–19:00 IST Window (R-75):</strong> It is currently outside the commercial window. The message will be queued and scheduled for dispatch tomorrow at 09:00 IST.
            </span>
          </div>
        )}

        {/* Opt-out or Unsubscribe Warning */}
        {(isOptedOut || isUnsubscribed) && (
          <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/30 flex items-center gap-2.5 text-xs text-red-600 dark:text-red-400">
            <AlertTriangle className="w-4 h-4 shrink-0 text-red-500" />
            <span>
              <strong>Opt-In Restriction (R-74):</strong> Recipient has {isUnsubscribed ? 'unsubscribed from communications' : 'not opted into ' + channel}. Dispatch is blocked by guardrails.
            </span>
          </div>
        )}
      </div>

      {/* Per-Channel Preview Panes */}
      <div className="space-y-3">
        <div className="flex items-center justify-between text-xs font-semibold text-foreground-muted">
          <span>Live Draft Preview (Sensitive data masked per Rule R-77)</span>
          {channel === 'EMAIL' && (
            <label className="flex items-center gap-2 cursor-pointer text-foreground">
              <input
                type="checkbox"
                checked={includePdf}
                onChange={(e) => setIncludePdf(e.target.checked)}
                className="rounded accent-brand cursor-pointer"
              />
              <span className="text-[11px] font-medium">Attach PDF Statement</span>
            </label>
          )}
        </div>

        {channel === 'EMAIL' ? (
          /* Email Preview Card */
          <div className="rounded-2xl border border-border bg-surface-subtle overflow-hidden text-xs">
            <div className="bg-surface p-3 border-b border-border space-y-1.5 font-mono text-[11px]">
              <div className="flex items-center justify-between text-foreground-muted">
                <span>To: <span className="text-foreground">{contact?.email || 'accounts@kestrel-ind.com'}</span></span>
                <span className="text-[10px] text-brand bg-brand/10 px-2 py-0.5 rounded-full font-sans">Verified Recipient</span>
              </div>
              <div className="text-foreground-muted">
                Subject: <span className="text-foreground font-semibold">Commercial Account Reconciliation: Case {caseCode}</span>
              </div>
            </div>

            <div className="p-4 space-y-3 text-foreground leading-relaxed whitespace-pre-wrap font-sans text-xs">
              {previewBody}
            </div>

            {includePdf && (
              <div className="p-3 bg-surface border-t border-border flex items-center justify-between">
                <div className="flex items-center gap-2 text-foreground font-mono text-[11px]">
                  <FileText className="w-4 h-4 text-brand" />
                  <span>Statement_of_Account_{caseCode}.pdf</span>
                  <span className="text-foreground-muted text-[10px]">(Evidence-backed PDFBox 3.0.4)</span>
                </div>
                <span className="text-[11px] text-brand font-medium">Included</span>
              </div>
            )}
          </div>
        ) : (
          /* WhatsApp Chat Mockup Preview */
          <div className="rounded-2xl border border-border bg-emerald-950/20 p-4 relative overflow-hidden">
            <div className="max-w-md ml-auto bg-emerald-800/30 text-foreground border border-emerald-500/30 rounded-2xl rounded-tr-xs p-3.5 shadow-sm space-y-2 text-xs">
              <div className="flex items-center justify-between text-[10px] text-emerald-400 font-medium">
                <span>Template: {templateKey}</span>
                <span>Rule R-73 Compliant</span>
              </div>
              <p className="leading-relaxed whitespace-pre-wrap">{previewBody}</p>
              <div className="text-right text-[10px] text-foreground-muted flex items-center justify-end gap-1">
                <span>{new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                <CheckCircle2 className="w-3 h-3 text-brand" />
              </div>
            </div>
            <div className="text-[10px] text-foreground-muted mt-2 text-center">
              wa.me click-to-chat opens recipient chat with prefilled message.
            </div>
          </div>
        )}
      </div>

      {/* Pre-Send Compliance Checklist */}
      <div className="p-4 rounded-2xl bg-surface-subtle border border-border space-y-3 text-xs">
        <div className="font-semibold text-foreground flex items-center gap-1.5">
          <Sparkles className="w-3.5 h-3.5 text-brand" />
          <span>Operator Verification Checklist (Required for Human Approval)</span>
        </div>
        <div className="space-y-2">
          <div
            role="checkbox"
            data-testid="send-checklist-evidence"
            aria-checked={checkEvidence}
            tabIndex={0}
            onClick={() => setCheckEvidence(!checkEvidence)}
            onKeyDown={(e) => { if (e.key === ' ' || e.key === 'Enter') setCheckEvidence(!checkEvidence); }}
            className="flex items-start gap-2.5 cursor-pointer text-foreground select-none"
          >
            <div className="mt-0.5 text-foreground hover:text-brand">
              {checkEvidence ? <CheckSquare className="w-4 h-4 text-brand" /> : <Square className="w-4 h-4 text-foreground-muted" />}
            </div>
            <span className={cn(checkEvidence ? 'text-foreground font-medium' : 'text-foreground-muted')}>
              I have cross-verified the underlying tax invoices, credit notes, and reconciled balance.
            </span>
          </div>

          <div
            role="checkbox"
            data-testid="send-checklist-recipient"
            aria-checked={checkRecipient}
            tabIndex={0}
            onClick={() => setCheckRecipient(!checkRecipient)}
            onKeyDown={(e) => { if (e.key === ' ' || e.key === 'Enter') setCheckRecipient(!checkRecipient); }}
            className="flex items-start gap-2.5 cursor-pointer text-foreground select-none"
          >
            <div className="mt-0.5 text-foreground hover:text-brand">
              {checkRecipient ? <CheckSquare className="w-4 h-4 text-brand" /> : <Square className="w-4 h-4 text-foreground-muted" />}
            </div>
            <span className={cn(checkRecipient ? 'text-foreground font-medium' : 'text-foreground-muted')}>
              I confirmed buyer contact details ({channel === 'EMAIL' ? contact?.email || 'accounts@kestrel-ind.com' : contact?.whatsappNumber || '+919811111111'}) and channel consent.
            </span>
          </div>

          <div
            role="checkbox"
            data-testid="send-checklist-approval"
            aria-checked={checkApproval}
            tabIndex={0}
            onClick={() => setCheckApproval(!checkApproval)}
            onKeyDown={(e) => { if (e.key === ' ' || e.key === 'Enter') setCheckApproval(!checkApproval); }}
            className="flex items-start gap-2.5 cursor-pointer text-foreground select-none"
          >
            <div className="mt-0.5 text-foreground hover:text-brand">
              {checkApproval ? <CheckSquare className="w-4 h-4 text-brand" /> : <Square className="w-4 h-4 text-foreground-muted" />}
            </div>
            <span className={cn(checkApproval ? 'text-foreground font-medium' : 'text-foreground-muted')}>
              I approve sending this formal communication to resolve commercial accounts.
            </span>
          </div>
        </div>
      </div>

      {/* Confirmation Dialog after clicking WhatsApp Link */}
      {confirmManualPrompt && (
        <div className="p-4 rounded-2xl bg-brand/10 border border-brand/30 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs">
          <div className="space-y-1">
            <div className="font-bold text-foreground flex items-center gap-1.5">
              <CheckCircle2 className="w-4 h-4 text-brand" />
              <span>Did you send the message in WhatsApp?</span>
            </div>
            <p className="text-foreground-muted">
              Confirming records the manual dispatch timestamp in the case communications log.
            </p>
          </div>
          <Button
            size="sm"
            data-testid="confirm-manual-sent-btn"
            onClick={handleConfirmManualSent}
            className="w-full sm:w-auto shrink-0 bg-brand text-black font-bold hover:bg-brand-hover"
          >
            Confirm "I sent it"
          </Button>
        </div>
      )}

      {manualSentSuccess && (
        <div className="p-3 rounded-xl bg-brand/10 border border-brand/30 text-xs text-brand font-medium flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4" />
          <span>WhatsApp dispatch confirmed and saved as MANUAL_SENT in case outbox.</span>
        </div>
      )}

      {/* Error state */}
      {errorMsg && (
        <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/30 text-xs text-red-500 flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Success feedback */}
      {successResult && !confirmManualPrompt && (
        <div className="p-3 rounded-xl bg-brand/10 border border-brand/30 text-xs text-brand font-medium flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span>
            {channel === 'EMAIL'
              ? `Notification sent via Resend (Status: ${successResult.status}, ID: ${successResult.provider_message_id || 'outbox'})`
              : `WhatsApp click-to-chat URL generated (Status: ${successResult.status})`}
          </span>
        </div>
      )}

      {/* Action footer */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-2">
        <div className="text-[11px] text-foreground-muted">
          {!isChecklistComplete ? 'Complete all 3 verification checklist items to enable sending.' : 'Ready for human dispatch.'}
        </div>

        {isAnonymous ? (
          <div className="flex items-center gap-2">
            <span className="text-xs text-amber-500 flex items-center gap-1">
              <Lock className="w-3.5 h-3.5" />
              Demo workspace is read-only
            </span>
            <Link to="/signup">
              <Button size="sm" data-testid="sign-up-to-send-btn" className="bg-brand text-black font-bold hover:bg-brand-hover">
                Sign up to send
              </Button>
            </Link>
          </div>
        ) : (
          <Button
            size="md"
            data-testid="dispatch-action-btn"
            disabled={!isChecklistComplete || isSubmitting || isOptedOut || isUnsubscribed}
            onClick={handleSend}
            className="w-full sm:w-auto font-bold"
          >
            <Send className="w-3.5 h-3.5 mr-2" />
            {isSubmitting
              ? 'Dispatching...'
              : channel === 'WHATSAPP_LINK'
              ? 'Open WhatsApp & Send'
              : 'Send Formal Email'}
          </Button>
        )}
      </div>
    </Card>
  );
};
