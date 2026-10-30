# DuesPilot

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![CI](https://github.com/Rohan45create/DuesPilot/actions/workflows/ci.yml/badge.svg)](https://github.com/Rohan45create/DuesPilot/actions/workflows/ci.yml)
[![Third-Party Notices](https://img.shields.io/badge/Notices-Third--Party-informational.svg)](THIRD_PARTY_NOTICES.md)

> **Evidence-backed receivables reconciliation and dispute resolution assistant for Indian MSMEs under the MSMED Act, 2006.**  
> Built for the **Nebius × NVIDIA Global AI Hackathon** (Best Apps & Agents track · Best Use of Tavily).

---

## 1. What is DuesPilot?

DuesPilot is an intelligent reconciliation engine that protects micro, small, and medium enterprises (MSMEs) from delayed payments and disputed receivables. 

MSME suppliers routinely face delayed payments from corporate buyers, leading to working-capital lockups. When invoices are disputed, communications scatter across PDF invoices, purchase orders, handwritten lorry receipts, delivery challans, credit notes, payment vouchers, and unstructured WhatsApp transcripts.

DuesPilot ingests all conflicting documents, extracts financial terms and dates with **verbatim source-span verification**, reconciles the true balance in paise-exact integer arithmetic, evaluates statutory eligibility under Sections 15–16 of the MSMED Act, calculates compound interest at 3× the RBI Bank Rate, and drafts source-linked action notices for human approval.

---

## 2. Key Features

- **Tiered Nemotron Multi-Model Routing:** 
  - **Nemotron 3 Nano (30B-A3B):** High-throughput extraction of invoice dates, line items, and TDS deductions.
  - **Nemotron 3 Super (120B-A12B):** Cross-document reconciliation and semantic conflict detection (C1–C10).
  - **Nemotron 3 Ultra (550B-A55B):** High-stakes legal dispute synthesis and statutory notice adjudication.
- **Verbatim Source-Span Verification (Anti-Hallucination):** Every extracted value must match an exact character span in the source PDF/text or it is downgraded to `UNVERIFIED` and excluded from financial calculations.
- **Deterministic Paise Math (Rule R-1):** Zero floating-point drift. All balance calculations, interest compounding, and deductions are performed in 64-bit integer paise.
- **Statutory MSMEDA Gate & Interest Engine:** Evaluates buyer eligibility Gates A–D, statutory acceptance dates (Section 15), and monthly compound interest at 3× the official Bank Rate (Section 16).
- **Live RBI Bank Rate Lookup via Tavily:** Fetches verified Reserve Bank of India policy rates with domain allow-listing (`rbi.org.in`), regex-based rate verification, and user confirmation dialogs.
- **Human-in-the-Loop Guardrail (Rule R-72):** The system prepares notices and WhatsApp communications, but never dispatches without explicit human review and approval.
- **1-Click Personal Demo Workspaces (F7):** Anonymous users receive an isolated, auto-seeded personal demo workspace (`is_demo=true`) with full operational access (case creation, file uploads, analysis runs, review, and approval) governed by strict abuse quotas (30 cases, 25 MB storage, 3 live analyses, simulated notifications, and 7-day idle TTL purge).
- **Reproducible Evaluation Benchmark:** Deterministic test generator with 40 synthetic + 8 holdout cases, measuring balance match, conflict recall, and tiering cost savings (6.23x to 6.83x cheaper than an all-Ultra baseline).

---

## 3. Architecture

```
+-------------------------------------------------------------------------------+
|                             Vercel Edge Network                               |
|       React 19 + TypeScript + Vite + Tailwind CSS v4 Single Page App         |
+---------------------------------------+---------------------------------------+
                                        | (HTTPS / REST + SSE / Supabase JWT)
                                        v
+-------------------------------------------------------------------------------+
|              Google Cloud Run / Render (Backend API Container)                |
|                    Spring Boot 4.1.1 on Eclipse Temurin JDK 21                |
|                                                                               |
|  +--------------------+  +----------------------+  +-----------------------+  |
|  | duespilot-core     |  | Model Router         |  | Tavily Rate Provider  |  |
|  | - Paise arithmetic |  | - Nemotron 3 Nano    |  | - rbi.org.in lookup   |  |
|  | - Span verifier    |  | - Nemotron 3 Super   |  | - 24h cache           |  |
|  | - Gates A-D        |  | - Nemotron 3 Ultra   |  | - User confirmation   |  |
|  | - Conflict rules   |  | - Token cost caps    |  |                       |  |
|  +--------------------+  +----------------------+  +-----------------------+  |
+---------------------------------------+---------------------------------------+
                                        | JDBC (HikariCP / TLS)
                                        v
+-------------------------------------------------------------------------------+
|                       Supabase Managed PostgreSQL 16                          |
|             (or local postgres:16-alpine container in Docker Compose)         |
|                                                                               |
|  - Flyway Versioned Migrations (V1__init.sql .. V5__benchmark_holdout.sql)    |
|  - Relational Schema: cases, documents, extracted_fields, audit_events        |
|  - Evaluation Schema: bench.benchmark_cases, bench.benchmark_runs             |
|  - Document Storage: PostgreSQL bytea in app.file_blobs (Zero disk files)    |
+-------------------------------------------------------------------------------+
```

---

## 4. Technology Stack

- **Backend:** Java 21, Spring Boot 4.1.x, Spring WebMVC, Virtual Threads, Apache PDFBox 3.0, FasterXML Jackson 3, HikariCP, Flyway, Testcontainers, WireMock.
- **Core Domain:** Pure Java 21 (`duespilot-core`) with zero external framework dependencies for maximum determinism and testability.
- **Frontend:** React 19, TypeScript (strict), Vite 6, Tailwind CSS v4 (`@tailwindcss/vite`), TanStack Query v5, React Router v7, Lucide Icons, Zod.
- **AI Models:** NVIDIA Nemotron 3 (Nano 30B, Super 120B, Ultra 550B) on Nebius Token Factory.
- **External Search:** Tavily Search API with RBI domain filtering.
- **Identity:** Supabase Auth (GoTrue) with RS256 JWKS JWT validation.
- **Database & Storage:** PostgreSQL 16 (Supabase Managed or Docker Compose) with `bytea` document persistence (Rule R-84: zero local filesystem dependencies).

---

## 5. Prerequisites

Before running DuesPilot locally, ensure the following are installed:

- **Java Development Kit (JDK):** Version 21 or later ([Eclipse Temurin 21 recommended](https://adoptium.net/))
- **Apache Maven:** Version 3.9+ (`mvn -version`)
- **Node.js & npm:** Node.js 20+ LTS and npm 10+ (`node -v`, `npm -v`)
- **Docker & Docker Compose:** Optional for local PostgreSQL database or containerized builds (`docker compose version`)

---

## 6. Quick Start

### Step 1: Environment Setup

Clone the repository and create your local configuration from the unified template:

```bash
git clone https://github.com/Rohan45create/DuesPilot.git
cd DuesPilot
cp .env.example .env
```

Validate the configuration using the check script:

```bash
node scripts/check-env.js
```

---

### Step 2A: Option A — Using a Supabase Database (Default)

If you have a Supabase project:
1. Set `DATABASE_URL` and `SPRING_DATASOURCE_URL` in `.env` to your Supabase PostgreSQL connection string.
2. Set `SUPABASE_URL` and `VITE_SUPABASE_URL` to your project URL.
3. Set `VITE_SUPABASE_ANON_KEY` to your Supabase anon public key.
4. Set `SUPABASE_JWKS_URI` to `https://<your-project>.supabase.co/auth/v1/.well-known/jwks.json`.

---

### Step 2B: Option B — Using a Local Docker Database

If running entirely offline without Supabase:
1. Start the local PostgreSQL 16 container:
   ```bash
   docker compose up -d postgres
   ```
2. The database will initialize on port 5432 with credentials `duespilot / duespilot`.
3. Ensure `.env` points to the local datasource:
   ```properties
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/duespilot
   SPRING_DATASOURCE_USERNAME=duespilot
   SPRING_DATASOURCE_PASSWORD=duespilot
   ```

---

### Step 3: Run the Backend

Build and start the Spring Boot API server:

```bash
cd backend
mvn clean verify
mvn --projects duespilot-api spring-boot:run
```

The backend server starts on `http://localhost:8080`.
- Health Check: `http://localhost:8080/actuator/health`
- Ping Endpoint: `http://localhost:8080/api/v1/ping`

---

### Step 4: Run the Frontend

In a separate terminal, install dependencies and launch the Vite development server:

```bash
cd frontend
npm ci
npm run dev
```

The frontend will be available at `http://localhost:5173`.

---

### Step 5: Run with Docker Compose (Full Stack)

To run the entire system via Docker Compose:

```bash
docker compose up --build
```

---

## 7. Environment Variables Reference

DuesPilot strictly adheres to **Rule R-83**: a single `.env` file at the repository root governs the entire stack.

| Variable Name | Required | Description |
|---|---|---|
| `PORT` / `SERVER_PORT` | Optional | Port on which the Spring Boot API listens (default: 8080) |
| `SPRING_DATASOURCE_URL` | Required | JDBC PostgreSQL connection string |
| `SPRING_DATASOURCE_USERNAME` | Required | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Required | Database password |
| `DATABASE_URL` | Required | Direct or pooled PostgreSQL connection URL |
| `SUPABASE_URL` | Required | Base URL of the Supabase project |
| `SUPABASE_JWKS_URI` | Required | JSON Web Key Set endpoint for RS256 token verification |
| `CORS_ALLOWED_ORIGINS` | Required | Comma-separated list of permitted frontend origins |
| `DEMO_MODE` | Required | Set to `replay` (cached responses) or `live` (live LLM calls) |
| `NEBIUS_API_KEY` | Optional in replay | API key for Nebius Token Factory (NVIDIA Nemotron models) |
| `LLM_BASE_URL` | Optional | Nebius OpenAI-compatible endpoint URL |
| `TAVILY_API_KEY` | Optional in replay | API key for Tavily Search API |
| `DAILY_COST_CAP_MICROS` | Optional | Spend cap per calendar day in micro-USD (default: $50) |
| `PER_CASE_COST_CAP_MICROS` | Optional | Spend cap per case analysis in micro-USD (default: $2) |
| `VITE_SUPABASE_URL` | Required | Frontend Supabase URL for client authentication |
| `VITE_SUPABASE_ANON_KEY` | Required | Frontend public anonymous key |
| `VITE_API_BASE_URL` | Required | Backend API base URL for client HTTP requests |
| `VITE_TURNSTILE_SITE_KEY` | Optional | Cloudflare Turnstile public site key |
| `NOTIFY_SANDBOX` | Optional | Set `true` to capture outbound emails/WhatsApp in outbox table without sending |
| `RESEND_API_KEY` | Optional | Resend email API key |
| `WHATSAPP_TOKEN` | Optional | Meta Cloud API access token |

For details on secret rotation and cloud storage locations, consult [`docs/secrets-and-env.md`](docs/secrets-and-env.md).

---

## 8. How to Run Tests

### Backend Unit & Integration Tests

Run all unit tests and integration tests across all modules (excluding live third-party network tests):

```bash
cd backend
mvn -B clean verify -Dduespilot.live-tests=false
```

To run opt-in live tests against external APIs (requires active keys in `.env`):

```bash
cd backend
mvn test -Dtest=TavilyRateProviderLiveTest -Dduespilot.live-tests=true
```

### Frontend Unit & Component Tests

Run Vitest unit tests:

```bash
cd frontend
npm test
```

Run TypeScript strict type checking and linting:

```bash
cd frontend
npm run typecheck
npm run lint
```

### Playwright End-to-End Tests

To execute end-to-end browser tests:

```bash
cd frontend
npx playwright install chromium
npm run test:e2e
```

---

## 9. Benchmarks & Evaluation

DuesPilot includes a dedicated evaluation suite in `duespilot-bench` that tests the reconciliation and routing pipeline against deterministic ground truth.

### Generating Benchmark Data into the Database

In compliance with **Rule R-84**, benchmark cases and synthetic invoice PDFs are rendered purely in memory and persisted directly into the database (`bench.benchmark_cases` and `app.file_blobs`):

```bash
cd backend
mvn -pl duespilot-bench compile exec:java "-Dexec.mainClass=com.duespilot.bench.DuesPilotBench" "-Dexec.args=generate --target db"
```

### Running Benchmark Evaluation

Execute deterministic evaluation against the holdout dataset:

```bash
cd backend
mvn -pl duespilot-bench exec:java "-Dexec.mainClass=com.duespilot.bench.DuesPilotBench" "-Dexec.args=bench --dataset holdout"
```

### Real Measured Output Summary

The following metrics are measured directly from the evaluation runner across both the 8-case hand-curated holdout suite and the 40-case synthetic generator suite:

```text
==========================================================================================
 DUESPILOT BENCHMARK REPORT: HOLDOUT (v1.0)
 Rules R-62, R-63: Deterministic evaluation against ground_truth.json
==========================================================================================
Total Cases: 8 | Passed: 8 (100.0%)
Reconciled Balance Match: 8 / 8 (100.0%)
Conflict Recall: 100.0% | False-Conflict Rate: 0.0%
Unsupported-Claim Rate: 0.0% | Abstention Accuracy: 100.0%
Field Accuracy: 100.0%
Average Latency: 115 ms/case
------------------------------------------------------------------------------------------
COST & TIERING COMPARISON (MEASURED TOKENS):
  Tiered Routing Total Cost:      $0.0010 (960 micros, avg 120 micros/case)
  All-Ultra Hypothetical Cost:    $0.0060 (5985 micros)
  Tiering Cost Savings Multiplier: 6.23x cheaper than All-Ultra
------------------------------------------------------------------------------------------
EXPERIMENT C — LANGUAGE GROUP BREAKDOWN:
  LANGUAGE   | CASES  | BALANCE MATCH   | CONFLICT RECALL  | FIELD ACCURACY 
  HI         | 1      | 100.0%          | 100.0%          | 100.0%
  EN         | 6      | 100.0%          | 100.0%          | 100.0%
  HINGLISH   | 1      | 100.0%          | 100.0%          | 100.0%
==========================================================================================

==========================================================================================
 DUESPILOT BENCHMARK REPORT: GENERATED (v1.0)
 Rules R-62, R-63: Deterministic evaluation against ground_truth.json
==========================================================================================
Total Cases: 40 | Passed: 40 (100.0%)
Reconciled Balance Match: 40 / 40 (100.0%)
Conflict Recall: 100.0% | False-Conflict Rate: 0.0%
Unsupported-Claim Rate: 0.0% | Abstention Accuracy: 100.0%
Field Accuracy: 97.9%
Average Latency: 117 ms/case
------------------------------------------------------------------------------------------
COST & TIERING COMPARISON (MEASURED TOKENS):
  Tiered Routing Total Cost:      $0.0048 (4800 micros, avg 120 micros/case)
  All-Ultra Hypothetical Cost:    $0.0328 (32760 micros)
  Tiering Cost Savings Multiplier: 6.83x cheaper than All-Ultra
------------------------------------------------------------------------------------------
EXPERIMENT C — LANGUAGE GROUP BREAKDOWN:
  LANGUAGE   | CASES  | BALANCE MATCH   | CONFLICT RECALL  | FIELD ACCURACY 
  HI         | 3      | 100.0%          | 100.0%          | 100.0%
  EN         | 34     | 100.0%          | 100.0%          |  97.5%
  HINGLISH   | 3      | 100.0%          | 100.0%          | 100.0%
==========================================================================================
```

---

## 10. Deployment Architecture

DuesPilot is deployed in production across four specialized tiers:

- **Frontend:** Hosted on **Vercel** with Root Directory set to `frontend` (`frontend/vercel.json` provides SPA routing rewrites and strict CSP headers). Static build via Vite with route-level chunk splitting.
- **Backend:** Packaged via `deploy/Dockerfile` as an unprivileged container running Eclipse Temurin JRE 21 on **Google Cloud Run** in `asia-south1` (Mumbai) with 1 vCPU, 1 GiB RAM, CPU boost, min instances 0 (scale-to-zero), concurrency 80, and memory flags `-XX:+UseSerialGC -XX:MaxRAMPercentage=70.0`. Sensitive credentials are mounted directly from Google Secret Manager.
- **Database & Identity:** Hosted on **Supabase** (Managed PostgreSQL 16 with Flyway migrations managing the private `app` schema, PostgREST disabled for application tables, and RS256 JWKS JWT authentication). Pinned GitHub Actions workflow (`.github/workflows/keep-supabase-alive.yml`) runs every 3 days to prevent free-tier project pausing.
- **AI Inference:** Hosted on **Nebius Token Factory** (`token-factory.nebius.com`), calling NVIDIA Nemotron 3 Nano (30B), Super (120B), and Ultra (550B) at runtime (`DEMO_MODE=live`) protected by daily ($5.00) and per-case ($0.25) cost caps.
- **Regulatory Search:** Real-time Reserve Bank of India (RBI) Bank Rate lookups via the **Tavily Search API** with strict domain filtering (`rbi.org.in`).
- **Notification Services:** Email notices via **Resend** and WhatsApp click-to-chat links (`wa.me`) with sandbox mode (`NOTIFY_SANDBOX=true`) active by default.
- **Fallback:** Container service pre-configured for **Render** via [`render.yaml`](file:///c:/Users/ROHAN/OneDrive/Documents/01_CODE_JOURNEY/Projects/DuesPilot/render.yaml).

For complete step-by-step setup and infrastructure runbooks, see [`deploy/README.md`](deploy/README.md).

---

## 11. Instructions for Hackathon Judges

We have made evaluating DuesPilot completely frictionless and fully interactive:

1. **Visit the Live Application:** Open the deployed URL (or run locally at `http://localhost:5173`).
2. **Instant 1-Click Personal Demo:** Click **"Try demo — no sign-up"** on the landing page or login screen. Supabase creates an anonymous session, and DuesPilot automatically provisions an isolated, personal demo workspace (`is_demo=true`) pre-seeded with realistic cases.
3. **Full Operational Capabilities:**
   - Unlike restrictive read-only demos, you have full write access: **create new cases**, **upload invoices/receipts**, **trigger AI analyses**, and **review and approve drafted actions**.
   - Outbound notifications from demo workspaces are safely simulated (visible in the outbox as `SIMULATED` with zero external email or WhatsApp dispatch).
   - Abuse quotas protect infrastructure: max 30 cases, max 25 MB file storage, and max 3 live analyses per demo workspace (seeded cases replay with zero model cost). Demo workspaces auto-expire after 7 days idle.
4. **Inspect the Evidence Ledger:**
   - Open any case (e.g. `DP-0142` or `GEN-001`) to inspect the **Paise-exact reconciliation ledger**.
   - Click any extracted value to verify the **verbatim source-span quote** highlighting the exact characters from the source document.
   - Review detected conflicts (e.g. C1 Unacknowledged Short Delivery, C3 Price Mismatch, C6 Missing TDS Certificate).
   - Check the **MSMEDA Statutory Gates** (Gates A–D) and the computed compound interest schedule calculated at 3× the live RBI Bank Rate.
   - Inspect the human-approved **Notice & Next Action** draft before simulated dispatch.
5. **Seed / Reset Demo Data:** Registered users can also seed demonstration cases at any time from the case list via the **"Seed demo data"** dialog with real row count estimates.

---

## 12. Pre-Existing Work Statement

DuesPilot was developed and built fresh during the **Nebius × NVIDIA Global AI Hackathon** (October 2026). All domain engines (`duespilot-core`), multi-model LLM router and escalation logic, verbatim span verifier, REST/SSE APIs (`duespilot-api`), synthetic benchmark generator and evaluation pipeline (`duespilot-bench`), React 19 UI, and deployment manifests were authored specifically for this submission. No pre-existing proprietary codebase was reused.

---

## 13. Curated Repository Structure

```
duespilot/
├─ LICENSE                           # Apache-2.0 License
├─ README.md                         # This file
├─ THIRD_PARTY_NOTICES.md            # Typography and open-source licenses
├─ docker-compose.yml                # Local development stack (Postgres + App)
├─ .env.example                      # Unified environment template (Single source of truth)
├─ scripts/
│  ├─ check-env.js                   # Cross-platform environment validator
│  └─ check-env                      # Shell wrapper for check-env.js
├─ backend/
│  ├─ pom.xml                        # Multi-module Maven root
│  ├─ duespilot-core/                # Pure Java: Money, Dates, Reconciler, Gates, Spans
│  ├─ duespilot-api/                 # Spring Boot 4 REST API, SSE, LLM Client, Tavily
│  └─ duespilot-bench/               # Synthetic Case Generator, Runner, Scorer CLI
├─ frontend/
│  ├─ package.json                   # React 19 + TypeScript + Vite + Tailwind v4
│  ├─ playwright.config.ts           # Playwright E2E configuration
│  ├─ src/                           # UI pages, components, hooks, API client
│  └─ e2e/                           # End-to-end browser specifications
├─ deploy/
│  ├─ Dockerfile                     # Multi-stage production container build
│  ├─ README.md                      # Cloud Run / Render deployment instructions
│  └─ smoke.sh                       # Automated post-deployment smoke test
├─ docs/
│  ├─ architecture.md                # System architecture, schemas, and design invariants
│  ├─ secrets-and-env.md             # Secret locations, consumers, and rotation runbooks
│  ├─ prd.md                         # Product requirements and user workflows
│  ├─ rules.md                       # Engineering and legal compliance invariants
│  ├─ phases.md                      # Milestone roadmap and delivery schedule
│  └─ memory.md                      # Decision history, benchmarks, and gotchas
└─ .github/workflows/
   ├─ ci.yml                         # Secret scan, Maven build, Vitest, E2E
   └─ keep-supabase-alive.yml        # PostgREST ping workflow (3-day cron)
```

---

## 14. Limitations & Disclaimers

1. **Not Legal Advice:** DuesPilot is a technical reconciliation and computational assistant designed to assist MSME proprietors and accountants. It does not provide formal legal representation, legal opinions, or judicial determinations. Consult qualified legal counsel before filing petitions before MSME Facilitation Councils (MSEFC) or civil courts.
2. **Synthetic Demonstration Data:** To protect commercial confidentiality and comply with data protection regulations, all debtor names, GSTINs, addresses, invoices, and bank statements in the demo and benchmark suites are entirely synthetic.
3. **Marathi Language Support (Experimental):** Full bilingual support is currently verified for English, Hindi, and Hinglish. Support for Marathi document extraction and draft generation is experimental.

---

## 15. License

This project is licensed under the **Apache License, Version 2.0**. See the [LICENSE](LICENSE) file for details.

Third-party typography (Outfit, Plus Jakarta Sans, JetBrains Mono, Poppins) and open-source library notices are detailed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
