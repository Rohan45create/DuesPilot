import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { BarChart3, ArrowRight, Database } from 'lucide-react';
import { api } from '../../api/client';
import { BenchmarkSummary } from '../../api/schemas';
import { useAuth } from '../../context/AuthContext';

export const BenchmarksTeaserSection: React.FC = () => {
  const { user } = useAuth();
  const { data, isLoading, isError } = useQuery<BenchmarkSummary>({
    queryKey: ['benchmark-summary-teaser'],
    queryFn: () => api.getBenchmarkSummary(),
    retry: false,
    staleTime: 1000 * 60 * 5,
  });

  // Strict compliance with R-80: If not verified live data from API, show "[ — ]"
  const hasRealData = !isLoading && !isError && !!data;

  const exactMatch = hasRealData ? `${data.balanceExactMatchPercent}%` : '[ — ]';
  const conflictRecall = hasRealData ? `${data.conflictRecallPercent}%` : '[ — ]';
  const falseConflict = hasRealData ? `${data.falseConflictPercent}%` : '[ — ]';
  const abstention = hasRealData ? `${data.abstentionAccuracyPercent}%` : '[ — ]';
  const datasetVersion = hasRealData ? data.datasetVersion : '[ — ]';
  const holdoutCount = hasRealData ? `${data.holdoutCount} cases` : '[ — ]';

  return (
    <section id="benchmarks" className="py-20 md:py-28 bg-surface-subtle/40 dark:bg-[#0E110E] border-b border-border/60 scroll-mt-16">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-brand/10 border border-brand/30 text-brand text-xs font-bold uppercase tracking-wider mb-4">
            <BarChart3 className="w-3.5 h-3.5" />
            <span>Empirical Evaluation</span>
          </div>
          <h2 className="font-heading font-extrabold text-3xl sm:text-4xl lg:text-5xl text-foreground tracking-tight">
            Benchmarks teaser.
          </h2>
          <p className="mt-4 text-base sm:text-lg text-foreground-muted">
            Live accuracy telemetry from our synthetic holdout evaluation suite. Zero fabricated ratings or marketing claims.
          </p>
        </div>

        {/* Real Data Metrics Display Grid */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-6 mb-12">
          
          {/* Metric 1: Balance Exact Match */}
          <div className="p-6 rounded-2xl bg-surface border border-border shadow-xs flex flex-col justify-between">
            <div>
              <div className="text-[11px] font-mono uppercase tracking-wider text-foreground-subtle font-semibold mb-2">
                Balance Exact Match
              </div>
              <div className="font-heading font-extrabold text-3xl sm:text-4xl text-brand tabular-nums tracking-tight">
                {exactMatch}
              </div>
            </div>
            <div className="mt-4 pt-3 border-t border-border/60 text-[11px] text-foreground-muted">
              Gross invoice minus payments & credits equals ground truth
            </div>
          </div>

          {/* Metric 2: Conflict Recall */}
          <div className="p-6 rounded-2xl bg-surface border border-border shadow-xs flex flex-col justify-between">
            <div>
              <div className="text-[11px] font-mono uppercase tracking-wider text-foreground-subtle font-semibold mb-2">
                Conflict Recall (C1–C10)
              </div>
              <div className="font-heading font-extrabold text-3xl sm:text-4xl text-brand tabular-nums tracking-tight">
                {conflictRecall}
              </div>
            </div>
            <div className="mt-4 pt-3 border-t border-border/60 text-[11px] text-foreground-muted">
              Percentage of injected discrepancies successfully caught
            </div>
          </div>

          {/* Metric 3: False Conflict Rate */}
          <div className="p-6 rounded-2xl bg-surface border border-border shadow-xs flex flex-col justify-between">
            <div>
              <div className="text-[11px] font-mono uppercase tracking-wider text-foreground-subtle font-semibold mb-2">
                False Conflict Rate
              </div>
              <div className="font-heading font-extrabold text-3xl sm:text-4xl text-brand tabular-nums tracking-tight">
                {falseConflict}
              </div>
            </div>
            <div className="mt-4 pt-3 border-t border-border/60 text-[11px] text-foreground-muted">
              Clean transactions falsely flagged with phantom issues
            </div>
          </div>

          {/* Metric 4: Abstention Accuracy */}
          <div className="p-6 rounded-2xl bg-surface border border-border shadow-xs flex flex-col justify-between">
            <div>
              <div className="text-[11px] font-mono uppercase tracking-wider text-foreground-subtle font-semibold mb-2">
                Abstention Accuracy
              </div>
              <div className="font-heading font-extrabold text-3xl sm:text-4xl text-brand tabular-nums tracking-tight">
                {abstention}
              </div>
            </div>
            <div className="mt-4 pt-3 border-t border-border/60 text-[11px] text-foreground-muted">
              Gate D correctly declining to guess unconfirmed rates
            </div>
          </div>

        </div>

        {/* Dataset telemetry & link */}
        <div className="p-6 rounded-2xl bg-surface border border-border flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3 text-xs font-mono text-foreground-muted">
            <Database className="w-4 h-4 text-brand shrink-0" />
            <span>
              Evaluation Dataset: <strong className="text-foreground">{datasetVersion}</strong> ({holdoutCount})
            </span>
            <span className="text-border">·</span>
            <span className="text-foreground-subtle">
              {hasRealData ? 'Live benchmark telemetry connected' : 'Live benchmark telemetry requires signed-in session'}
            </span>
          </div>

          <Link
            to={user ? "/app/benchmarks" : "/signin"}
            className="inline-flex items-center gap-1.5 text-xs font-bold text-brand hover:text-brand-hover hover:underline transition-colors group shrink-0"
          >
            <span>Explore full benchmark dashboard</span>
            <ArrowRight className="w-3.5 h-3.5 transition-transform group-hover:translate-x-0.5" />
          </Link>
        </div>

      </div>
    </section>
  );
};
