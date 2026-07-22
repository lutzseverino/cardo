# Keycloak Provider Contract

This reference is authoritative for the Keycloak objects Identity requires at
runtime and for the ownership boundary between deployment provisioning and
Cardo runtime behavior.

## Ownership

The deployment repository owns realm bootstrap, clients, client secrets,
service accounts, provider-definition roles, protocol mappers, and grants to
service accounts. Cardo validates those definitions at startup but does not
normally create or repair them. Product repositories own their product resource
catalogs, scopes, policies, and grants. Cardo's fixed `identity` resource server
is the only resource-server catalog covered here.

The runtime credential is not a realm bootstrap credential. It may read the
provider definitions listed below and retain the operational permissions needed
for user lifecycle, client-role assignment/removal, and UMA resource/grant
operations. It must not be able to create clients, change client definitions,
write protocol mappers, create client roles, or grant new authority to service
accounts.

## Required Definitions

Identity requires exactly one exact client match for every distinct identifier
in this set:

- `cardo.identity.keycloak.client-id`
- `cardo.identity.keycloak.credential-setup-client-id`
- every `cardo.identity.keycloak.user-id-claim-client-ids` entry
- the fixed Identity authorization resource server, `identity`

Every configured user-ID claim target has exactly one protocol mapper named
`cardo_user_id` with these semantics:

| Field | Required value |
| --- | --- |
| protocol | `openid-connect` |
| mapper type | `oidc-usermodel-attribute-mapper` |
| user attribute | `cardo_user_id` |
| claim name | `cardo_user_id` |
| JSON type | `String` |
| access-token claim | `true` |
| ID-token claim | `false` |
| user-info claim | `false` |
| multivalued | `false` |
| consent required | `false` |

Provider-added mapper defaults are permitted when these required semantics stay
unchanged. Duplicate named mappers are invalid.

The `identity` client has exactly these Cardo-owned client roles:

- `profile:read`
- `profile:write`
- `user:provision`

The startup credential must successfully perform exact client lookup, mapper
lookup, Identity role lookup, a bounded user-directory read, and an UMA
protection resource read. A missing role is drift: role assignment paths never
create its definition.

## Startup Behavior

`cardo.identity.keycloak.legacy-startup-mutation-enabled` defaults to `false`.
In that mode startup is read-only and fails before serving traffic when the
contract is incomplete, ambiguous, incompatible, or unreadable. The failure
aggregates independent drift while excluding credentials, tokens, provider
response bodies, and configured endpoints.

When the temporary flag is `true`, Identity logs a broad-authority warning,
repairs only the `cardo_user_id` mappers and the three Identity client roles,
then executes the same validator. Repair is convergent and tolerates concurrent
create conflicts. It never creates a client, enables a service account, or
adds a service-account grant. See the
[migration guide](../how-to/migrate-keycloak-runtime-credentials.md).

## Verification

The test suite provisions a disposable Keycloak from the digest-pinned image
declared in `DisposableKeycloakProvisioner`. It proves read-only validation,
runtime role assignment and UMA behavior, definition-write denial, drift
detection, privileged repair, and a second convergent repair. The portable
artifact smoke uses a deterministic protocol stub with the same valid read
surface; mapper and role-definition writes return `403`, and unexpected routes
return `404`.
