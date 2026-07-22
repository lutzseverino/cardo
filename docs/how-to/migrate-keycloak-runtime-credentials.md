# Migrate Keycloak Runtime Credentials

Use this guide to move an existing Identity deployment from startup-owned
Keycloak mapper and role creation to deployment-owned provisioning and a
least-privilege runtime credential.

## Steps

1. Inventory the exact runtime client, credential-setup client, configured
   user-ID mapper targets, and fixed `identity` resource server. Compare them
   with the [provider contract](../reference/keycloak-provider-contract.md).
2. Keep one-time realm bootstrap separate from an idempotent deployment
   materializer. In the materializer, provision exactly one canonical
   `cardo_user_id` mapper on every configured target and the three fixed
   Identity client roles. Make duplicate cleanup explicit and reviewable. Run
   the materializer twice in deployment tests and require the second snapshot
   of clients, mappers, roles, and grants to be unchanged.
3. If the deployed realm is already drifted, temporarily deploy Identity with
   `IDENTITY_KEYCLOAK_LEGACY_STARTUP_MUTATION_ENABLED=true` and the existing
   privileged credential. Confirm the broad-authority warning and a successful
   post-repair validation. This mode does not create missing clients or grants.
4. Apply the same canonical definitions through deployment provisioning, then
   set `IDENTITY_KEYCLOAK_LEGACY_STARTUP_MUTATION_ENABLED=false` or remove the
   variable. Restart and require read-only validation to succeed.
5. Provision a distinct confidential `cardo-identity` runtime client and
   service account. Enable Authorization Services so Keycloak automatically
   assigns `cardo-identity:uma_protection`. Directly assign only
   `realm-management:manage-users` and `realm-management:view-clients`; the
   latter appears in tokens with its Keycloak-derived `query-clients`
   composite. Keep the `identity` resource-server/static-role client distinct,
   and put the mapper on `cardo-identity`, `identity`, and `billing` (plus any
   other configured targets).
6. Before removing the old authority, stop every Identity replica that still
   uses the privileged credential. A rolling secret change is insufficient:
   an already-issued access token remains usable until revoked or expired, and
   each Identity process caches its provider token.
7. Remove the old service-account grants and rotate/revoke the old secret.
   Revoke the old client's sessions or issued tokens when the provider and
   deployment support that operation. If revocation is not supported, wait at
   least the maximum access-token lifetime that was configured when the old
   tokens were issued. Secret rotation alone does not invalidate those tokens.
8. Start every Identity replica with the constrained client and the legacy
   flag absent or `false`. This restart clears Cardo's process-local provider
   token cache. Require read-only validation and the runtime checks below to
   succeed before restoring traffic.

The compatibility flag is a one-migration escape hatch, not a supported steady
state. Remove it from deployment configuration immediately after the successful
read-only restart. Cardo should delete the seam in the next minor release after
all maintained deployments have migrated.

## Verification

- Identity starts with the flag absent or `false`.
- Startup logs contain no legacy mutation warning.
- Exact client, mapper, role, user-directory, and UMA reads succeed.
- The service-account token contains effective realm-management roles
  `manage-users`, `view-clients`, and the composite child `query-clients`, plus
  `cardo-identity:uma_protection`; direct grant inspection contains only
  `manage-users`, `view-clients`, and the automatic PAT role.
- A mapper-definition or client-role-definition write made with the runtime
  credential receives `403`.
- User provisioning, enabled-state changes, existing role assignment/removal,
  and UMA resource/grant operations still work.
- Removing or corrupting a definition in a disposable environment produces one
  redacted startup failure that identifies all discovered drift.

## Rollback

If constrained startup fails, keep traffic stopped while you capture the
redacted drift list and repair the deployment-owned definitions or missing
runtime grant. If rollback is necessary, restore the previous credential and
authority, restart all replicas so no process retains the constrained token,
and temporarily set the legacy flag to `true`. Repeat the same revocation or
maximum-token-lifetime drain when moving forward again. Do not grant
client-management authority permanently and do not leave the flag enabled
after validation succeeds.
