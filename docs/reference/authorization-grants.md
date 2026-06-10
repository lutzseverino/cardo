# Authorization Grants

This document is authoritative for staging authorization grants from application
flows.

## Contract

- A flow that assigns authorization must create one `GrantPlan` and call
  `Grants.stage(plan)` inside its application transaction.
- `Grants.stage(...)` requires an active transaction. The plan publication is
  persisted with that transaction by Spring Modulith.
- A plan describes provider-neutral intent: resources to provision, resource
  actions to grant, and client authorities to grant.
- The authorization module applies plans asynchronously through
  `AuthorizationAdminClient` and retries failed publications.
- Grant application is idempotent. Existing resources and resource-action
  grants are detected before writes; the provider adapter must preserve that
  property for authority assignment.
- Each service stores event publications in its own configured PostgreSQL
  schema, even when services share a database.

## Ownership

- The application module that owns a flow owns its planner. Use a small class
  named for the module concept, such as `ClinicGrants` or `InvitationGrants`.
- A planner method is named for the flow action, such as `activation(...)` or
  `acceptance(...)`, and returns a complete `GrantPlan`.
- Permission constants remain in types such as `ClinicPermissions`. They name
  the authorization vocabulary; grant planners decide when and to whom that
  vocabulary is assigned.
- Flow services persist domain state and stage the plan. They do not call
  Keycloak or another authorization provider directly.

## Configuration

Import `GrantConfiguration` in each application that stages grants and configure:

- `spring.modulith.events.jdbc.schema`: service-local publication schema
- Flyway placeholder `authorizationSchema`: the same schema name
- `odonta.authorization.grants.retry-delay`: failed-publication retry delay;
  defaults to `PT1M`

The shared `db/authorization` Flyway location creates the publication schema and
Spring Modulith JDBC tables.
