import { z } from 'zod';

/**
 * RFC 9457 Problem Details for HTTP APIs
 */
export const ProblemDetailSchema = z.object({
  type: z.string().optional(),
  title: z.string(),
  status: z.number(),
  detail: z.string().optional(),
  instance: z.string().optional(),
  code: z.string().optional(),
  invalidParams: z.array(z.object({
    name: z.string(),
    reason: z.string(),
  })).optional(),
});
export type ProblemDetail = z.infer<typeof ProblemDetailSchema>;

/**
 * Span coordinates in source document
 */
export const SourceSpanSchema = z.object({
  page: z.number().int(),
  charStart: z.number().int(),
  charEnd: z.number().int(),
  quote: z.string(),
  confidence: z.number().min(0).max(1).optional(),
});
export type SourceSpan = z.infer<typeof SourceSpanSchema>;

/**
 * Extracted & verified field
 */
export const ExtractedFieldSchema = z.object({
  id: z.string(),
  documentId: z.string(),
  fieldName: z.string(),
  fieldValue: z.string(),
  sourceSpan: SourceSpanSchema.nullable().optional(),
  verified: z.boolean(),
  modelUsed: z.string().optional(),
});
export type ExtractedField = z.infer<typeof ExtractedFieldSchema>;

/**
 * Uploaded Document metadata
 */
export const DocumentMetadataSchema = z.object({
  id: z.string(),
  caseId: z.string(),
  filename: z.string(),
  docType: z.enum(['INVOICE', 'PURCHASE_ORDER', 'PAYMENT', 'CREDIT_NOTE', 'DELIVERY_CHALLAN', 'MESSAGE']),
  pageCount: z.number().int(),
  sha256: z.string(),
  status: z.enum(['PARSED', 'PARSED_HINDI', 'PARSED_HINGLISH', 'VERIFIED', 'UNVERIFIED', 'FAILED', 'MISSING']),
  uploadedAt: z.string(),
  extractedFields: z.array(ExtractedFieldSchema).optional(),
});
export type DocumentMetadata = z.infer<typeof DocumentMetadataSchema>;

/**
 * Ledger entry and transaction proof
 */
export const LedgerEntrySchema = z.object({
  id: z.string(),
  date: z.string(),
  entryType: z.enum(['INVOICE', 'PAYMENT', 'CREDIT_NOTE', 'CLAIM', 'DISPUTED']),
  description: z.string(),
  amountPaise: z.number().int(),
  status: z.enum(['CONFIRMED', 'UNVERIFIED_EXCLUDED', 'DISPUTED', 'PENDING']),
  proofDoc: z.string().optional(),
  proofPage: z.number().int().optional(),
});
export type LedgerEntry = z.infer<typeof LedgerEntrySchema>;

/**
 * Balance walk waterfall step
 */
export const BalanceWalkItemSchema = z.object({
  label: z.string(),
  amountPaise: z.number().int(),
  stepType: z.enum(['INVOICE', 'PAYMENT', 'CREDIT', 'BALANCE']),
});
export type BalanceWalkItem = z.infer<typeof BalanceWalkItemSchema>;

/**
 * Conflict rule outcome (C1–C10)
 */
export const ConflictItemSchema = z.object({
  id: z.string(),
  ruleId: z.string(),
  title: z.string(),
  description: z.string(),
  evidenceSpans: z.array(SourceSpanSchema).optional(),
  evidenceRef: z.string().optional(),
  status: z.enum(['OPEN', 'ACKNOWLEDGED', 'RESOLVED']),
});
export type ConflictItem = z.infer<typeof ConflictItemSchema>;

/**
 * Eligibility Gate (A–D)
 */
export const GateStatusEnum = z.enum([
  'ELIGIBLE_FOR_REVIEW',
  'NOT_ESTABLISHED',
  'HUMAN_REVIEW_REQUIRED',
  'OUT_OF_SCOPE',
]);
export type GateStatus = z.infer<typeof GateStatusEnum>;

export const GateEvaluationSchema = z.object({
  gateId: z.enum(['GATE_A', 'GATE_B', 'GATE_C', 'GATE_D']),
  name: z.string(),
  status: GateStatusEnum,
  label: z.string(),
  reasons: z.array(z.string()),
  statutoryReference: z.string().optional(),
});
export type GateEvaluation = z.infer<typeof GateEvaluationSchema>;

