import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2, ArrowRight, AlertCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ExecutionRecordPanel } from '../components/auth/ExecutionRecordPanel';
import { cn } from '../utils/formatters';

export const OnboardingPage: React.FC = () => {
  const navigate = useNavigate();
  const { workspace, updateProfile, refreshProfile } = useAuth();

  const [businessName, setBusinessName] = useState(workspace?.businessName || workspace?.name || '');
  const [supplierSizeClass, setSupplierSizeClass] = useState<'MICRO' | 'SMALL' | 'MEDIUM'>(
    (workspace?.supplierSizeClass as 'MICRO' | 'SMALL' | 'MEDIUM') || 'MICRO'
  );
  const [udyamRegistrationDate, setUdyamRegistrationDate] = useState(workspace?.udyamRegistrationDate || '');
  const [ownerPhone, setOwnerPhone] = useState(workspace?.ownerPhone || '');
  const [ownerWhatsapp, setOwnerWhatsapp] = useState(workspace?.ownerWhatsapp || '');
  const [sameAsPhone, setSameAsPhone] = useState(false);
  const [notificationConsent, setNotificationConsent] = useState(true);

  // Validation & Loading
  const [errors, setErrors] = useState<{ [key: string]: string }>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [serverError, setServerError] = useState('');

  useEffect(() => {
    if (sameAsPhone) {
      setOwnerWhatsapp(ownerPhone);
    }
  }, [sameAsPhone, ownerPhone]);

  const validate = (): boolean => {
    const errs: { [key: string]: string } = {};
    if (!businessName.trim()) {
      errs.businessName = 'Business name is required.';
    }

    if (ownerPhone && !/^\+?[0-9]{10,14}$/.test(ownerPhone.replace(/[\s-]/g, ''))) {
      errs.ownerPhone = 'Please enter a valid 10-digit phone number with optional country code.';
    }

    if (ownerWhatsapp && !/^\+?[0-9]{10,14}$/.test(ownerWhatsapp.replace(/[\s-]/g, ''))) {
      errs.ownerWhatsapp = 'Please enter a valid WhatsApp number.';
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate() || isSubmitting) return;

    setIsSubmitting(true);
    setServerError('');

    try {
      await updateProfile({
        businessName: businessName.trim(),
        supplierSizeClass,
        udyamRegistrationDate: udyamRegistrationDate || null,
        ownerPhone: ownerPhone.trim() || null,
        ownerWhatsapp: (sameAsPhone ? ownerPhone.trim() : ownerWhatsapp.trim()) || null,
      });

      await refreshProfile();
      navigate('/app', { replace: true });
    } catch (err: unknown) {
      setServerError(err instanceof Error ? err.message : 'Failed to save workspace profile. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen w-full bg-background flex items-center justify-center p-4 sm:p-6 lg:p-10 transition-colors">
      <div className="w-full max-w-6xl grid grid-cols-1 lg:grid-cols-2 gap-8 items-center">
        {/* Onboarding Form Card */}
        <div className="w-full max-w-lg mx-auto bg-surface border border-border rounded-3xl p-6 sm:p-8 shadow-xl transition-colors">
          <div className="flex items-center gap-2 mb-4">
            <span className="w-2.5 h-2.5 rounded-full bg-brand" />
            <span className="font-mono text-xs uppercase tracking-wider text-brand-text-light dark:text-brand font-semibold">
              Step 1 of 1 · Workspace Setup
            </span>
          </div>

          <h1 className="font-heading font-extrabold text-2xl sm:text-3xl text-foreground tracking-tight">
            Complete your supplier profile
          </h1>
          <p className="text-xs sm:text-sm text-foreground-muted mt-1.5 mb-6">
            Configure your enterprise size and communication channels for statutory Section 16 interest calculations.
          </p>

          {serverError && (
            <div
              role="alert"
              className="mb-4 p-3 rounded-xl bg-status-red/10 border border-status-red/30 text-status-red text-xs flex items-start gap-2.5"
            >
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{serverError}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            {/* Business Name */}
            <div>
              <label htmlFor="business-name" className="block text-xs font-semibold text-foreground mb-1.5">
                Enterprise / Business Name <span className="text-status-red">*</span>
              </label>
              <div className="relative">
                <input
                  id="business-name"
                  type="text"
                  value={businessName}
                  onChange={(e) => setBusinessName(e.target.value)}
                  placeholder="e.g. Apex Industrial Fabricators Ltd"
                  className={cn(
                    'w-full pl-3.5 pr-4 py-2.5 rounded-xl bg-surface border text-sm text-foreground placeholder:text-foreground-subtle transition-colors focus:outline-hidden',
                    errors.businessName ? 'border-status-red focus:border-status-red' : 'border-border focus:border-brand'
                  )}
                  data-testid="onboarding-business-name"
                />
              </div>
              {errors.businessName && (
                <p className="text-[11px] text-status-red mt-1">{errors.businessName}</p>
              )}
            </div>

            {/* Supplier Size Class */}
            <div>
              <label className="block text-xs font-semibold text-foreground mb-1.5">
                MSMEDA Enterprise Classification <span className="text-status-red">*</span>
              </label>
              <div className="grid grid-cols-3 gap-2.5">
                {(['MICRO', 'SMALL', 'MEDIUM'] as const).map((cls) => (
                  <button
                    key={cls}
                    type="button"
                    onClick={() => setSupplierSizeClass(cls)}
                    className={cn(
                      'p-2.5 rounded-xl border text-center transition-all cursor-pointer flex flex-col items-center justify-center gap-1',
                      supplierSizeClass === cls
                        ? 'bg-brand/10 border-brand text-foreground shadow-xs font-bold'
                        : 'bg-surface border-border text-foreground-muted hover:text-foreground hover:bg-surface-subtle'
                    )}
                    data-testid={`size-class-${cls.toLowerCase()}`}
                  >
                    <span className="text-xs uppercase font-mono">{cls}</span>
                    <span className="text-[10px] text-foreground-muted font-normal">
                      {cls === 'MICRO' ? '≤ ₹1 Cr inv.' : cls === 'SMALL' ? '≤ ₹10 Cr inv.' : '≤ ₹50 Cr inv.'}
                    </span>
                  </button>
                ))}
              </div>
              <p className="text-[11px] text-foreground-muted mt-1.5">
                Micro & Small enterprises qualify for Section 16 statutory 3× compound interest.
              </p>
            </div>

            {/* Optional Udyam Registration Date */}
            <div>
              <label htmlFor="udyam-date" className="block text-xs font-semibold text-foreground mb-1.5">
                Udyam Registration Date <span className="text-foreground-subtle font-normal">(Optional)</span>
              </label>
              <div className="relative">
                <input
                  id="udyam-date"
                  type="date"
                  value={udyamRegistrationDate}
                  onChange={(e) => setUdyamRegistrationDate(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-surface border border-border text-sm text-foreground transition-colors focus:border-brand focus:outline-hidden"
                  data-testid="onboarding-udyam-date"
                />
              </div>
            </div>

            {/* Owner Phone & WhatsApp */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label htmlFor="owner-phone" className="block text-xs font-semibold text-foreground mb-1.5">
                  Owner Phone Number
                </label>
                <input
                  id="owner-phone"
                  type="tel"
                  value={ownerPhone}
                  onChange={(e) => setOwnerPhone(e.target.value)}
                  placeholder="+91 98765 43210"
                  className={cn(
                    'w-full px-3.5 py-2.5 rounded-xl bg-surface border text-sm text-foreground placeholder:text-foreground-subtle transition-colors focus:outline-hidden',
                    errors.ownerPhone ? 'border-status-red focus:border-status-red' : 'border-border focus:border-brand'
                  )}
                  data-testid="onboarding-phone"
                />
                {errors.ownerPhone && (
                  <p className="text-[11px] text-status-red mt-1">{errors.ownerPhone}</p>
                )}
              </div>

              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label htmlFor="owner-whatsapp" className="block text-xs font-semibold text-foreground">
                    WhatsApp Number
                  </label>
                  <label className="flex items-center gap-1 text-[11px] text-foreground-muted cursor-pointer">
                    <input
                      type="checkbox"
                      checked={sameAsPhone}
                      onChange={(e) => setSameAsPhone(e.target.checked)}
                      className="rounded accent-brand w-3 h-3 cursor-pointer"
                    />
                    <span>Same</span>
                  </label>
                </div>
                <input
                  id="owner-whatsapp"
                  type="tel"
                  disabled={sameAsPhone}
                  value={ownerWhatsapp}
                  onChange={(e) => setOwnerWhatsapp(e.target.value)}
                  placeholder="+91 98765 43210"
                  className={cn(
                    'w-full px-3.5 py-2.5 rounded-xl bg-surface border text-sm text-foreground placeholder:text-foreground-subtle transition-colors focus:outline-hidden disabled:opacity-60',
                    errors.ownerWhatsapp ? 'border-status-red focus:border-status-red' : 'border-border focus:border-brand'
                  )}
                  data-testid="onboarding-whatsapp"
                />
                {errors.ownerWhatsapp && (
                  <p className="text-[11px] text-status-red mt-1">{errors.ownerWhatsapp}</p>
                )}
              </div>
            </div>

            {/* Notification Consent Checkbox */}
            <div className="pt-2">
              <label className="flex items-start gap-2.5 cursor-pointer text-xs text-foreground-muted">
                <input
                  type="checkbox"
                  checked={notificationConsent}
                  onChange={(e) => setNotificationConsent(e.target.checked)}
                  className="rounded accent-brand w-4 h-4 mt-0.5 cursor-pointer shrink-0"
                />
                <span>
                  I consent to receive case escalation alerts, interest accrual updates, and debtor payment confirmation notices via WhatsApp and Email.
                </span>
              </label>
            </div>

            {/* Submit Button */}
            <div className="pt-3">
              <button
                type="submit"
                disabled={isSubmitting}
                className="w-full py-3 rounded-xl bg-brand hover:bg-brand-hover text-black font-extrabold text-xs sm:text-sm transition-all shadow-md flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
                data-testid="onboarding-submit-button"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin text-black" />
                    <span>Saving workspace...</span>
                  </>
                ) : (
                  <>
                    <span>Launch DuesPilot Workspace</span>
                    <ArrowRight className="w-4 h-4 text-black" />
                  </>
                )}
              </button>
            </div>
          </form>
        </div>

        {/* Right Brand Panel */}
        <div className="hidden lg:block w-full h-full">
          <ExecutionRecordPanel />
        </div>
      </div>
    </div>
  );
};
