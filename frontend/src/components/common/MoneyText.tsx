import React from 'react';
import { formatPaiseToRupees } from '../../utils/money';
import { cn } from '../../utils/formatters';
import { useCountUp } from './CountUpNumber';

interface MoneyTextProps {
  paise: number | null | undefined;
  showSymbol?: boolean;
  showPaiseDecimals?: boolean;
  nullFallback?: string;
  variant?: 'default' | 'brand' | 'positive' | 'negative' | 'muted';
  size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | '3xl';
  className?: string;
  animateCountUp?: boolean;
}

export const MoneyText: React.FC<MoneyTextProps> = ({
  paise,
  showSymbol = true,
  showPaiseDecimals = false,
  nullFallback = 'Cannot determine',
  variant = 'default',
  size = 'md',
  className,
  animateCountUp = false,
}) => {
  const animatedPaise = useCountUp(
    typeof paise === 'number' && animateCountUp ? paise : 0
  );
  const effectivePaise = animateCountUp && typeof paise === 'number' ? animatedPaise : paise;

  const formatted = formatPaiseToRupees(effectivePaise, {
    showSymbol,
    showPaiseDecimals,
    nullFallback,
  });

  const isNull = paise === null || paise === undefined;

  let colorClass = 'text-foreground';
  if (isNull) {
    colorClass = 'text-foreground-muted font-normal';
  } else if (variant === 'brand') {
    colorClass = 'text-brand-text-light dark:text-brand font-semibold';
  } else if (variant === 'positive') {
    colorClass = 'text-brand-text-light dark:text-brand font-semibold';
  } else if (variant === 'negative') {
    colorClass = 'text-foreground font-medium';
  } else if (variant === 'muted') {
    colorClass = 'text-foreground-muted';
  }

  let sizeClass = 'text-sm';
  if (size === 'sm') sizeClass = 'text-xs';
  if (size === 'md') sizeClass = 'text-sm';
  if (size === 'lg') sizeClass = 'text-base font-semibold';
  if (size === 'xl') sizeClass = 'text-xl font-bold font-heading';
  if (size === '2xl') sizeClass = 'text-2xl font-bold font-heading';
  if (size === '3xl') sizeClass = 'text-3xl font-extrabold font-heading';

  return (
    <span
      className={cn('tabular-nums tracking-tight', sizeClass, colorClass, className)}
      title={isNull ? 'Unknown balance: required evidence is missing' : undefined}
    >
      {formatted}
    </span>
  );
};
