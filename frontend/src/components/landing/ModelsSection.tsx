import React from 'react';
import { Cpu, ArrowRight } from 'lucide-react';

export const ModelsSection: React.FC = () => {
  return (
    <section id="models" className="py-20 md:py-28 bg-surface-subtle/40 dark:bg-[#0E110E] border-b border-border/60 scroll-mt-16">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-brand/10 border border-brand/30 text-brand text-xs font-bold uppercase tracking-wider mb-4">
            <Cpu className="w-3.5 h-3.5" />
            <span>Compute Architecture</span>
          </div>
          <h2 className="font-heading font-extrabold text-3xl sm:text-4xl lg:text-5xl text-foreground tracking-tight">
            The right model for each job.
          </h2>
          <p className="mt-4 text-base sm:text-lg text-foreground-muted">
            Rather than routing every simple invoice to a 405B behemoth, DuesPilot dispatches tasks through a tiered Nemotron routing architecture.
          </p>
        </div>

        {/* Tiered Routing Visual Cards */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-12">
          
          {/* Tier 1: Nano */}
          <div className="p-6 rounded-2xl bg-surface border border-border flex flex-col justify-between shadow-xs">
            <div>
              <div className="flex items-center justify-between mb-4">
                <span className="text-[11px] font-mono font-bold uppercase tracking-wider text-brand px-2.5 py-1 rounded bg-brand/10">
                  TIER 1 · FAST PARSER
                </span>
                <span className="text-xs font-mono text-foreground-subtle">&lt; 150 ms</span>
              </div>

              <h3 className="font-heading font-bold text-xl text-foreground">
                Nemotron-3-Nano
              </h3>
              <p className="mt-2 text-xs text-foreground-muted leading-relaxed">
                High-throughput optical tokenization, spatial bounding boxes, tabular line item extraction, and mathematical cross-checks.
              </p>

              <div className="mt-6 space-y-2 text-xs">
                <div className="text-[10px] font-mono text-foreground-subtle uppercase">Target Workloads</div>
                <ul className="space-y-1.5 text-foreground font-medium">
                  <li className="flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-brand" />
                    <span>Scanned PDF & image OCR</span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-brand" />
                    <span>Table grid reconstruction</span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-brand" />
                    <span>C3 arithmetic checksums</span>
                  </li>
                </ul>
              </div>
            </div>

            {/* Cost bar with "estimate" label */}
            <div className="mt-8 pt-4 border-t border-border">
              <div className="flex justify-between items-center text-xs font-mono mb-1.5">
                <span className="text-foreground-muted">Inference Cost:</span>
                <span className="text-brand font-bold">~$0.002 / doc</span>
              </div>
              <div className="w-full bg-surface-subtle h-2.5 rounded-full overflow-hidden border border-border">
                <div className="bg-brand h-full rounded-full" style={{ width: '12%' }} />
              </div>
              <div className="text-[10px] font-mono text-foreground-subtle mt-1 flex justify-between">
                <span>Lowest latency</span>
                <span className="text-amber-500 font-semibold">(estimate)</span>
              </div>
            </div>
          </div>

          {/* Tier 2: Super */}
          <div className="p-6 rounded-2xl bg-surface border-2 border-brand/30 shadow-md flex flex-col justify-between relative">
            <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-0.5 rounded-full bg-brand text-black font-mono font-extrabold text-[10px] uppercase">
              WORKHORSE
            </div>

            <div>
              <div className="flex items-center justify-between mb-4">
                <span className="text-[11px] font-mono font-bold uppercase tracking-wider text-brand px-2.5 py-1 rounded bg-brand/10">
                  TIER 2 · REASONER
                </span>
                <span className="text-xs font-mono text-foreground-subtle">~600 ms</span>
              </div>

              <h3 className="font-heading font-bold text-xl text-foreground">
                Nemotron-3-Super
              </h3>
              <p className="mt-2 text-xs text-foreground-muted leading-relaxed">
                Multi-document cross-alignment, Hindi/English bilingual dialogue comprehension, bank statement matching, and credit note deduction tracing.
              </p>

              <div className="mt-6 space-y-2 text-xs">
                <div className="text-[10px] font-mono text-foreground-subtle uppercase">Target Workloads</div>
                <ul className="space-y-1.5 text-foreground font-medium">
                  <li className="flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-brand" />
                    <span>Two-party PO ↔ Invoice matching</span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-brand" />
                    <span>WhatsApp Hindi/Hinglish chat intent</span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-brand" />
                    <span>C7 part-payment allocation</span>
                  </li>
                </ul>
              </div>
            </div>

            {/* Cost bar with "estimate" label */}
            <div className="mt-8 pt-4 border-t border-border">
              <div className="flex justify-between items-center text-xs font-mono mb-1.5">
                <span className="text-foreground-muted">Inference Cost:</span>
                <span className="text-brand font-bold">~$0.012 / doc</span>
              </div>
              <div className="w-full bg-surface-subtle h-2.5 rounded-full overflow-hidden border border-border">
                <div className="bg-brand h-full rounded-full" style={{ width: '38%' }} />
              </div>
              <div className="text-[10px] font-mono text-foreground-subtle mt-1 flex justify-between">
                <span>Balanced throughput</span>
                <span className="text-amber-500 font-semibold">(estimate)</span>
              </div>
            </div>
          </div>

          {/* Tier 3: Ultra */}
          <div className="p-6 rounded-2xl bg-surface border border-border flex flex-col justify-between shadow-xs">
            <div>
              <div className="flex items-center justify-between mb-4">
                <span className="text-[11px] font-mono font-bold uppercase tracking-wider text-purple-400 px-2.5 py-1 rounded bg-purple-500/10">
                  TIER 3 · STATUTORY
                </span>
                <span className="text-xs font-mono text-foreground-subtle">~1,800 ms</span>
              </div>

              <h3 className="font-heading font-bold text-xl text-foreground">
                Nemotron-3-Ultra
              </h3>
              <p className="mt-2 text-xs text-foreground-muted leading-relaxed">
                High-stakes statutory gate assessment, MSMEDA Section 16 compounding interest audits, and formal conciliation notice generation.
              </p>

              <div className="mt-6 space-y-2 text-xs">
                <div className="text-[10px] font-mono text-foreground-subtle uppercase">Target Workloads</div>
                <ul className="space-y-1.5 text-foreground font-medium">
                  <li className="flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-purple-400" />
                    <span>Statutory eligibility gates (A to E)</span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-purple-400" />
                    <span>Council claim dossier synthesis</span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-purple-400" />
                    <span>Dispute mitigation strategy</span>
                  </li>
                </ul>
              </div>
            </div>

            {/* Cost bar with "estimate" label */}
            <div className="mt-8 pt-4 border-t border-border">
              <div className="flex justify-between items-center text-xs font-mono mb-1.5">
                <span className="text-foreground-muted">Inference Cost:</span>
                <span className="text-purple-400 font-bold">~$0.045 / doc</span>
              </div>
              <div className="w-full bg-surface-subtle h-2.5 rounded-full overflow-hidden border border-border">
                <div className="bg-purple-500 h-full rounded-full" style={{ width: '85%' }} />
              </div>
              <div className="text-[10px] font-mono text-foreground-subtle mt-1 flex justify-between">
                <span>Maximum reasoning depth</span>
                <span className="text-amber-500 font-semibold">(estimate)</span>
              </div>
            </div>
          </div>

        </div>

        {/* Tiered Routing Workflow Diagram */}
        <div className="p-6 sm:p-8 rounded-2xl bg-surface border border-border shadow-sm">
          <div className="flex flex-col md:flex-row items-center justify-between gap-6 text-xs font-mono">
            
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-brand/10 border border-brand/30 text-brand flex items-center justify-center font-bold">
                IN
              </div>
              <div>
                <div className="font-bold text-foreground">Inbound Document</div>
                <div className="text-foreground-muted">Invoice, Challan, Bank UTR</div>
              </div>
            </div>

            <ArrowRight className="w-5 h-5 text-brand hidden md:block shrink-0" />

            <div className="flex items-center gap-3 p-3 rounded-xl bg-surface-subtle border border-border">
              <div className="w-8 h-8 rounded-lg bg-brand/20 text-brand flex items-center justify-center font-bold text-[10px]">
                NANO
              </div>
              <div>
                <div className="font-bold text-foreground">Extract & BBox</div>
                <div className="text-foreground-subtle">&lt;150ms · ~$0.002 (estimate)</div>
              </div>
            </div>

            <ArrowRight className="w-5 h-5 text-brand hidden md:block shrink-0" />

            <div className="flex items-center gap-3 p-3 rounded-xl bg-surface-subtle border border-border">
              <div className="w-8 h-8 rounded-lg bg-brand/20 text-brand flex items-center justify-center font-bold text-[10px]">
                SUPER
              </div>
              <div>
                <div className="font-bold text-foreground">Align & Cross-Match</div>
                <div className="text-foreground-subtle">Discrepancy detection</div>
              </div>
            </div>

            <ArrowRight className="w-5 h-5 text-purple-400 hidden md:block shrink-0" />

            <div className="flex items-center gap-3 p-3 rounded-xl bg-purple-500/10 border border-purple-500/30">
              <div className="w-8 h-8 rounded-lg bg-purple-500/20 text-purple-400 flex items-center justify-center font-bold text-[10px]">
                ULTRA
              </div>
              <div>
                <div className="font-bold text-foreground">Statutory Gate & Action</div>
                <div className="text-foreground-subtle">Only if ambiguous or legal</div>
              </div>
            </div>

          </div>

          <div className="mt-4 pt-4 border-t border-border flex items-center justify-between text-[11px] text-foreground-subtle">
            <span>* Cost estimates based on token pricing benchmarks on Nebius AI Cloud. Real benchmarks tracked live in the app.</span>
            <span className="font-mono text-brand font-semibold">Tiered Routing = ~70% cost reduction vs all-Ultra</span>
          </div>
        </div>

      </div>
    </section>
  );
};
