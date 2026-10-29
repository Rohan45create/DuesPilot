-- Migration V4: Blob Storage & Benchmark Database Schema
-- Storing all PDFs and documents in content-addressed database tables in app schema with RLS.
-- Zero disk storage, zero repository PDFs.

SET search_path = app, public;

-- 1. Content-addressed file blob table (identical files stored once via SHA-256)
CREATE TABLE IF NOT EXISTS app.file_blobs (
  sha256 CHAR(64) PRIMARY KEY,
  bytes BYTEA NOT NULL,
  mime_type TEXT NOT NULL,
  size_bytes BIGINT NOT NULL CHECK (size_bytes <= 5242880),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Reference blob_sha256 in app.documents
ALTER TABLE app.documents
  ADD COLUMN IF NOT EXISTS blob_sha256 CHAR(64) REFERENCES app.file_blobs(sha256) ON DELETE SET NULL;

ALTER TABLE app.documents
  ALTER COLUMN storage_path DROP NOT NULL,
  ALTER COLUMN storage_path SET DEFAULT 'db_blob';

CREATE INDEX IF NOT EXISTS idx_documents_blob ON app.documents(blob_sha256);

-- 3. Benchmark datasets metadata table
CREATE TABLE IF NOT EXISTS app.benchmark_datasets (
  name TEXT NOT NULL,
  version TEXT NOT NULL,
  seed BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (name, version)
);

-- 4. Benchmark cases table
CREATE TABLE IF NOT EXISTS app.benchmark_cases (
  dataset TEXT NOT NULL,
  case_code TEXT NOT NULL,
  ground_truth JSONB NOT NULL,
  metadata JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (dataset, case_code)
);

-- 5. Benchmark case files table referencing content-addressed blobs
CREATE TABLE IF NOT EXISTS app.benchmark_case_files (
  dataset TEXT NOT NULL,
  case_code TEXT NOT NULL,
  file_name TEXT NOT NULL,
  doc_type TEXT NOT NULL,
  blob_sha256 CHAR(64) NOT NULL REFERENCES app.file_blobs(sha256) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (dataset, case_code, file_name)
);

CREATE INDEX IF NOT EXISTS idx_bench_files_blob ON app.benchmark_case_files(blob_sha256);

-- 6. Demo seed templates table (idempotent seed definitions)
CREATE TABLE IF NOT EXISTS app.seed_templates (
  template_code TEXT PRIMARY KEY,
  case_data JSONB NOT NULL,
  recorded_analysis JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. Demo seed template documents referencing blobs
CREATE TABLE IF NOT EXISTS app.seed_template_documents (
  template_code TEXT NOT NULL REFERENCES app.seed_templates(template_code) ON DELETE CASCADE,
  file_name TEXT NOT NULL,
  doc_type TEXT NOT NULL,
  doc_date DATE,
  doc_text TEXT,
  blob_sha256 CHAR(64) NOT NULL REFERENCES app.file_blobs(sha256) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (template_code, file_name)
);

CREATE INDEX IF NOT EXISTS idx_seed_docs_blob ON app.seed_template_documents(blob_sha256);

-- 8. Enable Row Level Security (RLS) with zero public policies
ALTER TABLE app.file_blobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.benchmark_datasets ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.benchmark_cases ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.benchmark_case_files ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.seed_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.seed_template_documents ENABLE ROW LEVEL SECURITY;
