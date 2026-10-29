import React, { useState } from 'react';
import { AlertTriangle } from 'lucide-react';

interface ConflictItem {
  code: string;
  category: 'pricing' | 'statutory' | 'payments';
  title: string;
  description: string;
  scenario: string;
  handling: string;
  badge: string;
  badgeColor: string;
}

const CONFLICTS: ConflictItem[] = [
  {
    code: 'C1',
    category: 'pricing',
    title: 'Price Mismatch',
    description: 'PO unit price contradicts the invoice billed rate.',
    scenario: 'PO specifies ₹1,000/pc; invoice bills ₹1,100/pc without an authorized amendment.',
    handling: 'Flags discrepancy, defaults to authorized PO rate, and requests rate amendment proof.',
    badge: 'Evidence requested',
    badgeColor: 'border-amber-500/30 text-amber-500 bg-amber-500/10',
  },
  {
    code: 'C2',
    category: 'pricing',
    title: 'Quantity Mismatch',
    description: 'Billed units exceed signed delivery challan receipt.',
    scenario: 'Invoice bills 100 units; goods receipt note acknowledges only 80 units received.',
    handling: 'Quarantines unacknowledged 20 units; reconciles balance on confirmed 80 units only.',
    badge: 'Excluded from balance',
    badgeColor: 'border-red-500/30 text-red-500 bg-red-500/10',
  },
  {
    code: 'C3',
    category: 'pricing',
    title: 'Arithmetic Discrepancy',
    description: 'Line item multiplications do not sum to invoice total.',
    scenario: 'Subtotal ₹95,000 + GST ₹17,100 printed as ₹1,18,000 (₹5,900 phantom calculation gap).',
    handling: 'Recomputes raw ledger sum; marks arithmetic delta as unverified pending correction.',
    badge: 'Auto-flagged',
    badgeColor: 'border-brand/40 text-brand bg-brand/10',
  },
  {
    code: 'C4',
    category: 'statutory',
    title: 'GST / Tax Rate Conflict',
    description: 'Charged tax percentage conflicts with statutory HSN schedule.',
    scenario: 'HSN 8481 CNC parts billed at 18% GST when buyer claims 12% concessional rate.',
    handling: 'Validates against HSN Master; freezes disputed tax component in escrow ledger.',
    badge: 'Statutory hold',
    badgeColor: 'border-blue-500/30 text-blue-500 bg-blue-500/10',
  },
  {
    code: 'C5',
    category: 'statutory',
    title: 'MSMEDA Sec 15 Payment Terms Violation',
    description: 'Buyer agreement imposes payment terms beyond the 45-day statutory cap.',
    scenario: 'Buyer contract stipulates 90-day credit; MSME Development Act overrides contracts beyond 45 days.',
    handling: 'Enforces statutory Section 15 45-day milestone; marks day 46+ as overdue for interest.',
    badge: 'Statutory Override',
    badgeColor: 'border-brand/40 text-brand bg-brand/10',
  },
  {
    code: 'C6',
    category: 'pricing',
    title: 'Disputed Delivery Date',
    description: 'Day of deemed acceptance unclear or delivery proof missing.',
    scenario: 'Invoice dated 1-Jan; buyer claims dispatch arrived 28-Jan with no counter-signed POD.',
    handling: 'Abstains from interest clock initiation until signed transporter lorry receipt is linked.',
    badge: 'Gate D Abstains',
    badgeColor: 'border-amber-500/30 text-amber-500 bg-amber-500/10',
  },
  {
    code: 'C7',
    category: 'payments',
    title: 'Unallocated Part-Payment',
    description: 'Inward bank UTR does not reference target invoice number.',
    scenario: 'Buyer sends ₹50,000 NEFT; supplier has 3 pending invoices totaling ₹2,10,000.',
    handling: 'Applies FIFO ledger allocation with human confirmation gate before balance locking.',
    badge: 'Human confirmation',
    badgeColor: 'border-purple-500/30 text-purple-500 bg-purple-500/10',
  },
  {
    code: 'C8',
    category: 'payments',
    title: 'Unmatched Credit Note Adjustment',
    description: 'Debit or credit note unlinked to verified return or rate adjustment.',
    scenario: 'Buyer debits ₹11,800 for rework; supplier has not acknowledged debit note.',
    handling: 'Holds credit in unverified state until bilateral confirmation or exclusion.',
    badge: 'Pending bilateral match',
    badgeColor: 'border-amber-500/30 text-amber-500 bg-amber-500/10',
  },
  {
    code: 'C9',
    category: 'payments',
    title: 'Disputed Quality / Defect Deduction',
    description: 'Unilateral deductions without Joint Inspection Report within 15 days.',
    scenario: 'Buyer deducts 15% for surface blemishes 40 days after delivery with no timely objection.',
    handling: 'Enforces MSMEDA deemed acceptance window; flags deduction as legally unsupported.',
    badge: 'Statutory protection',
    badgeColor: 'border-brand/40 text-brand bg-brand/10',
  },
  {
    code: 'C10',
    category: 'payments',
    title: 'Duplicate Billing / Collision',
    description: 'Invoice number collision across multiple fiscal years or entities.',
    scenario: 'INV-0142 submitted twice with differing dates and delivery addresses.',
    handling: 'Immediately locks case; audits GST e-invoice IRN hash to verify unique legal status.',
    badge: 'Critical collision lock',
    badgeColor: 'border-red-500/30 text-red-500 bg-red-500/10',
  },
];

