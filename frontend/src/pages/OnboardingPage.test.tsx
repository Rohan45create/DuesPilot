import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { OnboardingPage } from './OnboardingPage';
import { api } from '../api/client';
import { supabase } from '../lib/supabase';

const renderOnboardingPage = () => {
  return render(
    <AuthProvider>
      <BrowserRouter>
        <OnboardingPage />
      </BrowserRouter>
    </AuthProvider>
  );
};

describe('OnboardingPage Component Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.spyOn(supabase.auth, 'getSession').mockResolvedValue({
      data: {
        session: {
          access_token: 'mock-token',
          refresh_token: 'mock-refresh',
          expires_in: 3600,
          token_type: 'bearer',
          user: {
            id: 'mock-user-1',
            email: 'msme@example.com',
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
          id: 'test-sub',
          callback: () => {},
          unsubscribe: () => {},
        },
      },
    });

    vi.spyOn(api, 'getMe').mockResolvedValue({
      user: { id: 'mock-user-1', email: 'msme@example.com', isAnonymous: false },
      workspace: {
        id: 'workspace-1',
        name: 'Apex Tools',
        businessName: 'Apex Tools',
        supplierSizeClass: 'MICRO',
        udyamRegistrationDate: null,
        ownerPhone: null,
        ownerWhatsapp: null,
        isDemo: false,
      },
      flags: {
        demoReadOnly: false,
        canCreateCases: true,
        requiresOnboarding: true,
      },
    });
  });

  it('renders onboarding form with prefilled business name and enterprise sizes', async () => {
    renderOnboardingPage();

    expect(await screen.findByRole('heading', { level: 1, name: /Complete your supplier profile/i })).toBeInTheDocument();
    expect(screen.getByTestId('onboarding-business-name')).toBeInTheDocument();
    expect(screen.getByTestId('size-class-micro')).toBeInTheDocument();
    expect(screen.getByTestId('size-class-small')).toBeInTheDocument();
    expect(screen.getByTestId('size-class-medium')).toBeInTheDocument();
    expect(screen.getByTestId('onboarding-udyam-date')).toBeInTheDocument();
    expect(screen.getByTestId('onboarding-phone')).toBeInTheDocument();
    expect(screen.getByTestId('onboarding-whatsapp')).toBeInTheDocument();
  });

  it('validates required business name before submitting', async () => {
    renderOnboardingPage();

    const businessNameInput = await screen.findByTestId('onboarding-business-name');
    fireEvent.change(businessNameInput, { target: { value: '   ' } });

    fireEvent.click(screen.getByTestId('onboarding-submit-button'));

    expect(await screen.findByText(/Business name is required/i)).toBeInTheDocument();
  });

  it('synchronizes WhatsApp with Phone when "Same" checkbox is checked', async () => {
    renderOnboardingPage();

    const phoneInput = await screen.findByTestId('onboarding-phone');
    const whatsappInput = screen.getByTestId('onboarding-whatsapp') as HTMLInputElement;

    fireEvent.change(phoneInput, { target: { value: '+919876543210' } });

    const sameCheckbox = screen.getByRole('checkbox', { name: /Same/i });
    fireEvent.click(sameCheckbox);

    expect(whatsappInput.value).toBe('+919876543210');
    expect(whatsappInput.disabled).toBe(true);
  });

  it('submits onboarding profile and calls updateMe', async () => {
    const updateSpy = vi.spyOn(api, 'updateMe').mockResolvedValue({
      user: { id: 'mock-user-1', email: 'msme@example.com', isAnonymous: false },
      workspace: {
        id: 'workspace-1',
        name: 'Apex Precision Engineering Ltd',
        businessName: 'Apex Precision Engineering Ltd',
        supplierSizeClass: 'SMALL',
        udyamRegistrationDate: '2023-01-15',
        ownerPhone: '+919876543210',
        ownerWhatsapp: '+919876543210',
        isDemo: false,
      },
      flags: {
        demoReadOnly: false,
        canCreateCases: true,
        requiresOnboarding: false,
      },
    });

    renderOnboardingPage();

    const businessNameInput = await screen.findByTestId('onboarding-business-name');
    fireEvent.change(businessNameInput, { target: { value: 'Apex Precision Engineering Ltd' } });
    fireEvent.click(screen.getByTestId('size-class-small'));

    fireEvent.change(screen.getByTestId('onboarding-phone'), { target: { value: '+919876543210' } });
    fireEvent.click(screen.getByRole('checkbox', { name: /Same/i }));

    fireEvent.click(screen.getByTestId('onboarding-submit-button'));

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          businessName: 'Apex Precision Engineering Ltd',
          supplierSizeClass: 'SMALL',
          ownerPhone: '+919876543210',
          ownerWhatsapp: '+919876543210',
        })
      );
    });
  });
});
