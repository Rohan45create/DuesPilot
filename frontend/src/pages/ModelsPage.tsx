import React, { useState } from 'react';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { cn } from '../utils/formatters';
import { useToast } from '../context/ToastContext';

export const ModelsPage: React.FC = () => {

  const [activeMode, setActiveMode] = useState<'REPLAY' | 'LIVE'>('REPLAY');
  const [perCaseCap, setPerCaseCap] = useState('$0.25');
  const [dailyCap, setDailyCap] = useState('$5.00');
  const [parallelCalls, setParallelCalls] = useState('8');
  const [saved, setSaved] = useState(false);
  const { showToast } = useToast();

  const handleSave = () => {
    setSaved(true);
    showToast('Limits and model parameters saved successfully.', 'success');
    setTimeout(() => setSaved(false), 2500);
  };

  const handleModeSwitch = (newMode: 'REPLAY' | 'LIVE') => {
    setActiveMode(newMode);
    showToast(`Switched execution mode to ${newMode === 'REPLAY' ? 'Replay Mode' : 'Live Mode (Cost-Capped)'}.`, 'info');
  };

  return (
    <div className="space-y-6 w-full">
      {/* Header matching Models.png */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold font-heading text-foreground tracking-tight">
            Models and rules
          </h1>
          <p className="text-xs sm:text-sm text-foreground-muted mt-1 max-w-2xl">
            How work is routed, what limits apply, and which legal settings are still pending review.
          </p>
        </div>

        {/* Mode Toggle matching Models.png top right */}
        <div id="section-modes" className="flex items-center gap-1.5 p-1 rounded-full bg-surface border border-border text-xs shrink-0 self-start sm:self-auto">
          <button
            type="button"
            data-testid="mode-replay-btn"
            onClick={() => handleModeSwitch('REPLAY')}
            className={cn(
              'px-3.5 py-1.5 rounded-full font-semibold transition-all cursor-pointer',
              activeMode === 'REPLAY'
                ? 'bg-brand text-black shadow-xs'
                : 'text-foreground-muted hover:text-foreground'
            )}
          >
            Replay
          </button>
          <button
            type="button"
            data-testid="mode-live-btn"
            onClick={() => handleModeSwitch('LIVE')}
            className={cn(
              'px-3.5 py-1.5 rounded-full font-semibold transition-all cursor-pointer',
              activeMode === 'LIVE'
                ? 'bg-brand text-black shadow-xs'
                : 'text-foreground-muted hover:text-foreground'
            )}
          >
            Live · capped
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Top Left: Model routing (7 cols) */}
        <Card id="section-routing" className="lg:col-span-7 p-5 space-y-5">
          <h2 className="text-sm font-bold font-heading text-foreground">
            Model routing
          </h2>

          {/* Mobile Stacked Cards (<640px) */}
          <div className="sm:hidden space-y-3">
            {[
              { role: 'Extract', model: 'Nemotron 3 Nano', usedFor: 'Fields and quotes per document', checks: 'Context to verify' },
              { role: 'Reason', model: 'Nemotron 3 Super', usedFor: 'Conflicts, clauses, drafts', checks: 'Price to verify' },
              { role: 'Adjudicate', model: 'Nemotron 3 Ultra', usedFor: 'Hard cases, at most one per case', checks: 'Price to verify' },
            ].map((r) => (
              <div key={r.role} className="p-3.5 rounded-xl border border-border bg-surface-subtle/40 space-y-2 text-xs">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-bold text-foreground">{r.role}</span>
                  <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-surface border border-border text-foreground font-mono">
                    Model: {r.model}
                  </span>
                </div>
                <div className="text-foreground-muted">{r.usedFor}</div>
                <div className="text-[11px] text-amber-500 font-medium pt-1 border-t border-border/40">
                  {r.checks}
                </div>
              </div>
            ))}
          </div>

          {/* Desktop Table (>=640px) */}
          <div className="hidden sm:block overflow-x-auto rounded-xl border border-border/60">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-border bg-surface-subtle/30 text-foreground-muted font-medium">
                  <th className="py-2.5 px-3 sticky left-0 bg-surface z-10 shadow-[1px_0_0_0_var(--color-border)]">Role</th>
                  <th className="py-2.5 px-3">Model</th>
                  <th className="py-2.5 px-3">Used for</th>
                  <th className="py-2.5 px-3">Checks</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                <tr className="hover:bg-surface-subtle/50 transition-colors">
                  <td className="py-3 px-3 font-semibold text-foreground sticky left-0 bg-surface z-10 shadow-[1px_0_0_0_var(--color-border)]">Extract</td>
                  <td className="py-3 px-3">
                    <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-surface-subtle border border-border text-foreground font-mono">
                      Nemotron 3 Nano
                    </span>
                  </td>
                  <td className="py-3 px-3 text-foreground-muted">
                    Fields and quotes per document
                  </td>
                  <td className="py-3 px-3 text-amber-500 font-medium whitespace-nowrap">
                    Context to verify
                  </td>
                </tr>
                <tr className="hover:bg-surface-subtle/50 transition-colors">
                  <td className="py-3 px-3 font-semibold text-foreground sticky left-0 bg-surface z-10 shadow-[1px_0_0_0_var(--color-border)]">Reason</td>
                  <td className="py-3 px-3">
                    <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-surface-subtle border border-border text-foreground font-mono">
                      Nemotron 3 Super
                    </span>
                  </td>
                  <td className="py-3 px-3 text-foreground-muted">
                    Conflicts, clauses, drafts
                  </td>
                  <td className="py-3 px-3 text-amber-500 font-medium whitespace-nowrap">
                    Price to verify
                  </td>
                </tr>
                <tr className="hover:bg-surface-subtle/50 transition-colors">
                  <td className="py-3 px-3 font-semibold text-foreground sticky left-0 bg-surface z-10 shadow-[1px_0_0_0_var(--color-border)]">Adjudicate</td>
                  <td className="py-3 px-3">
                    <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-surface-subtle border border-border text-foreground font-mono">
                      Nemotron 3 Ultra
                    </span>
                  </td>
                  <td className="py-3 px-3 text-foreground-muted">
                    Hard cases, at most one per case
                  </td>
                  <td className="py-3 px-3 text-amber-500 font-medium whitespace-nowrap">
                    Price to verify
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          {/* Escalation ladder */}
          <div className="pt-3 border-t border-border">
            <span className="text-[11px] font-semibold text-foreground-muted block mb-2">
              Escalation
            </span>
            <div className="flex flex-wrap items-center gap-2 text-xs text-foreground-muted">
              <span className="px-2 py-0.5 rounded-full bg-brand/10 border border-brand/30 text-brand-text-light dark:text-brand font-bold font-mono">
                Nano
              </span>
              <span>→ if invalid or unverified →</span>
              <span className="px-2 py-0.5 rounded-full bg-brand/10 border border-brand/30 text-brand-text-light dark:text-brand font-bold font-mono">
                Super
              </span>
              <span>→ if unsettled →</span>
              <span className="px-2 py-0.5 rounded-full bg-brand/10 border border-brand/30 text-brand-text-light dark:text-brand font-bold font-mono">
                Ultra
              </span>
              <span>→ else mark unverified</span>
            </div>
          </div>
        </Card>

        {/* Top Right: Limits (5 cols) */}
        <Card id="section-limits" className="lg:col-span-5 p-5 space-y-4">
          <h2 className="text-sm font-bold font-heading text-foreground">
            Limits
          </h2>

          <div>
            <label className="text-[11px] font-semibold text-foreground-muted block mb-1.5">
              Per-case cap
            </label>
            <input
              type="text"
              data-testid="input-per-case-cap"
              value={perCaseCap}
              onChange={(e) => setPerCaseCap(e.target.value)}
              className="w-full h-9 px-3 text-xs rounded-lg bg-surface-subtle border border-border text-foreground font-mono focus:border-brand focus:outline-hidden"
            />
          </div>

          <div>
            <label className="text-[11px] font-semibold text-foreground-muted block mb-1.5">
              Daily cap
            </label>
            <input
              type="text"
              data-testid="input-daily-cap"
              value={dailyCap}
              onChange={(e) => setDailyCap(e.target.value)}
              className="w-full h-9 px-3 text-xs rounded-lg bg-surface-subtle border border-border text-foreground font-mono focus:border-brand focus:outline-hidden"
            />
          </div>

          <div>
            <label className="text-[11px] font-semibold text-foreground-muted block mb-1.5">
              Max parallel model calls
            </label>
            <input
              type="text"
              data-testid="input-parallel-calls"
              value={parallelCalls}
              onChange={(e) => setParallelCalls(e.target.value)}
              className="w-full h-9 px-3 text-xs rounded-lg bg-surface-subtle border border-border text-foreground font-mono focus:border-brand focus:outline-hidden"
            />
          </div>

          <Button
            variant="secondary"
            size="sm"
            data-testid="save-limits-btn"
            onClick={handleSave}
            className="rounded-lg text-xs h-9"
          >
            {saved ? 'Limits saved ✓' : 'Save limits'}
          </Button>
        </Card>

        {/* Bottom Left: Statutory settings (7 cols) */}
        <Card id="section-statutory-settings" className="lg:col-span-7 p-5 space-y-4">
          <h2 className="text-sm font-bold font-heading text-foreground">
            Statutory settings
          </h2>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-border text-foreground-muted font-medium">
                  <th className="py-2.5 px-2">Setting</th>
                  <th className="py-2.5 px-2">Value</th>
                  <th className="py-2.5 px-2">Last checked</th>
                  <th className="py-2.5 px-2">Legal review</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                <tr className="hover:bg-surface-subtle/50 transition-colors">
                  <td className="py-3 px-2 font-medium text-foreground">Agreed payment period cap</td>
                  <td className="py-3 px-2 font-semibold text-foreground">45 days</td>
                  <td className="py-3 px-2 text-foreground-muted font-mono">12 Oct 2026</td>
                  <td className="py-3 px-2">
                    <span className="px-2.5 py-0.5 rounded-full text-xs font-medium border border-brand/40 text-brand bg-brand/10">
                      Verified
                    </span>
                  </td>
                </tr>
                <tr className="hover:bg-surface-subtle/50 transition-colors">
                  <td className="py-3 px-2 font-medium text-foreground">Default when no written agreement</td>
                  <td className="py-3 px-2 font-semibold text-foreground">15 days</td>
                  <td className="py-3 px-2 text-foreground-muted font-mono">12 Oct 2026</td>
                  <td className="py-3 px-2">
                    <span className="px-2.5 py-0.5 rounded-full text-xs font-medium border border-brand/40 text-brand bg-brand/10">
                      Verified
                    </span>
                  </td>
                </tr>
                <tr className="hover:bg-surface-subtle/50 transition-colors">
                  <td className="py-3 px-2 font-medium text-foreground">Interest basis</td>
                  <td className="py-3 px-2 font-semibold text-foreground">3 × bank rate, monthly rests</td>
                  <td className="py-3 px-2 text-foreground-muted font-mono">12 Oct 2026</td>
                  <td className="py-3 px-2">
                    <span className="px-2.5 py-0.5 rounded-full text-xs font-medium border border-brand/40 text-brand bg-brand/10">
                      Verified
                    </span>
                  </td>
                </tr>
                <tr className="hover:bg-surface-subtle/50 transition-colors">
                  <td className="py-3 px-2 font-medium text-foreground">Amendment Bill 2026</td>
                  <td className="py-3 px-2 text-foreground">Not in force</td>
                  <td className="py-3 px-2 text-foreground-muted font-mono">12 Oct 2026</td>
                  <td className="py-3 px-2">
                    <span className="px-2.5 py-0.5 rounded-full text-xs font-medium border border-border text-foreground-muted">
                      Tracked
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </Card>

        {/* Bottom Right: Limitations (5 cols) */}
        <Card className="lg:col-span-5 p-5 flex flex-col justify-between">
          <div>
            <h2 className="text-sm font-bold font-heading text-foreground mb-3">
              Limitations
            </h2>
            <p className="text-xs text-foreground-muted leading-relaxed">
              Not legal advice; no amount is stated as legally owed. Synthetic data only. Estimates are provisional. Marathi is experimental. Nothing is sent or filed automatically.
            </p>
          </div>
          <div className="text-[11px] text-foreground-muted/60 pt-4 border-t border-border mt-4">
            Rules R-7, R-18, R-52 & Appendix B compliance
          </div>
        </Card>
      </div>
    </div>
  );
};
