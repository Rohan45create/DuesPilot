import React, { useState, useEffect } from 'react';
import { Outlet, useLocation, useSearchParams } from 'react-router-dom';
import { LeftRail } from './LeftRail';
import { TopBar } from './TopBar';
import { BottomTabBar } from './BottomTabBar';
import { DemoModeBanner } from './DemoModeBanner';

export const AppShell: React.FC = () => {
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const urlTab = searchParams.get('tab');
  const [activeTab, setActiveTabState] = useState<string | undefined>(urlTab ?? undefined);

  useEffect(() => {
    setActiveTabState(urlTab ?? undefined);
  }, [location.pathname, urlTab]);

  const handleTabChange = (tab: string) => {
    setActiveTabState(tab);

    if (location.pathname === '/app/cases') {
      const nextParams = new URLSearchParams(searchParams);
      if (tab === 'All cases') nextParams.delete('status');
      else if (tab === 'Needs review') nextParams.set('status', 'NEEDS_REVIEW');
      else if (tab === 'Reconciled') nextParams.set('status', 'RECONCILED');
      else if (tab === 'Archived') nextParams.set('status', 'ARCHIVED');
      nextParams.set('page', '1');
      setSearchParams(nextParams, { replace: true });
    } else if (location.pathname === '/app/cases/new') {
      const stepMatch = tab.match(/^(\d+)/);
      const step = stepMatch ? stepMatch[1] : '1';
      const nextParams = new URLSearchParams(searchParams);
      nextParams.set('step', step);
      setSearchParams(nextParams, { replace: true });
    } else {
      const nextParams = new URLSearchParams(searchParams);
      nextParams.set('tab', tab);
      setSearchParams(nextParams, { replace: true });

      // In-page smooth scroll if section exists
      const targetId = `section-${tab.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
      const el = document.getElementById(targetId);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }
  };

  return (
    <div className="min-h-screen min-h-[100dvh] w-full max-w-[100vw] overflow-x-hidden bg-background p-2 sm:p-4 lg:p-7 pb-[calc(4.75rem+env(safe-area-inset-bottom,0px))] lg:pb-7 flex justify-center items-start text-foreground antialiased selection:bg-brand selection:text-black transition-colors duration-200">
      {/* Accessible skip link for keyboard users (NFR-6) */}
      <a
        href="#main-content"
        onClick={() => {
          document.getElementById('main-content')?.focus();
        }}
        className="sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-50 focus:px-4 focus:py-2 focus:bg-brand focus:text-black focus:font-bold focus:rounded-lg focus:shadow-xl focus:outline-hidden"
      >
        Skip to main content
      </a>

      {/* Outer rounded application shell matching the design layout */}
      <div className="w-full max-w-[1520px] min-h-[90vh] rounded-2xl lg:rounded-[28px] bg-shell border border-border p-3.5 sm:p-6 lg:p-8 flex flex-col shadow-2xl relative overflow-hidden">
        {/* Persistent Demo Mode Banner if anonymous */}
        <DemoModeBanner />

        {/* Top bar with branding, dynamic pills, search, notifications, mode, user chip */}
        <TopBar currentTab={activeTab} onTabChange={handleTabChange} />

        {/* Horizontal split: Left slim icon rail (desktop) + main scrollable/view content */}
        <div className="flex items-start gap-4 lg:gap-6 w-full flex-1 min-w-0">
          <LeftRail />
          <main id="main-content" tabIndex={-1} className="flex-1 min-w-0 flex flex-col focus:outline-hidden">
            <div key={location.pathname} className="route-fade-enter flex-1 min-w-0 flex flex-col">
              <Outlet context={{ activeTab, setActiveTab: handleTabChange }} />
            </div>
          </main>
        </div>
      </div>

      {/* Mobile & Tablet Fixed Bottom Tab Bar */}
      <BottomTabBar />
    </div>
  );
};
