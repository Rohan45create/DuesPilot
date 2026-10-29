import React, { useState, useEffect, useRef, useMemo } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Search,
  Bell,
  LogOut,
  ChevronDown,
  Settings,
  User,
  X,
  FileText,
  Folder,
  CheckCircle2,
  AlertTriangle,
  Info,
  Sparkles,
} from 'lucide-react';
import { cn } from '../../utils/formatters';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { Button } from '../ui/Button';

interface TopBarProps {
  currentTab?: string;
  onTabChange?: (tab: string) => void;
  replayMode?: boolean;
}

interface NotificationItem {
  id: string;
  title: string;
  description: string;
  timestamp: string;
  read: boolean;
  type: 'success' | 'warning' | 'info';
  link?: string;
}

const INITIAL_NOTIFICATIONS: NotificationItem[] = [
  {
    id: 'notif-1',
    title: 'Pre-send checklist verified',
    description: 'Operator approved DP-0142 commercial reconciliation notice.',
    timestamp: '10 min ago',
    read: false,
    type: 'success',
    link: '/app/cases/DP-0142',
  },
  {
    id: 'notif-2',
    title: 'Statutory sending window open',
    description: 'Current IST time is inside 09:00–19:00 business hours.',
    timestamp: '35 min ago',
    read: false,
    type: 'info',
    link: '/app/settings',
  },
  {
    id: 'notif-3',
    title: 'Conflict detected in DP-0139',
    description: 'Invoice and PO matched; delivery challan missing.',
    timestamp: '2 hours ago',
    read: true,
    type: 'warning',
    link: '/app/cases/DP-0139',
  },
  {
    id: 'notif-4',
    title: 'Resend Statement PDF Box ready',
    description: 'Automated evidence statement rendered for DP-0142.',
    timestamp: 'Yesterday',
    read: true,
    type: 'info',
    link: '/app/cases/DP-0142',
  },
];

const SEARCH_ITEMS = [
  { title: 'DP-0142 · Kestrel Industrial Ltd', subtitle: 'Balance: ₹56,200 · Credit note not applied', link: '/app/cases/DP-0142', category: 'Case' },
  { title: 'DP-0139 · Harbor Tooling Co', subtitle: 'Balance: ₹1,40,000 · Acceptance missing', link: '/app/cases/DP-0139', category: 'Case' },
  { title: 'DP-0127 · Orion Castings Pvt Ltd', subtitle: 'Cannot determine balance · Allocation conflict', link: '/app/cases/DP-0127', category: 'Case' },
  { title: 'DP-0131 · Mistral Fabrication', subtitle: 'Balance: ₹0 · Fully reconciled closing note', link: '/app/cases/DP-0131', category: 'Case' },
  { title: 'Overview & Position', subtitle: 'Total invoiced, aging, and recent activity', link: '/app', category: 'Navigation' },
  { title: 'New Case Wizard', subtitle: 'Upload evidence and run MSME recovery analysis', link: '/app/cases/new', category: 'Navigation' },
  { title: 'Cases Directory', subtitle: 'List of all 14 synthetic recovery cases', link: '/app/cases', category: 'Navigation' },
  { title: 'Review Queue', subtitle: 'Human-in-the-loop action and draft approvals', link: '/app/review', category: 'Navigation' },
  { title: 'Benchmarks & Cost', subtitle: 'Accuracy, latency, and Nemotron cost models', link: '/app/benchmarks', category: 'Navigation' },
  { title: 'Models & Rules', subtitle: 'Routing rules, Section 16 interest, and caps', link: '/app/models', category: 'Navigation' },
  { title: 'Settings & Providers', subtitle: 'Business profile, sandbox mode, Resend & WhatsApp', link: '/app/settings', category: 'Navigation' },
];

