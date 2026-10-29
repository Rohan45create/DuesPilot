import React from 'react';
import { cn } from '../../utils/formatters';

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'surface' | 'subtle' | 'brand';
  interactive?: boolean;
  children: React.ReactNode;
}

export const Card: React.FC<CardProps> = ({
  className,
  variant = 'default',
  interactive,
  children,
  onClick,
  onKeyDown,
  role,
  tabIndex,
  ...props
}) => {
  let variantStyles = 'bg-surface border border-border text-foreground';

  if (variant === 'brand') {
    variantStyles = 'bg-brand text-black border border-brand font-medium';
  } else if (variant === 'subtle') {
    variantStyles = 'bg-surface-subtle border border-border text-foreground';
  } else if (variant === 'surface') {
    variantStyles = 'bg-surface border border-border text-foreground';
  }

  const isClickable = interactive || Boolean(onClick);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (isClickable && onClick && (e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault();
      onClick(e as unknown as React.MouseEvent<HTMLDivElement>);
    }
    onKeyDown?.(e);
  };

  return (
    <div
      role={role ?? (isClickable ? 'button' : undefined)}
      tabIndex={tabIndex ?? (isClickable ? 0 : undefined)}
      onClick={onClick}
      onKeyDown={handleKeyDown}
      className={cn(
        'rounded-2xl p-5 transition-colors',
        variantStyles,
        isClickable && 'interactive-card focus-visible:outline-hidden',
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
};
