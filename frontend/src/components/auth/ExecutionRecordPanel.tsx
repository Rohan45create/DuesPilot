import React, { useState, useEffect } from 'react';
import { CheckCircle2, Clock, FileText } from 'lucide-react';

export const ExecutionRecordPanel: React.FC = () => {
  const [activeStep, setActiveStep] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setActiveStep((prev) => (prev + 1) % 4);
    }, 2800);
    return () => clearInterval(timer);
  }, []);

  const steps = [
    {
      title: 'Invoice & PO Reconciliation',
      badge: 'SPAN VERIFIED',
      detail: 'INV-2024-001 (₹1,20,000) matched with PO-4412 and Challan CH-890.',
      status: 'VERIFIED',
      time: '0.4s',
    },
    {
      title: 'Section 16 Statutory Interest Gate',
      badge: '3X BANK RATE',
      detail: 'Compound interest computed at 20.25% p.a. with monthly rests: ₹56,200.',
      status: 'CALCULATED',
      time: '0.6s',
    },
    {
      title: 'Clause Conflict Adjudication',
      badge: 'RESOLVED',
      detail: 'Nemotron-3 Super adjudicated 45-day MSMEDA statutory mandate over buyer term.',
      status: 'CONFIRMED',
      time: '1.1s',
    },
    {
      title: 'Notice & WhatsApp Action Ready',
      badge: 'DISPATCH READY',
      detail: 'Statutory demand notice generated with bilingual legal proof bundle.',
      status: 'READY',
      time: '1.4s',
    },
  ];

  return (
    <div className="relative w-full h-full min-h-[520px] rounded-3xl overflow-hidden bg-gradient-to-br from-[#0F140D] via-[#141A12] to-[#0A0D09] border border-[#273522] p-8 sm:p-10 flex flex-col justify-between text-white shadow-2xl">
      {/* Background ambient glow matching NVIDIA brand aesthetic */}
      <div className="absolute -top-24 -right-24 w-96 h-96 bg-[#76B900]/15 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute -bottom-24 -left-24 w-80 h-80 bg-[#76B900]/10 rounded-full blur-3xl pointer-events-none" />

      {/* Top Header */}
      <div className="relative z-10">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-[#76B900] flex items-center justify-center text-black font-extrabold text-sm shadow-md">
              D
            </div>
            <span className="font-heading font-extrabold text-xl tracking-tight text-white">
              DuesPilot
            </span>
          </div>
          <div className="flex items-center gap-2 px-3 py-1 rounded-full bg-[#1A2616] border border-[#76B900]/30 text-xs font-mono text-[#76B900]">
            <span className="w-2 h-2 rounded-full bg-[#76B900] animate-pulse" />
            <span>LIVE RECONCILIATION</span>
          </div>
        </div>

        <h2 className="text-2xl sm:text-3xl font-heading font-bold text-white tracking-tight mt-6 leading-snug">
          Statutory recovery for Indian MSMEs, backed by verifiable evidence.
        </h2>
        <p className="text-sm text-[#9AA29A] mt-2 max-w-lg">
          Automated MSMEDA Section 16 interest calculation, strict paise arithmetic, and clause conflict adjudication.
        </p>
      </div>

      {/* Execution Record Card */}
      <div className="relative z-10 my-6 bg-[#161D15]/90 backdrop-blur-md border border-[#273522] rounded-2xl p-5 shadow-xl">
        <div className="flex items-center justify-between border-b border-[#273522] pb-3 mb-4 text-xs font-mono">
          <div className="flex items-center gap-2 text-[#9AA29A]">
            <FileText className="w-3.5 h-3.5 text-[#76B900]" />
            <span>CASE: <span className="text-white font-semibold">DP-0142</span> · Kestrel Industrial</span>
          </div>
          <div className="flex items-center gap-1.5 text-[#76B900]">
            <Clock className="w-3 h-3" />
            <span>1.4s TOTAL</span>
          </div>
        </div>

        {/* Pipeline Steps */}
        <div className="space-y-3">
          {steps.map((step, idx) => {
            const isActive = idx === activeStep;
            const isCompleted = idx <= activeStep;
            return (
              <div
                key={step.title}
                className={`transition-all duration-300 rounded-xl p-3 border ${
                  isActive
                    ? 'bg-[#1D271B] border-[#76B900]/50 shadow-sm'
                    : isCompleted
                    ? 'bg-[#161D15] border-[#273522] opacity-90'
                    : 'bg-[#121711] border-[#1F281E] opacity-50'
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2.5">
                    {isCompleted ? (
                      <CheckCircle2 className="w-4 h-4 text-[#76B900] shrink-0" />
                    ) : (
                      <div className="w-4 h-4 rounded-full border border-[#4E5C4A] shrink-0" />
                    )}
                    <span className="text-xs font-semibold text-white tracking-tight">
                      {step.title}
                    </span>
                  </div>
                  <span className="px-2 py-0.5 rounded text-[10px] font-mono font-medium bg-[#22301F] text-[#9EE516] border border-[#76B900]/20">
                    {step.badge}
                  </span>
                </div>
                <p className="text-[11px] text-[#A6B2A5] mt-1.5 pl-6.5 leading-relaxed font-sans">
                  {step.detail}
                </p>
              </div>
            );
          })}
        </div>
      </div>

      {/* Footer Metrics */}
      <div className="relative z-10 pt-4 border-t border-[#222E1F] grid grid-cols-3 gap-4 text-center">
        <div>
          <div className="text-xs font-mono text-[#76B900] font-bold">100%</div>
          <div className="text-[10px] text-[#9AA29A] mt-0.5">Paise Precision</div>
        </div>
        <div>
          <div className="text-xs font-mono text-[#76B900] font-bold">3× RBI</div>
          <div className="text-[10px] text-[#9AA29A] mt-0.5">Statutory Rate</div>
        </div>
        <div>
          <div className="text-xs font-mono text-[#76B900] font-bold">0 PII</div>
          <div className="text-[10px] text-[#9AA29A] mt-0.5">Zero Data Leaks</div>
        </div>
      </div>
    </div>
  );
};
