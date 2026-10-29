import React from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  Sun,
  Moon,
  LayoutGrid,
  PlusCircle,
  Folder,
  FileText,
  CheckCircle2,
  BarChart3,
  SlidersHorizontal,
  Settings,
} from 'lucide-react';
import { useTheme } from '../../context/ThemeContext';
import { cn } from '../../utils/formatters';

interface NavItem {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  matchPrefix?: string;
}

const NAV_ITEMS: NavItem[] = [
  { to: '/app', label: 'Overview', icon: LayoutGrid },
  { to: '/app/cases/new', label: 'New case', icon: PlusCircle },
  { to: '/app/cases', label: 'Cases', icon: Folder },
  { to: '/app/cases/DP-0142', label: 'Case workspace', icon: FileText, matchPrefix: '/app/cases/' },
  { to: '/app/review', label: 'Review queue', icon: CheckCircle2 },
  { to: '/app/benchmarks', label: 'Benchmarks', icon: BarChart3 },
  { to: '/app/models', label: 'Models and rules', icon: SlidersHorizontal },
  { to: '/app/settings', label: 'Settings', icon: Settings },
];

export const LeftRail: React.FC = () => {
  const { theme, toggleTheme } = useTheme();
  const location = useLocation();

  const isCurrentActive = (item: NavItem) => {
    if (item.to === '/app') {
      return location.pathname === '/app';
    }
    if (item.to === '/app/cases/new') {
      return location.pathname === '/app/cases/new';
    }
    if (item.to === '/app/cases') {
      return location.pathname === '/app/cases';
    }
    if (item.matchPrefix) {
      return (
        location.pathname.startsWith(item.matchPrefix) &&
        location.pathname !== '/app/cases/new' &&
        location.pathname !== '/app/cases'
      );
    }
    return location.pathname.startsWith(item.to);
  };

  return (
    <aside
      aria-label="Sidebar navigation"
      data-testid="left-rail"
      className="hidden lg:flex flex-col items-center py-5 px-2.5 rounded-full bg-surface border border-border shrink-0 self-start w-13 shadow-xs space-y-6"
    >
      {/* Theme toggle (Sun / Moon) */}
      <div className="flex flex-col items-center space-y-2">
        <div className="relative group flex items-center">
          <button
            type="button"
            data-testid="theme-light-btn"
            onClick={() => theme !== 'light' && toggleTheme()}
            aria-label="Switch to light mode"
            className={cn(
              'p-1.5 rounded-full transition-colors cursor-pointer',
              theme === 'light'
                ? 'text-foreground bg-surface-subtle shadow-xs'
                : 'text-foreground-muted hover:text-foreground'
            )}
          >
            <Sun className="w-4 h-4" />
          </button>
          <span
            role="tooltip"
            className="absolute left-full ml-3 px-2.5 py-1 bg-surface border border-border text-foreground text-xs font-medium rounded-lg shadow-xl pointer-events-none opacity-0 translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 group-focus-within:opacity-100 group-focus-within:translate-x-0 transition-all duration-150 whitespace-nowrap z-50"
          >
            Light mode
          </span>
        </div>

        <div className="relative group flex items-center">
          <button
            type="button"
            data-testid="theme-dark-btn"
            onClick={() => theme !== 'dark' && toggleTheme()}
            aria-label="Switch to dark mode"
            className={cn(
              'p-1.5 rounded-full transition-colors cursor-pointer',
              theme === 'dark'
                ? 'text-foreground bg-surface-subtle shadow-xs'
                : 'text-foreground-muted hover:text-foreground'
            )}
          >
            <Moon className="w-4 h-4" />
          </button>
          <span
            role="tooltip"
            className="absolute left-full ml-3 px-2.5 py-1 bg-surface border border-border text-foreground text-xs font-medium rounded-lg shadow-xl pointer-events-none opacity-0 translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 group-focus-within:opacity-100 group-focus-within:translate-x-0 transition-all duration-150 whitespace-nowrap z-50"
          >
            Dark mode
          </span>
        </div>
      </div>

      <div className="w-5 h-px bg-border" />

      {/* Main navigation icons */}
      <nav aria-label="Sidebar navigation" className="flex flex-col items-center space-y-3.5">
        {NAV_ITEMS.map((item) => {
          const active = isCurrentActive(item);
          const Icon = item.icon;
          const slug = item.label.toLowerCase().replace(/[^a-z0-9]+/g, '-');

          return (
            <div key={item.label} className="relative group flex items-center">
              <NavLink
                to={item.to}
                data-testid={`nav-${slug}`}
                aria-label={item.label}
                className={cn(
                  'relative flex items-center justify-center rounded-full transition-all duration-150',
                  active
                    ? 'w-9 h-9 bg-brand text-black shadow-xs font-bold'
                    : 'w-9 h-9 text-foreground-muted hover:text-foreground hover:bg-surface-subtle'
                )}
              >
                <Icon className="w-4.5 h-4.5 transition-transform group-hover:scale-105" />
              </NavLink>
              <span
                role="tooltip"
                className="absolute left-full ml-3 px-2.5 py-1 bg-surface border border-border text-foreground text-xs font-medium rounded-lg shadow-xl pointer-events-none opacity-0 translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 group-focus-within:opacity-100 group-focus-within:translate-x-0 transition-all duration-150 whitespace-nowrap z-50"
              >
                {item.label}
              </span>
            </div>
          );
        })}
      </nav>
    </aside>
  );
};
