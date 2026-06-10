# Embed Authorization

Embed the `authorization` module when a service needs shared authorization
mechanics while retaining ownership of its product-specific authorization model.

## Steps

1. Add dependencies on `authorization`, `spring-boot-starter-flyway`, and the
   PostgreSQL Flyway database module.
2. Give the service its own Flyway history table and configure
   `baseline-on-migrate: true` with baseline version `0`. This allows multiple
   services to migrate the shared non-empty database without skipping `V1`.
3. Import `AuthorizationSchemaConfiguration` when the service persists access
   profiles. It migrates `db/authorization/access` first using the dedicated
   `flyway_schema_history_authorization` history table.
4. Include `AccessProfile` in `@EntityScan` and `AccessProfileRepository` in
   `@EnableJpaRepositories`. Provide an `AccessProfileService` bean from the
   shared repositories.
5. Keep product-owned access-profile templates and grants in service migrations.
   Do not redefine the authorization-owned tables or indexes.
6. When staging grant or revocation plans, import
   `AuthorizationPlanConfiguration`, add
   `classpath:db/authorization/publications` to `spring.flyway.locations`, and
   configure `authorizationSchema` to match
   `spring.modulith.events.jdbc.schema`.

The authorization module owns access-profile entities, repositories, services,
tables, constraints, indexes, staging infrastructure, and provider adapters. The
embedding service owns resource types, actions, planners, event handlers, seed
data, and service-local credentials.

## Verification

Start the service against an empty PostgreSQL database. Verify that the
authorization history table is created, access-profile tables exist before the
service baseline runs, service-owned seed data is present, and JPA schema
validation succeeds.
