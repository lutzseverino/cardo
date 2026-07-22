ALTER TABLE identity_operations
  ADD COLUMN lease_token uuid,
  ADD CONSTRAINT chk_identity_operations_terminal_lease
    CHECK (status IN ('REQUESTED', 'AWAITING_USER') OR lease_token IS NULL);

CREATE INDEX idx_identity_operations_terminal
  ON identity_operations (status, updated_at)
  WHERE status = 'FAILED';

CREATE INDEX idx_identity_provider_mutations_terminal
  ON identity_provider_mutations (status, updated_at)
  WHERE status = 'FAILED';
