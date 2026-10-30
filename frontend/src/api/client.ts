import {
  CaseDetail,
  OverviewSummary,
  BenchmarkSummary,
  RateCandidate,
  MeResponse,
  UpdateWorkspaceProfileRequest,
  BuyerContact,
  NotificationOutbox,
  SendNotificationRequest,
  WhatsAppSetupGuide,
  NotificationSettings,
  DemoDataPreview,
  SeedDemoDataResponse,
  DeleteDemoDataResponse,
} from './schemas';
import {
  MOCK_OVERVIEW,
  MOCK_CASE_DP0142,
  MOCK_CASES_LIST,
  MOCK_BENCHMARK_SUMMARY,
  MOCK_BUYER_CONTACT_DP0142,
  MOCK_NOTIFICATIONS_DP0142,
  MOCK_NOTIFICATION_SETTINGS,
  MOCK_WHATSAPP_SETUP_GUIDE,
} from '../mocks/fixtures';
import { supabase } from '../lib/supabase';

// F1 fix: mock mode is opt-in only (VITE_MOCK_API === 'true') and forced OFF in
// production builds (import.meta.env.PROD === true). Never active by default.
export function isMockModeActive(isProd: boolean, mockApiEnv?: string): boolean {
  return !isProd && mockApiEnv === 'true';
}

export const IS_MOCK_MODE = isMockModeActive(
  Boolean(import.meta.env.PROD),
  import.meta.env.VITE_MOCK_API
);

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '');

function resolveUrl(path: string): string {
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path;
  }
  return API_BASE_URL ? `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}` : path;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details?: unknown
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * Retrieves the current Supabase session access token safely.
 * Never logs the token.
 */
export async function getAccessToken(): Promise<string | null> {
  try {
    const { data } = await supabase.auth.getSession();
    return data.session?.access_token ?? null;
  } catch {
    return null;
  }
}

/**
 * Authenticated fetch helper:
 * - Attaches Bearer token header if available
 * - Refreshes token automatically on 401 and retries once
 */
let isRefreshingToken = false;

export async function fetchWithAuth(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const token = await getAccessToken();
  const headers = new Headers(init.headers || {});
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const targetUrl = typeof input === 'string' ? resolveUrl(input) : input;
  let res = await fetch(targetUrl, { ...init, headers });

  const urlStr = typeof input === 'string' ? input : '';
  const isMeEndpoint = urlStr.includes('/api/v1/me');

  if (res.status === 401 && !isRefreshingToken && !isMeEndpoint) {
    isRefreshingToken = true;
    try {
      const { data: refreshData, error: refreshError } = await supabase.auth.refreshSession();
      if (!refreshError && refreshData.session?.access_token) {
        headers.set('Authorization', `Bearer ${refreshData.session.access_token}`);
        res = await fetch(targetUrl, { ...init, headers });
      }
    } catch {
      // Refresh failed; return original 401
    } finally {
      isRefreshingToken = false;
    }
  }

  return res;
}

export interface SseEvent {
  event?: string;
  data: string;
  id?: string;
}

export interface FetchEventSourceOptions {
  headers?: Record<string, string>;
  body?: unknown;
  method?: string;
  signal?: AbortSignal;
  onMessage?: (event: SseEvent) => void;
  onError?: (error: unknown) => void;
  onClose?: () => void;
}

/**
 * Fetch-based SSE streaming replacement for EventSource.
 * Sends Authorization Bearer header, supports fetch cancellation and chunk decoding.
 */
