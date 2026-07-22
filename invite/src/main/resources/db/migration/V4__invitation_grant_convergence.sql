ALTER TABLE invitations
  ADD COLUMN grant_receipt_id uuid,
  ADD CONSTRAINT uk_invitations_grant_receipt_id UNIQUE (grant_receipt_id);

ALTER TABLE invitation_completion_operations
  DROP CONSTRAINT chk_invitation_completion_status,
  ADD CONSTRAINT chk_invitation_completion_status
    CHECK (status IN ('REQUESTED', 'AWAITING_IDENTITY', 'COMPLETED', 'FAILED', 'REVOKED'));
