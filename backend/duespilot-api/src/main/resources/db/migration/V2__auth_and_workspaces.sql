-- Migration V2: Supabase Auth, Users, Workspaces, Multi-Tenancy Scoping

CREATE TABLE IF NOT EXISTS app.users (
  id TEXT PRIMARY KEY,                             -- Supabase auth.users UUID (token sub)
  email TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS app.workspaces (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,                              -- Business name / Trade name
  supplier_size_class TEXT NOT NULL DEFAULT 'MICRO'
    CHECK (supplier_size_class IN ('MICRO','SMALL','MEDIUM','ENTERPRISE','UNKNOWN')),
  udyam_registration_date DATE,                    -- Optional Udyam registration date
  owner_phone TEXT,
  owner_whatsapp TEXT,
  is_demo BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS app.workspace_members (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES app.workspaces(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
  role TEXT NOT NULL DEFAULT 'OWNER' CHECK (role IN ('OWNER','MEMBER','VIEWER')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (workspace_id, user_id)
);

-- Seed shared DEMO workspace for anonymous and replay users
INSERT INTO app.workspaces (id, name, supplier_size_class, is_demo)
VALUES ('00000000-0000-0000-0000-000000000001'::uuid, 'DuesPilot Demo MSME Workspace', 'MICRO', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Scope cases, documents, analysis data, and communications by workspace_id
ALTER TABLE app.cases
  ADD COLUMN IF NOT EXISTS workspace_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid
  REFERENCES app.workspaces(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_cases_workspace ON app.cases(workspace_id);

ALTER TABLE app.documents
  ADD COLUMN IF NOT EXISTS workspace_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid
  REFERENCES app.workspaces(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_documents_workspace ON app.documents(workspace_id);

ALTER TABLE app.analysis_runs
  ADD COLUMN IF NOT EXISTS workspace_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid
  REFERENCES app.workspaces(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_runs_workspace ON app.analysis_runs(workspace_id);

-- Communications & notifications tables scoped by workspace_id
CREATE TABLE IF NOT EXISTS app.contacts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES app.workspaces(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  email TEXT,
  phone TEXT,
  whatsapp_number TEXT,
  opt_in_whatsapp BOOLEAN NOT NULL DEFAULT TRUE,
  opt_in_email BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_contacts_workspace ON app.contacts(workspace_id);

CREATE TABLE IF NOT EXISTS app.outbox_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES app.workspaces(id) ON DELETE CASCADE,
  case_id UUID REFERENCES app.cases(id) ON DELETE CASCADE,
  channel TEXT NOT NULL CHECK (channel IN ('EMAIL','WHATSAPP')),
  recipient TEXT NOT NULL,
  subject TEXT,
  body TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
  sent_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_outbox_workspace ON app.outbox_messages(workspace_id);

-- Enable RLS with zero public policies (Security first, no public Data API exposure)
ALTER TABLE app.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.workspaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.workspace_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.outbox_messages ENABLE ROW LEVEL SECURITY;