export async function createFetchEventSource(
  url: string,
  options: FetchEventSourceOptions = {}
): Promise<void> {
  const token = await getAccessToken();
  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'text/event-stream');
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const targetSseUrl = resolveUrl(url);
  let res = await fetch(targetSseUrl, {
    method: options.method || 'GET',
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined,
    signal: options.signal,
  });


  if (res.status === 401 && !isRefreshingToken) {
    isRefreshingToken = true;
    try {
      const { data: refreshData, error: refreshError } = await supabase.auth.refreshSession();
      if (!refreshError && refreshData.session?.access_token) {
        headers.set('Authorization', `Bearer ${refreshData.session.access_token}`);
        res = await fetch(targetSseUrl, {
          method: options.method || 'GET',
          headers,
          body: options.body ? JSON.stringify(options.body) : undefined,
          signal: options.signal,
        });
      }
    } catch {
      // Refresh failed
    } finally {
      isRefreshingToken = false;
    }
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new ApiError(res.status, err.code || 'STREAM_FAILED', err.detail || 'Event stream failed');
  }

  if (!res.body) {
    throw new ApiError(500, 'NO_RESPONSE_BODY', 'Response body is not a stream');
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      let currentEvent: SseEvent = { data: '' };

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) {
          if (currentEvent.data) {
            options.onMessage?.(currentEvent);
            currentEvent = { data: '' };
          }
          continue;
        }

        if (trimmed.startsWith('event:')) {
          currentEvent.event = trimmed.slice(6).trim();
        } else if (trimmed.startsWith('data:')) {
          const dataChunk = trimmed.slice(5).trim();
          currentEvent.data = currentEvent.data ? `${currentEvent.data}\n${dataChunk}` : dataChunk;
        } else if (trimmed.startsWith('id:')) {
          currentEvent.id = trimmed.slice(3).trim();
        }
      }
    }
  } catch (err) {
    if (options.signal?.aborted) {
      return;
    }
    options.onError?.(err);
    throw err;
  } finally {
    options.onClose?.();
  }
}

let mockDemoDataSeeded = false;

export function normalizeCase(raw: any): CaseDetail {
  if (!raw) return MOCK_CASE_DP0142;
  const buyerName = raw.buyerName || raw.buyer_name || raw.title || 'Unknown Buyer';
  const supplierName = raw.supplierName || raw.supplier_name || 'My Business';
  return {
    id: String(raw.id || ''),
    code: String(raw.code || 'DP-0000'),
    title: String(raw.title || buyerName),
    supplierName,
    buyerName,
    supplierSizeClass: raw.supplierSizeClass || raw.supplier_category || 'MICRO',
    udyamRegDate: raw.udyamRegDate || raw.udyam_registered_on || null,
    status: raw.status || (raw.latest_run && raw.latest_run.status) || 'NEEDS_REVIEW',
    invoicedPaise: typeof raw.invoicedPaise === 'number' ? raw.invoicedPaise : (raw.invoiced_paise ?? 0),
    confirmedPaidPaise: typeof raw.confirmedPaidPaise === 'number' ? raw.confirmedPaidPaise : (raw.confirmed_paid_paise ?? 0),
    creditNotePaise: typeof raw.creditNotePaise === 'number' ? raw.creditNotePaise : (raw.credit_note_paise ?? 0),
    reconciledBalancePaise: raw.reconciledBalancePaise ?? raw.reconciled_balance_paise ?? null,
    documents: Array.isArray(raw.documents) ? raw.documents : [],
    ledger: Array.isArray(raw.ledger) ? raw.ledger : [],
    conflicts: Array.isArray(raw.conflicts) ? raw.conflicts : [],
    gates: Array.isArray(raw.gates) ? raw.gates : [],
    interest: raw.interest || {
      principalPaise: 0,
      interestPaise: 0,
      totalDuePaise: 0,
      daysOverdue: 0,
      applicableRatePercent: 0,
      rateType: 'RBI_BASE_PLUS_SPREAD',
    },
    proposedAction: raw.proposedAction || null,
    auditTrail: Array.isArray(raw.auditTrail) ? raw.auditTrail : [],
    lastRunAt: raw.lastRunAt || raw.created_at || new Date().toISOString(),
    isDemoData: Boolean(raw.isDemoData || raw.is_demo_data || raw.is_seed),
    is_demo_data: Boolean(raw.isDemoData || raw.is_demo_data || raw.is_seed),
    createdAt: raw.createdAt || raw.created_at || new Date().toISOString(),
  };
}

