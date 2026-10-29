import React, { useEffect, useRef } from 'react';
import { AlertTriangle, AlertCircle, Info, X } from 'lucide-react';
import { Button } from './Button';
import { cn } from '../../utils/formatters';

export interface ConfirmDialogProps {
  isOpen: boolean;
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: 'danger' | 'warning' | 'primary';
  isLoading?: boolean;
  onConfirm: () => void | Promise<void>;
  onCancel: () => void;
  'data-testid'?: string;
  cancelButtonTestId?: string;
  confirmButtonTestId?: string;
}

export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  isOpen,
  title,
  description,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  variant = 'danger',
  isLoading = false,
  onConfirm,
  onCancel,
  'data-testid': testId = 'confirm-dialog',
  cancelButtonTestId,
  confirmButtonTestId,
}) => {
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelBtnRef = useRef<HTMLButtonElement>(null);

  // Focus trap and Escape listener
  useEffect(() => {
    if (!isOpen) return;

    // Focus safe cancel button on mount
    const timeout = setTimeout(() => {
      cancelBtnRef.current?.focus();
    }, 50);

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onCancel();
      } else if (e.key === 'Tab' && dialogRef.current) {
        const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
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
    return () => {
      clearTimeout(timeout);
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onCancel]);

  if (!isOpen) return null;

  let icon = <AlertTriangle className="w-5 h-5 text-status-red" />;
  let buttonVariant: 'danger' | 'primary' | 'secondary' = 'danger';

  if (variant === 'warning') {
    icon = <AlertCircle className="w-5 h-5 text-amber-500" />;
    buttonVariant = 'primary';
  } else if (variant === 'primary') {
    icon = <Info className="w-5 h-5 text-brand" />;
    buttonVariant = 'primary';
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4 bg-black/60 backdrop-blur-xs animate-in fade-in duration-150"
      onClick={(e) => {
        if (e.target === e.currentTarget && !isLoading) onCancel();
      }}
      data-testid={`${testId}-backdrop`}
    >
      <div
        ref={dialogRef}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby="confirm-dialog-desc"
        data-testid={testId}
        className={cn(
          'w-full sm:max-w-md rounded-t-3xl sm:rounded-2xl bg-surface border border-border p-6 shadow-2xl space-y-4 max-h-[90dvh] overflow-y-auto pb-[max(1.5rem,env(safe-area-inset-bottom,0px))] sm:pb-6 animate-in slide-in-from-bottom sm:slide-in-from-top-4 duration-150',
          'focus-visible:outline-hidden'
        )}
      >
        {/* Mobile handle indicator */}
        <div className="w-10 h-1 bg-border rounded-full mx-auto sm:hidden -mt-1 mb-2" />

        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <div
              className={cn(
                'w-10 h-10 rounded-full flex items-center justify-center shrink-0',
                variant === 'danger' && 'bg-status-red/10',
                variant === 'warning' && 'bg-amber-500/10',
                variant === 'primary' && 'bg-brand/10'
              )}
            >
              {icon}
            </div>
            <div>
              <h3 id="confirm-dialog-title" className="text-sm font-bold font-heading text-foreground">
                {title}
              </h3>
            </div>
          </div>
          <button
            type="button"
            onClick={onCancel}
            disabled={isLoading}
            aria-label="Close dialog"
            className="min-h-[44px] min-w-[44px] flex items-center justify-center text-foreground-muted hover:text-foreground cursor-pointer rounded-lg transition-colors -mr-2 -mt-2"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <p id="confirm-dialog-desc" className="text-xs text-foreground-muted leading-relaxed">
          {description}
        </p>

        <div className="flex flex-col-reverse sm:flex-row items-stretch sm:items-center justify-end gap-2.5 pt-2">
          <Button
            ref={cancelBtnRef}
            variant="outline"
            size="sm"
            onClick={onCancel}
            disabled={isLoading}
            className="min-h-[44px] w-full sm:w-auto"
            data-testid={cancelButtonTestId || `${testId}-cancel-btn`}
          >
            {cancelLabel}
          </Button>
          <Button
            variant={buttonVariant}
            size="sm"
            onClick={onConfirm}
            isLoading={isLoading}
            loadingText="Processing..."
            className="min-h-[44px] w-full sm:w-auto"
            data-testid={confirmButtonTestId || `${testId}-confirm-btn`}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
};
