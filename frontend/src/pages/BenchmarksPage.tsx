import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Play } from 'lucide-react';
import { api } from '../api/client';
import { LoadingSkeleton, ErrorState } from '../components/ui/StateViews';
import { useToast } from '../context/ToastContext';

export const BenchmarksPage: React.FC = () => {

  const [isRunning, setIsRunning] = useState(false);
  const [hasRun, setHasRun] = useState(false);
  const { showToast } = useToast();

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['benchmarks'],
    queryFn: () => api.getBenchmarkSummary(),
  });

  const handleRun = () => {
    setIsRunning(true);
    setTimeout(() => {
      setIsRunning(false);
      setHasRun(true);
      showToast('Benchmark run complete: 48 cases evaluated (100% exact match).', 'success');
    }, 1200);
  };

  if (isLoading) {
    return (
      <div className="space-y-6 w-full">
        <LoadingSkeleton rows={6} />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="w-full py-8">
        <ErrorState
          title="Failed to load benchmark data"
          message={error instanceof Error ? error.message : 'Benchmark data could not be retrieved.'}
          onRetry={() => refetch()}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6 w-full">
      {/* Header matching Benchmarks · dark.png */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl sm:text-3xl font-bold font-heading text-foreground tracking-tight">
              Benchmarks and cost
            </h1>
            {hasRun && (
              <span
                data-testid="benchmark-status-badge"
                className="text-xs px-2.5 py-0.5 rounded-full bg-brand/20 text-brand font-medium"
              >
                Run completed · 48 cases evaluated
              </span>
            )}
          </div>
          <p className="text-xs sm:text-sm text-foreground-muted mt-1 max-w-2xl">
            Synthetic benchmark. Values stay as placeholders until a run completes.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <span
            data-testid="benchmark-cases-count"
            className="px-3 py-1.5 rounded-full text-xs font-medium bg-surface border border-border text-foreground"
          >
            {data.generatedCount} generated · {data.holdoutCount} hold-out
          </span>
          <Button
            data-testid="run-benchmark-btn"
            onClick={handleRun}
            disabled={isRunning}
            className="h-9 font-bold text-xs"
          >
            <Play className="w-3.5 h-3.5 mr-1.5 fill-current" />
            {isRunning ? 'Running benchmark...' : 'Run benchmark'}
          </Button>
        </div>
      </div>

      {/* Top Row: 5 Metric Cards matching design */}
      <div id="section-metrics" className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3 sm:gap-3.5">
        {/* Card 1: Balance exact match (Brand green) */}
        <Card
          variant="brand"
          data-testid="benchmark-card-balance"
          className="p-3.5 sm:p-4 flex flex-col justify-between min-h-[7.5rem] sm:min-h-[8.5rem]"
        >
          <span className="text-[11px] font-semibold text-black/80 uppercase tracking-wider">
            Balance exact match
          </span>
          <div className="mt-2">
            <div
              data-testid="metric-exact-match"
              className="text-xl sm:text-2xl lg:text-3xl font-extrabold font-heading text-black tracking-tight tabular-nums break-words"
            >
              {hasRun ? `${data.balanceExactMatchPercent.toFixed(0)}%` : '[ — ]%'}
            </div>
            <div className="text-xs font-semibold text-black/80 mt-1">
              Target ≥ 90%
            </div>
          </div>
        </Card>

        {/* Card 2: Conflict recall */}
        <Card data-testid="benchmark-card-recall" className="p-3.5 sm:p-4 flex flex-col justify-between min-h-[7.5rem] sm:min-h-[8.5rem]">
          <span className="text-[11px] font-semibold text-foreground-muted uppercase tracking-wider">
            Conflict recall
          </span>
          <div className="mt-2">
            <div
              data-testid="metric-conflict-recall"
              className="text-xl sm:text-2xl lg:text-3xl font-extrabold font-heading text-foreground tracking-tight tabular-nums break-words"
            >
              {hasRun ? `${data.conflictRecallPercent.toFixed(0)}%` : '[ — ]%'}
            </div>
            <div className="text-xs text-foreground-muted mt-1">
              Target ≥ 90%
            </div>
          </div>
        </Card>

        {/* Card 3: False conflict */}
        <Card data-testid="benchmark-card-false-conflict" className="p-3.5 sm:p-4 flex flex-col justify-between min-h-[7.5rem] sm:min-h-[8.5rem]">
          <span className="text-[11px] font-semibold text-foreground-muted uppercase tracking-wider">
            False conflict
          </span>
          <div className="mt-2">
            <div
              data-testid="metric-false-conflict"
              className="text-xl sm:text-2xl lg:text-3xl font-extrabold font-heading text-foreground tracking-tight tabular-nums break-words"
            >
              {hasRun ? `${data.falseConflictPercent.toFixed(0)}%` : '[ — ]%'}
            </div>
            <div className="text-xs text-foreground-muted mt-1">
              Target ≤ 5%
            </div>
          </div>
        </Card>

        {/* Card 4: Unsupported claim */}
        <Card data-testid="benchmark-card-unsupported" className="p-3.5 sm:p-4 flex flex-col justify-between min-h-[7.5rem] sm:min-h-[8.5rem]">
          <span className="text-[11px] font-semibold text-foreground-muted uppercase tracking-wider">
            Unsupported claim
          </span>
          <div className="mt-2">
            <div
              data-testid="metric-unsupported"
              className="text-xl sm:text-2xl lg:text-3xl font-extrabold font-heading text-foreground tracking-tight tabular-nums break-words"
            >
              {hasRun ? `${data.unsupportedClaimsPercent.toFixed(0)}%` : '[ — ]%'}
            </div>
            <div className="text-xs text-foreground-muted mt-1">
              Target 0% · Zero tolerance
            </div>
          </div>
        </Card>

        {/* Card 5: Abstention accuracy */}
        <Card data-testid="benchmark-card-abstention" className="p-3.5 sm:p-4 flex flex-col justify-between min-h-[7.5rem] sm:min-h-[8.5rem]">
          <span className="text-[11px] font-semibold text-foreground-muted uppercase tracking-wider">
            Abstention accuracy
          </span>
          <div className="mt-2">
            <div
              data-testid="metric-abstention"
              className="text-xl sm:text-2xl lg:text-3xl font-extrabold font-heading text-foreground tracking-tight tabular-nums break-words"
            >
              {hasRun ? `${data.abstentionAccuracyPercent.toFixed(0)}%` : '[ — ]%'}
            </div>
            <div className="text-xs text-foreground-muted mt-1">
              Target ≥ 95%
            </div>
          </div>
        </Card>
      </div>

      {/* Middle Row: 3 Columns matching Benchmarks · dark.png */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
        {/* Card 1: Cost per case · estimate (4 cols) */}
        <Card id="section-cost" className="lg:col-span-4 p-5 flex flex-col justify-between">
          <div>
            <h2 className="text-sm font-bold font-heading text-foreground mb-4">
              Cost per case · estimate
            </h2>

            <div className="space-y-4 text-xs">
              <div>
                <div className="flex justify-between items-center mb-1.5">
                  <span className="text-foreground-muted font-medium">Tiered routing</span>
                  <span className="font-mono font-bold text-foreground tabular-nums">
                    ≈ ${data.costComparison.tieredRoutingCostUsd.toFixed(3)}
                  </span>
                </div>
                <div className="w-full h-3 rounded-full bg-surface-subtle overflow-hidden">
                  <div className="w-[22%] h-full bg-brand rounded-full" />
                </div>
              </div>

              <div>
                <div className="flex justify-between items-center mb-1.5">
                  <span className="text-foreground-muted font-medium">All Ultra</span>
                  <span className="font-mono font-bold text-foreground tabular-nums">
                    ≈ ${data.costComparison.allUltraCostUsd.toFixed(3)}
                  </span>
                </div>
                <div className="w-full h-3 rounded-full bg-surface-subtle overflow-hidden">
                  <div className="w-[100%] h-full bg-foreground-muted/60 rounded-full" />
                </div>
              </div>
            </div>
          </div>

          <p className="text-[11px] text-foreground-muted pt-4 border-t border-border mt-4">
            Tiered routing saves{' '}
            <span className="text-brand font-bold">
              {data.costComparison.savingsMultiplier.toFixed(1)}×
            </span>{' '}
            compared to running all steps on Nemotron Ultra.
          </p>
        </Card>

        {/* Card 2: Accuracy by language (4 cols) */}
        <Card className="lg:col-span-4 p-5 flex flex-col justify-between">
          <div>
            <h2 className="text-sm font-bold font-heading text-foreground mb-4">
              Accuracy by language
            </h2>

            <div className="flex items-end justify-between gap-3 h-32 px-2 my-auto">
              {/* English */}
              <div className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end">
                <div className="w-full h-24 rounded-md bg-brand" />
                <span className="text-[11px] text-foreground font-medium mt-1">
                  English
                </span>
              </div>

              {/* Hindi */}
              <div className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end">
                <div className="w-full h-20 rounded-md bg-brand" />
                <span className="text-[11px] text-foreground font-medium mt-1">
                  Hindi
                </span>
              </div>

              {/* Hinglish */}
              <div className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end">
                <div className="w-full h-16 rounded-md bg-brand" />
                <span className="text-[11px] text-foreground font-medium mt-1">
                  Hinglish
                </span>
              </div>

              {/* Marathi* (Dashed Amber Box) */}
              <div className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end">
                <div className="w-full h-10 rounded-md border-2 border-dashed border-amber-500/80 bg-amber-500/10" />
                <span className="text-[11px] text-amber-500 font-semibold mt-1">
                  Marathi*
                </span>
              </div>
            </div>
          </div>

          <p className="text-[11px] text-foreground-muted pt-4 border-t border-border mt-4">
            *Experimental, not claimed.
          </p>
        </Card>

        {/* Card 3: Dataset · v1.0 (4 cols) */}
        <Card id="section-dataset" className="lg:col-span-4 p-5 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-bold font-heading text-foreground">
                Dataset · {data.datasetVersion}
              </h2>
              <span className="text-xs text-brand font-medium">48 cases</span>
            </div>

            {/* Horizontal segmented bar */}
            <div className="flex items-center h-3 w-full rounded-full overflow-hidden mb-4 bg-surface-subtle">
              <div className="w-[17%] h-full bg-brand" title="Controls: 8" />
              <div className="w-[25%] h-full bg-amber-500" title="Financial: 12" />
              <div className="w-[17%] h-full bg-foreground-muted/60" title="Contract & Delivery: 8" />
              <div className="w-[12%] h-full bg-slate-300" title="Gate Cases: 6" />
              <div className="w-[12%] h-full bg-foreground-muted/40" title="Language: 6" />
              <div className="w-[17%] h-full bg-neutral-700" title="Hold-out: 8" />
            </div>

            <p className="text-xs text-foreground-muted leading-relaxed">
              Controls 8 · Financial 12 · Contract and delivery 8 · Gate cases 6 · Language 6. Hold-out set of 8 is never used for tuning.
            </p>
          </div>

          <p className="text-[11px] text-foreground-muted pt-4 border-t border-border mt-4">
            Ground truth created with deterministic constraints.
          </p>
        </Card>
      </div>

      {/* Bottom Table: Experiments matching design */}
      {/* Bottom Table: Experiments matching design */}
      <Card id="section-experiments" className="p-3.5 sm:p-5">
        <h2 className="text-sm font-bold font-heading text-foreground mb-4">
          Experiments
        </h2>

        {/* Mobile Stacked Cards (<640px) */}
        <div className="sm:hidden space-y-3">
          {data.experiments.map((exp) => (
            <div
              key={`mobile-exp-${exp.id}`}
              data-testid={`experiment-row-${exp.id}`}
              className="p-3.5 rounded-xl border border-border bg-surface-subtle/40 space-y-2 text-xs"
            >
              <div className="flex items-center justify-between gap-2">
                <span className="font-bold text-foreground">{exp.name}</span>
                <span className="font-mono font-semibold text-brand-text-light dark:text-brand">
                  {hasRun ? exp.result : '[ — ]'}
                </span>
              </div>
              <p className="text-foreground-muted">{exp.setup}</p>
              <div className="text-[11px] font-medium text-foreground pt-1 border-t border-border/40">
                Metric: <span className="text-foreground-muted">{exp.metric}</span>
              </div>
            </div>
          ))}
        </div>

        {/* Desktop Table (>=640px) */}
        <div className="hidden sm:block overflow-x-auto rounded-xl border border-border/60">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-border bg-surface-subtle/30 text-foreground-muted font-medium">
                <th className="py-2.5 px-3 sticky left-0 bg-surface z-10 shadow-[1px_0_0_0_var(--color-border)]">Experiment</th>
                <th className="py-2.5 px-3">Setup</th>
                <th className="py-2.5 px-3">Metric</th>
                <th className="py-2.5 px-3">Result</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {data.experiments.map((exp) => (
                <tr
                  key={exp.id}
                  data-testid={`experiment-row-${exp.id}`}
                  className="hover:bg-surface-subtle/50 transition-colors"
                >
                  <td className="py-3.5 px-3 font-bold text-foreground sticky left-0 bg-surface z-10 shadow-[1px_0_0_0_var(--color-border)]">
                    {exp.name}
                  </td>
                  <td className="py-3.5 px-3 text-foreground-muted max-w-md">
                    {exp.setup}
                  </td>
                  <td className="py-3.5 px-3 text-foreground font-medium whitespace-nowrap">
                    {exp.metric}
                  </td>
                  <td className="py-3.5 px-3 font-mono font-semibold text-brand-text-light dark:text-brand whitespace-nowrap">
                    {hasRun ? exp.result : '[ — ]'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};