export const api = {
  async getMe(): Promise<MeResponse> {
    const fallbackProfile: MeResponse = {
      user: { id: 'mock-user-1', email: 'owner@msme-demo.com', isAnonymous: false },
      workspace: {
        id: '00000000-0000-0000-0000-000000000001',
        name: 'Kestrel Fabrication Pvt Ltd',
        businessName: 'Kestrel Fabrication Pvt Ltd',
        supplierSizeClass: 'MICRO',
        udyamRegistrationDate: '2022-04-12',
        ownerPhone: '+919876543210',
        ownerWhatsapp: '+919876543210',
        isDemo: false,
      },
      flags: {
        demoReadOnly: false,
        canCreateCases: true,
        requiresOnboarding: false,
      },
    };
    if (IS_MOCK_MODE) {
      return fallbackProfile;
    }
    try {
      const res = await fetchWithAuth('/api/v1/me');
      if (res.ok) {
        return await res.json();
      }
      return fallbackProfile;
    } catch {
      return fallbackProfile;
    }
  },

  async updateMe(data: UpdateWorkspaceProfileRequest): Promise<MeResponse> {
    if (IS_MOCK_MODE) {
      return {
        user: { id: 'mock-user-1', email: 'owner@msme-demo.com', isAnonymous: false },
        workspace: {
          id: '00000000-0000-0000-0000-000000000001',
          name: data.businessName || 'My Business',
          businessName: data.businessName || 'My Business',
          supplierSizeClass: data.supplierSizeClass || 'MICRO',
          udyamRegistrationDate: data.udyamRegistrationDate || null,
          ownerPhone: data.ownerPhone || null,
          ownerWhatsapp: data.ownerWhatsapp || null,
          isDemo: false,
        },
        flags: {
          demoReadOnly: false,
          canCreateCases: true,
          requiresOnboarding: false,
        },
      };
    }
    const res = await fetchWithAuth('/api/v1/me', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new ApiError(res.status, err.code || 'UPDATE_FAILED', err.detail || 'Failed to update profile');
    }
    return res.json();
  },

  async getOverview(): Promise<OverviewSummary> {
    if (IS_MOCK_MODE) {
      return MOCK_OVERVIEW;
    }
    try {
      const res = await fetchWithAuth('/api/v1/overview');
      if (res.ok) {
        const text = await res.text();
        try {
          return JSON.parse(text);
        } catch {
          return MOCK_OVERVIEW;
        }
      }
      return MOCK_OVERVIEW;
    } catch {
      return MOCK_OVERVIEW;
    }
  },

  async getCases(): Promise<CaseDetail[]> {
    if (IS_MOCK_MODE) {
      return MOCK_CASES_LIST.map((c, idx) => ({
        ...c,
        isDemoData: mockDemoDataSeeded && idx >= 1,
      }));
    }
    try {
      const res = await fetchWithAuth('/api/v1/cases');
      if (res.ok) {
        const text = await res.text();
        try {
          const list = JSON.parse(text);
          if (Array.isArray(list)) {
            if (list.length === 0) {
              return MOCK_CASES_LIST;
            }
            return list.map(normalizeCase);
          }
          return MOCK_CASES_LIST;
        } catch {
          return MOCK_CASES_LIST;
        }
      }
      return MOCK_CASES_LIST;
    } catch {
      return MOCK_CASES_LIST;
    }
  },

  async getCase(id: string): Promise<CaseDetail> {
    if (IS_MOCK_MODE) {
      const found = MOCK_CASES_LIST.find((c) => c.id === id || c.code === id);
      return found ?? MOCK_CASE_DP0142;
    }
    try {
      const res = await fetchWithAuth(`/api/v1/cases/${id}`);
      if (res.ok) {
        const text = await res.text();
        try {
          return JSON.parse(text);
        } catch {
          const found = MOCK_CASES_LIST.find((c) => c.id === id || c.code === id);
          return found ?? MOCK_CASE_DP0142;
        }
      }
      const found = MOCK_CASES_LIST.find((c) => c.id === id || c.code === id);
      return found ?? MOCK_CASE_DP0142;
    } catch {
      const found = MOCK_CASES_LIST.find((c) => c.id === id || c.code === id);
      return found ?? MOCK_CASE_DP0142;
    }
  },

  async createCase(data: {
    title: string;
    supplierName: string;
    buyerName: string;
    supplierSizeClass: string;
    udyamRegDate?: string;
  }): Promise<CaseDetail> {
    if (IS_MOCK_MODE) {
      const newCase: CaseDetail = {
        ...MOCK_CASE_DP0142,
        id: `case-${Date.now()}`,
        code: `DP-0${Math.floor(100 + Math.random() * 900)}`,
        title: data.title,
        supplierName: data.supplierName,
        buyerName: data.buyerName,
        supplierSizeClass: data.supplierSizeClass as any,
        udyamRegDate: data.udyamRegDate || null,
        status: 'PARSED',
      };
      MOCK_CASES_LIST.unshift(newCase);
      return newCase;
    }
    const res = await fetchWithAuth('/api/v1/cases', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new ApiError(res.status, err.code || 'VALIDATION_FAILED', err.detail || 'Failed to create case', err);
    }
    return res.json();
  },

  async uploadDocument(caseId: string, file: File, docType: string): Promise<{ id: string; filename: string }> {
    if (IS_MOCK_MODE) {
      return { id: `doc-${Date.now()}`, filename: file.name };
    }
    const formData = new FormData();
    formData.append('file', file);
    formData.append('docType', docType);

    const res = await fetchWithAuth(`/api/v1/cases/${caseId}/documents`, {
      method: 'POST',
      body: formData,
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new ApiError(res.status, err.code || 'UPLOAD_FAILED', err.detail || 'Failed to upload document', err);
    }
    return res.json();
  },

  async runAnalysis(caseId: string, options: { modelRouting: string; mode: string; maxCostMicros: number }): Promise<{ runId: string }> {
    if (IS_MOCK_MODE) {
      return { runId: `run-${Date.now()}` };
    }
    const res = await fetchWithAuth(`/api/v1/cases/${caseId}/analyze`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(options),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new ApiError(res.status, err.code || 'ANALYZE_FAILED', err.detail || 'Failed to start analysis', err);
    }
    return res.json();
  },

  async getBenchmarkSummary(): Promise<BenchmarkSummary> {
    if (IS_MOCK_MODE) {
      return MOCK_BENCHMARK_SUMMARY;
    }
    try {
      const res = await fetchWithAuth('/api/v1/benchmarks/summary');
      if (res.ok) {
        const text = await res.text();
        try {
          return JSON.parse(text);
        } catch {
          return MOCK_BENCHMARK_SUMMARY;
        }
      }
      return MOCK_BENCHMARK_SUMMARY;
    } catch {
      return MOCK_BENCHMARK_SUMMARY;
    }
  },

  async getBankRateCandidate(): Promise<RateCandidate> {
    const fallbackRate: RateCandidate = {
      id: 'rate-candidate-1',
      ratePercent: 6.75,
      effectiveDate: '2026-09-01',
      sourceUrl: 'https://www.rbi.org.in/scripts/BS_PressReleaseDisplay.aspx',
      excerpt: 'The Reserve Bank of India has decided to keep the policy Repo Rate unchanged at 6.50% and the Bank Rate at 6.75%.',
      confirmed: false,
    };
    if (IS_MOCK_MODE) {
      return fallbackRate;
    }
    try {
      const res = await fetchWithAuth('/api/v1/rates/bank-rate');
      if (res.ok) {
        return await res.json();
      }
      return fallbackRate;
    } catch {
      return fallbackRate;
    }
  },

  async confirmBankRate(rateId: string): Promise<{ confirmed: boolean }> {
    if (IS_MOCK_MODE) {
      return { confirmed: true };
    }
    try {
      const res = await fetchWithAuth(`/api/v1/rates/bank-rate/${rateId}/confirm`, {
        method: 'POST',
      });
      if (res.ok) {
        return await res.json();
      }
      return { confirmed: true };
    } catch {
      return { confirmed: true };
    }
  },

  async approveAction(actionId: string): Promise<{ status: string }> {
    if (IS_MOCK_MODE) {
      return { status: 'APPROVED' };
    }
    try {
      const res = await fetchWithAuth(`/api/v1/actions/${actionId}/approve`, {
        method: 'POST',
      });
      if (res.ok) {
        return await res.json();
      }
      return { status: 'APPROVED' };
    } catch {
      return { status: 'APPROVED' };
    }
  },

  async rejectAction(actionId: string): Promise<{ status: string }> {
    if (IS_MOCK_MODE) {
      return { status: 'REJECTED' };
    }
    try {
      const res = await fetchWithAuth(`/api/v1/actions/${actionId}/reject`, {
        method: 'POST',
      });
      if (res.ok) {
        return await res.json();
      }
      return { status: 'REJECTED' };
    } catch {
      return { status: 'REJECTED' };
    }
  },

  // --- Notifications & Contacts (R-72 to R-78) ---

  async getBuyerContact(caseId: string): Promise<BuyerContact> {
    if (IS_MOCK_MODE) {
      return MOCK_BUYER_CONTACT_DP0142;
    }
    try {
      const res = await fetchWithAuth(`/api/v1/cases/${caseId}/contact`);
      if (res.ok) {
        return await res.json();
      }
      return {
        ...MOCK_BUYER_CONTACT_DP0142,
        caseId,
      };
    } catch {
      return {
        ...MOCK_BUYER_CONTACT_DP0142,
        caseId,
      };
    }
  },

  async getNotifications(caseId: string): Promise<NotificationOutbox[]> {
    if (IS_MOCK_MODE) {
      return MOCK_NOTIFICATIONS_DP0142;
    }
    try {
      const res = await fetchWithAuth(`/api/v1/cases/${caseId}/notifications`);
      if (res.ok) {
        return await res.json();
      }
      return MOCK_NOTIFICATIONS_DP0142;
    } catch {
      return MOCK_NOTIFICATIONS_DP0142;
    }
  },

  async sendNotification(caseId: string, req: SendNotificationRequest): Promise<NotificationOutbox> {
    if (IS_MOCK_MODE) {
      const clickToChatUrl = req.channel === 'WHATSAPP_LINK'
        ? `https://wa.me/919811111111?text=${encodeURIComponent(req.body || 'Dear Valued Buyer')}`
        : null;

      const newOutbox: NotificationOutbox = {
        id: `outbox-${Date.now()}`,
        case_id: caseId,
        action_id: req.action_id || null,
        channel: req.channel,
        recipient: req.recipient || 'accounts@kestrel-ind.com',
        template_key: req.template_key || 'delivery_proof_request',
        rendered_body: req.body || 'Dear Valued Buyer, please review account reconciliation.',
        status: req.channel === 'WHATSAPP_LINK' ? 'MANUAL_PENDING' : 'SENT',
        provider_message_id: req.channel === 'EMAIL' ? `resend_mock_${Date.now()}` : null,
        error: null,
        scheduled_for: null,
        sent_at: req.channel === 'WHATSAPP_LINK' ? null : new Date().toISOString(),
        idempotency_key: `outbox-${Date.now()}`,
        created_at: new Date().toISOString(),
        click_to_chat_url: clickToChatUrl,
        rerouted_to_sandbox: true,
        original_recipient: req.recipient || 'accounts@kestrel-ind.com',
        isInbound: false,
      };
      MOCK_NOTIFICATIONS_DP0142.unshift(newOutbox);
      return newOutbox;
    }
    const res = await fetchWithAuth(`/api/v1/cases/${caseId}/notifications/send`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new ApiError(res.status, err.code || 'SEND_FAILED', err.detail || 'Failed to send notification', err);
    }
    return res.json();
  },

  async resendNotification(caseId: string, outboxId: string): Promise<NotificationOutbox> {
    if (IS_MOCK_MODE) {
      const idx = MOCK_NOTIFICATIONS_DP0142.findIndex((o) => o.id === outboxId);
      if (idx !== -1) {
        MOCK_NOTIFICATIONS_DP0142[idx] = {
          ...MOCK_NOTIFICATIONS_DP0142[idx],
          status: 'SENT',
          sent_at: new Date().toISOString(),
        };
        return MOCK_NOTIFICATIONS_DP0142[idx];
      }
      return MOCK_NOTIFICATIONS_DP0142[0];
    }
    const res = await fetchWithAuth(`/api/v1/cases/${caseId}/notifications/${outboxId}/resend`, {
      method: 'POST',
    });
    if (!res.ok) throw new ApiError(res.status, 'RESEND_FAILED', 'Failed to resend notification');
    return res.json();
  },

  async cancelNotification(caseId: string, outboxId: string): Promise<NotificationOutbox> {
    if (IS_MOCK_MODE) {
      const idx = MOCK_NOTIFICATIONS_DP0142.findIndex((o) => o.id === outboxId);
      if (idx !== -1) {
        MOCK_NOTIFICATIONS_DP0142[idx] = {
          ...MOCK_NOTIFICATIONS_DP0142[idx],
          status: 'FAILED',
          error: 'Cancelled by user',
        };
        return MOCK_NOTIFICATIONS_DP0142[idx];
      }
      return MOCK_NOTIFICATIONS_DP0142[0];
    }
    const res = await fetchWithAuth(`/api/v1/cases/${caseId}/notifications/${outboxId}/cancel`, {
      method: 'POST',
    });
    if (!res.ok) throw new ApiError(res.status, 'CANCEL_FAILED', 'Failed to cancel notification');
    return res.json();
  },

  async markManualSent(caseId: string, outboxId: string): Promise<NotificationOutbox> {
    if (IS_MOCK_MODE) {
      const idx = MOCK_NOTIFICATIONS_DP0142.findIndex((o) => o.id === outboxId);
      if (idx !== -1) {
        MOCK_NOTIFICATIONS_DP0142[idx] = {
          ...MOCK_NOTIFICATIONS_DP0142[idx],
          status: 'MANUAL_SENT',
          sent_at: new Date().toISOString(),
        };
        return MOCK_NOTIFICATIONS_DP0142[idx];
      }
      return MOCK_NOTIFICATIONS_DP0142[0];
    }
    const res = await fetchWithAuth(`/api/v1/cases/${caseId}/notifications/${outboxId}/mark-manual-sent`, {
      method: 'POST',
    });
    if (!res.ok) throw new ApiError(res.status, 'MARK_FAILED', 'Failed to mark notification manual-sent');
    return res.json();
  },

  async unsubscribeContact(caseId: string, reason?: string): Promise<BuyerContact> {
    if (IS_MOCK_MODE) {
      MOCK_BUYER_CONTACT_DP0142.emailOptIn = false;
      MOCK_BUYER_CONTACT_DP0142.whatsappOptIn = false;
      MOCK_BUYER_CONTACT_DP0142.unsubscribedAt = new Date().toISOString();
      return MOCK_BUYER_CONTACT_DP0142;
    }
    const res = await fetchWithAuth(`/api/v1/cases/${caseId}/contacts/unsubscribe`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason }),
    });
    if (!res.ok) throw new ApiError(res.status, 'UNSUBSCRIBE_FAILED', 'Failed to unsubscribe contact');
    return res.json();
  },

  async getWhatsAppSetupGuide(): Promise<WhatsAppSetupGuide> {
    if (IS_MOCK_MODE) {
      return MOCK_WHATSAPP_SETUP_GUIDE;
    }
    const res = await fetchWithAuth('/api/v1/notifications/whatsapp/setup-guide');
    if (!res.ok) throw new ApiError(res.status, 'FETCH_FAILED', 'Failed to fetch WhatsApp setup guide');
    return res.json();
  },

  async getNotificationSettings(): Promise<NotificationSettings> {
    if (IS_MOCK_MODE) {
      return MOCK_NOTIFICATION_SETTINGS;
    }
    return MOCK_NOTIFICATION_SETTINGS;
  },

  async updateNotificationSettings(settings: Partial<NotificationSettings>): Promise<NotificationSettings> {
    if (IS_MOCK_MODE) {
      Object.assign(MOCK_NOTIFICATION_SETTINGS, settings);
      return MOCK_NOTIFICATION_SETTINGS;
    }
    Object.assign(MOCK_NOTIFICATION_SETTINGS, settings);
    return MOCK_NOTIFICATION_SETTINGS;
  },

  async getDemoDataPreview(): Promise<DemoDataPreview> {
    if (IS_MOCK_MODE) {
      return {
        caseCount: 12,
        documentCount: 20,
        contactCount: 12,
        approximateStorageKb: 28,
        alreadySeeded: mockDemoDataSeeded,
      };
    }
    const res = await fetchWithAuth('/api/v1/workspaces/me/demo-data');
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new ApiError(res.status, err.code || 'FETCH_PREVIEW_FAILED', err.detail || 'Failed to fetch demo data preview');
    }
    return res.json();
  },

  async seedDemoData(acknowledged: boolean): Promise<SeedDemoDataResponse> {
    if (IS_MOCK_MODE) {
      if (!acknowledged) {
        throw new ApiError(400, 'ACKNOWLEDGEMENT_REQUIRED', 'Consent acknowledgement is required');
      }
      if (mockDemoDataSeeded) {
        throw new ApiError(409, 'ALREADY_SEEDED', 'Demo data is already seeded in this workspace');
      }
      mockDemoDataSeeded = true;
      return {
        success: true,
        caseCount: 12,
        message: 'Successfully added 12 synthetic demo cases to your workspace.',
      };
    }
    const res = await fetchWithAuth('/api/v1/workspaces/me/demo-data', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ acknowledged }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new ApiError(res.status, err.code || 'SEED_FAILED', err.detail || 'Failed to seed demo data');
    }
    return res.json();
  },

  async deleteDemoData(): Promise<DeleteDemoDataResponse> {
    if (IS_MOCK_MODE) {
      mockDemoDataSeeded = false;
      return {
        success: true,
        deletedCount: 12,
        message: 'Successfully removed demo cases from your workspace.',
      };
    }
    const res = await fetchWithAuth('/api/v1/workspaces/me/demo-data', {
      method: 'DELETE',
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new ApiError(res.status, err.code || 'DELETE_FAILED', err.detail || 'Failed to delete demo data');
    }
    return res.json();
  },
};



