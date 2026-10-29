import React from 'react';

interface PasswordStrengthProps {
  password?: string;
}

export const getPasswordStrength = (password: string = ''): { score: number; label: string; color: string; hint: string } => {
  if (!password) {
    return { score: 0, label: '', color: 'bg-border', hint: '' };
  }

  let score = 0;
  if (password.length >= 8) score += 1;
  if (/[A-Z]/.test(password) && /[a-z]/.test(password)) score += 1;
  if (/[0-9]/.test(password)) score += 1;
  if (/[^A-Za-z0-9]/.test(password)) score += 1;

  if (score <= 1) {
    return {
      score: 1,
      label: 'Weak',
      color: 'bg-status-red',
      hint: 'Include at least 8 characters with letters, numbers & symbols',
    };
  }
  if (score <= 3) {
    return {
      score: 2,
      label: 'Medium',
      color: 'bg-status-amber',
      hint: 'Add a special symbol or mix case to make it stronger',
    };
  }
  return {
    score: 3,
    label: 'Strong',
    color: 'bg-brand',
    hint: 'Excellent password strength',
  };
};

export const PasswordStrength: React.FC<PasswordStrengthProps> = ({ password = '' }) => {
  if (!password) return null;

  const { score, label, color, hint } = getPasswordStrength(password);

  return (
    <div className="mt-2 space-y-1.5" data-testid="password-strength-container">
      <div className="flex items-center justify-between text-xs">
        <span className="text-foreground-muted">Password strength:</span>
        <span className="font-semibold text-foreground" data-testid="password-strength-label">{label}</span>
      </div>
      <div className="grid grid-cols-3 gap-1.5 h-1.5 w-full">
        <div className={`h-full rounded-full transition-all duration-300 ${score >= 1 ? color : 'bg-border'}`} />
        <div className={`h-full rounded-full transition-all duration-300 ${score >= 2 ? color : 'bg-border'}`} />
        <div className={`h-full rounded-full transition-all duration-300 ${score >= 3 ? color : 'bg-border'}`} />
      </div>
      {hint && <p className="text-[11px] text-foreground-muted">{hint}</p>}
    </div>
  );
};
