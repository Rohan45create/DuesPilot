# DuesPilot — Production Deployment Runbook

This runbook provides step-by-step instructions to deploy **DuesPilot** to production using the official architecture:
- **Database & Identity:** [Supabase](https://supabase.com) (Free-tier PostgreSQL with Flyway migrations in private schema `app`, RS256 JWKS JWT authentication).
- **Backend:** [Google Cloud Run](https://cloud.google.com/run) in `asia-south1` (Mumbai) packaged from [`deploy/Dockerfile`](file:///c:/Users/ROHAN/OneDrive/Documents/01_CODE_JOURNEY/Projects/DuesPilot/deploy/Dockerfile) (1 vCPU, 1 GiB, CPU boost, min instances 0, virtual threads concurrency).
- **Frontend:** [Vercel](https://vercel.com) (Root directory `frontend`, Vite static build, global edge CDN, SPA routing).
- **AI Inference:** [Nebius Token Factory](https://token-factory.nebius.com) calling NVIDIA Nemotron 3 (Nano 30B, Super 120B, Ultra 550B) at runtime.
- **Regulatory Search:** [Tavily](https://tavily.com) for real-time Reserve Bank of India (RBI) Bank Rate lookups.
- **Backend Fallback:** [Render](https://render.com) (`render.yaml` Docker web service on free tier).
- **Database Keep-Alive:** GitHub Actions workflow (`.github/workflows/keep-supabase-alive.yml`) querying `keep_alive` every 3 days.

---

## Architecture Diagram

```
                      ┌────────────────────────────────────────┐
                      │              Vercel Edge               │
                      │       React 19 + Vite Static SPA       │
                      │      Root Directory: "frontend"        │
                      │      https://duespilot.vercel.app      │
                      └───────────────────┬────────────────────┘
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  │                                               │
           Browser Client                                  Browser Client
        (Auth & Realtime SSE)                           (Authorization: Bearer)
                  │                                               │
                  ▼                                               ▼
       ┌────────────────────────┐                      ┌────────────────────────┐
       │     Supabase Auth      │                      │    Google Cloud Run    │
       │    (Google / Anon)     │                      │   (Spring Boot 4 API)  │
       │ https://<REF>.supabase │                      │  asia-south1 / 0-scale │
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

## Step 1: Supabase Setup (Database & Auth)

### 1.1 Create Supabase Project
1. Go to [Supabase Dashboard](https://supabase.com/dashboard) and click **New Project**.
2. Name: `duespilot`
3. Database Password: Generate and store securely (needed for `DATABASE_URL` and `DATABASE_URL_DIRECT`).
4. Region: Choose **South Asia (Mumbai)** (`ap-south-1`) to co-locate with Google Cloud Run `asia-south1` for low database latency (<10ms).
5. Plan: **Free Tier**.

### 1.2 Connection Strings
Under **Project Settings → Database → Connection string**:
- **Transaction Pooler (Port 6543 / Supavisor):**
  ```
  jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:6543/postgres?sslmode=require
  ```
  This is used for runtime application traffic (`DATABASE_URL`).
- **Session / Direct Connection (Port 5432):**
  ```
  jdbc:postgresql://db.<YOUR-PROJECT-REF>.supabase.co:5432/postgres?sslmode=require
  ```
  This is used for Flyway migrations (`DATABASE_URL_DIRECT`).

### 1.3 Supabase Authentication Configuration
Under **Authentication → Configuration**:

1. **URL Configuration:**
   - **Site URL:** `https://duespilot.vercel.app` (or your production Vercel URL).
   - **Redirect URLs:** Add:
     - `https://duespilot.vercel.app/auth/callback`
     - `https://*-<your-vercel-team>.vercel.app/auth/callback`
     - `http://localhost:5173/auth/callback`

2. **Auth Providers:**
   - **Anonymous Sign-ins:** Enable **Allow Anonymous Sign-ins** (essential for judges to launch 1-click personal demo workspaces).
   - **Email:** Enabled.
   - **Confirm Email:** If using default Supabase SMTP, turn off "Confirm email" for testing, or configure custom SMTP (e.g. Resend) under **Email Settings**.
   - **Google Provider:** Under **Providers → Google**, enable Google sign-in and provide Client ID and Client Secret from Google Cloud Console.

3. **Bot Protection (CAPTCHA):**
   - Under **Authentication → Protection**, enable Cloudflare Turnstile if using bot protection, entering Turnstile Secret Key.

4. **JWT Signing Keys:**
   - Supabase signs tokens using RS256. The Spring Boot backend automatically verifies JWTs using the JWKS endpoint:
     `https://<YOUR-PROJECT-REF>.supabase.co/auth/v1/.well-known/jwks.json`

### 1.4 Run Initial Flyway Migrations
Run Flyway once from your local workstation using the direct or session URL to initialize schemas and seed benchmark cases:

```bash
cd backend
mvn -pl duespilot-api flyway:migrate \
  -Dflyway.url="jdbc:postgresql://db.<YOUR-PROJECT-REF>.supabase.co:5432/postgres?sslmode=require" \
  -Dflyway.user="postgres.<YOUR-PROJECT-REF>" \
  -Dflyway.password="<YOUR_DB_PASSWORD>"
```

> [!NOTE]
> `flyway.baseline-on-migrate` is set to `false` by default. If migrating into an existing database that already contains tables, run `flyway:baseline` first or set `flyway.baseline-on-migrate: true`.

---

## Step 2: Build and Push Backend to Artifact Registry

### 2.1 Authenticate and Setup GCP
```bash
# Set GCP Project
gcloud auth login
gcloud config set project <YOUR_GCP_PROJECT_ID>

# Enable required Google Cloud APIs
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com

# Create Artifact Registry repository in asia-south1
gcloud artifacts repositories create duespilot \
  --repository-format=docker \
  --location=asia-south1 \
  --description="DuesPilot container repository"
```

### 2.2 Build and Push Container Image
Build the backend container using [`deploy/Dockerfile`](file:///c:/Users/ROHAN/OneDrive/Documents/01_CODE_JOURNEY/Projects/DuesPilot/deploy/Dockerfile):

```bash
gcloud builds submit \
  --tag asia-south1-docker.pkg.dev/<YOUR_GCP_PROJECT_ID>/duespilot/duespilot-api:latest \
  -f deploy/Dockerfile .
```

---

## Step 3: Configure Google Secret Manager

Store all sensitive secrets in Google Secret Manager:

```bash
# 1. Pooled Database URL (Supavisor port 6543)
echo -n "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:6543/postgres?sslmode=require&user=postgres.<REF>&password=<PASSWORD>" \
  | gcloud secrets create DATABASE_URL --data-file=-

# 2. Direct Database URL (Session port 5432)
echo -n "jdbc:postgresql://db.<REF>.supabase.co:5432/postgres?sslmode=require&user=postgres.<REF>&password=<PASSWORD>" \
  | gcloud secrets create DATABASE_URL_DIRECT --data-file=-

# 3. Supabase JWT Secret (from Supabase Dashboard -> Project Settings -> API -> JWT Secret)
echo -n "<SUPABASE_JWT_SECRET>" | gcloud secrets create SUPABASE_JWT_SECRET --data-file=-

# 4. Nebius Token Factory API Key
echo -n "<NEBIUS_API_KEY>" | gcloud secrets create LLM_API_KEY --data-file=-

# 5. Tavily Search API Key
echo -n "<TAVILY_API_KEY>" | gcloud secrets create TAVILY_API_KEY --data-file=-

# 6. Resend API Key (Optional / Sandbox)
echo -n "<RESEND_API_KEY>" | gcloud secrets create RESEND_API_KEY --data-file=-
```

---

## Step 4: Deploy Backend to Google Cloud Run

Deploy `duespilot-api` with 1 vCPU, 1 GiB, CPU boost, min instances 0, virtual thread concurrency 80, plain environment variables, and Secret Manager mounts:

```bash
gcloud run deploy duespilot-api \
  --image asia-south1-docker.pkg.dev/<YOUR_GCP_PROJECT_ID>/duespilot/duespilot-api:latest \
  --platform managed \
  --region asia-south1 \
  --allow-unauthenticated \
  --memory 1Gi \
  --cpu 1 \
  --cpu-boost \
  --min-instances 0 \
  --max-instances 10 \
  --concurrency 80 \
  --timeout 300 \
  --set-env-vars "\
SUPABASE_URL=https://<YOUR-PROJECT-REF>.supabase.co,\
CORS_ALLOWED_ORIGINS=https://duespilot.vercel.app,https://*-<your-vercel-team>.vercel.app,http://localhost:5173,\
NOTIFY_SANDBOX=true,\
DEMO_MODE=live,\
DAILY_COST_CAP_MICROS=5000000,\
PER_CASE_COST_CAP_MICROS=250000,\
LLM_BASE_URL=https://token-factory.nebius.com/v1,\
LLM_MAX_INFLIGHT=4,\
RATE_LIMIT_RPM=60,\
SPRING_PROFILES_ACTIVE=prod" \
  --set-secrets "\
SPRING_DATASOURCE_URL=DATABASE_URL:latest,\
DATABASE_URL=DATABASE_URL:latest,\
DATABASE_URL_DIRECT=DATABASE_URL_DIRECT:latest,\
SUPABASE_JWT_SECRET=SUPABASE_JWT_SECRET:latest,\
LLM_API_KEY=LLM_API_KEY:latest,\
TAVILY_API_KEY=TAVILY_API_KEY:latest,\
RESEND_API_KEY=RESEND_API_KEY:latest"
```

Save the output Cloud Run Service URL (e.g. `https://duespilot-api-xxx.asia-south1.run.app`).

---

## Step 5: Deploy Frontend to Vercel

### 5.1 Project Settings in Vercel Dashboard
1. Go to [Vercel Dashboard](https://vercel.com/dashboard) and click **Add New... → Project**.
2. Import the `DuesPilot` Git repository.
3. In **Project Configuration**:
   - **Framework Preset:** Vite
   - **Root Directory:** Edit and set to `frontend` (critical: must be `frontend`).
   - **Build Command:** `npm run build`
   - **Output Directory:** `dist`
   - **Install Command:** `npm install`

### 5.2 Frontend Environment Variables
Add the following in Vercel **Environment Variables**:

| Variable | Value | Description |
|---|---|---|
| `VITE_SUPABASE_URL` | `https://<YOUR-PROJECT-REF>.supabase.co` | Supabase API endpoint |
| `VITE_SUPABASE_ANON_KEY` | `<SUPABASE_ANON_KEY>` | Public client anon key |
| `VITE_API_BASE_URL` | `https://duespilot-api-xxx.asia-south1.run.app` | Cloud Run backend URL |
| `VITE_TURNSTILE_SITE_KEY` | `<SITE_KEY>` (or `1x00000000000000000000AA`) | Cloudflare Turnstile public key |
| `VITE_MOCK_API` | `false` | Explicitly disabled; uses real backend |
| `VITE_SMTP_CONFIGURED` | `false` | Sandbox mode by default |

Click **Deploy**.

---

## Step 6: Render Fallback Deployment (`render.yaml`)

If Google Cloud Run is unavailable, DuesPilot can be deployed on Render free tier via the committed [`render.yaml`](file:///c:/Users/ROHAN/OneDrive/Documents/01_CODE_JOURNEY/Projects/DuesPilot/render.yaml):

1. Go to [Render Dashboard](https://dashboard.render.com/) and click **New + → Blueprint**.
2. Connect the `DuesPilot` repository.
3. Render reads `render.yaml`, which builds `./deploy/Dockerfile`.
4. Enter the sensitive environment variables marked with `sync: false` in Render UI:
   - `DATABASE_URL`
   - `DATABASE_URL_DIRECT`
   - `SPRING_DATASOURCE_PASSWORD`
   - `LLM_API_KEY`
   - `TAVILY_API_KEY`
   - `RESEND_API_KEY`
5. Click **Apply**.

> [!NOTE]
> Render free tier spins down after 15 minutes idle and takes 30-60 seconds to wake on the next request.

---

## Step 7: Database Keep-Alive Workflow Setup

Supabase free databases pause after 7 days of inactivity. The GitHub Actions workflow [`.github/workflows/keep-supabase-alive.yml`](file:///c:/Users/ROHAN/OneDrive/Documents/01_CODE_JOURNEY/Projects/DuesPilot/.github/workflows/keep-supabase-alive.yml) runs every 3 days.

In your GitHub repository:
1. Go to **Settings → Secrets and variables → Actions**.
2. Add Repository Secrets:
   - `SUPABASE_URL`: `https://<YOUR-PROJECT-REF>.supabase.co`
   - `SUPABASE_ANON_KEY`: `<SUPABASE_ANON_KEY>`

> [!WARNING]
> Do NOT add Render URL to any keep-alive cron. Pinging Render continuously exhausts its 750 free monthly hours.

---

## Step 8: Post-Deployment Smoke Test

Verify all components using [`deploy/smoke.sh`](file:///c:/Users/ROHAN/OneDrive/Documents/01_CODE_JOURNEY/Projects/DuesPilot/deploy/smoke.sh):

```bash
chmod +x deploy/smoke.sh
./deploy/smoke.sh \
  https://duespilot-api-xxx.asia-south1.run.app \
  https://duespilot.vercel.app \
  https://<YOUR-PROJECT-REF>.supabase.co \
  <SUPABASE_ANON_KEY>
```

The script verifies:
1. Public health check (`GET /actuator/health` -> HTTP 200 `UP`).
2. Frontend availability (`GET /` and `GET /signin` -> HTTP 200).
3. 1-Click anonymous demo login and JWT validation (`GET /api/v1/me`).
4. Replay-mode analysis on seeded case `DP-0142`.
5. Safe budget cap enforcement under live conditions.
