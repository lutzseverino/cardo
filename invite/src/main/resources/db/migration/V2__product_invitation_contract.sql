ALTER TABLE invitations
  ADD COLUMN version bigint NOT NULL DEFAULT 0,
  ADD COLUMN request_id uuid,
  ADD COLUMN product text,
  ADD COLUMN access_profile text,
  ADD COLUMN accept_url_base text,
  ADD COLUMN expires_at timestamp with time zone,
  ADD COLUMN revoked_at timestamp with time zone;

-- The former contract did not retain product ownership, stable profile names,
-- exact grants, or an acceptance URL. Preserve those rows for audit, but make
-- pending legacy invitations unusable rather than inventing authorization data.
UPDATE invitations
SET request_id = id,
    product = 'legacy',
    access_profile = 'legacy:' || access_profile_id::text,
    accept_url_base = 'https://invalid.invalid/invitations',
    expires_at = created_at + interval '72 hours',
    status = CASE WHEN status = 'PENDING' THEN 'REVOKED' ELSE status END,
    revoked_at = CASE WHEN status = 'PENDING' THEN now() ELSE revoked_at END;

ALTER TABLE invitations
  ALTER COLUMN request_id SET NOT NULL,
  ALTER COLUMN product SET NOT NULL,
  ALTER COLUMN access_profile SET NOT NULL,
  ALTER COLUMN accept_url_base SET NOT NULL,
  ALTER COLUMN expires_at SET NOT NULL,
  DROP COLUMN access_profile_id,
  ADD CONSTRAINT uk_invitations_product_request_id UNIQUE (product, request_id);

CREATE TABLE invitation_grants (
  invitation_id uuid NOT NULL REFERENCES invitations (id) ON DELETE CASCADE,
  resource_type text NOT NULL,
  action text NOT NULL,
  CONSTRAINT uk_invitation_grants UNIQUE (invitation_id, resource_type, action)
);

CREATE INDEX idx_invitations_product_tenant
  ON invitations (product, tenant_id, created_at DESC);

CREATE INDEX idx_invitations_invited_user
  ON invitations (product, invited_user_id, status, created_at DESC);
