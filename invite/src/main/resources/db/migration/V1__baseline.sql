CREATE EXTENSION IF NOT EXISTS pgcrypto;

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
