import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { AuthPage } from './AuthPage';
import { supabase } from '../lib/supabase';

const renderAuthPage = (initialMode: 'signin' | 'signup' = 'signin') => {
  return render(
    <AuthProvider>
      <BrowserRouter>
        <AuthPage initialMode={initialMode} />
      </BrowserRouter>
    </AuthProvider>
  );
};

describe('AuthPage Component Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.spyOn(supabase.auth, 'getSession').mockResolvedValue({
      data: { session: null },
      error: null,
    });

    vi.spyOn(supabase.auth, 'onAuthStateChange').mockReturnValue({
      data: {
        subscription: {
          id: 'test-sub',
          callback: () => {},
          unsubscribe: () => {},
        },
      },
    });
  });

  it('renders sign in mode with Google, demo button, and mode toggle', () => {
    renderAuthPage('signin');

    expect(screen.getByRole('heading', { level: 1, name: /Welcome back/i })).toBeInTheDocument();
    expect(screen.getByTestId('demo-login-button')).toBeInTheDocument();
    expect(screen.getByTestId('google-signin-button')).toBeInTheDocument();
    expect(screen.getByTestId('email-input')).toBeInTheDocument();
    expect(screen.getByTestId('password-input')).toBeInTheDocument();
    expect(screen.getByTestId('submit-auth-button')).toHaveTextContent(/Sign in to DuesPilot/i);
    expect(screen.getByText(/Use Google or the demo/i)).toBeInTheDocument();
  });

  it('toggles between Sign In and Create Account modes', () => {
    renderAuthPage('signin');

    const createAccountTab = screen.getByRole('button', { name: /Create account/i });
    fireEvent.click(createAccountTab);

    expect(screen.getByRole('heading', { level: 1, name: /Start your MSME recovery/i })).toBeInTheDocument();
    expect(screen.getByTestId('submit-auth-button')).toHaveTextContent(/Create your account/i);
  });

  it('validates empty email and password on submission', async () => {
    renderAuthPage('signin');

    fireEvent.click(screen.getByTestId('submit-auth-button'));

    expect(await screen.findByTestId('email-error')).toHaveTextContent(/Email is required/i);
    expect(screen.getByTestId('password-error')).toHaveTextContent(/Password is required/i);
  });

  it('validates invalid email format', async () => {
    renderAuthPage('signin');

    fireEvent.change(screen.getByTestId('email-input'), { target: { value: 'not-an-email' } });
    fireEvent.change(screen.getByTestId('password-input'), { target: { value: 'password123' } });
    fireEvent.click(screen.getByTestId('submit-auth-button'));

    expect(await screen.findByTestId('email-error')).toHaveTextContent(/valid email address/i);
  });

  it('validates password length on sign up', async () => {
    renderAuthPage('signup');

    fireEvent.change(screen.getByTestId('email-input'), { target: { value: 'test@example.com' } });
    fireEvent.change(screen.getByTestId('password-input'), { target: { value: 'short' } });
    fireEvent.click(screen.getByTestId('submit-auth-button'));

    expect(await screen.findByTestId('password-error')).toHaveTextContent(/at least 8 characters/i);
  });

  it('displays password strength hint dynamically on sign up', () => {
    renderAuthPage('signup');

    const passwordInput = screen.getByTestId('password-input');

    // Weak
    fireEvent.change(passwordInput, { target: { value: 'weak' } });
    expect(screen.getByTestId('password-strength-label')).toHaveTextContent('Weak');

    // Medium
    fireEvent.change(passwordInput, { target: { value: 'MediumPass1' } });
    expect(screen.getByTestId('password-strength-label')).toHaveTextContent('Medium');

    // Strong
    fireEvent.change(passwordInput, { target: { value: 'Str0ng!Pass#2026' } });
    expect(screen.getByTestId('password-strength-label')).toHaveTextContent('Strong');
  });

  it('displays friendly error message for wrong password / invalid credentials', async () => {
    vi.spyOn(supabase.auth, 'signInWithPassword').mockResolvedValue({
      data: { user: null, session: null },
      error: new Error('Invalid login credentials') as any,
    });

    renderAuthPage('signin');

    fireEvent.change(screen.getByTestId('email-input'), { target: { value: 'user@example.com' } });
    fireEvent.change(screen.getByTestId('password-input'), { target: { value: 'wrongpassword' } });
    fireEvent.click(screen.getByTestId('submit-auth-button'));

    const banner = await screen.findByTestId('auth-error-banner');
    expect(banner).toHaveTextContent(/Incorrect email or password/i);
  });

  it('displays friendly error message for already registered user on sign up', async () => {
    vi.spyOn(supabase.auth, 'signUp').mockResolvedValue({
      data: { user: null, session: null },
      error: new Error('User already registered') as any,
    });

    renderAuthPage('signup');

    fireEvent.change(screen.getByTestId('email-input'), { target: { value: 'existing@example.com' } });
    fireEvent.change(screen.getByTestId('password-input'), { target: { value: 'ValidPass123!' } });
    fireEvent.click(screen.getByTestId('submit-auth-button'));

    const banner = await screen.findByTestId('auth-error-banner');
    expect(banner).toHaveTextContent(/already exists/i);
  });

  it('displays friendly error message for rate-limited requests', async () => {
    vi.spyOn(supabase.auth, 'signInWithPassword').mockResolvedValue({
      data: { user: null, session: null },
      error: new Error('Over email rate limit: too many requests') as any,
    });

    renderAuthPage('signin');

    fireEvent.change(screen.getByTestId('email-input'), { target: { value: 'user@example.com' } });
    fireEvent.change(screen.getByTestId('password-input'), { target: { value: 'ValidPass123!' } });
    fireEvent.click(screen.getByTestId('submit-auth-button'));

    const banner = await screen.findByTestId('auth-error-banner');
    expect(banner).toHaveTextContent(/Too many login attempts/i);
  });

  it('triggers anonymous sign-in when "Try demo - no sign-up" button is clicked', async () => {
    const signInAnonSpy = vi.spyOn(supabase.auth, 'signInAnonymously').mockResolvedValue({
      data: {
        session: {
          access_token: 'anon-token',
          refresh_token: 'anon-refresh',
          expires_in: 3600,
          token_type: 'bearer',
          user: {
            id: 'anon-user-id',
            is_anonymous: true,
            app_metadata: { provider: 'anonymous' },
            user_metadata: {},
            aud: 'authenticated',
            created_at: new Date().toISOString(),
          },
        },
        user: {
          id: 'anon-user-id',
          is_anonymous: true,
          app_metadata: { provider: 'anonymous' },
          user_metadata: {},
          aud: 'authenticated',
          created_at: new Date().toISOString(),
        },
      },
      error: null,
    });

    renderAuthPage('signin');

    fireEvent.click(screen.getByTestId('demo-login-button'));

    await waitFor(() => {
      expect(signInAnonSpy).toHaveBeenCalled();
    });
  });

  it('toggles password visibility with eye icon', () => {
    renderAuthPage('signin');

    const passwordInput = screen.getByTestId('password-input') as HTMLInputElement;
    expect(passwordInput.type).toBe('password');

    const toggleButton = screen.getByRole('button', { name: /Show password/i });
    fireEvent.click(toggleButton);

    expect(passwordInput.type).toBe('text');
  });
});