export const TopBar: React.FC<TopBarProps> = ({
  currentTab,
  onTabChange,
  replayMode = true,
}) => {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { user, isAnonymous, workspace, signOut } = useAuth();
  const { showToast } = useToast();

  const [showMenu, setShowMenu] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const [showSearchModal, setShowSearchModal] = useState(false);
  const [showModeModal, setShowModeModal] = useState(false);
  const [showProfileModal, setShowProfileModal] = useState(false);

  const [searchQuery, setSearchQuery] = useState('');
  const [notifications, setNotifications] = useState<NotificationItem[]>(INITIAL_NOTIFICATIONS);

  const searchInputRef = useRef<HTMLInputElement>(null);

  // Global Ctrl+K / Cmd+K listener for search palette
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setShowSearchModal((prev) => !prev);
      } else if (e.key === 'Escape') {
        setShowSearchModal(false);
        setShowNotifications(false);
        setShowMenu(false);
        setShowModeModal(false);
        setShowProfileModal(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  useEffect(() => {
    if (showSearchModal) {
      setTimeout(() => searchInputRef.current?.focus(), 50);
    }
  }, [showSearchModal]);

  // Determine sub-navigation tabs based on active pathname
  const tabs = useMemo(() => {
    if (location.pathname === '/app' || location.pathname === '/') {
      return ['Overview', 'Aging', 'Conflicts', 'Activity'];
    }
    if (location.pathname === '/app/cases/new' || location.pathname === '/cases/new') {
      return ['1 Upload', '2 Details', '3 Options', '4 Review'];
    }
    if (location.pathname === '/app/cases' || location.pathname === '/cases') {
      return ['All cases', 'Needs review', 'Reconciled', 'Archived'];
    }
    if (location.pathname.startsWith('/app/cases/') || location.pathname.startsWith('/cases/')) {
      return ['Summary', 'Documents', 'Ledger', 'Conflicts', 'Gates', 'Interest', 'Actions', 'Communications', 'Audit'];
    }
    if (location.pathname === '/app/review' || location.pathname === '/review') {
      return ['Pending', 'Approved', 'Rejected', 'Audit log'];
    }
    if (location.pathname === '/app/benchmarks' || location.pathname === '/benchmarks') {
      return ['Metrics', 'Experiments', 'Cost', 'Dataset'];
    }
    if (location.pathname === '/app/models' || location.pathname === '/models') {
      return ['Routing', 'Statutory settings', 'Limits', 'Modes'];
    }
    if (location.pathname === '/app/settings' || location.pathname === '/settings') {
      return ['Profile', 'Defaults', 'Sandbox', 'Providers'];
    }
    return [];
  }, [location.pathname]);

  const defaultActive = useMemo(() => {
    if (location.pathname === '/app' || location.pathname === '/') {
      return searchParams.get('tab') || 'Overview';
    }
    if (location.pathname === '/app/cases/new' || location.pathname === '/cases/new') {
      return searchParams.get('step') ? `${searchParams.get('step')} ` : '1 Upload';
    }
    if (location.pathname === '/app/cases' || location.pathname === '/cases') {
      const statusParam = searchParams.get('status');
      if (statusParam === 'NEEDS_REVIEW') return 'Needs review';
      if (statusParam === 'RECONCILED') return 'Reconciled';
      if (statusParam === 'ARCHIVED') return 'Archived';
      return 'All cases';
    }
    if (location.pathname.startsWith('/app/cases/') || location.pathname.startsWith('/cases/')) {
      return searchParams.get('tab') || 'Summary';
    }
    if (location.pathname === '/app/review' || location.pathname === '/review') {
      return searchParams.get('tab') || 'Pending';
    }
    if (location.pathname === '/app/benchmarks' || location.pathname === '/benchmarks') {
      return searchParams.get('tab') || 'Metrics';
    }
    if (location.pathname === '/app/models' || location.pathname === '/models') {
      return searchParams.get('tab') || 'Routing';
    }
    if (location.pathname === '/app/settings' || location.pathname === '/settings') {
      return searchParams.get('tab') || 'Profile';
    }
    return '';
  }, [location.pathname, searchParams]);

  const activeTab = currentTab ?? defaultActive;

  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const navRef = useRef<HTMLElement>(null);
  const [indicatorStyle, setIndicatorStyle] = useState<{ left: number; width: number } | null>(null);

  useEffect(() => {
    const activeIdx = tabs.findIndex((t) => t.trim() === activeTab.trim());
    if (activeIdx !== -1 && tabRefs.current[activeIdx] && navRef.current) {
      const tabEl = tabRefs.current[activeIdx]!;
      const navEl = navRef.current;
      const tabRect = tabEl.getBoundingClientRect();
      const navRect = navEl.getBoundingClientRect();
      const nextLeft = tabRect.left - navRect.left;
      const nextWidth = tabRect.width;

      setIndicatorStyle((prev) => {
        if (prev && Math.abs(prev.left - nextLeft) < 1 && Math.abs(prev.width - nextWidth) < 1) {
          return prev;
        }
        return { left: nextLeft, width: nextWidth };
      });
    }
  }, [activeTab, tabs]);

  const handleTabKeyDown = (e: React.KeyboardEvent, index: number) => {
    let nextIndex: number | null = null;
    if (e.key === 'ArrowRight') {
      e.preventDefault();
      nextIndex = (index + 1) % tabs.length;
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault();
      nextIndex = (index - 1 + tabs.length) % tabs.length;
    } else if (e.key === 'Home') {
      e.preventDefault();
      nextIndex = 0;
    } else if (e.key === 'End') {
      e.preventDefault();
      nextIndex = tabs.length - 1;
    }

    if (nextIndex !== null) {
      tabRefs.current[nextIndex]?.focus();
      onTabChange?.(tabs[nextIndex]);
    }
  };

  // Determine initials and display name
  const displayName = isAnonymous
    ? 'Demo user'
    : (workspace?.businessName || workspace?.name || user?.email?.split('@')[0] || 'User');

  const initials = isAnonymous
    ? 'DU'
    : (user?.email ? user.email.slice(0, 2).toUpperCase() : 'ME');

  const unreadCount = notifications.filter((n) => !n.read).length;

  const handleMarkAllRead = () => {
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    showToast('All notifications marked as read.', 'success');
  };

  const handleSignOut = () => {
    setShowMenu(false);
    signOut();
    showToast('Signed out successfully.', 'info');
    navigate('/signin');
  };

  const filteredSearch = SEARCH_ITEMS.filter(
    (item) =>
      item.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.subtitle.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.category.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <>
      <header className="flex items-center justify-between w-full h-14 mb-2 lg:mb-6 shrink-0 relative z-30">
        {/* Brand logo */}
        <Link
          to="/app"
          data-testid="topbar-brand-link"
          className="flex items-center gap-2.5 group transition-opacity hover:opacity-90 min-h-[44px] py-1"
        >
          <div className="w-8 h-8 rounded-lg bg-brand flex items-center justify-center text-black font-extrabold text-base tracking-tighter shadow-xs shrink-0">
            D
          </div>
          <span className="font-heading font-bold text-lg text-foreground tracking-tight">
            DuesPilot
          </span>
        </Link>

        {/* Center dynamic pill sub-navigation (Desktop only >= 1024px) */}
        {tabs.length > 0 && (
          <nav
            ref={navRef}
            role="tablist"
            aria-label="Sub navigation"
            data-testid="topbar-subnav"
            className="hidden lg:flex relative items-center p-1 rounded-full bg-surface border border-border"
          >
            {/* Animated active sliding indicator */}
            {indicatorStyle && (
              <div
                aria-hidden="true"
                data-testid="pill-active-indicator"
                className="absolute top-1 bottom-1 bg-foreground rounded-full transition-all duration-200 ease-out pointer-events-none shadow-xs"
                style={{
                  left: `${indicatorStyle.left}px`,
                  width: `${indicatorStyle.width}px`,
                }}
              />
            )}
            {tabs.map((tab, idx) => {
              const isActive = tab.trim() === activeTab.trim();
              const slug = tab.toLowerCase().replace(/[^a-z0-9]+/g, '-');
              return (
                <button
                  key={tab}
                  ref={(el) => {
                    tabRefs.current[idx] = el;
                  }}
                  role="tab"
                  type="button"
                  tabIndex={isActive ? 0 : -1}
                  aria-selected={isActive}
                  data-testid={`topbar-tab-${slug}`}
                  aria-current={isActive ? 'page' : undefined}
                  onClick={() => onTabChange?.(tab)}
                  onKeyDown={(e) => handleTabKeyDown(e, idx)}
                  className={cn(
                    'relative z-10 px-4 py-1 text-xs font-medium rounded-full transition-colors duration-150 cursor-pointer',
                    isActive
                      ? 'text-background font-semibold'
                      : 'text-foreground-muted hover:text-foreground'
                  )}
                >
                  {tab}
                </button>
              );
            })}
          </nav>
        )}

        {/* Right controls: Search, Notifications, Mode tag, User chip */}
        <div className="flex items-center gap-1.5 sm:gap-2.5">
          {/* Search Trigger (Desktop & Tablet) */}
          <button
            type="button"
            data-testid="global-search-btn"
            aria-label="Search cases and navigation"
            title="Search (Ctrl+K)"
            onClick={() => setShowSearchModal(true)}
            className="hidden sm:flex w-9 h-9 min-h-[44px] min-w-[44px] sm:min-h-0 sm:min-w-0 rounded-full bg-surface border border-border items-center justify-center text-foreground-muted hover:text-foreground hover:bg-surface-subtle transition-colors cursor-pointer"
          >
            <Search className="w-4 h-4" />
          </button>

          {/* Notifications Trigger (Always visible, min 44px on touch) */}
          <div className="relative">
            <button
              type="button"
              data-testid="global-notifications-btn"
              aria-label="Notifications"
              title="Notifications"
              onClick={() => {
                setShowNotifications(!showNotifications);
                setShowMenu(false);
              }}
              className="w-10 h-10 sm:w-9 sm:h-9 min-h-[44px] min-w-[44px] sm:min-h-0 sm:min-w-0 rounded-full bg-surface border border-border flex items-center justify-center text-foreground-muted hover:text-foreground hover:bg-surface-subtle transition-colors cursor-pointer relative"
            >
              <Bell className="w-4 h-4" />
              {unreadCount > 0 && (
                <span
                  data-testid="notifications-unread-badge"
                  className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-brand animate-pulse"
                />
              )}
            </button>

            {/* Notifications Popover */}
            {showNotifications && (
              <div
                data-testid="notifications-popover"
                className="fixed inset-x-2 sm:inset-x-auto sm:right-0 top-16 sm:top-11 w-auto sm:w-96 max-w-[calc(100vw-1rem)] rounded-2xl bg-surface border border-border shadow-2xl p-4 z-50 text-xs animate-in fade-in zoom-in-95 duration-150"
              >
                <div className="flex items-center justify-between pb-3 border-b border-border mb-3">
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-foreground font-heading">Notifications</span>
                    {unreadCount > 0 && (
                      <span className="text-[10px] bg-brand/10 text-brand px-2 py-0.5 rounded-full font-semibold">
                        {unreadCount} new
                      </span>
                    )}
                  </div>
                  {unreadCount > 0 && (
                    <button
                      type="button"
                      data-testid="mark-all-read-btn"
                      onClick={handleMarkAllRead}
                      className="text-[11px] text-brand hover:underline cursor-pointer font-medium p-1"
                    >
                      Mark all read
                    </button>
                  )}
                </div>

                <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
                  {notifications.map((n) => (
                    <div
                      key={n.id}
                      data-testid={`notification-item-${n.id}`}
                      onClick={() => {
                        setNotifications((prev) =>
                          prev.map((item) => (item.id === n.id ? { ...item, read: true } : item))
                        );
                        if (n.link) {
                          setShowNotifications(false);
                          navigate(n.link);
                        }
                      }}
                      className={cn(
                        'p-2.5 rounded-xl border transition-colors cursor-pointer flex items-start gap-2.5 min-h-[44px]',
                        n.read
                          ? 'bg-surface-subtle/50 border-border/50 text-foreground-muted'
                          : 'bg-surface border-brand/30 text-foreground shadow-xs'
                      )}
                    >
                      <div className="mt-0.5 shrink-0">
                        {n.type === 'success' && <CheckCircle2 className="w-3.5 h-3.5 text-brand" />}
                        {n.type === 'warning' && <AlertTriangle className="w-3.5 h-3.5 text-amber-500" />}
                        {n.type === 'info' && <Info className="w-3.5 h-3.5 text-brand" />}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between gap-1">
                          <span className="font-semibold text-foreground truncate">{n.title}</span>
                          <span className="text-[10px] text-foreground-muted shrink-0">{n.timestamp}</span>
                        </div>
                        <p className="text-[11px] text-foreground-muted mt-0.5 line-clamp-2">{n.description}</p>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="pt-3 border-t border-border mt-3 text-center">
                  <Link
                    to="/app/settings"
                    data-testid="configure-notifications-link"
                    onClick={() => setShowNotifications(false)}
                    className="text-[11px] text-foreground-muted hover:text-foreground font-medium inline-block py-1"
                  >
                    Configure notification channels & sandbox &rarr;
                  </Link>
                </div>
              </div>
            )}
          </div>

          {/* Mode tag button (Desktop only) */}
          <button
            type="button"
            data-testid="mode-tag-btn"
            onClick={() => setShowModeModal(true)}
            aria-label="Execution mode status"
            className={cn(
              'hidden lg:flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium border transition-colors cursor-pointer',
              replayMode
                ? 'bg-surface border-border text-foreground hover:bg-surface-subtle'
                : 'bg-brand/10 border-brand/30 text-brand-text-light dark:text-brand hover:bg-brand/20'
            )}
          >
            <span className="w-2 h-2 rounded-full bg-brand animate-pulse" />
            <span>{replayMode ? 'Replay mode' : 'Live · cost capped'}</span>
          </button>

          {/* User chip with dropdown menu (min 44px touch target) */}
          <div className="relative">
            <button
              type="button"
              data-testid="user-menu-btn"
              onClick={() => {
                setShowMenu(!showMenu);
                setShowNotifications(false);
              }}
              aria-label="User profile menu"
              className="flex items-center gap-2 pl-1 pr-2 sm:pr-3 py-1 rounded-full bg-surface border border-border text-xs font-medium hover:bg-surface-subtle transition-colors cursor-pointer min-h-[44px]"
            >
              <div className="w-8 h-8 sm:w-7 sm:h-7 rounded-full bg-brand flex items-center justify-center text-black font-bold text-xs shrink-0">
                {initials}
              </div>
              <span className="text-foreground max-w-[80px] sm:max-w-[120px] truncate hidden xs:inline">{displayName}</span>
              <ChevronDown className="w-3 h-3 text-foreground-muted" />
            </button>

            {showMenu && (
              <div
                data-testid="user-menu-dropdown"
                className="fixed inset-x-2 sm:inset-x-auto sm:right-0 top-16 sm:top-11 w-auto sm:w-56 max-w-[calc(100vw-1rem)] rounded-2xl bg-surface border border-border shadow-xl p-1.5 z-50 text-xs animate-in fade-in zoom-in-95 duration-150"
              >
                <div className="px-3 py-2 border-b border-border mb-1">
                  <div className="font-semibold text-foreground truncate">{displayName}</div>
                  <div className="text-[11px] text-foreground-muted truncate">{user?.email || 'Demo mode (Read-only)'}</div>
                </div>

                <button
                  type="button"
                  data-testid="user-menu-profile"
                  onClick={() => {
                    setShowMenu(false);
                    setShowProfileModal(true);
                  }}
                  className="w-full flex items-center gap-2 px-3 py-2.5 rounded-xl text-foreground hover:bg-surface-subtle transition-colors text-left cursor-pointer min-h-[44px]"
                >
                  <User className="w-4 h-4 text-foreground-muted" />
                  <span>Profile & Enterprise</span>
                </button>

                <Link
                  to="/app/settings"
                  data-testid="user-menu-settings"
                  onClick={() => setShowMenu(false)}
                  className="w-full flex items-center gap-2 px-3 py-2.5 rounded-xl text-foreground hover:bg-surface-subtle transition-colors min-h-[44px]"
                >
                  <Settings className="w-4 h-4 text-foreground-muted" />
                  <span>Settings & Guardrails</span>
                </Link>

                <button
                  type="button"
                  data-testid="user-menu-signout"
                  onClick={handleSignOut}
                  className="w-full flex items-center gap-2 px-3 py-2.5 rounded-xl text-status-red hover:bg-status-red/10 transition-colors cursor-pointer text-left min-h-[44px]"
                >
                  <LogOut className="w-4 h-4" />
                  <span>Sign out</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Mobile & Tablet Pill Navigation (Horizontally scrollable chip row with scroll-snap and edge fade) */}
      {tabs.length > 0 && (
        <div className="w-full relative pb-4 lg:hidden">
          {/* Left Edge Fade */}
          <div className="pointer-events-none absolute left-0 top-0 bottom-4 w-6 bg-gradient-to-r from-shell to-transparent z-10" />
          <nav
            role="tablist"
            aria-label="Sub navigation"
            data-testid="topbar-subnav-mobile"
            className="flex items-center gap-2 overflow-x-auto scrollbar-none snap-x snap-mandatory px-1 py-1"
          >
            {tabs.map((tab) => {
              const isActive = tab.trim() === activeTab.trim();
              const slug = tab.toLowerCase().replace(/[^a-z0-9]+/g, '-');
              return (
                <button
                  key={tab}
                  role="tab"
                  type="button"
                  tabIndex={isActive ? 0 : -1}
                  aria-selected={isActive}
                  data-testid={`topbar-mobile-tab-${slug}`}
                  aria-current={isActive ? 'page' : undefined}
                  onClick={() => onTabChange?.(tab)}
                  className={cn(
                    'snap-start shrink-0 min-h-[44px] px-4 py-2 rounded-full text-xs font-semibold transition-all cursor-pointer whitespace-nowrap active:scale-95',
                    isActive
                      ? 'bg-foreground text-background shadow-xs font-bold'
                      : 'bg-surface border border-border text-foreground-muted hover:text-foreground'
                  )}
                >
                  {tab}
                </button>
              );
            })}
          </nav>
          {/* Right Edge Fade */}
          <div className="pointer-events-none absolute right-0 top-0 bottom-4 w-6 bg-gradient-to-l from-shell to-transparent z-10" />
        </div>
      )}

      {/* Global Search Dialog Modal (Responsive Bottom Sheet on phone, centered modal on desktop) */}
      {showSearchModal && (
        <div
          data-testid="search-dialog"
          className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-end sm:items-start justify-center pt-0 sm:pt-20 p-0 sm:px-4 animate-in fade-in duration-150"
          onClick={() => setShowSearchModal(false)}
        >
          <div
            className="w-full sm:max-w-xl rounded-t-3xl sm:rounded-2xl bg-surface border border-border shadow-2xl overflow-hidden max-h-[85dvh] flex flex-col pb-[max(1rem,env(safe-area-inset-bottom,0px))] sm:pb-0 animate-in slide-in-from-bottom sm:slide-in-from-top-4 duration-200"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="p-3.5 border-b border-border flex items-center gap-3">
              <Search className="w-4 h-4 text-brand shrink-0" />
              <input
                ref={searchInputRef}
                type="text"
                data-testid="search-palette-input"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search cases, buyers, invoices or pages... (ESC to close)"
                className="w-full bg-transparent text-sm text-foreground placeholder:text-foreground-muted focus:outline-none"
              />
              <button
                type="button"
                data-testid="close-search-dialog"
                onClick={() => setShowSearchModal(false)}
                className="p-1 rounded-lg hover:bg-surface-subtle text-foreground-muted cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="max-h-96 overflow-y-auto p-2 space-y-1">
              {filteredSearch.length === 0 ? (
                <div className="p-8 text-center text-xs text-foreground-muted">
                  No matching cases, invoices or navigation items found.
                </div>
              ) : (
                filteredSearch.map((item, idx) => (
                  <button
                    key={idx}
                    type="button"
                    data-testid={`search-item-${item.title.split(' ')[0]}`}
                    onClick={() => {
                      setShowSearchModal(false);
                      navigate(item.link);
                    }}
                    className="w-full text-left p-3 rounded-xl hover:bg-surface-subtle transition-colors flex items-center justify-between gap-3 group cursor-pointer"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="p-2 rounded-lg bg-surface border border-border text-foreground-muted group-hover:text-brand group-hover:border-brand/40">
                        {item.category === 'Case' ? <FileText className="w-4 h-4" /> : <Folder className="w-4 h-4" />}
                      </div>
                      <div className="min-w-0">
                        <div className="text-xs font-bold text-foreground group-hover:text-brand transition-colors truncate">
                          {item.title}
                        </div>
                        <div className="text-[11px] text-foreground-muted truncate">{item.subtitle}</div>
                      </div>
                    </div>
                    <span className="text-[10px] uppercase font-mono px-2 py-0.5 rounded-full bg-surface border border-border text-foreground-muted shrink-0">
                      {item.category}
                    </span>
                  </button>
                ))
              )}
            </div>

            <div className="p-2.5 bg-surface-subtle border-t border-border flex items-center justify-between text-[11px] text-foreground-muted px-4">
              <span>Tip: Press ESC to exit or click any item to jump</span>
              <span className="font-mono">14 cases indexed</span>
            </div>
          </div>
        </div>
      )}

      {/* Mode Tag Explanation Modal (Bottom sheet on phone) */}
      {showModeModal && (
        <div
          data-testid="mode-modal"
          className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-end sm:items-center justify-center p-0 sm:p-4 animate-in fade-in duration-150"
          onClick={() => setShowModeModal(false)}
        >
          <div
            className="w-full sm:max-w-md rounded-t-3xl sm:rounded-2xl bg-surface border border-border p-6 shadow-2xl space-y-4 max-h-[85dvh] overflow-y-auto pb-[max(1.5rem,env(safe-area-inset-bottom,0px))] sm:pb-6 animate-in slide-in-from-bottom sm:slide-in-from-top-4 duration-200"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-brand" />
                <h3 className="text-base font-bold font-heading text-foreground">
                  {replayMode ? 'Replay Mode Active' : 'Live Mode (Cost-Capped)'}
                </h3>
              </div>
              <button
                type="button"
                data-testid="close-mode-modal"
                onClick={() => setShowModeModal(false)}
                className="min-h-[44px] min-w-[44px] flex items-center justify-center text-foreground-muted hover:text-foreground cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <p className="text-xs text-foreground-muted leading-relaxed">
              DuesPilot runs deterministic MSME statutory reconciliation pipelines. In Replay mode, runs use verified traces and synthetic benchmark data without external provider costs.
            </p>

            <div className="p-3 rounded-xl bg-surface-subtle border border-border text-xs space-y-2">
              <div className="flex justify-between">
                <span className="text-foreground-muted">Daily Spend Limit:</span>
                <span className="font-bold text-foreground font-mono">$5.00</span>
              </div>
              <div className="flex justify-between">
                <span className="text-foreground-muted">Per-Case Max Budget:</span>
                <span className="font-bold text-foreground font-mono">$0.25</span>
              </div>
              <div className="flex justify-between">
                <span className="text-foreground-muted">Model Tiering:</span>
                <span className="font-bold text-brand">Nemotron Nano &rarr; Super &rarr; Ultra</span>
              </div>
            </div>

            <div className="flex flex-col sm:flex-row justify-end gap-2 pt-2">
              <Button
                variant="outline"
                size="sm"
                data-testid="dismiss-mode-modal-btn"
                onClick={() => setShowModeModal(false)}
                className="min-h-[44px]"
              >
                Dismiss
              </Button>
              <Button
                size="sm"
                data-testid="configure-models-btn"
                onClick={() => {
                  setShowModeModal(false);
                  navigate('/app/models');
                }}
                className="min-h-[44px]"
              >
                Configure Models & Limits
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* User Profile Dialog Modal (Bottom sheet on phone) */}
      {showProfileModal && (
        <div
          data-testid="profile-modal"
          className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-end sm:items-center justify-center p-0 sm:p-4 animate-in fade-in duration-150"
          onClick={() => setShowProfileModal(false)}
        >
          <div
            className="w-full sm:max-w-md rounded-t-3xl sm:rounded-2xl bg-surface border border-border p-6 shadow-2xl space-y-5 max-h-[85dvh] overflow-y-auto pb-[max(1.5rem,env(safe-area-inset-bottom,0px))] sm:pb-6 animate-in slide-in-from-bottom sm:slide-in-from-top-4 duration-200"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <User className="w-4 h-4 text-brand" />
                <h3 className="text-base font-bold font-heading text-foreground">
                  Enterprise Profile
                </h3>
              </div>
              <button
                type="button"
                data-testid="close-profile-modal"
                onClick={() => setShowProfileModal(false)}
                className="min-h-[44px] min-w-[44px] flex items-center justify-center text-foreground-muted hover:text-foreground cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div>
                <label className="text-[11px] font-semibold text-foreground-muted block mb-1">
                  Business / Supplier Name
                </label>
                <div className="p-2.5 rounded-xl bg-surface-subtle border border-border text-foreground font-medium">
                  {workspace?.businessName || 'Basalt Heavy Engineering Pvt Ltd'}
                </div>
              </div>

              <div>
                <label className="text-[11px] font-semibold text-foreground-muted block mb-1">
                  Registered Account Email
                </label>
                <div className="p-2.5 rounded-xl bg-surface-subtle border border-border text-foreground">
                  {user?.email || 'owner@duespilot.example.com (Demo mode)'}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-[11px] font-semibold text-foreground-muted block mb-1">
                    MSME Classification
                  </label>
                  <div className="p-2.5 rounded-xl bg-surface-subtle border border-border text-foreground font-semibold">
                    Small Enterprise
                  </div>
                </div>
                <div>
                  <label className="text-[11px] font-semibold text-foreground-muted block mb-1">
                    Workspace Role
                  </label>
                  <div className="p-2.5 rounded-xl bg-surface-subtle border border-border text-brand font-semibold">
                    Owner
                  </div>
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-border">
              <Button
                variant="outline"
                size="sm"
                data-testid="close-profile-btn"
                onClick={() => setShowProfileModal(false)}
              >
                Close
              </Button>
              <Button
                size="sm"
                data-testid="edit-settings-profile-btn"
                onClick={() => {
                  setShowProfileModal(false);
                  navigate('/app/settings');
                }}
              >
                Edit in Settings
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
