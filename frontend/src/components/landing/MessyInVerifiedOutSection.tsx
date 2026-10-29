import React from 'react';
import {
  FileText,
  FileCheck,
  CreditCard,
  FileMinus,
  MessageSquare,
  Cpu,
  CheckCircle2,
  ShieldCheck,
  Search,
  Sparkles
} from 'lucide-react';

export const MessyInVerifiedOutSection: React.FC = () => {
  return (
    <section id="how-it-works" className="py-20 md:py-28 border-b border-border/60 relative scroll-mt-16">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-brand/10 border border-brand/30 text-brand text-xs font-bold uppercase tracking-wider mb-4">
            <Cpu className="w-3.5 h-3.5" />
            <span>End-to-End Pipeline</span>
          </div>
          <h2 className="font-heading font-extrabold text-3xl sm:text-4xl lg:text-5xl text-foreground tracking-tight">
            Messy in, verified out.
          </h2>
          <p className="mt-4 text-base sm:text-lg text-foreground-muted">
            Invoices, POs, bank receipts, credit notes, and buyer messages enter the pipeline.
            Only cross-verified facts reach the balance.
          </p>
        </div>

        {/* Visual Pipeline Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
          
          {/* Column 1: Messy Documents Input (5 source artifacts) */}
          <div className="lg:col-span-4 space-y-3">
            <div className="text-xs font-mono font-bold uppercase tracking-wider text-foreground-subtle mb-3 flex items-center justify-between">
              <span>01 · Inbound Paperwork</span>
              <span className="text-amber-500 font-semibold">5 Disjoint Sources</span>
            </div>

            {/* Doc 1: PO */}
            <div className="p-3.5 rounded-xl bg-surface border border-border flex items-center justify-between hover:border-brand/50 transition-colors shadow-xs">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-blue-500/10 text-blue-500 flex items-center justify-center">
                  <FileText className="w-4 h-4" />
                </div>
                <div>
                  <div className="text-xs font-bold text-foreground">Purchase Order PO-4402</div>
                  <div className="text-[11px] text-foreground-muted">Quantity 100 @ ₹1,000 + 18% GST</div>
                </div>
              </div>
              <span className="text-xs font-mono font-semibold text-foreground">₹1,18,000</span>
            </div>

            {/* Doc 2: Invoice */}
            <div className="p-3.5 rounded-xl bg-surface border border-border flex items-center justify-between hover:border-brand/50 transition-colors shadow-xs">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-brand/10 text-brand flex items-center justify-center">
                  <FileCheck className="w-4 h-4" />
                </div>
                <div>
                  <div className="text-xs font-bold text-foreground">Tax Invoice INV-0142</div>
                  <div className="text-[11px] text-foreground-muted">Delivered · HSN 8481 · Due 45d</div>
                </div>
              </div>
              <span className="text-xs font-mono font-semibold text-foreground">₹1,18,000</span>
            </div>

            {/* Doc 3: Bank Confirmation */}
            <div className="p-3.5 rounded-xl bg-surface border border-border flex items-center justify-between hover:border-brand/50 transition-colors shadow-xs">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-emerald-500/10 text-emerald-500 flex items-center justify-center">
                  <CreditCard className="w-4 h-4" />
                </div>
                <div>
                  <div className="text-xs font-bold text-foreground">Bank UTR-991208</div>
                  <div className="text-[11px] text-foreground-muted">NEFT Inward Credit · Confirmed</div>
                </div>
              </div>
              <span className="text-xs font-mono font-semibold text-emerald-600 dark:text-emerald-400">-₹50,000</span>
            </div>

            {/* Doc 4: Credit Note */}
            <div className="p-3.5 rounded-xl bg-surface border border-border flex items-center justify-between hover:border-brand/50 transition-colors shadow-xs">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-purple-500/10 text-purple-500 flex items-center justify-center">
                  <FileMinus className="w-4 h-4" />
                </div>
                <div>
                  <div className="text-xs font-bold text-foreground">Credit Note CN-004</div>
                  <div className="text-[11px] text-foreground-muted">Return of 10 damaged units</div>
                </div>
              </div>
              <span className="text-xs font-mono font-semibold text-purple-600 dark:text-purple-400">-₹11,800</span>
            </div>

            {/* Doc 5: WhatsApp Chat */}
            <div className="p-3.5 rounded-xl bg-surface border border-amber-500/30 bg-amber-500/[0.02] flex items-center justify-between shadow-xs">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-amber-500/10 text-amber-500 flex items-center justify-center">
                  <MessageSquare className="w-4 h-4" />
                </div>
                <div>
                  <div className="text-xs font-bold text-foreground">WhatsApp: &ldquo;₹20k sent&rdquo;</div>
                  <div className="text-[11px] text-amber-500 font-medium">Unverified buyer assertion</div>
                </div>
              </div>
              <span className="text-[11px] font-mono font-bold text-amber-500 bg-amber-500/10 px-2 py-0.5 rounded">
                EXCLUDED
              </span>
            </div>
          </div>

          {/* Column 2: The Core Pipeline Transformation */}
          <div className="lg:col-span-4 flex flex-col items-center justify-center py-6 lg:py-0 relative">
            
            {/* SVG Connecting Line Drawing */}
            <div className="w-full h-12 flex items-center justify-center my-2">
              <svg className="w-full h-12 stroke-brand" fill="none" viewBox="0 0 300 48">
                <path
                  d="M10 24 L290 24"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                  className="svg-pipeline-line"
                />
              </svg>
            </div>

            {/* Pipeline Stage Cards */}
            <div className="w-full space-y-3">
              <div className="p-4 rounded-xl bg-surface-subtle dark:bg-[#151915] border border-border">
                <div className="flex items-center gap-2 text-xs font-bold text-brand uppercase tracking-wider mb-1">
                  <Search className="w-3.5 h-3.5" />
                  <span>1. Extract (Nemotron Nano)</span>
                </div>
                <p className="text-xs text-foreground-muted">
                  BBoxes & coordinates for every number; table parsing; multilingual OCR.
                </p>
              </div>

              <div className="p-4 rounded-xl bg-surface-subtle dark:bg-[#151915] border border-border">
                <div className="flex items-center gap-2 text-xs font-bold text-brand uppercase tracking-wider mb-1">
                  <ShieldCheck className="w-3.5 h-3.5" />
                  <span>2. Verify (Nemotron Super)</span>
                </div>
                <p className="text-xs text-foreground-muted">
                  Two-party alignment, bank UTR settlement proof, GST rate compliance.
                </p>
              </div>

              <div className="p-4 rounded-xl bg-surface-subtle dark:bg-[#151915] border border-border">
                <div className="flex items-center gap-2 text-xs font-bold text-brand uppercase tracking-wider mb-1">
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>3. Reconcile (Nemotron Ultra)</span>
                </div>
                <p className="text-xs text-foreground-muted">
                  MSMEDA eligibility gates, unverified claims quarantine, next action draft.
                </p>
              </div>
            </div>

            <div className="w-full h-12 flex items-center justify-center my-2">
              <svg className="w-full h-12 stroke-brand" fill="none" viewBox="0 0 300 48">
                <path
                  d="M10 24 L290 24"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                  className="svg-pipeline-line"
                />
              </svg>
            </div>
          </div>

          {/* Column 3: The Verified Output Card */}
          <div className="lg:col-span-4">
            <div className="text-xs font-mono font-bold uppercase tracking-wider text-brand mb-3 flex items-center justify-between">
              <span>02 · Audit-Ready Output</span>
              <span className="text-xs font-semibold text-brand">100% Provenance</span>
            </div>

            <div className="rounded-2xl bg-surface dark:bg-[#121612] border-2 border-brand/50 p-6 shadow-xl relative overflow-hidden">
              <div className="absolute top-0 right-0 px-3 py-1 bg-brand text-black font-mono font-extrabold text-[10px] uppercase rounded-bl-lg">
                VERIFIED LEDGER
              </div>

              <div className="space-y-4">
                <div>
                  <div className="text-xs font-mono text-foreground-subtle uppercase">Target Invoice</div>
                  <div className="font-heading font-bold text-lg text-foreground">Kestrel Industrial · INV-0142</div>
                </div>

                <div className="pt-3 border-t border-border/80 space-y-2 text-xs font-mono">
                  <div className="flex justify-between text-foreground">
                    <span>Gross Invoice:</span>
                    <span className="font-bold">₹1,18,000.00</span>
                  </div>
                  <div className="flex justify-between text-emerald-600 dark:text-emerald-400">
                    <span>Bank UTR-991208:</span>
                    <span className="font-bold">-₹50,000.00</span>
                  </div>
                  <div className="flex justify-between text-purple-600 dark:text-purple-400">
                    <span>Credit Note CN-004:</span>
                    <span className="font-bold">-₹11,800.00</span>
                  </div>
                  <div className="flex justify-between text-foreground-subtle italic">
                    <span>WhatsApp claim (₹20,000):</span>
                    <span className="text-amber-500 font-semibold">[Excluded: No UTR]</span>
                  </div>
                </div>

                {/* Final Net Verified Amount */}
                <div className="p-4 rounded-xl bg-brand/10 border border-brand/40 text-center">
                  <div className="text-xs font-mono text-foreground-muted uppercase">Verified Balance Payable</div>
                  <div className="text-3xl font-heading font-extrabold text-brand tabular-nums mt-1">
                    ₹56,200.00
                  </div>
                  <div className="text-[11px] text-foreground-subtle mt-1 flex items-center justify-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5 text-brand" />
                    <span>Cross-referenced against 3 verified records</span>
                  </div>
                </div>

                {/* Recommended Next Action */}
                <div className="p-3 rounded-lg bg-surface-subtle dark:bg-[#171C17] border border-border text-xs">
                  <div className="text-[10px] font-mono uppercase text-brand font-bold mb-1">
                    Next Action Prepared
                  </div>
                  <p className="text-foreground font-medium">
                    &ldquo;Request delivery proof for remaining ₹56,200 — draft ready for approval&rdquo;
                  </p>
                </div>
              </div>

            </div>
          </div>

        </div>

      </div>
    </section>
  );
};
