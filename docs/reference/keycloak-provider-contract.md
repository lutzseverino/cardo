# Keycloak Provider Contract

This reference is authoritative for the Keycloak objects Identity requires at
runtime and for the ownership boundary between deployment provisioning and
Cardo runtime behavior.

## Ownership

The deployment repository owns the one-time realm bootstrap and an idempotent
contract materializer for clients, client secrets, service accounts,
provider-definition roles, protocol mappers, and grants to service accounts.
Cardo validates those definitions at startup but does not normally create or
repair them. Product repositories own their product resource catalogs, scopes,
policies, and grants.

The clients have distinct responsibilities:

- `cardo-identity` is the confidential runtime OAuth client and service account.
  Authorization Services is disabled. Its distinct credential performs user
  lifecycle and the constrained realm-admin operations below.
- `identity` is both the fixed Identity resource-server audience/static-role
  client and the owner of the Identity UMA catalog. Authorization Services and
  its service account are enabled; its distinct credential obtains a PAT whose
  client roles are exactly `identity:uma_protection`. It owns `profile:read`,
  `profile:write`, and `user:provision` in addition to its provider-defined PAT
  role, but the service account is not granted those application roles.
- `cardo-identity`, `identity`, and `billing` are the default
  `cardo_user_id` mapper targets. Deployment configuration may add other
  distinct targets.

The runtime credential is not a realm bootstrap or contract-materializer
credential. It may read the provider definitions listed below and retain the
operational permissions needed for user lifecycle, existing client-role
assignment/removal, and UMA resource/grant operations. It must not be able to
create clients, change client definitions, write protocol mappers, or create
client roles.

### Endpoint and Authority Matrix

| Surface | Runtime operation | Required runtime authority | Definition owner |
| --- | --- | --- | --- |
| `/realms/{realm}/protocol/openid-connect/token` | obtain separate client-credentials tokens for `cardo-identity` runtime administration and `identity` catalog protection | the corresponding distinct client credential | deployment materializer |
| `/admin/realms/{realm}/clients` and client mapper/role reads | exact client, `cardo_user_id` mapper, and fixed `identity` role validation | `realm-management:view-clients` | deployment materializer |
| `/admin/realms/{realm}/users` and user-scoped endpoints | user search, create, read, update, enable/disable, and delete | `realm-management:manage-users` | Cardo runtime |
| `/admin/realms/{realm}/users/{userId}/role-mappings/clients/{clientUuid}` | assign/remove already-defined `identity` client roles | `realm-management:manage-users` | Cardo runtime |
| `/realms/{realm}/authz/protection/resource_set` and `/permission/ticket` | Identity-owned UMA resources and grants | the automatic `identity:uma_protection` PAT role | Identity runtime |
| realm, client, mapper, client-role, and service-account grant definitions | bootstrap and converge desired provider definitions | deployment-only provisioning credential; absent from the runtime credential | deployment bootstrap/materializer |

The smallest directly assigned realm-management set proven by the disposable
Keycloak exercise is `manage-users` plus `view-clients`. Keycloak expands the
`view-clients` composite in the access token with `query-clients`; that effective
role is not a third direct grant. Do not directly add `query-users`,
`view-users`, or `query-clients`.

The `identity` PAT has no realm-management roles; Admin API reads for clients,
users, mappers, and roles must return `403`. Conversely, catalog protection
calls never use the `cardo-identity` realm-admin token.

`manage-users` is a coarse built-in Keycloak role. Cardo never uses it to grant
authority to a service-account user, but the built-in role can technically
alter user and service-account role mappings. A deployment that requires hard
provider-side denial of that operation must replace the coarse role with
Keycloak fine-grained admin permissions and separately prove every endpoint in
this matrix. The default contract proves least authority for Cardo's required
operations; it does not claim that `manage-users` creates a technical
service-account-grant boundary.

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

The runtime credential must successfully perform exact client lookup, mapper
lookup, Identity role lookup, and a bounded user-directory read. The separate
Identity catalog credential must have `azp=identity`, exactly the
`identity:uma_protection` client role, no realm-management roles, and a
successful UMA protection resource read. A missing role is drift: role
assignment paths never create its definition.

## Startup Behavior

`cardo.identity.keycloak.legacy-startup-mutation-enabled` defaults to `false`.
In that mode startup is read-only and fails before serving traffic when the
contract is incomplete, ambiguous, incompatible, or unreadable. The failure
aggregates independent drift while excluding credentials, tokens, provider
response bodies, and configured endpoints.

Every validation failure directs operators to deployment-owned provisioning.
Only when all discovered drift is repairable mapper or fixed-role drift does
the failure also mention the legacy flag.

When the temporary flag is `true`, Identity logs a broad-authority warning,
repairs only the `cardo_user_id` mappers and the three Identity client roles,
then executes the same validator. Repair is convergent and tolerates concurrent
create conflicts. It never creates a client, enables a service account, or
adds a service-account grant. See the
[migration guide](../how-to/migrate-keycloak-runtime-credentials.md).

## Verification

The test suite performs a one-time realm bootstrap and then calls the
deployment-style materializer twice against a disposable Keycloak from the
digest-pinned image declared in `DisposableKeycloakProvisioner`. The snapshots
prove unique clients, one canonical mapper per target, the exact fixed roles,
and unchanged direct grants. Token claims prove the two direct
realm-management grants (plus Keycloak's derived `query-clients`) and the
automatic `identity:uma_protection` role on the separate catalog service account.
The exercise also proves read-only validation,
runtime role assignment and UMA behavior, definition-write denial, drift
detection, privileged repair, and a second convergent repair. The portable
artifact smoke uses a deterministic protocol stub with the same valid read
surface; mapper and role-definition writes return `403`, and unexpected routes
return `404`.
