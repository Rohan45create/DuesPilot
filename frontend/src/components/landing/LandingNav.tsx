import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Sun, Moon, Menu, X, ArrowRight, ShieldCheck } from 'lucide-react';
import { useTheme } from '../../context/ThemeContext';
import { useAuth } from '../../context/AuthContext';

export const LandingNav: React.FC = () => {
  const { theme, toggleTheme } = useTheme();
  const { user } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const scrollToSection = (e: React.MouseEvent<HTMLAnchorElement>, id: string) => {
    e.preventDefault();
    setMobileMenuOpen(false);
    const element = document.getElementById(id);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <header className="sticky top-0 z-50 w-full backdrop-blur-md bg-background/85 border-b border-border/80 transition-colors">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        {/* Logo */}
        <Link
          to="/"
          data-testid="landing-logo"
          className="flex items-center gap-2.5 group transition-opacity hover:opacity-90 focus-visible:ring-2 focus-visible:ring-brand rounded-lg p-1"
          aria-label="DuesPilot Home"
        >
          <div className="w-8 h-8 rounded-lg bg-brand flex items-center justify-center text-black font-extrabold text-base tracking-tighter shadow-sm">
            D
          </div>
          <span className="font-heading font-extrabold text-lg sm:text-xl text-foreground tracking-tight">
            DuesPilot
          </span>
        </Link>

        {/* Desktop Navigation Links */}
        <nav aria-label="Main Navigation" className="hidden md:flex items-center gap-7">
          <a
            href="#how-it-works"
            data-testid="landing-nav-how-it-works"
            onClick={(e) => scrollToSection(e, 'how-it-works')}
            className="text-sm font-medium text-foreground-muted hover:text-brand transition-colors"
          >
            How it works
          </a>
          <a
            href="#proof"
            data-testid="landing-nav-proof"
            onClick={(e) => scrollToSection(e, 'proof')}
            className="text-sm font-medium text-foreground-muted hover:text-brand transition-colors"
          >
            Proof
          </a>
          <a
            href="#models"
            data-testid="landing-nav-models"
            onClick={(e) => scrollToSection(e, 'models')}
            className="text-sm font-medium text-foreground-muted hover:text-brand transition-colors"
          >
            Models
          </a>
          <a
            href="#guardrails"
            data-testid="landing-nav-guardrails"
            onClick={(e) => scrollToSection(e, 'guardrails')}
            className="text-sm font-medium text-foreground-muted hover:text-brand transition-colors"
          >
            Guardrails
          </a>
        </nav>

        {/* Right action group */}
        <div className="hidden md:flex items-center gap-3">
          {/* Theme toggle */}
          <button
            type="button"
            data-testid="landing-theme-toggle"
            onClick={toggleTheme}
            aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
            className="w-9 h-9 rounded-lg border border-border flex items-center justify-center text-foreground-muted hover:text-foreground hover:bg-surface transition-colors cursor-pointer"
          >
            {theme === 'dark' ? <Sun className="w-4 h-4 text-amber-400" /> : <Moon className="w-4 h-4 text-brand" />}
          </button>

          {/* Sign in / App Link */}
          {user ? (
            <Link
              to="/app"
              data-testid="landing-open-app-btn"
              className="text-sm font-medium text-foreground-muted hover:text-foreground px-3 py-1.5 transition-colors"
            >
              Open App
            </Link>
          ) : (
            <Link
              to="/signin"
              data-testid="landing-signin-btn"
              className="text-sm font-medium text-foreground-muted hover:text-foreground px-3 py-1.5 transition-colors"
            >
              Sign in
            </Link>
          )}

          {/* Try Demo Primary Button */}
          <Link
            to={user ? "/app" : "/signin"}
            data-testid="landing-try-demo-btn"
            className="inline-flex items-center gap-1.5 bg-brand hover:bg-brand-hover text-black font-semibold text-sm px-4 py-2 rounded-lg transition-all shadow-xs group"
          >
            <span>Try demo</span>
            <ArrowRight className="w-4 h-4 transition-transform group-hover:translate-x-0.5" />
          </Link>
        </div>

        {/* Mobile menu trigger */}
        <div className="flex md:hidden items-center gap-2">
          <button
            type="button"
            data-testid="landing-theme-toggle-mobile"
            onClick={toggleTheme}
            aria-label="Toggle theme"
            className="w-11 h-11 min-h-[44px] min-w-[44px] rounded-xl border border-border flex items-center justify-center text-foreground-muted hover:text-foreground cursor-pointer"
          >
            {theme === 'dark' ? <Sun className="w-4 h-4 text-amber-400" /> : <Moon className="w-4 h-4 text-brand" />}
          </button>
          <button
            type="button"
            data-testid="landing-mobile-menu-btn"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            aria-expanded={mobileMenuOpen}
            aria-label={mobileMenuOpen ? 'Close navigation menu' : 'Open navigation menu'}
            className="w-11 h-11 min-h-[44px] min-w-[44px] rounded-xl border border-border flex items-center justify-center text-foreground cursor-pointer"
          >
            {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>
      </div>

      {/* Mobile navigation drawer */}
      {mobileMenuOpen && (
        <div className="md:hidden border-b border-border bg-surface px-4 pt-3 pb-6 space-y-3 animate-in fade-in slide-in-from-top-2 duration-200">
          <nav className="flex flex-col space-y-2">
            <a
              href="#how-it-works"
              onClick={(e) => scrollToSection(e, 'how-it-works')}
              className="text-base font-medium text-foreground px-2 py-2 rounded-md hover:bg-surface-hover"
            >
              How it works
            </a>
            <a
              href="#proof"
              onClick={(e) => scrollToSection(e, 'proof')}
              className="text-base font-medium text-foreground px-2 py-2 rounded-md hover:bg-surface-hover"
            >
              Proof
            </a>
            <a
              href="#models"
              onClick={(e) => scrollToSection(e, 'models')}
              className="text-base font-medium text-foreground px-2 py-2 rounded-md hover:bg-surface-hover"
            >
              Models
            </a>
            <a
              href="#guardrails"
              onClick={(e) => scrollToSection(e, 'guardrails')}
              className="text-base font-medium text-foreground px-2 py-2 rounded-md hover:bg-surface-hover"
            >
              Guardrails
            </a>
          </nav>

          <div className="pt-3 border-t border-border flex flex-col gap-2.5">
            {user ? (
              <Link
                to="/app"
                onClick={() => setMobileMenuOpen(false)}
                className="w-full text-center py-2.5 rounded-lg border border-border text-sm font-semibold text-foreground hover:bg-surface-hover"
              >
                Go to Workspace
              </Link>
            ) : (
              <Link
                to="/signin"
                onClick={() => setMobileMenuOpen(false)}
                className="w-full text-center py-2.5 rounded-lg border border-border text-sm font-semibold text-foreground hover:bg-surface-hover"
              >
                Sign in
              </Link>
            )}

            <Link
              to={user ? "/app" : "/signin"}
              onClick={() => setMobileMenuOpen(false)}
              className="w-full text-center py-2.5 rounded-lg bg-brand hover:bg-brand-hover text-black text-sm font-semibold flex items-center justify-center gap-2 shadow-xs"
            >
              <ShieldCheck className="w-4 h-4" />
              <span>Try demo — No sign-up required</span>
            </Link>
          </div>
        </div>
      )}
    </header>
  );
};
