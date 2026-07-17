CREATE TABLE identity_operations (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL,
  provider_subject text NOT NULL,
  operation_type text NOT NULL,
  status text NOT NULL,
  attempt_count integer NOT NULL DEFAULT 0,
  next_attempt_at timestamp with time zone NOT NULL,
  last_error text,
  not_after timestamp with time zone,
  expires_at timestamp with time zone,
  completed_at timestamp with time zone,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT chk_identity_operations_type
    CHECK (operation_type IN ('CREDENTIAL_SETUP', 'PROVISIONAL_DELETION')),
  CONSTRAINT chk_identity_operations_status
    CHECK (status IN ('REQUESTED', 'AWAITING_USER', 'COMPLETED', 'FAILED')),
  CONSTRAINT chk_identity_operations_attempt_count CHECK (attempt_count >= 0),
  CONSTRAINT chk_identity_operations_awaiting_expiry
    CHECK (status <> 'AWAITING_USER' OR expires_at IS NOT NULL),
  CONSTRAINT chk_identity_operations_setup_deadline
    CHECK (operation_type <> 'CREDENTIAL_SETUP' OR not_after IS NOT NULL)
);

CREATE INDEX idx_identity_operations_ready
  ON identity_operations (status, next_attempt_at, created_at);

CREATE UNIQUE INDEX uk_identity_operations_active_user_type
  ON identity_operations (user_id, operation_type)
  WHERE status IN ('REQUESTED', 'AWAITING_USER');
