# Authorization Grant Lifecycle

This document is authoritative for staging authorization assignments and
revocations from application flows.

## Contract

- A flow that assigns authorization must create one `GrantPlan` and call
  `Grants.stage(plan)` inside its application transaction.
- A flow that revokes authorization must create one `RevocationPlan` and call
  `Revocations.stage(plan)` inside its application transaction.
- Flows construct plans through `GrantPlan.builder()`. The builder merges repeated
  compatible definitions and rejects conflicting resource definitions.
- Flows construct revocations through `RevocationPlan.builder()`. The builder
  merges repeated revocations for the same resource or client authority set.
- Both staging APIs require an active transaction. Spring Modulith persists the
  plan publication with that transaction.
- A plan describes provider-neutral intent: resources to provision, resource
  actions and client authorities to assign or revoke.
- The authorization module applies plans asynchronously through
  `AuthorizationAdminClient` and retries failed publications.
- Grant application is idempotent. Existing resource capabilities are widened
  without removing capabilities, and existing resource-action grants are
  detected before writes. The provider adapter must preserve that property for
  authority assignment.
- Revocation is idempotent. Processing queries current resource-action grants
  before deletion and ignores client authorities that are already absent, so a
  retry only applies remaining work.
- Each application stores event publications in its configured PostgreSQL
  schema.

## Ownership

- The application module that owns a flow owns its planners. Use a small class
  named for the owned concept and operation, such as `WorkspaceGrantPlanner`
  or `WorkspaceRevocationPlanner`.
- A planner method is named for the flow action, such as `creation(...)` or
  `membership(...)`, and returns one complete plan.
- `AuthorizationResourceType` owns canonical resource-type parsing and the
  construction of resources targeted at a domain identifier.
- Permission constants remain in types such as `WorkspacePermissions`. They name
  the authorization vocabulary; planners decide when and to whom that
  vocabulary is assigned or revoked.
- Flow services persist domain state and stage the plan. They do not call
  an authorization provider directly.
- Effective-grant readers expose current authorization state. They do not own
  mutations or provider calls.

## Configuration

Import `AuthorizationPlanConfiguration` in each application that stages plans
and configure:

- `spring.modulith.events.jdbc.schema`: service-local publication schema
- Flyway placeholder `authorizationSchema`: the same schema name
- `odonta.authorization.plans.retry-delay`: failed-publication retry delay;
  defaults to `PT1M`

The shared `db/authorization` Flyway location creates the publication schema and
Spring Modulith JDBC tables.
