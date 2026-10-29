import React, { useState } from 'react';
import { Copy, Check } from 'lucide-react';
import { cn } from '../../utils/formatters';

export interface CopyButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  textToCopy: string;
  label?: string;
  copiedLabel?: string;
  showText?: boolean;
}

export const CopyButton: React.FC<CopyButtonProps> = ({
  textToCopy,
  label = 'Copy',
  copiedLabel = 'Copied',
  showText = true,
  className,
  onClick,
  ...props
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = async (e: React.MouseEvent<HTMLButtonElement>) => {
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(textToCopy);
      }
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Fallback
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
    onClick?.(e);
  };

  return (
    <button
      type="button"
      data-testid="copy-btn"
      onClick={handleCopy}
      aria-label={copied ? `${copiedLabel} to clipboard` : `${label} to clipboard`}
      title={copied ? copiedLabel : label}
      className={cn(
        'inline-flex items-center gap-1.5 px-2.5 py-1 text-xs rounded-lg border transition-all duration-150 cursor-pointer select-none',
        copied
          ? 'bg-brand/10 border-brand/40 text-brand-text-light dark:text-brand font-semibold'
          : 'bg-surface-subtle border-border text-foreground-muted hover:text-foreground hover:bg-surface-hover',
        className
      )}
      {...props}
    >
      {copied ? (
        <Check className="w-3.5 h-3.5 text-brand animate-in zoom-in-75 duration-150 shrink-0" />
      ) : (
        <Copy className="w-3.5 h-3.5 shrink-0" />
      )}
      {showText && <span>{copied ? copiedLabel : label}</span>}
      <span className="sr-only" role="status">
        {copied ? copiedLabel : ''}
      </span>
    </button>
  );
};