export const ConflictsSection: React.FC = () => {
  const [filter, setFilter] = useState<'all' | 'pricing' | 'statutory' | 'payments'>('all');

  const filtered = filter === 'all' ? CONFLICTS : CONFLICTS.filter((c) => c.category === filter);

  return (
    <section id="conflicts" className="py-20 md:py-28 border-b border-border/60 scroll-mt-16">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-12">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/30 text-amber-500 text-xs font-bold uppercase tracking-wider mb-4">
            <AlertTriangle className="w-3.5 h-3.5" />
            <span>Statutory & Ledger Discrepancies</span>
          </div>
          <h2 className="font-heading font-extrabold text-3xl sm:text-4xl lg:text-5xl text-foreground tracking-tight">
            Conflicts it catches.
          </h2>
          <p className="mt-4 text-base sm:text-lg text-foreground-muted">
            The ten most common receivables conflicts in Indian B2B trade, auto-detected from raw documents and handled with statutory rigor.
          </p>
        </div>

        {/* Filter Controls (R-79 compliant) */}
        <div className="flex flex-wrap items-center justify-center gap-2 mb-12">
          <button
            type="button"
            onClick={() => setFilter('all')}
            className={`px-4 py-2 rounded-lg text-xs font-semibold transition-all ${
              filter === 'all'
                ? 'bg-brand text-black shadow-xs'
                : 'bg-surface border border-border text-foreground-muted hover:text-foreground'
            }`}
          >
            All conflicts (C1–C10)
          </button>
          <button
            type="button"
            onClick={() => setFilter('pricing')}
            className={`px-4 py-2 rounded-lg text-xs font-semibold transition-all ${
              filter === 'pricing'
                ? 'bg-brand text-black shadow-xs'
                : 'bg-surface border border-border text-foreground-muted hover:text-foreground'
            }`}
          >
            Pricing & Quantity (C1–C3, C6)
          </button>
          <button
            type="button"
            onClick={() => setFilter('statutory')}
            className={`px-4 py-2 rounded-lg text-xs font-semibold transition-all ${
              filter === 'statutory'
                ? 'bg-brand text-black shadow-xs'
                : 'bg-surface border border-border text-foreground-muted hover:text-foreground'
            }`}
          >
            Statutory & MSMEDA (C4, C5)
          </button>
          <button
            type="button"
            onClick={() => setFilter('payments')}
            className={`px-4 py-2 rounded-lg text-xs font-semibold transition-all ${
              filter === 'payments'
                ? 'bg-brand text-black shadow-xs'
                : 'bg-surface border border-border text-foreground-muted hover:text-foreground'
            }`}
          >
            Payments & Deductions (C7–C10)
          </button>
        </div>

        {/* Conflicts Grid: Hover Lift Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filtered.map((item) => (
            <div
              key={item.code}
              className="group p-6 rounded-2xl bg-surface border border-border hover:border-brand/60 hover:-translate-y-1.5 transition-all duration-300 shadow-xs hover:shadow-lg flex flex-col justify-between"
            >
              <div>
                {/* Card Top: Code badge & Status Tag */}
                <div className="flex items-center justify-between mb-4">
                  <span className="w-8 h-8 rounded-lg bg-surface-subtle border border-border text-foreground font-mono font-extrabold text-xs flex items-center justify-center group-hover:border-brand group-hover:text-brand transition-colors">
                    {item.code}
                  </span>
                  <span className={`text-[11px] font-mono font-semibold px-2.5 py-0.5 rounded-full border ${item.badgeColor}`}>
                    {item.badge}
                  </span>
                </div>

                {/* Title & Description */}
                <h3 className="font-heading font-bold text-lg text-foreground group-hover:text-brand transition-colors">
                  {item.title}
                </h3>
                <p className="mt-2 text-xs text-foreground-muted leading-relaxed">
                  {item.description}
                </p>

                {/* Realistic Scenario */}
                <div className="mt-4 p-3 rounded-lg bg-surface-subtle/80 dark:bg-[#151915] border border-border/60 text-xs">
                  <span className="text-[10px] font-mono uppercase text-foreground-subtle block font-bold mb-1">
                    Typical Scenario
                  </span>
                  <p className="text-foreground-muted italic leading-normal">
                    &ldquo;{item.scenario}&rdquo;
                  </p>
                </div>
              </div>

              {/* Handling Rule */}
              <div className="mt-5 pt-4 border-t border-border/60 text-xs">
                <span className="text-[10px] font-mono uppercase text-brand block font-bold mb-1">
                  DuesPilot Rule
                </span>
                <p className="text-foreground font-medium">
                  {item.handling}
                </p>
              </div>

            </div>
          ))}
        </div>

      </div>
    </section>
  );
};
