# DuesPilot — Production Deployment & Infrastructure Guide

This guide details the complete production deployment setup for **DuesPilot**:
- **Frontend:** Hosted on **Vercel** (Global Edge CDN, SPA rewrites, strict Content-Security-Policy).
- **Backend:** Hosted on **Google Cloud Run** (Scale-to-zero, virtual threads concurrency, unprivileged JRE container).
- **Database & Identity:** Hosted on **Supabase** (Managed PostgreSQL with Flyway migrations, RLS, and OAuth2/JWT authentication).
- **Backend Fallback:** Hosted on **Render** (`render.yaml` Docker web service on free tier).
- **Evaluation:** Run benchmarks locally or as a **Nebius Serverless Job**.

---

## 1. Architecture Overview

```
                      ┌────────────────────────────────────────┐
                      │             Vercel Edge                │
                      │    (SPA: React 19 + Tailwind v4)      │
                      │         https://duespilot.vercel.app   │
                      └───────────────────┬────────────────────┘
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  │                                               │
           Browser Client                                  Browser Client
       (Auth & Realtime SSE)                           (Direct Auth Token)
                  │                                               │
                  ▼                                               ▼
       ┌────────────────────────┐                      ┌────────────────────────┐
       │     Supabase Auth      │                      │    Google Cloud Run    │
       │    (Google / Anon)     │                      │   (Spring Boot 4 API)  │
       │  https://*.supabase.co │                      │  asia-south1 / 0-scale │
       └──────────┬─────────────┘                      └──────────┬─────────────┘
                  │                                               │
                  │ Validate Bearer JWT via JWKS                  │ HikariCP Pool
                  └───────────────────────┬───────────────────────┘ (Direct/Pooled)
                                          ▼
                               ┌────────────────────────┐
                               │  Supabase PostgreSQL   │
                               │  Schema: app (Private) │
                               │   RLS: Zero Public API │
                               └────────────────────────┘
```

---

## 2. Supabase Configuration (Database & Auth)

