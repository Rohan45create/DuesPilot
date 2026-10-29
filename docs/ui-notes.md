# DuesPilot UI Architecture & Responsiveness Notes

This document specifies the responsive design system, breakpoint contracts, interaction models, and verification suites implemented in DuesPilot.

---

## 1. Breakpoint Architecture & Viewport Targets

DuesPilot implements a 3-tier responsive layout system adhering strictly to zero horizontal overflow (`document.documentElement.scrollWidth <= window.innerWidth`) from `320px` to `1440px+`.

| Breakpoint Tier | Width Range | Target Devices | Primary Navigation | Sub Navigation |
|---|---|---|---|---|
| **Phone** | `< 640px` | iPhone SE (320px/375px), iPhone 12–15 (390px), Android devices | Fixed bottom tab bar (5 items) + "More" slide-up bottom sheet | Edge-faded horizontally scrollable chip row with scroll-snap |
| **Tablet** | `640px` – `1023px` | iPad Mini (768px), iPad Air, Foldables | Fixed bottom tab bar (5 items) + "More" slide-up bottom sheet | Edge-faded horizontally scrollable chip row with scroll-snap |
| **Desktop** | `≥ 1024px` | Small laptops (1024px), MacBooks (1440px), Ultrawide displays | Fixed left rail icon bar (`aside.fixed` or `aside.sticky`) | Standard horizontal tab row |

---

## 2. Navigation Hierarchy Across Viewports

### 2.1 Mobile & Tablet (`< 1024px`): Fixed Bottom Tab Bar & Slide-Up Sheet
- **Component:** `src/components/layout/BottomTabBar.tsx`
- **Main Tab Items (5):**
  1. **Overview** (`/app`) — Icon: `LayoutGrid`
  2. **New case** (`/app/cases/new`) — Icon: `PlusCircle`
  3. **Cases** (`/app/cases`) — Icon: `Folder`
  4. **Review** (`/app/review`) — Icon: `CheckCircle2`
  5. **More** — Triggers slide-up bottom sheet with `aria-modal="true"`.
- **"More" Bottom Sheet Content:**
  - **Benchmarks and cost** (`/app/benchmarks`)
  - **Models and rules** (`/app/models`)
  - **Settings & Providers** (`/app/settings`)
  - **Appearance / Theme Toggle:** Accessible 44px pill switching between light and dark modes.
  - **Sign out:** Destructive action with sign-out flow.
  - Pull handle indicator, backdrop dismiss, and `Escape` key capture.

### 2.2 Top Bar Adaptation (`< 1024px`)
- **Component:** `src/components/layout/TopBar.tsx`
- In mobile viewports, the desktop breadcrumb and search bar collapse into:
  - DuesPilot logo badge and title.
  - Global search icon button (opens responsive search bottom sheet).
  - Notifications bell with unread badge counter.
  - User avatar chip with initials.
  - Horizontally scrollable chip row with CSS mask edge fades (`.edge-fade-mask`), momentum scrolling (`-webkit-overflow-scrolling: touch`), and `.scrollbar-none`.

---

## 3. Data Tables, Cards, and Proof Viewer Adaptations

### 3.1 Data Tables & Stacked Mobile Cards (`< 640px`)
- **Cases List (`src/pages/CasesListPage.tsx`):**
  - `< 640px`: Renders stacked interactive cards (`data-testid="cases-mobile-cards"`). Each card displays the case ID badge, status tag, buyer name (truncated with title tooltip), invoiced, paid, reconciled balance, open conflict count, and statutory gate status pills (A, B, C, D).
  - `≥ 640px`: Renders full multi-column table with `sticky left-0 bg-surface z-10` on the first column for effortless horizontal scrolling without losing row identity.
- **Review Queue (`src/pages/ReviewQueuePage.tsx`), Benchmarks (`src/pages/BenchmarksPage.tsx`), Models & Rules (`src/pages/ModelsPage.tsx`):**
  - Converted into dual-mode rendering: stacked cards on phones and horizontally scrolling sticky-first-column tables on tablets/desktops.

### 3.2 Case Workspace 3-Pane Phone Viewport Transformation
- **Component:** `src/pages/CaseWorkspacePage.tsx`
- On desktop (`≥ 1024px`), the workspace utilizes a 3-column layout:
  - Left (3 cols): Case files list (6 documents).
  - Middle (6 cols): Document Proof Viewer with verified green boundary boxes and zoom controls (`-`, `100%`, `+`, Original vs Extracted text).
  - Right (3 cols): Extracted fields & provenance checklist.
- On phones (`< 640px`), the 3 columns transform into dedicated tabs:
  - `[ Case Files (6) ] [ Proof Viewer ] [ Fields & Provenance ]`
  - Selecting "Proof Viewer" renders the evidence document full-width with responsive zoom controls, and extracted values listed directly beneath.

---

## 4. Touch Targets & Safe Area Ergonomics

1. **44px Minimum Touch Targets:**
   - All interactive buttons, icon buttons, filter dropdown triggers, pagination items, and input controls enforce `min-h-[44px] min-w-[44px]` touch targets.
2. **Safe-Area Insets & Dynamic Viewport Units:**
   - Handled via `env(safe-area-inset-bottom, 0px)` and `min-height: 100dvh` in `src/index.css`.
   - `AppShell` applies bottom padding `pb-[calc(4.75rem+env(safe-area-inset-bottom,0px))] lg:pb-7` to prevent fixed bottom tab bar overlap on phones.
3. **Typography & Metrics Scaling:**
   - Clamped heading scales (e.g. `clamp(1.5rem, 4vw, 2.25rem)`) prevent awkward title wrapping or text clipping against card borders.
   - Financial figures leverage `<MoneyText />` with monospace tabular figures (`font-variant-numeric: tabular-nums`).

---

## 5. Automated Verification Results

### 5.1 Playwright End-to-End Responsive Test Suite (`e2e/responsive.spec.ts`)
- **Total Tests:** 56 tests across 6 viewports in both Light and Dark themes.
- **Passing Status:** 56/56 (100% green).
- **Tested Viewports:**
  - `phone-320`: 320x568 (iPhone SE 1st gen)
  - `phone-375`: 375x667 (iPhone SE 2nd/3rd gen)
  - `phone-390`: 390x844 (iPhone 12/13/14/15)
  - `tablet-768`: 768x1024 (iPad Mini / Portrait Tablet)
  - `desktop-1024`: 1024x768 (Small laptop / iPad Pro landscape)
  - `desktop-1440`: 1440x900 (MacBook / Desktop monitor)
- **Key Assertions Verified:**
  - `document.documentElement.scrollWidth <= window.innerWidth` across all routes (`/`, `/signin`, `/signup`, `/app`, `/app/cases`, `/app/cases/DP-0142`, `/app/cases/new`, `/app/review`, `/app/benchmarks`, `/app/models`, `/app/settings`).
  - Conditional navigation: `data-testid="bottom-tab-bar"` visible `< 1024px`, `data-testid="left-rail"` hidden `< 1024px`; and vice versa `≥ 1024px`.
  - Stacked mobile cards vs tables at 640px boundary.
  - 3-pane switcher on mobile for Case Workspace.
  - "More" bottom sheet open/close, navigation, and theme toggling.

### 5.2 Regression Verification
- `e2e/interaction-matrix.spec.ts`: 11/11 passed (100% green)
- `e2e/keyboard-navigation.spec.ts`: 5/5 passed (100% green)
- `e2e/demo-login.spec.ts`: 3/3 passed (100% green)
- Vitest unit tests: 46/46 passed (100% green)
