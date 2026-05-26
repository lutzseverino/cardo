CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  keycloak_subject text NOT NULL,
  keycloak_resource_id text,
  authorization_sync_status text NOT NULL DEFAULT 'PENDING',
  authorization_synced_at timestamp with time zone,
  authorization_sync_error text,
  email text NOT NULL,
  email_verified boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'ACTIVE',
  name text,
  avatar_url text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT uk_users_email UNIQUE (email),
  CONSTRAINT uk_users_keycloak_subject UNIQUE (keycloak_subject)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_keycloak_resource_id
  ON users (keycloak_resource_id)
  WHERE keycloak_resource_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS authorization_sync_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  unique_key text NOT NULL,
  operation text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING',
  resource_server_client_id text NOT NULL,
  resource_name text NOT NULL,
  resource_type text,
  owner_subject text,
  requester_subject text,
  actions text NOT NULL,
  attempt_count integer NOT NULL DEFAULT 0,
  last_attempted_at timestamp with time zone,
  synced_at timestamp with time zone,
  last_error text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT uk_authorization_sync_item_key UNIQUE (unique_key)
);
