import { test, expect } from '@playwright/test';

test.describe('DuesPilot Authentication & Demo Login Flow', () => {
  test.beforeEach(async ({ page }) => {
    // Intercept Supabase Auth anonymous sign-in endpoint for deterministic offline & CI runs
    await page.route('**/auth/v1/signup', async (route) => {
      const json = {
        access_token: 'mock-playwright-anon-token',
        token_type: 'bearer',
        expires_in: 3600,
        refresh_token: 'mock-playwright-anon-refresh',
        user: {
          id: '00000000-0000-0000-0000-000000000001',
          aud: 'authenticated',
          role: 'authenticated',
          email: null,
          is_anonymous: true,
          app_metadata: { provider: 'anonymous' },
          user_metadata: {},
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        },
      };
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json) });
    });

    await page.route('**/api/v1/me', async (route) => {
      const json = {
        user: {
          id: '00000000-0000-0000-0000-000000000001',
          email: null,
          isAnonymous: true,
        },
        workspace: {
          id: '00000000-0000-0000-0000-000000000001',
          name: 'DuesPilot Demo Workspace',
          businessName: 'DuesPilot Demo Workspace',
          supplierSizeClass: 'MICRO',
          udyamRegistrationDate: null,
          ownerPhone: null,
          ownerWhatsapp: null,
          isDemo: true,
        },
        flags: {
          demoReadOnly: false,
          canCreateCases: true,
          requiresOnboarding: false,
        },
      };
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json) });
    });
  });

  test('unauthenticated user accessing protected /app is redirected to /signin', async ({ page }) => {
    await page.goto('/app');
    await expect(page).toHaveURL(/.*\/signin/);
    await expect(page.getByRole('heading', { level: 1, name: /Welcome back/i })).toBeVisible();
    await expect(page.getByTestId('demo-login-button')).toBeVisible();
  });

  test('demo login flow: 1-click anonymous sign-in, redirects to dashboard with persistent Demo banner', async ({ page }) => {
    await page.goto('/signin');

    // Verify demo login button is present
    const demoButton = page.getByTestId('demo-login-button');
    await expect(demoButton).toBeVisible();
    await expect(demoButton).toContainText('Try demo — no sign-up');

    // Click 1-click demo login
    await demoButton.click();

    // Verify redirect to overview dashboard
    await expect(page).toHaveURL(/.*\/app/);

    // Verify persistent demo mode banner is rendered
    const banner = page.getByTestId('demo-mode-banner');
    await expect(banner).toBeVisible();
    await expect(banner).toContainText(/Demo Workspace/i);

    // Click "Create account" button on the demo banner
    const createAccountCta = page.getByTestId('demo-banner-signup-btn');
    await expect(createAccountCta).toBeVisible();
    await createAccountCta.click();

    // Should navigate to /signup
    await expect(page).toHaveURL(/.*\/signup/);
    await expect(page.getByRole('heading', { level: 1, name: /Start your MSME recovery/i })).toBeVisible();
  });

  test('form validation shows inline errors for invalid submissions', async ({ page }) => {
    await page.goto('/signin');

    // Submit empty form
    await page.getByTestId('submit-auth-button').click();

    await expect(page.getByTestId('email-error')).toContainText(/Email is required/i);
    await expect(page.getByTestId('password-error')).toContainText(/Password is required/i);

    // Enter invalid email
    await page.getByTestId('email-input').fill('invalid-email-address');
    await page.getByTestId('submit-auth-button').click();
    await expect(page.getByTestId('email-error')).toContainText(/valid email address/i);
  });
});
