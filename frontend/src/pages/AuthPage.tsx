import React, { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { Eye, EyeOff, Sparkles, Loader2, AlertCircle, CheckCircle2, ArrowRight } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ExecutionRecordPanel } from '../components/auth/ExecutionRecordPanel';
import { PasswordStrength } from '../components/auth/PasswordStrength';
import { Turnstile } from '../components/auth/Turnstile';
import { getFriendlyAuthErrorMessage } from '../utils/authErrors';
import { cn } from '../utils/formatters';

interface AuthPageProps {
  initialMode?: 'signin' | 'signup';
}

export const AuthPage: React.FC<AuthPageProps> = ({ initialMode = 'signin' }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const { signInWithPassword, signUpWithPassword, signInWithGoogle, signInAnonymously } = useAuth();

  const [mode, setMode] = useState<'signin' | 'signup'>(
    location.pathname === '/signup' ? 'signup' : initialMode
  );
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [turnstileToken, setTurnstileToken] = useState<string | undefined>(undefined);

  React.useEffect(() => {
    if (location.pathname === '/signup') {
      setMode('signup');
    } else if (location.pathname === '/signin') {
      setMode('signin');
    }
  }, [location.pathname]);

  // Validation & Submission States
  const [emailError, setEmailError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [formError, setFormError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isDemoSubmitting, setIsDemoSubmitting] = useState(false);
  const [isGoogleSubmitting, setIsGoogleSubmitting] = useState(false);

  const isSmtpConfigured = import.meta.env.VITE_SMTP_CONFIGURED === 'true';

  const validateForm = (): boolean => {
    let isValid = true;
    setEmailError('');
    setPasswordError('');
    setFormError('');

    const trimmedEmail = email.trim();
    if (!trimmedEmail) {
      setEmailError('Email is required.');
      isValid = false;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      setEmailError('Please enter a valid email address.');
      isValid = false;
    }

    if (!password) {
      setPasswordError('Password is required.');
      isValid = false;
    } else if (mode === 'signup' && password.length < 8) {
      setPasswordError('Password must be at least 8 characters long.');
      isValid = false;
    }

    return isValid;
  };

  const handleEmailSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateForm() || isSubmitting) return;

    setIsSubmitting(true);
    setFormError('');
    setSuccessMessage('');

    try {
      if (mode === 'signin') {
        const { error } = await signInWithPassword(email.trim(), password);
        if (error) {
          setFormError(getFriendlyAuthErrorMessage(error));
        } else {
          const rawDest = (location.state as { from?: { pathname: string } })?.from?.pathname;
          const destination = (!rawDest || rawDest === '/') ? '/app' : rawDest;
          navigate(destination, { replace: true });
        }
      } else {
        const { error, needsEmailVerification } = await signUpWithPassword(email.trim(), password, turnstileToken);
        if (error) {
          setFormError(getFriendlyAuthErrorMessage(error));
        } else if (needsEmailVerification) {
          setSuccessMessage('Account created! Please check your email to verify your address before signing in.');
        } else {
          // Auto signed in -> route to onboarding
          navigate('/onboarding', { replace: true });
        }
      }
    } catch (err) {
      setFormError(getFriendlyAuthErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDemoSignIn = async () => {
    if (isDemoSubmitting) return;
    setIsDemoSubmitting(true);
    setFormError('');
    try {
      const { error } = await signInAnonymously(turnstileToken);
      if (error) {
        setFormError(getFriendlyAuthErrorMessage(error));
      } else {
        navigate('/app', { replace: true });
      }
    } catch (err) {
      setFormError(getFriendlyAuthErrorMessage(err));
    } finally {
      setIsDemoSubmitting(false);
    }
  };

  const handleGoogleSignIn = async () => {
    if (isGoogleSubmitting) return;
    setIsGoogleSubmitting(true);
    setFormError('');
    try {
      await signInWithGoogle();
    } catch (err) {
      setFormError(getFriendlyAuthErrorMessage(err));
      setIsGoogleSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen w-full bg-background flex items-center justify-center p-4 sm:p-6 lg:p-10 transition-colors">
      <div className="w-full max-w-6xl grid grid-cols-1 lg:grid-cols-2 gap-8 items-center">
        {/* Left Form Card */}
        <div className="w-full max-w-md mx-auto bg-surface border border-border rounded-3xl p-6 sm:p-8 shadow-xl transition-colors">
          {/* Brand header */}
          <div className="flex items-center justify-between mb-6">
            <Link to="/" className="flex items-center gap-2 group">
              <div className="w-8 h-8 rounded-lg bg-brand flex items-center justify-center text-black font-extrabold text-base shadow-xs">
                D
              </div>
              <span className="font-heading font-bold text-lg text-foreground tracking-tight">
                DuesPilot
              </span>
            </Link>

            {/* Mode toggle tabs */}
            <div className="flex items-center p-1 rounded-full bg-surface-subtle border border-border text-xs font-medium">
              <button
                type="button"
                data-testid="mode-signin-btn"
                onClick={() => {
                  setMode('signin');
                  setFormError('');
                  setSuccessMessage('');
                }}
                className={cn(
                  'px-3 py-1 rounded-full transition-all duration-150 cursor-pointer',
                  mode === 'signin'
                    ? 'bg-foreground text-background font-semibold shadow-xs'
                    : 'text-foreground-muted hover:text-foreground'
                )}
              >
                Sign in
              </button>
              <button
                type="button"
                data-testid="mode-signup-btn"
                onClick={() => {
                  setMode('signup');
                  setFormError('');
                  setSuccessMessage('');
                }}
                className={cn(
                  'px-3 py-1 rounded-full transition-all duration-150 cursor-pointer',
                  mode === 'signup'
                    ? 'bg-foreground text-background font-semibold shadow-xs'
                    : 'text-foreground-muted hover:text-foreground'
                )}
              >
                Create account
              </button>
            </div>
          </div>

          <div className="mb-6">
            <h1 className="font-heading font-extrabold text-2xl text-foreground tracking-tight">
              {mode === 'signin' ? 'Welcome back' : 'Start your MSME recovery'}
            </h1>
            <p className="text-xs sm:text-sm text-foreground-muted mt-1">
              {mode === 'signin'
                ? 'Sign in to access your dispute files, audits, and interest calculations.'
                : 'Create an account to manage cases and enforce Section 16 recovery.'}
            </p>
          </div>

          {/* Prominent Demo Mode Button */}
          <button
            type="button"
            onClick={handleDemoSignIn}
            disabled={isDemoSubmitting || isSubmitting}
            className="w-full mb-5 py-3 px-4 rounded-xl border-2 border-brand/40 bg-brand/10 hover:bg-brand/20 dark:bg-brand/15 dark:hover:bg-brand/25 text-foreground flex items-center justify-between transition-all group shadow-xs cursor-pointer disabled:opacity-50"
            data-testid="demo-login-button"
          >
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-brand flex items-center justify-center text-black">
                {isDemoSubmitting ? (
                  <Loader2 className="w-4 h-4 animate-spin text-black" />
                ) : (
                  <Sparkles className="w-4 h-4 text-black" />
                )}
              </div>
              <div className="text-left">
                <div className="text-xs sm:text-sm font-bold text-foreground flex items-center gap-1.5">
                  <span>Try demo — no sign-up</span>
                  <span className="text-[10px] uppercase font-mono px-1.5 py-0.2 rounded bg-brand/30 text-brand-text-light dark:text-brand font-semibold">
                    1-Click
                  </span>
                </div>
                <div className="text-[11px] text-foreground-muted">
                  Explore pre-seeded disputes with instant access
                </div>
              </div>
            </div>
            <ArrowRight className="w-4 h-4 text-foreground-muted group-hover:text-foreground group-hover:translate-x-0.5 transition-transform" />
          </button>

          {/* Google OAuth Button */}
          <button
            type="button"
            onClick={handleGoogleSignIn}
            disabled={isGoogleSubmitting || isSubmitting}
            className="w-full py-2.5 px-4 rounded-xl border border-border bg-surface hover:bg-surface-subtle text-foreground text-xs sm:text-sm font-semibold flex items-center justify-center gap-2.5 transition-colors shadow-xs cursor-pointer disabled:opacity-50"
            data-testid="google-signin-button"
          >
            {isGoogleSubmitting ? (
              <Loader2 className="w-4 h-4 animate-spin text-foreground-muted" />
            ) : (
              <svg className="w-4 h-4" viewBox="0 0 24 24">
                <path
                  fill="#4285F4"
                  d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                />
                <path
                  fill="#34A853"
                  d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                />
                <path
                  fill="#FBBC05"
                  d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                />
                <path
                  fill="#EA4335"
                  d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                />
              </svg>
            )}
            <span>Continue with Google</span>
          </button>

          {/* Divider */}
          <div className="relative my-5">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-border" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-surface px-2.5 text-foreground-muted font-mono text-[11px]">
                or continue with email
              </span>
            </div>
          </div>

          {/* Alerts: Form Error & Success Messages */}
          {formError && (
            <div
              role="alert"
              className="mb-4 p-3 rounded-xl bg-status-red/10 border border-status-red/30 text-status-red text-xs flex items-start gap-2.5 animate-fadeIn"
              data-testid="auth-error-banner"
            >
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <div className="leading-relaxed">{formError}</div>
            </div>
          )}

          {successMessage && (
            <div
              role="status"
              className="mb-4 p-3 rounded-xl bg-status-green/10 border border-status-green/30 text-brand-text-light dark:text-brand text-xs flex items-start gap-2.5"
            >
              <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" />
              <div className="leading-relaxed">{successMessage}</div>
            </div>
          )}

          {/* Email / Password Form */}
          <form onSubmit={handleEmailSubmit} noValidate className="space-y-4">
            <div>
              <label htmlFor="auth-email" className="block text-xs font-semibold text-foreground mb-1.5">
                Work Email
              </label>
              <input
                id="auth-email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  if (emailError) setEmailError('');
                }}
                placeholder="name@company.com"
                className={cn(
                  'w-full px-3.5 py-2.5 rounded-xl bg-surface border text-sm text-foreground placeholder:text-foreground-subtle transition-colors focus:outline-hidden min-h-[44px]',
                  emailError
                    ? 'border-status-red focus:border-status-red'
                    : 'border-border focus:border-brand'
                )}
                data-testid="email-input"
              />
              {emailError && (
                <p className="text-[11px] text-status-red mt-1" data-testid="email-error">
                  {emailError}
                </p>
              )}
            </div>

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label htmlFor="auth-password" className="block text-xs font-semibold text-foreground">
                  Password
                </label>
                {mode === 'signin' && (
                  <div>
                    {isSmtpConfigured ? (
                      <button
                        type="button"
                        onClick={() => alert('Password reset link sent if account exists.')}
                        className="text-[11px] text-foreground-muted hover:text-foreground transition-colors cursor-pointer"
                      >
                        Forgot password?
                      </button>
                    ) : (
                      <span className="text-[11px] text-foreground-subtle">
                        Use Google or the demo
                      </span>
                    )}
                  </div>
                )}
              </div>

              <div className="relative">
                <input
                  id="auth-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (passwordError) setPasswordError('');
                  }}
                  placeholder="••••••••••••"
                  className={cn(
                    'w-full pl-3.5 pr-11 py-2.5 rounded-xl bg-surface border text-sm text-foreground placeholder:text-foreground-subtle transition-colors focus:outline-hidden min-h-[44px]',
                    passwordError
                      ? 'border-status-red focus:border-status-red'
                      : 'border-border focus:border-brand'
                  )}
                  data-testid="password-input"
                />
                <button
                  type="button"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-1 top-1/2 -translate-y-1/2 text-foreground-muted hover:text-foreground transition-colors cursor-pointer w-10 h-10 flex items-center justify-center min-h-[40px] min-w-[40px]"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>

              {passwordError && (
                <p className="text-[11px] text-status-red mt-1" data-testid="password-error">
                  {passwordError}
                </p>
              )}

              {/* Password strength hint on sign-up */}
              {mode === 'signup' && <PasswordStrength password={password} />}
            </div>

            {/* Cloudflare Turnstile if site key configured */}
            <Turnstile
              onVerify={(token) => setTurnstileToken(token)}
              onExpire={() => setTurnstileToken(undefined)}
            />

            {/* Submit Button */}
            <button
              type="submit"
              disabled={isSubmitting || isDemoSubmitting}
              className="w-full py-3 rounded-xl bg-foreground text-background hover:opacity-90 font-bold text-xs sm:text-sm transition-all shadow-md flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
              data-testid="submit-auth-button"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>{mode === 'signin' ? 'Signing in...' : 'Creating account...'}</span>
                </>
              ) : (
                <span>{mode === 'signin' ? 'Sign in to DuesPilot' : 'Create your account'}</span>
              )}
            </button>
          </form>

          {/* Footer toggle note */}
          <div className="mt-6 text-center text-xs text-foreground-muted">
            {mode === 'signin' ? (
              <span>
                Don't have an account?{' '}
                <button
                  type="button"
                  onClick={() => {
                    setMode('signup');
                    setFormError('');
                    setSuccessMessage('');
                  }}
                  className="text-foreground font-semibold hover:underline cursor-pointer"
                >
                  Create one now
                </button>
              </span>
            ) : (
              <span>
                Already have an account?{' '}
                <button
                  type="button"
                  onClick={() => {
                    setMode('signin');
                    setFormError('');
                    setSuccessMessage('');
                  }}
                  className="text-foreground font-semibold hover:underline cursor-pointer"
                >
                  Sign in here
                </button>
              </span>
            )}
          </div>
        </div>

        {/* Right Brand Panel (Animated Execution Record) */}
        <div className="hidden lg:block w-full h-full">
          <ExecutionRecordPanel />
        </div>
      </div>
    </div>
  );
};
