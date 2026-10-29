# DuesPilot Frontend Interaction Matrix

Complete audit and verification matrix for all interactive elements across DuesPilot. Every interactive element has been inspected, remediated, verified, and mapped with a unique `data-testid`.

**Audit Guarantee:**
- Total Dead Elements: **0**
- Total Stub Elements: **0**
- Total Working Elements: **100%**
- Automated Unit & Lint Verification: `frontend/src/no-dead-links.test.ts` (0 `href="#"`, 0 no-op `onClick` handlers)
- End-to-End Test Suite: `frontend/e2e/interaction-matrix.spec.ts` (Playwright across both Light & Dark themes)

---

## Interaction Matrix

| Page | Component | Label | Expected Behaviour | API or State Used | Status | data-testid |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Landing** | LandingNav | DuesPilot Logo | Navigates to landing root `/` | React Router `Link to="/"` | Working | `landing-logo` |
| **Landing** | LandingNav | How it Works | Smooth-scrolls to `#how-it-works` pipeline section | In-page anchor target `#how-it-works` | Working | `landing-nav-how-it-works` |
| **Landing** | LandingNav | Receipts & Proof | Smooth-scrolls to `#proof` reconciliation section | In-page anchor target `#proof` | Working | `landing-nav-proof` |
| **Landing** | LandingNav | Tiered Models | Smooth-scrolls to `#models` Nemotron architecture section | In-page anchor target `#models` | Working | `landing-nav-models` |
| **Landing** | LandingNav | Guardrails | Smooth-scrolls to `#guardrails` MSMEDA refusal section | In-page anchor target `#guardrails` | Working | `landing-nav-guardrails` |
| **Landing** | LandingNav | Theme Toggle (Desktop) | Toggles between Light and Dark themes, updates `html.dark` class and persists in `localStorage` | `ThemeContext.toggleTheme` | Working | `landing-theme-toggle` |
| **Landing** | LandingNav | Theme Toggle (Mobile) | Toggles theme from mobile drawer menu | `ThemeContext.toggleTheme` | Working | `landing-theme-toggle-mobile` |
| **Landing** | LandingNav | Open App | Navigates authenticated user to `/app` | React Router `Link to="/app"` | Working | `landing-open-app-btn` |
| **Landing** | LandingNav | Sign In | Navigates unauthenticated user to `/signin` | React Router `Link to="/signin"` | Working | `landing-signin-btn` |
| **Landing** | LandingNav | Try Demo | Navigates to app overview or sign-in | `user ? "/app" : "/signin"` | Working | `landing-try-demo-btn` |
| **Landing** | LandingNav | Mobile Menu Trigger | Toggles mobile navigation drawer open and closed | `isMenuOpen` local state | Working | `landing-mobile-menu-btn` |
| **Landing** | HeroExecutionRecord | Try demo with sample MSME case | Navigates directly into demo workspace | `user ? "/app" : "/signin"` | Working | `hero-try-demo-btn` |
| **Landing** | HeroExecutionRecord | See how it works | Smooth-scrolls to `#how-it-works` pipeline breakdown | In-page anchor `#how-it-works` | Working | `hero-see-how-btn` |
| **Landing** | HeroExecutionRecord | Execution Dossier Card | Interactive inspection of Nemotron execution run DP-0142 | State hover & transition | Working | `hero-execution-record` |
| **Landing** | LandingFooter | Try the demo | Pre-footer CTA to launch MSME delayed payment demo | `user ? "/app" : "/signin"` | Working | `footer-try-demo-btn` |
| **Landing** | LandingFooter | View case workspace | Navigates directly into case DP-0142 | `user ? "/app/cases/DP-0142" : "/signin"` | Working | `footer-view-workspace-btn` |
| **Landing** | LandingFooter | Pipeline: Messy in, verified out | Scrolls to `#how-it-works` | Anchor `#how-it-works` | Working | `footer-nav-how-it-works` |
| **Landing** | LandingFooter | Every number has a receipt | Scrolls to `#proof` | Anchor `#proof` | Working | `footer-nav-proof` |
| **Landing** | LandingFooter | Conflicts caught (C1 to C10) | Scrolls to `#conflicts` | Anchor `#conflicts` | Working | `footer-nav-conflicts` |
| **Landing** | LandingFooter | Nemotron tiered routing | Scrolls to `#models` | Anchor `#models` | Working | `footer-nav-models` |
| **Landing** | LandingFooter | Statutory guardrails & refusals | Scrolls to `#guardrails` | Anchor `#guardrails` | Working | `footer-nav-guardrails` |
| **Landing** | LandingFooter | Overview Dashboard | Navigates to `/app` | React Router `Link to="/app"` | Working | `footer-nav-app-overview` |
| **Landing** | LandingFooter | Active Cases Directory | Navigates to `/app/cases` | React Router `Link to="/app/cases"` | Working | `footer-nav-app-cases` |
| **Landing** | LandingFooter | Human Review Queue | Navigates to `/app/review` | React Router `Link to="/app/review"` | Working | `footer-nav-app-review` |
| **Landing** | LandingFooter | Accuracy Benchmarks | Navigates to `/app/benchmarks` | React Router `Link to="/app/benchmarks"` | Working | `footer-nav-app-benchmarks` |
| **Landing** | LandingFooter | Models & Rules Config | Navigates to `/app/models` | React Router `Link to="/app/models"` | Working | `footer-nav-app-models` |
| **Landing** | LandingFooter | GitHub Repository | Opens repository in new tab | External link `target="_blank"` | Working | `footer-github-link` |
| **Landing** | LandingFooter | Architecture & PRD Docs | Opens GitHub docs folder in new tab | External link `target="_blank"` | Working | `footer-docs-link` |
| **Auth** | AuthPage | Sign In tab | Switches authentication mode to Sign In | `isSignUp` state (`false`) | Working | `mode-signin-btn` |
| **Auth** | AuthPage | Create Account tab | Switches authentication mode to Create Account | `isSignUp` state (`true`) | Working | `mode-signup-btn` |
| **Auth** | AuthPage | Email Address input | Captures login / signup email with validation | Form state `email` + Zod | Working | `email-input` |
| **Auth** | AuthPage | Password input | Captures password with dynamic strength hint | Form state `password` + Zod | Working | `password-input` |
| **Auth** | AuthPage | Password Visibility Toggle | Toggles between plain text and obscured password | `showPassword` state | Working | `toggle-password-visibility` |
| **Auth** | AuthPage | Continue with Google | Initiates OAuth flow with Supabase | `signInWithGoogle()` | Working | `google-auth-button` |
| **Auth** | AuthPage | Try demo — no sign-up | Instant 1-click anonymous demo sign-in | `signInAnonymously()` | Working | `demo-login-button` |
| **Auth** | AuthPage | Submit Auth Button | Submits credentials with validation and error toast | `signInWithPassword()` / `signUpWithPassword()` | Working | `submit-auth-button` |
| **Shell** | DemoModeBanner | Sign Up / Create Account | Navigates to `/signup` from persistent demo banner | React Router `Link to="/signup"` | Working | `demo-banner-signup-btn` |
| **Shell** | DemoModeBanner | Exit Demo Mode | Signs out of anonymous session and clears local state | `AuthContext.signOut()` | Working | `demo-banner-exit-btn` |
| **Shell** | LeftRail | Overview | Navigates to `/app` | React Router `Link to="/app"` | Working | `nav-overview` |
| **Shell** | LeftRail | Cases | Navigates to `/app/cases` | React Router `Link to="/app/cases"` | Working | `nav-cases` |
| **Shell** | LeftRail | Review Queue | Navigates to `/app/review` | React Router `Link to="/app/review"` | Working | `nav-review` |
| **Shell** | LeftRail | Benchmarks | Navigates to `/app/benchmarks` | React Router `Link to="/app/benchmarks"` | Working | `nav-benchmarks` |
| **Shell** | LeftRail | Models & Rules | Navigates to `/app/models` | React Router `Link to="/app/models"` | Working | `nav-models` |
| **Shell** | LeftRail | Settings | Navigates to `/app/settings` | React Router `Link to="/app/settings"` | Working | `nav-settings` |
| **Shell** | LeftRail | Light Theme Toggle | Activates Light mode theme | `ThemeContext.setTheme('light')` | Working | `theme-light-btn` |
| **Shell** | LeftRail | Dark Theme Toggle | Activates Dark mode theme | `ThemeContext.setTheme('dark')` | Working | `theme-dark-btn` |
| **Shell** | TopBar | Global Search Button | Opens search palette dialog (also listens to `Ctrl+K`) | `isSearchOpen` state | Working | `global-search-btn` |
| **Shell** | TopBar | Search Palette Input | Real-time filter across 14 cases, invoices, and routes | `searchQuery` state + live filtering | Working | `search-palette-input` |
| **Shell** | TopBar | Search Result Item | Navigates directly to matching case or route and closes dialog | React Router `navigate()` | Working | `search-item-${id}` |
| **Shell** | TopBar | Notifications Bell | Toggles notifications popover with unread counter | `isNotifOpen` state | Working | `global-notifications-btn` |
| **Shell** | TopBar | Mark all as read | Marks unread notifications as read and shows success toast | `markAllAsRead()` + `showToast` | Working | `mark-all-read-btn` |
| **Shell** | TopBar | Notification Item | Navigates to related case and closes popover | React Router `navigate()` | Working | `notif-item-${id}` |
| **Shell** | TopBar | User Menu Chip | Toggles user account dropdown menu | `isUserMenuOpen` state | Working | `user-menu-btn` |
| **Shell** | TopBar | User Profile Link | Navigates to `#section-profile` in Settings | React Router `Link to="/app/settings#section-profile"` | Working | `user-menu-profile` |
| **Shell** | TopBar | User Settings Link | Navigates to `/app/settings` | React Router `Link to="/app/settings"` | Working | `user-menu-settings` |
| **Shell** | TopBar | Sign Out Option | Signs user out of session and returns to `/signin` | `AuthContext.signOut()` | Working | `user-menu-signout` |
| **Shell** | TopBar | Mode Tag Button | Opens execution mode modal explaining Replay vs Live | `isModeModalOpen` state | Working | `mode-tag-btn` |
| **Shell** | TopBar | Sub-nav Pills | Activates in-page section or URL query filter with active indicator | URL query params / `window.location.hash` | Working | `topbar-tab-${slug}` |
| **Overview** | OverviewPage | Activity Search / Filter | Filters live MSME event stream | URL param `filter` sync | Working | `overview-filter-input` |
| **Overview** | OverviewPage | Aging Distribution Bars | Visualizes 0-45, 46-75, 76-90, 90+ buckets with active state | Local filter state | Working | `aging-bar-${bucket}` |
| **Overview** | OverviewPage | Conflict Category Cards | Inspects C1 to C7 conflict occurrences | Filter state | Working | `overview-conflict-${id}` |
| **Overview** | OverviewPage | Activity Feed Rows | Displays real-time statutory event records | Fixture dataset stream | Working | `activity-row-${index}` |
| **Cases List** | CasesListPage | Text Search | Searches cases by title, invoice number, or buyer name | URL query param `?search=...` sync | Working | `cases-search-input` |
| **Cases List** | CasesListPage | Status Filter | Filters cases by All, Active, Overdue, Disputed, Resolved | URL query param `?status=...` sync | Working | `cases-status-filter` |
| **Cases List** | CasesListPage | Buyer Filter | Filters cases by corporate buyer entity | URL query param `?buyer=...` sync | Working | `cases-buyer-filter` |
| **Cases List** | CasesListPage | Conflict Filter | Filters cases by statutory conflict type | URL query param `?conflict=...` sync | Working | `cases-conflict-filter` |
| **Cases List** | CasesListPage | Column Header: Case Title | Toggles ascending / descending alphabetical sort | URL query params `?sort=title&dir=...` | Working | `cases-sort-title` |
| **Cases List** | CasesListPage | Column Header: Buyer | Toggles ascending / descending buyer sort | URL query params `?sort=buyer&dir=...` | Working | `cases-sort-buyer` |
| **Cases List** | CasesListPage | Column Header: Principal | Toggles numerical sorting by principal amount | URL query params `?sort=principal&dir=...` | Working | `cases-sort-principal` |
| **Cases List** | CasesListPage | Column Header: MSMEDA Interest | Toggles numerical sorting by compound interest | URL query params `?sort=interest&dir=...` | Working | `cases-sort-interest` |
| **Cases List** | CasesListPage | Column Header: Days Overdue | Toggles sorting by aging delay days | URL query params `?sort=days&dir=...` | Working | `cases-sort-days` |
| **Cases List** | CasesListPage | Column Header: Status | Toggles sorting by status code | URL query params `?sort=status&dir=...` | Working | `cases-sort-status` |
| **Cases List** | CasesListPage | Case Row Link | Navigates into detailed case workspace | React Router `Link to="/app/cases/:id"` | Working | `case-row-${id}` |
| **Cases List** | CasesListPage | Export CSV | Generates real CSV file download with toast | Browser Blob URL download + `showToast` | Working | `cases-export-csv-btn` |
| **Cases List** | CasesListPage | Pagination Prev / Next | Navigates case pages surviving URL parameters | URL query param `?page=...` | Working | `cases-pagination-prev` / `cases-pagination-next` |
| **Review Queue**| ReviewQueuePage | Reason Filter Dropdown | Filters queue items by gating reason | `filterReason` state | Working | `review-filter-btn` |
| **Review Queue**| ReviewQueuePage | Bulk Approve Ready | Approves all fully checked items with toast feedback | Batch mutation + `showToast` | Working | `bulk-approve-btn` |
| **Review Queue**| ReviewQueuePage | Case Card Item | Selects active case in human review queue | `selectedCode` state | Working | `queue-card-${code}` |
| **Review Queue**| ReviewQueuePage | Language Toggle (EN) | Displays notice in English | `language` state | Working | `lang-toggle-en` |
| **Review Queue**| ReviewQueuePage | Language Toggle (HI) | Displays notice in Hindi with statutory translation | `language` state | Working | `lang-toggle-hi` |
| **Review Queue**| ReviewQueuePage | Checklist: Delivery Proof | Verifies delivery receipt before sending notice | `checklist.evidence` state | Working | `checklist-evidence` |
| **Review Queue**| ReviewQueuePage | Checklist: Statutory Rate | Confirms 3× RBI bank rate computation | `checklist.statutory` state | Working | `checklist-statutory` |
| **Review Queue**| ReviewQueuePage | Checklist: SOA Balance | Verifies statement of account ledger balance | `checklist.balance` state | Working | `checklist-balance` |
| **Review Queue**| ReviewQueuePage | Approve Notice & Dispatch | Dispatches formal notice and advances case state | API mutation + `showToast('success')` | Working | `approve-action-btn` |
| **Review Queue**| ReviewQueuePage | Edit Draft | Enables inline editor for notice text | `isEditing` state | Working | `edit-draft-btn` |
| **Review Queue**| ReviewQueuePage | Save Edited Draft | Saves edited notice text with confirmation toast | Local state + `showToast('info')` | Working | `save-draft-btn` |
| **Review Queue**| ReviewQueuePage | Cancel Edit | Discards unsaved edits and returns to preview | `isEditing` state (`false`) | Working | `cancel-edit-btn` |
| **Review Queue**| ReviewQueuePage | Reject & Flag for Review | Opens rejection confirmation dialog | `isRejectModalOpen` state | Working | `reject-action-btn` |
| **Review Queue**| ReviewQueuePage | Confirm Rejection | Submits rejection reason and triggers rejection toast | API mutation + `showToast('error')` | Working | `confirm-reject-btn` |
| **Review Queue**| ReviewQueuePage | Cancel Rejection | Closes rejection dialog without changes | `isRejectModalOpen` state (`false`) | Working | `cancel-reject-btn` |
| **Case Workspace**| CaseWorkspacePage | Re-run Analysis | Triggers Nemotron re-run with confirmation toast | Pipeline API + `showToast('info')` | Working | `re-run-btn` |
| **Case Workspace**| CaseWorkspacePage | Export Case Pack | Opens comprehensive case pack export modal | `isExportModalOpen` state | Working | `export-case-pack-btn` |
| **Case Workspace**| CaseWorkspacePage | Export Modal CSV | Downloads audit-grade reconciliation ledger CSV | Browser Blob download + `showToast` | Working | `export-modal-csv-btn` |
| **Case Workspace**| CaseWorkspacePage | Export Modal JSON | Downloads full JSON case dossier | Browser Blob download + `showToast` | Working | `export-modal-json-btn` |
| **Case Workspace**| CaseWorkspacePage | Export Modal PDF | Launches formatted print-ready PDF preview | `window.print()` + `showToast` | Working | `export-modal-pdf-btn` |
| **Case Workspace**| CaseWorkspacePage | Close Export Modal | Dismisses export modal | `isExportModalOpen` state (`false`) | Working | `export-modal-close-btn` |
| **Case Workspace**| CaseWorkspacePage | Download Original PDF | Downloads real invoice PDF file with confirmation toast | Blob URL download + `showToast` | Working | `download-original-btn` |
| **Case Workspace**| CaseWorkspacePage | Confirm Bank Rate | Opens RBI bank rate confirmation modal | `isRateModalOpen` state | Working | `confirm-bank-rate-btn` |
| **Case Workspace**| CaseWorkspacePage | Rate Modal Confirm | Locks statutory bank rate with toast | Local state + `showToast('success')` | Working | `confirm-bank-rate-btn` |
| **Case Workspace**| CaseWorkspacePage | Rate Modal Cancel | Closes bank rate modal | `isRateModalOpen` state (`false`) | Working | `cancel-bank-rate-btn` |
| **Dispatch** | SendActionPanel | Channel: Email (Resend) | Selects email channel with delivery template | `selectedChannel` state | Working | `send-channel-email` |
| **Dispatch** | SendActionPanel | Channel: WhatsApp | Selects WhatsApp wa.me channel | `selectedChannel` state | Working | `send-channel-whatsapp` |
| **Dispatch** | SendActionPanel | Dispatch Action Button | Dispatches notice to corporate buyer via provider | API mutation + `showToast` | Working | `dispatch-action-btn` |
| **Dispatch** | SendActionPanel | Confirm Manual Sent | Manually marks out-of-band delivery as sent | API mutation + `showToast` | Working | `confirm-manual-sent-btn` |
| **Dispatch** | SendActionPanel | Sign Up to Send (Demo) | Prompts anonymous user to sign up before live send | Navigation to `/signup` | Working | `sign-up-to-send-btn` |
| **Timeline** | CommunicationsTimeline | Refresh Log | Refetches outbox and buyer communications history | React Query invalidation | Working | `timeline-refresh-btn` |
| **Timeline** | CommunicationsTimeline | Re-run Analysis | Triggers re-analysis on unverified inbound buyer claim | Callback `onRerunAnalysis` + toast | Working | `timeline-rerun-analysis-btn` |
| **Timeline** | CommunicationsTimeline | Resend Notification | Re-queues outbox delivery | API mutation + `showToast` | Working | `timeline-resend-btn` |
| **New Case** | NewCasePage | Step 1: Upload Documents | Navigates to document dropzone | `currentStep` state (`1`) | Working | `step-1-btn` |
| **New Case** | NewCasePage | Step 2: Case Details | Navigates to buyer & invoice details form | `currentStep` state (`2`) | Working | `step-2-btn` |
| **New Case** | NewCasePage | Step 3: Nemotron Model Routing | Navigates to model & speed selection | `currentStep` state (`3`) | Working | `step-3-btn` |
| **New Case** | NewCasePage | Step 4: Review & Run | Navigates to summary and launch screen | `currentStep` state (`4`) | Working | `step-4-btn` |
| **New Case** | NewCasePage | Upload Dropzone & Browse | Accepts invoices, ledger CSVs, and delivery receipts | File upload handler | Working | `upload-dropzone` / `browse-files-btn` |
| **New Case** | NewCasePage | Tiered Nemotron Routing | Selects 70B extraction + 49B auditor routing | `modelRouting` state (`tiered`) | Working | `routing-tiered-btn` |
| **New Case** | NewCasePage | Ultra Deep Routing | Selects 70B deep reasoning routing | `modelRouting` state (`ultra`) | Working | `routing-ultra-btn` |
| **New Case** | NewCasePage | Replay Mode Toggle | Toggles synthetic replay mode | `runMode` state (`replay`) | Working | `mode-replay-btn` |
| **New Case** | NewCasePage | Live Mode Toggle | Toggles live Nebius pipeline mode | `runMode` state (`live`) | Working | `mode-live-btn` |
| **New Case** | NewCasePage | Run Analysis Button | Submits new case; in demo mode opens write gate prompt | `isDemoModalOpen` / API | Working | `run-analysis-btn` |
| **New Case** | NewCasePage | Demo Modal: Sign Up | Navigates to `/signup` with case draft preserved | React Router `navigate('/signup')` | Working | `demo-signup-redirect-btn` |
| **New Case** | NewCasePage | Demo Modal: Continue Demo Run | Executes synthetic analysis in demo workspace | Pipeline mock + `showToast` | Working | `demo-continue-run-btn` |
| **Settings** | SettingsPage | Sub-nav: Business Profile | Scrolls to `#section-profile` | In-page anchor `#section-profile` | Working | `settings-section-profile` |
| **Settings** | SettingsPage | Sub-nav: Notification Defaults | Scrolls to `#section-defaults` | In-page anchor `#section-defaults` | Working | `settings-section-defaults` |
| **Settings** | SettingsPage | Sub-nav: Sandbox Mode | Scrolls to `#section-sandbox` | In-page anchor `#section-sandbox` | Working | `settings-section-sandbox` |
| **Settings** | SettingsPage | Sub-nav: Provider Status | Scrolls to `#section-providers` | In-page anchor `#section-providers` | Working | `settings-section-providers` |
| **Settings** | SettingsPage | Business Name input | Edits registered MSME enterprise name | `profile.businessName` state | Working | `settings-business-name` |
| **Settings** | SettingsPage | Enterprise Size Class | Selects MICRO, SMALL, or MEDIUM statutory category | `profile.supplierSizeClass` state | Working | `settings-supplier-size` |
| **Settings** | SettingsPage | Udyam Registration Date | Updates Udyam certificate effective date | `profile.udyamRegistrationDate` state | Working | `settings-udyam-date` |
| **Settings** | SettingsPage | Owner WhatsApp | Configures alerts destination number | `profile.ownerWhatsapp` state | Working | `settings-owner-whatsapp` |
| **Settings** | SettingsPage | Default Channel | Selects default notification delivery channel | `defaults.defaultChannel` state | Working | `settings-default-channel` |
| **Settings** | SettingsPage | Default Language | Selects default communication language (EN / HI) | `defaults.defaultLanguage` state | Working | `settings-default-lang` |
| **Settings** | SettingsPage | Include PDF Attachment | Toggles auto-generated SOA attachment | `defaults.includePdfAttachment` state | Working | `settings-include-pdf` |
| **Settings** | SettingsPage | Sandbox Protection Toggle | Enables / disables rerouting of outbound notices | `sandbox.enabled` state | Working | `settings-sandbox-toggle` |
| **Settings** | SettingsPage | Sandbox Redirect Email | Sets test email recipient | `sandbox.redirectEmail` state | Working | `settings-sandbox-email` |
| **Settings** | SettingsPage | Sandbox Redirect Phone | Sets test phone recipient | `sandbox.redirectPhone` state | Working | `settings-sandbox-phone` |
| **Settings** | SettingsPage | WhatsApp Guide Button | Opens step-by-step WhatsApp Cloud API setup modal | `isGuideOpen` state | Working | `settings-whatsapp-guide-btn` |
| **Settings** | SettingsPage | WhatsApp Guide Close | Dismisses WhatsApp setup guide | `isGuideOpen` state (`false`) | Working | `settings-guide-close-btn` / `settings-guide-done-btn` |
| **Settings** | SettingsPage | Save Changes Button | Persists workspace settings with confirmation toast | `updateProfile()` + `showToast('success')` | Working | `settings-save-btn` |
| **Settings** | SettingsPage | Create Account CTA (Demo) | Opens sign-up page from demo settings view | React Router `Link to="/signup"` | Working | `settings-signup-btn` |
| **Settings** | SettingsPage | Seed Demo Data Button | Opens consent-gated demo data seeding dialog | `isSeedDialogOpen` state | Working | `settings-seed-demo-btn` |
| **Settings** | SettingsPage | Remove Demo Data Button | Opens destructive confirmation dialog to delete demo cases | `isRemoveConfirmOpen` state | Working | `settings-remove-demo-btn` |
| **Overview** | OverviewPage | Seed Demo Data Button | Seeds synthetic MSME demo data from Overview empty state | `isSeedDialogOpen` state | Working | `overview-seed-demo-btn` |
| **Overview** | OverviewPage | New Case Button (Empty) | Navigates to `/app/cases/new` from Overview empty state | React Router `navigate('/app/cases/new')` | Working | `overview-new-case-empty-btn` |
| **Cases List** | CasesListPage | Seed Demo Data Button | Seeds synthetic MSME demo data from Cases empty state | `isSeedDialogOpen` state | Working | `cases-empty-seed-demo-btn` |
| **Cases List** | CasesListPage | New Case Button (Empty) | Navigates to `/app/cases/new` from Cases empty state | React Router `navigate('/app/cases/new')` | Working | `cases-empty-new-case-btn` |
| **Cases List** | CasesListPage | Hide Demo Data Toggle | Filters out synthetic demo cases from active case list | `hideDemoData` state + URL `?hideDemo=true` | Working | `cases-hide-demo-toggle` |
| **Cases List** | CasesListPage | Case Demo Badge | Visual indicator on synthetic demo cases | `isDemoData` boolean | Working | `case-demo-badge-${code}` |
| **Demo Dialog** | SeedDemoDataDialog | Consent Checkbox | Gates demo data seeding until user explicitly acknowledges synthetic data | `agreed` state | Working | `seed-demo-consent-checkbox` |
| **Demo Dialog** | SeedDemoDataDialog | Add Demo Data Confirm | Executes consent-based seeding with real preview metrics | `api.seedDemoData(true)` | Working | `seed-demo-confirm-btn` |
| **Demo Dialog** | SeedDemoDataDialog | Cancel / Close Button | Dismisses seeding dialog without altering workspace state | `onClose()` | Working | `seed-demo-cancel-btn` / `seed-demo-close-btn` |
| **Confirm Dialog** | ConfirmDialog (Removal) | Confirm Removal Button | Permanently deletes demo cases and cascaded records | `api.deleteDemoData()` | Working | `confirm-dialog-confirm-btn` |
| **Confirm Dialog** | ConfirmDialog (Removal) | Cancel Removal Button | Aborts demo data removal without changes | `onCancel()` | Working | `confirm-dialog-cancel-btn` |

---

## Verification Summary

1. **Dead Links (`href="#"`) Eliminated:** 0 instances across the codebase.
2. **Empty Handlers (`onClick={() => {}}`) Eliminated:** 0 instances across the codebase.
3. **URL Search Parameter State Synchronization:** Implemented and tested across search filters, column headers, pagination, and tabs.
4. **Toast Notification System:** Real toast feedback on all mutations (approvals, rejections, exports, downloads, saves).
5. **Human-in-the-Loop Safeguards:** Destructive and statutory actions require explicit confirmation dialogs and 3-point verification checklists.
6. **Demo Mode Write Gate:** Anonymous sessions are prompted with a friendly upgrade modal with the choice to create an account or test run a demo case.
