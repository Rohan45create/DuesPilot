import React from 'react';
import { Loader2 } from 'lucide-react';
import { cn } from '../../utils/formatters';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg' | 'icon';
  isLoading?: boolean;
  loadingText?: string;
  children: React.ReactNode;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant = 'primary',
      size = 'md',
      isLoading = false,
      loadingText,
      children,
      disabled,
      ...props
    },
    ref
  ) => {
    let variantStyles = '';
    switch (variant) {
      case 'primary':
        variantStyles =
          'bg-brand text-black hover:bg-brand-hover font-semibold shadow-xs active:scale-[0.98] border border-transparent';
        break;
      case 'secondary':
        variantStyles =
          'bg-surface-subtle text-foreground hover:bg-surface-hover border border-border font-medium active:scale-[0.98]';
        break;
      case 'outline':
        variantStyles =
          'bg-transparent text-foreground hover:bg-surface-subtle border border-border font-medium active:scale-[0.98]';
        break;
      case 'ghost':
        variantStyles =
          'bg-transparent text-foreground-muted hover:text-foreground hover:bg-surface-subtle border border-transparent active:scale-[0.98]';
        break;
      case 'danger':
        variantStyles =
          'bg-status-red/10 text-status-red hover:bg-status-red/20 border border-status-red/30 font-medium active:scale-[0.98]';
        break;
    }

    let sizeStyles = '';
    switch (size) {
      case 'sm':
        sizeStyles = 'h-8 px-3 text-xs rounded-full';
        break;
      case 'md':
        sizeStyles = 'h-10 px-5 text-sm rounded-full';
        break;
      case 'lg':
        sizeStyles = 'h-12 px-6 text-base rounded-full';
        break;
      case 'icon':
        sizeStyles = 'h-10 w-10 p-0 rounded-full flex items-center justify-center';
        break;
    }

    const isEffectivelyDisabled = disabled || isLoading;

    return (
      <button
        ref={ref}
        disabled={isEffectivelyDisabled}
        aria-busy={isLoading ? 'true' : undefined}
        className={cn(
          'inline-flex items-center justify-center gap-2 cursor-pointer transition-all duration-150 select-none disabled:opacity-40 disabled:cursor-not-allowed disabled:pointer-events-none focus-visible:outline-hidden',
          variantStyles,
          sizeStyles,
          className
        )}
        {...props}
      >
        {isLoading && (
          <Loader2
            className={cn(
              'animate-spin shrink-0',
              size === 'sm' ? 'w-3.5 h-3.5' : 'w-4 h-4'
            )}
            data-testid="button-spinner"
            aria-hidden="true"
          />
        )}
        {isLoading && loadingText ? loadingText : children}
      </button>
    );
  }
);

Button.displayName = 'Button';
