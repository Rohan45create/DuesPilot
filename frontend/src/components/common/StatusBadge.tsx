import React from 'react';
import { cn } from '../../utils/formatters';

export type StatusType =
  | 'NEEDS_REVIEW'
  | 'RECONCILED'
  | 'MISSING_PROOF'
  | 'CLARIFY'
  | 'PARSED'
  | 'CONFIRMED'
  | 'UNVERIFIED_EXCLUDED'
  | 'CONFLICT'
  | 'READY'
  | 'PENDING';

interface StatusBadgeProps {
  status: string | StatusType;
  label?: string;
  size?: 'sm' | 'md';
  className?: string;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({
  status,
  label,
  size = 'md',
  className,
}) => {
  const normStatus = status.toUpperCase().replace(/\s+/g, '_');

  let displayLabel = label;
  let colorClasses = '';

  switch (normStatus) {
    case 'NEEDS_REVIEW':
      displayLabel = displayLabel ?? 'Needs review';
      colorClasses =
        'text-amber-500 border border-amber-500/40 bg-amber-500/10 dark:text-amber-400 dark:border-amber-400/30 dark:bg-amber-400/10';
      break;
    case 'RECONCILED':
      displayLabel = displayLabel ?? 'Reconciled';
      colorClasses =
        'text-brand-text-light border border-brand/50 bg-brand/10 dark:text-brand dark:border-brand/40 dark:bg-brand/10';
      break;
    case 'MISSING_PROOF':
      displayLabel = displayLabel ?? 'Missing proof';
      colorClasses =
        'text-amber-600 border border-amber-600/40 bg-amber-600/10 dark:text-amber-400 dark:border-amber-400/30 dark:bg-amber-400/10';
      break;
    case 'CLARIFY':
      displayLabel = displayLabel ?? 'Clarify';
      colorClasses =
        'text-foreground-muted border border-border bg-surface-subtle dark:text-foreground-muted dark:border-border dark:bg-surface-subtle';
      break;
    case 'PARSED':
      displayLabel = displayLabel ?? 'Parsed';
      colorClasses =
        'text-foreground-muted border border-border bg-surface-subtle dark:text-foreground-muted dark:border-border dark:bg-surface-subtle';
      break;
    case 'CONFIRMED':
      displayLabel = displayLabel ?? 'Confirmed';
      colorClasses =
        'text-brand-text-light border border-brand/40 bg-brand/10 dark:text-brand dark:border-brand/30 dark:bg-brand/10';
      break;
    case 'UNVERIFIED_EXCLUDED':
      displayLabel = displayLabel ?? 'Unverified · excluded';
      colorClasses =
        'text-amber-600 border border-amber-600/40 bg-amber-600/10 dark:text-amber-400 dark:border-amber-400/30 dark:bg-amber-400/10';
      break;
    case 'CONFLICT':
      displayLabel = displayLabel ?? 'Conflict';
      colorClasses =
        'text-amber-500 border border-amber-500/40 bg-amber-500/10 dark:text-amber-400 dark:border-amber-400/30 dark:bg-amber-400/10';
      break;
    case 'READY':
      displayLabel = displayLabel ?? 'Ready';
      colorClasses =
        'text-brand-text-light border border-brand/40 bg-brand/10 dark:text-brand dark:border-brand/30 dark:bg-brand/10';
      break;
    default:
      displayLabel = displayLabel ?? status;
      colorClasses =
        'text-foreground-muted border border-border bg-surface-subtle';
  }

  const sizeClasses =
    size === 'sm'
      ? 'px-2 py-0.5 text-xs'
      : 'px-3 py-1 text-xs font-medium';

  return (
    <span
      className={cn(
        'inline-flex items-center justify-center rounded-full transition-colors',
        sizeClasses,
        colorClasses,
        className
      )}
    >
      {displayLabel}
    </span>
  );
};
