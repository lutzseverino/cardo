# Migrate Keycloak Runtime Credentials

Use this guide to move an existing Identity deployment from startup-owned
Keycloak mapper and role creation to deployment-owned provisioning and a
least-privilege runtime credential.

## Steps

1. Inventory the exact runtime client, credential-setup client, configured
   user-ID mapper targets, and fixed `identity` resource server. Compare them
   with the [provider contract](../reference/keycloak-provider-contract.md).
2. In deployment code, provision the canonical `cardo_user_id` mapper once on
   every configured target and provision the three fixed Identity client roles.
   Make duplicate cleanup explicit and reviewable. Do not use application
   startup as the desired-state owner.
3. If the deployed realm is already drifted, temporarily deploy Identity with
   `IDENTITY_KEYCLOAK_LEGACY_STARTUP_MUTATION_ENABLED=true` and the existing
   privileged credential. Confirm the broad-authority warning and a successful
   post-repair validation. This mode does not create missing clients or grants.
4. Apply the same canonical definitions through deployment provisioning, then
   set `IDENTITY_KEYCLOAK_LEGACY_STARTUP_MUTATION_ENABLED=false` or remove the
   variable. Restart and require read-only validation to succeed.
5. Rotate the runtime secret to a client whose service account can perform the
   documented provider reads, user lifecycle, existing client-role
   assignment/removal, and Identity UMA operations, but cannot write clients,
   mapper definitions, client-role definitions, or service-account grants.
6. Revoke the old privileged secret and authority only after all Identity
   replicas use the constrained credential.

The compatibility flag is a one-migration escape hatch, not a supported steady
state. Remove it from deployment configuration immediately after the successful
read-only restart. Cardo should delete the seam in the next minor release after
all maintained deployments have migrated.

## Verification

- Identity starts with the flag absent or `false`.
- Startup logs contain no legacy mutation warning.
- Exact client, mapper, role, user-directory, and UMA reads succeed.
- A mapper-definition or client-role-definition write made with the runtime
  credential receives `403`.
- User provisioning, enabled-state changes, existing role assignment/removal,
  and UMA resource/grant operations still work.
- Removing or corrupting a definition in a disposable environment produces one
  redacted startup failure that identifies all discovered drift.

## Rollback

If constrained startup fails, restore the previous credential and temporarily
set the legacy flag to `true`. Capture the redacted drift list, repair the
deployment-owned definitions or missing read grants, and repeat the migration.
Do not grant client-management authority permanently and do not leave the flag
enabled after validation succeeds.
