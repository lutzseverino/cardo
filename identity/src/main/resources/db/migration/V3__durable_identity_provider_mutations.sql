CREATE TABLE identity_provider_mutations (
  id uuid PRIMARY KEY,
  mutation_type text NOT NULL,
  status text NOT NULL,
  user_id uuid,
  provider_subject text,
  normalized_email text,
  display_name text,
  correlation_marker text,
  desired_enabled boolean,
  desired_version integer NOT NULL DEFAULT 0,
  attempt_count integer NOT NULL DEFAULT 0,
  next_attempt_at timestamp with time zone NOT NULL,
  lease_token uuid,
  lease_until timestamp with time zone,
  last_error text,
  terminal_reason text,
  completed_at timestamp with time zone,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT chk_identity_provider_mutations_type CHECK (
    mutation_type IN ('PROVISION_PASSWORD_USER', 'BIND_USER_ID', 'SET_IDENTITY_ENABLED')
  ),
  CONSTRAINT chk_identity_provider_mutations_status CHECK (
    status IN ('REQUESTED', 'COMPLETED', 'FAILED')
  ),
  CONSTRAINT chk_identity_provider_mutations_terminal_reason CHECK (
    terminal_reason IS NULL OR terminal_reason IN (
      'CREDENTIAL_RESUBMISSION_REQUIRED',
      'PROVIDER_REJECTED',
      'RETRY_EXHAUSTED',
      'LOCAL_STATE_CONFLICT'
    )
  ),
  CONSTRAINT chk_identity_provider_mutations_attempt_count CHECK (attempt_count >= 0),
  CONSTRAINT chk_identity_provider_mutations_desired_version CHECK (desired_version >= 0),
  CONSTRAINT chk_identity_provider_mutations_lease CHECK (
    (lease_token IS NULL AND lease_until IS NULL)
      OR (lease_token IS NOT NULL AND lease_until IS NOT NULL)
  ),
  CONSTRAINT chk_identity_provider_mutations_failure CHECK (
    status <> 'FAILED' OR terminal_reason IS NOT NULL
  ),
  CONSTRAINT chk_identity_provider_mutations_provision CHECK (
    mutation_type <> 'PROVISION_PASSWORD_USER'
      OR (
        normalized_email IS NOT NULL
        AND display_name IS NOT NULL
        AND correlation_marker IS NOT NULL
        AND desired_enabled IS NULL
      )
  ),
  CONSTRAINT chk_identity_provider_mutations_bind CHECK (
    mutation_type <> 'BIND_USER_ID'
      OR (user_id IS NOT NULL AND provider_subject IS NOT NULL AND desired_enabled IS NULL)
  ),
  CONSTRAINT chk_identity_provider_mutations_enabled CHECK (
    mutation_type <> 'SET_IDENTITY_ENABLED'
      OR (
        user_id IS NOT NULL
        AND provider_subject IS NOT NULL
        AND desired_enabled IS NOT NULL
        AND desired_version > 0
      )
  )
);

CREATE UNIQUE INDEX uk_identity_provider_mutations_correlation
  ON identity_provider_mutations (correlation_marker)
  WHERE correlation_marker IS NOT NULL;

CREATE UNIQUE INDEX uk_identity_provider_mutations_active_provision_email
  ON identity_provider_mutations (normalized_email, mutation_type)
  WHERE mutation_type = 'PROVISION_PASSWORD_USER' AND status = 'REQUESTED';

CREATE UNIQUE INDEX uk_identity_provider_mutations_active_user_type
  ON identity_provider_mutations (user_id, mutation_type)
  WHERE mutation_type IN ('BIND_USER_ID', 'SET_IDENTITY_ENABLED') AND status = 'REQUESTED';

CREATE INDEX idx_identity_provider_mutations_ready
  ON identity_provider_mutations (next_attempt_at, created_at)
  WHERE status = 'REQUESTED';

INSERT INTO identity_provider_mutations (
  id,
  mutation_type,
  status,
  user_id,
  provider_subject,
  next_attempt_at
)
SELECT
  gen_random_uuid(),
  'BIND_USER_ID',
  'REQUESTED',
  id,
  keycloak_subject,
  now()
FROM users;

INSERT INTO identity_provider_mutations (
  id,
  mutation_type,
  status,
  user_id,
  provider_subject,
  desired_enabled,
  desired_version,
  next_attempt_at
)
SELECT
  gen_random_uuid(),
  'SET_IDENTITY_ENABLED',
  'REQUESTED',
  id,
  keycloak_subject,
  status <> 'DISABLED',
  1,
  now()
FROM users;
