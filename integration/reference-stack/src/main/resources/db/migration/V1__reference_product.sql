create table reference_invitation (
  id uuid primary key,
  request_id uuid not null unique,
  email varchar(320) not null,
  invited_by uuid not null,
  remote_invitation_id uuid unique,
  invited_user_id uuid,
  acceptance_intent_at timestamp with time zone,
  accepted_subject varchar(255),
  accepted_at timestamp with time zone,
  receipt_id uuid unique,
  created_at timestamp with time zone not null default current_timestamp,
  updated_at timestamp with time zone not null default current_timestamp
);

create table reference_membership (
  tenant_id uuid not null,
  subject varchar(255) not null,
  created_at timestamp with time zone not null default current_timestamp,
  primary key (tenant_id, subject)
);

create table reference_command (
  id uuid primary key,
  command_type varchar(16) not null check (command_type in ('CREATE', 'ACCEPT')),
  invitation_id uuid not null references reference_invitation(id),
  accepted_subject varchar(255),
  accepted_at timestamp with time zone,
  completed_at timestamp with time zone,
  created_at timestamp with time zone not null default current_timestamp,
  unique (command_type, invitation_id)
);
