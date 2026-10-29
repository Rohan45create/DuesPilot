# Secrets and Environment Variables Guide

> **Security Invariant (Rule R-83):** Exactly one `.env` file exists at the repository root for local development. Never commit `.env` or any real credentials to version control. This document lists variable **names only**, never values.

---

## 1. Secrets Inventory & Location Matrix

The following table documents every secret and configuration variable used across DuesPilot environments:

| Variable Name | Local `.env` | GitHub Actions Secrets | Vercel Env (Frontend) | Cloud Run / Render (Backend) | Provider Dashboard / Origin |
|---|---|---|---|---|---|
| `POSTGRES_DB` | Required | — | — | Config / Env | Local Docker / Supabase |
| `POSTGRES_USER` | Required | — | — | Config / Env | Local Docker / Supabase |
| `POSTGRES_PASSWORD` | Required | — | — | Secret Manager | Local Docker / Supabase DB Settings |
| `POSTGRES_PORT` | Optional (5432) | — | — | Config / Env | Local Docker / Supabase (6543 pooled, 5432 direct) |
| `SPRING_DATASOURCE_URL` | Required | — | — | Secret Manager | Supabase Connection String (JDBC) |
| `SPRING_DATASOURCE_USERNAME` | Required | — | — | Config / Env | Supabase Database Username |
| `SPRING_DATASOURCE_PASSWORD` | Required | — | — | Secret Manager | Supabase Database Password |
| `DATABASE_URL` | Required | — | — | Secret Manager | Supabase Connection String |
| `DATABASE_URL_DIRECT` | Required | — | — | Secret Manager | Supabase Direct Session Mode URL (5432) |
| `SUPABASE_URL` | Required | **Required** | — | Config / Env | Supabase Project Settings → API |
| `SUPABASE_JWKS_URI` | Required | — | — | Config / Env | Derived from `SUPABASE_URL/auth/v1/.well-known/jwks.json` |
| `SUPABASE_ANON_KEY` | — | **Required** | — | — | Supabase Project Settings → API (keepalive only) |
| `NEBIUS_API_KEY` | Required | — | — | Secret Manager | Nebius Token Factory Console |
| `LLM_BASE_URL` | Required | — | — | Config / Env | Nebius Token Factory API URL |
| `TAVILY_API_KEY` | Required | — | — | Secret Manager | Tavily Developer Dashboard |
| `TAVILY_BASE_URL` | Optional | — | — | Config / Env | Tavily API URL |
| `VITE_SUPABASE_URL` | Required | — | Required | — | Supabase Project Settings → API |
| `VITE_SUPABASE_ANON_KEY` | Required | — | Required | — | Supabase Project Settings → API (public anon key) |
| `VITE_TURNSTILE_SITE_KEY` | Optional | — | Required | — | Cloudflare Turnstile Dashboard |
| `VITE_API_BASE_URL` | Required | — | Required | — | Cloud Run / Render Backend Origin URL |
| `VITE_SMTP_CONFIGURED` | Optional | — | Required | — | Set `true` if Resend/SMTP is live, `false` for sandbox |
| `VITE_MOCK_API` | Optional | — | Required | — | Set `false` for live backend API |
| `RESEND_API_KEY` | Optional | — | — | Secret Manager | Resend API Keys Dashboard |
| `RESEND_BASE_URL` | Optional | — | — | Config / Env | Resend API URL |
| `RESEND_FROM_EMAIL` | Optional | — | — | Config / Env | Resend Verified Domain Email |
| `RESEND_WEBHOOK_SECRET` | Optional | — | — | Secret Manager | Resend Webhooks Settings |
| `WHATSAPP_TOKEN` | Optional | — | — | Secret Manager | Meta for Developers → WhatsApp Access Token |
| `WHATSAPP_PHONE_NUMBER_ID`| Optional | — | — | Config / Env | Meta for Developers → Phone Number ID |
| `WHATSAPP_VERIFY_TOKEN` | Optional | — | — | Config / Env | Webhook Callback Verification String |
| `WHATSAPP_APP_SECRET` | Optional | — | — | Secret Manager | Meta App Dashboard → Basic Settings |
| `WHATSAPP_BASE_URL` | Optional | — | — | Config / Env | Meta Graph API Base URL |
| `CORS_ALLOWED_ORIGINS` | Required | — | — | Config / Env | Permitted Frontend Origins (e.g. Vercel, localhost) |
| `DEMO_MODE` | Required | — | — | Config / Env | `replay` or `live` |
| `RATE_LIMIT_RPM` | Optional | — | — | Config / Env | Client IP rate limit (default 60) |
| `DAILY_COST_CAP_MICROS` | Optional | — | — | Config / Env | Daily LLM spend ceiling in micros (USD) |
| `PER_CASE_COST_CAP_MICROS`| Optional | — | — | Config / Env | Per-case LLM spend ceiling in micros (USD) |
| `LLM_MAX_INFLIGHT` | Optional | — | — | Config / Env | In-flight concurrency limit for LLM calls |
| `STORAGE_DIR` | Optional | — | — | Config / Env | Local fallback upload directory |
| `NOTIFY_SANDBOX` | Optional | — | — | Config / Env | Set `true` to record notices without external dispatch |
| `NOTIFY_SANDBOX_EMAIL` | Optional | — | — | Config / Env | Sandbox test destination email |
| `NOTIFY_SANDBOX_PHONE` | Optional | — | — | Config / Env | Sandbox test destination phone |
| `CLOUD_RUN_BACKEND_URL` | Optional | — | — | — | Optional Cloud Run backend health ping target |

