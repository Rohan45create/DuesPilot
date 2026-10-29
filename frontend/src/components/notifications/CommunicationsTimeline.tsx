import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card } from '../ui/Card';
import { Button } from '../ui/Button';
import { LoadingSkeleton, ErrorState } from '../ui/StateViews';
import { api } from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import {
  Mail,
  MessageSquare,
  ArrowUpRight,
  ArrowDownLeft,
  RotateCcw,
  XCircle,
  CheckCircle2,
  AlertCircle,
  FileText,
  Clock,
  Sparkles,
  RefreshCw,
  ExternalLink,
} from 'lucide-react';
import { cn } from '../../utils/formatters';
import { NotificationOutbox } from '../../api/schemas';

interface CommunicationsTimelineProps {
  caseId: string;
  onRerunAnalysis?: () => void;
  className?: string;
}

export const CommunicationsTimeline: React.FC<CommunicationsTimelineProps> = ({
  caseId,
  onRerunAnalysis,
  className,
}) => {
  const queryClient = useQueryClient();
  const { isAnonymous } = useAuth();

  const [actionFeedback, setActionFeedback] = useState<string | null>(null);

  // Fetch communications outbox list
  const {
    data: messages,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['notifications', caseId],
    queryFn: () => api.getNotifications(caseId),
  });

  // Resend mutation
  const resendMutation = useMutation({
    mutationFn: (outboxId: string) => api.resendNotification(caseId, outboxId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications', caseId] });
      setActionFeedback('Notification re-dispatch initiated.');
      setTimeout(() => setActionFeedback(null), 3000);
    },
  });

  // Cancel mutation
  const cancelMutation = useMutation({
    mutationFn: (outboxId: string) => api.cancelNotification(caseId, outboxId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications', caseId] });
      setActionFeedback('Queued dispatch cancelled.');
      setTimeout(() => setActionFeedback(null), 3000);
    },
  });

  // Mark Manual Sent mutation
  const markManualSentMutation = useMutation({
    mutationFn: (outboxId: string) => api.markManualSent(caseId, outboxId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications', caseId] });
      setActionFeedback('Confirmed manual WhatsApp dispatch.');
      setTimeout(() => setActionFeedback(null), 3000);
    },
  });

  if (isLoading) {
    return (
      <div className="space-y-4 w-full">
        <LoadingSkeleton rows={5} />
      </div>
    );
  }

  if (isError) {
    return (
      <ErrorState
        title="Failed to load communications timeline"
        message={error instanceof Error ? error.message : 'Could not retrieve outbox records.'}
        onRetry={() => refetch()}
      />
    );
  }

  const renderStatusBadge = (status: NotificationOutbox['status'], scheduledFor?: string | null) => {
    switch (status) {
      case 'QUEUED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20">
            <Clock className="w-3 h-3" />
            Queued {scheduledFor ? `for ${new Date(scheduledFor).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}` : ''}
          </span>
        );
      case 'SENT':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-blue-500/10 text-blue-600 dark:text-blue-400 border border-blue-500/20">
            <CheckCircle2 className="w-3 h-3" />
            Sent
          </span>
        );
      case 'DELIVERED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-brand/10 text-brand-text-light dark:text-brand border border-brand/20">
            <CheckCircle2 className="w-3 h-3" />
            Delivered
          </span>
        );
      case 'MANUAL_PENDING':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-amber-500/15 text-amber-600 dark:text-amber-400 border border-amber-500/30">
            <Clock className="w-3 h-3" />
            Manual Pending
          </span>
        );
      case 'MANUAL_SENT':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-brand/10 text-brand-text-light dark:text-brand border border-brand/20">
            <CheckCircle2 className="w-3 h-3" />
            Manual Sent
          </span>
        );
      case 'BOUNCED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-red-500/10 text-red-600 dark:text-red-400 border border-red-500/20">
            <AlertCircle className="w-3 h-3" />
            Bounced
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-red-500/10 text-red-600 dark:text-red-400 border border-red-500/20">
            <XCircle className="w-3 h-3" />
            Failed
          </span>
        );
      default:
        return (
          <span className="px-2 py-0.5 rounded-full text-[11px] font-medium bg-surface-subtle text-foreground-muted">
            {status}
          </span>
        );
    }
  };

  return (
    <div className={cn('space-y-6', className)}>
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-base sm:text-lg font-bold font-heading text-foreground">
            Communications & Outbox Log
          </h2>
          <p className="text-xs text-foreground-muted mt-0.5">
            Audit trail of outbound notices, click-to-chat links, and unverified inbound buyer messages (R-78).
          </p>
        </div>

        <div className="flex items-center gap-2">
          {actionFeedback && (
            <span className="text-xs px-3 py-1 rounded-full bg-brand/20 text-brand font-medium animate-pulse self-start sm:self-auto">
              {actionFeedback}
            </span>
          )}
          <Button
            variant="secondary"
            size="sm"
            data-testid="timeline-refresh-btn"
            onClick={() => refetch()}
            className="h-8 text-xs"
          >
            <RefreshCw className="w-3 h-3 mr-1" />
            Refresh
          </Button>
        </div>
      </div>

      {/* Empty State */}
      {(!messages || messages.length === 0) ? (
        <Card className="p-12 text-center border border-border bg-surface flex flex-col items-center justify-center space-y-3">
          <div className="w-12 h-12 rounded-2xl bg-surface-subtle border border-border flex items-center justify-center text-foreground-muted">
            <Mail className="w-6 h-6" />
          </div>
          <h3 className="text-sm font-bold text-foreground">No Communications Recorded</h3>
          <p className="text-xs text-foreground-muted max-w-sm">
            No notices or WhatsApp dispatches have been sent for this case yet. Go to the Actions tab to prepare and review your first dispatch.
          </p>
        </Card>
      ) : (
        /* Timeline Items */
        <div className="relative pl-6 sm:pl-8 space-y-6 before:absolute before:left-3 sm:before:left-4 before:top-3 before:bottom-3 before:w-0.5 before:bg-border">
          {messages.map((item) => {
            const isInbound = item.isInbound || item.template_key === 'inbound_reply';

            return (
              <div key={item.id} className="relative group">
                {/* Timeline node icon */}
                <div className={cn(
                  'absolute -left-6 sm:-left-8 top-3 w-6 h-6 rounded-full border flex items-center justify-center shadow-xs',
                  isInbound
                    ? 'bg-amber-500/20 border-amber-500/50 text-amber-500'
                    : 'bg-surface border-border text-foreground-muted'
                )}>
                  {isInbound ? (
                    <ArrowDownLeft className="w-3.5 h-3.5" />
                  ) : (
                    <ArrowUpRight className="w-3.5 h-3.5 text-brand" />
                  )}
                </div>

                {/* Message Item Card */}
                <Card className={cn(
                  'p-4 sm:p-5 border transition-all text-xs space-y-3',
                  isInbound
                    ? 'border-amber-500/30 bg-amber-500/5'
                    : 'border-border bg-surface hover:border-border/80'
                )}>
                  {/* Top Header of Card */}
                  <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/50 pb-2.5">
                    <div className="flex items-center gap-2">
                      {/* Channel Pill */}
                      <span className={cn(
                        'inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold font-mono',
                        item.channel === 'EMAIL'
                          ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border border-blue-500/20'
                          : 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                      )}>
                        {item.channel === 'EMAIL' ? <Mail className="w-3 h-3" /> : <MessageSquare className="w-3 h-3" />}
                        {item.channel}
                      </span>

                      {/* Inbound Unverified message flag */}
                      {isInbound && (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-500 text-black shadow-xs">
                          <AlertCircle className="w-3 h-3" />
                          <span>Unverified message</span>
                        </span>
                      )}

                      {/* Sandbox reroute pill */}
                      {item.rerouted_to_sandbox && (
                        <span className="px-2 py-0.5 rounded-full text-[10px] bg-brand/10 text-brand border border-brand/20 font-medium">
                          Sandbox Rerouted
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-3">
                      {/* Status chip */}
                      {!isInbound && renderStatusBadge(item.status, item.scheduled_for)}

                      {/* Timestamp */}
                      <span className="text-[11px] text-foreground-muted font-mono">
                        {item.sent_at
                          ? new Date(item.sent_at).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })
                          : new Date(item.created_at).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })}
                      </span>
                    </div>
                  </div>

                  {/* Recipient / Sender Info */}
                  <div className="text-[11px] text-foreground-muted font-mono flex flex-wrap items-center gap-2">
                    <span>
                      {isInbound ? 'From: ' : 'To: '}
                      <strong className="text-foreground">{item.recipient}</strong>
                    </span>
                    {item.template_key && (
                      <span className="text-foreground-subtle">· Template: {item.template_key}</span>
                    )}
                    {item.provider_message_id && (
                      <span className="text-foreground-subtle">· ID: {item.provider_message_id}</span>
                    )}
                  </div>

                  {/* Rendered Body Text */}
                  <div className={cn(
                    'p-3.5 rounded-xl text-xs leading-relaxed whitespace-pre-wrap',
                    isInbound ? 'bg-surface border border-amber-500/20 text-foreground' : 'bg-surface-subtle text-foreground'
                  )}>
                    {item.rendered_body}
                  </div>

                  {/* Inbound Special Action: Offer to re-run analysis (Rule R-78) */}
                  {isInbound && (
                    <div className="p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                      <div className="space-y-0.5">
                        <div className="font-bold text-foreground text-xs flex items-center gap-1.5">
                          <Sparkles className="w-3.5 h-3.5 text-amber-500" />
                          <span>Buyer Reply Received via WhatsApp</span>
                        </div>
                        <p className="text-[11px] text-foreground-muted">
                          Stored as an unverified MESSAGE document. Re-run analysis to evaluate this claim against the ledger.
                        </p>
                      </div>

                      <Button
                        size="sm"
                        data-testid="timeline-rerun-analysis-btn"
                        onClick={onRerunAnalysis}
                        className="bg-amber-500 hover:bg-amber-400 text-black font-bold shrink-0"
                      >
                        <RefreshCw className="w-3.5 h-3.5 mr-1.5" />
                        Re-run analysis
                      </Button>
                    </div>
                  )}

                  {/* Outbound Action Buttons */}
                  {!isInbound && (
                    <div className="flex flex-wrap items-center justify-between gap-2 pt-1 border-t border-border/40">
                      <div className="flex items-center gap-2">
                        {/* Statement PDF Pill if attached */}
                        {item.channel === 'EMAIL' && (
                          <div className="flex items-center gap-1.5 text-[11px] text-foreground-muted font-mono">
                            <FileText className="w-3.5 h-3.5 text-brand" />
                            <span>Statement_of_Account.pdf</span>
                          </div>
                        )}

                        {/* wa.me link button if manual pending */}
                        {item.channel === 'WHATSAPP_LINK' && item.status === 'MANUAL_PENDING' && item.click_to_chat_url && (
                          <a
                            href={item.click_to_chat_url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 text-[11px] font-semibold text-brand hover:underline"
                          >
                            <ExternalLink className="w-3 h-3" />
                            Open WhatsApp Web
                          </a>
                        )}
                      </div>

                      <div className="flex items-center gap-2">
                        {/* Confirm I sent it for MANUAL_PENDING */}
                        {item.status === 'MANUAL_PENDING' && (
                          <Button
                            size="sm"
                            variant="secondary"
                            data-testid="timeline-confirm-sent-btn"
                            disabled={isAnonymous || markManualSentMutation.isPending}
                            onClick={() => markManualSentMutation.mutate(item.id)}
                            className="text-[11px] h-7 px-2.5 font-bold"
                          >
                            <CheckCircle2 className="w-3 h-3 mr-1 text-brand" />
                            Confirm I sent it
                          </Button>
                        )}

                        {/* Cancel for QUEUED or MANUAL_PENDING */}
                        {(item.status === 'QUEUED' || item.status === 'MANUAL_PENDING') && (
                          <Button
                            size="sm"
                            variant="ghost"
                            data-testid="timeline-cancel-btn"
                            disabled={isAnonymous || cancelMutation.isPending}
                            onClick={() => cancelMutation.mutate(item.id)}
                            className="text-[11px] h-7 px-2 text-foreground-muted hover:text-red-500"
                          >
                            Cancel
                          </Button>
                        )}

                        {/* Resend for FAILED, BOUNCED, or QUEUED */}
                        {(item.status === 'FAILED' || item.status === 'BOUNCED') && (
                          <Button
                            size="sm"
                            variant="secondary"
                            data-testid="timeline-resend-btn"
                            disabled={isAnonymous || resendMutation.isPending}
                            onClick={() => resendMutation.mutate(item.id)}
                            className="text-[11px] h-7 px-2.5 font-bold"
                          >
                            <RotateCcw className="w-3 h-3 mr-1" />
                            Resend
                          </Button>
                        )}
                      </div>
                    </div>
                  )}
                </Card>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
