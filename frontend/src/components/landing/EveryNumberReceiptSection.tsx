import React, { useState } from 'react';
import { FileSearch, CheckCircle2, ExternalLink, ShieldCheck, Tag } from 'lucide-react';

interface ProvenanceTarget {
  id: string;
  label: string;
  field: string;
  rawString: string;
  parsedValue: string;
  docName: string;
  page: number;
  bbox: string;
  model: string;
  status: string;
}

const TARGETS: ProvenanceTarget[] = [
  {
    id: 'total',
    label: 'Invoice Grand Total',
    field: 'invoice.total_amount',
    rawString: '₹ 1,18,000.00',
    parsedValue: '118000.00 INR',
    docName: 'INV-2026-0142.pdf',
    page: 1,
    bbox: 'x: 412, y: 788, w: 148, h: 26',
    model: 'Nemotron-3-Nano (Vision OCR)',
    status: 'Arithmetic Verified (C3 PASSED)',
  },
  {
    id: 'unit_price',
    label: 'Line Item Unit Price',
    field: 'invoice.items[0].unit_price',
    rawString: '₹ 1,000.00 / unit',
    parsedValue: '1000.00 INR',
    docName: 'INV-2026-0142.pdf',
    page: 1,
    bbox: 'x: 280, y: 440, w: 90, h: 20',
    model: 'Nemotron-3-Nano (Vision OCR)',
    status: 'PO-4402 Rate Matched (C1 PASSED)',
  },
  {
    id: 'payment',
    label: 'Bank UTR Settlement',
    field: 'payment.utr_number',
    rawString: 'UTR991208KESTREL',
    parsedValue: '50000.00 INR',
    docName: 'Bank_Statement_Jan26.pdf',
    page: 3,
    bbox: 'x: 140, y: 312, w: 180, h: 22',
    model: 'Nemotron-3-Super (Transaction Matcher)',
    status: 'NEFT Clearing Confirmed',
  },
];