/**
 * Provisional Interest Engine result
 */
export const InterestCalculationSchema = z.object({
  status: z.enum(['COMPUTED_PROVISIONAL', 'ABSTAINED_UNCONFIRMED_RATE', 'ABSTAINED_MISSING_ACCEPTANCE', 'NOT_APPLICABLE']),
  principalPaise: z.number().int().nullable().optional(),
  effectiveRatePercent: z.number().nullable().optional(),
  multiplier: z.number().default(3),
  compoundedInterestPaise: z.number().int().nullable().optional(),
  totalDuePaise: z.number().int().nullable().optional(),
  daysOverdue: z.number().int().nullable().optional(),
  calculationMonths: z.number().int().nullable().optional(),
  reasons: z.array(z.string()),
});
export type InterestCalculation = z.infer<typeof InterestCalculationSchema>;

/**
 * Rate Provider Candidate
 */
export const RateCandidateSchema = z.object({
  id: z.string(),
  ratePercent: z.number(),
  effectiveDate: z.string(),
  sourceUrl: z.string(),
  excerpt: z.string(),
  confirmed: z.boolean(),
  confirmedAt: z.string().nullable().optional(),
  confirmedBy: z.string().nullable().optional(),
});
export type RateCandidate = z.infer<typeof RateCandidateSchema>;

/**
 * Proposed Outbound Action
 */
export const ProposedActionSchema = z.object({
  id: z.string(),
  actionType: z.enum(['REQUEST_DELIVERY_PROOF', 'REQUEST_PAYMENT', 'SEND_STATEMENT', 'CLARIFY_TERMS', 'CLOSE_RECONCILED']),
  title: z.string(),
  englishDraft: z.string(),
  hindiDraft: z.string(),
  evidenceRefs: z.array(z.string()),
  status: z.enum(['PENDING', 'APPROVED', 'EDITED', 'REJECTED']),
  reviewedAt: z.string().nullable().optional(),
  reviewedBy: z.string().nullable().optional(),
});
export type ProposedAction = z.infer<typeof ProposedActionSchema>;

/**
 * Immutable Audit Event
 */
export const AuditEventSchema = z.object({
  id: z.string(),
  caseId: z.string().nullable().optional(),
  actor: z.string(),
  eventType: z.string(),
  payload: z.record(z.string(), z.unknown()),
  createdAt: z.string(),
});
export type AuditEvent = z.infer<typeof AuditEventSchema>;

/**
 * Complete Case Detail
 */
export const CaseDetailSchema = z.object({
  id: z.string(),
  code: z.string(),
  title: z.string(),
  supplierName: z.string(),
  buyerName: z.string(),
  supplierSizeClass: z.enum(['MICRO', 'SMALL', 'MEDIUM', 'UNKNOWN']),
  udyamRegDate: z.string().nullable().optional(),
  status: z.enum(['NEEDS_REVIEW', 'MISSING_PROOF', 'RECONCILED', 'CLARIFY', 'PARSED', 'ANALYZING', 'ARCHIVED']),
  invoicedPaise: z.number().int(),
  confirmedPaidPaise: z.number().int(),
  creditNotePaise: z.number().int().default(0),
  reconciledBalancePaise: z.number().int().nullable(),
  documents: z.array(DocumentMetadataSchema),
  ledger: z.array(LedgerEntrySchema),
  balanceWalk: z.array(BalanceWalkItemSchema).optional(),
  conflicts: z.array(ConflictItemSchema),
  gates: z.array(GateEvaluationSchema),
  interest: InterestCalculationSchema,
  proposedAction: ProposedActionSchema.nullable().optional(),
  auditTrail: z.array(AuditEventSchema).optional(),
  lastRunAt: z.string().optional(),
  modelRouting: z.string().optional(),
  costMicros: z.number().int().optional(),
  callCount: z.number().int().optional(),
  isDemoData: z.boolean().optional(),
  is_demo_data: z.boolean().optional(),
  createdAt: z.string(),
});
export type CaseDetail = z.infer<typeof CaseDetailSchema>;

/**
 * Overview Receivables Position
 */
