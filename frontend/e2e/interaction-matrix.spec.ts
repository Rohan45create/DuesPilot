import { test, expect } from '@playwright/test';

async function loginDemo(page: any) {
  await page.goto('/signin');
  const demoBtn = page.getByTestId('demo-login-button');
  await expect(demoBtn).toBeVisible();
  await demoBtn.click();
  await expect(page).toHaveURL(/.*\/app/);
  await expect(page.getByTestId('demo-mode-banner')).toBeVisible();
}

test.describe('DuesPilot Interaction Matrix E2E Test Suite', () => {
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
  });

  test('Landing Page: navigation links, logo, and theme toggles', async ({ page }) => {
    await page.goto('/');

    // Check logo
    await expect(page.getByTestId('landing-logo')).toBeVisible();

    // Check section nav links
    await expect(page.getByTestId('landing-nav-how-it-works')).toBeVisible();
    await expect(page.getByTestId('landing-nav-proof')).toBeVisible();
    await expect(page.getByTestId('landing-nav-models')).toBeVisible();
    await expect(page.getByTestId('landing-nav-guardrails')).toBeVisible();

    // Theme toggle on landing
    const themeBtn = page.getByTestId('landing-theme-toggle');
    const isDark = await page.locator('html').evaluate((el) => el.classList.contains('dark'));
    await themeBtn.click();
    if (isDark) {
      await expect(page.locator('html')).not.toHaveClass(/dark/);
      await themeBtn.click();
      await expect(page.locator('html')).toHaveClass(/dark/);
    } else {
      await expect(page.locator('html')).toHaveClass(/dark/);
      await themeBtn.click();
      await expect(page.locator('html')).not.toHaveClass(/dark/);
    }
  });

  test('Theme switching works in both Light and Dark themes across views', async ({ page }) => {
    await loginDemo(page);

    // Switch to dark theme
    const darkBtn = page.getByTestId('theme-dark-btn');
    await darkBtn.click();
    await expect(page.locator('html')).toHaveClass(/dark/);

    // Switch to light theme
    const lightBtn = page.getByTestId('theme-light-btn');
    await lightBtn.click();
    await expect(page.locator('html')).not.toHaveClass(/dark/);
  });

  test('Global Search Palette opens, searches cases, and navigates', async ({ page }) => {
    await loginDemo(page);

    // Open palette
    const searchBtn = page.getByTestId('global-search-btn');
    await searchBtn.click();
    const dialog = page.getByTestId('search-dialog');
    await expect(dialog).toBeVisible();

    // Type query
    const searchInput = page.getByTestId('search-palette-input');
    await searchInput.fill('DP-0142');

    // Click result item
    const item = page.getByTestId('search-item-DP-0142');
    await expect(item).toBeVisible();
    await item.click();

    // Dialog closes and navigates to case
    await expect(dialog).not.toBeVisible();
    await expect(page).toHaveURL(/.*\/app\/cases\/DP-0142/);
  });

  test('Global Notifications popover opens, marks all read, and navigates', async ({ page }) => {
    await loginDemo(page);

    // Click notifications bell
    const notifBtn = page.getByTestId('global-notifications-btn');
    await notifBtn.click();
    const popover = page.getByTestId('notifications-popover');
    await expect(popover).toBeVisible();

    // Mark all as read
    const markReadBtn = page.getByTestId('mark-all-read-btn');
    await markReadBtn.click();

    // Check toast appears
    await expect(page.getByTestId('toast-container')).toBeVisible();

    // Click first notification
    const firstNotif = page.getByTestId('notification-item-notif-1');
    await firstNotif.click();
    await expect(popover).not.toBeVisible();
  });

  test('User chip menu opens and navigates to Settings', async ({ page }) => {
    await loginDemo(page);

    // Click user chip
    const userMenuBtn = page.getByTestId('user-menu-btn');
    await userMenuBtn.click();
    const menu = page.getByTestId('user-menu-dropdown');
    await expect(menu).toBeVisible();

    // Click Settings
    const settingsLink = page.getByTestId('user-menu-settings');
    await settingsLink.click();
    await expect(page).toHaveURL(/.*\/app\/settings/);
  });

  test('Left rail navigation routes to all main pages and TopBar pills sync', async ({ page }) => {
    await loginDemo(page);

    // Navigate to Cases
    await page.getByTestId('nav-cases').click();
    await expect(page).toHaveURL(/.*\/app\/cases/);
    await expect(page.getByTestId('cases-page')).toBeVisible();

    // Navigate to Review Queue
    await page.getByTestId('nav-review-queue').click();
    await expect(page).toHaveURL(/.*\/app\/review/);
    await expect(page.getByTestId('review-queue-page')).toBeVisible();

    // Navigate to Benchmarks
    await page.getByTestId('nav-benchmarks').click();
    await expect(page).toHaveURL(/.*\/app\/benchmarks/);

    // Navigate to Models
    await page.getByTestId('nav-models-and-rules').click();
    await expect(page).toHaveURL(/.*\/app\/models/);

    // Navigate to Settings
    await page.getByTestId('nav-settings').click();
    await expect(page).toHaveURL(/.*\/app\/settings/);
  });

  test('Cases List Page: search filter, column sorting, and CSV export', async ({ page }) => {
    await loginDemo(page);
    await page.getByTestId('nav-cases').click();

    // Column sorting
    const invoicedHeader = page.getByTestId('sort-header-invoicedPaise');
    await invoicedHeader.click();
    await expect(page).toHaveURL(/.*sort=invoicedPaise/);

    // Search filter
    const searchInput = page.getByTestId('cases-search-input');
    await searchInput.fill('Kestrel');
    await expect(page).toHaveURL(/.*search=Kestrel/);

    // Export CSV
    const downloadPromise = page.waitForEvent('download');
    await page.getByTestId('cases-export-csv-btn').click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toContain('.csv');

    // Toast appears
    await expect(page.getByTestId('toast-container')).toBeVisible();
  });

  test('Review Queue: 3-point checklist, draft edit, approval, and rejection modal', async ({ page }) => {
    await loginDemo(page);
    await page.getByTestId('nav-review-queue').click();

    // Language toggle
    await page.getByTestId('lang-toggle-hi').click();
    await page.getByTestId('lang-toggle-en').click();

    // Checklist toggles
    await page.getByTestId('checklist-evidence').click();
    await page.getByTestId('checklist-statutory').click();
    await page.getByTestId('checklist-balance').click();

    // Approve button should be enabled and trigger approval toast
    const approveBtn = page.getByTestId('approve-action-btn');
    await expect(approveBtn).not.toBeDisabled();
    await approveBtn.click();
    await expect(page.getByTestId('toast-container')).toBeVisible();

    // Rejection confirmation dialog
    const rejectBtn = page.getByTestId('reject-action-btn');
    await rejectBtn.click();
    const rejectDialog = page.getByTestId('reject-confirm-dialog');
    await expect(rejectDialog).toBeVisible();

    // Cancel rejection
    await page.getByTestId('cancel-reject-btn').click();
    await expect(rejectDialog).not.toBeVisible();
  });

  test('Case Workspace: bank rate modal, export modal, and document download', async ({ page }) => {
    await loginDemo(page);
    await page.goto('/app/cases/DP-0142');

    // Re-run analysis
    const rerunBtn = page.getByTestId('re-run-btn');
    await rerunBtn.click();
    await expect(page.getByTestId('toast-container')).toBeVisible();

    // Bank rate modal
    await page.getByTestId('fetch-rate-btn').click();
    const rateModal = page.getByTestId('rate-modal');
    await expect(rateModal).toBeVisible();
    await page.getByTestId('cancel-bank-rate-btn').click();
    await expect(rateModal).not.toBeVisible();

    // Export Case Pack modal
    await page.getByTestId('export-case-pack-btn').click();
    const exportModal = page.getByTestId('export-modal');
    await expect(exportModal).toBeVisible();

    // Export CSV from modal
    await page.getByTestId('export-modal-csv-btn').click();
    await expect(page.getByText(/Exported CSV summary/i)).toBeVisible();
    await expect(exportModal).not.toBeVisible();

    // Switch to Documents sub-tab and verify download original document
    await page.goto('/app/cases/DP-0142?tab=Documents');
    const downloadBtn = page.getByTestId('download-original-btn');
    await expect(downloadBtn).toBeVisible();
    await downloadBtn.click();
    await expect(page.getByText(/Downloaded original document/i)).toBeVisible();
  });

  test('New Case Page: step navigation and demo-mode write gate prompt', async ({ page }) => {
    await loginDemo(page);
    await page.goto('/app/cases/new');

    // Step navigation
    await page.getByTestId('step-2-btn').click();
    await expect(page.getByTestId('input-case-title')).toBeVisible();

    await page.getByTestId('step-3-btn').click();
    await expect(page.getByTestId('routing-tiered-btn')).toBeVisible();

    await page.getByTestId('step-4-btn').click();
    const runBtn = page.getByTestId('run-analysis-btn');
    await expect(runBtn).toBeVisible();

    // In demo mode, clicking Run Analysis triggers the sign-up prompt modal
    await runBtn.click();
    const demoModal = page.getByTestId('demo-write-modal');
    await expect(demoModal).toBeVisible();

    // Option 1: "Continue Demo Run" proceeds without blocking
    await page.getByTestId('demo-continue-run-btn').click();
    await expect(demoModal).not.toBeVisible();
    await expect(page.getByTestId('toast-container')).toBeVisible();
  });

  test('Settings Page: business profile inputs, WhatsApp guide modal, and sign-up prompt for demo', async ({ page }) => {
    await loginDemo(page);
    await page.goto('/app/settings');

    // Business profile input
    const bizInput = page.getByTestId('settings-business-name');
    await expect(bizInput).toBeVisible();

    // WhatsApp setup guide modal
    await page.getByTestId('settings-whatsapp-guide-btn').click();
    const guideModal = page.getByTestId('settings-guide-modal');
    await expect(guideModal).toBeVisible();
    await page.getByTestId('settings-guide-close-btn').click();
    await expect(guideModal).not.toBeVisible();

    // In demo mode, sign up CTA is rendered in header
    await expect(page.getByTestId('settings-signup-btn')).toBeVisible();
  });
});
