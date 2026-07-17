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

## Product Integration

Authorization owns reusable mechanics: resource naming, permission evaluation,
access profiles, grant and revocation staging, and provider adapters.

Products own their resource catalogs, actions, planners, seed data, tenant
semantics, and the business flows that create or revoke access. A product embeds
this module when it needs those mechanics, but it should not ask Authorization to
own product policy.

There is no HTTP client module because Authorization does not own an HTTP
runtime. Keep using the embedded Java APIs unless Authorization is deliberately
extracted into a service.

## Documentation

Start with the [Authorization documentation index](docs/README.md).
Cross-project architecture and conventions remain in the
[Cardo documentation](../docs/README.md).
