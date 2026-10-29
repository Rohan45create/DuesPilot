import React, { useEffect, useRef } from 'react';
import { LandingNav } from '../components/landing/LandingNav';
import { HeroExecutionRecord } from '../components/landing/HeroExecutionRecord';
import { MessyInVerifiedOutSection } from '../components/landing/MessyInVerifiedOutSection';
import { EveryNumberReceiptSection } from '../components/landing/EveryNumberReceiptSection';
import { ConflictsSection } from '../components/landing/ConflictsSection';
import { ModelsSection } from '../components/landing/ModelsSection';
import { GuardrailsSection } from '../components/landing/GuardrailsSection';
import { BenchmarksTeaserSection } from '../components/landing/BenchmarksTeaserSection';
import { LandingFooter } from '../components/landing/LandingFooter';

interface RevealSectionProps {
  children: React.ReactNode;
}

const RevealSection: React.FC<RevealSectionProps> = ({ children }) => {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // If prefers-reduced-motion is enabled, make visible immediately
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      if (ref.current) {
        ref.current.classList.add('reveal-visible');
      }
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('reveal-visible');
            observer.unobserve(entry.target);
          }
        });
      },
      {
        threshold: 0.1,
        rootMargin: '0px 0px -40px 0px',
      }
    );

    const el = ref.current;
    if (el) {
      observer.observe(el);
    }

    return () => {
      if (el) {
        observer.unobserve(el);
      }
    };
  }, []);

  return (
    <div ref={ref} className="reveal-init">
      {children}
    </div>
  );
};

export const LandingPage: React.FC = () => {
  useEffect(() => {
    // Scroll to top on landing page mount
    window.scrollTo(0, 0);
  }, []);

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col selection:bg-brand selection:text-black">
      {/* Top sticky navigation */}
      <LandingNav />

      {/* Main content body */}
      <main id="main-content" className="flex-1">
        {/* Hero Section */}
        <HeroExecutionRecord />

        {/* 1. "Messy in, verified out" */}
        <RevealSection>
          <MessyInVerifiedOutSection />
        </RevealSection>

        {/* 2. "Every number has a receipt" */}
        <RevealSection>
          <EveryNumberReceiptSection />
        </RevealSection>

        {/* 3. "Conflicts it catches" */}
        <RevealSection>
          <ConflictsSection />
        </RevealSection>

        {/* 4. "The right model for each job" */}
        <RevealSection>
          <ModelsSection />
        </RevealSection>

        {/* 5. "What DuesPilot refuses to do" */}
        <RevealSection>
          <GuardrailsSection />
        </RevealSection>

        {/* 6. Benchmarks teaser */}
        <RevealSection>
          <BenchmarksTeaserSection />
        </RevealSection>
      </main>

      {/* 7. Final Call to Action & Footer */}
      <RevealSection>
        <LandingFooter />
      </RevealSection>
    </div>
  );
};

export default LandingPage;
