DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM identity_provider_mutations
    WHERE mutation_type IN ('PROVISION_PASSWORD_USER', 'PROVISION_PROVISIONAL_USER')
      AND status = 'REQUESTED'
    GROUP BY normalized_email
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION
      'Active Identity provisioning ownership conflicts require operator reconciliation before V6';
  END IF;
END
$$;

DROP INDEX uk_identity_provider_mutations_active_provision_email;

CREATE UNIQUE INDEX uk_identity_provider_mutations_active_provision_email
  ON identity_provider_mutations (normalized_email)
  WHERE mutation_type IN ('PROVISION_PASSWORD_USER', 'PROVISION_PROVISIONAL_USER')
    AND status = 'REQUESTED';
