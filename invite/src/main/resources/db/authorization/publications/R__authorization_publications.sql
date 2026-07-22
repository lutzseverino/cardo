CREATE SCHEMA IF NOT EXISTS ${authorizationSchema};

CREATE TABLE IF NOT EXISTS ${authorizationSchema}.event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
  ON ${authorizationSchema}.event_publication USING hash(serialized_event);

CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
  ON ${authorizationSchema}.event_publication (completion_date);

CREATE INDEX IF NOT EXISTS event_publication_by_listener_completion_date_idx
  ON ${authorizationSchema}.event_publication (listener_id, completion_date, publication_date);

CREATE TABLE IF NOT EXISTS ${authorizationSchema}.grant_receipt
(
  id            UUID NOT NULL,
  status        TEXT NOT NULL,
  failure_code  TEXT,
  attempt_count INT NOT NULL DEFAULT 0,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT chk_grant_receipt_status CHECK (status IN ('PENDING', 'APPLIED', 'FAILED')),
  CONSTRAINT chk_grant_receipt_attempt_count CHECK (attempt_count >= 0),
  CONSTRAINT chk_grant_receipt_failure CHECK (
    (status = 'FAILED' AND failure_code IS NOT NULL)
      OR (status <> 'FAILED' AND failure_code IS NULL)
  )
);

CREATE INDEX IF NOT EXISTS grant_receipt_by_status_updated_at_idx
  ON ${authorizationSchema}.grant_receipt (status, updated_at);