export const OverviewSummarySchema = z.object({
  verifiedPaise: z.number().int(),
  verifiedSharePercent: z.number(),
  totalInvoicedPaise: z.number().int(),
  activeCasesCount: z.number().int(),
  disputedPaise: z.number().int(),
  openConflictsCount: z.number().int(),
  unverifiedPaise: z.number().int(),
  unverifiedStatusText: z.string(),
  statusBreakdown: z.object({
    verifiedPercent: z.number(),
    disputedPercent: z.number(),
    unverifiedPercent: z.number(),
  }),
  agingBreakdown: z.array(z.object({
    range: z.string(),
    amountPaise: z.number().int(),
    heightPercent: z.number(),
    color: z.enum(['green', 'amber', 'dark-amber']),
  })),
  liveCostCap: z.object({
    spentDollars: z.number(),
    limitDollars: z.number(),
  }),
  needsReviewCases: z.array(z.object({
    code: z.string(),
    reason: z.string(),
    tagType: z.enum(['conflict', 'missing-proof', 'clarify']),
  })),
  recentActivity: z.array(z.object({
    caseCode: z.string(),
    event: z.string(),
    amountPaise: z.number().int(),
    status: z.enum(['NEEDS_REVIEW', 'RECONCILED', 'PARSED', 'CLARIFY']),
    statusLabel: z.string(),
    when: z.string(),
  })),
});
export type OverviewSummary = z.infer<typeof OverviewSummarySchema>;

/**
 * Benchmark Dashboard Data
 */
export const BenchmarkSummarySchema = z.object({
  datasetVersion: z.string(),
  generatedCount: z.number().int(),
  holdoutCount: z.number().int(),
  balanceExactMatchPercent: z.number(),
  conflictRecallPercent: z.number(),
  falseConflictPercent: z.number(),
  unsupportedClaimsPercent: z.number(),
  abstentionAccuracyPercent: z.number(),
  costComparison: z.object({
    tieredRoutingCostUsd: z.number(),
    allUltraCostUsd: z.number(),
    savingsMultiplier: z.number(),
  }),
  accuracyByLanguage: z.array(z.object({
    language: z.string(),
    accuracyPercent: z.number(),
    isExperimental: z.boolean().optional(),
  })),
  experiments: z.array(z.object({
    id: z.string(),
    name: z.string(),
    setup: z.string(),
    metric: z.string(),
    result: z.string(),
  })),
});
export type BenchmarkSummary = z.infer<typeof BenchmarkSummarySchema>;

/**
 * User & Workspace Profile Schemas (GET /api/v1/me and PATCH /api/v1/me)
 */
export const UserProfileSchema = z.object({
  id: z.string(),
  email: z.string().nullable().optional(),
  isAnonymous: z.boolean(),
});
export type UserProfile = z.infer<typeof UserProfileSchema>;

export const WorkspaceProfileSchema = z.object({
  id: z.string(),
  name: z.string(),
  businessName: z.string().optional(),
  supplierSizeClass: z.string(),
  udyamRegistrationDate: z.string().nullable().optional(),
  ownerPhone: z.string().nullable().optional(),
  ownerWhatsapp: z.string().nullable().optional(),
  isDemo: z.boolean(),
});
export type WorkspaceProfile = z.infer<typeof WorkspaceProfileSchema>;

export const WorkspaceFlagsSchema = z.object({
  demoReadOnly: z.boolean(),
  canCreateCases: z.boolean(),
  requiresOnboarding: z.boolean().optional(),
});
export type WorkspaceFlags = z.infer<typeof WorkspaceFlagsSchema>;

export const MeResponseSchema = z.object({
  user: UserProfileSchema,
  workspace: WorkspaceProfileSchema,
  flags: WorkspaceFlagsSchema,
});
export type MeResponse = z.infer<typeof MeResponseSchema>;

export interface UpdateWorkspaceProfileRequest {
  businessName?: string;
  supplierSizeClass?: string;
  udyamRegistrationDate?: string | null;
  ownerPhone?: string | null;
  ownerWhatsapp?: string | null;
}

/**
 * Buyer Contact & Opt-in details per case (Rule R-74)
 */
