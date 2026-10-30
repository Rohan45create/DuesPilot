import React, { useState, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { MoneyText } from '../components/common/MoneyText';
import { StatusBadge } from '../components/common/StatusBadge';
import { LoadingSkeleton, ErrorState, EmptyState } from '../components/ui/StateViews';
import { api } from '../api/client';
import { formatPaiseToRupees } from '../utils/money';
import { cn } from '../utils/formatters';
import { useToast } from '../context/ToastContext';
import { Search, Plus, Download, ChevronDown, Check, ArrowUpDown, ArrowUp, ArrowDown, Sparkles } from 'lucide-react';
import { SeedDemoDataDialog } from '../components/demo/SeedDemoDataDialog';

type SortField = 'code' | 'buyerName' | 'invoicedPaise' | 'confirmedPaidPaise' | 'reconciledBalancePaise' | 'conflicts' | 'status';
type SortDir = 'asc' | 'desc';

export const CasesListPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { showToast } = useToast();
  const queryClient = useQueryClient();

  // Read URL query params
  const search = searchParams.get('search') || '';
  const selectedStatus = searchParams.get('status') || 'ALL';
  const selectedBuyer = searchParams.get('buyer') || 'ALL';
  const selectedConflictType = searchParams.get('conflict') || 'ALL';
  const sortField = (searchParams.get('sort') as SortField) || 'code';
  const sortDir = (searchParams.get('dir') as SortDir) || 'asc';
  const currentPage = parseInt(searchParams.get('page') || '1', 10);
  const pageSize = 5;

  const [showStatusFilter, setShowStatusFilter] = useState(false);
  const [showBuyerFilter, setShowBuyerFilter] = useState(false);
  const [showConflictFilter, setShowConflictFilter] = useState(false);
  const [isSeedDialogOpen, setIsSeedDialogOpen] = useState(false);
  const [hideDemoData, setHideDemoData] = useState(searchParams.get('hideDemo') === 'true');

  const {
    data: casesList,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['cases'],
    queryFn: () => api.getCases(),
  });

  const updateParam = (key: string, val: string | null, resetPage = true) => {
    const next = new URLSearchParams(searchParams);
    if (!val || val === 'ALL') {
      next.delete(key);
    } else {
      next.set(key, val);
    }
    if (resetPage) {
      next.delete('page');
    }
    setSearchParams(next, { replace: true });
  };

  const handleSort = (field: SortField) => {
    const next = new URLSearchParams(searchParams);
    if (sortField === field) {
      next.set('dir', sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      next.set('sort', field);
      next.set('dir', 'asc');
    }
    setSearchParams(next, { replace: true });
  };

  const handlePageChange = (newPage: number) => {
    const next = new URLSearchParams(searchParams);
    if (newPage <= 1) next.delete('page');
    else next.set('page', String(newPage));
    setSearchParams(next, { replace: true });
  };

  const toggleHideDemoData = () => {
    const nextVal = !hideDemoData;
    setHideDemoData(nextVal);
    updateParam('hideDemo', nextVal ? 'true' : null);
  };

  const uniqueBuyers = useMemo(() => {
    if (!casesList) return [];
    return Array.from(new Set(casesList.map((c) => c.buyerName || (c as any).buyer_name || c.title || 'Unknown Buyer')));
  }, [casesList]);

  // Filter cases
  const filteredCases = useMemo(() => {
    if (!casesList) return [];
    return casesList.filter((c) => {
      if (hideDemoData && (c.isDemoData || (c as any).is_demo_data)) {
        return false;
      }

      const code = c.code || '';
      const buyer = c.buyerName || (c as any).buyer_name || '';
      const title = c.title || '';
      const matchesSearch =
        code.toLowerCase().includes(search.toLowerCase()) ||
        buyer.toLowerCase().includes(search.toLowerCase()) ||
        title.toLowerCase().includes(search.toLowerCase());

      const matchesStatus =
        selectedStatus === 'ALL' || c.status === selectedStatus;

      const matchesBuyer =
        selectedBuyer === 'ALL' || buyer === selectedBuyer;

      const conflicts = c.conflicts || [];
      const matchesConflict =
        selectedConflictType === 'ALL' ||
        (selectedConflictType === 'WITH_CONFLICTS' && conflicts.length > 0) ||
        (selectedConflictType === 'NO_CONFLICTS' && conflicts.length === 0);

      return matchesSearch && matchesStatus && matchesBuyer && matchesConflict;
    });
  }, [casesList, search, selectedStatus, selectedBuyer, selectedConflictType, hideDemoData]);

  // Sort filtered cases
  const sortedCases = useMemo(() => {
    return [...filteredCases].sort((a, b) => {
      let cmp = 0;
      if (sortField === 'code') cmp = (a.code || '').localeCompare(b.code || '');
      else if (sortField === 'buyerName') {
        const aBuyer = a.buyerName || (a as any).buyer_name || '';
        const bBuyer = b.buyerName || (b as any).buyer_name || '';
        cmp = aBuyer.localeCompare(bBuyer);
      }
      else if (sortField === 'invoicedPaise') cmp = (a.invoicedPaise || 0) - (b.invoicedPaise || 0);
      else if (sortField === 'confirmedPaidPaise') cmp = (a.confirmedPaidPaise || 0) - (b.confirmedPaidPaise || 0);
      else if (sortField === 'reconciledBalancePaise') {
        const balA = a.reconciledBalancePaise ?? -1;
        const balB = b.reconciledBalancePaise ?? -1;
        cmp = balA - balB;
      } else if (sortField === 'conflicts') cmp = (a.conflicts || []).length - (b.conflicts || []).length;
      else if (sortField === 'status') cmp = (a.status || '').localeCompare(b.status || '');

      return sortDir === 'asc' ? cmp : -cmp;
    });
  }, [filteredCases, sortField, sortDir]);

  const totalPages = Math.ceil(sortedCases.length / pageSize) || 1;
  const paginatedCases = useMemo(() => {
    const validPage = Math.min(Math.max(currentPage, 1), totalPages);
    const start = (validPage - 1) * pageSize;
    return sortedCases.slice(start, start + pageSize);
  }, [sortedCases, currentPage, totalPages, pageSize]);

  const handleExportCsv = () => {
    if (!sortedCases.length) {
      showToast('No cases available to export.', 'error');
      return;
    }
    const headers = ['Case,Buyer,Invoiced,Confirmed_Paid,Balance,Conflicts_Count,Status,Last_Run'];
    const rows = sortedCases.map((c) => [
      c.code,
      `"${c.buyerName.replace(/"/g, '""')}"`,
      formatPaiseToRupees(c.invoicedPaise),
      formatPaiseToRupees(c.confirmedPaidPaise),
      c.reconciledBalancePaise !== null ? formatPaiseToRupees(c.reconciledBalancePaise) : 'Cannot determine',
      (c.conflicts || []).length,
      c.status,
      c.lastRunAt || 'N/A',
    ].join(','));

    const csvContent = 'data:text/csv;charset=utf-8,' + encodeURIComponent([headers, ...rows].join('\n'));
    const link = document.createElement('a');
    link.setAttribute('href', csvContent);
    link.setAttribute('download', `duespilot_cases_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    showToast(`Exported ${sortedCases.length} cases to CSV.`, 'success');
  };

  const handleResetFilters = () => {
    setSearchParams(new URLSearchParams(), { replace: true });
    setShowStatusFilter(false);
    setShowBuyerFilter(false);
    setShowConflictFilter(false);
    setHideDemoData(false);
  };

  const renderSortHeader = (label: string, field: SortField) => {
    const isCurrent = sortField === field;
    const isCode = field === 'code';
    return (
      <th
        className={cn(
          "py-3 px-3 cursor-pointer hover:text-foreground transition-colors select-none",
          isCode && "sticky left-0 bg-surface z-10 shadow-[1px_0_0_0_var(--color-border)]"
        )}
        data-testid={`sort-header-${field}`}
        onClick={() => handleSort(field)}
      >
        <div className="flex items-center gap-1.5 font-medium">
          <span>{label}</span>
          {isCurrent ? (
            sortDir === 'asc' ? (
              <ArrowUp className="w-3.5 h-3.5 text-brand" />
            ) : (
              <ArrowDown className="w-3.5 h-3.5 text-brand" />
            )
          ) : (
            <ArrowUpDown className="w-3.5 h-3.5 text-foreground-muted opacity-40 hover:opacity-100" />
          )}
        </div>
      </th>
    );
  };

  if (isLoading) {
    return (
      <div className="space-y-6 w-full p-2">
        <div className="h-10 bg-surface-subtle rounded-md w-1/4 animate-pulse" />
        <Card className="p-5">
          <LoadingSkeleton rows={6} />
        </Card>
      </div>
    );
  }

  if (isError || !casesList) {
    return (
      <ErrorState
        title="Failed to load cases"
        message={error instanceof Error ? error.message : 'Could not fetch case records.'}
        onRetry={() => refetch()}
      />
    );
  }

  return (
    <div data-testid="cases-page" className="space-y-6 w-full">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold font-heading text-foreground tracking-tight">
            Cases
          </h1>
          <p className="text-xs sm:text-sm text-foreground-muted mt-1 max-w-2xl">
            Every case with its balance, open conflicts and eligibility-gate status.
          </p>
        </div>
        <Button
          data-testid="cases-new-case-btn"
          onClick={() => navigate('/app/cases/new')}
          className="shrink-0"
        >
          <Plus className="w-4 h-4 mr-1 stroke-[3]" />
          New case
        </Button>
      </div>

      {/* Main Table Card */}
      <Card className="p-5">
        {casesList.length === 0 ? (
          <div data-testid="cases-empty-state" className="p-10 text-center flex flex-col items-center justify-center max-w-md mx-auto my-6">
            <div className="w-12 h-12 rounded-2xl bg-brand/10 border border-brand/20 flex items-center justify-center text-brand mb-4">
              <Sparkles className="w-6 h-6" />
            </div>
            <h3 className="text-base font-bold font-heading text-foreground mb-1.5">
              No cases in your workspace yet
            </h3>
            <p className="text-xs text-foreground-muted mb-6 leading-relaxed">
              Create a new receivables case or seed realistic MSME demo data with verified audit trails to test the workflow.
            </p>
            <div className="flex flex-wrap items-center justify-center gap-3">
              <Button
                variant="primary"
                size="sm"
                data-testid="cases-empty-seed-demo-btn"
                onClick={() => setIsSeedDialogOpen(true)}
                className="text-xs font-medium"
              >
                <Sparkles className="w-3.5 h-3.5 mr-1.5" />
                Seed demo data
              </Button>
              <Button
                variant="outline"
                size="sm"
                data-testid="cases-empty-new-case-btn"
                onClick={() => navigate('/app/cases/new')}
                className="text-xs"
              >
                <Plus className="w-3.5 h-3.5 mr-1.5" />
                New case
              </Button>
            </div>
          </div>
        ) : (
          <>
            {/* Filter controls */}
            <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3 mb-5">
              <div className="flex flex-wrap items-center gap-2.5 flex-1 relative">
                {/* Search Input */}
                <div className="relative flex-1 min-w-[240px] max-w-sm">
                  <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-foreground-muted" />
                  <input
                    type="text"
                    data-testid="cases-search-input"
                    value={search}
                    onChange={(e) => updateParam('search', e.target.value)}
                    placeholder="Search by case, buyer or invoice"
                    className="w-full h-9 pl-9 pr-3 text-xs rounded-lg bg-surface-subtle border border-border text-foreground placeholder:text-foreground-muted focus:outline-none focus:ring-1 focus:ring-brand"
                  />
                </div>

            {/* Status Filter Dropdown */}
            <div className="relative">
              <Button
                variant="outline"
                size="sm"
                data-testid="cases-status-filter-btn"
                onClick={() => {
                  setShowStatusFilter(!showStatusFilter);
                  setShowBuyerFilter(false);
                  setShowConflictFilter(false);
                }}
                className="h-9 rounded-lg text-xs font-normal"
              >
                Status: {selectedStatus === 'ALL' ? 'All' : selectedStatus.replace('_', ' ')} <ChevronDown className="w-3 h-3 ml-1" />
              </Button>
              {showStatusFilter && (
                <div
                  data-testid="cases-status-dropdown"
                  className="absolute left-0 mt-1 w-44 rounded-xl bg-surface border border-border shadow-xl z-20 p-1.5 text-xs space-y-1"
                >
                  {['ALL', 'NEEDS_REVIEW', 'MISSING_PROOF', 'RECONCILED', 'CLARIFY'].map((st) => (
                    <button
                      key={st}
                      type="button"
                      data-testid={`filter-status-${st.toLowerCase()}`}
                      onClick={() => {
                        updateParam('status', st);
                        setShowStatusFilter(false);
                      }}
                      className="w-full text-left px-2.5 py-1.5 rounded-lg hover:bg-surface-subtle flex items-center justify-between cursor-pointer"
                    >
                      <span>{st === 'ALL' ? 'All statuses' : st.replace('_', ' ')}</span>
                      {selectedStatus === st && <Check className="w-3.5 h-3.5 text-brand" />}
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Buyer Filter Dropdown */}
            <div className="relative">
              <Button
                variant="outline"
                size="sm"
                data-testid="cases-buyer-filter-btn"
                onClick={() => {
                  setShowBuyerFilter(!showBuyerFilter);
                  setShowStatusFilter(false);
                  setShowConflictFilter(false);
                }}
                className="h-9 rounded-lg text-xs font-normal"
              >
                Buyer: {selectedBuyer === 'ALL' ? 'All' : selectedBuyer} <ChevronDown className="w-3 h-3 ml-1" />
              </Button>
              {showBuyerFilter && (
                <div
                  data-testid="cases-buyer-dropdown"
                  className="absolute left-0 mt-1 w-56 rounded-xl bg-surface border border-border shadow-xl z-20 p-1.5 text-xs space-y-1 max-h-48 overflow-y-auto"
                >
                  <button
                    type="button"
                    data-testid="filter-buyer-all"
                    onClick={() => {
                      updateParam('buyer', 'ALL');
                      setShowBuyerFilter(false);
                    }}
                    className="w-full text-left px-2.5 py-1.5 rounded-lg hover:bg-surface-subtle flex items-center justify-between cursor-pointer"
                  >
                    <span>All buyers</span>
                    {selectedBuyer === 'ALL' && <Check className="w-3.5 h-3.5 text-brand" />}
                  </button>
                  {uniqueBuyers.map((b) => (
                    <button
                      key={b}
                      type="button"
                      data-testid={`filter-buyer-${b.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`}
                      onClick={() => {
                        updateParam('buyer', b);
                        setShowBuyerFilter(false);
                      }}
                      className="w-full text-left px-2.5 py-1.5 rounded-lg hover:bg-surface-subtle flex items-center justify-between cursor-pointer"
                    >
                      <span className="truncate">{b}</span>
                      {selectedBuyer === b && <Check className="w-3.5 h-3.5 text-brand" />}
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Conflict Filter Dropdown */}
            <div className="relative">
              <Button
                variant="outline"
                size="sm"
                data-testid="cases-conflict-filter-btn"
                onClick={() => {
                  setShowConflictFilter(!showConflictFilter);
                  setShowStatusFilter(false);
                  setShowBuyerFilter(false);
                }}
                className="h-9 rounded-lg text-xs font-normal"
              >
                Conflict: {selectedConflictType === 'ALL' ? 'All' : selectedConflictType === 'WITH_CONFLICTS' ? 'Has conflicts' : 'No conflicts'} <ChevronDown className="w-3 h-3 ml-1" />
              </Button>
              {showConflictFilter && (
                <div
                  data-testid="cases-conflict-dropdown"
                  className="absolute left-0 mt-1 w-44 rounded-xl bg-surface border border-border shadow-xl z-20 p-1.5 text-xs space-y-1"
                >
                  {[
                    { id: 'ALL', label: 'All' },
                    { id: 'WITH_CONFLICTS', label: 'Has open conflicts' },
                    { id: 'NO_CONFLICTS', label: 'No conflicts' },
                  ].map((cf) => (
                    <button
                      key={cf.id}
                      type="button"
                      data-testid={`filter-conflict-${cf.id.toLowerCase()}`}
                      onClick={() => {
                        updateParam('conflict', cf.id);
                        setShowConflictFilter(false);
                      }}
                      className="w-full text-left px-2.5 py-1.5 rounded-lg hover:bg-surface-subtle flex items-center justify-between cursor-pointer"
                    >
                      <span>{cf.label}</span>
                      {selectedConflictType === cf.id && <Check className="w-3.5 h-3.5 text-brand" />}
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Hide Demo Data Toggle */}
            <Button
              variant={hideDemoData ? "primary" : "outline"}
              size="sm"
              data-testid="cases-hide-demo-toggle"
              onClick={toggleHideDemoData}
              className={cn(
                "h-9 rounded-lg text-xs font-normal transition-colors",
                hideDemoData && "bg-brand/15 text-brand border-brand/30 hover:bg-brand/20"
              )}
            >
              {hideDemoData ? "Showing real cases only" : "Hide demo data"}
            </Button>
          </div>

          {/* Export CSV Button */}
          <Button
            variant="outline"
            size="sm"
            data-testid="cases-export-csv-btn"
            onClick={handleExportCsv}
            className="h-9 rounded-lg text-xs shrink-0"
          >
            <Download className="w-3.5 h-3.5 mr-1.5" />
            Export CSV
          </Button>
        </div>

        {/* Table or Empty State */}
        {sortedCases.length === 0 ? (
          <EmptyState
            title="No cases match your filters"
            description="Clear or adjust your search and filters to view cases."
            actionText="Reset filters"
            onAction={handleResetFilters}
          />
        ) : (
          <>
            {/* Mobile Stacked Cards (<640px) */}
            <div data-testid="cases-mobile-cards" className="sm:hidden space-y-3">
              {paginatedCases.map((c) => {
                const isUnknownBalance = c.reconciledBalancePaise === null;
                return (
                  <div
                    key={`mobile-case-${c.code}`}
                    data-testid={`case-row-${c.code}`}
                    onClick={() => navigate(`/app/cases/${c.code}`)}
                    className="p-4 rounded-2xl border border-border bg-surface-subtle/40 active:bg-surface-subtle transition-colors cursor-pointer space-y-3 min-h-[44px]"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-sm text-foreground">
                          {c.code}
                        </span>
                        {(c.isDemoData || (c as any).is_demo_data) && (
                          <span
                            data-testid={`case-demo-badge-${c.code}`}
                            className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-amber-500/10 text-amber-500 border border-amber-500/20"
                          >
                            Demo
                          </span>
                        )}
                        <StatusBadge status={c.status} size="sm" />
                      </div>
                      <span className="text-[11px] font-mono text-foreground-muted">
                        12 Oct
                      </span>
                    </div>

                    <div>
                      <div
                        className="text-xs font-semibold text-foreground truncate max-w-full"
                        title={c.buyerName}
                      >
                        {c.buyerName}
                      </div>
                      <div className="text-[11px] text-foreground-muted mt-0.5">
                        {(c.conflicts || []).length > 0 ? (
                          <span className="text-amber-500 font-medium">
                            {(c.conflicts || []).length} open conflict{(c.conflicts || []).length > 1 ? 's' : ''}
                          </span>
                        ) : (
                          <span className="text-brand font-medium">Clean reconciliation</span>
                        )}
                      </div>
                    </div>

                    <div className="grid grid-cols-3 gap-2 pt-2 border-t border-border/50 text-xs">
                      <div>
                        <div className="text-[10px] uppercase font-semibold text-foreground-muted">Invoiced</div>
                        <div className="font-medium text-foreground truncate mt-0.5">
                          <MoneyText paise={c.invoicedPaise} />
                        </div>
                      </div>
                      <div>
                        <div className="text-[10px] uppercase font-semibold text-foreground-muted">Paid</div>
                        <div className="font-medium text-foreground truncate mt-0.5">
                          <MoneyText paise={c.confirmedPaidPaise} />
                        </div>
                      </div>
                      <div>
                        <div className="text-[10px] uppercase font-semibold text-foreground-muted">Balance</div>
                        <div className="font-bold truncate mt-0.5">
                          {isUnknownBalance ? (
                            <span className="text-foreground text-[11px]">Cannot determine</span>
                          ) : (
                            <MoneyText
                              paise={c.reconciledBalancePaise}
                              className="text-brand-text-light dark:text-brand"
                            />
                          )}
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center justify-between pt-2 border-t border-border/40 text-[11px]">
                      <div className="flex items-center gap-1.5" aria-label="Gate statuses A, B, C, D">
                        <span className="text-foreground-muted text-[10px] mr-1">Gates:</span>
                        <span className="w-2 h-2 rounded-full bg-amber-500" title="Gate A: Supplier classification" />
                        <span className="w-2 h-2 rounded-full bg-amber-500" title="Gate B: Delivery proof" />
                        <span className="w-2 h-2 rounded-full bg-brand" title="Gate C: Agreed payment period" />
                        <span className="w-2 h-2 rounded-full bg-amber-500" title="Gate D: Interest calculation" />
                      </div>
                      <span className="text-brand font-semibold hover:underline flex items-center">
                        Open case &rarr;
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Desktop / Tablet Table (>=640px) */}
            <div className="hidden sm:block overflow-x-auto rounded-xl border border-border/60">
              <table data-testid="cases-table" className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-border bg-surface-subtle/30 text-foreground-muted font-medium">
                    {renderSortHeader('Case', 'code')}
                    {renderSortHeader('Buyer', 'buyerName')}
                    {renderSortHeader('Invoiced', 'invoicedPaise')}
                    {renderSortHeader('Confirmed paid', 'confirmedPaidPaise')}
                    {renderSortHeader('Balance', 'reconciledBalancePaise')}
                    {renderSortHeader('Conflicts', 'conflicts')}
                    <th className="py-3 px-3">Gates A B C D</th>
                    <th className="py-3 px-3">Last run</th>
                    {renderSortHeader('Status', 'status')}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {paginatedCases.map((c) => {
                    const isUnknownBalance = c.reconciledBalancePaise === null;

                    return (
                      <tr
                        key={c.code}
                        data-testid={`case-row-${c.code}`}
                        onClick={() => navigate(`/app/cases/${c.code}`)}
                        className="hover:bg-surface-subtle/50 transition-colors cursor-pointer group"
                      >
                        <td className="py-3.5 px-3 font-bold text-foreground group-hover:text-brand sticky left-0 bg-surface group-hover:bg-surface-subtle/50 z-10 shadow-[1px_0_0_0_var(--color-border)]">
                          <div className="flex items-center gap-1.5">
                            <span>{c.code}</span>
                            {(c.isDemoData || (c as any).is_demo_data) && (
                              <span
                                data-testid={`case-demo-badge-${c.code}`}
                                className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-amber-500/10 text-amber-500 border border-amber-500/20"
                              >
                                Demo
                              </span>
                            )}
                          </div>
                        </td>
                        <td className="py-3.5 px-3 text-foreground font-medium truncate max-w-[140px] sm:max-w-[200px] lg:max-w-xs" title={c.buyerName}>
                          {c.buyerName}
                        </td>
                        <td className="py-3.5 px-3 text-foreground whitespace-nowrap">
                          <MoneyText paise={c.invoicedPaise} />
                        </td>
                        <td className="py-3.5 px-3 text-foreground whitespace-nowrap">
                          <MoneyText paise={c.confirmedPaidPaise} />
                        </td>
                        <td className="py-3.5 px-3 whitespace-nowrap">
                          {isUnknownBalance ? (
                            <span className="font-semibold text-foreground">Cannot determine</span>
                          ) : (
                            <MoneyText
                              paise={c.reconciledBalancePaise}
                              className="font-bold text-brand-text-light dark:text-brand"
                            />
                          )}
                        </td>
                        <td className="py-3.5 px-3 text-foreground font-medium">
                          {(c.conflicts || []).length}
                        </td>
                        <td className="py-3.5 px-3">
                          <div className="flex items-center gap-1.5" aria-label="Gate statuses A, B, C, D">
                            <span
                              className="w-2 h-2 rounded-full bg-amber-500"
                              title="Gate A: Supplier classification requires review"
                            />
                            <span
                              className="w-2 h-2 rounded-full bg-amber-500"
                              title="Gate B: Delivery acceptance proof missing"
                            />
                            <span
                              className="w-2 h-2 rounded-full bg-brand"
                              title="Gate C: Agreed payment period eligible"
                            />
                            <span
                              className="w-2 h-2 rounded-full bg-amber-500"
                              title="Gate D: Interest calculation abstained"
                            />
                          </div>
                        </td>
                        <td className="py-3.5 px-3 text-foreground-muted whitespace-nowrap">
                          12 Oct
                        </td>
                        <td className="py-3.5 px-3 whitespace-nowrap">
                          <StatusBadge status={c.status} size="sm" />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}

        {/* Footer info & pagination */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-5 mt-3 border-t border-border text-xs text-foreground-muted">
          <div>
            Showing {paginatedCases.length} of {sortedCases.length} · Unknown balances show &ldquo;Cannot determine&rdquo;, never zero
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              data-testid="cases-pagination-prev"
              disabled={currentPage <= 1}
              onClick={() => handlePageChange(currentPage - 1)}
              className="rounded-lg text-xs"
            >
              Previous
            </Button>
            <span data-testid="cases-pagination-info" className="text-xs px-2 font-medium">
              Page {currentPage} of {totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              data-testid="cases-pagination-next"
              disabled={currentPage >= totalPages}
              onClick={() => handlePageChange(currentPage + 1)}
              className="rounded-lg text-xs"
            >
              Next
            </Button>
          </div>
        </div>
      </>
    )}
    </Card>

    <SeedDemoDataDialog
      isOpen={isSeedDialogOpen}
      onClose={() => setIsSeedDialogOpen(false)}
      onSuccess={(count) => {
        showToast(`Added ${count} demo cases to your workspace.`, 'success');
        queryClient.invalidateQueries({ queryKey: ['cases'] });
        queryClient.invalidateQueries({ queryKey: ['overview'] });
        queryClient.invalidateQueries({ queryKey: ['demoDataPreview'] });
      }}
    />
  </div>
);
};