> **IMPORTANT: GitHub Actions Secrets Scope**  
> GitHub Actions repository secrets are strictly limited to two keys:
> 1. `SUPABASE_URL`
> 2. `SUPABASE_ANON_KEY`  
> These are used exclusively by `.github/workflows/keep-supabase-alive.yml` to execute a periodic read ping against the Supabase PostgREST API to keep the free-tier database active. No backend or LLM secrets are permitted or needed in GitHub Actions.

---

## 2. Component Requirements & Rotation Runbooks

### 2.1 Database Credentials (`POSTGRES_PASSWORD`, `SPRING_DATASOURCE_PASSWORD`)
- **Who needs it:** Backend Spring Boot application (`duespilot-api`), Flyway migrations, benchmark tool (`duespilot-bench`).
- **Where it lives:** Local `.env`, Cloud Run Secret Manager / Render Environment.
- **Rotation Procedure:**
  1. Open Supabase Dashboard → **Project Settings** → **Database** → **Database Password**.
  2. Generate a new secure password.
  3. Update Cloud Run Secret Manager / Render environment variable `SPRING_DATASOURCE_PASSWORD` and `DATABASE_URL`.
  4. Update local root `.env`.
  5. Redeploy backend; verify connectivity with `GET /actuator/health`.

### 2.2 Nebius Token Factory Key (`NEBIUS_API_KEY`)
- **Who needs it:** Backend `RestClientLlmClient` for NVIDIA Nemotron 3 Nano/Super/Ultra calls.
- **Where it lives:** Local `.env`, Cloud Run Secret Manager / Render Environment.
- **Rotation Procedure:**
  1. Navigate to Nebius Token Factory Console → **API Keys**.
  2. Create a new API key.
  3. Update Cloud Run Secret Manager with the new secret value.
  4. In local `.env`, update `NEBIUS_API_KEY`.
  5. Verify backend live inference or run holdout benchmark (`mvn -pl duespilot-bench exec:java ...`).
  6. Revoke the old key in the Nebius Console.

### 2.3 Tavily Search API Key (`TAVILY_API_KEY`)
- **Who needs it:** Backend `TavilyRateProvider` for official Reserve Bank of India (RBI) Bank Rate lookups.
- **Where it lives:** Local `.env`, Cloud Run Secret Manager / Render Environment.
- **Rotation Procedure:**
  1. Navigate to Tavily Developer Dashboard → **API Keys**.
  2. Generate a new API Key.
  3. Update Cloud Run Secret Manager / Render environment.
  4. Update local `.env`.
  5. Test lookup via `GET /api/v1/rates/bank-rate` or run live test (`-Dduespilot.live-tests=true`).
  6. Delete previous key in Tavily Dashboard.

### 2.4 Supabase Anon Key & JWKS (`VITE_SUPABASE_ANON_KEY`, `SUPABASE_ANON_KEY`, `SUPABASE_JWKS_URI`)
- **Who needs it:** 
  - Frontend SPA (Supabase JavaScript SDK for client authentication).
  - GitHub Actions `keep-supabase-alive.yml` (for periodic ping).
  - Backend Spring Boot (validates JWT tokens using `SUPABASE_JWKS_URI` public keys).
- **Where it lives:** Local `.env`, Vercel project environment settings, GitHub Actions Secrets (`SUPABASE_ANON_KEY`).
- **Rotation Procedure:**
  1. Supabase Dashboard → **Project Settings** → **API**.
  2. Generate a new API Secret / Anon key.
  3. Update Vercel environment variable `VITE_SUPABASE_ANON_KEY` and trigger a frontend redeployment.
  4. Update GitHub Actions repository secret `SUPABASE_ANON_KEY`.
  5. Update local `.env`.
  6. Note: `SUPABASE_JWKS_URI` uses asymmetric RS256 public keys and does not require manual secret rotation when rotating the API key.

### 2.5 Resend API Key (`RESEND_API_KEY`)
- **Who needs it:** Backend notification service for outbound payment reminders (when `NOTIFY_SANDBOX=false`).
- **Where it lives:** Local `.env`, Cloud Run Secret Manager / Render Environment.
- **Rotation Procedure:**
  1. Navigate to Resend Dashboard → **API Keys**.
  2. Create a new API key with restricted sending permissions for your verified domain.
  3. Update Cloud Run Secret Manager / Render environment.
  4. Revoke previous key in Resend dashboard.

### 2.6 WhatsApp Cloud API (`WHATSAPP_TOKEN`, `WHATSAPP_APP_SECRET`)
- **Who needs it:** Optional automated delivery of human-approved WhatsApp templates.
- **Where it lives:** Local `.env`, Cloud Run Secret Manager / Render Environment.
- **Rotation Procedure:**
  1. Meta for Developers → App Dashboard → **WhatsApp** → **API Setup**.
  2. Generate a permanent System User Access Token in Meta Business Manager.
  3. Update Cloud Run Secret Manager / Render environment.
  4. Update `WHATSAPP_VERIFY_TOKEN` and webhook callbacks if webhook secrets change.
