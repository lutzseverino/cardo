# Authorization

`authorization` provides reusable authorization primitives for platform and product
services.

The module currently owns:

- authenticated user and permission helpers for Spring Security
- Keycloak authorization and token clients
- authorization resource and grant value objects
- access profile entities, repositories, and services
- durable grant and revocation staging and provider application

Application flows define assignment or revocation intent in flow-owned planners
such as `ClinicGrantPlanner` and `ClinicRevocationPlanner`. They stage the
resulting `GrantPlan` or `RevocationPlan` inside the flow transaction through
`Grants` or `Revocations`. The authorization module records each plan with
Spring Modulith and applies it asynchronously through the configured
`AuthorizationAdminClient`.

See [Authorization Grant Lifecycle](../docs/reference/authorization-grants.md)
for the contract and ownership rules.

This is an embedded Java library boundary. It should only gain an OpenAPI contract
and generated HTTP clients if it is extracted into a service that owns HTTP
endpoints.
