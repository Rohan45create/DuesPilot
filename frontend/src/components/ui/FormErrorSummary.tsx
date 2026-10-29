import React from 'react';
import { AlertCircle } from 'lucide-react';
import { cn } from '../../utils/formatters';

export interface FormErrorItem {
  field?: string;
  message: string;
  fieldId?: string;
}

export interface FormErrorSummaryProps {
  errors: (string | FormErrorItem)[];
  title?: string;
  className?: string;
}

export const FormErrorSummary: React.FC<FormErrorSummaryProps> = ({
  errors,
  title = 'Please correct the following errors:',
  className,
}) => {
  if (!errors || errors.length === 0) return null;

  const normalizedErrors = errors.map((err) =>
    typeof err === 'string' ? { message: err } : err
  );

  const handleFieldClick = (fieldId?: string) => {
    if (!fieldId) return;
    const element = document.getElementById(fieldId);
    if (element) {
      element.focus();
      element.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  };

  return (
    <div
      role="alert"
      aria-atomic="true"
      data-testid="form-error-summary"
      className={cn(
        'p-4 rounded-xl border border-status-red/40 bg-status-red/10 text-foreground text-xs space-y-2 animate-in fade-in duration-200',
        className
      )}
    >
      <div className="flex items-center gap-2 font-semibold text-status-red">
        <AlertCircle className="w-4 h-4 shrink-0" />
        <span>{title}</span>
      </div>
      <ul className="list-disc pl-5 space-y-1 text-foreground-muted">
        {normalizedErrors.map((err, idx) => (
          <li key={idx}>
            {err.fieldId ? (
              <button
                type="button"
                onClick={() => handleFieldClick(err.fieldId)}
                className="text-status-red hover:underline focus-visible:outline-2 focus-visible:outline-status-red cursor-pointer text-left"
              >
                {err.field ? <strong className="text-foreground">{err.field}: </strong> : null}
                {err.message}
              </button>
            ) : (
              <span>
                {err.field ? <strong className="text-foreground">{err.field}: </strong> : null}
                {err.message}
              </span>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
};
