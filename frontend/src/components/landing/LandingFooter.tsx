import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, FileText, ShieldCheck } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

export const LandingFooter: React.FC = () => {
  const { user } = useAuth();

  return (
    <footer className="bg-surface border-t border-border">
      
      {/* Pre-footer Call to Action Banner */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-20 pb-16">
        <div className="p-8 sm:p-12 lg:p-16 rounded-3xl bg-gradient-to-br from-brand/20 via-surface to-surface border-2 border-brand/40 shadow-2xl relative overflow-hidden text-center sm:text-left">
          
          <div className="max-w-2xl relative z-10">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-brand/20 border border-brand/40 text-brand text-xs font-bold uppercase tracking-wider mb-4">
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>Instant Synthetic Demo</span>
            </div>
            
            <h2 className="font-heading font-extrabold text-3xl sm:text-4xl lg:text-5xl text-foreground tracking-tight">
              Know what you&apos;re really owed. Reconcile with proof.
            </h2>
            
            <p className="mt-4 text-base sm:text-lg text-foreground-muted leading-relaxed">
              Explore how DuesPilot ingests complex B2B invoices, handles part-payments, flags statutory violations, and prepares audit-grade reconciliation dossiers.
            </p>

            <div className="mt-8 flex flex-wrap items-center justify-center sm:justify-start gap-4">
              <Link
                to={user ? "/app" : "/signin"}
                data-testid="footer-try-demo-btn"
                className="inline-flex items-center gap-2 bg-brand hover:bg-brand-hover text-black font-bold text-base px-6 py-3.5 rounded-lg transition-all shadow-md hover:shadow-brand/20 group"
              >
                <span>Try the demo</span>
                <ArrowRight className="w-4 h-4 transition-transform group-hover:translate-x-1" />
              </Link>

              <Link
                to={user ? "/app/cases/DP-0142" : "/signin"}
                data-testid="footer-view-workspace-btn"
                className="inline-flex items-center gap-2 border border-border hover:border-brand/60 bg-surface text-foreground font-semibold text-base px-6 py-3.5 rounded-lg transition-all"
              >
                <span>View case workspace</span>
              </Link>
            </div>
          </div>

        </div>
      </div>

      {/* Main Footer Links */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12 border-t border-border/60">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
          
          {/* Brand Info */}
          <div className="space-y-4">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-brand flex items-center justify-center text-black font-extrabold text-base tracking-tighter shadow-sm">
                D
              </div>
              <span className="font-heading font-extrabold text-lg text-foreground tracking-tight">
                DuesPilot
              </span>
            </div>
            <p className="text-xs text-foreground-muted leading-relaxed">
              Autonomous delayed-payments copilot for Indian MSMEs. Grounded evidence, statutory MSMEDA compliance, and human approval for every decision.
            </p>
          </div>

          {/* Product Links */}
          <div className="space-y-3">
            <div className="text-xs font-mono font-bold uppercase tracking-wider text-foreground">
              Architecture & Proof
            </div>
            <ul className="space-y-2 text-xs">
              <li>
                <a href="#how-it-works" data-testid="footer-nav-how-it-works" className="text-foreground-muted hover:text-brand transition-colors">
                  Pipeline: Messy in, verified out
                </a>
              </li>
              <li>
                <a href="#proof" data-testid="footer-nav-proof" className="text-foreground-muted hover:text-brand transition-colors">
                  Every number has a receipt
                </a>
              </li>
              <li>
                <a href="#conflicts" data-testid="footer-nav-conflicts" className="text-foreground-muted hover:text-brand transition-colors">
                  Conflicts caught (C1 to C10)
                </a>
              </li>
              <li>
                <a href="#models" data-testid="footer-nav-models" className="text-foreground-muted hover:text-brand transition-colors">
                  Nemotron tiered routing
                </a>
              </li>
              <li>
                <a href="#guardrails" data-testid="footer-nav-guardrails" className="text-foreground-muted hover:text-brand transition-colors">
                  Statutory guardrails & refusals
                </a>
              </li>
            </ul>
          </div>

          {/* Platform Links */}
          <div className="space-y-3">
            <div className="text-xs font-mono font-bold uppercase tracking-wider text-foreground">
              Workspace & Tools
            </div>
            <ul className="space-y-2 text-xs">
              <li>
                <Link to="/app" data-testid="footer-nav-app-overview" className="text-foreground-muted hover:text-brand transition-colors">
                  Overview Dashboard
                </Link>
              </li>
              <li>
                <Link to="/app/cases" data-testid="footer-nav-app-cases" className="text-foreground-muted hover:text-brand transition-colors">
                  Active Cases Directory
                </Link>
              </li>
              <li>
                <Link to="/app/review" data-testid="footer-nav-app-review" className="text-foreground-muted hover:text-brand transition-colors">
                  Human Review Queue
                </Link>
              </li>
              <li>
                <Link to="/app/benchmarks" data-testid="footer-nav-app-benchmarks" className="text-foreground-muted hover:text-brand transition-colors">
                  Accuracy Benchmarks
                </Link>
              </li>
              <li>
                <Link to="/app/models" data-testid="footer-nav-app-models" className="text-foreground-muted hover:text-brand transition-colors">
                  Models & Rules Config
                </Link>
              </li>
            </ul>
          </div>

          {/* Hackathon & Open Source */}
          <div className="space-y-3">
            <div className="text-xs font-mono font-bold uppercase tracking-wider text-foreground">
              Hackathon & Docs
            </div>
            <ul className="space-y-2 text-xs">
              <li>
                <a
                  href="https://github.com/Rohan45create/DuesPilot"
                  target="_blank"
                  rel="noreferrer"
                  data-testid="footer-github-link"
                  className="flex items-center gap-1.5 text-foreground-muted hover:text-brand transition-colors"
                >
                  <svg className="w-3.5 h-3.5 fill-current" viewBox="0 0 24 24" aria-hidden="true">
                    <path fillRule="evenodd" clipRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
                  </svg>
                  <span>GitHub Repository</span>
                </a>
              </li>
              <li>
                <a
                  href="https://github.com/Rohan45create/DuesPilot/tree/main/docs"
                  target="_blank"
                  rel="noreferrer"
                  data-testid="footer-docs-link"
                  className="flex items-center gap-1.5 text-foreground-muted hover:text-brand transition-colors"
                >
                  <FileText className="w-3.5 h-3.5" />
                  <span>Architecture & PRD Docs</span>
                </a>
              </li>
              <li>
                <span className="text-foreground-subtle block pt-1 text-[11px]">
                  Built for Nebius × NVIDIA Global AI Hackathon
                </span>
              </li>
            </ul>
          </div>

        </div>

        {/* Bottom Disclaimer Notice */}
        <div className="mt-12 pt-8 border-t border-border flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-foreground-subtle">
          <p className="font-medium text-center sm:text-left">
            Not legal advice. Synthetic demo data. Built for the Nebius × NVIDIA Global AI Hackathon.
          </p>
          <p className="font-mono text-[11px] text-center sm:text-right">
            NVIDIA Nemotron · Nebius AI Cloud · Supabase Postgres
          </p>
        </div>

      </div>

    </footer>
  );
};
