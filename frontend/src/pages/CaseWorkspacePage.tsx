import React, { useState } from 'react';
import { useParams, useOutletContext } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { MoneyText } from '../components/common/MoneyText';
import { StatusBadge } from '../components/common/StatusBadge';
import { LoadingSkeleton, ErrorState } from '../components/ui/StateViews';
import { api } from '../api/client';
import {
  Download,
  RotateCcw,
  Check,
  AlertCircle,
  ExternalLink,
  ShieldCheck,
  Edit2,
  Printer,
  FileCode,
  FileText,
  UserCheck,
  Mail,
  MessageSquare,
} from 'lucide-react';
import { cn } from '../utils/formatters';
import { SendActionPanel } from '../components/notifications/SendActionPanel';
import { CommunicationsTimeline } from '../components/notifications/CommunicationsTimeline';
import { useToast } from '../context/ToastContext';

export const CaseWorkspacePage: React.FC = () => {
  const { showToast } = useToast();
  const { id } = useParams<{ id: string }>();
  const caseId = id || 'DP-0142';

  const context = useOutletContext<{
    activeTab?: string;
    setActiveTab?: (tab: string) => void;
  }>();

  // Tab state
  const [localTab, setLocalTab] = useState('Summary');
  const activeTab = context?.activeTab ?? localTab;
  const setActiveTab = context?.setActiveTab ?? setLocalTab;

  // Local interaction states
  const [langDraft, setLangDraft] = useState<'EN' | 'HI'>('EN');
  const [selectedDocId, setSelectedDocId] = useState('doc-02'); // default to Tax Invoice
  const [viewerMode, setViewerMode] = useState<'ORIGINAL' | 'EXTRACTED'>('ORIGINAL');
  const [activeHighlightFieldId, setActiveHighlightFieldId] = useState<string | null>('f-6'); // Default highlights Total payable
  const [showConfirmRateDialog, setShowConfirmRateDialog] = useState(false);
  const [rateConfirmed, setRateConfirmed] = useState(false);
  const [actionStatus, setActionStatus] = useState<'PENDING' | 'APPROVED' | 'REJECTED'>('PENDING');
  const [isEditingDraft, setIsEditingDraft] = useState(false);
  const [customDraftEn, setCustomDraftEn] = useState<string>('');
  const [customDraftHi, setCustomDraftHi] = useState<string>('');
  const [showExportModal, setShowExportModal] = useState(false);
  const [selectedGateDetail, setSelectedGateDetail] = useState<string | null>(null);
  const [rerunFeedback, setRerunFeedback] = useState<string | null>(null);
  const [mobileDocPane, setMobileDocPane] = useState<'FILES' | 'VIEWER' | 'FIELDS'>('VIEWER');
  const [docZoom, setDocZoom] = useState<number>(100);

  // Fetch case details using TanStack Query
  const {
    data: caseData,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['case', caseId],
    queryFn: () => api.getCase(caseId),
  });

  const {
    data: buyerContact,
    refetch: refetchBuyerContact,
  } = useQuery({
    queryKey: ['buyerContact', caseId],
    queryFn: () => api.getBuyerContact(caseId),
  });

  if (isLoading) {
    return (
      <div className="space-y-6 w-full">
        <LoadingSkeleton rows={8} />
      </div>
    );
  }

  if (isError || !caseData) {
    return (
      <div className="w-full py-8">
        <ErrorState
          title="Failed to load case workspace"
          message={error instanceof Error ? error.message : 'The requested case could not be retrieved.'}
          onRetry={() => refetch()}
        />
      </div>
    );
  }

  // Handle proof chip click
  const handleProofClick = (proofDocName?: string) => {
    setActiveTab('Documents');
    if (!proofDocName) return;
    if (proofDocName.toLowerCase().includes('invoice')) {
      setSelectedDocId('doc-02');
      setActiveHighlightFieldId('f-6');
    } else if (proofDocName.toLowerCase().includes('bank')) {
      setSelectedDocId('doc-03');
      setActiveHighlightFieldId(null);
    } else if (proofDocName.toLowerCase().includes('cn')) {
      setSelectedDocId('doc-04');
      setActiveHighlightFieldId(null);
    } else if (proofDocName.toLowerCase().includes('message')) {
      setSelectedDocId('doc-05');
      setActiveHighlightFieldId(null);
    } else {
      setSelectedDocId('doc-missing');
      setActiveHighlightFieldId(null);
    }
  };

  // Handle Action Approval
  const handleApproveAction = async () => {
    if (caseData.proposedAction) {
      await api.approveAction(caseData.proposedAction.id);
    }
    setActionStatus('APPROVED');
    showToast(`Approved proposed action for ${caseData.code}`, 'success');
  };

  // Handle Action Rejection
  const handleRejectAction = async () => {
    if (caseData.proposedAction) {
      await api.rejectAction(caseData.proposedAction.id);
    }
    setActionStatus('REJECTED');
    showToast(`Rejected proposed action for ${caseData.code}`, 'info');
  };

  // Handle Rate Confirmation
  const handleConfirmRate = async () => {
    await api.confirmBankRate('rate-candidate-1');
    setRateConfirmed(true);
    setShowConfirmRateDialog(false);
    showToast('RBI Bank Rate (6.75%) confirmed and locked', 'success');
  };

  // Handle Export Case Pack (JSON)
  const handleExportJson = () => {
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(caseData, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute('href', dataStr);
    downloadAnchor.setAttribute('download', `case-${caseData.code}-pack.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
    setShowExportModal(false);
    showToast(`Exported full case pack JSON for ${caseData.code}`, 'success');
  };

  // Handle Export Case Pack (Printable PDF Summary)
  const handleExportPdf = () => {
    setShowExportModal(false);
    showToast('Opening printable case summary...', 'info');
    window.print();
  };

  // Handle Export Case Pack (CSV)
  const handleExportCsv = () => {
    const headers = ['Document ID', 'Filename', 'Type', 'Status', 'Extracted Fields'];
    const rows = (caseData.documents || []).map((d) => [
      d.id,
      `"${(d.filename || (d as any).title || 'Document').replace(/"/g, '""')}"`,
      d.docType,
      d.status,
      d.extractedFields?.length || 0,
    ]);
    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `case-${caseData.code}-summary.csv`;
    document.body.appendChild(link);
    link.click();
    setTimeout(() => {
      if (link.parentNode) link.parentNode.removeChild(link);
      URL.revokeObjectURL(url);
    }, 200);
    setShowExportModal(false);
    showToast(`Exported CSV summary for ${caseData.code}`, 'success');
  };

  // Handle Download Original File
  const handleDownloadOriginal = () => {
    const filename = selectedDoc?.filename || 'INV-2026-0142.pdf';
    const dummyPdfContent = `%PDF-1.4\n% DuesPilot Case ${caseData.code} Original File: ${filename}\n`;
    const blob = new Blob([dummyPdfContent], { type: 'application/pdf' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    showToast(`Downloaded original document: ${filename}`, 'success');
  };

  // Re-run extraction or analysis
  const handleRerun = () => {
    setRerunFeedback('Re-running analysis pipeline...');
    setTimeout(() => {
      setRerunFeedback('Analysis updated with latest evidence.');
      showToast('Analysis re-run complete with latest evidence', 'success');
      setTimeout(() => setRerunFeedback(null), 3500);
    }, 1200);
  };

  const selectedDoc = caseData.documents.find((d) => d.id === selectedDocId);
  const isMissingDocSelected = selectedDocId === 'doc-missing';

  return (
    <div className="space-y-6 w-full">
      {/* Workspace Top Header matching design */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl sm:text-3xl font-bold font-heading text-foreground tracking-tight">
              {caseData.code} · {caseData.title}
            </h1>
            <StatusBadge status={caseData.status} size="sm" />
            {rerunFeedback && (
              <span className="text-xs px-2.5 py-1 rounded-full bg-brand/20 text-brand font-medium animate-pulse">
                {rerunFeedback}
              </span>
            )}
          </div>
          <p className="text-xs sm:text-sm text-foreground-muted mt-1">
            Invoice INV-2026-0142 · {caseData.documents.length} documents · analyzed with{' '}
            {caseData.modelRouting} · {caseData.lastRunAt}
          </p>
        </div>

        <div className="flex items-center gap-2.5 shrink-0">
          <Button
            variant="secondary"
            size="sm"
            data-testid="re-run-btn"
            onClick={handleRerun}
            className="h-9"
          >
            <RotateCcw className="w-3.5 h-3.5 mr-1.5" />
            Re-run
          </Button>
          <Button
            size="sm"
            data-testid="export-case-pack-btn"
            onClick={() => setShowExportModal(true)}
            className="h-9"
          >
            <Download className="w-3.5 h-3.5 mr-1.5" />
            Export case pack
          </Button>
        </div>
      </div>

      {/* ========================================================================= */}
      {/* TAB 1: SUMMARY (Primary Workspace View)                                   */}
      {/* ========================================================================= */}
      {activeTab === 'Summary' && (
        <div className="space-y-6">
          {/* Top Row: 4 Metric Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
            <Card className="p-3.5 sm:p-5 flex flex-col justify-between min-h-[8rem] sm:min-h-[8.5rem]">
              <span className="text-[11px] sm:text-xs font-semibold text-foreground-muted uppercase tracking-wider">
                Invoice total
              </span>
              <div className="mt-2 text-xl sm:text-2xl lg:text-3xl font-extrabold font-heading text-foreground tracking-tight tabular-nums break-words">
                <MoneyText paise={caseData.invoicedPaise} className="font-extrabold text-xl sm:text-2xl lg:text-3xl" />
              </div>
            </Card>

            <Card className="p-3.5 sm:p-5 flex flex-col justify-between min-h-[8rem] sm:min-h-[8.5rem]">
              <span className="text-[11px] sm:text-xs font-semibold text-foreground-muted uppercase tracking-wider">
                Confirmed payments
              </span>
              <div className="mt-2 text-xl sm:text-2xl lg:text-3xl font-extrabold font-heading text-foreground tracking-tight tabular-nums break-words">
                −<MoneyText paise={caseData.confirmedPaidPaise} className="font-extrabold text-xl sm:text-2xl lg:text-3xl" />
              </div>
            </Card>

            <Card className="p-3.5 sm:p-5 flex flex-col justify-between min-h-[8rem] sm:min-h-[8.5rem]">
              <span className="text-[11px] sm:text-xs font-semibold text-foreground-muted uppercase tracking-wider">
                Credit note
              </span>
              <div className="mt-2 text-xl sm:text-2xl lg:text-3xl font-extrabold font-heading text-foreground tracking-tight tabular-nums break-words">
                −<MoneyText paise={caseData.creditNotePaise} className="font-extrabold text-xl sm:text-2xl lg:text-3xl" />
              </div>
            </Card>

            <Card variant="brand" className="p-3.5 sm:p-5 flex flex-col justify-between min-h-[8rem] sm:min-h-[8.5rem]">
              <span className="text-[11px] sm:text-xs font-semibold text-black/80 uppercase tracking-wider">
                Reconciled balance
              </span>
              <div className="mt-2 text-xl sm:text-2xl lg:text-3xl font-extrabold font-heading text-black tracking-tight tabular-nums break-words">
                <MoneyText
                  paise={caseData.reconciledBalancePaise}
                  className="text-black font-extrabold text-xl sm:text-2xl lg:text-3xl"
                />
              </div>
            </Card>
          </div>

          {/* Middle Row 1: Ledger & Proofs + Balance Walk */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
            {/* Ledger table (8 cols) */}
            <Card className="lg:col-span-8 p-5">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-sm font-bold font-heading text-foreground">
                  Ledger and proofs
                </h2>
                <span className="text-xs text-foreground-muted">
                  Click a proof chip to view source document
                </span>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead>
                    <tr className="border-b border-border text-foreground-muted font-medium">
                      <th className="py-2.5 px-3">Date</th>
                      <th className="py-2.5 px-3">Entry</th>
                      <th className="py-2.5 px-3">Amount</th>
                      <th className="py-2.5 px-3">Status</th>
                      <th className="py-2.5 px-3">Proof</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {caseData.ledger.map((entry) => (
                      <tr key={entry.id} className="hover:bg-surface-subtle/50 transition-colors">
                        <td className="py-3 px-3 text-foreground-muted font-mono text-[11px]">
                          {entry.date}
                        </td>
                        <td className="py-3 px-3 font-medium text-foreground">
                          {entry.description}
                        </td>
                        <td className="py-3 px-3 font-semibold text-foreground tabular-nums">
                          {entry.amountPaise < 0 ? '−' : '+'}
                          <MoneyText paise={Math.abs(entry.amountPaise)} />
                        </td>
                        <td className="py-3 px-3">
                          <StatusBadge status={entry.status} size="sm" />
                        </td>
                        <td className="py-3 px-3">
                          <button
                            type="button"
                            onClick={() => handleProofClick(entry.proofDoc)}
                            className="inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] bg-surface-subtle border border-border text-foreground hover:border-brand hover:text-brand transition-colors cursor-pointer"
                            title={`Inspect ${entry.proofDoc} in Documents tab`}
                          >
                            {entry.proofDoc} {entry.proofPage ? `p.${entry.proofPage}` : ''}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>

            {/* Balance walk waterfall (4 cols) */}
            <Card className="lg:col-span-4 p-5 flex flex-col justify-between">
              <h2 className="text-sm font-bold font-heading text-foreground mb-4">
                Balance walk
              </h2>
              <div className="flex items-end justify-between gap-3 h-44 px-3 my-auto">
                {/* 1. Invoice */}
                <div className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end">
                  <span className="text-[10px] text-foreground font-semibold tabular-nums">
                    1,18,000
                  </span>
                  <div className="w-full h-32 rounded-sm bg-foreground-muted/60" />
                  <span className="text-[11px] text-foreground-muted font-medium mt-1">
                    Invoice
                  </span>
                </div>

                {/* 2. Paid */}
                <div className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end">
                  <span className="text-[10px] text-foreground font-semibold tabular-nums">
                    50,000
                  </span>
                  <div className="w-full h-16 mb-16 rounded-sm bg-brand/80" />
                  <span className="text-[11px] text-foreground-muted font-medium mt-1">
                    Paid
                  </span>
                </div>

                {/* 3. Credit */}
                <div className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end">
                  <span className="text-[10px] text-foreground font-semibold tabular-nums">
                    11,800
                  </span>
                  <div className="w-full h-4 mb-12 rounded-sm bg-brand/60" />
                  <span className="text-[11px] text-foreground-muted font-medium mt-1">
                    Credit
                  </span>
                </div>

                {/* 4. Balance */}
                <div className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end">
                  <span className="text-[10px] text-foreground font-semibold tabular-nums">
                    56,200
                  </span>
                  <div className="w-full h-20 rounded-sm bg-brand" />
                  <span className="text-[11px] text-foreground font-bold mt-1">
                    Balance
                  </span>
                </div>
              </div>
            </Card>
          </div>

          {/* Middle Row 2: Conflicts + Eligibility Gates + Interest */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            {/* Conflicts */}
            <Card className="p-5 flex flex-col justify-between">
              <div>
                <h2 className="text-sm font-bold font-heading text-foreground mb-3">
                  Conflicts · {caseData.conflicts.length}
                </h2>
                <div className="space-y-4">
                  {caseData.conflicts.map((conf) => (
                    <div key={conf.id} className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="px-1.5 py-0.5 rounded-full text-[10px] font-bold border border-amber-500/50 text-amber-500">
                          {conf.ruleId}
                        </span>
                        <span className="text-xs font-bold text-foreground">
                          {conf.title}
                        </span>
                      </div>
                      <p className="text-xs text-foreground-muted pl-6">
                        {conf.description}
                      </p>
                      {conf.evidenceRef && (
                        <div className="pl-6 pt-1">
                          <button
                            type="button"
                            onClick={() => handleProofClick(conf.evidenceRef || '')}
                            className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] bg-surface-subtle border border-border text-foreground hover:border-brand hover:text-brand cursor-pointer"
                          >
                            {conf.evidenceRef}
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </Card>

            {/* Eligibility Gates */}
            <Card className="p-5 flex flex-col justify-between">
              <div>
                <h2 className="text-sm font-bold font-heading text-foreground mb-3">
                  Eligibility gates
                </h2>
                <div className="flex flex-wrap gap-2 mb-3">
                  {caseData.gates.map((g) => {
                    let borderClass = 'border-amber-500/50 text-amber-500';
                    if (g.status === 'ELIGIBLE_FOR_REVIEW') {
                      borderClass = 'border-brand text-brand-text-light dark:text-brand';
                    } else if (g.status === 'OUT_OF_SCOPE') {
                      borderClass = 'border-border text-foreground-muted';
                    }

                    return (
                      <button
                        type="button"
                        key={g.gateId}
                        onClick={() => setSelectedGateDetail(selectedGateDetail === g.gateId ? null : g.gateId)}
                        className={cn(
                          'px-2.5 py-1 rounded-full text-xs font-medium border bg-surface-subtle/50 hover:opacity-80 transition-all text-left cursor-pointer',
                          borderClass
                        )}
                        title="Click to view gate rationale"
                      >
                        {g.label}
                      </button>
                    );
                  })}
                </div>

                {/* Gate Detail Popover/Drawer when clicked */}
                {selectedGateDetail && (
                  <div className="p-3 mb-2 rounded-lg bg-surface-subtle border border-border text-xs space-y-1">
                    {caseData.gates
                      .filter((g) => g.gateId === selectedGateDetail)
                      .map((g) => (
                        <div key={g.gateId}>
                          <div className="font-bold text-foreground mb-1">{g.name}:</div>
                          <ul className="list-disc list-inside space-y-0.5 text-foreground-muted">
                            {g.reasons.map((r, i) => (
                              <li key={i}>{r}</li>
                            ))}
                          </ul>
                        </div>
                      ))}
                  </div>
                )}
              </div>
              <p className="text-[11px] text-foreground-muted">
                Workflow states, not legal findings. Click each gate to inspect its reasons.
              </p>
            </Card>

            {/* Interest */}
            <Card className="p-5 flex flex-col justify-between">
              <div>
                <h2 className="text-sm font-bold font-heading text-foreground mb-2">
                  Interest
                </h2>
                <p className="text-xs text-foreground-muted mb-3">
                  {rateConfirmed
                    ? 'Provisional interest calculation pending delivery confirmation.'
                    : caseData.interest.reasons[0]}
                </p>
                <div className="p-3 rounded-xl bg-surface-subtle border border-border text-xs text-foreground-muted space-y-1">
                  <div className="text-[11px] font-semibold text-foreground">
                    Bank rate
                  </div>
                  <div>
                    {rateConfirmed ? (
                      <span className="text-brand-text-light dark:text-brand font-bold">
                        6.75% · confirmed by user · source rbi.org.in
                      </span>
                    ) : (
                      'Candidate: [ — ] · source rbi.org.in · effective [ — ]'
                    )}
                  </div>
                </div>
              </div>
              <div className="mt-4">
                <Button
                  variant="outline"
                  size="sm"
                  data-testid="fetch-rate-btn"
                  onClick={() => setShowConfirmRateDialog(true)}
                  className="w-full text-xs h-9"
                >
                  {rateConfirmed ? 'Rate confirmed (6.75%)' : 'Fetch and confirm rate'}
                </Button>
              </div>
            </Card>
          </div>

          {/* Bottom Row: Proposed Action + Audit Trail */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
            {/* Proposed Action (8 cols) */}
            <Card className="lg:col-span-8 p-5">
              <div className="flex items-center justify-between gap-4 mb-3">
                <h2 className="text-sm font-bold font-heading text-foreground">
                  {caseData.proposedAction?.title}
                </h2>
                <div className="flex items-center p-0.5 rounded-full bg-surface-subtle border border-border text-xs">
                  <button
                    type="button"
                    onClick={() => setLangDraft('EN')}
                    className={cn(
                      'px-3 py-1 rounded-full text-xs font-medium transition-colors cursor-pointer',
                      langDraft === 'EN'
                        ? 'bg-foreground text-background font-bold'
                        : 'text-foreground-muted hover:text-foreground'
                    )}
                  >
                    English
                  </button>
                  <button
                    type="button"
                    onClick={() => setLangDraft('HI')}
                    className={cn(
                      'px-3 py-1 rounded-full text-xs font-medium transition-colors cursor-pointer',
                      langDraft === 'HI'
                        ? 'bg-foreground text-background font-bold'
                        : 'text-foreground-muted hover:text-foreground'
                    )}
                  >
                    हिंदी
                  </button>
                </div>
              </div>

              {/* Draft Box / Inline Edit Mode */}
              {isEditingDraft ? (
                <div className="space-y-3 mb-4">
                  <textarea
                    rows={3}
                    value={
                      langDraft === 'EN'
                        ? customDraftEn || caseData.proposedAction?.englishDraft || ''
                        : customDraftHi || caseData.proposedAction?.hindiDraft || ''
                    }
                    onChange={(e) => {
                      if (langDraft === 'EN') setCustomDraftEn(e.target.value);
                      else setCustomDraftHi(e.target.value);
                    }}
                    className="w-full p-3 rounded-xl bg-surface-subtle border border-brand text-xs text-foreground focus:outline-hidden"
                  />
                  <div className="flex justify-end gap-2">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => setIsEditingDraft(false)}
                      className="h-7 text-xs"
                    >
                      Cancel
                    </Button>
                    <Button
                      size="sm"
                      onClick={() => setIsEditingDraft(false)}
                      className="h-7 text-xs"
                    >
                      Save draft
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="p-4 rounded-xl bg-surface-subtle border border-border text-xs leading-relaxed text-foreground mb-4">
                  {langDraft === 'EN'
                    ? customDraftEn || caseData.proposedAction?.englishDraft
                    : customDraftHi || caseData.proposedAction?.hindiDraft}
                </div>
              )}

              {/* Action Buttons & Safety Notice */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <Button
                    size="sm"
                    disabled={actionStatus === 'APPROVED'}
                    onClick={handleApproveAction}
                    className="h-8 text-xs font-bold"
                  >
                    <Check className="w-3.5 h-3.5 mr-1 stroke-[3]" />
                    {actionStatus === 'APPROVED' ? 'Approved' : 'Approve draft'}
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => setIsEditingDraft(!isEditingDraft)}
                    className="h-8 text-xs"
                  >
                    <Edit2 className="w-3 h-3 mr-1" />
                    {isEditingDraft ? 'Done' : 'Edit'}
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    disabled={actionStatus === 'REJECTED'}
                    onClick={handleRejectAction}
                    className="h-8 text-xs"
                  >
                    Reject
                  </Button>
                </div>
                <span className="text-[11px] text-foreground-muted">
                  Nothing is sent automatically.
                </span>
              </div>
            </Card>

            {/* Audit Trail (4 cols) */}
            <Card className="lg:col-span-4 p-5 flex flex-col justify-between">
              <div>
                <h2 className="text-sm font-bold font-heading text-foreground mb-4">
                  Audit trail
                </h2>
                <div className="space-y-3.5 text-xs">
                  <div>
                    <span className="font-bold text-foreground">Extraction</span>{' '}
                    <span className="text-foreground-muted">· Nano · 5 docs · 1 escalation</span>
                  </div>
                  <div>
                    <span className="font-bold text-foreground">Conflicts</span>{' '}
                    <span className="text-foreground-muted">· Super · evidence checked</span>
                  </div>
                </div>
              </div>
              <div className="border-t border-border pt-4 mt-4 text-xs text-foreground-muted">
                <span className="font-bold text-foreground">Cost</span> $0.016 · 14 model calls
              </div>
            </Card>

            {/* Buyer Contact & Opt-in Status on Summary */}
            <Card className="lg:col-span-12 p-5">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
                <div className="flex items-center gap-2">
                  <UserCheck className="w-4 h-4 text-brand" />
                  <h2 className="text-sm font-bold font-heading text-foreground">
                    Buyer Contact & Communication Opt-In
                  </h2>
                </div>
                {buyerContact?.unsubscribedAt ? (
                  <span className="text-xs px-2.5 py-1 rounded-full bg-red-500/10 text-red-500 font-semibold border border-red-500/20">
                    Unsubscribed: {buyerContact.unsubscribedAt}
                  </span>
                ) : (
                  <span className="text-xs px-2.5 py-1 rounded-full bg-brand/10 text-brand font-semibold border border-brand/20">
                    Opt-in Active
                  </span>
                )}
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="p-3.5 rounded-xl bg-surface-subtle border border-border">
                  <span className="text-[11px] font-semibold text-foreground-muted uppercase tracking-wider block mb-1">
                    Buyer Contact
                  </span>
                  <div className="text-sm font-bold text-foreground">
                    {buyerContact?.buyerName || 'Kestrel Procurement Office'}
                  </div>
                  <div className="text-xs text-foreground-muted mt-1">
                    Authorized Procurement Officer
                  </div>
                </div>

                <div className="p-3.5 rounded-xl bg-surface-subtle border border-border flex items-start justify-between">
                  <div>
                    <div className="flex items-center gap-1.5 mb-1">
                      <Mail className="w-3.5 h-3.5 text-foreground-muted" />
                      <span className="text-[11px] font-semibold text-foreground-muted uppercase tracking-wider">
                        Email
                      </span>
                    </div>
                    <div className="text-xs font-mono text-foreground font-medium truncate max-w-[160px]">
                      {buyerContact?.email || 'ap@kestrel-industrial.example.com'}
                    </div>
                  </div>
                  <span
                    className={cn(
                      'text-[10px] font-bold px-2 py-0.5 rounded-full border shrink-0',
                      buyerContact?.emailOptIn
                        ? 'border-brand/40 bg-brand/10 text-brand'
                        : 'border-red-500/40 bg-red-500/10 text-red-500'
                    )}
                  >
                    {buyerContact?.emailOptIn ? 'Opted in' : 'Opted out'}
                  </span>
                </div>

                <div className="p-3.5 rounded-xl bg-surface-subtle border border-border flex items-start justify-between">
                  <div>
                    <div className="flex items-center gap-1.5 mb-1">
                      <MessageSquare className="w-3.5 h-3.5 text-foreground-muted" />
                      <span className="text-[11px] font-semibold text-foreground-muted uppercase tracking-wider">
                        WhatsApp (E.164)
                      </span>
                    </div>
                    <div className="text-xs font-mono text-foreground font-medium">
                      {buyerContact?.whatsappNumber || '+91 98765 43210'}
                    </div>
                  </div>
                  <span
                    className={cn(
                      'text-[10px] font-bold px-2 py-0.5 rounded-full border shrink-0',
                      buyerContact?.whatsappOptIn
                        ? 'border-brand/40 bg-brand/10 text-brand'
                        : 'border-red-500/40 bg-red-500/10 text-red-500'
                    )}
                  >
                    {buyerContact?.whatsappOptIn ? 'Opted in' : 'Opted out'}
                  </span>
                </div>
              </div>

              <div className="mt-3.5 p-3 rounded-lg bg-surface/50 border border-border text-xs text-foreground-muted flex items-start gap-2">
                <ShieldCheck className="w-4 h-4 text-brand shrink-0 mt-0.5" />
                <div className="leading-relaxed">
                  <span className="font-semibold text-foreground">Consent Note: </span>
                  {buyerContact?.consentNote ||
                    'Transactional consent documented in vendor onboarding. Channel communication restricted to payment reconciliation, ledger verification, and evidence requests under MSMEDA guidelines.'}
                </div>
              </div>
            </Card>

            {/* Send Panel on Summary */}
            <div className="lg:col-span-12">
              <SendActionPanel
                caseId={caseId}
                caseCode={caseData.code}
                actionId={caseData.proposedAction?.id}
                actionTitle={caseData.proposedAction?.title}
                englishDraft={customDraftEn || caseData.proposedAction?.englishDraft}
                hindiDraft={customDraftHi || caseData.proposedAction?.hindiDraft}
                contact={buyerContact}
                onSuccess={() => {
                  refetch();
                  refetchBuyerContact();
                }}
              />
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 2: DOCUMENTS & PROOFS (Matching Documents.png)                        */}
      {/* ========================================================================= */}
      {activeTab === 'Documents' && (
        <div className="space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h2 className="text-xl sm:text-2xl font-bold font-heading text-foreground tracking-tight">
                {caseData.code} · Documents and proofs
              </h2>
              <p className="text-xs sm:text-sm text-foreground-muted mt-0.5">
                The original files, with every extracted value linked to its source. Unverified values are excluded from calculations.
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="secondary"
                size="sm"
                data-testid="download-original-btn"
                onClick={handleDownloadOriginal}
                className="h-8 text-xs"
              >
                <Download className="w-3.5 h-3.5 mr-1" />
                Download original
              </Button>
              <Button
                size="sm"
                data-testid="export-case-pack-docs-btn"
                onClick={() => setShowExportModal(true)}
                className="h-8 text-xs"
              >
                <Download className="w-3.5 h-3.5 mr-1" />
                Export case pack
              </Button>
            </div>
          </div>

          {/* Mobile subtabs for 3-pane layout (<1024px) */}
          <div data-testid="workspace-pane-switcher" className="lg:hidden flex items-center p-1 rounded-xl bg-surface-subtle border border-border text-xs mb-4">
            <button
              type="button"
              data-testid="pane-tab-files"
              onClick={() => setMobileDocPane('FILES')}
              className={cn(
                'flex-1 py-2 px-2 rounded-lg font-medium transition-colors text-center cursor-pointer min-h-[44px] flex items-center justify-center',
                mobileDocPane === 'FILES' ? 'bg-surface font-bold text-foreground shadow-xs' : 'text-foreground-muted hover:text-foreground'
              )}
            >
              Case Files (6)
            </button>
            <button
              type="button"
              data-testid="pane-tab-viewer"
              onClick={() => setMobileDocPane('VIEWER')}
              className={cn(
                'flex-1 py-2 px-2 rounded-lg font-medium transition-colors text-center cursor-pointer min-h-[44px] flex items-center justify-center',
                mobileDocPane === 'VIEWER' ? 'bg-surface font-bold text-foreground shadow-xs' : 'text-foreground-muted hover:text-foreground'
              )}
            >
              Proof Viewer
            </button>
            <button
              type="button"
              data-testid="pane-tab-fields"
              onClick={() => setMobileDocPane('FIELDS')}
              className={cn(
                'flex-1 py-2 px-2 rounded-lg font-medium transition-colors text-center cursor-pointer min-h-[44px] flex items-center justify-center',
                mobileDocPane === 'FIELDS' ? 'bg-surface font-bold text-foreground shadow-xs' : 'text-foreground-muted hover:text-foreground'
              )}
            >
              Fields & Provenance
            </button>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
            {/* Left: Case Files List (3 cols) */}
            <Card className={cn("lg:col-span-3 p-5", mobileDocPane !== 'FILES' && 'hidden lg:block')}>
              <h3 className="text-sm font-bold font-heading text-foreground mb-4">
                Case files · 6
              </h3>
              <div className="space-y-2">
                {caseData.documents.map((doc) => {
                  const isSelected = doc.id === selectedDocId;
                  const isMessage = doc.docType === 'MESSAGE';
                  return (
                    <div
                      key={doc.id}
                      onClick={() => {
                        setSelectedDocId(doc.id);
                        setActiveHighlightFieldId(null);
                        setMobileDocPane('VIEWER');
                      }}
                      className={cn(
                        'p-3 rounded-xl border transition-all cursor-pointer min-h-[44px]',
                        isSelected
                          ? 'border-brand bg-brand/5 shadow-xs'
                          : 'border-border bg-surface-subtle/50 hover:bg-surface-subtle'
                      )}
                    >
                      <div className="text-xs font-bold text-foreground flex items-center justify-between">
                        <span>{doc.filename}</span>
                        {isSelected && <span className="w-2 h-2 rounded-full bg-brand" />}
                      </div>
                      <div className="mt-1">
                        {isMessage ? (
                          <span className="text-[10px] text-amber-500 font-medium">
                            Message · Unverified claim
                          </span>
                        ) : (
                          <span className="text-[10px] text-brand-text-light dark:text-brand font-medium">
                            {doc.docType.replace('_', ' ')} · Verified
                          </span>
                        )}
                      </div>
                    </div>
                  );
                })}

                {/* Missing Delivery Challan Placeholder */}
                <div
                  onClick={() => {
                    setSelectedDocId('doc-missing');
                    setActiveHighlightFieldId(null);
                    setMobileDocPane('VIEWER');
                  }}
                  className={cn(
                    'p-3 rounded-xl border border-dashed transition-all cursor-pointer min-h-[44px]',
                    isMissingDocSelected
                      ? 'border-amber-500 bg-amber-500/10'
                      : 'border-amber-500/40 bg-amber-500/5 hover:bg-amber-500/10'
                  )}
                >
                  <div className="text-xs font-bold text-foreground">
                    Delivery challan
                  </div>
                  <div className="mt-1">
                    <span className="text-[10px] text-amber-500 font-medium">
                      Missing
                    </span>
                  </div>
                </div>
              </div>
            </Card>

            {/* Middle: Document Viewer with verified green marks (6 cols) */}
            <Card data-testid="proof-viewer" className={cn("lg:col-span-6 p-5 flex flex-col justify-between", mobileDocPane !== 'VIEWER' && 'hidden lg:flex')}>
              <div>
                <div className="flex flex-wrap items-center justify-between gap-2 pb-3 mb-4 border-b border-border">
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setViewerMode('ORIGINAL')}
                      className={cn(
                        'px-2.5 py-1 rounded-full text-xs font-bold transition-colors cursor-pointer min-h-[32px] flex items-center',
                        viewerMode === 'ORIGINAL'
                          ? 'bg-foreground text-background'
                          : 'text-foreground-muted hover:text-foreground'
                      )}
                    >
                      Original
                    </button>
                    <button
                      type="button"
                      onClick={() => setViewerMode('EXTRACTED')}
                      className={cn(
                        'px-2.5 py-1 rounded-full text-xs font-bold transition-colors cursor-pointer min-h-[32px] flex items-center',
                        viewerMode === 'EXTRACTED'
                          ? 'bg-foreground text-background'
                          : 'text-foreground-muted hover:text-foreground'
                      )}
                    >
                      Extracted text
                    </button>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="flex items-center gap-1 bg-surface-subtle border border-border rounded-lg p-0.5">
                      <button
                        type="button"
                        aria-label="Zoom out"
                        onClick={() => setDocZoom((prev) => Math.max(70, prev - 15))}
                        className="w-6 h-6 rounded bg-surface hover:bg-surface-subtle flex items-center justify-center text-xs font-bold cursor-pointer"
                      >
                        −
                      </button>
                      <span className="text-[11px] font-mono text-foreground-muted px-1">{docZoom}%</span>
                      <button
                        type="button"
                        aria-label="Zoom in"
                        onClick={() => setDocZoom((prev) => Math.min(150, prev + 15))}
                        className="w-6 h-6 rounded bg-surface hover:bg-surface-subtle flex items-center justify-center text-xs font-bold cursor-pointer"
                      >
                        +
                      </button>
                    </div>
                    <span className="text-xs text-foreground-muted hidden sm:inline">
                      Page 1 of 1
                    </span>
                  </div>
                </div>

                {/* VIEWER CONTENT: Depending on selected document & mode */}
                {isMissingDocSelected ? (
                  /* Missing Document State */
                  <div className="p-8 rounded-xl bg-surface-subtle border border-dashed border-amber-500/40 text-center space-y-3 my-6">
                    <AlertCircle className="w-10 h-10 text-amber-500 mx-auto" />
                    <h4 className="font-bold text-base text-foreground">
                      Delivery challan — missing
                    </h4>
                    <p className="text-xs text-foreground-muted max-w-md mx-auto leading-relaxed">
                      No signed delivery challan or proof of physical goods receipt has been uploaded for this transaction.
                      Statutory eligibility Gate B requires delivery proof or a deemed acceptance record.
                    </p>
                  </div>
                ) : viewerMode === 'ORIGINAL' ? (
                  /* Original Document View (Synthetic Paper Document Canvas) */
                  <div className="p-6 rounded-xl bg-white text-black font-sans shadow-md border border-slate-200 text-xs leading-relaxed transition-all">
                    {selectedDoc?.docType === 'INVOICE' && (
                      <div>
                        <div className="flex justify-between items-start border-b border-slate-200 pb-3 mb-3">
                          <div>
                            <h3 className="text-sm font-extrabold tracking-tight">TAX INVOICE</h3>
                            <p className="font-semibold text-slate-800">Veda Components Pvt Ltd</p>
                            <p className="text-[10px] text-slate-600">GSTIN 27AAAAA0000A1Z5 (synthetic)</p>
                          </div>
                          <div className="text-right">
                            <p className={cn(
                              'font-bold transition-all',
                              activeHighlightFieldId === 'f-1' ? 'bg-brand/40 px-1 rounded border border-brand' : 'text-slate-900'
                            )}>
                              INV-2026-0142
                            </p>
                            <p className="text-[10px] text-slate-600 mt-1">Bill to: Kestrel Industrial Ltd</p>
                            <p className="text-[10px] text-slate-600">
                              Invoice date:{' '}
                              <span className={cn(activeHighlightFieldId === 'f-3' && 'bg-brand/40 px-1 rounded border border-brand')}>
                                01 Sep 2026
                              </span>{' '}
                              · PO Ref:{' '}
                              <span className={cn(activeHighlightFieldId === 'f-2' && 'bg-brand/40 px-1 rounded border border-brand')}>
                                PO-7781
                              </span>
                            </p>
                          </div>
                        </div>

                        <table className="w-full text-left text-[11px] mb-4">
                          <thead>
                            <tr className="border-b border-slate-300 text-slate-600">
                              <th className="py-1">Item</th>
                              <th className="py-1">Qty</th>
                              <th className="py-1">Rate</th>
                              <th className="py-1 text-right">Amount</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-slate-100">
                            <tr>
                              <td className="py-1.5">Hex bolts M10</td>
                              <td className="py-1.5">1,000</td>
                              <td className="py-1.5">₹40.00</td>
                              <td className="py-1.5 text-right font-medium">₹40,000.00</td>
                            </tr>
                            <tr>
                              <td className="py-1.5">Steel washers</td>
                              <td className="py-1.5">2,000</td>
                              <td className="py-1.5">₹10.00</td>
                              <td className="py-1.5 text-right font-medium">₹20,000.00</td>
                            </tr>
                            <tr>
                              <td className="py-1.5">Brackets</td>
                              <td className="py-1.5">400</td>
                              <td className="py-1.5">₹100.00</td>
                              <td className="py-1.5 text-right font-medium">₹40,000.00</td>
                            </tr>
                            <tr className="text-slate-600">
                              <td colSpan={3} className="py-1.5">CGST 9% + SGST 9%</td>
                              <td className={cn(
                                'py-1.5 text-right transition-all',
                                activeHighlightFieldId === 'f-5' && 'bg-brand/40 px-1 rounded border border-brand font-bold'
                              )}>
                                ₹18,000.00
                              </td>
                            </tr>
                            <tr className="font-bold text-slate-900 border-t border-slate-300">
                              <td colSpan={3} className="py-2">Total payable</td>
                              <td className="py-2 text-right">
                                <mark className={cn(
                                  'px-1.5 py-0.5 rounded border font-bold transition-all',
                                  activeHighlightFieldId === 'f-6'
                                    ? 'bg-brand text-black border-black ring-2 ring-brand'
                                    : 'bg-brand/40 text-black border-brand'
                                )}>
                                  ₹1,18,000.00
                                </mark>
                              </td>
                            </tr>
                          </tbody>
                        </table>

                        <div className="pt-2 border-t border-slate-200 text-[10px] text-slate-600">
                          Payment terms:{' '}
                          <mark className={cn(
                            'px-1 py-0.5 rounded border font-semibold transition-all',
                            activeHighlightFieldId === 'f-7'
                              ? 'bg-brand text-black border-black ring-2 ring-brand'
                              : 'bg-brand/40 text-black border-brand'
                          )}>
                            30 days
                          </mark>{' '}
                          from invoice date. Synthetic document for demonstration.
                        </div>
                      </div>
                    )}

                    {selectedDoc?.docType === 'PURCHASE_ORDER' && (
                      <div className="space-y-3">
                        <div className="flex justify-between items-start border-b border-slate-200 pb-3">
                          <div>
                            <h3 className="text-sm font-extrabold tracking-tight">PURCHASE ORDER</h3>
                            <p className="font-bold text-slate-800">Kestrel Industrial Ltd</p>
                          </div>
                          <div className="text-right font-mono font-bold">PO-7781</div>
                        </div>
                        <p className="text-xs text-slate-700">Vendor: Veda Components Pvt Ltd</p>
                        <p className="text-xs text-slate-700">Order Amount: ₹1,18,000.00</p>
                        <p className="text-xs text-slate-700">Credit terms agreed: 30 days from delivery</p>
                      </div>
                    )}

                    {selectedDoc?.docType === 'PAYMENT' && (
                      <div className="space-y-3">
                        <div className="flex justify-between items-start border-b border-slate-200 pb-3">
                          <div>
                            <h3 className="text-sm font-extrabold tracking-tight">BANK PAYMENT ADVICE</h3>
                            <p className="font-bold text-slate-800">HDFC Bank · Corporate Banking</p>
                          </div>
                          <div className="text-right font-mono text-xs">UTR: HDFC00019284412</div>
                        </div>
                        <p className="text-xs text-slate-700">Remitter: Kestrel Industrial Ltd</p>
                        <p className="text-xs text-slate-700">Beneficiary: Veda Components Pvt Ltd</p>
                        <p className="text-xs font-bold text-slate-900">
                          Amount Credited: ₹50,000.00 · Date: 18 Sep 2026
                        </p>
                      </div>
                    )}

                    {selectedDoc?.docType === 'CREDIT_NOTE' && (
                      <div className="space-y-3">
                        <div className="flex justify-between items-start border-b border-slate-200 pb-3">
                          <div>
                            <h3 className="text-sm font-extrabold tracking-tight">CREDIT NOTE</h3>
                            <p className="font-bold text-slate-800">Veda Components Pvt Ltd</p>
                          </div>
                          <div className="text-right font-mono font-bold">CN-0031</div>
                        </div>
                        <p className="text-xs text-slate-700">Buyer: Kestrel Industrial Ltd</p>
                        <p className="text-xs text-slate-700">Reason: Defective batch adjustment</p>
                        <p className="text-xs font-bold text-slate-900">Credit Amount: ₹11,800.00 · Date: 02 Oct 2026</p>
                      </div>
                    )}

                    {selectedDoc?.docType === 'MESSAGE' && (
                      <div className="space-y-3">
                        <div className="border-b border-slate-200 pb-2">
                          <h3 className="text-xs font-bold text-slate-800">WhatsApp Export · 08 Oct 2026</h3>
                        </div>
                        <div className="p-3 rounded-lg bg-emerald-50 border border-emerald-200 text-xs text-slate-800">
                          <p className="font-medium">
                            "Sir, we have transferred ₹20,000 more on Monday. Please verify."
                          </p>
                          <span className="text-[10px] text-amber-700 mt-1 block">
                            ⚠ No UTR or bank confirmation uploaded. Unverified claim excluded from ledger balance.
                          </span>
                        </div>
                      </div>
                    )}
                  </div>
                ) : (
                  /* Extracted Text Mode (Delimited data blocks, safe React highlighting) */
                  <div className="p-4 rounded-xl bg-surface-subtle font-mono text-[11px] leading-relaxed border border-border overflow-x-auto text-foreground">
                    <div className="text-foreground-muted mb-2">--- BEGIN DOCUMENT DATA BLOCK ---</div>
                    <div>DOC_ID: {selectedDoc?.id}</div>
                    <div>FILENAME: {selectedDoc?.filename}</div>
                    <div>DOC_TYPE: {selectedDoc?.docType}</div>
                    <div>PAGE: 1 of 1</div>
                    <div className="my-2 border-b border-border/50" />
                    <div>TEXT LAYER:</div>
                    <div className="pl-2 space-y-1 mt-1">
                      <div>TAX INVOICE: {selectedDoc?.filename === 'INV-2026-0142.pdf' ? 'INV-2026-0142' : selectedDoc?.filename}</div>
                      <div>DATE: 2026-09-01</div>
                      <div>BUYER: Kestrel Industrial Ltd</div>
                      <div>
                        TOTAL AMOUNT:{' '}
                        <mark className="bg-brand text-black px-1 rounded font-bold">
                          ₹1,18,000.00
                        </mark>
                      </div>
                      <div>
                        PAYMENT TERMS:{' '}
                        <mark className="bg-brand text-black px-1 rounded font-bold">
                          30 days
                        </mark>
                      </div>
                    </div>
                    <div className="text-foreground-muted mt-3">--- END DOCUMENT DATA BLOCK ---</div>
                  </div>
                )}
              </div>

              {/* Mobile field list beneath proof viewer */}
              <div className="lg:hidden mt-5 pt-4 border-t border-border">
                <h3 className="text-sm font-bold font-heading text-foreground mb-3">
                  Extracted fields
                </h3>
                <div className="space-y-2 text-xs">
                  {selectedDoc?.extractedFields ? (
                    selectedDoc.extractedFields.map((f) => (
                      <div
                        key={`mobile-sub-${f.id}`}
                        onClick={() => setActiveHighlightFieldId(f.id)}
                        className={cn(
                          'flex items-center justify-between py-2 px-2.5 rounded-xl transition-colors cursor-pointer min-h-[44px]',
                          activeHighlightFieldId === f.id
                            ? 'bg-brand/10 border border-brand/50 font-bold'
                            : 'hover:bg-surface-subtle border border-border/40'
                        )}
                      >
                        <span className="text-foreground-muted">{f.fieldName}</span>
                        <div className="flex items-center gap-1.5">
                          <span className={cn('font-semibold', f.verified ? 'text-foreground' : 'text-amber-500')}>
                            {f.fieldValue}
                          </span>
                          {f.verified && <Check className="w-3.5 h-3.5 text-brand stroke-[3]" />}
                        </div>
                      </div>
                    ))
                  ) : (
                    <div className="py-2 text-xs text-foreground-muted">No extracted fields.</div>
                  )}
                </div>
              </div>

              <p className="text-[11px] text-foreground-muted mt-4">
                Green boxes mark verified spans. Click a value on the right to jump to its source.
              </p>
            </Card>

            {/* Right: Extracted Fields & Provenance (3 cols) */}
            <div className={cn("lg:col-span-3 space-y-4", mobileDocPane !== 'FIELDS' && 'hidden lg:block')}>
              <Card className="p-5">
                <h3 className="text-sm font-bold font-heading text-foreground mb-3">
                  Extracted fields
                </h3>
                <div className="space-y-2 text-xs">
                  {selectedDoc?.extractedFields ? (
                    selectedDoc.extractedFields.map((f) => {
                      const isHighlighted = activeHighlightFieldId === f.id;
                      return (
                        <div
                          key={f.id}
                          onClick={() => setActiveHighlightFieldId(f.id)}
                          className={cn(
                            'flex items-center justify-between py-1.5 px-2 rounded-lg transition-colors cursor-pointer',
                            isHighlighted
                              ? 'bg-brand/10 border border-brand/50'
                              : 'hover:bg-surface-subtle border-b border-border/50'
                          )}
                          title="Click to highlight in viewer"
                        >
                          <span className="text-foreground-muted">{f.fieldName}</span>
                          <div className="flex items-center gap-1.5">
                            <span className={cn('font-semibold', f.verified ? 'text-foreground' : 'text-amber-500')}>
                              {f.fieldValue}
                            </span>
                            {f.verified ? (
                              <Check className="w-3.5 h-3.5 text-brand stroke-[3]" />
                            ) : (
                              <span className="text-foreground-muted">—</span>
                            )}
                          </div>
                        </div>
                      );
                    })
                  ) : (
                    <div className="py-4 text-center text-xs text-foreground-muted">
                      No critical fields extracted for this document type.
                    </div>
                  )}
                </div>
              </Card>

              <Card className="p-5">
                <h3 className="text-sm font-bold font-heading text-foreground mb-2">
                  Provenance
                </h3>
                <p className="text-xs text-foreground-muted leading-relaxed">
                  SHA-256 {selectedDoc?.sha256 || '9f2c...a71e'} · uploaded {caseData.lastRunAt}<br />
                  Parsed from PDF text layer<br />
                  Extracted by Nano; 1 field escalated to Super<br />
                  Total: page 1, characters 212–238
                </p>
                <div className="mt-4">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      setRerunFeedback('Re-running extraction for this document...');
                      setTimeout(() => setRerunFeedback(null), 2500);
                    }}
                    className="w-full text-xs"
                  >
                    Re-run extraction
                  </Button>
                </div>
              </Card>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* OTHER TABS (Ledger, Conflicts, Gates, Interest, Actions, Audit)           */}
      {/* ========================================================================= */}
      {activeTab === 'Ledger' && (
        <Card className="p-6">
          <h2 className="text-base font-bold font-heading text-foreground mb-4">
            Transaction Ledger and Matched Proofs
          </h2>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-border text-foreground-muted">
                  <th className="py-3 px-3">Date</th>
                  <th className="py-3 px-3">Description</th>
                  <th className="py-3 px-3">Type</th>
                  <th className="py-3 px-3">Amount</th>
                  <th className="py-3 px-3">Status</th>
                  <th className="py-3 px-3">Proof Document</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {caseData.ledger.map((entry) => (
                  <tr key={entry.id} className="hover:bg-surface-subtle/50">
                    <td className="py-3.5 px-3 font-mono">{entry.date}</td>
                    <td className="py-3.5 px-3 font-medium text-foreground">{entry.description}</td>
                    <td className="py-3.5 px-3">{entry.entryType}</td>
                    <td className="py-3.5 px-3 font-bold"><MoneyText paise={entry.amountPaise} /></td>
                    <td className="py-3.5 px-3"><StatusBadge status={entry.status} size="sm" /></td>
                    <td className="py-3.5 px-3 font-mono text-brand-text-light dark:text-brand">
                      <button
                        type="button"
                        onClick={() => handleProofClick(entry.proofDoc)}
                        className="hover:underline cursor-pointer"
                      >
                        {entry.proofDoc} {entry.proofPage ? `(p.${entry.proofPage})` : ''}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {activeTab === 'Conflicts' && (
        <Card className="p-6">
          <h2 className="text-base font-bold font-heading text-foreground mb-4">
            Detected Conflicts & Evidence (Rules C1–C10)
          </h2>
          <div className="space-y-4">
            {caseData.conflicts.map((conf) => (
              <div key={conf.id} className="p-4 rounded-xl border border-amber-500/30 bg-amber-500/5">
                <div className="flex items-center gap-2">
                  <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-amber-500 text-black">
                    {conf.ruleId}
                  </span>
                  <h3 className="font-bold text-sm text-foreground">{conf.title}</h3>
                </div>
                <p className="text-xs text-foreground-muted mt-2">{conf.description}</p>
                {conf.evidenceRef && (
                  <div className="mt-3 text-xs">
                    <span className="text-foreground-muted">Cited Evidence: </span>
                    <button
                      type="button"
                      onClick={() => handleProofClick(conf.evidenceRef || '')}
                      className="font-mono text-brand-text-light dark:text-brand hover:underline cursor-pointer"
                    >
                      {conf.evidenceRef}
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        </Card>
      )}

      {activeTab === 'Gates' && (
        <Card className="p-6">
          <h2 className="text-base font-bold font-heading text-foreground mb-4">
            MSMEDA Statutory Eligibility Gates (A–D)
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {caseData.gates.map((gate) => (
              <div key={gate.gateId} className="p-4 rounded-xl border border-border bg-surface-subtle">
                <div className="flex items-center justify-between mb-2">
                  <span className="font-bold text-sm text-foreground">{gate.name}</span>
                  <StatusBadge status={gate.status} size="sm" />
                </div>
                <ul className="list-disc list-inside text-xs text-foreground-muted space-y-1">
                  {gate.reasons.map((r, i) => (
                    <li key={i}>{r}</li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </Card>
      )}

      {activeTab === 'Interest' && (
        <Card className="p-6 space-y-4">
          <h2 className="text-base font-bold font-heading text-foreground">
            MSMEDA Section 16 Interest Engine
          </h2>
          <div className="p-4 rounded-xl bg-surface-subtle border border-border space-y-2 text-xs">
            <p className="text-foreground-muted">
              Section 16 stipulates compound interest with monthly rests at 3 times the bank rate notified by the RBI.
            </p>
            <div className="font-semibold text-foreground">
              Current Calculation Status:{' '}
              <span className="text-amber-500">
                {rateConfirmed ? 'RATE CONFIRMED · AWAITING ACCEPTANCE PROOF' : 'PROVISIONAL / ABSTAINED'}
              </span>
            </div>
            <p className="text-foreground-muted">
              Reason: {caseData.interest.reasons[0]}
            </p>
          </div>
          <Button onClick={() => setShowConfirmRateDialog(true)}>
            {rateConfirmed ? 'Re-confirm bank rate' : 'Fetch and confirm bank rate candidate'}
          </Button>
        </Card>
      )}

      {activeTab === 'Actions' && (
        <div className="space-y-6">
          <Card className="p-6 space-y-4">
            <h2 className="text-base font-bold font-heading text-foreground">
              Proposed Next Action & Communication Drafts
            </h2>
            <div className="p-4 rounded-xl border border-border bg-surface-subtle text-xs space-y-3">
              <div className="font-bold text-sm text-foreground">{caseData.proposedAction?.title}</div>
              <p className="text-foreground leading-relaxed">{caseData.proposedAction?.englishDraft}</p>
              <p className="text-foreground leading-relaxed font-sans">{caseData.proposedAction?.hindiDraft}</p>
            </div>
          </Card>

          <SendActionPanel
            caseId={caseId}
            caseCode={caseData.code}
            actionId={caseData.proposedAction?.id}
            actionTitle={caseData.proposedAction?.title}
            englishDraft={caseData.proposedAction?.englishDraft}
            hindiDraft={caseData.proposedAction?.hindiDraft}
            contact={buyerContact}
            onSuccess={() => {
              refetch();
              refetchBuyerContact();
            }}
          />
        </div>
      )}

      {activeTab === 'Communications' && (
        <CommunicationsTimeline caseId={caseId} onRerunAnalysis={handleRerun} />
      )}

      {activeTab === 'Audit' && (
        <Card className="p-6">
          <h2 className="text-base font-bold font-heading text-foreground mb-4">
            Immutable Audit Trail
          </h2>
          <div className="space-y-3">
            {caseData.auditTrail?.map((ev) => (
              <div
                key={ev.id}
                className="p-3 rounded-lg border border-border bg-surface-subtle text-xs flex justify-between items-center"
              >
                <div>
                  <span className="font-bold text-foreground">{ev.actor}</span> · {ev.eventType}
                </div>
                <span className="text-foreground-muted font-mono">{ev.createdAt}</span>
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* ========================================================================= */}
      {/* MODALS: Rate Confirmation & Export Case Pack                              */}
      {/* ========================================================================= */}
      {/* Rate Confirmation Modal Dialog */}
      {showConfirmRateDialog && (
        <div
          data-testid="rate-modal"
          className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-end sm:items-center justify-center p-0 sm:p-4 z-50 animate-in fade-in duration-150"
        >
          <Card className="w-full sm:max-w-md p-5 sm:p-6 space-y-4 shadow-2xl rounded-t-3xl sm:rounded-2xl border-b-0 sm:border-b max-h-[90dvh] overflow-y-auto">
            <div className="w-10 h-1 bg-foreground-muted/30 rounded-full mx-auto sm:hidden mb-1" />
            <div className="flex items-center gap-2">
              <ShieldCheck className="w-5 h-5 text-brand" />
              <h3 className="font-bold text-base font-heading text-foreground">
                Confirm RBI Bank Rate Candidate
              </h3>
            </div>
            <p className="text-xs text-foreground-muted leading-relaxed">
              Tavily retrieved the latest candidate from the official domain <span className="font-mono text-brand font-bold">rbi.org.in</span>:
            </p>
            <div className="p-3.5 rounded-lg bg-surface-subtle border border-border text-xs space-y-2">
              <div className="flex justify-between items-center">
                <span className="text-foreground-muted">Candidate Rate:</span>
                <span className="font-bold text-brand font-mono text-sm">6.75%</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-foreground-muted">Effective Date:</span>
                <span className="font-medium text-foreground">01 Sep 2026</span>
              </div>
              <div className="pt-2 border-t border-border/60">
                <div className="text-[11px] font-semibold text-foreground mb-1">Source Excerpt:</div>
                <p className="text-[11px] text-foreground-muted italic bg-surface/50 p-2 rounded border border-border/40">
                  "The Monetary Policy Committee decided to keep the policy repo rate unchanged at 6.50 per cent. Consequently, the standing deposit facility (SDF) rate remains at 6.25 per cent and the marginal standing facility (MSF) rate and the Bank Rate at 6.75 per cent."
                </p>
              </div>
              <div className="flex items-center gap-1.5 text-[10px] text-brand">
                <ExternalLink className="w-3 h-3" />
                <a
                  href="https://www.rbi.org.in/Scripts/BS_PressReleaseDisplay.aspx"
                  target="_blank"
                  rel="noreferrer"
                  className="hover:underline"
                >
                  rbi.org.in/Scripts/BS_PressReleaseDisplay.aspx
                </a>
              </div>
            </div>
            <div className="flex flex-col-reverse sm:flex-row justify-end gap-2 pt-2">
              <Button
                variant="secondary"
                size="sm"
                data-testid="cancel-bank-rate-btn"
                onClick={() => setShowConfirmRateDialog(false)}
                className="w-full sm:w-auto min-h-[44px]"
              >
                Cancel
              </Button>
              <Button
                size="sm"
                data-testid="confirm-bank-rate-btn"
                onClick={handleConfirmRate}
                className="w-full sm:w-auto min-h-[44px]"
              >
                Confirm Rate (6.75%)
              </Button>
            </div>
          </Card>
        </div>
      )}

      {/* Export Case Pack Dialog */}
      {showExportModal && (
        <div
          data-testid="export-modal"
          className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-end sm:items-center justify-center p-0 sm:p-4 z-50 animate-in fade-in duration-150"
        >
          <Card className="w-full sm:max-w-md p-5 sm:p-6 space-y-4 shadow-2xl rounded-t-3xl sm:rounded-2xl border-b-0 sm:border-b max-h-[90dvh] overflow-y-auto">
            <div className="w-10 h-1 bg-foreground-muted/30 rounded-full mx-auto sm:hidden mb-1" />
            <div className="flex items-center gap-2">
              <Download className="w-5 h-5 text-brand" />
              <h3 className="font-bold text-base font-heading text-foreground">
                Export Case Pack — {caseData.code}
              </h3>
            </div>
            <p className="text-xs text-foreground-muted leading-relaxed">
              Generate an audit-ready package containing all extracted facts, reconciled transactions, statutory gate evaluations, and cited evidence.
            </p>
            <div className="space-y-2 pt-1">
              <button
                type="button"
                data-testid="export-modal-json-btn"
                onClick={handleExportJson}
                className="w-full p-3 rounded-xl border border-border bg-surface-subtle hover:border-brand flex items-center justify-between text-left transition-all cursor-pointer group min-h-[44px]"
              >
                <div className="flex items-center gap-3">
                  <FileCode className="w-5 h-5 text-brand shrink-0" />
                  <div>
                    <div className="font-bold text-xs text-foreground">Full JSON Bundle</div>
                    <div className="text-[11px] text-foreground-muted">Complete machine-readable audit archive</div>
                  </div>
                </div>
                <Download className="w-4 h-4 text-foreground-muted group-hover:text-brand shrink-0" />
              </button>

              <button
                type="button"
                data-testid="export-modal-pdf-btn"
                onClick={handleExportPdf}
                className="w-full p-3 rounded-xl border border-border bg-surface-subtle hover:border-brand flex items-center justify-between text-left transition-all cursor-pointer group min-h-[44px]"
              >
                <div className="flex items-center gap-3">
                  <Printer className="w-5 h-5 text-brand shrink-0" />
                  <div>
                    <div className="font-bold text-xs text-foreground">Printable PDF Summary</div>
                    <div className="text-[11px] text-foreground-muted">Executive summary & balance walk report</div>
                  </div>
                </div>
                <Download className="w-4 h-4 text-foreground-muted group-hover:text-brand shrink-0" />
              </button>

              <button
                type="button"
                data-testid="export-modal-csv-btn"
                onClick={handleExportCsv}
                className="w-full p-3 rounded-xl border border-border bg-surface-subtle hover:border-brand flex items-center justify-between text-left transition-all cursor-pointer group min-h-[44px]"
              >
                <div className="flex items-center gap-3">
                  <FileText className="w-5 h-5 text-brand shrink-0" />
                  <div>
                    <div className="font-bold text-xs text-foreground">Reconciliation CSV</div>
                    <div className="text-[11px] text-foreground-muted">Document breakdown and status archive</div>
                  </div>
                </div>
                <Download className="w-4 h-4 text-foreground-muted group-hover:text-brand shrink-0" />
              </button>
            </div>
            <div className="flex justify-end pt-2">
              <Button
                variant="secondary"
                size="sm"
                data-testid="export-modal-close-btn"
                onClick={() => setShowExportModal(false)}
                className="w-full sm:w-auto min-h-[44px]"
              >
                Close
              </Button>
            </div>
          </Card>
        </div>
      )}
    </div>
  );
};
