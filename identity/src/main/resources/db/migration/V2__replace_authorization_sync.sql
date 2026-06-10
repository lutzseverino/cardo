DROP TABLE IF EXISTS authorization_sync_items;

ALTER TABLE users
  DROP COLUMN IF EXISTS keycloak_resource_id,
  DROP COLUMN IF EXISTS authorization_sync_status,
  DROP COLUMN IF EXISTS authorization_synced_at,
  DROP COLUMN IF EXISTS authorization_sync_error;
