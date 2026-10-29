import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '../context/ToastContext';
import { SendActionPanel } from '../components/notifications/SendActionPanel';
import { CommunicationsTimeline } from '../components/notifications/CommunicationsTimeline';
import { SettingsPage } from './SettingsPage';
import { NewCasePage } from './NewCasePage';
import * as authHook from '../context/AuthContext';
import { api } from '../api/client';
import { MOCK_BUYER_CONTACT_DP0142, MOCK_NOTIFICATIONS_DP0142 } from '../mocks/fixtures';

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

const mockAuthContextValue = (isAnonymous = false) => ({
  user: {
    id: isAnonymous ? 'anon-user' : 'mock-user-id',
    email: isAnonymous ? undefined : 'owner@duespilot.example.com',
    is_anonymous: isAnonymous,
    app_metadata: {},
    user_metadata: {},
    aud: 'authenticated',
    created_at: new Date().toISOString(),
  } as any,
  session: {} as any,
  isLoading: false,
  profile: null,
  isAnonymous,
  flags: null,
  signInWithGoogle: vi.fn(),
  signInWithPassword: vi.fn(),
  signUpWithPassword: vi.fn(),
  signInAnonymously: vi.fn(),
  workspace: {
    id: 'ws-1',
    name: 'Basalt Heavy Engineering',
    businessName: 'Basalt Heavy Engineering Pvt Ltd',
    role: 'OWNER',
  },
  refreshProfile: vi.fn(),
  updateProfile: vi.fn(),
  signOut: vi.fn(),
});

describe('Notification and Settings Feature Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authHook, 'useAuth').mockReturnValue(mockAuthContextValue(false) as any);
  });

  it('renders buyer contact and opt-in fields on New Case page', () => {
    const queryClient = createTestQueryClient();
    render(
      <ToastProvider>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter>
            <NewCasePage />
          </MemoryRouter>
        </QueryClientProvider>
      </ToastProvider>
    );

    // Switch to step 2: Details
    fireEvent.click(screen.getByRole('button', { name: /2 Details/i }));

    // Verify buyer contact fields are rendered
    expect(screen.getByText(/Buyer Contact & Consent/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Accounts Payable \/ Finance Head/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/accounts@buyer\.com/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/\+919876543210/i)).toBeInTheDocument();

    // Verify per-channel opt-ins and consent note
    expect(screen.getByText(/Per-Channel Notification Opt-In/i)).toBeInTheDocument();
    expect(screen.getByText(/Consent provided via bilateral commercial agreement/i)).toBeInTheDocument();
  });

  it('renders SendActionPanel with channel selector, preview, language toggle, and checklist gating', async () => {
    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <SendActionPanel
            caseId="DP-0142"
            caseCode="DP-0142"
            actionTitle="Request delivery proof"
            englishDraft="Please share the signed delivery challan for invoice INV-2026-0142."
            hindiDraft="कृपया चालान INV-2026-0142 के लिए हस्ताक्षरित डिलीवरी चालान साझा करें।"
            contact={MOCK_BUYER_CONTACT_DP0142}
          />
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Header and action title
    expect(screen.getByText(/Dispatch Panel · Human-in-the-Loop Send/i)).toBeInTheDocument();
    expect(screen.getByText(/Request delivery proof/i)).toBeInTheDocument();

    // Channel options
    expect(screen.getByText(/Email \(Resend\)/i)).toBeInTheDocument();
    expect(screen.getByText(/WhatsApp \(wa\.me Link\)/i)).toBeInTheDocument();

    // Language toggle
    const hindiBtn = screen.getByRole('button', { name: 'हिंदी' });
    fireEvent.click(hindiBtn);
    expect(screen.getByText(/हस्ताक्षरित डिलीवरी चालान/i)).toBeInTheDocument();

    // Send button disabled initially before checklist
    const sendBtn = screen.getByRole('button', { name: /Send Formal Email/i });
    expect(sendBtn).toBeDisabled();

    // Tick the 3-point operator checklist items
    const checklistItems = screen.getAllByRole('checkbox').filter((el) => el.tagName !== 'INPUT');
    checklistItems.forEach((cb) => fireEvent.click(cb));

    // Send button should now be enabled
    expect(sendBtn).not.toBeDisabled();
  });

  it('renders demo mode restriction "Sign up to send" on SendActionPanel', () => {
    vi.spyOn(authHook, 'useAuth').mockReturnValue(mockAuthContextValue(true) as any);
    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <SendActionPanel
            caseId="DP-0142"
            caseCode="DP-0142"
            contact={MOCK_BUYER_CONTACT_DP0142}
          />
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Verify demo mode lock banner and action button
    expect(screen.getByText(/Demo workspace is read-only/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sign up to send/i })).toBeInTheDocument();
  });

  it('renders Communications timeline with channel chips, resend, and unverified inbound buyer messages', async () => {
    vi.spyOn(api, 'getNotifications').mockResolvedValue(MOCK_NOTIFICATIONS_DP0142);
    const queryClient = createTestQueryClient();
    const handleRerun = vi.fn();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <CommunicationsTimeline caseId="DP-0142" onRerunAnalysis={handleRerun} />
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Verify header
    expect(await screen.findByText(/Communications & Outbox Log/i)).toBeInTheDocument();

    // Verify inbound unverified message chip and Re-run analysis button
    expect(await screen.findByText('Unverified message')).toBeInTheDocument();
    const rerunBtn = await screen.findByRole('button', { name: /Re-run analysis/i });
    expect(rerunBtn).toBeInTheDocument();

    fireEvent.click(rerunBtn);
    expect(handleRerun).toHaveBeenCalled();
  });

  it('renders Settings page with business profile, notification defaults, sandbox, and provider status', async () => {
    const queryClient = createTestQueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <MemoryRouter>
            <SettingsPage />
          </MemoryRouter>
        </ToastProvider>
      </QueryClientProvider>
    );

    // Section: Demo Data
    expect(await screen.findByTestId('settings-demo-data-section')).toBeInTheDocument();

    // Section 1: Business Profile
    expect(await screen.findByText(/Business Profile & Statutory Identity/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue(/Basalt Heavy Engineering Pvt Ltd/i)).toBeInTheDocument();

    // Section 2: Notification Defaults
    expect(screen.getByText(/Notification Defaults & Formats/i)).toBeInTheDocument();
    expect(screen.getByText(/Attach Statement of Account PDF by default/i)).toBeInTheDocument();

    // Section 3: Sandbox Mode
    expect(screen.getByText(/Sandbox Mode \(Rerouting Protection\)/i)).toBeInTheDocument();
    expect(screen.getByText(/SANDBOX ACTIVE/i)).toBeInTheDocument();

    // Section 4: Sending Window
    expect(screen.getByText(/Sending Window & Harassment Guardrails/i)).toBeInTheDocument();
    expect(screen.getByText(/09:00 – 19:00 IST/i)).toBeInTheDocument();

    // Section 5: Provider Status (Never show keys)
    expect(screen.getByText(/Provider Status & Webhook Connections/i)).toBeInTheDocument();
    expect(screen.getByText(/Secret keys are never exposed in UI/i)).toBeInTheDocument();
    expect(screen.getByText(/Resend Email/i)).toBeInTheDocument();
    expect(screen.getAllByText(/WhatsApp Cloud API/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Server Vault/i).length).toBeGreaterThan(0);
  });
});
