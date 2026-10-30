import React from 'react';
import { Link } from 'react-router-dom';
import { UserPlus, LogOut } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

export const DemoModeBanner: React.FC = () => {
  const { isAnonymous, signOut } = useAuth();

  if (!isAnonymous) {
    return null;
  }

  return (
    <div
      role="banner"
      data-testid="demo-mode-banner"
      className="w-full bg-status-amber/15 border-b border-status-amber/30 px-4 py-2.5 text-xs text-foreground flex flex-wrap items-center justify-between gap-3 shrink-0 rounded-t-[28px] -mt-6 sm:-mt-8 -mx-6 sm:-mx-8 w-[calc(100%+3rem)] sm:w-[calc(100%+4rem)] mb-6 transition-colors"
    >
      <div className="flex items-center gap-2.5">
        <span className="flex h-2 w-2 relative">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-status-amber opacity-75" />
          <span className="relative inline-flex rounded-full h-2 w-2 bg-status-amber" />
        </span>
        <div className="flex items-center gap-1.5 font-medium">
          <span className="font-bold text-status-amber uppercase tracking-wider text-[11px]">
            Demo Workspace
          </span>
          <span className="text-foreground-muted hidden sm:inline">—</span>
          <span className="text-foreground-muted hidden sm:inline">
            Temporary personal demo workspace. Data is automatically deleted after 7 days.
          </span>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <Link
          to="/signup"
          data-testid="demo-banner-signup-btn"
          className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-brand text-black font-semibold text-xs hover:bg-brand-hover shadow-xs transition-transform active:scale-95 cursor-pointer"
        >
          <UserPlus className="w-3.5 h-3.5" />
          <span>Create account</span>
        </Link>
        <button
          type="button"
          data-testid="demo-banner-exit-btn"
          onClick={() => signOut()}
          className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full border border-border text-foreground-muted hover:text-foreground hover:bg-surface-subtle transition-colors text-xs cursor-pointer"
        >
          <LogOut className="w-3 h-3" />
          <span>Exit demo</span>
        </button>
      </div>
    </div>
  );
};
