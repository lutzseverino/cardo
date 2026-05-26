CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS billing_entitlements (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  subject_id uuid NOT NULL,
  product text NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  tenant_limit integer,
  seat_limit integer,
  trial_ends_at timestamp with time zone,
  current_period_ends_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT uq_billing_entitlements_subject_product UNIQUE (subject_id, product)
);

CREATE TABLE IF NOT EXISTS billing_customers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  subject_id uuid NOT NULL,
  provider text NOT NULL,
  provider_customer_id text NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT uq_billing_customers_subject_provider UNIQUE (subject_id, provider),
  CONSTRAINT uq_billing_customers_provider_customer UNIQUE (provider, provider_customer_id)
);

CREATE TABLE IF NOT EXISTS billing_provider_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  provider text NOT NULL,
  provider_event_id text NOT NULL,
  event_type text NOT NULL,
  processed_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT uq_billing_provider_events_provider_event UNIQUE (provider, provider_event_id)
);
