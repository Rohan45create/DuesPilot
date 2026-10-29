import React, { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { MoneyText } from '../components/common/MoneyText';
import { StatusBadge } from '../components/common/StatusBadge';
import { Filter, Check, Edit2, ChevronDown, Send } from 'lucide-react';
import { cn } from '../utils/formatters';
import { SendActionPanel } from '../components/notifications/SendActionPanel';
import { useToast } from '../context/ToastContext';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';

interface QueueItem {
  code: string;
  name: string;
  status: 'CONFLICT' | 'MISSING_PROOF' | 'CLARIFY' | 'READY';
  statusLabel: string;
  balancePaise: number | null;
  summary: string;
  draftEn: string;
  draftHi: string;
  evidence: string[];
  actionTitle: string;
}

interface DecisionRecord {
  id: string;
  caseCode: string;
  action: 'APPROVED' | 'REJECTED' | 'EDITED';
  who: string;
  timestamp: string;
  whatChanged: string;
}

const INITIAL_QUEUE: QueueItem[] = [
  {
    code: 'DP-0142',
    name: 'Kestrel Industrial',
    status: 'CONFLICT',
    statusLabel: 'Conflict',
    balancePaise: 5620000,
    summary: 'Credit note not applied; delivery proof missing. Interest not computed.',
    draftEn: 'Please share the signed delivery challan or goods receipt for INV-2026-0142 so we can confirm the outstanding balance of ₹56,200.',
    draftHi: 'कृपया INV-2026-0142 के लिए हस्ताक्षरित डिलीवरी चालान या माल रसीद साझा करें ताकि हम ₹56,200 के बकाया शेष की पुष्टि कर सकें।',
    evidence: ['Invoice p.1', 'CN p.1', 'Bank p.1'],
    actionTitle: 'Request delivery proof',
  },
  {
    code: 'DP-0139',
    name: 'Harbor Tooling',
    status: 'MISSING_PROOF',
    statusLabel: 'Missing proof',
    balancePaise: 14000000,
    summary: 'Invoice and PO match; acceptance record absent.',
    draftEn: 'Please confirm whether the goods supplied under PO-8812 have been inspected and accepted.',
    draftHi: 'कृपया पुष्टि करें कि क्या PO-8812 के तहत आपूर्ति किए गए माल का निरीक्षण और स्वीकृति हो चुकी है।',
    evidence: ['PO p.1', 'INV p.1'],
    actionTitle: 'Request acceptance confirmation',
  },
  {
    code: 'DP-0127',
    name: 'Orion Castings',
    status: 'CLARIFY',
    statusLabel: 'Clarify',
    balancePaise: null,
    summary: 'One payment references two invoices; allocation unclear.',
    draftEn: 'Please clarify payment allocation between INV-101 and INV-102 from remittance UTR 9912.',
    draftHi: 'कृपया प्रेषण UTR 9912 से INV-101 और INV-102 के बीच भुगतान आवंटन स्पष्ट करें।',
    evidence: ['Bank statement p.2'],
    actionTitle: 'Clarify invoice allocation',
  },
  {
    code: 'DP-0131',
    name: 'Mistral Fabrication',
    status: 'READY',
    statusLabel: 'Ready',
    balancePaise: 0,
    summary: 'Fully reconciled. Closing note awaiting approval.',
    draftEn: 'Account statement shows full zero-balance reconciliation. Acknowledging receipt with thanks.',
    draftHi: 'खाता विवरण पूर्ण शून्य-शेष समाधान दिखाता है। धन्यवाद सहित पावती।',
    evidence: ['Bank p.1', 'INV p.1'],
    actionTitle: 'Approve reconciliation closing note',
  },
];

const INITIAL_LOG: DecisionRecord[] = [
  {
    id: 'dec-1',
    caseCode: 'DP-0118',
    action: 'APPROVED',
    who: 'Demo user',
    timestamp: '12 Oct 2026, 2:15 pm',
    whatChanged: 'Approved formal payment demand notice for Basalt Engineering.',
  },
  {
    id: 'dec-2',
    caseCode: 'DP-0105',
    action: 'REJECTED',
    who: 'Demo user',
    timestamp: '11 Oct 2026, 4:40 pm',
    whatChanged: 'Rejected premature litigation notice; requested phone follow-up instead.',
  },
];

export const ReviewQueuePage: React.FC = () => {
  const { showToast } = useToast();
  const context = useOutletContext<{
    activeTab?: string;
    setActiveTab?: (tab: string) => void;
  }>();

  const [localTab] = useState<'Pending' | 'Approved' | 'Rejected' | 'Audit log'>('Pending');
  const activeTab = (context?.activeTab as any) ?? localTab;
  const queue = INITIAL_QUEUE;
  const [selectedCode, setSelectedCode] = useState('DP-0142');
  const [lang, setLang] = useState<'EN' | 'HI'>('EN');

  // Pre-approval checklist
  const [evidenceChecked, setEvidenceChecked] = useState(true);
  const [noLegalClaimsChecked, setNoLegalClaimsChecked] = useState(true);
  const [amountsChecked, setAmountsChecked] = useState(false);

  // Decision records
  const [approvedItems, setApprovedItems] = useState<{ [code: string]: { draft: string; timestamp: string } }>({});
  const [rejectedItems, setRejectedItems] = useState<{ [code: string]: { timestamp: string } }>({});
  const [decisionLog, setDecisionLog] = useState<DecisionRecord[]>(INITIAL_LOG);

  // Inline edit state
  const [isEditing, setIsEditing] = useState(false);
  const [editedDraft, setEditedDraft] = useState('');

  // Rejection confirmation dialog state
  const [showRejectDialog, setShowRejectDialog] = useState(false);

  // Filter state
  const [reasonFilter, setReasonFilter] = useState<'ALL' | 'CONFLICT' | 'MISSING_PROOF' | 'CLARIFY' | 'READY'>('ALL');
  const [showFilterDropdown, setShowFilterDropdown] = useState(false);
  const [notification, setNotification] = useState<string | null>(null);

  const selectedItem = queue.find((q) => q.code === selectedCode) ?? queue[0];
  const isApproved = Boolean(approvedItems[selectedItem?.code]);
  const isRejected = Boolean(rejectedItems[selectedItem?.code]);

  // Filtered queue based on activeTab and reasonFilter
  const pendingItems = queue.filter(
    (item) => !approvedItems[item.code] && !rejectedItems[item.code] && (reasonFilter === 'ALL' || item.status === reasonFilter)
  );

  const handleUndoApprove = (code: string) => {
    setApprovedItems((prev) => {
      const next = { ...prev };
      delete next[code];
      return next;
    });
    setDecisionLog((prev) => prev.filter((d) => !(d.caseCode === code && d.action === 'APPROVED')));
    showToast(`Undid approval for ${code}`, 'info');
  };

  const handleUndoReject = (code: string) => {
    setRejectedItems((prev) => {
      const next = { ...prev };
      delete next[code];
      return next;
    });
    setDecisionLog((prev) => prev.filter((d) => !(d.caseCode === code && d.action === 'REJECTED')));
    showToast(`Undid rejection for ${code}`, 'info');
  };

  const handleApprove = () => {
    if (!selectedItem) return;
    const now = new Date().toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
    const draftContent = isEditing ? editedDraft : (lang === 'EN' ? selectedItem.draftEn : selectedItem.draftHi);

    setApprovedItems((prev) => ({
      ...prev,
      [selectedItem.code]: { draft: draftContent, timestamp: now },
    }));

    const newRecord: DecisionRecord = {
      id: `dec-${Date.now()}`,
      caseCode: selectedItem.code,
      action: 'APPROVED',
      who: 'Demo user',
      timestamp: now,
      whatChanged: `Approved draft: "${draftContent.substring(0, 50)}..."`,
    };
    setDecisionLog((prev) => [newRecord, ...prev]);
    setIsEditing(false);
    setNotification(`Approved ${selectedItem.code} draft.`);
    showToast(`Approved draft for ${selectedItem.code}`, {
      type: 'success',
      action: {
        label: 'Undo',
        onClick: () => handleUndoApprove(selectedItem.code),
      },
    });
    setTimeout(() => setNotification(null), 3000);
  };

  const handleRejectClick = () => {
    if (!selectedItem) return;
    setShowRejectDialog(true);
  };

  const confirmReject = () => {
    if (!selectedItem) return;
    const now = new Date().toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });

    setRejectedItems((prev) => ({
      ...prev,
      [selectedItem.code]: { timestamp: now },
    }));

    const newRecord: DecisionRecord = {
      id: `dec-${Date.now()}`,
      caseCode: selectedItem.code,
      action: 'REJECTED',
      who: 'Demo user',
      timestamp: now,
      whatChanged: `Rejected proposed action for ${selectedItem.name}.`,
    };
    setDecisionLog((prev) => [newRecord, ...prev]);
    setIsEditing(false);
    setShowRejectDialog(false);
    setNotification(`Rejected ${selectedItem.code} action.`);
    showToast(`Rejected proposed action for ${selectedItem.code}`, {
      type: 'info',
      action: {
        label: 'Undo',
        onClick: () => handleUndoReject(selectedItem.code),
      },
    });
    setTimeout(() => setNotification(null), 3000);
  };

  const handleBulkApproveReady = () => {
    const readyItems = queue.filter((i) => i.status === 'READY' && !approvedItems[i.code]);
    if (readyItems.length === 0) {
      setNotification('No unapproved "Ready" items in queue.');
      showToast('No unapproved "Ready" items in queue', 'info');
      setTimeout(() => setNotification(null), 2500);
      return;
    }
    const now = new Date().toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
    const newApproved = { ...approvedItems };
    const newLogs: DecisionRecord[] = [];

    readyItems.forEach((item) => {
      newApproved[item.code] = { draft: item.draftEn, timestamp: now };
      newLogs.push({
        id: `dec-${Date.now()}-${item.code}`,
        caseCode: item.code,
        action: 'APPROVED',
        who: 'Demo user (Bulk)',
        timestamp: now,
        whatChanged: `Bulk approved closing note for ${item.name}.`,
      });
    });

    setApprovedItems(newApproved);
    setDecisionLog((prev) => [...newLogs, ...prev]);
    setNotification(`Bulk approved ${readyItems.length} ready case(s).`);
    showToast(`Bulk approved ${readyItems.length} ready case(s)`, 'success');
    setTimeout(() => setNotification(null), 3500);
  };

  return (
    <div data-testid="review-queue-page" className="space-y-6 w-full">
      {/* Header matching Review queue · dark.png */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl sm:text-3xl font-bold font-heading text-foreground tracking-tight">
              Review queue
            </h1>
            {notification && (
              <span className="text-xs px-2.5 py-1 rounded-full bg-brand/20 text-brand font-medium animate-pulse">
                {notification}
              </span>
            )}
          </div>
          <p className="text-xs sm:text-sm text-foreground-muted mt-1 max-w-2xl">
            The system proposes; a person decides. Unknowns are listed, never guessed.
          </p>
        </div>

        <div className="flex items-center gap-2.5 relative">
          <Button
            variant="secondary"
            size="sm"
            data-testid="bulk-approve-btn"
            onClick={handleBulkApproveReady}
            className="h-9 text-xs"
          >
            Bulk approve ready
          </Button>

          <div className="relative">
            <Button
              variant="secondary"
              size="sm"
              data-testid="review-filter-btn"
              onClick={() => setShowFilterDropdown(!showFilterDropdown)}
              className="h-9 text-xs"
            >
              <Filter className="w-3.5 h-3.5 mr-1" />
              Filter {reasonFilter !== 'ALL' ? `(${reasonFilter})` : ''}
              <ChevronDown className="w-3 h-3 ml-1" />
            </Button>

            {showFilterDropdown && (
              <div className="absolute right-0 mt-2 w-48 rounded-xl bg-surface border border-border shadow-xl p-1.5 z-30 text-xs">
                {(['ALL', 'CONFLICT', 'MISSING_PROOF', 'CLARIFY', 'READY'] as const).map((r) => (
                  <button
                    key={r}
                    data-testid={`filter-option-${r.toLowerCase()}`}
                    onClick={() => {
                      setReasonFilter(r);
                      setShowFilterDropdown(false);
                    }}
                    className={cn(
                      'w-full text-left px-3 py-2 rounded-lg transition-colors cursor-pointer',
                      reasonFilter === r ? 'bg-brand/10 text-brand font-bold' : 'text-foreground hover:bg-surface-subtle'
                    )}
                  >
                    {r === 'ALL' ? 'All Reasons' : r.replace('_', ' ')}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Main Content Area based on Tab */}
      {activeTab === 'Audit log' ? (
        /* ================= Audit Log View ================= */
        <Card className="p-6 space-y-4" data-testid="audit-log-table">
          <h2 className="text-base font-bold font-heading text-foreground">
            Immutable Human Decision Log
          </h2>
          <p className="text-xs text-foreground-muted">
            All reviews, approvals, and rejections are permanently recorded with timestamp and rationale.
          </p>

          {/* Mobile Decision Log Cards (<640px) */}
          <div className="sm:hidden space-y-3">
            {decisionLog.map((log) => (
              <div
                key={`mobile-log-${log.id}`}
                className="p-3.5 rounded-xl border border-border bg-surface-subtle/40 space-y-2 text-xs"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-bold text-foreground">{log.caseCode}</span>
                  <span
                    className={cn(
                      'px-2 py-0.5 rounded-full text-[10px] font-bold',
                      log.action === 'APPROVED' ? 'bg-brand text-black' : 'bg-red-500/20 text-red-500'
                    )}
                  >
                    {log.action}
                  </span>
                </div>
                <p className="text-foreground-muted">{log.whatChanged}</p>
                <div className="flex items-center justify-between pt-1 border-t border-border/40 text-[11px] text-foreground-muted font-mono">
                  <span>{log.who}</span>
                  <span>{log.timestamp}</span>
                </div>
              </div>
            ))}
          </div>

          {/* Desktop Table (>=640px) */}
          <div className="hidden sm:block overflow-x-auto rounded-xl border border-border/60">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-border bg-surface-subtle/30 text-foreground-muted font-medium">
                  <th className="py-2.5 px-3 sticky left-0 bg-surface z-10 shadow-[1px_0_0_0_var(--color-border)]">Case</th>
                  <th className="py-2.5 px-3">Timestamp</th>
                  <th className="py-2.5 px-3">Action</th>
                  <th className="py-2.5 px-3">Decided By</th>
                  <th className="py-2.5 px-3">What Changed</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {decisionLog.map((log) => (
                  <tr key={log.id} className="hover:bg-surface-subtle/50">
                    <td className="py-3 px-3 font-bold text-foreground sticky left-0 bg-surface z-10 shadow-[1px_0_0_0_var(--color-border)]">{log.caseCode}</td>
                    <td className="py-3 px-3 font-mono text-[11px] text-foreground-muted whitespace-nowrap">{log.timestamp}</td>
                    <td className="py-3 px-3 whitespace-nowrap">
                      <span
                        className={cn(
                          'px-2 py-0.5 rounded-full text-[10px] font-bold',
                          log.action === 'APPROVED' ? 'bg-brand text-black' : 'bg-red-500/20 text-red-500'
                        )}
                      >
                        {log.action}
                      </span>
                    </td>
                    <td className="py-3 px-3 font-medium text-foreground whitespace-nowrap">{log.who}</td>
                    <td className="py-3 px-3 text-foreground-muted">{log.whatChanged}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      ) : activeTab === 'Approved' ? (
        /* ================= Approved Tab ================= */
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div className="lg:col-span-12 space-y-3">
            {Object.keys(approvedItems).length === 0 ? (
              <Card className="p-8 text-center text-xs text-foreground-muted">
                No approved drafts yet in this session. Review pending cases to approve actions.
              </Card>
            ) : (
              queue
                .filter((item) => approvedItems[item.code])
                .map((item) => (
                  <Card key={item.code} className="p-5 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <span className="font-bold text-sm text-foreground">{item.code}</span>
                        <span className="text-xs font-semibold text-foreground">{item.name}</span>
                        <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-brand text-black">
                          Approved
                        </span>
                      </div>
                      <p className="text-xs text-foreground-muted">{approvedItems[item.code].draft}</p>
                    </div>
                    <span className="text-[11px] font-mono text-foreground-muted shrink-0">
                      {approvedItems[item.code].timestamp}
                    </span>
                  </Card>
                ))
            )}
          </div>
        </div>
      ) : activeTab === 'Rejected' ? (
        /* ================= Rejected Tab ================= */
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div className="lg:col-span-12 space-y-3">
            {Object.keys(rejectedItems).length === 0 ? (
              <Card className="p-8 text-center text-xs text-foreground-muted">
                No rejected drafts in this session.
              </Card>
            ) : (
              queue
                .filter((item) => rejectedItems[item.code])
                .map((item) => (
                  <Card key={item.code} className="p-5 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <span className="font-bold text-sm text-foreground">{item.code}</span>
                        <span className="text-xs font-semibold text-foreground">{item.name}</span>
                        <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-red-500/20 text-red-500">
                          Rejected
                        </span>
                      </div>
                      <p className="text-xs text-foreground-muted">{item.summary}</p>
                    </div>
                    <span className="text-[11px] font-mono text-foreground-muted shrink-0">
                      {rejectedItems[item.code].timestamp}
                    </span>
                  </Card>
                ))
            )}
          </div>
        </div>
      ) : (
        /* ================= Default Pending Review Queue ================= */
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Left Column: Queue List (6 cols) */}
          <div className="lg:col-span-6 space-y-3">
            {pendingItems.length === 0 ? (
              <Card className="p-8 text-center text-xs text-foreground-muted">
                No pending items matching the selected filter.
              </Card>
            ) : (
              pendingItems.map((item) => {
                const isSelected = item.code === selectedCode;
                const itemApproved = Boolean(approvedItems[item.code]);

                return (
                  <Card
                    key={item.code}
                    data-testid={`queue-card-${item.code.toLowerCase()}`}
                    onClick={() => {
                      setSelectedCode(item.code);
                      setIsEditing(false);
                    }}
                    className={cn(
                      'p-4 transition-all cursor-pointer border',
                      isSelected
                        ? 'border-brand bg-brand/5 shadow-xs ring-1 ring-brand'
                        : 'border-border bg-surface hover:bg-surface-subtle'
                    )}
                  >
                    <div className="flex items-center justify-between gap-2 mb-2">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-sm text-foreground">
                          {item.code}
                        </span>
                        <span className="text-xs font-semibold text-foreground">
                          {item.name}
                        </span>
                      </div>

                      <div className="flex items-center gap-2">
                        {itemApproved ? (
                          <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-brand text-black">
                            Approved
                          </span>
                        ) : (
                          <StatusBadge status={item.status} size="sm" />
                        )}
                        <span className="font-bold text-sm text-foreground tabular-nums">
                          <MoneyText paise={item.balancePaise} />
                        </span>
                      </div>
                    </div>

                    <p className="text-xs text-foreground-muted">
                      {item.summary}
                    </p>
                  </Card>
                );
              })
            )}
          </div>

          {/* Right Column: Review Details & Queue by Reason (6 cols) */}
          <div className="lg:col-span-6 space-y-5">
            {/* Review Card */}
            {selectedItem && (
              <Card className="p-5 space-y-4">
                <div className="flex items-center justify-between">
                  <h2 className="text-sm font-bold font-heading text-foreground">
                    {selectedItem.code} · {selectedItem.actionTitle}
                  </h2>

                  <div className="flex items-center p-0.5 rounded-full bg-surface-subtle border border-border text-xs">
                    <button
                      type="button"
                      data-testid="lang-toggle-en"
                      onClick={() => setLang('EN')}
                      className={cn(
                        'px-2.5 py-0.5 rounded-full font-medium transition-colors cursor-pointer',
                        lang === 'EN'
                          ? 'bg-foreground text-background font-bold'
                          : 'text-foreground-muted'
                      )}
                    >
                      English
                    </button>
                    <button
                      type="button"
                      data-testid="lang-toggle-hi"
                      onClick={() => setLang('HI')}
                      className={cn(
                        'px-2.5 py-0.5 rounded-full font-medium transition-colors cursor-pointer',
                        lang === 'HI'
                          ? 'bg-foreground text-background font-bold'
                          : 'text-foreground-muted'
                      )}
                    >
                      हिंदी
                    </button>
                  </div>
                </div>

                {/* Evidence chips */}
                <div className="flex flex-wrap gap-2">
                  {selectedItem.evidence.map((ev) => (
                    <span
                      key={ev}
                      className="px-2.5 py-0.5 rounded-full text-[11px] bg-surface-subtle border border-border text-foreground font-mono"
                    >
                      {ev}
                    </span>
                  ))}
                </div>

                {/* Draft text or edit mode */}
                {isEditing ? (
                  <div className="space-y-2">
                    <textarea
                      rows={3}
                      data-testid="edit-draft-textarea"
                      value={editedDraft || (lang === 'EN' ? selectedItem.draftEn : selectedItem.draftHi)}
                      onChange={(e) => setEditedDraft(e.target.value)}
                      className="w-full p-3 rounded-xl bg-surface-subtle border border-brand text-xs text-foreground focus:outline-hidden"
                    />
                    <div className="flex justify-end gap-2">
                      <Button
                        variant="secondary"
                        size="sm"
                        data-testid="cancel-edit-btn"
                        onClick={() => setIsEditing(false)}
                        className="h-7 text-xs"
                      >
                        Cancel
                      </Button>
                      <Button
                        size="sm"
                        data-testid="save-draft-btn"
                        onClick={() => {
                          setIsEditing(false);
                          showToast('Draft changes saved', 'info');
                        }}
                        className="h-7 text-xs"
                      >
                        Save draft
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className="p-4 rounded-xl bg-surface-subtle border border-border text-xs text-foreground leading-relaxed">
                    {editedDraft || (lang === 'EN' ? selectedItem.draftEn : selectedItem.draftHi)}
                  </div>
                )}

                {/* Pre-approval checklist */}
                <div className="space-y-2 pt-1 text-xs text-foreground">
                  <div className="text-[11px] font-semibold text-foreground-muted mb-1">
                    Before approving
                  </div>
                  <label className="flex items-center gap-2 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      data-testid="checklist-evidence"
                      checked={evidenceChecked}
                      onChange={(e) => setEvidenceChecked(e.target.checked)}
                      className="w-3.5 h-3.5 accent-brand rounded cursor-pointer"
                    />
                    <span>Evidence reviewed</span>
                  </label>
                  <label className="flex items-center gap-2 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      data-testid="checklist-statutory"
                      checked={noLegalClaimsChecked}
                      onChange={(e) => setNoLegalClaimsChecked(e.target.checked)}
                      className="w-3.5 h-3.5 accent-brand rounded cursor-pointer"
                    />
                    <span>No legal claims in draft</span>
                  </label>
                  <label className="flex items-center gap-2 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      data-testid="checklist-balance"
                      checked={amountsChecked}
                      onChange={(e) => setAmountsChecked(e.target.checked)}
                      className="w-3.5 h-3.5 accent-brand rounded cursor-pointer"
                    />
                    <span>Amounts checked</span>
                  </label>
                </div>

                {/* Action Buttons */}
                <div className="flex flex-wrap items-center gap-2 pt-2">
                  <Button
                    size="sm"
                    data-testid="approve-action-btn"
                    disabled={isApproved}
                    onClick={handleApprove}
                    className="h-10 sm:h-8 text-xs font-bold min-h-[44px] sm:min-h-0 flex-1 sm:flex-initial"
                  >
                    <Check className="w-3.5 h-3.5 mr-1 stroke-[3]" />
                    {isApproved ? 'Approved' : 'Approve'}
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    data-testid="edit-draft-btn"
                    onClick={() => {
                      if (!isEditing) setEditedDraft(lang === 'EN' ? selectedItem.draftEn : selectedItem.draftHi);
                      setIsEditing(!isEditing);
                    }}
                    className="h-10 sm:h-8 text-xs min-h-[44px] sm:min-h-0 flex-1 sm:flex-initial"
                  >
                    <Edit2 className="w-3.5 h-3.5 mr-1" />
                    {isEditing ? 'Cancel edit' : 'Edit'}
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    data-testid="reject-action-btn"
                    disabled={isRejected}
                    onClick={handleRejectClick}
                    className="h-10 sm:h-8 text-xs min-h-[44px] sm:min-h-0 flex-1 sm:flex-initial"
                  >
                    Reject
                  </Button>
                </div>

                {/* Send & Dispatch Panel */}
                <div className="mt-5 pt-4 border-t border-border">
                  <div className="text-xs font-bold text-foreground mb-3 flex items-center justify-between">
                    <span className="flex items-center gap-1.5">
                      <Send className="w-3.5 h-3.5 text-brand" />
                      Send & Channel Dispatch
                    </span>
                    {isApproved && (
                      <span className="text-[10px] text-brand font-semibold px-2 py-0.5 rounded-full bg-brand/10">
                        Action Approved
                      </span>
                    )}
                  </div>
                  <SendActionPanel
                    caseId={selectedItem.code}
                    caseCode={selectedItem.code}
                    actionTitle={selectedItem.actionTitle}
                    englishDraft={editedDraft && lang === 'EN' ? editedDraft : selectedItem.draftEn}
                    hindiDraft={editedDraft && lang === 'HI' ? editedDraft : selectedItem.draftHi}
                    onSuccess={() => {
                      setNotification(`Dispatched communication for ${selectedItem.code}`);
                      setTimeout(() => setNotification(null), 3500);
                    }}
                  />
                </div>
              </Card>
            )}

            {/* Queue by reason card */}
            <Card className="p-5" data-testid="queue-by-reason-card">
              <h3 className="text-sm font-bold font-heading text-foreground mb-4">
                Queue by reason
              </h3>

              <div className="space-y-3.5 text-xs">
                <div className="flex items-center justify-between gap-4">
                  <span className="w-28 text-foreground font-medium">Conflict</span>
                  <div className="flex-1 h-3 rounded-full bg-surface-subtle overflow-hidden">
                    <div className="w-[85%] h-full bg-amber-500 rounded-full" />
                  </div>
                </div>

                <div className="flex items-center justify-between gap-4">
                  <span className="w-28 text-foreground font-medium">Missing proof</span>
                  <div className="flex-1 h-3 rounded-full bg-surface-subtle overflow-hidden">
                    <div className="w-[60%] h-full bg-amber-500 rounded-full" />
                  </div>
                </div>

                <div className="flex items-center justify-between gap-4">
                  <span className="w-28 text-foreground font-medium">Clarify</span>
                  <div className="flex-1 h-3 rounded-full bg-surface-subtle overflow-hidden">
                    <div className="w-[35%] h-full bg-foreground-muted/40 rounded-full" />
                  </div>
                </div>

                <div className="flex items-center justify-between gap-4">
                  <span className="w-28 text-foreground font-medium">Ready</span>
                  <div className="flex-1 h-3 rounded-full bg-surface-subtle overflow-hidden">
                    <div className="w-[20%] h-full bg-brand rounded-full" />
                  </div>
                </div>
              </div>

              <p className="text-[11px] text-foreground-muted mt-5">
                Decisions are logged with who, when and what changed.
              </p>
            </Card>
          </div>
        </div>
      )}

      {/* Rejection Confirmation Dialog */}
      <ConfirmDialog
        isOpen={showRejectDialog}
        title="Reject Proposed Action?"
        description={`Are you sure you want to reject the recommended action for ${selectedItem?.name} (${selectedItem?.code})? This will remove it from the active pending queue and log the rejection permanently in the audit trail.`}
        confirmLabel="Confirm Rejection"
        cancelLabel="Cancel"
        variant="danger"
        data-testid="reject-confirm-dialog"
        cancelButtonTestId="cancel-reject-btn"
        confirmButtonTestId="confirm-reject-btn"
        onConfirm={confirmReject}
        onCancel={() => setShowRejectDialog(false)}
      />
    </div>
  );
};
