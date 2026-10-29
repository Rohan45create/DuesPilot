import { test, expect } from '@playwright/test';

async function loginDemo(page: any) {
  await page.goto('/signin');
  const demoBtn = page.getByTestId('demo-login-button');
  await expect(demoBtn).toBeVisible();
  await demoBtn.click();
  await expect(page).toHaveURL(/.*\/app/);
  await expect(page.getByTestId('demo-mode-banner')).toBeVisible();
}

test.describe('Keyboard Navigation & Interactive Feedback E2E', () => {
  test.beforeEach(async ({ page }) => {
    // Intercept Supabase Auth anonymous sign-in endpoint
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
          name: 'Basalt Heavy Engineering Pvt Ltd',
          businessName: 'Basalt Heavy Engineering Pvt Ltd',
          supplierSizeClass: 'SMALL',
          udyamRegistrationDate: '2023-04-01',
          ownerPhone: '+919876543210',
          ownerWhatsapp: '+919876543210',
          isDemo: true,
        },
        flags: {
          demoReadOnly: true,
          canCreateCases: true,
          requiresOnboarding: false,
        },
      };
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json) });
    });

    await page.route('**/api/v1/overview', async (route) => {
      const json = {
        totalInvoicedPaise: 2184000000,
        verifiedPaise: 1354080000,
        disputedPaise: 546000000,
        unverifiedPaise: 283920000,
        verifiedSharePercent: 62,
        activeCasesCount: 14,
        openConflictsCount: 4,
        unverifiedStatusText: 'Missing proof in 3 cases',
        statusBreakdown: {
          verifiedPercent: 62,
          disputedPercent: 25,
          unverifiedPercent: 13,
        },
        agingBreakdown: [
          { range: '0–15', heightPercent: 40, color: 'brand' },
          { range: '16–30', heightPercent: 75, color: 'brand' },
          { range: '31–45', heightPercent: 95, color: 'amber' },
          { range: '46–60', heightPercent: 60, color: 'dark-amber' },
          { range: '>60', heightPercent: 30, color: 'dark-amber' },
        ],
        liveCostCap: {
          spentDollars: 1.18,
          limitDollars: 5.0,
        },
        recentActivity: [],
      };
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json) });
    });
  });

  test('Skip to main content link works with keyboard', async ({ page }) => {
    await loginDemo(page);
    const skipLink = page.getByRole('link', { name: /Skip to main content/i });
    await skipLink.focus();
    await expect(skipLink).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/.*#main-content/);
  });

  test('Pill subnav arrow-key navigation (ArrowRight, ArrowLeft, Home, End)', async ({ page }) => {
    await loginDemo(page);

    const overviewTab = page.getByTestId('topbar-tab-overview');
    await expect(overviewTab).toBeVisible();
    await overviewTab.focus();
    await expect(overviewTab).toBeFocused();
    await expect(overviewTab).toHaveAttribute('aria-selected', 'true');

    // ArrowRight navigates to next tab 'Aging'
    await page.keyboard.press('ArrowRight');
    const agingTab = page.getByTestId('topbar-tab-aging');
    await expect(agingTab).toBeFocused();
    await expect(agingTab).toHaveAttribute('aria-selected', 'true');

    // ArrowRight navigates to 'Conflicts'
    await page.keyboard.press('ArrowRight');
    const conflictsTab = page.getByTestId('topbar-tab-conflicts');
    await expect(conflictsTab).toBeFocused();
    await expect(conflictsTab).toHaveAttribute('aria-selected', 'true');

    // End navigates to last tab 'Activity'
    await page.keyboard.press('End');
    const activityTab = page.getByTestId('topbar-tab-activity');
    await expect(activityTab).toBeFocused();
    await expect(activityTab).toHaveAttribute('aria-selected', 'true');

    // Home navigates back to first tab 'Overview'
    await page.keyboard.press('Home');
    await expect(overviewTab).toBeFocused();
    await expect(overviewTab).toHaveAttribute('aria-selected', 'true');
  });

  test('Interactive cards are keyboard focusable and activate on Enter', async ({ page }) => {
    await loginDemo(page);

    const verifiedCard = page.getByTestId('kpi-verified');
    await expect(verifiedCard).toBeVisible();
    await verifiedCard.focus();
    await expect(verifiedCard).toBeFocused();

    // Activating on Enter navigates to reconciled cases
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/.*status=RECONCILED/);
  });

  test('Destructive action confirm dialog traps focus and closes on Escape', async ({ page }) => {
    await loginDemo(page);
    await page.goto('/app/review');

    // Open reject dialog
    const rejectBtn = page.getByTestId('reject-action-btn');
    await expect(rejectBtn).toBeVisible();
    await rejectBtn.focus();
    await page.keyboard.press('Enter');

    const dialog = page.getByRole('alertdialog');
    await expect(dialog).toBeVisible();

    // Safe cancel button should receive initial focus
    const cancelBtn = page.getByTestId('cancel-reject-btn');
    await expect(cancelBtn).toBeFocused();

    // Tab moves focus to confirm button
    await page.keyboard.press('Tab');
    const confirmBtn = page.getByTestId('confirm-reject-btn');
    await expect(confirmBtn).toBeFocused();

    // Escape closes dialog
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
  });

  test('Toast undo action button is reachable and operable with keyboard', async ({ page }) => {
    await loginDemo(page);
    await page.goto('/app/review');

    // Approve the pending item
    await page.getByTestId('checklist-evidence').click();
    await page.getByTestId('checklist-statutory').click();
    await page.getByTestId('checklist-balance').click();

    const approveBtn = page.getByTestId('approve-action-btn');
    await approveBtn.focus();
    await page.keyboard.press('Enter');

    // Toast with Undo action should appear
    const toast = page.getByTestId('toast-success');
    await expect(toast).toBeVisible();
    const undoBtn = page.getByTestId('toast-action-btn');
    await expect(undoBtn).toBeVisible();
    await undoBtn.focus();
    await page.keyboard.press('Enter');

    // Undo action reverts approval and displays undid toast
    await expect(page.getByText(/Undid approval/i)).toBeVisible();
  });
});
