import React from 'react';
import { ShieldAlert, XCircle, UserCheck, HelpCircle, MessageSquareOff } from 'lucide-react';

interface Guardrail {
  title: string;
  refusal: string;
  rationale: string;
  enforcement: string;
  icon: React.ComponentType<{ className?: string }>;
}

const GUARDRAILS: Guardrail[] = [
  {
    title: 'No Legal Conclusions',
    refusal: 'DuesPilot never renders legal advice or declares a debt definitively enforceable.',
    rationale: 'MSME debt recovery involves jurisdictional nuances, statutory limitation acts, and judicial discretion that cannot be promised by automated software.',
    enforcement: 'Outputs factual reconciliation dossiers formatted for review by your advocate, chartered accountant, or the MSME Facilitation Council.',
    icon: XCircle,
  },
  {
    title: 'Never Sends Without Approval',
    refusal: 'DuesPilot never autonomously transmits emails, WhatsApp messages, or legal demands.',
    rationale: 'Autonomous agent communications risk damaging critical commercial vendor relationships with flawed or premature demands.',
    enforcement: 'Every action rests in a draft state in your Review Queue. Dispatch requires an explicit human review and click.',
    icon: UserCheck,
  },
  {
    title: 'Never Guesses Unknowns',
    refusal: 'DuesPilot never invents missing dates, disputed payments, or unconfirmed interest rates.',
    rationale: 'Hallucinating unverified payments destroys credibility in council proceedings and commercial disputes.',
    enforcement: 'Statutory Gate D abstains: "Interest not computed — bank rate unconfirmed". Missing facts are quarantined as pending evidence.',
    icon: HelpCircle,
  },
  {
    title: 'No Legal Notices on WhatsApp',
    refusal: 'DuesPilot strictly prohibits issuing formal MSMEDA Section 16 demand notices via WhatsApp.',
    rationale: 'Statutory demand notices require rigorous evidentiary proof of service recognized under Indian civil procedure rules.',
    enforcement: 'WhatsApp click-to-chat is strictly restricted to friendly document requests; formal notices generate as downloadable legal PDFs for registered post.',
    icon: MessageSquareOff,
  },
];

export const GuardrailsSection: React.FC = () => {
  return (
    <section id="guardrails" className="py-20 md:py-28 border-b border-border/60 scroll-mt-16">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-red-500/10 border border-red-500/30 text-red-500 text-xs font-bold uppercase tracking-wider mb-4">
            <ShieldAlert className="w-3.5 h-3.5" />
            <span>Strict Operational Boundaries</span>
          </div>
          <h2 className="font-heading font-extrabold text-3xl sm:text-4xl lg:text-5xl text-foreground tracking-tight">
            What DuesPilot refuses to do.
          </h2>
          <p className="mt-4 text-base sm:text-lg text-foreground-muted">
            Reliability comes from knowing where AI must stop. These four non-negotiable boundaries protect your business and legal standing.
          </p>
        </div>

        {/* 4 Guardrails Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {GUARDRAILS.map((g) => {
            const Icon = g.icon;
            return (
              <div
                key={g.title}
                className="p-6 sm:p-8 rounded-2xl bg-surface border border-border hover:border-red-500/40 transition-colors shadow-xs flex flex-col justify-between"
              >
                <div>
                  {/* Title Bar */}
                  <div className="flex items-center gap-3 mb-4">
                    <div className="w-10 h-10 rounded-xl bg-red-500/10 border border-red-500/30 text-red-500 flex items-center justify-center shrink-0">
                      <Icon className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-heading font-bold text-lg text-foreground">
                        {g.title}
                      </h3>
                      <span className="text-[10px] font-mono uppercase tracking-wider text-red-500 font-bold">
                        NON-NEGOTIABLE GUARDRAIL
                      </span>
                    </div>
                  </div>

                  {/* Refusal statement */}
                  <div className="p-3.5 rounded-xl bg-red-500/[0.04] border border-red-500/20 text-xs font-medium text-foreground mb-4">
                    {g.refusal}
                  </div>

                  {/* Rationale */}
                  <div className="text-xs text-foreground-muted mb-4 leading-relaxed">
                    <span className="font-bold text-foreground block text-[11px] mb-1">
                      Why this matters:
                    </span>
                    {g.rationale}
                  </div>
                </div>

                {/* Enforcement */}
                <div className="pt-4 border-t border-border text-xs">
                  <span className="font-bold text-brand block text-[11px] mb-1 font-mono uppercase">
                    Systemic Safeguard:
                  </span>
                  <p className="text-foreground font-medium">
                    {g.enforcement}
                  </p>
                </div>
              </div>
            );
          })}
        </div>

      </div>
    </section>
  );
};
