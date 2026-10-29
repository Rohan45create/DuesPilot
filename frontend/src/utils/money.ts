/**
 * Rule R-45: The UI never does money arithmetic.
 * It formats paise to rupees (Indian grouping) and displays server-computed values only.
 */

/**
 * Formats integer paise into Indian currency format (e.g. 5620000 -> ₹56,200).
 * Handles null/undefined by returning "Cannot determine" or fallback string (Rule R-48).
 */
export function formatPaiseToRupees(
  paise: number | null | undefined,
  options?: {
    showSymbol?: boolean;
    showPaiseDecimals?: boolean;
    nullFallback?: string;
  }
): string {
  const showSymbol = options?.showSymbol ?? true;
  const showPaiseDecimals = options?.showPaiseDecimals ?? false;
  const nullFallback = options?.nullFallback ?? 'Cannot determine';

  if (paise === null || paise === undefined) {
    return nullFallback;
  }

  const isNegative = paise < 0;
  const absolutePaise = Math.abs(paise);
  const rupees = Math.floor(absolutePaise / 100);
  const remainderPaise = absolutePaise % 100;

  // Indian number grouping (last 3 digits, then groups of 2 digits)
  const rupeesStr = rupees.toString();
  let formattedRupees: string;

  if (rupeesStr.length <= 3) {
    formattedRupees = rupeesStr;
  } else {
    const lastThree = rupeesStr.substring(rupeesStr.length - 3);
    const otherNumbers = rupeesStr.substring(0, rupeesStr.length - 3);
    const grouped = otherNumbers.replace(/\B(?=(\d{2})+(?!\d))/g, ',');
    formattedRupees = `${grouped},${lastThree}`;
  }

  let result = formattedRupees;
  if (showPaiseDecimals && remainderPaise > 0) {
    result += `.${remainderPaise.toString().padStart(2, '0')}`;
  }

  const sign = isNegative ? '−' : '';
  const symbol = showSymbol ? '₹' : '';

  return `${sign}${symbol}${result}`;
}

/**
 * Formats micro-dollars into standard USD format (e.g. 17000 micros -> $0.017)
 */
export function formatMicrosToUsd(micros: number | null | undefined): string {
  if (micros === null || micros === undefined) {
    return '[ — ]';
  }
  const dollars = micros / 1_000_000;
  if (dollars < 0.01 && dollars > 0) {
    return `$${dollars.toFixed(4)}`;
  }
  return `$${dollars.toFixed(2)}`;
}

/**
 * Formats percentages with one decimal place.
 */
export function formatPercentage(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '[ — ]%';
  }
  return `${value.toFixed(1)}%`;
}
