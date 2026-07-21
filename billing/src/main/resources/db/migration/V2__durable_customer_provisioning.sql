CREATE TABLE billing_customer_provisioning_operations (
  id uuid PRIMARY KEY,
  subject_id uuid NOT NULL,
  provider text NOT NULL,
  status text NOT NULL,
  attempt_count integer NOT NULL DEFAULT 0,
  next_attempt_at timestamp with time zone NOT NULL,
  last_error text,
  remote_attempted_at timestamp with time zone,
  lease_token uuid,
  provider_customer_id text,
  completed_at timestamp with time zone,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT ck_billing_customer_provisioning_status
    CHECK (status IN ('REQUESTED', 'COMPLETED', 'FAILED')),
  CONSTRAINT ck_billing_customer_provisioning_completed
    CHECK (status <> 'COMPLETED' OR (provider_customer_id IS NOT NULL AND completed_at IS NOT NULL)),
  CONSTRAINT ck_billing_customer_provisioning_terminal_lease
    CHECK (status = 'REQUESTED' OR lease_token IS NULL)
);

CREATE UNIQUE INDEX uq_billing_customer_provisioning_active
  ON billing_customer_provisioning_operations (subject_id, provider)
  WHERE status = 'REQUESTED';

CREATE INDEX ix_billing_customer_provisioning_ready
  ON billing_customer_provisioning_operations (next_attempt_at, created_at)
  WHERE status = 'REQUESTED';
