# Authorization

`authorization` provides reusable authorization primitives for platform and product
services.

The module currently owns:

- authenticated user and permission helpers for Spring Security
- Keycloak authorization and token clients
- authorization resource and grant value objects
- access profile entities, repositories, and services
- durable grant staging and provider application

Application flows define their grant intent in a flow-owned planner such as
`ClinicGrants`, then stage the resulting `GrantPlan` through `Grants.stage(...)`
inside the flow transaction. The authorization module records the plan with
Spring Modulith and applies it asynchronously through the configured
`AuthorizationAdminClient`.

See [Authorization Grants](../docs/reference/authorization-grants.md) for the
contract and ownership rules.

This is an embedded Java library boundary. It should only gain an OpenAPI contract
and generated HTTP clients if it is extracted into a service that owns HTTP
endpoints.
