import React, { useState, useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { Button } from '../ui/Button';
import { Sparkles, Database, FileText, Users, HardDrive, ShieldCheck, CheckSquare, X, AlertCircle } from 'lucide-react';
import { cn } from '../../utils/formatters';

export interface SeedDemoDataDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (caseCount: number) => void;
}

export const SeedDemoDataDialog: React.FC<SeedDemoDataDialogProps> = ({
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [agreed, setAgreed] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelBtnRef = useRef<HTMLButtonElement>(null);

  // Fetch real preview metrics when open
  const {
    data: preview,
    isLoading: isPreviewLoading,
  } = useQuery({
    queryKey: ['demoDataPreview'],
    queryFn: () => api.getDemoDataPreview(),
    enabled: isOpen,
  });

  // Reset local state when dialog opens or closes
  useEffect(() => {
    if (isOpen) {
      setAgreed(false);
      setIsSubmitting(false);
      setSubmitError(null);
      const timer = setTimeout(() => cancelBtnRef.current?.focus(), 50);
      return () => clearTimeout(timer);
    }
  }, [isOpen]);

  // Keyboard navigation & focus trap
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        if (!isSubmitting) onClose();
      } else if (e.key === 'Tab' && dialogRef.current) {
        const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input:not([disabled]), [tabindex]:not([tabindex="-1"])'
        );
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];

        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, isSubmitting, onClose]);

  if (!isOpen) return null;

  const handleSubmit = async () => {
    if (!agreed || isSubmitting) return;

    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const res = await api.seedDemoData(true);
      onSuccess(res.caseCount);
      onClose();
    } catch (err: any) {
      setSubmitError(err.message || 'Failed to seed demo data. Please try again.');
      setIsSubmitting(false);
    }
  };

  const caseCount = preview?.caseCount ?? 12;
  const documentCount = preview?.documentCount ?? 20;
  const contactCount = preview?.contactCount ?? 12;
  const storageKb = preview?.approximateStorageKb ?? 28;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in duration-150"
      onClick={(e) => {
        if (e.target === e.currentTarget && !isSubmitting) onClose();
      }}
      data-testid="seed-demo-data-modal"
    >
      <div
        ref={dialogRef}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="seed-demo-title"
        aria-describedby="seed-demo-description"
        className="w-full max-w-lg bg-surface border border-border rounded-2xl shadow-2xl overflow-hidden flex flex-col animate-in zoom-in-95 duration-150 text-foreground"
      >
        {/* Header */}
        <div className="p-6 pb-4 border-b border-border flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-brand/10 border border-brand/20 flex items-center justify-center shrink-0 text-brand">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h2 id="seed-demo-title" className="text-lg font-bold tracking-tight text-foreground">
                Add demo data to your workspace?
              </h2>
              <p className="text-xs text-foreground-muted mt-0.5">
                Explore DuesPilot features with realistic MSME accounts receivable cases
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="text-foreground-muted hover:text-foreground p-1.5 rounded-lg hover:bg-surface-subtle transition-colors cursor-pointer"
            aria-label="Close dialog"
            data-testid="seed-demo-close-btn"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Body Content */}
        <div id="seed-demo-description" className="p-6 space-y-4 text-sm text-foreground">
          {isPreviewLoading ? (
            <div className="py-6 flex flex-col items-center justify-center gap-2 text-foreground-muted">
              <div className="w-6 h-6 border-2 border-brand border-t-transparent rounded-full animate-spin" />
              <p className="text-xs">Calculating preview metrics...</p>
            </div>
          ) : (
            <>
              <p className="text-foreground-muted leading-relaxed text-xs">
                Seeding demo data allows you to test the complete workflow without needing your own production documents. Here is what will be added:
              </p>

              {/* Spec-driven real preview metrics */}
              <div className="grid grid-cols-2 gap-2.5 text-xs">
                <div className="p-3 rounded-xl bg-surface-subtle/60 border border-border/80 flex items-start gap-2.5">
                  <Database className="w-4 h-4 text-brand shrink-0 mt-0.5" />
                  <div>
                    <span className="font-semibold text-foreground block">{caseCount} Synthetic Cases</span>
                    <span className="text-foreground-muted text-[11px]">Control & conflict scenarios</span>
                  </div>
                </div>

                <div className="p-3 rounded-xl bg-surface-subtle/60 border border-border/80 flex items-start gap-2.5">
                  <FileText className="w-4 h-4 text-brand shrink-0 mt-0.5" />
                  <div>
                    <span className="font-semibold text-foreground block">{documentCount} Sample Documents</span>
                    <span className="text-foreground-muted text-[11px]">PO, invoice, bank, credit-note, message</span>
                  </div>
                </div>

                <div className="p-3 rounded-xl bg-surface-subtle/60 border border-border/80 flex items-start gap-2.5">
                  <Users className="w-4 h-4 text-brand shrink-0 mt-0.5" />
                  <div>
                    <span className="font-semibold text-foreground block">{contactCount} Fictional Contacts</span>
                    <span className="text-foreground-muted text-[11px]">No real emails or phone numbers</span>
                  </div>
                </div>

                <div className="p-3 rounded-xl bg-surface-subtle/60 border border-border/80 flex items-start gap-2.5">
                  <HardDrive className="w-4 h-4 text-brand shrink-0 mt-0.5" />
                  <div>
                    <span className="font-semibold text-foreground block">~{storageKb} KB Storage</span>
                    <span className="text-foreground-muted text-[11px]">Blobs shared, zero bloat</span>
                  </div>
                </div>
              </div>

              {/* Safety guarantees callout */}
              <div className="p-3.5 rounded-xl bg-brand/5 border border-brand/20 text-xs space-y-1.5">
                <div className="flex items-center gap-2 font-medium text-brand">
                  <ShieldCheck className="w-4 h-4 shrink-0" />
                  <span>Safe & Non-Destructive Guarantee</span>
                </div>
                <ul className="list-disc list-inside text-foreground-muted space-y-1 text-[11px] pl-1">
                  <li><strong>Zero communications dispatched:</strong> All demo notifications are simulated, nothing is sent to anyone.</li>
                  <li><strong>Labelled & Segregated:</strong> Seeded items are marked with a <span className="inline-block px-1.5 py-0.2 bg-brand/10 text-brand font-semibold rounded text-[10px]">Demo</span> badge and can be filtered or removed anytime.</li>
                  <li><strong>Your real cases are protected:</strong> Removing demo data never touches your own cases or records.</li>
                </ul>
              </div>
            </>
          )}

          {submitError && (
            <div className="p-3 rounded-xl bg-status-red/10 border border-status-red/20 text-status-red text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{submitError}</span>
            </div>
          )}

          {/* Consent Checkbox */}
          <div className="pt-2 border-t border-border">
            <label
              htmlFor="seed-demo-consent-checkbox"
              className="flex items-start gap-3 select-none cursor-pointer group"
            >
              <div className="relative flex items-center justify-center mt-0.5">
                <input
                  type="checkbox"
                  id="seed-demo-consent-checkbox"
                  data-testid="seed-demo-consent-checkbox"
                  checked={agreed}
                  onChange={(e) => setAgreed(e.target.checked)}
                  disabled={isSubmitting}
                  className="sr-only"
                />
                <div
                  className={cn(
                    'w-4 h-4 rounded border flex items-center justify-center transition-colors',
                    agreed
                      ? 'bg-brand border-brand text-brand-foreground'
                      : 'border-border bg-surface hover:border-brand/60'
                  )}
                >
                  {agreed ? (
                    <CheckSquare className="w-3.5 h-3.5 text-white" />
                  ) : null}
                </div>
              </div>
              <span className="text-xs text-foreground font-medium group-hover:text-foreground/90 leading-tight">
                I understand this adds synthetic sample data to my workspace
              </span>
            </label>
          </div>
        </div>

        {/* Actions Footer */}
        <div className="p-4 bg-surface-subtle border-t border-border flex items-center justify-end gap-2.5">
          <Button
            ref={cancelBtnRef}
            type="button"
            variant="outline"
            size="sm"
            onClick={onClose}
            disabled={isSubmitting}
            data-testid="seed-demo-cancel-btn"
          >
            Cancel
          </Button>
          <Button
            type="button"
            variant="primary"
            size="sm"
            onClick={handleSubmit}
            disabled={!agreed || isSubmitting || isPreviewLoading}
            isLoading={isSubmitting}
            data-testid="seed-demo-confirm-btn"
            className="font-medium"
          >
            Add demo data
          </Button>
        </div>
      </div>
    </div>
  );
};
