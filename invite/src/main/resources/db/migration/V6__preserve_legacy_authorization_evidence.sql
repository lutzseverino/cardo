ALTER TABLE invitations
  RENAME COLUMN grant_receipt_id TO legacy_grant_receipt_id;

ALTER TABLE invitations
  RENAME COLUMN access_profile TO legacy_access_profile;

ALTER TABLE invitations
  ALTER COLUMN legacy_access_profile DROP NOT NULL;

ALTER TABLE invitations
  RENAME COLUMN invited_authorization_subject TO legacy_invited_authorization_subject;

ALTER TABLE invitations
  ALTER COLUMN legacy_invited_authorization_subject DROP NOT NULL;

ALTER TABLE invitation_grants
  RENAME TO legacy_invitation_grants;
