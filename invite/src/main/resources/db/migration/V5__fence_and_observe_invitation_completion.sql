ALTER TABLE invitation_completion_operations
  ADD COLUMN lease_token uuid,
  ADD CONSTRAINT chk_invitation_completion_terminal_lease
    CHECK (status IN ('REQUESTED', 'AWAITING_IDENTITY') OR lease_token IS NULL);

CREATE INDEX idx_invitation_completion_terminal
  ON invitation_completion_operations (status, updated_at)
  WHERE status = 'FAILED';
