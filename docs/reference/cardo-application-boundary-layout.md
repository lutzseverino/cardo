# Cardo Application Boundary Layout

This local reference applies the roles in [Application Boundaries](application-boundaries.md) to
Cardo's modules. It narrows package placement without changing the authoritative role definitions.

## Adopted Profile

Cardo uses Spring Boot, Spring Data JPA, MapStruct, generated OpenAPI contracts, and Spring Modulith
durable events. Within that profile:

- HTTP services expose generated transport contracts only through controllers and transport
  mappers;
- stable application owners use services, while effectful cross-owner use cases use workflows;
- application contracts use Cardo-owned inputs, results, domain values, or ordinary context values;
- provider protocols remain behind application-owned ports and provider-specific adapters;
- embedded modules expose intentional Java contracts without acquiring an HTTP-service shape;
- generated code is contained behind stable client or controller boundaries.

Framework extension points keep the responsibility implied by the framework contract. Credential
resolvers extract credentials, mappers translate owned data shapes, converters convert values, and
repository methods access persistence. Network calls, transactions, provider orchestration, and
cross-owner decisions belong in an explicit application or authentication boundary rather than a
nominally mechanical hook.

## HTTP Service Modules

Identity, Invite, and Billing use the following package map beneath each module root:

| Package | Canonical contents |
| --- | --- |
| `controller` | Generated-API implementations, authentication extraction, and HTTP response construction. |
| `service` | Stable semantic-owner services only. |
| `workflow` | Public cross-owner use cases and package-private workflow collaborators. |
| `model` | Application inputs, results, domain values, and persistence entities. |
| `repository` | Spring Data repositories and persistence projections. |
| `mapper` | Owner-named transport and application mappers plus pure conversion helpers. |
| `provider` | Application-owned integration ports and the DTOs owned by those ports. |
| `integration` | Provider-specific adapters; a provider subpackage such as `integration.keycloak` or `integration.stripe` is used when useful. |
| `reader` | Precise principal, token, or external-data readers; not generic entity readers. |
| `patch` | Transport-presence adapters for partial updates. |
| `authorization` | Product-owned policies and grant planners. |
| `config` | Framework wiring and inbound lifecycle adapters, not application orchestration. |
| `exception` | Transport exception handling. |

Not every module needs every package. A new role goes into its canonical package instead of creating
an equivalent parallel convention.

## Embedded And Client Modules

Authorization is an embedded application module. Its access-profile entity, projections,
repositories, results, and service are deliberately colocated under `authorization.access` because
that package is the independently embedded owner. Its public service contract still uses results,
never projections or entities.

`Grants` and `Revocations` are deliberate domain-vocabulary entrypoints for durable authorization
plan staging. Their transaction and delivery contract is defined in
[Authorization Grants](authorization-grants.md); their names are not a general alternative to
services or workflows.

The `client` modules publish stable client contracts and client-owned DTOs. The `client-http`
modules are outbound integration adapters over generated transport clients; they are not
application services and do not establish package conventions for HTTP service modules.

Status controllers are transport-only health endpoints. They may construct their simple generated
status response directly because no application behavior or persistence access is involved.

## Canonical Module Owners

- `common-api` owns shared API error values, exceptions, and outbound error translation.
- `common` is the compatibility aggregate for the remaining shared persistence, validation, web,
  and value mechanics. It does not own product behavior or become a holding area for possible reuse.
- `openapi-support` owns reusable generated-contract mechanics. It does not own any service's API
  vocabulary or application model.
- `authorization-keycloak-client` owns client-credential and requesting-party-token HTTP mechanics.
- `authorization-security` owns shared Spring Security principal, JWT, authority, and permission
  mechanics.
- `authorization` is the compatibility aggregate and owns embedded authorization resources, access
  profiles, durable grant and revocation application, and provider administration. Calling
  applications own the product meaning of their resources, actions, and grant plans.
- `identity` owns platform users, authentication, browser-session lifecycle, Identity runtime
  reconciliation, and Identity-provider integration. It does not own product membership or product
  authorization policy.
- `invite` owns invitation tokens, delivery, provisional-identity coordination, expiry, and captured
  grant mechanics. The consuming product owns the domain transition performed when an invitation is
  accepted.
