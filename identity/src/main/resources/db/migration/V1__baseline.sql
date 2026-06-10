CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  keycloak_subject text NOT NULL,
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
