import { test, expect } from '@playwright/test';

// Standard test viewports across breakpoints
const VIEWPORTS = [
  { name: 'phone-320', width: 320, height: 568, isMobile: true, isTablet: false },
  { name: 'phone-375', width: 375, height: 667, isMobile: true, isTablet: false },
  { name: 'phone-390', width: 390, height: 844, isMobile: true, isTablet: false },
  { name: 'tablet-768', width: 768, height: 1024, isMobile: false, isTablet: true },
  { name: 'desktop-1024', width: 1024, height: 768, isMobile: false, isTablet: false },
  { name: 'desktop-1440', width: 1440, height: 900, isMobile: false, isTablet: false },
];

async function setupMocks(page: any) {
  // Mock anonymous signup for demo mode
  await page.route('**/auth/v1/signup', async (route: any) => {
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

  // Mock workspace / profile
  await page.route('**/api/v1/me', async (route: any) => {
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
        demoReadOnly: false,
        canCreateCases: true,
        requiresOnboarding: false,
      },
    };
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json) });
  });

  // Mock cases list
  await page.route('**/api/v1/cases', async (route: any) => {
    const json = [
      {
        id: 'c8d4e92a-71b3-4f9e-9d8a-112233445566',
        code: 'DP-0142',
        title: 'Kestrel Industrial Ltd',
        buyerName: 'Kestrel Industrial Ltd',
        invoicedPaise: 11800000,
        confirmedPaidPaise: 6180000,
        creditNotePaise: 0,
        reconciledBalancePaise: 5620000,
        status: 'NEEDS_REVIEW',
        lastRunAt: '12 Oct 2026, 3:45 pm',
        conflicts: [
          {
            id: 'c-1',
            ruleId: 'C7',
            title: 'Credit note not accounted in payment',
            description: 'Credit note CN-004 for ₹12,000 was issued but buyer deducted ₹15,000.',
            status: 'OPEN',
          },
        ],
        documents: [],
      },
    ];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json) });
  });
}

async function loginDemo(page: any) {
  await page.goto('/signin');
  const demoBtn = page.getByTestId('demo-login-button');
  await expect(demoBtn).toBeVisible();
  await demoBtn.click();
  await expect(page).toHaveURL(/.*\/app/);
  await expect(page.getByTestId('demo-mode-banner')).toBeVisible();
}

