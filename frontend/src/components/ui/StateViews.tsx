import React from 'react';
import { AlertCircle, FolderOpen, RefreshCw } from 'lucide-react';
import { Button } from './Button';
import { cn } from '../../utils/formatters';

export const LoadingSkeleton: React.FC<{ className?: string; rows?: number }> = ({
  className,
  rows = 3,
}) => {
  return (
    <div className={cn('animate-pulse space-y-3 w-full', className)} role="status" aria-label="Loading data">
      <div className="h-6 bg-surface-subtle rounded-md w-1/3" />
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="h-10 bg-surface-subtle rounded-lg w-full" />
      ))}
      <span className="sr-only">Loading...</span>
    </div>
  );
};

export const ErrorState: React.FC<{
  title?: string;
  message?: string;
  onRetry?: () => void;
  className?: string;
}> = ({
  title = 'Failed to load data',
  message = 'An unexpected error occurred while communicating with the server.',
  onRetry,
  className,
}) => {
  return (
    <div
      role="alert"
      className={cn(
        'p-8 rounded-2xl border border-status-red/30 bg-status-red/5 flex flex-col items-center justify-center text-center max-w-md mx-auto my-6',
        className
      )}
    >
      <div className="w-10 h-10 rounded-full bg-status-red/10 flex items-center justify-center text-status-red mb-3">
        <AlertCircle className="w-5 h-5" />
      </div>
      <h3 className="text-sm font-bold font-heading text-foreground mb-1">
        {title}
      </h3>
      <p className="text-xs text-foreground-muted leading-relaxed mb-4">
        {message}
      </p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry} className="text-xs">
          <RefreshCw className="w-3.5 h-3.5 mr-1.5" />
          Retry
        </Button>
      )}
    </div>
  );
};

export const EmptyState: React.FC<{
  title?: string;
  description?: string;
  actionText?: string;
  onAction?: () => void;
  className?: string;
}> = ({
  title = 'No records found',
  description = 'There are no items to display right now.',
  actionText,
  onAction,
  className,
}) => {
  return (
    <div
      className={cn(
        'p-10 rounded-2xl border border-border bg-surface-subtle/40 flex flex-col items-center justify-center text-center my-6',
        className
      )}
    >
      <div className="w-10 h-10 rounded-full bg-surface border border-border flex items-center justify-center text-foreground-muted mb-3">
        <FolderOpen className="w-5 h-5" />
      </div>
      <h3 className="text-sm font-bold font-heading text-foreground mb-1">
        {title}
      </h3>
      <p className="text-xs text-foreground-muted max-w-sm mb-4">
        {description}
      </p>
      {actionText && onAction && (
        <Button size="sm" onClick={onAction} className="text-xs">
          {actionText}
        </Button>
      )}
    </div>
  );
};

export const ServerWakingUpState: React.FC<{
  onRetry?: () => void;
  className?: string;
  estimatedSeconds?: number;
}> = ({ onRetry, className, estimatedSeconds = 25 }) => {
  const [secondsElapsed, setSecondsElapsed] = React.useState(0);

  React.useEffect(() => {
    const timer = setInterval(() => {
      setSecondsElapsed((prev) => prev + 1);
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const progressPercent = Math.min(Math.round((secondsElapsed / estimatedSeconds) * 100), 98);

  return (
    <div
      role="status"
      aria-live="polite"
      data-testid="server-waking-up-state"
      className={cn(
        'p-8 rounded-2xl border border-brand/30 bg-surface flex flex-col items-center justify-center text-center max-w-md mx-auto my-8 shadow-xl shadow-brand/5 animate-in fade-in duration-300',
        className
      )}
    >
      <div className="relative w-12 h-12 rounded-2xl bg-brand/10 border border-brand/30 flex items-center justify-center text-brand mb-4">
        <RefreshCw className="w-6 h-6 animate-spin text-brand" />
      </div>
      <h3 className="text-base font-bold font-heading text-foreground mb-1.5">
        Waking up the server
      </h3>
      <p className="text-xs text-foreground-muted leading-relaxed max-w-sm mb-5">
        Our backend services enter sleep mode when idle to conserve energy. We're spinning things back up for you right now — usually takes 15–30 seconds.
      </p>

      {/* Progress bar */}
      <div className="w-full bg-surface-muted rounded-full h-2 overflow-hidden mb-2 border border-border">
        <div
          className="bg-brand h-full rounded-full transition-all duration-1000 ease-out"
          style={{ width: `${progressPercent}%` }}
        />
      </div>
      <div className="flex justify-between w-full text-[10px] text-foreground-muted font-mono mb-5">
        <span>Initializing containers...</span>
        <span>{progressPercent}%</span>
      </div>

      {onRetry && (
        <Button
          variant="secondary"
          size="sm"
          onClick={onRetry}
          className="text-xs"
          data-testid="server-wake-retry-btn"
        >
          <RefreshCw className="w-3.5 h-3.5 mr-1.5" />
          Check Again
        </Button>
      )}
    </div>
  );
};
