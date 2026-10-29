import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import App from './App.tsx';
import { formatPaiseToRupees } from './utils/money';
import { supabase } from './lib/supabase';

describe('DuesPilot App & Landing Page', () => {
  beforeEach(() => {
    // Mock matchMedia for jsdom
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      }),
    });

    // Mock IntersectionObserver for jsdom
    class MockIntersectionObserver implements IntersectionObserver {
      readonly root: Element | null = null;
      readonly rootMargin: string = '';
      readonly thresholds: ReadonlyArray<number> = [];
      observe = vi.fn();
      unobserve = vi.fn();
      disconnect = vi.fn();
      takeRecords = vi.fn(() => []);
    }
    Object.defineProperty(window, 'IntersectionObserver', {
      writable: true,
      configurable: true,
      value: MockIntersectionObserver,
    });

    // Mock active session for authenticated app shell tests
    vi.spyOn(supabase.auth, 'getSession').mockResolvedValue({
      data: {
        session: {
          access_token: 'mock-token',
          refresh_token: 'mock-refresh',
          expires_in: 3600,
          token_type: 'bearer',
          user: {
            id: 'mock-user-id',
            email: 'test@example.com',
            is_anonymous: false,
            app_metadata: {},
            user_metadata: {},
            aud: 'authenticated',
            created_at: new Date().toISOString(),
          },
        },
      },
      error: null,
    });

    vi.spyOn(supabase.auth, 'onAuthStateChange').mockReturnValue({
      data: {
        subscription: {
          id: 'mock-sub',
          callback: () => {},
          unsubscribe: () => {},
        },
      },
    });
  });

  it('renders public landing page at root "/"', async () => {
    window.history.pushState({}, 'Landing Page', '/');
    render(<App />);

    expect((await screen.findAllByText('DuesPilot')).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Know what you're really owed.")).toBeInTheDocument();
    expect(screen.getByText('Backed by the original proof.')).toBeInTheDocument();
    expect(screen.getByText('Evidence-first receivables reconciliation')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: /Messy in, verified out/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: /Every number has a receipt/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: /Conflicts it catches/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: /The right model for each job/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: /What DuesPilot refuses to do/i })).toBeInTheDocument();
    expect(screen.getByText(/Not legal advice\. Synthetic demo data\./i)).toBeInTheDocument();
  });

  it('renders App Overview at "/app" with brand and main receivables position heading', async () => {
    window.history.pushState({}, 'App Overview', '/app');
    render(<App />);

    expect(await screen.findByRole('heading', { level: 1, name: /Receivables position/i })).toBeInTheDocument();
    expect(await screen.findByText('Total invoiced')).toBeInTheDocument();
    expect(screen.getByText('Disputed')).toBeInTheDocument();
    expect(screen.getByText('Unverified')).toBeInTheDocument();
    expect(await screen.findByRole('navigation', { name: /Sidebar navigation/i })).toBeInTheDocument();
  });

  it('formats money strictly without client-side arithmetic (Rule R-45)', () => {
    expect(formatPaiseToRupees(5620000)).toBe('₹56,200');
    expect(formatPaiseToRupees(2184000000)).toBe('₹2,18,40,000');
    expect(formatPaiseToRupees(0)).toBe('₹0');
    expect(formatPaiseToRupees(-5000000)).toBe('−₹50,000');
    expect(formatPaiseToRupees(null)).toBe('Cannot determine');
  });

  it('renders Case Workspace with proof chips, gates, and action drafts', async () => {
    window.history.pushState({}, 'Case Workspace', '/app/cases/DP-0142');
    render(<App />);

    expect(await screen.findByText(/DP-0142 · Kestrel Industrial Ltd/i)).toBeInTheDocument();
    expect(await screen.findByText('Ledger and proofs')).toBeInTheDocument();
    expect(screen.getByText('Balance walk')).toBeInTheDocument();
    expect(screen.getByText('Eligibility gates')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Export case pack/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Fetch and confirm rate/i })).toBeInTheDocument();
  });

  it('redirects legacy /cases/DP-0142 to /app/cases/DP-0142', async () => {
    window.history.pushState({}, 'Legacy Case Link', '/cases/DP-0142');
    render(<App />);

    expect(await screen.findByText(/DP-0142 · Kestrel Industrial Ltd/i)).toBeInTheDocument();
  });

  it('renders Review Queue page with checklist and decision options', async () => {
    window.history.pushState({}, 'Review Queue', '/app/review');
    render(<App />);

    expect(await screen.findByRole('heading', { level: 1, name: /Review queue/i })).toBeInTheDocument();
    expect(await screen.findByText('Before approving')).toBeInTheDocument();
    expect(screen.getByText('Evidence reviewed')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Approve$/i })).toBeInTheDocument();
  });

  it('renders Benchmarks page with targets and placeholder states', async () => {
    window.history.pushState({}, 'Benchmarks', '/app/benchmarks');
    render(<App />);

    expect(await screen.findByRole('heading', { level: 1, name: /Benchmarks and cost/i })).toBeInTheDocument();
    expect(await screen.findByText('Balance exact match')).toBeInTheDocument();
    expect(screen.getAllByText('Target ≥ 90%').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole('button', { name: /Run benchmark/i })).toBeInTheDocument();
  });

  it('renders Models and rules page with routing, limits, and statutory settings', async () => {
    window.history.pushState({}, 'Models and rules', '/app/models');
    render(<App />);

    expect(await screen.findByRole('heading', { level: 1, name: /Models and rules/i })).toBeInTheDocument();
    expect(await screen.findByText('Model routing')).toBeInTheDocument();
    expect(screen.getByText('Nemotron 3 Nano')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Statutory settings/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Save limits/i })).toBeInTheDocument();
  });

  it('route guard: sends signed-out users to /signin when visiting protected /app', async () => {
    vi.spyOn(supabase.auth, 'getSession').mockResolvedValue({
      data: { session: null },
      error: null,
    });

    window.history.pushState({}, 'Protected App Area', '/app');
    render(<App />);

    expect(await screen.findByText(/Welcome back/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: /Welcome back/i })).toBeInTheDocument();
    expect(screen.getByTestId('demo-login-button')).toBeInTheDocument();
  });

  it('anonymous demo user: displays persistent demo mode banner when visiting /app', async () => {
    vi.spyOn(supabase.auth, 'getSession').mockResolvedValue({
      data: {
        session: {
          access_token: 'demo-token',
          refresh_token: 'demo-refresh',
          expires_in: 3600,
          token_type: 'bearer',
          user: {
            id: 'anon-user-1',
            email: undefined,
            is_anonymous: true,
            app_metadata: { provider: 'anonymous' },
            user_metadata: {},
            aud: 'authenticated',
            created_at: new Date().toISOString(),
          },
        },
      },
      error: null,
    });

    window.history.pushState({}, 'Overview Page', '/app');
    render(<App />);

    const banner = await screen.findByTestId('demo-mode-banner');
    expect(banner).toBeInTheDocument();
    expect(banner).toHaveTextContent(/Demo Mode \(Read-Only\)/i);
    expect(screen.getByRole('link', { name: /Create account/i })).toBeInTheDocument();
  });
});
