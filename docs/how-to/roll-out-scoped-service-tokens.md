# Roll Out Scoped Service Tokens

This runbook stages Cardo issue #25 under the parent security migration in
issue #20. Completing or deploying the Cardo code does **not** close #20.
Polity caller configuration, live Keycloak changes, maximum-token-lifetime
drainage, canary traffic, and deployed smoke evidence remain required.

Cardo does not provide a compatibility flag for permissive audiences. Roll out
the provider, callers, user-token issuance, and strict resource servers in the
following order.

## Steps

### Preparation

1. Back up the Keycloak realm and record the current client scopes, audience
   mappers, service-account role grants, access-token maximum lifetime, and all
   Cardo and Polity replica versions. Do not treat repository tests as evidence
   of the live realm state.
2. Inventory every caller of Identity, Billing, and Invite, including Polity,
   scheduled jobs, canaries, and administrative paths. Confirm which Billing
   endpoints receive Cardo user tokens and which receive service tokens.
3. Prepare dashboards or logs that distinguish issuer, missing/extra audience,
   missing expiration, role, Invite allowlist, discovery, and token-acquisition
   failures without recording bearer credentials.

### Provider And Caller Migration

1. In Keycloak, create or verify optional client scopes named `identity`,
   `billing`, and `cardo-invite`. Give each scope one audience mapper that emits
   only its identically named resource audience. Do not attach these scopes as
   defaults and do not add all three audiences to one token. These are the
   repository defaults. If Invite's deployed `cardo.invite.keycloak.client-id`
   is customized, its scope mapper must instead emit that configured client ID
   as the sole audience; use the same client ID for Invite's role namespace.
2. Attach the target scopes as optional scopes to each existing confidential
   caller that needs them. In particular, attach the required scopes to the
   existing Polity client instead of creating a replacement client merely for
   this migration. Preserve each service account's least-privilege client-role
   assignments and Invite allowlist entry.
3. Stage Identity's existing audience mapper from default to optional without
   an outage: add and test the optional `identity` scope first while the old
   default mapper remains, then deploy callers that explicitly request
   `scope=identity`. Do not remove the default mapper yet.
4. Configure every deployed adapter explicitly:

   ```yaml
   cardo:
     identity:
       client:
         service-token-scope: identity
     billing:
       client:
         service-token-scope: billing
     invite:
       client:
         service-token-scope: cardo-invite
   ```

   Configure only the clients present in that application. Missing or blank
   values prevent that client auto-configuration from starting.
5. Deploy caller-only changes while Billing and Invite still run their previous
   audience policy. Roll Cardo callers and Polity separately, and verify from
   decoded test credentials that each request contains one audience, the
   configured issuer, an expiration, the unchanged required roles, and no
   unrelated audience. Never paste live bearer tokens into deployment evidence.
6. Restart or replace every old caller replica so no process can continue using
   its unscoped cache. Record the timestamp when the last old replica stopped,
   then wait at least the Keycloak maximum access-token lifetime from that
   timestamp. The provider's refresh skew is not a substitute for this drain.
   Canary Identity calls with the explicitly scoped token before removing the
   old default Identity audience mapper.
7. Remove the old default Identity audience mapper only after the caller rollout
   and maximum-lifetime drain. Keep the new `identity` mapper optional. Repeat
   the decoded-credential and Identity smoke checks.

### Billing User Tokens And Strict Consumers

1. Before strict Billing enforcement, configure every browser or backend flow
   that issues a Cardo user credential for Billing to request the `billing`
   resource audience. Ensure the issued user token has exactly that one
   audience, the configured issuer, an expiration, the Cardo user claim, and
   the existing user permissions.
2. Rotate browser sessions or wait the maximum lifetime of previously issued
   Billing user tokens after the last old issuer configuration is removed.
   Exercise current-user, checkout, portal, and service-account entitlement
   traffic independently; a successful service-token smoke does not validate
   user-token traffic.
3. Canary the Cardo release with strict Billing validation (`aud` exactly
   `billing`) and strict Invite validation (`aud` exactly the deployed Invite
   client ID, `cardo-invite` by default). Both
   also require issuer and expiration. Keep health, API documentation, Billing
   status/webhook, and Invite status/token-inspection routes public, and verify
   Billing's user/service authorization split plus Invite's service role and
   caller allowlist. Issuer discovery remains lazy: application startup and
   public health checks can succeed before the first bearer token is decoded.
   Include an authenticated protected request in every smoke test. If discovery
   or JWK access is unavailable, that request must fail closed.
4. Expand the strict Billing and Invite release only after canaries show no
   missing, extra, or stale audiences. Verify issuer discovery and JWK refresh
   from each deployment network. Capture the deployed smoke results and
   maximum-lifetime timestamps in #20.

### Rollback

1. Stop expansion and preserve the failed-token diagnostics. Do not weaken
   audience checking through a runtime flag.
2. Roll Billing or Invite back to the previous application release before
   rolling callers back. This restores the previous resource-server behavior
   while scoped tokens remain valid credentials.
3. Keep the optional scopes and audience mappers while any scoped caller is
   running. If callers must also roll back, replace every scoped replica, wait
   the maximum token lifetime again, and restore the previous Identity default
   mapper before removing its optional replacement.
4. If Billing user-token issuance is rolled back, keep Billing on the previous
   resource-server release until all exact-audience user credentials have been
   replaced or expired. Re-run user and service smoke tests independently.
5. Restore the recorded realm/client configuration only after no deployed
   caller requests the removed scopes. Document the rollback evidence and leave
   #20 open until the complete migration is successfully repeated.

Repository validation proves the Cardo implementation and failure modes only.
It does not prove live Keycloak provisioning, Polity configuration, deployment
ordering, token drainage, or production traffic.

## Verification

Record all of the following in #20 before closing the parent migration:

- the live optional-scope and audience-mapper configuration;
- the last-old-replica and maximum-token-lifetime drain timestamps;
- exact issuer, audience, and expiration evidence from non-secret decoded test
  credentials for each user and service path;
- Identity, Billing, and Invite canary results, including public routes and
  unchanged role/allowlist checks;
- issuer-discovery and JWK-refresh results from the deployment network;
- the deployed Cardo and Polity versions and rollback checkpoint.
