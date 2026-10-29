import React, { useState, useEffect, useRef } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import {
  LayoutGrid,
  PlusCircle,
  Folder,
  CheckCircle2,
  MoreHorizontal,
  BarChart3,
  SlidersHorizontal,
  Settings,
  Sun,
  Moon,
  LogOut,
  X,
} from 'lucide-react';
import { useTheme } from '../../context/ThemeContext';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { cn } from '../../utils/formatters';

interface BottomTabBarProps {
  className?: string;
}

export const BottomTabBar: React.FC<BottomTabBarProps> = ({ className }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const { signOut } = useAuth();
  const { showToast } = useToast();
  const [showMoreSheet, setShowMoreSheet] = useState(false);
  const sheetRef = useRef<HTMLDivElement>(null);

  // Close sheet on route change or Escape
  useEffect(() => {
    setShowMoreSheet(false);
  }, [location.pathname]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && showMoreSheet) {
        setShowMoreSheet(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [showMoreSheet]);

  const handleSignOut = () => {
    setShowMoreSheet(false);
    signOut();
    showToast('Signed out successfully.', 'info');
    navigate('/signin');
  };

  const isTabActive = (path: string, exact = false) => {
    if (exact || path === '/app') {
      return location.pathname === path;
    }
    return location.pathname.startsWith(path);
  };

  const isMoreActive =
    location.pathname.startsWith('/app/benchmarks') ||
    location.pathname.startsWith('/app/models') ||
    location.pathname.startsWith('/app/settings');

  return (
    <>
      {/* Fixed Bottom Tab Bar for Mobile & Tablet (< 1024px) */}
      <nav
        aria-label="Mobile Bottom Navigation"
        data-testid="bottom-tab-bar"
        className={cn(
          'fixed bottom-0 inset-x-0 z-40 lg:hidden',
          'bg-surface/95 dark:bg-[#121512]/95 backdrop-blur-md border-t border-border',
          'flex items-center justify-around px-2 pb-[max(0.5rem,env(safe-area-inset-bottom,0px))] pt-1 h-[calc(3.75rem+env(safe-area-inset-bottom,0px))]',
          'shadow-2xl transition-colors',
          className
        )}
      >
        {/* 1. Overview */}
        <NavLink
          to="/app"
          data-testid="bottom-nav-overview"
          aria-label="Overview"
          className={() =>
            cn(
              'flex flex-col items-center justify-center flex-1 h-full min-h-[44px] min-w-[44px] rounded-xl transition-all duration-150',
              location.pathname === '/app'
                ? 'text-brand font-bold scale-105'
                : 'text-foreground-muted hover:text-foreground active:scale-95'
            )
          }
        >
          <LayoutGrid className="w-5 h-5 mb-0.5" />
          <span className="text-[10px] font-medium leading-none">Overview</span>
        </NavLink>

        {/* 2. New Case */}
        <NavLink
          to="/app/cases/new"
          data-testid="bottom-nav-new-case"
          aria-label="New case"
          className={() =>
            cn(
              'flex flex-col items-center justify-center flex-1 h-full min-h-[44px] min-w-[44px] rounded-xl transition-all duration-150',
              isTabActive('/app/cases/new')
                ? 'text-brand font-bold scale-105'
                : 'text-foreground-muted hover:text-foreground active:scale-95'
            )
          }
        >
          <PlusCircle className="w-5 h-5 mb-0.5" />
          <span className="text-[10px] font-medium leading-none">New case</span>
        </NavLink>

        {/* 3. Cases */}
        <NavLink
          to="/app/cases"
          data-testid="bottom-nav-cases"
          aria-label="Cases"
          className={() =>
            cn(
              'flex flex-col items-center justify-center flex-1 h-full min-h-[44px] min-w-[44px] rounded-xl transition-all duration-150',
              isTabActive('/app/cases') && !isTabActive('/app/cases/new')
                ? 'text-brand font-bold scale-105'
                : 'text-foreground-muted hover:text-foreground active:scale-95'
            )
          }
        >
          <Folder className="w-5 h-5 mb-0.5" />
          <span className="text-[10px] font-medium leading-none">Cases</span>
        </NavLink>

        {/* 4. Review */}
        <NavLink
          to="/app/review"
          data-testid="bottom-nav-review-queue"
          aria-label="Review"
          className={() =>
            cn(
              'flex flex-col items-center justify-center flex-1 h-full min-h-[44px] min-w-[44px] rounded-xl transition-all duration-150',
              isTabActive('/app/review')
                ? 'text-brand font-bold scale-105'
                : 'text-foreground-muted hover:text-foreground active:scale-95'
            )
          }
        >
          <CheckCircle2 className="w-5 h-5 mb-0.5" />
          <span className="text-[10px] font-medium leading-none">Review</span>
        </NavLink>

        {/* 5. More */}
        <button
          type="button"
          data-testid="tab-btn-more"
          aria-label="More options"
          aria-expanded={showMoreSheet}
          onClick={() => setShowMoreSheet(true)}
          className={cn(
            'flex flex-col items-center justify-center flex-1 h-full min-h-[44px] min-w-[44px] rounded-xl transition-all duration-150 cursor-pointer',
            showMoreSheet || isMoreActive
              ? 'text-brand font-bold scale-105'
              : 'text-foreground-muted hover:text-foreground active:scale-95'
          )}
        >
          <MoreHorizontal className="w-5 h-5 mb-0.5" />
          <span className="text-[10px] font-medium leading-none">More</span>
        </button>
      </nav>

      {/* "More" Bottom Sheet Dialog on Mobile & Tablet */}
      {showMoreSheet && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="More navigation and settings"
          data-testid="mobile-more-sheet"
          className="fixed inset-0 z-50 lg:hidden flex flex-col justify-end bg-black/60 backdrop-blur-xs animate-in fade-in duration-200"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowMoreSheet(false);
          }}
        >
          <div
            ref={sheetRef}
            className="w-full bg-surface dark:bg-[#141814] border-t border-border rounded-t-3xl p-5 pb-[max(1.5rem,env(safe-area-inset-bottom,0px))] shadow-2xl animate-in slide-in-from-bottom duration-200 max-h-[85dvh] overflow-y-auto space-y-4"
          >
            {/* Sheet Handle */}
            <div className="flex items-center justify-between pb-2 border-b border-border/60">
              <div className="flex items-center gap-2">
                <div className="w-8 h-1 rounded-full bg-foreground-muted/30" />
                <span className="font-heading font-bold text-sm text-foreground">Menu & Navigation</span>
              </div>
              <button
                type="button"
                data-testid="close-more-sheet-btn"
                aria-label="Close menu"
                onClick={() => setShowMoreSheet(false)}
                className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-full text-foreground-muted hover:text-foreground hover:bg-surface-subtle transition-colors cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Navigation links */}
            <div className="space-y-1">
              <NavLink
                to="/app/benchmarks"
                data-testid="more-sheet-benchmarks"
                onClick={() => setShowMoreSheet(false)}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 px-3.5 py-3 rounded-xl min-h-[44px] transition-colors text-sm font-semibold',
                    isActive
                      ? 'bg-brand/15 text-brand'
                      : 'text-foreground hover:bg-surface-subtle active:scale-98'
                  )
                }
              >
                <BarChart3 className="w-5 h-5 text-brand" />
                <span>Benchmarks and cost</span>
              </NavLink>

              <NavLink
                to="/app/models"
                data-testid="more-sheet-models"
                onClick={() => setShowMoreSheet(false)}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 px-3.5 py-3 rounded-xl min-h-[44px] transition-colors text-sm font-semibold',
                    isActive
                      ? 'bg-brand/15 text-brand'
                      : 'text-foreground hover:bg-surface-subtle active:scale-98'
                  )
                }
              >
                <SlidersHorizontal className="w-5 h-5 text-brand" />
                <span>Models and rules</span>
              </NavLink>

              <NavLink
                to="/app/settings"
                data-testid="more-sheet-settings"
                onClick={() => setShowMoreSheet(false)}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 px-3.5 py-3 rounded-xl min-h-[44px] transition-colors text-sm font-semibold',
                    isActive
                      ? 'bg-brand/15 text-brand'
                      : 'text-foreground hover:bg-surface-subtle active:scale-98'
                  )
                }
              >
                <Settings className="w-5 h-5 text-brand" />
                <span>Settings & Providers</span>
              </NavLink>
            </div>

            <div className="h-px bg-border my-2" />

            {/* Theme Toggle in sheet */}
            <div className="flex items-center justify-between p-2 rounded-xl bg-surface-subtle border border-border">
              <span className="text-xs font-semibold text-foreground px-2">Appearance</span>
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  data-testid="more-sheet-theme-toggle"
                  onClick={toggleTheme}
                  className={cn(
                    'min-h-[44px] px-3.5 py-2 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer',
                    'bg-foreground text-background shadow-xs hover:opacity-90'
                  )}
                >
                  {theme === 'dark' ? (
                    <>
                      <Moon className="w-4 h-4 text-brand" />
                      <span>Dark mode</span>
                    </>
                  ) : (
                    <>
                      <Sun className="w-4 h-4 text-amber-500" />
                      <span>Light mode</span>
                    </>
                  )}
                </button>
              </div>
            </div>

            {/* Sign Out Button in sheet */}
            <button
              type="button"
              data-testid="sheet-sign-out"
              onClick={handleSignOut}
              className="w-full min-h-[44px] flex items-center justify-center gap-2 px-4 py-3 rounded-xl border border-border text-status-red hover:bg-status-red/10 active:scale-98 transition-colors text-sm font-semibold cursor-pointer"
            >
              <LogOut className="w-4 h-4" />
              <span>Sign out</span>
            </button>
          </div>
        </div>
      )}
    </>
  );
};
