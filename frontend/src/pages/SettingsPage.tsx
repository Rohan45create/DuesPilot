import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { LoadingSkeleton, ErrorState } from '../components/ui/StateViews';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { NotificationSettings } from '../api/schemas';
import {
  Building2,
  Bell,
  Shield,
  Clock,
  Radio,
  CheckCircle2,
  AlertTriangle,
  Lock,
  ExternalLink,
  Save,
  HelpCircle,
  Mail,
  MessageSquare,
  Sparkles,
  Info,
  Trash2,
} from 'lucide-react';
import { cn } from '../utils/formatters';
import { Link } from 'react-router-dom';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { SeedDemoDataDialog } from '../components/demo/SeedDemoDataDialog';
import { useToast } from '../context/ToastContext';

export const SettingsPage: React.FC = () => {
  const { isAnonymous } = useAuth();
  const queryClient = useQueryClient();
  const { showToast } = useToast();

  const [form, setForm] = useState<NotificationSettings | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [showSetupGuide, setShowSetupGuide] = useState(false);
  const [isSeedDialogOpen, setIsSeedDialogOpen] = useState(false);
  const [isRemoveConfirmOpen, setIsRemoveConfirmOpen] = useState(false);
  const [isRemoving, setIsRemoving] = useState(false);

  // Load demo data status
  const { data: previewData } = useQuery({
    queryKey: ['demoDataPreview'],
    queryFn: () => api.getDemoDataPreview(),
  });

  const handleRemoveDemoData = async () => {
    setIsRemoving(true);
    try {
      await api.deleteDemoData();
      showToast('Demo data removed', 'success');
      setIsRemoveConfirmOpen(false);
      queryClient.invalidateQueries({ queryKey: ['demoDataPreview'] });
      queryClient.invalidateQueries({ queryKey: ['cases'] });
      queryClient.invalidateQueries({ queryKey: ['overview'] });
    } catch (err: any) {
      showToast(err.message || 'Failed to remove demo data.', 'error');
    } finally {
      setIsRemoving(false);
    }
  };

  // Load settings
  const {
    data: settingsData,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['notificationSettings'],
    queryFn: () => api.getNotificationSettings(),
  });

  // Load WhatsApp setup guide
  const { data: setupGuide } = useQuery({
    queryKey: ['whatsappSetupGuide'],
    queryFn: () => api.getWhatsAppSetupGuide(),
  });

  // Initialize form state when query succeeds
  useEffect(() => {
    if (settingsData) {
      setForm(settingsData);
    }
  }, [settingsData]);

  // Mutation for saving
  const saveMutation = useMutation({
    mutationFn: (updated: Partial<NotificationSettings>) =>
      api.updateNotificationSettings(updated),
    onSuccess: (saved) => {
      setForm(saved);
      queryClient.setQueryData(['notificationSettings'], saved);
      setSuccessMsg('Settings updated successfully.');
      setErrorMsg(null);
      setTimeout(() => setSuccessMsg(null), 3500);
    },
    onError: (err: any) => {
      setErrorMsg(err.message || 'Failed to save settings.');
      setTimeout(() => setErrorMsg(null), 4000);
    },
  });

  // Calculate live IST time
  const now = new Date();
  const utcHours = now.getUTCHours();
  const utcMinutes = now.getUTCMinutes();
  const istMinutesTotal = (utcHours * 60 + utcMinutes + 330) % 1440;
  const istHours = Math.floor(istMinutesTotal / 60);
  const istMinutes = istMinutesTotal % 60;
  const isInsideWindow = istHours >= 9 && istHours < 19;
  const istFormatted = `${String(istHours).padStart(2, '0')}:${String(istMinutes).padStart(2, '0')} IST`;

  if (isLoading) {
    return (
      <div className="space-y-6 w-full">
        <LoadingSkeleton rows={10} />
      </div>
    );
  }

  if (isError || (!settingsData && !form)) {
    return (
      <div className="w-full py-8">
        <ErrorState
          title="Failed to load settings"
          message={error instanceof Error ? error.message : 'Settings could not be retrieved.'}
          onRetry={() => refetch()}
        />
      </div>
    );
  }

  const current = form || settingsData;
  if (!current) {
    return (
      <Card className="p-8 text-center text-xs text-foreground-muted">
        No settings records found.
      </Card>
    );
  }

  const handleFieldChange = <K extends keyof NotificationSettings>(
    field: K,
    val: NotificationSettings[K]
  ) => {
    if (isAnonymous) return; // Prevent mutation in demo mode
    setForm((prev) => (prev ? { ...prev, [field]: val } : null));
  };

  const handleSave = () => {
    if (isAnonymous || !form) return;
    saveMutation.mutate(form);
  };

  return (
    <div className="space-y-6 w-full max-w-5xl">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold font-heading text-foreground tracking-tight">
            Settings & Guardrails
          </h1>
          <p className="text-xs sm:text-sm text-foreground-muted mt-1">
            Enterprise identity, communication defaults, sandbox isolation, and delivery provider credentials.
          </p>
        </div>

        <div className="flex items-center gap-3">
          {isAnonymous ? (
            <Link to="/signup">
              <Button size="sm" data-testid="settings-signup-btn" className="h-9 font-bold bg-brand text-black">
                <Sparkles className="w-3.5 h-3.5 mr-1.5" />
                Sign up to send
              </Button>
            </Link>
          ) : (
            <Button
              size="sm"
              data-testid="settings-save-btn"
              onClick={handleSave}
              disabled={saveMutation.isPending}
              className="h-9 font-bold"
            >
              <Save className="w-3.5 h-3.5 mr-1.5" />
              {saveMutation.isPending ? 'Saving...' : 'Save settings'}
            </Button>
          )}
        </div>
      </div>

      {/* Demo Mode Notice Banner */}
      {isAnonymous && (
        <div className="p-4 rounded-2xl border border-brand/40 bg-brand/5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-full bg-brand/20 flex items-center justify-center shrink-0">
              <Lock className="w-4 h-4 text-brand" />
            </div>
            <div>
              <div className="text-xs font-bold text-foreground">
                Demo Workspace · Preview Controls Active
              </div>
              <div className="text-[11px] text-foreground-muted">
                Settings controls are displayed in a read-only preview state. Create a free account to customize sending policies and configure live providers.
              </div>
            </div>
          </div>
          <Link to="/signup" className="shrink-0">
            <Button size="sm" variant="outline" className="text-xs border-brand text-brand hover:bg-brand hover:text-black">
              Sign up to customize
            </Button>
          </Link>
        </div>
      )}

      {/* Success / Error Feedback */}
      {successMsg && (
        <div className="p-3.5 rounded-xl bg-brand/10 border border-brand/30 text-xs text-brand font-medium flex items-center gap-2 animate-in fade-in">
          <CheckCircle2 className="w-4 h-4 text-brand shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}
      {errorMsg && (
        <div className="p-3.5 rounded-xl bg-red-500/10 border border-red-500/30 text-xs text-red-500 font-medium flex items-center gap-2 animate-in fade-in">
          <AlertTriangle className="w-4 h-4 text-red-500 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* SECTION 1: BUSINESS PROFILE */}
      <Card id="section-profile" data-testid="settings-section-profile" className="p-6 space-y-5">
        <div className="flex items-center gap-2 pb-3 border-b border-border">
          <Building2 className="w-4 h-4 text-brand" />
          <h2 className="text-sm font-bold font-heading text-foreground">
            Business Profile & Statutory Identity
          </h2>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
          <div>
            <label className="block text-foreground-muted font-medium mb-1.5">
              Legal Business Name
            </label>
            <input
              type="text"
              data-testid="settings-business-name"
              disabled={isAnonymous}
              value={current.businessName}
              onChange={(e) => handleFieldChange('businessName', e.target.value)}
              className="w-full p-2.5 rounded-xl bg-surface-subtle border border-border text-foreground focus:border-brand focus:outline-hidden disabled:opacity-60"
            />
          </div>

          <div>
            <label className="block text-foreground-muted font-medium mb-1.5">
              MSMEDA Supplier Size Class
            </label>
            <select
              disabled={isAnonymous}
              data-testid="settings-supplier-size"
              value={current.supplierSizeClass}
              onChange={(e) => handleFieldChange('supplierSizeClass', e.target.value)}
              className="w-full p-2.5 rounded-xl bg-surface-subtle border border-border text-foreground focus:border-brand focus:outline-hidden disabled:opacity-60"
            >
              <option value="MICRO">Micro Enterprise (Investment &le; ₹1 Cr, Turnover &le; ₹5 Cr)</option>
              <option value="SMALL">Small Enterprise (Investment &le; ₹10 Cr, Turnover &le; ₹50 Cr)</option>
              <option value="MEDIUM">Medium Enterprise (Investment &le; ₹50 Cr, Turnover &le; ₹250 Cr)</option>
            </select>
          </div>

          <div>
            <label className="block text-foreground-muted font-medium mb-1.5">
              Udyam Registration Date
            </label>
            <input
              type="date"
              data-testid="settings-udyam-date"
              disabled={isAnonymous}
              value={current.udyamRegistrationDate || ''}
              onChange={(e) => handleFieldChange('udyamRegistrationDate', e.target.value)}
              className="w-full p-2.5 rounded-xl bg-surface-subtle border border-border text-foreground focus:border-brand focus:outline-hidden disabled:opacity-60"
            />
          </div>

          <div>
            <label className="block text-foreground-muted font-medium mb-1.5">
              Owner WhatsApp (E.164)
            </label>
            <input
              type="text"
              data-testid="settings-owner-whatsapp"
              disabled={isAnonymous}
              value={current.ownerWhatsapp}
              onChange={(e) => handleFieldChange('ownerWhatsapp', e.target.value)}
              placeholder="+91 98200 12345"
              className="w-full p-2.5 rounded-xl bg-surface-subtle border border-border text-foreground font-mono focus:border-brand focus:outline-hidden disabled:opacity-60"
            />
          </div>
        </div>
      </Card>

      {/* SECTION 2: NOTIFICATION DEFAULTS */}
      <Card id="section-defaults" data-testid="settings-section-defaults" className="p-6 space-y-5">
        <div className="flex items-center gap-2 pb-3 border-b border-border">
          <Bell className="w-4 h-4 text-brand" />
          <h2 className="text-sm font-bold font-heading text-foreground">
            Notification Defaults & Formats
          </h2>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
          <div>
            <label className="block text-foreground-muted font-medium mb-1.5">
              Default Outbox Channel
            </label>
            <select
              disabled={isAnonymous}
              data-testid="settings-default-channel"
              value={current.defaultChannel}
              onChange={(e) =>
                handleFieldChange(
                  'defaultChannel',
                  e.target.value as 'EMAIL' | 'WHATSAPP_LINK' | 'WHATSAPP_API'
                )
              }
              className="w-full p-2.5 rounded-xl bg-surface-subtle border border-border text-foreground focus:border-brand focus:outline-hidden disabled:opacity-60"
            >
              <option value="EMAIL">Email via Resend</option>
              <option value="WHATSAPP_LINK">WhatsApp Link (wa.me click-to-chat)</option>
              <option value="WHATSAPP_API">WhatsApp Cloud API (Meta Graph)</option>
            </select>
          </div>

          <div>
            <label className="block text-foreground-muted font-medium mb-1.5">
              Default Draft Language
            </label>
            <select
              disabled={isAnonymous}
              data-testid="settings-default-lang"
              value={current.defaultLanguage}
              onChange={(e) =>
                handleFieldChange('defaultLanguage', e.target.value as 'EN' | 'HI')
              }
              className="w-full p-2.5 rounded-xl bg-surface-subtle border border-border text-foreground focus:border-brand focus:outline-hidden disabled:opacity-60"
            >
              <option value="EN">English</option>
              <option value="HI">हिंदी (Hindi)</option>
            </select>
          </div>

          <div className="md:col-span-2 pt-2">
            <label className="flex items-center gap-3 cursor-pointer select-none">
              <input
                type="checkbox"
                data-testid="settings-include-pdf"
                disabled={isAnonymous}
                checked={current.defaultIncludePdf}
                onChange={(e) => handleFieldChange('defaultIncludePdf', e.target.checked)}
                className="w-4 h-4 accent-brand rounded cursor-pointer disabled:opacity-60"
              />
              <div>
                <span className="font-semibold text-foreground text-xs">
                  Attach Statement of Account PDF by default
                </span>
                <p className="text-[11px] text-foreground-muted">
                  Generates an audit-ready, tamper-evident reconciliation statement listing matched invoices, verified payments, and cited evidence.
                </p>
              </div>
            </label>
          </div>
        </div>
      </Card>

      {/* SECTION 3: SANDBOX MODE (RULE R-76) */}
      <Card id="section-sandbox" data-testid="settings-section-sandbox" className="p-6 space-y-5">
        <div className="flex items-center justify-between pb-3 border-b border-border">
          <div className="flex items-center gap-2">
            <Shield className="w-4 h-4 text-brand" />
            <h2 className="text-sm font-bold font-heading text-foreground">
              Sandbox Mode (Rerouting Protection)
            </h2>
          </div>
          <span
            className={cn(
              'text-[10px] font-bold px-2.5 py-0.5 rounded-full border',
              current.sandboxMode
                ? 'border-brand/40 bg-brand/10 text-brand'
                : 'border-red-500/40 bg-red-500/10 text-red-500'
            )}
          >
            {current.sandboxMode ? 'SANDBOX ACTIVE' : 'LIVE DISPATCH'}
          </span>
        </div>

        <div className="p-4 rounded-xl bg-surface-subtle border border-border text-xs space-y-3">
          <div className="flex items-start gap-3">
            <input
              type="checkbox"
              id="sandbox-toggle"
              data-testid="settings-sandbox-toggle"
              disabled={isAnonymous}
              checked={current.sandboxMode}
              onChange={(e) => handleFieldChange('sandboxMode', e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-brand rounded cursor-pointer disabled:opacity-60"
            />
            <label htmlFor="sandbox-toggle" className="cursor-pointer select-none">
              <span className="font-bold text-foreground">
                Reroute all outbox communications to sandbox addresses (On by default)
              </span>
              <p className="text-[11px] text-foreground-muted mt-1 leading-relaxed">
                Per Rule R-76, when sandbox is active, all emails and WhatsApp notifications are strictly diverted to your configured test address. No buyer will ever receive a real notification while testing.
              </p>
            </label>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2">
            <div>
              <span className="text-[11px] font-semibold text-foreground-muted block mb-1">
                Fallback Sandbox Email
              </span>
              <input
                type="email"
                data-testid="settings-sandbox-email"
                disabled={isAnonymous}
                value={current.sandboxEmail}
                onChange={(e) => handleFieldChange('sandboxEmail', e.target.value)}
                className="w-full p-2 rounded-lg bg-surface border border-border text-foreground font-mono text-xs focus:border-brand focus:outline-hidden disabled:opacity-60"
              />
            </div>
            <div>
              <span className="text-[11px] font-semibold text-foreground-muted block mb-1">
                Fallback Sandbox Phone
              </span>
              <input
                type="text"
                data-testid="settings-sandbox-phone"
                disabled={isAnonymous}
                value={current.sandboxPhone}
                onChange={(e) => handleFieldChange('sandboxPhone', e.target.value)}
                className="w-full p-2 rounded-lg bg-surface border border-border text-foreground font-mono text-xs focus:border-brand focus:outline-hidden disabled:opacity-60"
              />
            </div>
          </div>
        </div>
      </Card>

      {/* SECTION 4: SENDING WINDOW & STATUTORY GUARDRAILS (RULE R-78) */}
      <Card className="p-6 space-y-5">
        <div className="flex items-center justify-between pb-3 border-b border-border">
          <div className="flex items-center gap-2">
            <Clock className="w-4 h-4 text-brand" />
            <h2 className="text-sm font-bold font-heading text-foreground">
              Sending Window & Harassment Guardrails
            </h2>
          </div>
          <div
            className={cn(
              'flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10px] font-bold border',
              isInsideWindow
                ? 'border-brand/40 bg-brand/10 text-brand'
                : 'border-amber-500/40 bg-amber-500/10 text-amber-500'
            )}
          >
            <span
              className={cn(
                'w-1.5 h-1.5 rounded-full',
                isInsideWindow ? 'bg-brand animate-pulse' : 'bg-amber-500'
              )}
            />
            <span>{isInsideWindow ? 'Window Open' : 'Window Closed'} · {istFormatted}</span>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
          <div className="p-3.5 rounded-xl bg-surface-subtle border border-border">
            <div className="font-semibold text-foreground mb-1">Permitted Hours</div>
            <div className="text-sm font-bold font-mono text-brand">09:00 – 19:00 IST</div>
            <p className="text-[11px] text-foreground-muted mt-1">
              Statutory sending window per Rule R-78 to prevent anti-harassment violations.
            </p>
          </div>

          <div className="p-3.5 rounded-xl bg-surface-subtle border border-border">
            <div className="font-semibold text-foreground mb-1">Follow-up Cool-down</div>
            <div className="text-sm font-bold font-mono text-brand">{current.cooldownHours} Hours</div>
            <p className="text-[11px] text-foreground-muted mt-1">
              Minimum elapsed time enforced before a subsequent message can be sent to the same buyer.
            </p>
          </div>

          <div className="p-3.5 rounded-xl bg-surface-subtle border border-border">
            <div className="font-semibold text-foreground mb-1">Follow-up Cap</div>
            <div className="text-sm font-bold font-mono text-brand">Max {current.maxFollowUps} Notices</div>
            <p className="text-[11px] text-foreground-muted mt-1">
              No further automated outreach allowed once 3 follow-ups have been sent without resolution.
            </p>
          </div>
        </div>

        <div className="p-3 rounded-lg bg-surface/50 border border-border text-xs text-foreground-muted flex items-start gap-2">
          <Info className="w-4 h-4 text-brand shrink-0 mt-0.5" />
          <div className="leading-relaxed">
            <span className="font-semibold text-foreground">Prohibited Claims Filter: </span>
            Every draft is automatically scanned before queuing. Terms claiming "legal notice", "statutory penalty under section 16", or "IBC insolvency application" are strictly blocked from automated dispatch and require formal legal counsel review.
          </div>
        </div>
      </Card>

      {/* SECTION 5: PROVIDER STATUS (NEVER SHOW KEYS) */}
      <Card id="section-providers" data-testid="settings-section-providers" className="p-6 space-y-5">
        <div className="flex items-center justify-between pb-3 border-b border-border">
          <div className="flex items-center gap-2">
            <Radio className="w-4 h-4 text-brand" />
            <h2 className="text-sm font-bold font-heading text-foreground">
              Provider Status & Webhook Connections
            </h2>
          </div>
          <span className="text-[11px] text-foreground-muted flex items-center gap-1">
            <Lock className="w-3 h-3 text-brand" /> Secret keys are never exposed in UI
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
          {/* Resend Card */}
          <div className="p-4 rounded-xl border border-border bg-surface-subtle flex flex-col justify-between space-y-3">
            <div>
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <Mail className="w-4 h-4 text-brand" />
                  <span className="font-bold text-sm text-foreground">Resend Email</span>
                </div>
                <span
                  className={cn(
                    'text-[10px] font-bold px-2 py-0.5 rounded-full border',
                    current.resendStatus === 'CONNECTED'
                      ? 'border-brand/40 bg-brand/10 text-brand'
                      : 'border-amber-500/40 bg-amber-500/10 text-amber-500'
                  )}
                >
                  {current.resendStatus}
                </span>
              </div>
              <p className="text-[11px] text-foreground-muted leading-relaxed">
                Transactional email provider with PDF statement attachment support and Svix webhook delivery tracking.
              </p>
              <div className="mt-3 space-y-1 font-mono text-[11px] text-foreground-muted">
                <div>From Address: <span className="text-foreground">notifications@duespilot.example.com</span></div>
                <div>Webhook Signature: <span className="text-brand">Svix Verified</span></div>
                <div>Secret Key: <span className="text-foreground-muted italic">•••••••••••••••• (Server Vault)</span></div>
              </div>
            </div>
          </div>

          {/* WhatsApp Cloud API Card */}
          <div className="p-4 rounded-xl border border-border bg-surface-subtle flex flex-col justify-between space-y-3">
            <div>
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <MessageSquare className="w-4 h-4 text-brand" />
                  <span className="font-bold text-sm text-foreground">WhatsApp Cloud API</span>
                </div>
                <span
                  className={cn(
                    'text-[10px] font-bold px-2 py-0.5 rounded-full border',
                    current.whatsappStatus === 'CONNECTED'
                      ? 'border-brand/40 bg-brand/10 text-brand'
                      : 'border-amber-500/40 bg-amber-500/10 text-amber-500'
                  )}
                >
                  {current.whatsappStatus}
                </span>
              </div>
              <p className="text-[11px] text-foreground-muted leading-relaxed">
                Meta Graph Cloud API for approved template message dispatches and inbound buyer delivery confirmations.
              </p>
              <div className="mt-3 space-y-1 font-mono text-[11px] text-foreground-muted">
                <div>Phone Number ID: <span className="text-foreground">•••••••8812</span></div>
                <div>Business Account ID: <span className="text-foreground">•••••••3910</span></div>
                <div>Access Token: <span className="text-foreground-muted italic">•••••••••••••••• (Server Vault)</span></div>
              </div>
            </div>

            <div className="pt-2 border-t border-border/60 flex items-center justify-between">
              <span className="text-[11px] text-foreground-muted">Meta Developer Guide</span>
              <Button
                variant="outline"
                size="sm"
                data-testid="settings-whatsapp-guide-btn"
                onClick={() => setShowSetupGuide(true)}
                className="text-xs h-7"
              >
                <HelpCircle className="w-3 h-3 mr-1" />
                Setup Guide
              </Button>
            </div>
          </div>
        </div>
      </Card>

      {/* DEMO DATA SECTION */}
      <Card data-testid="settings-demo-data-section" className="p-6 space-y-4">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-brand/10 border border-brand/20 flex items-center justify-center text-brand shrink-0">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold font-heading text-foreground">
                Demo Data
              </h2>
              <p className="text-xs text-foreground-muted mt-0.5">
                Seed or remove synthetic MSME test cases to explore DuesPilot features
              </p>
            </div>
          </div>
          {previewData?.alreadySeeded ? (
            <span
              data-testid="settings-demo-active-badge"
              className="px-2 py-0.5 rounded-full text-[11px] font-semibold bg-brand/10 text-brand border border-brand/20 shrink-0"
            >
              Demo Data Active
            </span>
          ) : (
            <span
              data-testid="settings-demo-empty-badge"
              className="px-2 py-0.5 rounded-full text-[11px] font-medium bg-surface-subtle text-foreground-muted border border-border shrink-0"
            >
              Not Seeded
            </span>
          )}
        </div>

        <div className="p-4 rounded-xl bg-surface-subtle/50 border border-border text-xs text-foreground-muted space-y-2">
          <p className="leading-relaxed">
            Demo data adds synthetic MSME receivables cases, invoices, purchase orders, credit notes, and fictional buyer contacts with simulated audit logs. All dispatches are sandbox-simulated and never sent to real recipients.
          </p>
          <p className="text-[11px] text-foreground-muted/80">
            Removing demo data cleans up only rows marked as demo data, keeping any real cases you created completely untouched.
          </p>
        </div>

        <div className="flex items-center justify-between pt-2">
          {previewData?.alreadySeeded ? (
            <div className="flex items-center justify-between w-full">
              <span className="text-xs text-foreground-muted">
                {previewData.caseCount} demo cases currently in workspace
              </span>
              <Button
                variant="danger"
                size="sm"
                data-testid="settings-remove-demo-btn"
                onClick={() => setIsRemoveConfirmOpen(true)}
                isLoading={isRemoving}
                className="text-xs"
              >
                <Trash2 className="w-3.5 h-3.5 mr-1.5" />
                Remove demo data
              </Button>
            </div>
          ) : (
            <div className="flex items-center justify-between w-full">
              <span className="text-xs text-foreground-muted">
                Add realistic demo data to test reconciliation & eligibility gates
              </span>
              <Button
                variant="primary"
                size="sm"
                data-testid="settings-seed-demo-btn"
                onClick={() => setIsSeedDialogOpen(true)}
                className="text-xs font-medium"
              >
                <Sparkles className="w-3.5 h-3.5 mr-1.5" />
                Seed demo data
              </Button>
            </div>
          )}
        </div>
      </Card>

      {/* WHATSAPP SETUP GUIDE MODAL */}
      {showSetupGuide && setupGuide && (
        <div
          data-testid="settings-guide-modal"
          className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-end sm:items-center justify-center p-0 sm:p-4 z-50 animate-in fade-in duration-150"
        >
          <Card className="w-full sm:max-w-xl p-5 sm:p-6 space-y-4 shadow-2xl rounded-t-3xl sm:rounded-2xl border-b-0 sm:border-b max-h-[90dvh] overflow-y-auto">
            <div className="w-10 h-1 bg-foreground-muted/30 rounded-full mx-auto sm:hidden mb-1" />
            <div className="flex items-center justify-between pb-2 border-b border-border">
              <div className="flex items-center gap-2">
                <MessageSquare className="w-5 h-5 text-brand" />
                <h3 className="font-bold text-base font-heading text-foreground">
                  WhatsApp Cloud API Configuration Guide
                </h3>
              </div>
              <button
                type="button"
                data-testid="settings-guide-close-btn"
                onClick={() => setShowSetupGuide(false)}
                className="text-foreground-muted hover:text-foreground text-sm cursor-pointer p-1 min-h-[44px] min-w-[44px] flex items-center justify-center"
              >
                ✕
              </button>
            </div>

            <p className="text-xs text-foreground-muted leading-relaxed">
              Follow these steps in your Meta for Developers dashboard to connect production WhatsApp messaging.
            </p>

            <div className="space-y-3 max-h-80 overflow-y-auto pr-1">
              {setupGuide.steps.map((st) => (
                <div key={st.stepNumber} className="p-3 rounded-xl bg-surface-subtle border border-border text-xs space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="w-5 h-5 rounded-full bg-brand text-black font-bold flex items-center justify-center text-[10px]">
                      {st.stepNumber}
                    </span>
                    <span className="font-bold text-foreground">{st.title}</span>
                  </div>
                  <p className="text-foreground-muted pl-7">{st.instruction}</p>
                  {st.referenceUrl && (
                    <div className="pl-7 pt-1">
                      <a
                        href={st.referenceUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="text-brand hover:underline inline-flex items-center gap-1 text-[11px]"
                      >
                        {st.referenceUrl}
                        <ExternalLink className="w-2.5 h-2.5" />
                      </a>
                    </div>
                  )}
                </div>
              ))}
            </div>

            <div className="p-3 rounded-lg bg-surface/50 border border-border text-[11px] text-foreground-muted">
              Webhook Endpoint: <span className="font-mono text-brand">/api/webhooks/whatsapp</span> with hub verification token configured in backend environment variables.
            </div>

            <div className="flex justify-end pt-2">
              <Button size="sm" data-testid="settings-guide-done-btn" onClick={() => setShowSetupGuide(false)} className="w-full sm:w-auto min-h-[44px]">
                Done
              </Button>
            </div>
          </Card>
        </div>
      )}

      {/* SEED DEMO DATA DIALOG */}
      <SeedDemoDataDialog
        isOpen={isSeedDialogOpen}
        onClose={() => setIsSeedDialogOpen(false)}
        onSuccess={(count) => {
          showToast(`Added ${count} demo cases to your workspace.`, 'success');
          queryClient.invalidateQueries({ queryKey: ['demoDataPreview'] });
          queryClient.invalidateQueries({ queryKey: ['cases'] });
          queryClient.invalidateQueries({ queryKey: ['overview'] });
        }}
      />

      {/* REMOVE DEMO DATA CONFIRM DIALOG */}
      <ConfirmDialog
        isOpen={isRemoveConfirmOpen}
        title="Remove demo data from workspace?"
        description="This will permanently delete all synthetic demo cases, documents, contacts, and analysis from your workspace. Your own real cases and data will never be touched."
        confirmLabel="Remove demo data"
        cancelLabel="Cancel"
        variant="danger"
        isLoading={isRemoving}
        onConfirm={handleRemoveDemoData}
        onCancel={() => setIsRemoveConfirmOpen(false)}
        data-testid="confirm-remove-demo-dialog"
        confirmButtonTestId="confirm-dialog-confirm-btn"
        cancelButtonTestId="confirm-dialog-cancel-btn"
      />
    </div>
  );
};

export default SettingsPage;
