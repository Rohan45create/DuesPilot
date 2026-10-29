-- Migration V5: Explicit Demo Data Flag & Workspace Unique Code
-- Enables registered users to seed and purge synthetic demo data safely within their workspace.

SET search_path = app, public;

-- 1. Add is_demo_data flag to cases
ALTER TABLE app.cases
  ADD COLUMN IF NOT EXISTS is_demo_data BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_cases_workspace_demo ON app.cases (workspace_id, is_demo_data);

-- 2. Add is_demo_data flag to documents
ALTER TABLE app.documents
  ADD COLUMN IF NOT EXISTS is_demo_data BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_documents_workspace_demo ON app.documents (workspace_id, is_demo_data);

-- 3. Add is_demo_data flag to contacts
ALTER TABLE app.contacts
  ADD COLUMN IF NOT EXISTS is_demo_data BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_contacts_workspace_demo ON app.contacts (workspace_id, is_demo_data);

-- 4. Scope case code uniqueness per workspace rather than globally
ALTER TABLE app.cases DROP CONSTRAINT IF EXISTS cases_code_key;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'cases_workspace_code_key'
  ) THEN
    ALTER TABLE app.cases ADD CONSTRAINT cases_workspace_code_key UNIQUE (workspace_id, code);
  END IF;
END $$;
