-- Owner: API. All money in paise (BIGINT). Costs in micro-USD (BIGINT).
-- Schema isolation: All application tables reside in the private 'app' schema with RLS enabled and zero public policies.
-- Only public.keep_alive is in the public schema and publicly readable by anon/authenticated roles.

CREATE SCHEMA IF NOT EXISTS app;

SET search_path = app, public;

CREATE TABLE app.cases (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code TEXT UNIQUE NOT NULL,                       -- e.g. DP-001
  title TEXT NOT NULL,
  supplier_name TEXT, buyer_name TEXT,
  supplier_category TEXT NOT NULL DEFAULT 'UNKNOWN'
    CHECK (supplier_category IN ('MICRO','SMALL','MEDIUM','UNKNOWN')),
  udyam_registered_on DATE,
  is_seed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE app.documents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  case_id UUID NOT NULL REFERENCES app.cases(id) ON DELETE CASCADE,
  doc_type TEXT NOT NULL CHECK (doc_type IN
    ('PO','INVOICE','DELIVERY','ACCEPTANCE','CREDIT_NOTE','PAYMENT','MESSAGE','CONTRACT','UDYAM')),
  file_name TEXT NOT NULL,
  mime_type TEXT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  storage_path TEXT NOT NULL DEFAULT 'bytea',
  page_count INTEGER NOT NULL DEFAULT 1,
  file_size_bytes INTEGER NOT NULL DEFAULT 0 CHECK (file_size_bytes <= 5242880),
  content_bytes BYTEA,
  doc_date DATE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_documents_case ON app.documents(case_id);

CREATE TABLE app.document_pages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES app.documents(id) ON DELETE CASCADE,
  page_no INTEGER NOT NULL,
  text TEXT NOT NULL,
  UNIQUE (document_id, page_no)
);

CREATE TABLE app.extracted_fields (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES app.documents(id) ON DELETE CASCADE,
  run_id UUID NOT NULL,
  field_name TEXT NOT NULL,
  value_text TEXT, value_paise BIGINT, value_date DATE,   -- NULL = unknown, never 0
  status TEXT NOT NULL CHECK (status IN ('VERIFIED','UNVERIFIED','REJECTED')),
  page_no INTEGER, char_start INTEGER, char_end INTEGER, quote TEXT,
  model TEXT NOT NULL
);
CREATE INDEX idx_fields_doc_run ON app.extracted_fields(document_id, run_id);

CREATE TABLE app.analysis_runs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  case_id UUID NOT NULL REFERENCES app.cases(id) ON DELETE CASCADE,
  status TEXT NOT NULL CHECK (status IN ('RUNNING','COMPLETED','FAILED')),
  router_policy JSONB NOT NULL,
  total_cost_micros BIGINT NOT NULL DEFAULT 0,
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  finished_at TIMESTAMPTZ
);
CREATE INDEX idx_runs_case ON app.analysis_runs(case_id, started_at DESC);

CREATE TABLE app.ledger_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id UUID NOT NULL REFERENCES app.analysis_runs(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('INVOICE','PAYMENT','CREDIT_NOTE','ADJUSTMENT')),
  ref TEXT,
  amount_paise BIGINT NOT NULL,
  entry_date DATE,
  status TEXT NOT NULL CHECK (status IN ('CONFIRMED','UNVERIFIED','DISPUTED','DUPLICATE_SUSPECT')),
  source_field_ids UUID[] NOT NULL
);

CREATE TABLE app.reconciliation_results (
  run_id UUID PRIMARY KEY REFERENCES app.analysis_runs(id) ON DELETE CASCADE,
  invoiced_paise BIGINT, confirmed_paid_paise BIGINT, confirmed_credit_paise BIGINT,
  disputed_paise BIGINT, unverified_paise BIGINT,
  balance_paise BIGINT,                                   -- NULL = cannot be determined
  balance_status TEXT NOT NULL CHECK (balance_status IN ('RECONCILED','PARTIAL','UNRECONCILED'))
);

CREATE TABLE app.conflicts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id UUID NOT NULL REFERENCES app.analysis_runs(id) ON DELETE CASCADE,
  type TEXT NOT NULL CHECK (type ~ '^C([1-9]|10)$'),
  detected_by TEXT NOT NULL CHECK (detected_by IN ('RULE','LLM')),
  severity TEXT NOT NULL CHECK (severity IN ('INFO','WARN','BLOCKING')),
  summary TEXT NOT NULL,
  amount_paise BIGINT,
  evidence_field_ids UUID[] NOT NULL,
  status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','ACKNOWLEDGED','DISMISSED'))
);

