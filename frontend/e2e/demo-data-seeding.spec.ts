import { test, expect } from '@playwright/test';

test.describe('Consent-Based Demo Data Seeding & Management', () => {
  let seeded = false;

  test.beforeEach(async ({ page }) => {
    seeded = false;

    // Mock auth profile for registered (non-demo) user
    await page.route('**/api/v1/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          user: {
            id: 'registered-user-1',
            email: 'user@registered-msme.in',
            isAnonymous: false,
          },
          workspace: {
            id: 'ws-registered-1',
            name: 'Apex Precision Engineering',
            businessName: 'Apex Precision Engineering',
            supplierSizeClass: 'SMALL',
            udyamRegistrationDate: '2023-01-15',
            ownerPhone: '+919988776655',
            ownerWhatsapp: '+919988776655',
            isDemo: false,
          },
          flags: {
            demoReadOnly: false,
            canCreateCases: true,
            requiresOnboarding: false,
          },
        }),
      });
    });

    // Mock demo data preview endpoint
    await page.route('**/api/v1/workspaces/me/demo-data', async (route) => {
      const method = route.request().method();

      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            caseCount: 12,
            documentCount: 20,
            contactCount: 12,
            approximateStorageKb: 28,
            alreadySeeded: seeded,
          }),
        });
      } else if (method === 'POST') {
        const body = route.request().postDataJSON();
        if (!body || body.acknowledged !== true) {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              code: 'ACKNOWLEDGEMENT_REQUIRED',
              title: 'Acknowledgement Required',
              status: 400,
              detail: 'Explicit user consent acknowledgement is required to seed demo data',
            }),
          });
          return;
        }

        if (seeded) {
          await route.fulfill({
            status: 409,
            contentType: 'application/json',
            body: JSON.stringify({
              code: 'ALREADY_SEEDED',
              title: 'Demo Data Already Seeded',
              status: 409,
              detail: 'Demo data is already present in this workspace',
            }),
          });
          return;
        }

        seeded = true;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            caseCount: 12,
            message: 'Successfully seeded 12 demo cases into workspace.',
          }),
        });
      } else if (method === 'DELETE') {
        seeded = false;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            deletedCount: 12,
            message: 'Successfully removed demo cases from workspace.',
          }),
        });
      } else {
        await route.continue();
      }
    });

    // Mock cases list endpoint based on seeded state
    await page.route('**/api/v1/cases', async (route) => {
      if (route.request().method() === 'GET') {
        if (!seeded) {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify([]),
          });
        } else {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify([
              {
                id: 'demo-case-1',
                code: 'GEN-001',
                title: 'Kestrel Fabrication',
                supplierName: 'Apex Precision Engineering',
                buyerName: 'Kestrel Fabrication Pvt Ltd',
                supplierSizeClass: 'SMALL',
                status: 'NEEDS_REVIEW',
                invoicedPaise: 42000000,
                confirmedPaidPaise: 28000000,
                creditNotePaise: 0,
                reconciledBalancePaise: 14000000,
                documents: [],
                ledger: [],
                conflicts: [
                  {
                    id: 'c-1',
                    ruleId: 'C5',
                    title: 'Missing acceptance record',
                    description: 'Invoice and PO match; delivery challan absent.',
                    status: 'OPEN',
                  },
                ],
                gates: [],
                interest: {
                  principalPaise: 14000000,
                  interestPaise: 215000,
                  totalDuePaise: 14215000,
                  daysOverdue: 28,
                  applicableRatePercent: 19.5,
                  statutoryBasis: 'SECTION_16_MSMEDA',
                  calculatedUpTo: '2026-10-24',
                },
                isDemoData: true,
                createdAt: new Date().toISOString(),
              },
            ]),
          });
        }
      } else {
        await route.continue();
      }
    });

    // Mock overview endpoint based on seeded state
    await page.route('**/api/v1/overview', async (route) => {
      if (!seeded) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            activeCasesCount: 0,
            totalInvoicedPaise: 0,
            verifiedPaise: 0,
            verifiedSharePercent: 0,
            disputedPaise: 0,
            openConflictsCount: 0,
            unverifiedPaise: 0,
            unverifiedStatusText: 'No records',
            agingBuckets: [],
            conflictSummary: [],
            recentActivity: [],
          }),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            activeCasesCount: 1,
            totalInvoicedPaise: 42000000,
            verifiedPaise: 28000000,
            verifiedSharePercent: 67,
            disputedPaise: 14000000,
            openConflictsCount: 1,
            unverifiedPaise: 0,
            unverifiedStatusText: '1 case reconciled',
            agingBuckets: [],
            conflictSummary: [],
            recentActivity: [
              {
                caseCode: 'GEN-001',
                event: 'Invoice extracted',
                amountPaise: 42000000,
                status: 'NEEDS_REVIEW',
                statusLabel: 'Needs review',
                when: 'Just now',
              },
            ],
          }),
        });
      }
    });

    await page.route('**/auth/v1/user', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'registered-user-1',
          aud: 'authenticated',
          role: 'authenticated',
          email: 'user@registered-msme.in',
          is_anonymous: false,
          app_metadata: { provider: 'email' },
          user_metadata: {},
          created_at: '2026-10-24T00:00:00.000Z',
          updated_at: '2026-10-24T00:00:00.000Z',
        }),
      });
    });

    // Provide simulated authenticated session in localStorage using duespilot-auth-token
    await page.addInitScript(() => {
      const mockSession = {
        access_token: 'mock-registered-user-token',
        token_type: 'bearer',
        expires_in: 3600,
        expires_at: Math.floor(Date.now() / 1000) + 3600,
        refresh_token: 'mock-refresh-token',
        user: {
          id: 'registered-user-1',
          aud: 'authenticated',
          role: 'authenticated',
          email: 'user@registered-msme.in',
          is_anonymous: false,
          app_metadata: { provider: 'email' },
          user_metadata: {},
          created_at: '2026-10-24T00:00:00.000Z',
          updated_at: '2026-10-24T00:00:00.000Z',
        },
      };
      localStorage.setItem('duespilot-auth-token', JSON.stringify(mockSession));
    });
  });

  test('dialog gating: Add demo data button is disabled until consent checkbox is ticked', async ({ page }) => {
    await page.goto('/app/cases');

    // Should see empty state for registered user with no cases
    const seedBtn = page.getByTestId('cases-empty-seed-demo-btn');
    await expect(seedBtn).toBeVisible();
    await seedBtn.click();

    // Dialog opens
    const dialog = page.getByTestId('seed-demo-data-modal');
    await expect(dialog).toBeVisible();
    await expect(page.getByText('Add demo data to your workspace?')).toBeVisible();

    // Verify preview numbers are rendered
    await expect(page.getByText('12 Synthetic Cases')).toBeVisible();
    await expect(page.getByText('20 Sample Documents')).toBeVisible();
    await expect(page.getByText('12 Fictional Contacts')).toBeVisible();

    // Confirm button is initially disabled
    const confirmBtn = page.getByTestId('seed-demo-confirm-btn');
    await expect(confirmBtn).toBeDisabled();

    // Click cancel closes without seeding
    const cancelBtn = page.getByTestId('seed-demo-cancel-btn');
    await cancelBtn.click();
    await expect(dialog).not.toBeVisible();
    expect(seeded).toBe(false);
  });

  test('seeding lifecycle: consent tick -> Add demo data -> demo badge -> hide demo toggle', async ({ page }) => {
    await page.goto('/app/cases');

    // Open dialog from empty state
    await page.getByTestId('cases-empty-seed-demo-btn').click();

    // Check consent box
    const checkbox = page.getByTestId('seed-demo-consent-checkbox');
    await checkbox.check({ force: true });

    // Confirm button becomes enabled
    const confirmBtn = page.getByTestId('seed-demo-confirm-btn');
    await expect(confirmBtn).toBeEnabled();
    await confirmBtn.click();

    // Dialog closes
    await expect(page.getByTestId('seed-demo-data-modal')).not.toBeVisible();

    // Seeded case appears in table with "Demo" badge
    const caseRow = page.getByTestId('cases-table').getByTestId('case-row-GEN-001');
    await expect(caseRow).toBeVisible();
    const demoBadge = page.getByTestId('cases-table').getByTestId('case-demo-badge-GEN-001');
    await expect(demoBadge).toBeVisible();
    await expect(demoBadge).toHaveText('Demo');

    // Hide demo data filter toggle hides the demo case
    const hideToggle = page.getByTestId('cases-hide-demo-toggle');
    await expect(hideToggle).toBeVisible();
    await hideToggle.click();

    // The case should now be filtered out
    await expect(caseRow).not.toBeVisible();

    // Toggling back shows the case again
    await hideToggle.click();
    await expect(caseRow).toBeVisible();
  });

  test('removal lifecycle: Settings shows Demo Data section -> ConfirmDialog -> deletes demo records', async ({ page }) => {
    // Start with seeded = true
    seeded = true;

    await page.goto('/app/settings');

    // Demo Data section is present
    const demoSection = page.getByTestId('settings-demo-data-section');
    await expect(demoSection).toBeVisible();
    await expect(page.getByTestId('settings-demo-active-badge')).toHaveText(/Demo Data Active/i);

    // Click Remove demo data
    const removeBtn = page.getByTestId('settings-remove-demo-btn');
    await expect(removeBtn).toBeVisible();
    await removeBtn.click();

    // Confirm dialog opens
    const confirmDialog = page.getByTestId('confirm-remove-demo-dialog');
    await expect(confirmDialog).toBeVisible();
    await expect(page.getByText('Remove demo data from workspace?')).toBeVisible();

    // Cancel does not delete
    const cancelBtn = page.getByTestId('confirm-dialog-cancel-btn');
    await cancelBtn.click();
    await expect(confirmDialog).not.toBeVisible();
    expect(seeded).toBe(true);

    // Re-open and confirm deletion
    await removeBtn.click();
    const confirmDeleteBtn = page.getByTestId('confirm-dialog-confirm-btn');
    await confirmDeleteBtn.click();

    // Deletion succeeded
    await expect(confirmDialog).not.toBeVisible();
    expect(seeded).toBe(false);

    // Status updates to "Not Seeded"
    await expect(page.getByTestId('settings-demo-empty-badge')).toHaveText(/Not Seeded/i);
    await expect(page.getByTestId('settings-seed-demo-btn')).toBeVisible();
  });
});
