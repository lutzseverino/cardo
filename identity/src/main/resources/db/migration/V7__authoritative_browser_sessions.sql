CREATE TABLE browser_sessions (
  provider_session_id text PRIMARY KEY,
  provider_subject text NOT NULL,
  refresh_credential_hash varchar(64) NOT NULL,
  status text NOT NULL,
  started_at timestamp with time zone NOT NULL,
  idle_expires_at timestamp with time zone NOT NULL,
  absolute_expires_at timestamp with time zone NOT NULL,
  refresh_lease_token uuid,
  refresh_lease_until timestamp with time zone,
  revoked_at timestamp with time zone,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT uk_browser_sessions_refresh_credential UNIQUE (refresh_credential_hash),
  CONSTRAINT chk_browser_sessions_status CHECK (status IN ('ACTIVE', 'REVOKED')),
  CONSTRAINT chk_browser_sessions_deadlines CHECK (
    started_at < idle_expires_at
    AND started_at < absolute_expires_at
    AND idle_expires_at <= absolute_expires_at
  ),
  CONSTRAINT chk_browser_sessions_refresh_lease CHECK (
    (refresh_lease_token IS NULL AND refresh_lease_until IS NULL)
      OR (refresh_lease_token IS NOT NULL AND refresh_lease_until IS NOT NULL)
  ),
  CONSTRAINT chk_browser_sessions_revocation CHECK (
    (status = 'ACTIVE' AND revoked_at IS NULL)
      OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
  )
);

CREATE INDEX idx_browser_sessions_absolute_expiry
  ON browser_sessions (absolute_expires_at);

CREATE INDEX idx_browser_sessions_revoked_at
  ON browser_sessions (revoked_at)
  WHERE revoked_at IS NOT NULL;
