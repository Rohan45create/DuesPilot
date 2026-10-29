import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { MoneyText } from '../components/common/MoneyText';
import { StatusBadge } from '../components/common/StatusBadge';
import { ErrorState, EmptyState } from '../components/ui/StateViews';
import { CardSkeleton, TableSkeleton, ChartSkeleton } from '../components/ui/Skeleton';
import { api } from '../api/client';
import { Search, Filter, Plus, Check, Sparkles } from 'lucide-react';
import { cn } from '../utils/formatters';
import { useToast } from '../context/ToastContext';
import { SeedDemoDataDialog } from '../components/demo/SeedDemoDataDialog';

export const OverviewPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { showToast } = useToast();
  const queryClient = useQueryClient();

  const searchQuery = searchParams.get('search') || '';
  const eventFilter = searchParams.get('eventFilter') || 'ALL';
  const [showFilterDropdown, setShowFilterDropdown] = useState(false);
  const [hiddenSeries, setHiddenSeries] = useState<Record<string, boolean>>({});
  const [isSeedDialogOpen, setIsSeedDialogOpen] = useState(false);

  const toggleSeries = (key: string) => {
    setHiddenSeries((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const {
    data,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['overview'],
    queryFn: () => api.getOverview(),
  });

  const handleSearchChange = (val: string) => {
    const next = new URLSearchParams(searchParams);
    if (val) next.set('search', val);
    else next.delete('search');
    setSearchParams(next, { replace: true });
  };

  const handleFilterChange = (filter: string) => {
    const next = new URLSearchParams(searchParams);
    if (filter === 'ALL') next.delete('eventFilter');
    else next.set('eventFilter', filter);
    setSearchParams(next, { replace: true });
    setShowFilterDropdown(false);
  };

  if (isLoading) {
    return (
      <div className="space-y-6 w-full p-2" data-testid="overview-loading-state">
        <div className="flex justify-between items-center">
          <div className="h-8 bg-surface-muted rounded-xl w-1/4 animate-pulse" />
          <div className="h-9 bg-surface-muted rounded-xl w-28 animate-pulse" />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <CardSkeleton count={4} />
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
          <div className="lg:col-span-8">
            <ChartSkeleton height="h-72" />
          </div>
          <div className="lg:col-span-4">
            <CardSkeleton count={2} />
          </div>
        </div>
        <TableSkeleton rows={4} columns={5} />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <ErrorState
        title="Failed to load receivables overview"
        message={error instanceof Error ? error.message : 'Could not retrieve financial data.'}
        onRetry={() => refetch()}
      />
    );
  }

  if (data.activeCasesCount === 0) {
    return (
      <div className="space-y-6 w-full" data-testid="overview-empty-state">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold font-heading text-foreground tracking-tight">
              Receivables position
            </h1>
            <p className="text-xs sm:text-sm text-foreground-muted mt-1 max-w-2xl">
              Track, reconcile, and dispute MSME receivables with verified audit trails.
            </p>
          </div>
          <Button
            data-testid="overview-new-case-btn"
            onClick={() => navigate('/app/cases/new')}
            className="shrink-0"
          >
            <Plus className="w-4 h-4 mr-1 stroke-[3]" />
            New case
          </Button>
        </div>

        <Card className="p-10 text-center flex flex-col items-center justify-center max-w-xl mx-auto my-12">
          <div className="w-12 h-12 rounded-2xl bg-brand/10 border border-brand/20 flex items-center justify-center text-brand mb-4">
            <Sparkles className="w-6 h-6" />
          </div>
          <h2 className="text-lg font-bold font-heading text-foreground mb-2">
            No cases in your workspace yet
          </h2>
          <p className="text-xs text-foreground-muted max-w-md mb-6 leading-relaxed">
            Get started by creating your first receivables case or explore with realistic sample data to see DuesPilot in action.
          </p>
          <div className="flex flex-wrap items-center justify-center gap-3">
            <Button
              variant="primary"
              data-testid="overview-seed-demo-btn"
              onClick={() => setIsSeedDialogOpen(true)}
              className="text-xs font-medium"
            >
              <Sparkles className="w-3.5 h-3.5 mr-1.5" />
              Seed demo data
            </Button>
            <Button
              variant="outline"
              data-testid="overview-new-case-empty-btn"
              onClick={() => navigate('/app/cases/new')}
              className="text-xs"
            >
              <Plus className="w-3.5 h-3.5 mr-1.5" />
              New case
            </Button>
          </div>
        </Card>

        <SeedDemoDataDialog
          isOpen={isSeedDialogOpen}
          onClose={() => setIsSeedDialogOpen(false)}
          onSuccess={(count) => {
            showToast(`Added ${count} demo cases to your workspace.`, 'success');
            queryClient.invalidateQueries({ queryKey: ['overview'] });
            queryClient.invalidateQueries({ queryKey: ['cases'] });
            queryClient.invalidateQueries({ queryKey: ['demoDataPreview'] });
          }}
        />
      </div>
    );
  }

  const filteredActivity = data.recentActivity.filter((item) => {
    const matchesSearch =
      item.caseCode.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.event.toLowerCase().includes(searchQuery.toLowerCase());

    const matchesFilter =
      eventFilter === 'ALL' ||
      (eventFilter === 'INVOICE' && item.event.toLowerCase().includes('invoice')) ||
      (eventFilter === 'PAYMENT' && item.event.toLowerCase().includes('payment')) ||
      (eventFilter === 'CONFLICT' && (item.status === 'NEEDS_REVIEW' || item.event.toLowerCase().includes('conflict'))) ||
      (eventFilter === 'RECONCILED' && item.status === 'RECONCILED');

    return matchesSearch && matchesFilter;
  });

  return (
    <div className="space-y-6 w-full">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold font-heading text-foreground tracking-tight">
            Receivables position
          </h1>
          <p className="text-xs sm:text-sm text-foreground-muted mt-1 max-w-2xl">
            14 synthetic cases. Every figure comes from verified evidence; disputed and unverified amounts stay separate.
          </p>
        </div>
        <Button
          data-testid="overview-new-case-btn"
          onClick={() => navigate('/app/cases/new')}
          className="shrink-0"
        >
          <Plus className="w-4 h-4 mr-1 stroke-[3]" />
          New case
        </Button>
      </div>

      {/* Top Row: 4 KPI Cards */}
      <div id="section-overview" className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        {/* Card 1: Verified (Brand Green fill) */}
        <Card
          variant="brand"
          data-testid="kpi-verified"
          onClick={() => navigate('/app/cases?status=RECONCILED')}
          className="p-3.5 sm:p-5 flex flex-col justify-between min-h-[8rem] sm:min-h-[9rem] cursor-pointer"
        >
          <span className="text-[11px] sm:text-xs font-semibold text-black/80 uppercase tracking-wider">
            Verified
          </span>
          <div className="mt-2">
            <div className="text-xl sm:text-2xl lg:text-3xl font-extrabold font-heading text-black tracking-tight tabular-nums break-words">
              <MoneyText paise={data.verifiedPaise} showSymbol={true} animateCountUp={true} className="text-black font-extrabold text-xl sm:text-2xl lg:text-3xl" />
            </div>
            <div className="text-xs font-medium text-black/80 mt-1">
              {data.verifiedSharePercent}% of invoiced &rarr;
            </div>
          </div>
        </Card>

        {/* Card 2: Total invoiced */}
        <Card
          data-testid="kpi-total-invoiced"
          onClick={() => navigate('/app/cases')}
          className="p-3.5 sm:p-5 flex flex-col justify-between min-h-[8rem] sm:min-h-[9rem] cursor-pointer"
        >
          <span className="text-[11px] sm:text-xs font-semibold text-foreground-muted uppercase tracking-wider">
            Total invoiced
          </span>
          <div className="mt-2">
            <div className="text-xl sm:text-2xl lg:text-3xl font-extrabold font-heading text-foreground tracking-tight tabular-nums break-words">
              <MoneyText paise={data.totalInvoicedPaise} showSymbol={true} animateCountUp={true} className="font-extrabold text-xl sm:text-2xl lg:text-3xl" />
            </div>
            <div className="text-xs text-foreground-muted mt-1">
              {data.activeCasesCount} active cases &rarr;
            </div>
          </div>
        </Card>

        {/* Card 3: Disputed */}
        <Card
          data-testid="kpi-disputed"
          onClick={() => navigate('/app/cases?conflict=WITH_CONFLICTS')}
          className="p-3.5 sm:p-5 flex flex-col justify-between min-h-[8rem] sm:min-h-[9rem] cursor-pointer"
        >
          <span className="text-[11px] sm:text-xs font-semibold text-foreground-muted uppercase tracking-wider">
            Disputed
          </span>
          <div className="mt-2">
            <div className="text-xl sm:text-2xl lg:text-3xl font-extrabold font-heading text-amber-500 dark:text-amber-400 tracking-tight tabular-nums break-words">
              <MoneyText
                paise={data.disputedPaise}
                showSymbol={true}
                animateCountUp={true}
                className="text-amber-500 dark:text-amber-400 font-extrabold text-xl sm:text-2xl lg:text-3xl"
              />
            </div>
            <div className="text-xs text-foreground-muted mt-1">
              {data.openConflictsCount} open conflicts &rarr;
            </div>
          </div>
        </Card>

        {/* Card 4: Unverified */}
        <Card
          data-testid="kpi-unverified"
          onClick={() => navigate('/app/cases?status=MISSING_PROOF')}
          className="p-3.5 sm:p-5 flex flex-col justify-between min-h-[8rem] sm:min-h-[9rem] cursor-pointer"
        >
          <span className="text-[11px] sm:text-xs font-semibold text-foreground-muted uppercase tracking-wider">
            Unverified
          </span>
          <div className="mt-2">
            <div className="text-xl sm:text-2xl lg:text-3xl font-extrabold font-heading text-foreground tracking-tight tabular-nums break-words">
              <MoneyText paise={data.unverifiedPaise} showSymbol={true} animateCountUp={true} className="font-extrabold text-xl sm:text-2xl lg:text-3xl" />
            </div>
            <div className="text-xs text-foreground-muted mt-1">
              {data.unverifiedStatusText} &rarr;
            </div>
          </div>
        </Card>
      </div>

      {/* Middle Row: 3 Panels */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        {/* Panel 1: Position by status (5 cols) */}
        <Card className="lg:col-span-5 p-5 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-bold font-heading text-foreground">
                Position by status
              </h2>
              <span className="text-[10px] text-foreground-muted font-mono">Click legend to toggle</span>
            </div>

            {/* Stacked bar with accessible tooltips and series toggles */}
            <div
              className="w-full h-3 rounded-full overflow-hidden flex mt-3.5 bg-surface-subtle"
              role="progressbar"
              data-testid="position-breakdown-bar"
              aria-label="Receivables breakdown by status"
            >
              {!hiddenSeries.verified && (
                <div
                  tabIndex={0}
                  role="button"
                  aria-label={`Verified: ${data.statusBreakdown.verifiedPercent}%`}
                  style={{ width: `${data.statusBreakdown.verifiedPercent}%` }}
                  className="group relative h-full bg-brand cursor-pointer hover:opacity-90 transition-all duration-300 focus-visible:outline-hidden"
                  onClick={() => navigate('/app/cases?status=RECONCILED')}
                >
                  <span
                    role="tooltip"
                    className="absolute bottom-full mb-2 left-1/2 -translate-x-1/2 px-2 py-1 bg-surface border border-border text-foreground text-[11px] font-medium rounded-lg shadow-xl pointer-events-none opacity-0 group-hover:opacity-100 group-focus-visible:opacity-100 transition-opacity z-50 whitespace-nowrap"
                  >
                    Verified: {data.statusBreakdown.verifiedPercent}%
                  </span>
                </div>
              )}
              {!hiddenSeries.disputed && (
                <div
                  tabIndex={0}
                  role="button"
                  aria-label={`Disputed: ${data.statusBreakdown.disputedPercent}%`}
                  style={{ width: `${data.statusBreakdown.disputedPercent}%` }}
                  className="group relative h-full bg-amber-600 cursor-pointer hover:opacity-90 transition-all duration-300 focus-visible:outline-hidden"
                  onClick={() => navigate('/app/cases?conflict=WITH_CONFLICTS')}
                >
                  <span
                    role="tooltip"
                    className="absolute bottom-full mb-2 left-1/2 -translate-x-1/2 px-2 py-1 bg-surface border border-border text-foreground text-[11px] font-medium rounded-lg shadow-xl pointer-events-none opacity-0 group-hover:opacity-100 group-focus-visible:opacity-100 transition-opacity z-50 whitespace-nowrap"
                  >
                    Disputed: {data.statusBreakdown.disputedPercent}%
                  </span>
                </div>
              )}
              {!hiddenSeries.unverified && (
                <div
                  tabIndex={0}
                  role="button"
                  aria-label={`Unverified: ${data.statusBreakdown.unverifiedPercent}%`}
                  style={{ width: `${data.statusBreakdown.unverifiedPercent}%` }}
                  className="group relative h-full bg-foreground-subtle/40 cursor-pointer hover:opacity-90 transition-all duration-300 focus-visible:outline-hidden"
                  onClick={() => navigate('/app/cases?status=MISSING_PROOF')}
                >
                  <span
                    role="tooltip"
                    className="absolute bottom-full mb-2 left-1/2 -translate-x-1/2 px-2 py-1 bg-surface border border-border text-foreground text-[11px] font-medium rounded-lg shadow-xl pointer-events-none opacity-0 group-hover:opacity-100 group-focus-visible:opacity-100 transition-opacity z-50 whitespace-nowrap"
                  >
                    Unverified: {data.statusBreakdown.unverifiedPercent}%
                  </span>
                </div>
              )}
            </div>

            {/* Interactive Legend with toggles */}
            <div className="flex items-center gap-4 text-xs text-foreground-muted mt-3">
              <button
                type="button"
                data-testid="legend-verified"
                onClick={() => toggleSeries('verified')}
                className={cn(
                  'flex items-center gap-1.5 cursor-pointer transition-opacity',
                  hiddenSeries.verified ? 'opacity-30 line-through' : 'hover:text-foreground font-medium'
                )}
              >
                <span className="w-2 h-2 rounded-full bg-brand" />
                <span>Verified {data.statusBreakdown.verifiedPercent}%</span>
              </button>
              <button
                type="button"
                data-testid="legend-disputed"
                onClick={() => toggleSeries('disputed')}
                className={cn(
                  'flex items-center gap-1.5 cursor-pointer transition-opacity',
                  hiddenSeries.disputed ? 'opacity-30 line-through' : 'hover:text-foreground font-medium'
                )}
              >
                <span className="w-2 h-2 rounded-full bg-amber-600" />
                <span>Disputed {data.statusBreakdown.disputedPercent}%</span>
              </button>
              <button
                type="button"
                data-testid="legend-unverified"
                onClick={() => toggleSeries('unverified')}
                className={cn(
                  'flex items-center gap-1.5 cursor-pointer transition-opacity',
                  hiddenSeries.unverified ? 'opacity-30 line-through' : 'hover:text-foreground font-medium'
                )}
              >
                <span className="w-2 h-2 rounded-full bg-foreground-subtle/40" />
                <span>Unverified {data.statusBreakdown.unverifiedPercent}%</span>
              </button>
            </div>
          </div>

          <div className="mt-6">
            <span className="text-xs text-foreground-muted">Verified share, last 6 months</span>
            <div className="h-24 w-full mt-2 relative" aria-label="Verified share 6-month trend chart" role="img">
              <svg className="w-full h-full overflow-visible" viewBox="0 0 300 80" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="verifiedGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#76B900" stopOpacity="0.35" />
                    <stop offset="100%" stopColor="#76B900" stopOpacity="0.02" />
                  </linearGradient>
                </defs>
                <path
                  d="M 0,65 L 50,60 L 100,62 L 150,50 L 220,38 L 300,15 L 300,80 L 0,80 Z"
                  fill="url(#verifiedGrad)"
                />
                <path
                  d="M 0,65 L 50,60 L 100,62 L 150,50 L 220,38 L 300,15"
                  fill="none"
                  stroke="#76B900"
                  strokeWidth="2.5"
                />
              </svg>
            </div>
          </div>
        </Card>

        {/* Panel 2: Aging of open balances (4 cols) */}
        <Card id="section-aging" className="lg:col-span-4 p-5 flex flex-col justify-between">
          <div>
            <h2 className="text-sm font-bold font-heading text-foreground">
              Aging of open balances
            </h2>
          </div>

          {/* Aging Bar Chart with accessible ARIA label and focus/hover tooltips */}
          <div
            className="flex items-end justify-between gap-3 h-36 px-2 my-2"
            role="region"
            data-testid="aging-distribution"
            aria-label="Aging of open balances distribution"
          >
            {data.agingBreakdown.map((bar) => {
              let bg = 'bg-brand';
              if (bar.color === 'amber') bg = 'bg-amber-600';
              if (bar.color === 'dark-amber') bg = 'bg-amber-800 dark:bg-amber-700';

              return (
                <div
                  key={bar.range}
                  tabIndex={0}
                  role="button"
                  aria-label={`${bar.range} days bracket: ${bar.heightPercent}%`}
                  onClick={() => navigate('/app/cases')}
                  className="relative flex-1 flex flex-col items-center gap-2 h-full justify-end cursor-pointer group focus-visible:outline-hidden"
                >
                  <span
                    role="tooltip"
                    className="absolute bottom-full mb-1.5 left-1/2 -translate-x-1/2 px-2 py-1 bg-surface border border-border text-foreground text-[11px] font-medium rounded-lg shadow-xl pointer-events-none opacity-0 group-hover:opacity-100 group-focus-visible:opacity-100 transition-opacity z-50 whitespace-nowrap"
                  >
                    {bar.range} days: {bar.heightPercent}%
                  </span>
                  <div
                    style={{ height: `${bar.heightPercent}%` }}
                    className={`w-full rounded-md ${bg} transition-all duration-300 group-hover:opacity-80 group-focus-visible:ring-2 group-focus-visible:ring-brand`}
                  />
                  <span className="text-[11px] text-foreground-muted font-medium group-hover:text-foreground">
                    {bar.range}
                  </span>
                </div>
              );
            })}
          </div>

          <div className="text-xs text-foreground-muted">
            Days since invoice date · Click any bracket to explore cases
          </div>
        </Card>

        {/* Panel 3: Live-mode cost cap & Needs review (3 cols) */}
        <Card id="section-conflicts" className="lg:col-span-3 p-5 flex flex-col justify-between">
          <div>
            <h2 className="text-sm font-bold font-heading text-foreground">
              Live-mode cost cap
            </h2>
            <div className="mt-3">
              <span className="text-2xl font-bold font-heading text-foreground tracking-tight font-mono">
                ${data.liveCostCap.spentDollars.toFixed(2)}
              </span>
              <span className="text-xs text-foreground-muted ml-1.5">
                of ${data.liveCostCap.limitDollars.toFixed(2)} daily cap
              </span>
            </div>

            {/* Progress bar */}
            <div className="w-full h-1.5 rounded-full bg-surface-subtle overflow-hidden mt-3">
              <div
                style={{
                  width: `${(data.liveCostCap.spentDollars / data.liveCostCap.limitDollars) * 100}%`,
                }}
                className="bg-brand h-full"
              />
            </div>
          </div>

          <div className="mt-5 border-t border-border pt-4">
            <span className="text-xs font-semibold text-foreground-muted block mb-3">
              Needs review
            </span>
            <div className="space-y-2.5">
              {data.needsReviewCases.map((item) => (
                <div
                  key={item.code}
                  data-testid={`needs-review-${item.code}`}
                  onClick={() => navigate(`/app/cases/${item.code}`)}
                  className="flex items-center justify-between hover:bg-surface-subtle p-1.5 rounded-lg transition-colors cursor-pointer group"
                >
                  <span className="text-xs font-bold text-foreground group-hover:text-brand">
                    {item.code}
                  </span>
                  <StatusBadge
                    status={item.tagType === 'conflict' ? 'CONFLICT' : 'MISSING_PROOF'}
                    size="sm"
                  />
                </div>
              ))}
            </div>
          </div>
        </Card>
      </div>

      {/* Bottom Row: Recent Activity Table */}
      <Card id="section-activity" className="p-3.5 sm:p-5">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
          <h2 className="text-base font-bold font-heading text-foreground">
            Recent activity
          </h2>
          <div className="flex items-center gap-2 relative w-full sm:w-auto">
            <div className="relative flex-1 sm:flex-initial">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-foreground-muted" />
              <input
                type="text"
                data-testid="activity-search-input"
                value={searchQuery}
                onChange={(e) => handleSearchChange(e.target.value)}
                placeholder="Search cases & events"
                className="h-10 sm:h-9 pl-9 pr-3 text-xs rounded-xl bg-surface-subtle border border-border text-foreground placeholder:text-foreground-muted focus:outline-none focus:ring-1 focus:ring-brand w-full sm:w-60"
              />
            </div>

            <div className="relative shrink-0">
              <Button
                variant="secondary"
                size="sm"
                data-testid="activity-filter-btn"
                onClick={() => setShowFilterDropdown(!showFilterDropdown)}
                className="text-xs h-10 sm:h-9 min-h-[40px] sm:min-h-[36px] rounded-xl px-3"
              >
                <Filter className="w-3.5 h-3.5 mr-1.5" />
                {eventFilter === 'ALL' ? 'Filter' : eventFilter}
              </Button>

              {showFilterDropdown && (
                <div
                  data-testid="activity-filter-dropdown"
                  className="absolute right-0 mt-1 w-44 rounded-xl bg-surface border border-border shadow-xl z-20 p-1.5 text-xs space-y-1"
                >
                  {[
                    { id: 'ALL', label: 'All events' },
                    { id: 'INVOICE', label: 'Invoices' },
                    { id: 'PAYMENT', label: 'Payments' },
                    { id: 'CONFLICT', label: 'Conflicts' },
                    { id: 'RECONCILED', label: 'Reconciled' },
                  ].map((f) => (
                    <button
                      key={f.id}
                      type="button"
                      data-testid={`activity-filter-opt-${f.id.toLowerCase()}`}
                      onClick={() => handleFilterChange(f.id)}
                      className="w-full text-left px-2.5 py-2 rounded-lg hover:bg-surface-subtle flex items-center justify-between cursor-pointer min-h-[36px]"
                    >
                      <span>{f.label}</span>
                      {eventFilter === f.id && <Check className="w-3.5 h-3.5 text-brand" />}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>

        {filteredActivity.length === 0 ? (
          <EmptyState
            title="No matching activity found"
            description="Try modifying your search query or reset filter to see recent events."
            actionText="Reset search"
            onAction={() => {
              handleSearchChange('');
              handleFilterChange('ALL');
            }}
          />
        ) : (
          <>
            {/* Mobile Stacked Cards (<640px) */}
            <div className="sm:hidden space-y-2.5">
              {filteredActivity.map((row, idx) => (
                <div
                  key={`mobile-${row.caseCode}-${row.when}-${idx}`}
                  data-testid={`activity-row-${row.caseCode}`}
                  onClick={() => navigate(`/app/cases/${row.caseCode}`)}
                  className="p-3.5 rounded-xl border border-border bg-surface-subtle/40 active:bg-surface-subtle transition-colors cursor-pointer space-y-2 min-h-[44px]"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-bold text-xs text-foreground group-hover:text-brand">
                      {row.caseCode}
                    </span>
                    <StatusBadge status={row.status} label={row.statusLabel} size="sm" />
                  </div>
                  <div className="text-xs text-foreground font-medium">
                    {row.event}
                  </div>
                  <div className="flex items-center justify-between text-xs pt-1 border-t border-border/40">
                    <span className="font-semibold text-foreground">
                      <MoneyText paise={row.amountPaise} />
                    </span>
                    <span className="text-[11px] text-foreground-muted">
                      {row.when}
                    </span>
                  </div>
                </div>
              ))}
            </div>

            {/* Desktop / Tablet Table (>=640px) */}
            <div className="hidden sm:block overflow-x-auto rounded-xl border border-border/60">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-border bg-surface-subtle/30 text-foreground-muted font-medium">
                    <th className="py-2.5 px-3 sticky left-0 bg-surface z-10 shadow-[1px_0_0_0_var(--color-border)]">Case</th>
                    <th className="py-2.5 px-3">Event</th>
                    <th className="py-2.5 px-3">Amount</th>
                    <th className="py-2.5 px-3">Status</th>
                    <th className="py-2.5 px-3">When</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {filteredActivity.map((row, idx) => (
                    <tr
                      key={`${row.caseCode}-${row.when}-${idx}`}
                      data-testid={`activity-row-${row.caseCode}`}
                      onClick={() => navigate(`/app/cases/${row.caseCode}`)}
                      className="hover:bg-surface-subtle/50 transition-colors cursor-pointer group"
                    >
                      <td className="py-3.5 px-3 font-bold text-foreground group-hover:text-brand sticky left-0 bg-surface group-hover:bg-surface-subtle/50 z-10 shadow-[1px_0_0_0_var(--color-border)]">
                        {row.caseCode}
                      </td>
                      <td className="py-3.5 px-3 text-foreground truncate max-w-xs" title={row.event}>
                        {row.event}
                      </td>
                      <td className="py-3.5 px-3 font-medium text-foreground whitespace-nowrap">
                        <MoneyText paise={row.amountPaise} />
                      </td>
                      <td className="py-3.5 px-3 whitespace-nowrap">
                        <StatusBadge status={row.status} label={row.statusLabel} size="sm" />
                      </td>
                      <td className="py-3.5 px-3 text-foreground-muted whitespace-nowrap">
                        {row.when}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </Card>

      <SeedDemoDataDialog
        isOpen={isSeedDialogOpen}
        onClose={() => setIsSeedDialogOpen(false)}
        onSuccess={(count) => {
          showToast(`Added ${count} demo cases to your workspace.`, 'success');
          queryClient.invalidateQueries({ queryKey: ['overview'] });
          queryClient.invalidateQueries({ queryKey: ['cases'] });
          queryClient.invalidateQueries({ queryKey: ['demoDataPreview'] });
        }}
      />
    </div>
  );
};