export const EveryNumberReceiptSection: React.FC = () => {
  const [activeTarget, setActiveTarget] = useState<ProvenanceTarget>(TARGETS[0]);

  return (
    <section id="proof" className="py-20 md:py-28 bg-surface-subtle/40 dark:bg-[#0E110E] border-b border-border/60 scroll-mt-16">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-brand/10 border border-brand/30 text-brand text-xs font-bold uppercase tracking-wider mb-4">
            <FileSearch className="w-3.5 h-3.5" />
            <span>Verifiable Ground Truth</span>
          </div>
          <h2 className="font-heading font-extrabold text-3xl sm:text-4xl lg:text-5xl text-foreground tracking-tight">
            Every number has a receipt.
          </h2>
          <p className="mt-4 text-base sm:text-lg text-foreground-muted">
            No synthetic leaps or ungrounded LLM guesses. Every reconciled balance is anchored to exact document bounding boxes and bank clearing timestamps.
          </p>
        </div>

        {/* Interactive Receipt & Provenance Inspector */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          
          {/* Left Column: Simulated Invoice Card with Sweeping Highlight */}
          <div className="lg:col-span-7">
            <div className="rounded-2xl bg-surface border border-border p-6 sm:p-8 shadow-xl relative">
              
              {/* Document Header Bar */}
              <div className="flex items-center justify-between pb-4 mb-6 border-b border-border">
                <div className="flex items-center gap-3">
                  <div className="w-3 h-3 rounded-full bg-brand" />
                  <div>
                    <span className="font-mono text-xs font-bold text-foreground">
                      ORIGINAL DOCUMENT · PAGE 1
                    </span>
                    <span className="block text-[11px] text-foreground-muted">
                      INV-2026-0142.pdf (Scanned PDF · 300 DPI)
                    </span>
                  </div>
                </div>
                <div className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-brand/10 text-brand text-xs font-mono font-semibold">
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  <span>OCR Confidence 99.8%</span>
                </div>
              </div>

              {/* Invoice Body Content */}
              <div className="space-y-6 text-xs sm:text-sm">
                
                {/* Seller & Buyer details */}
                <div className="grid grid-cols-2 gap-4 pb-4 border-b border-border/60">
                  <div>
                    <div className="text-[10px] font-mono text-foreground-subtle uppercase">Supplier (Seller)</div>
                    <div className="font-bold text-foreground mt-0.5">Apex Precision Engg Ltd</div>
                    <div className="text-[11px] text-foreground-muted font-mono">GSTIN: 27AABCA1234F1Z8</div>
                    <div className="text-[11px] text-brand font-mono font-medium">Udyam: UDYAM-MH-02-0048123</div>
                  </div>
                  <div>
                    <div className="text-[10px] font-mono text-foreground-subtle uppercase">Buyer</div>
                    <div className="font-bold text-foreground mt-0.5">Kestrel Industrial Solutions</div>
                    <div className="text-[11px] text-foreground-muted font-mono">GSTIN: 27AAACK5678P1ZQ</div>
                    <div className="text-[11px] text-foreground-muted">PO Ref: PO-2026-4402</div>
                  </div>
                </div>

                {/* Line Item Table */}
                <div className="overflow-x-auto">
                  <table className="w-full text-left font-mono text-xs">
                    <thead>
                      <tr className="border-b border-border text-foreground-subtle text-[11px]">
                        <th className="py-2">Item Description</th>
                        <th className="py-2 text-right">Qty</th>
                        <th className="py-2 text-right">Unit Rate</th>
                        <th className="py-2 text-right">Amount (₹)</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/40">
                      <tr className={activeTarget.id === 'unit_price' ? 'bg-brand/10' : ''}>
                        <td className="py-2.5 font-sans font-medium text-foreground">
                          Precision CNC Machined Flanges (HSN 8481)
                        </td>
                        <td className="py-2.5 text-right text-foreground">100</td>
                        <td className="py-2.5 text-right font-bold text-brand">₹1,000.00</td>
                        <td className="py-2.5 text-right text-foreground">₹1,00,000.00</td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                {/* Taxes & Sweeping Highlight on Total */}
                <div className="pt-2 border-t border-border/60 space-y-2 font-mono text-xs">
                  <div className="flex justify-between text-foreground-muted">
                    <span>Central GST (9%):</span>
                    <span>₹9,000.00</span>
                  </div>
                  <div className="flex justify-between text-foreground-muted">
                    <span>State GST (9%):</span>
                    <span>₹9,000.00</span>
                  </div>
                  
                  {/* Total with Sweeping Highlight */}
                  <div
                    className={`flex justify-between items-center p-3 rounded-lg border transition-all ${
                      activeTarget.id === 'total'
                        ? 'sweep-highlight-effect border-brand text-brand shadow-sm'
                        : 'bg-surface-subtle border-border text-foreground'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <span className="font-heading font-extrabold text-sm text-foreground">
                        INVOICE TOTAL AMOUNT:
                      </span>
                      <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-brand/20 text-brand font-bold uppercase">
                        Audited
                      </span>
                    </div>
                    <span className="font-heading font-black text-lg sm:text-xl text-brand tabular-nums">
                      ₹1,18,000.00
                    </span>
                  </div>
                </div>

              </div>

              {/* Provenance bounding chip hovering over total */}
              <div className="mt-4 pt-3 border-t border-border flex items-center justify-between text-xs font-mono text-foreground-muted">
                <div className="flex items-center gap-2 text-brand">
                  <Tag className="w-3.5 h-3.5" />
                  <span>Chip linked to: {activeTarget.field}</span>
                </div>
                <span className="text-[11px] text-foreground-subtle">{activeTarget.bbox}</span>
              </div>

            </div>
          </div>

          {/* Right Column: Provenance Inspector & Selector */}
          <div className="lg:col-span-5 space-y-5">
            
            <div className="text-xs font-mono font-bold uppercase tracking-wider text-foreground-subtle">
              Interactive Grounding Inspector
            </div>

            {/* Target Selector Buttons (R-79 compliant) */}
            <div className="grid grid-cols-1 gap-2.5">
              {TARGETS.map((t) => (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => setActiveTarget(t)}
                  className={`text-left p-3.5 rounded-xl border transition-all flex items-center justify-between ${
                    activeTarget.id === t.id
                      ? 'bg-brand/10 border-brand shadow-sm'
                      : 'bg-surface border-border hover:border-brand/40'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-2.5 h-2.5 rounded-full ${
                        activeTarget.id === t.id ? 'bg-brand' : 'bg-foreground-subtle'
                      }`}
                    />
                    <div>
                      <div className="text-xs font-bold text-foreground">{t.label}</div>
                      <div className="text-[11px] font-mono text-foreground-muted">{t.rawString}</div>
                    </div>
                  </div>
                  <span className="text-[10px] font-mono px-2 py-1 rounded bg-surface border border-border text-foreground-muted">
                    Inspect
                  </span>
                </button>
              ))}
            </div>

            {/* Active Grounding Dossier Card */}
            <div className="p-5 rounded-2xl bg-surface border-2 border-brand/40 shadow-lg space-y-4">
              <div className="flex items-center gap-2 pb-3 border-b border-border">
                <ShieldCheck className="w-4 h-4 text-brand" />
                <span className="font-mono text-xs font-bold uppercase text-foreground">
                  Provenance Dossier
                </span>
              </div>

              <div className="space-y-2.5 text-xs font-mono">
                <div>
                  <span className="text-foreground-subtle block text-[10px] uppercase">Source Document</span>
                  <span className="font-semibold text-foreground flex items-center gap-1.5 mt-0.5">
                    <ExternalLink className="w-3 h-3 text-brand" />
                    {activeTarget.docName} (Page {activeTarget.page})
                  </span>
                </div>

                <div>
                  <span className="text-foreground-subtle block text-[10px] uppercase">Coordinate Bounding Box</span>
                  <span className="text-brand font-semibold">{activeTarget.bbox}</span>
                </div>

                <div>
                  <span className="text-foreground-subtle block text-[10px] uppercase">Extracted Raw Token</span>
                  <span className="text-foreground font-semibold bg-surface-subtle px-2 py-0.5 rounded border border-border inline-block mt-0.5">
                    {activeTarget.rawString}
                  </span>
                </div>

                <div>
                  <span className="text-foreground-subtle block text-[10px] uppercase">Extraction Engine</span>
                  <span className="text-foreground font-medium">{activeTarget.model}</span>
                </div>

                <div className="pt-2 border-t border-border">
                  <span className="text-foreground-subtle block text-[10px] uppercase">Integrity Check</span>
                  <span className="text-xs font-bold text-brand mt-0.5 block">
                    ✓ {activeTarget.status}
                  </span>
                </div>
              </div>

            </div>

          </div>

        </div>

      </div>
    </section>
  );
};