export const BuyerContactSchema = z.object({
  id: z.string(),
  workspaceId: z.string().optional(),
  caseId: z.string().nullable().optional(),
  buyerName: z.string(),
  email: z.string().nullable().optional(),
  phone: z.string().nullable().optional(),
  whatsappNumber: z.string().nullable().optional(),
  emailOptIn: z.boolean().default(true),
  emailOptInSource: z.string().optional(),
  emailOptInAt: z.string().optional(),
  whatsappOptIn: z.boolean().default(true),
  whatsappOptInSource: z.string().optional(),
  whatsappOptInAt: z.string().optional(),
  unsubscribedAt: z.string().nullable().optional(),
  consentNote: z.string().optional(),
});
export type BuyerContact = z.infer<typeof BuyerContactSchema>;

/**
 * Notification Outbox & Communications Item (Rule R-72 to R-78)
 */
export const NotificationOutboxSchema = z.object({
  id: z.string(),
  workspace_id: z.string().optional(),
  case_id: z.string(),
  action_id: z.string().nullable().optional(),
  channel: z.enum(['EMAIL', 'WHATSAPP_LINK', 'WHATSAPP_API']),
  recipient: z.string(),
  template_key: z.string(),
  rendered_body: z.string(),
  status: z.enum(['QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'BOUNCED', 'MANUAL_PENDING', 'MANUAL_SENT']),
  provider_message_id: z.string().nullable().optional(),
  error: z.string().nullable().optional(),
  scheduled_for: z.string().nullable().optional(),
  sent_at: z.string().nullable().optional(),
  idempotency_key: z.string().nullable().optional(),
  created_at: z.string(),
  click_to_chat_url: z.string().nullable().optional(),
  rerouted_to_sandbox: z.boolean().optional(),
  original_recipient: z.string().nullable().optional(),
  isInbound: z.boolean().optional(),
});
export type NotificationOutbox = z.infer<typeof NotificationOutboxSchema>;

export interface SendNotificationRequest {
  action_id?: string | null;
  channel: 'EMAIL' | 'WHATSAPP_LINK' | 'WHATSAPP_API';
  recipient?: string;
  template_key?: string;
  body?: string;
  template_variables?: Record<string, string>;
  include_statement_pdf: boolean;
  subject?: string;
}

export interface WhatsAppSetupStep {
  stepNumber: number;
  title: string;
  instruction: string;
  referenceUrl?: string;
}

export interface WhatsAppSetupGuide {
  apiEnabled: boolean;
  mode: string;
  webhookUrl: string;
  steps: WhatsAppSetupStep[];
  approvedTemplates: string[];
  disclaimer: string;
}

export interface NotificationSettings {
  businessName: string;
  supplierSizeClass: string;
  udyamRegistrationDate?: string | null;
  ownerPhone: string;
  ownerWhatsapp: string;
  sandboxMode: boolean;
  sandboxEmail: string;
  sandboxPhone: string;
  defaultChannel: 'EMAIL' | 'WHATSAPP_LINK' | 'WHATSAPP_API';
  defaultLanguage: 'EN' | 'HI';
  defaultIncludePdf: boolean;
  sendingWindowStart: string;
  sendingWindowEnd: string;
  cooldownHours: number;
  maxFollowUps: number;
  timeZone: string;
  resendConnected: boolean;
  resendFromEmail: string;
  whatsAppMode: string;
  whatsAppCloudConfigured: boolean;
  resendStatus: 'CONNECTED' | 'NOT_CONFIGURED';
  whatsappStatus: 'CONNECTED' | 'NOT_CONFIGURED';
}

/**
 * Explicit Consent-based Demo Data Schemas (Rule R-84)
 */
export const DemoDataPreviewSchema = z.object({
  caseCount: z.number().int(),
  documentCount: z.number().int(),
  contactCount: z.number().int(),
  approximateStorageKb: z.number(),
  alreadySeeded: z.boolean(),
});
export type DemoDataPreview = z.infer<typeof DemoDataPreviewSchema>;

export const SeedDemoDataResponseSchema = z.object({
  success: z.boolean(),
  caseCount: z.number().int(),
  message: z.string(),
});
export type SeedDemoDataResponse = z.infer<typeof SeedDemoDataResponseSchema>;

export const DeleteDemoDataResponseSchema = z.object({
  success: z.boolean(),
  deletedCount: z.number().int(),
  message: z.string(),
});
export type DeleteDemoDataResponse = z.infer<typeof DeleteDemoDataResponseSchema>;


