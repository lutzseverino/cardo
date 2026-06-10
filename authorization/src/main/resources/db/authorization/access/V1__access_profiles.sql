CREATE EXTENSION IF NOT EXISTS pgcrypto;

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
