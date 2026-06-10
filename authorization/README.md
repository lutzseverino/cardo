# Authorization

`authorization` provides reusable authorization primitives for applications.

The module provides:

- authenticated user and permission helpers for Spring Security
- an authorization provider port and Keycloak adapter
- authorization resource and grant value objects
- access profile entities, repositories, and services
- durable grant and revocation staging and provider application

Application flows define assignment or revocation intent in flow-owned planners
such as `WorkspaceGrantPlanner` and `WorkspaceRevocationPlanner`. They stage the
resulting `GrantPlan` or `RevocationPlan` inside the flow transaction through
`Grants` or `Revocations`. The authorization module records each plan with
Spring Modulith and applies it asynchronously through the configured
`AuthorizationAdminClient`.

See [Authorization Grant Lifecycle](../docs/reference/authorization-grants.md)
for the contract and ownership rules.

See [Embed Authorization](../docs/how-to/embed-authorization.md) for schema,
Flyway, entity scanning, repository, and bean configuration.