- `billing` owns billing customers, checkout and portal sessions, provider event processing, and
  platform entitlements. Products own the domain meaning of limits and paid capabilities.
- `identity-product-auth` owns the reusable product-boundary authentication mechanics documented in
  [Browser Sessions And Product Tokens](browser-sessions.md). Products own their routes and
  authorization decisions.

These owners are semantic boundaries, not a requirement to expose one service class per bullet.

## Platform And Dependency Boundaries

- HTTP services consume another Cardo service through its stable `client` contract and a
  `client-http` adapter. They do not consume another service's generated server models, repositories,
  entities, or provider implementation.
- Outbound client adapters use `common-api` and `authorization-keycloak-client` rather than the full
  aggregates. Product-boundary authentication additionally uses `authorization-security`. Maven
  enforcement keeps persistence, migrations, JDBC, and Modulith outside those consumer graphs.
- Embedded Authorization may be consumed directly through its intentional public Java contracts.
  Its persistence and Keycloak implementations remain internal mechanics unless a type is explicitly
  established as an integration contract.
- A Cardo service may decide platform facts in its owned vocabulary. Products translate those facts
  into product decisions; Cardo does not acquire product-domain ownership through an integration.
- Cross-module orchestration belongs to the owner of the initiating lifecycle transition. Client and
  adapter modules do not decide when an operation should happen.
- Auto-configuration owns only the mechanics promised by its module contract. Catch-all filter
  chains, public routes, bean replacement rules, and ordering constraints must be explicit because
  they affect host-application composition.
- A bearer-token resource server validates the expected issuer, a service-specific audience, and a
  required expiry. Introducing a missing audience is a deployment and caller migration, not merely
  a decoder change.
- Outbound HTTP calls have bounded connection and response times. Service credentials are reused
  until shortly before their server-reported expiry instead of being reacquired for every application
  request, unless a documented security requirement demands a different policy.

## Public And Durable Contracts

Public visibility is reserved for an intentional consumer, framework, or testable embedded contract.
Implementation classes remain package-private when Spring and generated-code requirements allow it.
Adding a public interface solely to make an implementation look layered is not simplification.

Configuration keys, bean names documented as seams, generated HTTP operations, client contracts,
database schemas, and durable event payloads are compatibility surfaces. When one changes, preserve
the old path, migrate existing state, or document and stage a breaking transition. A new durable
event envelope does not make incomplete publications using the previous payload disappear.

External effects that cannot participate in the local transaction make their ordering and recovery
visible. Durable listeners remain idempotent, bound retries where terminal failure is meaningful,
and distinguish provider failure from receipt or publication persistence failure.

Remote creates use a provider-supported idempotency key when one exists. A transaction callback or
best-effort compensating delete may reduce ordinary failures, but it is not recovery from process
death or an ambiguous provider response; workflows that cannot tolerate that gap persist enough
state to reconcile it.

## Simplification And Enforcement

Apply this decision order during review:

1. Delete a boundary that only forwards, renames, or anticipates hypothetical reuse.
2. Narrow visibility, configuration, provider scope, or ownership when the full surface is not part
   of the contract.
3. Prefer a well-matched framework facility when it preserves Cardo's invariants and makes the call
   graph clearer.
4. Retain a custom boundary when it protects a demonstrated invariant; name and test that invariant.
5. Introduce a shared abstraction only after independent owners demonstrate the same stable need.

Architecture tests enforce durable dependency and contract rules, not incidental class inventories.
Keep enforcement local when module roles differ. Share test machinery only when it removes repeated
policy rather than merely repeated test syntax.

Before making a module resemble another, first ask whether the modules have the same role. HTTP
services, embedded libraries, client contracts, outbound adapters, and generated-code support are
expected to have different shapes.

## Local Exceptions

An exception identifies the owner and invariant that the normal shape would obscure. Keep it narrow,
document it here or in the relevant contract reference, and add regression coverage when it is a
durable rule. Existing exceptions include Authorization's embedded owner packages, domain-vocabulary
`Grants` and `Revocations` entrypoints, and transport-only status controllers.
