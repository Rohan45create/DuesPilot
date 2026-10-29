import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  FileCheck2,
  AlertTriangle,
  Scale,
  Sparkles,
  Layers,
  FileSearch,
  Languages,
  UserCheck
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

export const HeroExecutionRecord: React.FC = () => {
  const { user } = useAuth();
  const [balance, setBalance] = useState(0);

  // Sync simulated balance count-up to ₹56,200 (runs with loop)
  useEffect(() => {
    let frameId: number;
    let startTime = performance.now();
    const duration = 12000; // 12 second loop

    const updateCounter = (now: number) => {
      const elapsed = (now - startTime) % duration;
      // The balance appears in step 4 / balance reveal (~2.8s to ~11s of the 12s cycle)
      if (elapsed < 2400) {
        setBalance(0);
      } else if (elapsed < 3800) {
        const progress = Math.min(1, (elapsed - 2400) / 1400);
        // Eased count up to 56200
        const current = Math.floor(56200 * (1 - Math.pow(1 - progress, 3)));
        setBalance(current);
      } else if (elapsed > 11200) {
        setBalance(0);
      } else {
        setBalance(56200);
      }
      frameId = requestAnimationFrame(updateCounter);
    };

    frameId = requestAnimationFrame(updateCounter);
    return () => cancelAnimationFrame(frameId);
  }, []);

  const scrollToHowItWorks = (e: React.MouseEvent<HTMLAnchorElement>) => {
    e.preventDefault();
    const el = document.getElementById('how-it-works');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <section className="relative overflow-hidden pt-8 pb-16 md:pt-16 md:pb-24 border-b border-border/60">
      {/* Background ambient radial gradients */}
      <div
        className="pointer-events-none absolute -top-40 left-1/2 -translate-x-1/2 w-[600px] h-[500px] bg-brand/10 dark:bg-brand/15 blur-[120px] rounded-full"
        aria-hidden="true"
      />

      {/* Floating feature chips drifting slowly around hero (hidden on mobile phones) */}
      <div className="hidden lg:block absolute inset-0 pointer-events-none max-w-7xl mx-auto px-6 overflow-hidden">
        {/* Chip 1: Reconciliation - top left */}
        <div className="absolute top-16 left-6 chip-drift-1">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-surface/90 border border-brand/30 backdrop-blur-md shadow-xs text-xs font-semibold text-foreground">
            <Layers className="w-3.5 h-3.5 text-brand" />
            <span>Reconciliation</span>
          </div>
        </div>

        {/* Chip 2: Conflicts - bottom left */}
        <div className="absolute bottom-20 left-12 chip-drift-2">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-surface/90 border border-amber-500/30 backdrop-blur-md shadow-xs text-xs font-semibold text-foreground">
            <AlertTriangle className="w-3.5 h-3.5 text-amber-500" />
            <span>Conflicts</span>
          </div>
        </div>

        {/* Chip 3: Eligibility gates - top center-right */}
        <div className="absolute top-12 right-1/3 chip-drift-3">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-surface/90 border border-brand/30 backdrop-blur-md shadow-xs text-xs font-semibold text-foreground">
            <Scale className="w-3.5 h-3.5 text-brand" />
            <span>Eligibility gates</span>
          </div>
        </div>

        {/* Chip 4: Proof viewer - top right */}
        <div className="absolute top-20 right-8 chip-drift-1">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-surface/90 border border-brand/30 backdrop-blur-md shadow-xs text-xs font-semibold text-foreground">
            <FileSearch className="w-3.5 h-3.5 text-brand" />
            <span>Proof viewer</span>
          </div>
        </div>

        {/* Chip 5: Hindi + English - bottom center */}
        <div className="absolute bottom-16 right-1/2 chip-drift-2">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-surface/90 border border-brand/30 backdrop-blur-md shadow-xs text-xs font-semibold text-foreground">
            <Languages className="w-3.5 h-3.5 text-brand" />
            <span>Hindi + English</span>
          </div>
        </div>

        {/* Chip 6: Human approval - bottom right */}
        <div className="absolute bottom-12 right-6 chip-drift-3">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-surface/90 border border-brand/30 backdrop-blur-md shadow-xs text-xs font-semibold text-foreground">
            <UserCheck className="w-3.5 h-3.5 text-brand" />
            <span>Human approval</span>
          </div>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-8 items-center">
          
          {/* Left Hero Column */}
          <div className="lg:col-span-6 flex flex-col items-start text-left">
            {/* Small eyebrow */}
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-brand/10 border border-brand/30 text-brand text-xs font-bold uppercase tracking-wider mb-5">
              <Sparkles className="w-3.5 h-3.5" />
              <span>Evidence-first receivables reconciliation</span>
            </div>

            {/* Headline */}
            <h1 className="font-heading font-extrabold text-4xl sm:text-5xl lg:text-[3.5rem] tracking-tight text-foreground leading-[1.1]">
              Know what you&apos;re really owed.
              <span className="block text-foreground-muted font-bold text-2xl sm:text-3xl lg:text-4xl mt-2 tracking-tight">
                Backed by the original proof.
              </span>
            </h1>

            {/* Short descriptive paragraph */}
            <p className="mt-6 text-base sm:text-lg text-foreground-muted max-w-xl leading-relaxed">
              DuesPilot turns conflicting invoices, POs, payments and buyer messages into a verified balance, a source-linked next action, and a human decision.
            </p>

            {/* CTA Buttons */}
            <div className="mt-8 flex flex-wrap items-center gap-4 w-full sm:w-auto">
              <Link
                to={user ? "/app" : "/signin"}
                data-testid="hero-cta-button"
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 bg-brand hover:bg-brand-hover text-black font-bold text-base px-6 py-3.5 rounded-lg transition-all shadow-md hover:shadow-brand/20 group"
              >
                <span>Try the demo</span>
                <ArrowRight className="w-4 h-4 transition-transform group-hover:translate-x-1" />
              </Link>
              <a
                href="#how-it-works"
                data-testid="hero-see-how-btn"
                onClick={scrollToHowItWorks}
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 border border-border hover:border-brand/60 bg-surface/50 hover:bg-surface text-foreground font-semibold text-base px-6 py-3.5 rounded-lg transition-all"
              >
                <span>See how it works</span>
              </a>
            </div>

            {/* Trust row */}
            <div className="mt-8 pt-6 border-t border-border/80 w-full flex items-center text-xs sm:text-sm text-foreground-subtle font-medium">
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-brand inline-block" />
                <span>Synthetic demo data</span>
                <span className="text-border">·</span>
                <span>Human approval</span>
                <span className="text-border">·</span>
                <span>Hindi + English</span>
              </div>
            </div>
          </div>

          {/* Right Hero Column: Glowing green-to-black gradient frame holding Execution Record */}
          <div className="lg:col-span-6 w-full flex justify-center">
            <div className="w-full max-w-xl relative">
              
              {/* Outer Glowing Gradient Frame */}
              <div className="hero-glow-frame relative p-[2px] rounded-2xl bg-gradient-to-b from-brand/50 via-brand/20 to-black/80 dark:to-black shadow-2xl transition-all">
                
                {/* Inner Execution Record Card */}
                <div
                  data-testid="hero-execution-record"
                  className="execution-card rounded-[14px] bg-surface dark:bg-[#111411] border border-border/70 p-5 sm:p-6 text-foreground shadow-inner relative overflow-hidden"
                  tabIndex={0}
                  aria-label="Simulated Execution Record for invoice reconciliation"
                >
                  {/* Card Header Bar */}
                  <div className="flex items-center justify-between pb-4 mb-4 border-b border-border/60">
                    <div className="flex items-center gap-2.5">
                      <div className="w-3 h-3 rounded-full bg-brand/80 animate-pulse" />
                      <span className="font-mono text-xs font-bold tracking-wider text-brand uppercase">
                        EXECUTION RECORD · DP-0142
                      </span>
                    </div>
                    <span className="text-[11px] font-mono text-foreground-subtle">
                      AUTONOMOUS AUDIT RUN
                    </span>
                  </div>

                  {/* Staggered Line Items */}
                  <div className="space-y-4">
                    
                    {/* Step 01: The request */}
                    <div className="exec-anim-1 rounded-lg bg-surface-subtle/80 dark:bg-[#161A16] p-3.5 border border-border/60">
                      <div className="flex items-center gap-2 text-xs font-mono text-foreground-muted mb-1">
                        <span className="w-5 h-5 rounded bg-brand/20 text-brand flex items-center justify-center font-bold text-[10px]">
                          01
                        </span>
                        <span className="font-semibold text-foreground uppercase tracking-wider">
                          The request
                        </span>
                      </div>
                      <p className="text-xs sm:text-sm font-medium text-foreground pl-7">
                        &ldquo;What does Kestrel Industrial actually owe on INV-2026-0142?&rdquo;
                      </p>
                    </div>

                    {/* Step 02: Establish the facts */}
                    <div className="exec-anim-2 rounded-lg bg-surface-subtle/80 dark:bg-[#161A16] p-3.5 border border-border/60">
                      <div className="flex items-center gap-2 text-xs font-mono text-foreground-muted mb-1.5">
                        <span className="w-5 h-5 rounded bg-brand/20 text-brand flex items-center justify-center font-bold text-[10px]">
                          02
                        </span>
                        <span className="font-semibold text-foreground uppercase tracking-wider">
                          Establish the facts
                        </span>
                      </div>
                      <div className="pl-7 space-y-1.5 text-xs font-mono">
                        <div className="flex items-center justify-between text-foreground">
                          <span className="flex items-center gap-1.5">
                            <FileCheck2 className="w-3.5 h-3.5 text-brand" />
                            <span>Invoice INV-2026-0142</span>
                          </span>
                          <span className="font-bold text-brand">₹1,18,000 verified</span>
                        </div>
                        <div className="flex items-center justify-between text-foreground">
                          <span className="flex items-center gap-1.5">
                            <FileCheck2 className="w-3.5 h-3.5 text-brand" />
                            <span>Part payment (UTR-9844)</span>
                          </span>
                          <span className="font-bold text-brand">₹50,000 verified</span>
                        </div>
                        <div className="flex items-center justify-between text-foreground">
                          <span className="flex items-center gap-1.5">
                            <FileCheck2 className="w-3.5 h-3.5 text-brand" />
                            <span>Credit note (CN-004)</span>
                          </span>
                          <span className="font-bold text-brand">₹11,800 verified</span>
                        </div>
                        <div className="flex items-center justify-between text-amber-500/90 pt-1 border-t border-border/40">
                          <span className="flex items-center gap-1.5 text-[11px]">
                            <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                            <span>Buyer message claim (₹20,000)</span>
                          </span>
                          <span className="text-[11px] font-semibold bg-amber-500/10 px-2 py-0.5 rounded text-amber-500">
                            Unverified · Excluded
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Step 03: Apply the rules */}
                    <div className="exec-anim-3 rounded-lg bg-surface-subtle/80 dark:bg-[#161A16] p-3.5 border border-border/60">
                      <div className="flex items-center gap-2 text-xs font-mono text-foreground-muted mb-1">
                        <span className="w-5 h-5 rounded bg-brand/20 text-brand flex items-center justify-center font-bold text-[10px]">
                          03
                        </span>
                        <span className="font-semibold text-foreground uppercase tracking-wider">
                          Apply the rules
                        </span>
                      </div>
                      <div className="pl-7 flex items-center justify-between text-xs">
                        <span className="text-foreground-muted">MSMEDA Sec 16 Interest Gate D:</span>
                        <span className="font-mono text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded text-[11px] font-semibold">
                          Abstains: &ldquo;Interest not computed — bank rate unconfirmed&rdquo;
                        </span>
                      </div>
                    </div>

                    {/* Step 04: Next action */}
                    <div className="exec-anim-4 rounded-lg bg-surface-subtle/80 dark:bg-[#161A16] p-3.5 border border-border/60">
                      <div className="flex items-center gap-2 text-xs font-mono text-foreground-muted mb-1">
                        <span className="w-5 h-5 rounded bg-brand/20 text-brand flex items-center justify-center font-bold text-[10px]">
                          04
                        </span>
                        <span className="font-semibold text-foreground uppercase tracking-wider">
                          Next action
                        </span>
                      </div>
                      <div className="pl-7 flex items-center justify-between text-xs">
                        <span className="text-foreground font-medium">
                          &ldquo;Request delivery proof — draft ready for approval&rdquo;
                        </span>
                        <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-brand/20 text-brand shrink-0">
                          Awaiting Click
                        </span>
                      </div>
                    </div>

                    {/* Verified Net Balance Counter Box */}
                    <div className="exec-anim-balance rounded-xl bg-brand/10 border border-brand/40 p-4 flex items-center justify-between shadow-xs">
                      <div>
                        <div className="text-[11px] font-mono uppercase tracking-wider text-foreground-muted">
                          Verified Balance Payable
                        </div>
                        <div className="text-xs text-foreground-subtle">
                          Excludes unverified chat claims & unconfirmed interest
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="text-2xl sm:text-3xl font-heading font-extrabold text-brand tabular-nums tracking-tight">
                          ₹{balance.toLocaleString('en-IN')}
                        </div>
                        <div className="text-[10px] font-mono text-brand/80">
                          RECONCILED · 100% PROVENANCE
                        </div>
                      </div>
                    </div>

                  </div>

                  {/* Card Footer notice */}
                  <div className="mt-4 pt-3 border-t border-border/50 flex items-center justify-between text-[11px] text-foreground-subtle">
                    <span>Staggered execution loops ~12s</span>
                    <span className="text-brand font-medium">Hover to pause</span>
                  </div>

                </div>
              </div>
            </div>
          </div>

        </div>
      </div>
    </section>
  );
};
