CREATE TABLE invitation_completion_operations (
  id uuid PRIMARY KEY,
  invitation_id uuid NOT NULL REFERENCES invitations (id),
  invited_user_id uuid NOT NULL,
  product text NOT NULL,
  status text NOT NULL,
  attempt_count integer NOT NULL DEFAULT 0,
  next_attempt_at timestamp with time zone NOT NULL,
  last_error text,
  expires_at timestamp with time zone NOT NULL,
  action_expires_at timestamp with time zone,
  completed_at timestamp with time zone,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT uk_invitation_completion_invitation UNIQUE (invitation_id),
  CONSTRAINT chk_invitation_completion_status
    CHECK (status IN ('REQUESTED', 'AWAITING_IDENTITY', 'COMPLETED', 'FAILED')),
  CONSTRAINT chk_invitation_completion_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_invitation_completion_ready
  ON invitation_completion_operations (status, next_attempt_at, created_at);
