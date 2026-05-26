CREATE EXTENSION IF NOT EXISTS pgcrypto;

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

CREATE TABLE IF NOT EXISTS authorization_access_profiles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product text NOT NULL,
  tenant_id uuid,
  name text NOT NULL,
  description text,
  template boolean NOT NULL DEFAULT false,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_authorization_access_profiles_template_name
  ON authorization_access_profiles (product, name)
  WHERE tenant_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_authorization_access_profiles_tenant_name
  ON authorization_access_profiles (product, tenant_id, name)
  WHERE tenant_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS authorization_access_profile_grants (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id uuid NOT NULL REFERENCES authorization_access_profiles (id) ON DELETE CASCADE,
  resource_type text NOT NULL,
  resource_id uuid,
  action text NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_authorization_access_profile_grants
  ON authorization_access_profile_grants (profile_id, resource_type, resource_id, action)
  WHERE resource_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_authorization_access_profile_wildcard_grants
  ON authorization_access_profile_grants (profile_id, resource_type, action)
  WHERE resource_id IS NULL;

CREATE TABLE IF NOT EXISTS invitations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  tenant_resource_type text NOT NULL,
  access_profile_id uuid NOT NULL,
  invited_email text NOT NULL,
  invited_user_id uuid NOT NULL,
  invited_authorization_subject text NOT NULL,
  invited_by uuid NOT NULL,
  token text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING',
  accepted_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT uk_invitations_token UNIQUE (token)
);
