ALTER TABLE identity_provider_mutations
  DROP CONSTRAINT chk_identity_provider_mutations_type,
  ADD CONSTRAINT chk_identity_provider_mutations_type CHECK (
    mutation_type IN (
      'PROVISION_PASSWORD_USER',
      'PROVISION_PROVISIONAL_USER',
      'BIND_USER_ID',
      'SET_IDENTITY_ENABLED'
    )
  );

ALTER TABLE identity_provider_mutations
  DROP CONSTRAINT chk_identity_provider_mutations_provision,
  ADD CONSTRAINT chk_identity_provider_mutations_provision CHECK (
    mutation_type NOT IN ('PROVISION_PASSWORD_USER', 'PROVISION_PROVISIONAL_USER')
      OR (
        normalized_email IS NOT NULL
        AND correlation_marker IS NOT NULL
        AND desired_enabled IS NULL
        AND (
          (mutation_type = 'PROVISION_PASSWORD_USER' AND display_name IS NOT NULL)
          OR (mutation_type = 'PROVISION_PROVISIONAL_USER' AND display_name IS NULL)
        )
      )
  );

DROP INDEX uk_identity_provider_mutations_active_provision_email;

CREATE UNIQUE INDEX uk_identity_provider_mutations_active_provision_email
  ON identity_provider_mutations (normalized_email, mutation_type)
  WHERE mutation_type IN ('PROVISION_PASSWORD_USER', 'PROVISION_PROVISIONAL_USER')
    AND status = 'REQUESTED';