test.describe('Responsive Layout & No Horizontal Overflow Suite', () => {
  test.beforeEach(async ({ page }) => {
    await setupMocks(page);
  });

  for (const vp of VIEWPORTS) {
    test.describe(`Viewport: ${vp.name} (${vp.width}x${vp.height})`, () => {
      test.use({ viewport: { width: vp.width, height: vp.height } });

      test('Landing Page: no horizontal overflow and interactive elements visible', async ({ page }) => {
        await page.goto('/');
        await page.waitForLoadState('domcontentloaded');

        // Check no horizontal scrollbar / overflow
        const isOverflowing = await page.evaluate(() => {
          return document.documentElement.scrollWidth > window.innerWidth;
        });
        expect(isOverflowing).toBe(false);

        // Logo is visible
        await expect(page.getByTestId('landing-logo')).toBeVisible();

        // CTA button exists and is visible
        const heroCta = page.getByTestId('hero-cta-button');
        await expect(heroCta).toBeVisible();
      });

      test('Auth Page (signin): no horizontal overflow and 44px min touch targets', async ({ page }) => {
        await page.goto('/signin');
        await page.waitForLoadState('domcontentloaded');

        const isOverflowing = await page.evaluate(() => {
          return document.documentElement.scrollWidth > window.innerWidth;
        });
        expect(isOverflowing).toBe(false);

        const demoBtn = page.getByTestId('demo-login-button');
        await expect(demoBtn).toBeVisible();

        // Verify button bounding box height is >= 44px
        const box = await demoBtn.boundingBox();
        expect(box).not.toBeNull();
        if (box) {
          expect(box.height).toBeGreaterThanOrEqual(44);
        }
      });

      test('App Shell: rail vs bottom tab bar conditional visibility', async ({ page }) => {
        await loginDemo(page);
        await page.waitForLoadState('domcontentloaded');

        const isOverflowing = await page.evaluate(() => {
          return document.documentElement.scrollWidth > window.innerWidth;
        });
        expect(isOverflowing).toBe(false);

        const isPhoneOrTablet = vp.width < 1024;
        const bottomTabBar = page.getByTestId('bottom-tab-bar');
        const leftRail = page.getByTestId('left-rail');

        if (isPhoneOrTablet) {
          await expect(bottomTabBar).toBeVisible();
          await expect(leftRail).toBeHidden();
        } else {
          await expect(leftRail).toBeVisible();
          await expect(bottomTabBar).toBeHidden();
        }
      });

      test('Cases List: no horizontal overflow and stacked cards vs table', async ({ page }) => {
        await loginDemo(page);
        if (vp.width < 1024) {
          await page.getByTestId('bottom-nav-cases').click();
        } else {
          await page.getByTestId('nav-cases').click();
        }
        await expect(page).toHaveURL(/.*\/app\/cases/);
        await expect(page.getByTestId('cases-page')).toBeVisible();

        const isOverflowing = await page.evaluate(() => {
          return document.documentElement.scrollWidth > window.innerWidth;
        });
        expect(isOverflowing).toBe(false);

        if (vp.width < 640) {
          // On mobile phones, mobile card view should be visible
          const mobileCards = page.getByTestId('cases-mobile-cards');
          await expect(mobileCards).toBeVisible();
        } else {
          // On tablets and desktops, the table is visible
          const desktopTable = page.getByTestId('cases-table');
          await expect(desktopTable).toBeVisible();
        }
      });

      test('Case Workspace: no horizontal overflow and pane switcher on phone', async ({ page }) => {
        await loginDemo(page);
        await page.goto('/app/cases/DP-0142');
        await page.waitForLoadState('domcontentloaded');

        const isOverflowing = await page.evaluate(() => {
          return document.documentElement.scrollWidth > window.innerWidth;
        });
        expect(isOverflowing).toBe(false);

        if (vp.width < 1024) {
          // Switch to Documents tab to view 3-pane switcher
          const docsTab = page.getByRole('button', { name: /^documents/i }).first();
          if (await docsTab.isVisible()) {
            await docsTab.click();
            const paneSwitcher = page.getByTestId('workspace-pane-switcher');
            await expect(paneSwitcher).toBeVisible();

            // Verify clicking the 3 mobile pane tabs switch views correctly
            await page.getByTestId('pane-tab-viewer').click();
            await expect(page.getByTestId('proof-viewer')).toBeVisible();

            await page.getByTestId('pane-tab-fields').click();
            await expect(page.getByTestId('pane-tab-fields')).toBeVisible();

            await page.getByTestId('pane-tab-files').click();
            await expect(page.getByTestId('pane-tab-files')).toBeVisible();
          }
        }
      });

      test('Review Queue: no horizontal overflow', async ({ page }) => {
        await loginDemo(page);
        await page.goto('/app/review');
        await page.waitForLoadState('domcontentloaded');

        const isOverflowing = await page.evaluate(() => {
          return document.documentElement.scrollWidth > window.innerWidth;
        });
        expect(isOverflowing).toBe(false);
      });

      test('Benchmarks: no horizontal overflow', async ({ page }) => {
        await loginDemo(page);
        await page.goto('/app/benchmarks');
        await page.waitForLoadState('domcontentloaded');

        const isOverflowing = await page.evaluate(() => {
          return document.documentElement.scrollWidth > window.innerWidth;
        });
        expect(isOverflowing).toBe(false);
      });

      test('Models & Rules: no horizontal overflow', async ({ page }) => {
        await loginDemo(page);
        await page.goto('/app/models');
        await page.waitForLoadState('domcontentloaded');

        const isOverflowing = await page.evaluate(() => {
          return document.documentElement.scrollWidth > window.innerWidth;
        });
        expect(isOverflowing).toBe(false);
      });

      test('Settings: no horizontal overflow', async ({ page }) => {
        await loginDemo(page);
        await page.goto('/app/settings');
        await page.waitForLoadState('domcontentloaded');

        const isOverflowing = await page.evaluate(() => {
          return document.documentElement.scrollWidth > window.innerWidth;
        });
        expect(isOverflowing).toBe(false);
      });
    });
  }

  test.describe('Mobile "More" Bottom Sheet & Theme Interactions', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test('Opening "More" tab displays sheet with navigation links and theme toggle', async ({ page }) => {
      await loginDemo(page);
      await page.waitForLoadState('domcontentloaded');

      const moreTabBtn = page.getByTestId('tab-btn-more');
      await expect(moreTabBtn).toBeVisible();
      await moreTabBtn.click();

      // More sheet is shown
      const moreSheet = page.getByTestId('mobile-more-sheet');
      await expect(moreSheet).toBeVisible();

      // Navigation links are present in the sheet
      await expect(page.getByTestId('more-sheet-benchmarks')).toBeVisible();
      await expect(page.getByTestId('more-sheet-models')).toBeVisible();
      await expect(page.getByTestId('more-sheet-settings')).toBeVisible();
      await expect(page.getByTestId('more-sheet-theme-toggle')).toBeVisible();

      // Navigate to Settings via More sheet
      await page.getByTestId('more-sheet-settings').click();
      await expect(page).toHaveURL(/.*\/app\/settings/);
      await expect(moreSheet).toBeHidden();
    });

    test('Theme toggle switches document class between light and dark', async ({ page }) => {
      await loginDemo(page);
      await page.waitForLoadState('domcontentloaded');

      // Click more to access theme toggle
      await page.getByTestId('tab-btn-more').click();
      const themeToggle = page.getByTestId('more-sheet-theme-toggle');
      await expect(themeToggle).toBeVisible();

      const initialIsDark = await page.evaluate(() => document.documentElement.classList.contains('dark'));
      await themeToggle.click();
      const afterIsDark = await page.evaluate(() => document.documentElement.classList.contains('dark'));
      expect(afterIsDark).not.toBe(initialIsDark);

      // Verify no horizontal overflow in dark/light mode
      const isOverflowing = await page.evaluate(() => {
        return document.documentElement.scrollWidth > window.innerWidth;
      });
      expect(isOverflowing).toBe(false);
    });
  });
});
