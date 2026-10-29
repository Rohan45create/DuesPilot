-- Migration V3: Contacts, Notification Outbox, and Channel Configuration (R-72 to R-78)

-- 1. Extend app.contacts to be scoped by case, buyer, and per-channel opt-in timestamps
ALTER TABLE app.contacts
  ADD COLUMN IF NOT EXISTS case_id UUID REFERENCES app.cases(id) ON DELETE CASCADE,
  ADD COLUMN IF NOT EXISTS buyer_name TEXT,
  ADD COLUMN IF NOT EXISTS email_opt_in BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS email_opt_in_source TEXT DEFAULT 'INBOUND_INVOICE',
  ADD COLUMN IF NOT EXISTS email_opt_in_at TIMESTAMPTZ DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS whatsapp_opt_in BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS whatsapp_opt_in_source TEXT DEFAULT 'COMMERCIAL_ENGAGEMENT',
  ADD COLUMN IF NOT EXISTS whatsapp_opt_in_at TIMESTAMPTZ DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS unsubscribed_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

UPDATE app.contacts SET buyer_name = name WHERE buyer_name IS NULL;
CREATE INDEX IF NOT EXISTS idx_contacts_case ON app.contacts(case_id);

-- 2. Notification Outbox Table
CREATE TABLE IF NOT EXISTS app.notification_outbox (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES app.workspaces(id) ON DELETE CASCADE,
  case_id UUID NOT NULL REFERENCES app.cases(id) ON DELETE CASCADE,
  action_id UUID REFERENCES app.proposed_actions(id) ON DELETE SET NULL,
  channel TEXT NOT NULL CHECK (channel IN ('EMAIL', 'WHATSAPP_LINK', 'WHATSAPP_API')),
  recipient TEXT NOT NULL,
  template_key TEXT NOT NULL,
  rendered_body TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'QUEUED'
    CHECK (status IN ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'BOUNCED', 'MANUAL_PENDING', 'MANUAL_SENT')),
  provider_message_id TEXT,
  error TEXT,
  scheduled_for TIMESTAMPTZ,
  sent_at TIMESTAMPTZ,
  idempotency_key TEXT UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_case ON app.notification_outbox(case_id);
CREATE INDEX IF NOT EXISTS idx_notification_outbox_workspace ON app.notification_outbox(workspace_id);
CREATE INDEX IF NOT EXISTS idx_notification_outbox_status ON app.notification_outbox(status);
CREATE INDEX IF NOT EXISTS idx_notification_outbox_channel_sent ON app.notification_outbox(case_id, channel, sent_at DESC);

-- Enable RLS
ALTER TABLE app.notification_outbox ENABLE ROW LEVEL SECURITY;