### 2.1 Database & Connection Strings
1. In the [Supabase Dashboard](https://supabase.com/dashboard), create a new project in the closest region to your backend (e.g. **Mumbai / ap-south-1** for Cloud Run `asia-south1`, or **Singapore / ap-southeast-1`).
2. Obtain your database connection strings under **Project Settings → Database**:
   - **Transaction Pooler (Port 6543 / Supavisor):** Use for runtime API connections (`DATABASE_URL`).
   - **Session / Direct Connection (Port 5432):** Use for Flyway database migrations (`DATABASE_URL_DIRECT`).
3. DuesPilot tables reside in private schema `app` with strict Row Level Security (RLS) enabled. PostgREST public exposure is prevented.

### 2.2 Supabase Auth & Redirect URLs
Under **Authentication → URL Configuration**:
1. Set **Site URL**:
   ```
   https://duespilot.vercel.app
   ```
2. In **Redirect URLs**, add all required callback patterns:
   ```
   http://localhost:5173/auth/callback
   https://duespilot.vercel.app/auth/callback
   https://*-<your-vercel-team>.vercel.app/auth/callback
   ```
3. Under **Authentication → Providers**:
   - Enable **Email** (optional: disable email confirmation for instant demo signup).
   - Enable **Anonymous sign-ins** (allows 1-click evaluation by judges).
   - Enable **Google** (enter Client ID and Secret if using Google OAuth).

---

## 3. Frontend Deployment on Vercel

The frontend is configured via [`frontend/vercel.json`](file:///c:/Users/ROHAN/OneDrive/Documents/01_CODE_JOURNEY/Projects/DuesPilot/frontend/vercel.json) with SPA rewrites, clean URLs, and strict security headers.

### 3.1 Content-Security-Policy (CSP)
The CSP header in `vercel.json` permits:
- **Scripts:** `'self'`, `'unsafe-inline'`, `https://challenges.cloudflare.com` (Turnstile).
- **Styles:** `'self'`, `'unsafe-inline'`, `https://fonts.googleapis.com`.
- **Fonts:** `'self'`, `https://fonts.gstatic.com`, `data:`.
- **Connections:** `'self'`, `https://*.supabase.co`, `wss://*.supabase.co`, `https://challenges.cloudflare.com`, and the Cloud Run backend URL.
- **Frames:** `https://challenges.cloudflare.com` (Turnstile widget).

### 3.2 Environment Variables (Vercel Project Settings)
> [!CAUTION]
> **CRITICAL SECURITY REQUIREMENT:**
> NEVER expose `SUPABASE_SERVICE_ROLE_KEY` or any database password in Vercel. Only public `VITE_` variables belong here.

| Variable | Required | Description | Example |
|---|---|---|---|
| `VITE_SUPABASE_URL` | Yes | Supabase API endpoint | `https://gkaeiociqcjsbjfgdofi.supabase.co` |
| `VITE_SUPABASE_ANON_KEY` | Yes | Public Supabase anonymous client key | `eyJhbGciOi...` |
| `VITE_TURNSTILE_SITE_KEY` | Yes | Cloudflare Turnstile public key (test key `1x00000000000000000000AA`) | `1x00000000000000000000AA` |
| `VITE_API_BASE_URL` | Yes | Google Cloud Run backend URL | `https://duespilot-api-xxx.asia-south1.run.app` |
| `VITE_MOCK_API` | Yes | Set to `false` for live backend API calls | `false` |
| `VITE_SMTP_CONFIGURED` | No | Set to `true` if custom SMTP is active | `false` |

### 3.3 Deploy via Vercel CLI
```bash
# Navigate to frontend
cd frontend

# Deploy preview
vercel

# Deploy production
vercel --prod
```

---

## 4. Backend Deployment on Google Cloud Run

The backend is packaged using the hardened, backend-only Dockerfile [`deploy/Dockerfile.backend`](file:///c:/Users/ROHAN/OneDrive/Documents/01_CODE_JOURNEY/Projects/DuesPilot/deploy/Dockerfile.backend) running an unprivileged `eclipse-temurin:21-jre-alpine` runtime with memory-conscious JVM flags.

### 4.1 Cloud Run Configuration Parameters
- **Region:** `asia-south1` (Mumbai) — colocated with Supabase Mumbai database for sub-10ms query latency.
- **Minimum Instances:** `0` (Zero idle compute costs; scales down to zero when idle).
- **Maximum Instances:** `10`
- **Concurrency:** `80` (Optimized for Spring Boot virtual threads / Project Loom).
- **CPU & Memory:** `1 vCPU`, `1 GiB` RAM (or `2 GiB` for heavy workloads).
- **Startup CPU Boost:** Enabled (`--cpu-boost`) for fast JVM bootstrap.
- **Timeout:** `300s` (Supports long-lived SSE evidence-streaming connections).
- **Graceful Shutdown:** Configured via `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s`.

### 4.2 Build and Push Container to Artifact Registry
```bash
# 1. Authenticate with Google Cloud
gcloud auth login
gcloud config set project <YOUR_GCP_PROJECT_ID>

# 2. Enable Cloud Run and Artifact Registry
gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com

# 3. Create Artifact Registry repository (if not already created)
gcloud artifacts repositories create duespilot \
  --repository-format=docker \
  --location=asia-south1 \
  --description="DuesPilot container images"

# 4. Build and push backend image
gcloud builds submit \
  --tag asia-south1-docker.pkg.dev/<YOUR_GCP_PROJECT_ID>/duespilot/duespilot-api:latest \
  -f deploy/Dockerfile.backend .
```

### 4.3 Configure Secrets in Google Secret Manager
```bash
# Database password
echo -n "your-supabase-db-password" | gcloud secrets create duespilot-db-password --data-file=-

# Nebius Token Factory API Key
echo -n "nvapi-xxx" | gcloud secrets create duespilot-nebius-key --data-file=-

# Tavily API Key
echo -n "tvly-xxx" | gcloud secrets create duespilot-tavily-key --data-file=-
```

### 4.4 Deploy to Cloud Run
```bash
gcloud run deploy duespilot-api \
  --image asia-south1-docker.pkg.dev/<YOUR_GCP_PROJECT_ID>/duespilot/duespilot-api:latest \
  --platform managed \
  --region asia-south1 \
  --allow-unauthenticated \
  --memory 1Gi \
  --cpu 1 \
  --min-instances 0 \
  --max-instances 10 \
  --concurrency 80 \
  --cpu-boost \
  --timeout 300 \
  --set-env-vars "\
DEMO_MODE=replay,\
RATE_LIMIT_RPM=60,\
DAILY_COST_CAP_MICROS=50000000,\
PER_CASE_COST_CAP_MICROS=2000000,\
LLM_MAX_INFLIGHT=8,\
CORS_ALLOWED_ORIGINS=https://duespilot.vercel.app,https://*-<your-vercel-team>.vercel.app,http://localhost:5173,\
SUPABASE_URL=https://<YOUR_PROJECT_REF>.supabase.co,\
SUPABASE_JWKS_URI=https://<YOUR_PROJECT_REF>.supabase.co/auth/v1/.well-known/jwks.json,\
DATABASE_URL=jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:6543/postgres?sslmode=require,\
DATABASE_URL_DIRECT=jdbc:postgresql://db.<YOUR_PROJECT_REF>.supabase.co:5432/postgres?sslmode=require,\
SPRING_DATASOURCE_USERNAME=postgres.<YOUR_PROJECT_REF>" \
  --set-secrets "\
SPRING_DATASOURCE_PASSWORD=duespilot-db-password:latest,\
NEBIUS_API_KEY=duespilot-nebius-key:latest,\
TAVILY_API_KEY=duespilot-tavily-key:latest"
```

---

## 5. Render Fallback Deployment (`render.yaml`)

If Google Cloud Run is temporarily unavailable or if an alternative host is preferred, DuesPilot includes a fully configured [`render.yaml`](file:///c:/Users/ROHAN/OneDrive/Documents/01_CODE_JOURNEY/Projects/DuesPilot/render.yaml) specification for Render's free tier.

> [!NOTE]
> **Render Free Plan Characteristics:**
> - **Spin-down:** Automatically spins down after **15 minutes** of inactivity.
> - **Cold-Start Delay:** The first inbound request takes **30 to 60 seconds** to wake the JVM.
> - **Resource Quota:** 0.1 CPU core, 512 MB RAM, and **750 free instance hours per month**.

### Deploy via Render Blueprint:
1. Log in to [Render Dashboard](https://dashboard.render.com/).
2. Click **New + → Blueprint**.
3. Connect the `DuesPilot` repository. Render will automatically read `render.yaml`.
4. Fill in the secret environment variables (`DATABASE_URL`, `DATABASE_URL_DIRECT`, `SPRING_DATASOURCE_PASSWORD`, `NEBIUS_API_KEY`, `TAVILY_API_KEY`).
5. Click **Apply**.

---

## 6. Keep-Alive Workflow & Health Probes

Supabase free databases pause after 7 days of inactivity. The GitHub Actions workflow [`.github/workflows/keep-supabase-alive.yml`](file:///c:/Users/ROHAN/OneDrive/Documents/01_CODE_JOURNEY/Projects/DuesPilot/.github/workflows/keep-supabase-alive.yml) runs every 3 days to query the `keep_alive` table.

### Optional Cloud Run Backend Ping
The workflow includes an optional step to ping Cloud Run's `/api/v1/ping` endpoint every 3 days if `CLOUD_RUN_BACKEND_URL` is set in GitHub repository secrets:
```
Repository Secrets:
- SUPABASE_URL: https://<project-ref>.supabase.co
- SUPABASE_ANON_KEY: <anon-jwt>
- CLOUD_RUN_BACKEND_URL: https://duespilot-api-xxx.asia-south1.run.app (Optional)
```

> [!WARNING]
> **NEVER ping Render with this workflow.** Pinging Render on a recurring timer keeps the container running 24/7 and burns through the 750 free hours in month 1.

---

## 7. Verification: Full-Stack Smoke Test

Run the automated smoke test script [`deploy/smoke.sh`](file:///c:/Users/ROHAN/OneDrive/Documents/01_CODE_JOURNEY/Projects/DuesPilot/deploy/smoke.sh) to verify all layers before submitting:

```bash
# Syntax:
# ./deploy/smoke.sh [BACKEND_URL] [FRONTEND_URL] [SUPABASE_URL] [SUPABASE_ANON_KEY]

# Production test:
./deploy/smoke.sh \
  https://duespilot-api-xxx.asia-south1.run.app \
  https://duespilot.vercel.app \
  https://gkaeiociqcjsbjfgdofi.supabase.co \
  eyJhbGciOi...
```

### What `smoke.sh` Verifies:
1. **Frontend Load:** HTTP 200 on landing page `/` and auth page `/signin`.
2. **Backend Health:** `/actuator/health`, `/api/v1/ping`, and `/api/v1/usage/models` cost meter.
3. **Demo Sign-in:** Validates authentication token handling on `/api/v1/me`.
4. **Replay Mode Case:** Validates seeded case `DP-0142` and analysis cache.
5. **Live Case under Cap:** Creates a test case, uploads an invoice document, triggers analysis, and asserts either HTTP 200/202 or safe budget cap enforcement (HTTP 429).

---

## 8. Nebius Serverless Job (Benchmark Evaluation)

If access to Nebius AI Cloud or Serverless Containers exists, run the reproducible benchmark suite against Nebius Token Factory:

```bash
# Run benchmark container as a Nebius Serverless Job
nebius serverless job run \
  --name duespilot-benchmarks-$(date +%s) \
  --image cr.nebius.ai/<YOUR_REGISTRY>/duespilot-api:latest \
  --cores 4 \
  --memory 8Gi \
  --environment-variable SPRING_PROFILES_ACTIVE=benchmark \
  --secret-environment-variable NEBIUS_API_KEY=duespilot-nebius-key \
  --secret-environment-variable TAVILY_API_KEY=duespilot-tavily-key
```

Or run locally via Maven:
```bash
cd backend
mvn test -pl duespilot-bench -Dtest=NemotronRoutingBenchmarkTest
```
