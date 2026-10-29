import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { FormErrorSummary } from '../components/ui/FormErrorSummary';
import { api } from '../api/client';
import { UploadCloud, FileText, AlertCircle, Lock, CheckCircle2, Loader2 } from 'lucide-react';
import { cn } from '../utils/formatters';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

interface UploadedFileItem {
  id: string;
  name: string;
  size: number;
  type: string; // INVOICE, PURCHASE_ORDER, PAYMENT, CREDIT_NOTE, DELIVERY_CHALLAN, MESSAGE
  pages: number;
  status: string;
  progress?: number;
  rawFile?: File;
  rawText?: string;
}

export const NewCasePage: React.FC = () => {
  const navigate = useNavigate();
  const { isAnonymous } = useAuth();
  const { showToast } = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [activeStep, setActiveStep] = useState<number>(1);
  const [caseTitle, setCaseTitle] = useState('Kestrel Industrial — INV-2026-0142');
  const [supplier, setSupplier] = useState('Veda Components Pvt Ltd');
  const [buyer, setBuyer] = useState('Kestrel Industrial Ltd');
  const [buyerContactName, setBuyerContactName] = useState('Rajesh Kumar (Finance)');
  const [buyerEmail, setBuyerEmail] = useState('accounts@kestrel-ind.com');
  const [buyerWhatsapp, setBuyerWhatsapp] = useState('+919811111111');
  const [emailOptIn, setEmailOptIn] = useState(true);
  const [whatsappOptIn, setWhatsappOptIn] = useState(true);
  const [sizeClass, setSizeClass] = useState('UNKNOWN');
  const [udyamDate, setUdyamDate] = useState('');
  const [routing, setRouting] = useState<'TIERED' | 'ULTRA'>('TIERED');
  const [mode, setMode] = useState<'REPLAY' | 'LIVE'>('REPLAY');
  const [perCaseCap, setPerCaseCap] = useState('$0.25');
  const [buyerMessage, setBuyerMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [showDemoPrompt, setShowDemoPrompt] = useState(false);
  const [formErrors, setFormErrors] = useState<{ field: string; message: string; fieldId: string }[]>([]);
  const [analysisStepIndex, setAnalysisStepIndex] = useState(0);

  const ANALYSIS_STEPS = [
    'Parsing & validating document checksums',
    'Nemotron OCR & structured entity extraction',
    'Reconciling invoice ledger & checking 3-way match',
    'Computing Section 16 statutory compound interest',
    'Generating final audit report & recovery notice',
  ];

  const [documents, setDocuments] = useState<UploadedFileItem[]>([
    { id: '1', name: 'PO-7781.pdf', size: 102400, type: 'PURCHASE_ORDER', pages: 1, status: 'Parsed', progress: 100 },
    { id: '2', name: 'INV-2026-0142.pdf', size: 204800, type: 'INVOICE', pages: 1, status: 'Parsed', progress: 100 },
    { id: '3', name: 'bank_confirmation.pdf', size: 98000, type: 'PAYMENT', pages: 1, status: 'Parsed', progress: 100 },
    { id: '4', name: 'CN-0031.pdf', size: 84000, type: 'CREDIT_NOTE', pages: 1, status: 'Parsed', progress: 100 },
    { id: '5', name: 'buyer_message.txt', size: 1200, type: 'MESSAGE', pages: 1, status: 'Parsed · Hindi', progress: 100 },
  ]);

  const hasDeliveryDoc = documents.some((d) => d.type === 'DELIVERY_CHALLAN');

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files?.length) return;
    const newFiles: UploadedFileItem[] = Array.from(e.target.files).map((file, idx) => ({
      id: `upload-${Date.now()}-${idx}`,
      name: file.name,
      size: file.size,
      type: file.name.toLowerCase().includes('inv')
        ? 'INVOICE'
        : file.name.toLowerCase().includes('po')
        ? 'PURCHASE_ORDER'
        : file.name.toLowerCase().includes('bank') || file.name.toLowerCase().includes('pay')
        ? 'PAYMENT'
        : file.name.toLowerCase().includes('cn')
        ? 'CREDIT_NOTE'
        : 'DELIVERY_CHALLAN',
      pages: 1,
      status: 'Uploading...',
      progress: 35,
      rawFile: file,
    }));
    setDocuments((prev) => [...prev, ...newFiles]);
    showToast(`Uploading ${newFiles.length} file(s)...`, 'info');

    setTimeout(() => {
      setDocuments((prev) =>
        prev.map((d) =>
          newFiles.some((nf) => nf.id === d.id) ? { ...d, progress: 80, status: 'Parsing OCR...' } : d
        )
      );
    }, 250);

    setTimeout(() => {
      setDocuments((prev) =>
        prev.map((d) =>
          newFiles.some((nf) => nf.id === d.id) ? { ...d, progress: 100, status: 'Parsed' } : d
        )
      );
      showToast(`Uploaded and parsed ${newFiles.length} file(s)`, 'success');
    }, 600);
  };

  const handleAddMessage = () => {
    if (!buyerMessage.trim()) return;
    const isHindi = /[\u0900-\u097F]/.test(buyerMessage);
    const msgDoc: UploadedFileItem = {
      id: `msg-${Date.now()}`,
      name: `buyer_message_${documents.length + 1}.txt`,
      size: buyerMessage.length,
      type: 'MESSAGE',
      pages: 1,
      status: isHindi ? 'Parsed · Hindi' : 'Parsed',
      progress: 100,
      rawText: buyerMessage,
    };
    setDocuments((prev) => [...prev, msgDoc]);
    setBuyerMessage('');
    showToast('Added buyer message as document', 'info');
  };

  const validateForm = () => {
    const errs: { field: string; message: string; fieldId: string }[] = [];
    if (!caseTitle.trim()) {
      errs.push({ field: 'Case title', message: 'Case title cannot be empty', fieldId: 'input-case-title' });
    }
    if (!supplier.trim()) {
      errs.push({ field: 'Supplier', message: 'Supplier name is required', fieldId: 'input-supplier' });
    }
    if (!buyer.trim()) {
      errs.push({ field: 'Buyer', message: 'Buyer name is required', fieldId: 'input-buyer' });
    }
    if (buyerEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(buyerEmail)) {
      errs.push({ field: 'Buyer Email', message: 'Please provide a valid email address', fieldId: 'input-buyer-email' });
    }
    setFormErrors(errs);
    return errs.length === 0;
  };

  const executeRunAnalysis = async () => {
    setIsSubmitting(true);
    setSubmitError(null);
    setAnalysisStepIndex(0);

    const stepTimer = setInterval(() => {
      setAnalysisStepIndex((prev) => (prev < ANALYSIS_STEPS.length - 1 ? prev + 1 : prev));
    }, 300);

    try {
      const created = await api.createCase({
        title: caseTitle,
        supplierName: supplier,
        buyerName: buyer,
        supplierSizeClass: sizeClass,
        udyamRegDate: udyamDate || undefined,
      });

      // Upload raw files if any
      for (const doc of documents) {
        if (doc.rawFile) {
          await api.uploadDocument(created.id, doc.rawFile, doc.type);
        }
      }

      // Start analysis run
      await api.runAnalysis(created.id, {
        modelRouting: routing === 'TIERED' ? 'tiered' : 'all_ultra',
        mode: mode.toLowerCase(),
        maxCostMicros: 250000,
      });

      clearInterval(stepTimer);
      setAnalysisStepIndex(ANALYSIS_STEPS.length - 1);
      showToast('Analysis pipeline launched successfully!', 'success');
      navigate(`/app/cases/${created.code || created.id}`);
    } catch (err: unknown) {
      clearInterval(stepTimer);
      setSubmitError(err instanceof Error ? err.message : 'Failed to launch analysis pipeline.');
      showToast('Failed to launch analysis', 'error');
      setIsSubmitting(false);
    }
  };

  const handleRunAnalysis = async () => {
    if (!validateForm()) {
      showToast('Please fix the errors in the form before submitting.', 'error');
      return;
    }
    if (isAnonymous) {
      setShowDemoPrompt(true);
      return;
    }
    await executeRunAnalysis();
  };

  return (
    <div className="space-y-6 w-full">
      {/* Header with 4-step indicator */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold font-heading text-foreground tracking-tight">
            New case
          </h1>
          <p className="text-xs sm:text-sm text-foreground-muted mt-1 max-w-2xl">
            Add the evidence, describe the supplier, choose how the analysis runs. Nothing is sent anywhere.
          </p>
        </div>

        {/* Step indicator pills */}
        <div
          className="flex items-center gap-1.5 p-1 rounded-full bg-surface border border-border text-xs shrink-0 self-start"
          role="navigation"
          aria-label="New case creation steps"
        >
          <button
            type="button"
            data-testid="step-1-btn"
            onClick={() => setActiveStep(1)}
            className={cn(
              'px-3 py-1 rounded-full font-bold transition-colors cursor-pointer',
              activeStep === 1
                ? 'bg-brand text-black'
                : 'text-foreground hover:bg-surface-subtle'
            )}
          >
            1 Upload
          </button>
          <button
            type="button"
            data-testid="step-2-btn"
            onClick={() => setActiveStep(2)}
            className={cn(
              'px-3 py-1 rounded-full font-medium transition-colors cursor-pointer',
              activeStep === 2
                ? 'bg-brand text-black'
                : 'text-foreground hover:bg-surface-subtle'
            )}
          >
            2 Details
          </button>
          <button
            type="button"
            data-testid="step-3-btn"
            onClick={() => setActiveStep(3)}
            className={cn(
              'px-3 py-1 rounded-full font-medium transition-colors cursor-pointer',
              activeStep === 3
                ? 'bg-brand text-black'
                : 'text-foreground-muted hover:text-foreground'
            )}
          >
            3 Options
          </button>
          <button
            type="button"
            data-testid="step-4-btn"
            onClick={() => setActiveStep(4)}
            className={cn(
              'px-3 py-1 rounded-full font-medium transition-colors cursor-pointer',
              activeStep === 4
                ? 'bg-brand text-black'
                : 'text-foreground-muted hover:text-foreground'
            )}
          >
            4 Review
          </button>
        </div>
      </div>

      {submitError && (
        <div className="p-4 rounded-xl bg-status-red/10 border border-status-red/30 flex items-center gap-3 text-xs text-status-red">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{submitError}</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Upload Dropzone, Doc List, Message (8 cols) */}
        <div className="lg:col-span-8 space-y-6">
          {/* Dropzone */}
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileUpload}
            multiple
            accept=".pdf,.txt,.png,.jpg,.jpeg"
            className="hidden"
          />

          <Card
            data-testid="upload-dropzone"
            onClick={() => fileInputRef.current?.click()}
            className="p-8 border-dashed border-2 border-border flex flex-col items-center justify-center text-center hover:border-brand/60 transition-colors cursor-pointer bg-surface/50"
            role="button"
            aria-label="Upload files dropzone"
          >
            <UploadCloud className="w-10 h-10 text-foreground-muted mb-3" />
            <h3 className="text-sm font-bold font-heading text-foreground">
              Drop PDFs, text or images here
            </h3>
            <p className="text-xs text-foreground-muted mt-1 mb-4">
              PDF, TXT, PNG, JPG · 10 MB per file · 25 files per case
            </p>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              data-testid="browse-files-btn"
              onClick={(e) => {
                e.stopPropagation();
                fileInputRef.current?.click();
              }}
              className="rounded-lg text-xs"
            >
              Browse files
            </Button>
          </Card>

          {/* Uploaded Documents List */}
          <Card className="p-5">
            <h3 className="text-sm font-bold font-heading text-foreground mb-4">
              Documents · {documents.length} of 6 expected types
            </h3>

            <div className="space-y-2.5">
              {documents.map((doc) => (
                <div
                  key={doc.id}
                  className="flex flex-col sm:flex-row sm:items-center justify-between p-2.5 rounded-lg bg-surface-subtle border border-border text-xs gap-2"
                >
                  <div className="flex items-center gap-2.5">
                    <FileText className="w-4 h-4 text-foreground-muted shrink-0" />
                    <span className="font-bold text-foreground truncate max-w-[200px]">
                      {doc.name}
                    </span>
                  </div>

                  <div className="flex items-center gap-3">
                    <select
                      value={doc.type}
                      onChange={(e) => {
                        const newType = e.target.value;
                        setDocuments((prev) =>
                          prev.map((d) => (d.id === doc.id ? { ...d, type: newType } : d))
                        );
                      }}
                      className="px-2.5 py-1 rounded-md bg-surface border border-border text-foreground font-medium text-[11px] focus:outline-none focus:ring-1 focus:ring-brand"
                    >
                      <option value="PURCHASE_ORDER">Purchase order</option>
                      <option value="INVOICE">Invoice</option>
                      <option value="PAYMENT">Payment</option>
                      <option value="CREDIT_NOTE">Credit note</option>
                      <option value="DELIVERY_CHALLAN">Delivery challan</option>
                      <option value="MESSAGE">Message</option>
                    </select>

                    <span className="text-foreground-muted text-[11px]">{doc.pages}</span>
                    <div className="flex items-center gap-2">
                      <div className="w-16 bg-surface-muted rounded-full h-1.5 overflow-hidden border border-border/40 shrink-0">
                        <div
                          className="bg-brand h-full rounded-full transition-all duration-300"
                          style={{ width: `${doc.progress ?? 100}%` }}
                        />
                      </div>
                      <span className="font-semibold text-brand-text-light dark:text-brand text-[11px] whitespace-nowrap">
                        {doc.status}
                      </span>
                    </div>
                    <button
                      type="button"
                      onClick={() => setDocuments((prev) => prev.filter((d) => d.id !== doc.id))}
                      className="text-foreground-muted hover:text-status-red text-[11px] cursor-pointer ml-1"
                    >
                      Remove
                    </button>
                  </div>
                </div>
              ))}
            </div>

            {/* Missing document hint */}
            {!hasDeliveryDoc && (
              <div className="mt-4 p-3 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-500 dark:text-amber-400 text-xs font-medium">
                Missing: delivery challan or acceptance record. You can continue; the analysis will flag it.
              </div>
            )}
          </Card>

          {/* Paste message */}
          <Card className="p-5">
            <h3 className="text-sm font-bold font-heading text-foreground mb-2">
              Or paste a buyer message
            </h3>
            <textarea
              rows={3}
              data-testid="buyer-message-input"
              value={buyerMessage}
              onChange={(e) => setBuyerMessage(e.target.value)}
              placeholder="Paste WhatsApp or email text. English, Hindi and Hinglish supported."
              className="w-full p-3 text-xs rounded-xl bg-surface-subtle border border-border text-foreground placeholder:text-foreground-muted focus:outline-none focus:ring-1 focus:ring-brand resize-none"
            />
            <div className="mt-3">
              <Button
                variant="secondary"
                size="sm"
                data-testid="add-message-btn"
                onClick={handleAddMessage}
                disabled={!buyerMessage.trim()}
                className="text-xs"
              >
                Add as document
              </Button>
            </div>
          </Card>
        </div>

        {/* Right Column: Case details & Analysis options (4 cols) */}
        <div className="lg:col-span-4 space-y-6">
          {/* Case Details Form */}
          <Card className="p-5 space-y-4">
            <h3 className="text-sm font-bold font-heading text-foreground">
              Case details
            </h3>

            {formErrors.length > 0 && (
              <FormErrorSummary errors={formErrors} className="mb-2" />
            )}

            <div>
              <label htmlFor="input-case-title" className="text-[11px] font-semibold text-foreground-muted block mb-1.5">
                Case title
              </label>
              <input
                id="input-case-title"
                type="text"
                data-testid="input-case-title"
                value={caseTitle}
                aria-invalid={formErrors.some((er) => er.fieldId === 'input-case-title')}
                onChange={(e) => {
                  setCaseTitle(e.target.value);
                  if (formErrors.some((er) => er.fieldId === 'input-case-title')) {
                    setFormErrors((prev) => prev.filter((er) => er.fieldId !== 'input-case-title'));
                  }
                }}
                className={cn(
                  'w-full h-9 px-3 text-xs rounded-lg bg-surface-subtle border text-foreground focus:outline-none focus:ring-1 focus:ring-brand',
                  formErrors.some((er) => er.fieldId === 'input-case-title') ? 'border-status-red' : 'border-border'
                )}
              />
              {formErrors.find((er) => er.fieldId === 'input-case-title') && (
                <p className="text-[11px] text-status-red mt-1" role="alert">
                  {formErrors.find((er) => er.fieldId === 'input-case-title')?.message}
                </p>
              )}
            </div>

            <div>
              <label htmlFor="input-supplier" className="text-[11px] font-semibold text-foreground-muted block mb-1.5">
                Supplier
              </label>
              <input
                id="input-supplier"
                type="text"
                data-testid="input-supplier"
                value={supplier}
                aria-invalid={formErrors.some((er) => er.fieldId === 'input-supplier')}
                onChange={(e) => {
                  setSupplier(e.target.value);
                  if (formErrors.some((er) => er.fieldId === 'input-supplier')) {
                    setFormErrors((prev) => prev.filter((er) => er.fieldId !== 'input-supplier'));
                  }
                }}
                className={cn(
                  'w-full h-9 px-3 text-xs rounded-lg bg-surface-subtle border text-foreground focus:outline-none focus:ring-1 focus:ring-brand',
                  formErrors.some((er) => er.fieldId === 'input-supplier') ? 'border-status-red' : 'border-border'
                )}
              />
              {formErrors.find((er) => er.fieldId === 'input-supplier') && (
                <p className="text-[11px] text-status-red mt-1" role="alert">
                  {formErrors.find((er) => er.fieldId === 'input-supplier')?.message}
                </p>
              )}
            </div>

            <div>
              <label htmlFor="input-buyer" className="text-[11px] font-semibold text-foreground-muted block mb-1.5">
                Buyer
              </label>
              <input
                id="input-buyer"
                type="text"
                data-testid="input-buyer"
                value={buyer}
                aria-invalid={formErrors.some((er) => er.fieldId === 'input-buyer')}
                onChange={(e) => {
                  setBuyer(e.target.value);
                  if (formErrors.some((er) => er.fieldId === 'input-buyer')) {
                    setFormErrors((prev) => prev.filter((er) => er.fieldId !== 'input-buyer'));
                  }
                }}
                className={cn(
                  'w-full h-9 px-3 text-xs rounded-lg bg-surface-subtle border text-foreground focus:outline-none focus:ring-1 focus:ring-brand',
                  formErrors.some((er) => er.fieldId === 'input-buyer') ? 'border-status-red' : 'border-border'
                )}
              />
              {formErrors.find((er) => er.fieldId === 'input-buyer') && (
                <p className="text-[11px] text-status-red mt-1" role="alert">
                  {formErrors.find((er) => er.fieldId === 'input-buyer')?.message}
                </p>
              )}
            </div>

            <div>
              <label className="text-[11px] font-semibold text-foreground-muted block mb-1.5">
                Supplier size class
              </label>
              <select
                value={sizeClass}
                data-testid="select-size-class"
                onChange={(e) => setSizeClass(e.target.value)}
                className="w-full h-9 px-3 text-xs rounded-lg bg-surface-subtle border border-border text-foreground focus:outline-none focus:ring-1 focus:ring-brand"
              >
                <option value="UNKNOWN">Unknown — evidence needed</option>
                <option value="MICRO">Micro enterprise</option>
                <option value="SMALL">Small enterprise</option>
                <option value="MEDIUM">Medium enterprise</option>
              </select>
            </div>

            <div>
              <label className="text-[11px] font-semibold text-foreground-muted block mb-1.5">
                Udyam registration date (optional)
              </label>
              <input
                type="text"
                value={udyamDate}
                placeholder="Not provided"
                onChange={(e) => setUdyamDate(e.target.value)}
                className="w-full h-9 px-3 text-xs rounded-lg bg-surface-subtle border border-border text-foreground focus:outline-none focus:ring-1 focus:ring-brand"
              />
            </div>
          </Card>

          {/* Buyer Contact & Consent Card (Rule R-74) */}
          <Card className="p-5 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold font-heading text-foreground">
                Buyer Contact & Consent
              </h3>
              <span className="text-[10px] font-mono text-brand bg-brand/10 px-2 py-0.5 rounded-full font-medium">
                Rule R-74
              </span>
            </div>

            <div>
              <label className="text-[11px] font-semibold text-foreground-muted block mb-1.5">
                Contact person / Department
              </label>
              <input
                type="text"
                value={buyerContactName}
                placeholder="Accounts Payable / Finance Head"
                onChange={(e) => setBuyerContactName(e.target.value)}
                className="w-full h-9 px-3 text-xs rounded-lg bg-surface-subtle border border-border text-foreground focus:outline-none focus:ring-1 focus:ring-brand"
              />
            </div>

            <div>
              <label className="text-[11px] font-semibold text-foreground-muted block mb-1.5">
                Buyer Email
              </label>
              <input
                type="email"
                value={buyerEmail}
                placeholder="accounts@buyer.com"
                onChange={(e) => setBuyerEmail(e.target.value)}
                className="w-full h-9 px-3 text-xs rounded-lg bg-surface-subtle border border-border text-foreground focus:outline-none focus:ring-1 focus:ring-brand"
              />
            </div>

            <div>
              <label className="text-[11px] font-semibold text-foreground-muted block mb-1.5">
                Buyer WhatsApp (E.164 format)
              </label>
              <input
                type="tel"
                value={buyerWhatsapp}
                placeholder="+919876543210"
                onChange={(e) => setBuyerWhatsapp(e.target.value)}
                className="w-full h-9 px-3 text-xs rounded-lg bg-surface-subtle border border-border text-foreground focus:outline-none focus:ring-1 focus:ring-brand"
              />
            </div>

            <div className="pt-2 border-t border-border/60 space-y-2">
              <span className="text-[11px] font-semibold text-foreground block">
                Per-Channel Notification Opt-In
              </span>

              <label className="flex items-center gap-2 cursor-pointer text-xs text-foreground select-none">
                <input
                  type="checkbox"
                  checked={emailOptIn}
                  onChange={(e) => setEmailOptIn(e.target.checked)}
                  className="rounded accent-brand cursor-pointer"
                />
                <span>Email communications (ledger statements, reconciliations)</span>
              </label>

              <label className="flex items-center gap-2 cursor-pointer text-xs text-foreground select-none">
                <input
                  type="checkbox"
                  checked={whatsappOptIn}
                  onChange={(e) => setWhatsappOptIn(e.target.checked)}
                  className="rounded accent-brand cursor-pointer"
                />
                <span>WhatsApp updates (delivery proof requests, payment confirmations)</span>
              </label>
            </div>

            <p className="text-[10px] text-foreground-muted leading-relaxed bg-surface-subtle p-2.5 rounded-lg border border-border/50">
              Consent provided via bilateral commercial agreement or invoice billing terms. Recipients can reply STOP or click unsubscribe at any time (R-74).
            </p>
          </Card>

          {/* Analysis Options */}
          <Card className="p-5 space-y-4">
            <h3 className="text-sm font-bold font-heading text-foreground">
              Analysis options
            </h3>

            <div>
              <label className="text-[11px] font-semibold text-foreground-muted block mb-2">
                Model routing
              </label>
              <div className="flex gap-2">
                <button
                  type="button"
                  data-testid="routing-tiered-btn"
                  onClick={() => setRouting('TIERED')}
                  className={cn(
                    'flex-1 py-1.5 px-3 rounded-full text-xs font-semibold border transition-all cursor-pointer',
                    routing === 'TIERED'
                      ? 'bg-brand text-black border-brand'
                      : 'bg-surface-subtle border-border text-foreground'
                  )}
                >
                  Standard tiered
                </button>
                <button
                  type="button"
                  data-testid="routing-ultra-btn"
                  onClick={() => setRouting('ULTRA')}
                  className={cn(
                    'flex-1 py-1.5 px-3 rounded-full text-xs font-semibold border transition-all cursor-pointer',
                    routing === 'ULTRA'
                      ? 'bg-brand text-black border-brand'
                      : 'bg-surface-subtle border-border text-foreground'
                  )}
                >
                  Deep · Ultra
                </button>
              </div>
            </div>

            <div>
              <label className="text-[11px] font-semibold text-foreground-muted block mb-2">
                Mode
              </label>
              <div className="flex gap-2">
                <button
                  type="button"
                  data-testid="mode-replay-btn"
                  onClick={() => setMode('REPLAY')}
                  className={cn(
                    'flex-1 py-1.5 px-3 rounded-full text-xs font-semibold border transition-all cursor-pointer',
                    mode === 'REPLAY'
                      ? 'bg-brand text-black border-brand'
                      : 'bg-surface-subtle border-border text-foreground'
                  )}
                >
                  Replay
                </button>
                <button
                  type="button"
                  data-testid="mode-live-btn"
                  onClick={() => setMode('LIVE')}
                  className={cn(
                    'flex-1 py-1.5 px-3 rounded-full text-xs font-semibold border transition-all cursor-pointer',
                    mode === 'LIVE'
                      ? 'bg-brand text-black border-brand'
                      : 'bg-surface-subtle border-border text-foreground'
                  )}
                >
                  Live · cost capped
                </button>
              </div>
            </div>

            <div>
              <label className="text-[11px] font-semibold text-foreground-muted block mb-1.5">
                Max cost for this case
              </label>
              <input
                type="text"
                value={perCaseCap}
                onChange={(e) => setPerCaseCap(e.target.value)}
                className="w-full h-9 px-3 text-xs rounded-lg bg-surface-subtle border border-border text-foreground font-mono"
              />
            </div>

            <Button
              data-testid="run-analysis-btn"
              onClick={handleRunAnalysis}
              isLoading={isSubmitting}
              loadingText="Analyzing case..."
              className="w-full font-bold text-sm h-11"
            >
              Run analysis
            </Button>

            <p className="text-[10px] text-foreground-muted leading-tight text-center">
              Steps: parse → extract → verify → reconcile → conflicts → gates → draft. Synthetic data only.
            </p>
          </Card>
        </div>
      </div>

      {/* Analysis Stream Event Stepper Modal */}
      {isSubmitting && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Running analysis pipeline"
          data-testid="analysis-stepper-modal"
          className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/60 backdrop-blur-xs p-0 sm:p-4 animate-in fade-in duration-200"
        >
          <div className="bg-surface border border-border rounded-t-3xl sm:rounded-2xl max-w-md w-full p-5 sm:p-6 shadow-2xl space-y-4 sm:space-y-5 max-h-[90dvh] overflow-y-auto">
            <div className="w-10 h-1 bg-foreground-muted/30 rounded-full mx-auto sm:hidden mb-1" />
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-brand/10 border border-brand/30 flex items-center justify-center text-brand shrink-0">
                <Loader2 className="w-5 h-5 animate-spin text-brand" />
              </div>
              <div>
                <h3 className="text-base font-bold text-foreground font-heading">
                  Running statutory analysis
                </h3>
                <p className="text-xs text-foreground-muted">
                  Executing deterministic MSME reconciliation pipeline
                </p>
              </div>
            </div>

            <div className="space-y-2.5 pt-1">
              {ANALYSIS_STEPS.map((step, idx) => {
                const isDone = idx < analysisStepIndex;
                const isCurrent = idx === analysisStepIndex;
                return (
                  <div
                    key={step}
                    data-testid={`stepper-step-${idx}`}
                    className={cn(
                      'flex items-center gap-3 p-2.5 rounded-xl border text-xs transition-all duration-300',
                      isDone && 'bg-brand/5 border-brand/30 text-foreground',
                      isCurrent && 'bg-surface-subtle border-brand text-foreground shadow-xs scale-[1.01]',
                      !isDone && !isCurrent && 'bg-surface-subtle/30 border-border/40 text-foreground-muted opacity-60'
                    )}
                  >
                    <div className="shrink-0">
                      {isDone ? (
                        <CheckCircle2 className="w-4 h-4 text-brand animate-in zoom-in-75 duration-200" />
                      ) : isCurrent ? (
                        <Loader2 className="w-4 h-4 text-brand animate-spin" />
                      ) : (
                        <div className="w-4 h-4 rounded-full border border-border flex items-center justify-center text-[10px] font-mono">
                          {idx + 1}
                        </div>
                      )}
                    </div>
                    <span className={cn('flex-1 font-medium', isCurrent && 'font-bold text-foreground')}>
                      {step}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Demo Mode Write Gate Modal */}
      {showDemoPrompt && (
        <div
          data-testid="demo-write-modal"
          className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/60 backdrop-blur-xs p-0 sm:p-4"
        >
          <div className="bg-surface border border-border rounded-t-3xl sm:rounded-2xl max-w-md w-full p-5 sm:p-6 shadow-2xl space-y-4 animate-in fade-in zoom-in-95 duration-150 max-h-[90dvh] overflow-y-auto">
            <div className="w-10 h-1 bg-foreground-muted/30 rounded-full mx-auto sm:hidden mb-1" />
            <div className="flex items-center gap-2.5 text-amber-500">
              <Lock className="w-5 h-5 text-brand" />
              <h3 className="text-base font-bold text-foreground">Sign Up Required for Custom Cases</h3>
            </div>
            <p className="text-xs text-foreground-muted leading-relaxed">
              The demo workspace is read-only for public demonstrations. To launch real extraction pipelines, upload custom invoices, and configure live RBI rates, please create an account.
            </p>
            <div className="flex flex-col-reverse sm:flex-row items-center justify-end gap-2 pt-2">
              <Button
                variant="secondary"
                size="sm"
                data-testid="demo-continue-run-btn"
                onClick={() => {
                  setShowDemoPrompt(false);
                  executeRunAnalysis();
                }}
                className="w-full sm:w-auto text-xs min-h-[44px]"
              >
                Proceed with Demo Case
              </Button>
              <Button
                size="sm"
                data-testid="demo-signup-redirect-btn"
                onClick={() => navigate('/signup')}
                className="w-full sm:w-auto text-xs font-bold min-h-[44px]"
              >
                Sign Up to Launch
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
