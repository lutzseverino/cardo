# Cardo Application Boundary Layout

This local reference applies the roles in [Application Boundaries](application-boundaries.md) to
Cardo's modules. It narrows package placement without changing the authoritative role definitions.

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