CREATE TABLE app.eligibility_assessments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id UUID NOT NULL REFERENCES app.analysis_runs(id) ON DELETE CASCADE,
  gate CHAR(1) NOT NULL CHECK (gate IN ('A','B','C','D')),
  status TEXT NOT NULL CHECK (status IN
    ('ELIGIBLE_FOR_REVIEW','NOT_ESTABLISHED','HUMAN_REVIEW_REQUIRED','OUT_OF_SCOPE')),
  reasons JSONB NOT NULL,
  UNIQUE (run_id, gate)
);

CREATE TABLE app.rate_references (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_url TEXT NOT NULL,
  raw_excerpt TEXT NOT NULL,
  rate_bps INTEGER NOT NULL CHECK (rate_bps BETWEEN 0 AND 2500),
  effective_on DATE NOT NULL,
  retrieved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  confirmed_at TIMESTAMPTZ,
  confirmed_by TEXT
);

CREATE TABLE app.interest_estimates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id UUID NOT NULL REFERENCES app.analysis_runs(id) ON DELETE CASCADE,
  principal_paise BIGINT NOT NULL,
  rate_ref_id UUID NOT NULL REFERENCES app.rate_references(id),
  multiplier INTEGER NOT NULL,
  from_date DATE NOT NULL,
  to_date DATE NOT NULL,
  interest_paise BIGINT NOT NULL,
  convention TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PROVISIONAL' CHECK (status = 'PROVISIONAL')
);

CREATE TABLE app.proposed_actions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id UUID NOT NULL REFERENCES app.analysis_runs(id) ON DELETE CASCADE,
  action_type TEXT NOT NULL,
  language TEXT NOT NULL CHECK (language IN ('en','hi')),
  draft_text TEXT NOT NULL,
  evidence_field_ids UUID[] NOT NULL,
  status TEXT NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED','APPROVED','REJECTED')),
  decided_by TEXT,
  decided_at TIMESTAMPTZ
);

CREATE TABLE app.llm_calls (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id UUID,
  benchmark_run_id UUID,
  task TEXT NOT NULL,
  model TEXT NOT NULL,
  prompt_sha256 CHAR(64) NOT NULL,
  input_tokens INTEGER,
  output_tokens INTEGER,
  reasoning_tokens INTEGER,
  latency_ms INTEGER NOT NULL,
  cost_micros BIGINT NOT NULL DEFAULT 0,
  outcome TEXT NOT NULL CHECK (outcome IN ('OK','EMPTY_CONTENT','INVALID_JSON','HTTP_ERROR','TIMEOUT')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_llm_calls_run ON app.llm_calls(run_id);

CREATE TABLE app.benchmark_runs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  dataset_version TEXT NOT NULL,
  config JSONB NOT NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  finished_at TIMESTAMPTZ
);

CREATE TABLE app.benchmark_results (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  benchmark_run_id UUID NOT NULL REFERENCES app.benchmark_runs(id) ON DELETE CASCADE,
  case_code TEXT NOT NULL,
  metrics JSONB NOT NULL,
  passed BOOLEAN NOT NULL,
  cost_micros BIGINT NOT NULL,
  latency_ms INTEGER NOT NULL
);

CREATE TABLE app.audit_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  case_id UUID,
  actor TEXT NOT NULL,
  event_type TEXT NOT NULL,
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =========================================================================
-- Public keep_alive table for Supabase Free Tier keep-alive ping
-- Only keep_alive is in the public schema and publicly readable
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.keep_alive (
  id INTEGER PRIMARY KEY DEFAULT 1,
  pinged_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO public.keep_alive (id, pinged_at)
VALUES (1, NOW())
ON CONFLICT (id) DO UPDATE SET pinged_at = EXCLUDED.pinged_at;

ALTER TABLE public.keep_alive ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS keep_alive_read_all ON public.keep_alive;
CREATE POLICY keep_alive_read_all ON public.keep_alive
  FOR SELECT TO PUBLIC
  USING (true);

-- =========================================================================
-- Enable Row Level Security (RLS) with NO policies on every application table.
-- With zero policies, non-owner roles (including Supabase anon/authenticated)
-- are completely prevented from reading or writing via PostgREST Data API.
-- =========================================================================
ALTER TABLE app.cases ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.document_pages ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.extracted_fields ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.analysis_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.ledger_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.reconciliation_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.conflicts ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.eligibility_assessments ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.rate_references ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.interest_estimates ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.proposed_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.llm_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.benchmark_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.benchmark_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.audit_events ENABLE ROW LEVEL SECURITY;
