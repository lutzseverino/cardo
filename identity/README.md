# Identity

Identity owns Cardo users, authentication, sessions, and the authenticated
principal exposed to products. Identity also owns global user status: disabling
an Identity user disables that subject in the identity provider and ends
provider sessions, without changing product-local memberships or grants.

## Product Integration

Products use `identity-client` as the stable Java contract for user lookup and
provisional-user flows. `identity-client-http` provides the HTTP implementation
and Spring Boot auto-configuration for service-to-service calls to Identity.

Products that accept logged-in Cardo users use `identity-product-auth`. It
auto-configures the shared Spring Security pieces for product services:

- JWT authority conversion
- permission evaluation
- authenticated-user reading
- method security
- OAuth2 resource-server setup
- Identity-session validation and product-token exchange
- cookie-selected CSRF enforcement

Products still own product access. Identity must not silently grant product-domain
permissions as a side effect of user creation
or login. Products issue product grants from their own flows by staging
`GrantPlan` instances with product-owned client authorities and resource
actions.

The accepted production [browser-session contract](../docs/reference/browser-sessions.md) requires
one HTTPS origin for the product frontend, product API, and reverse-proxied Identity session routes.
Identity implements the access/refresh cookie lifecycle, CSRF bootstrap and session-mutation
enforcement, refresh rotation, and refresh-token logout. `identity-product-auth` implements strict
Identity-session validation and product-token exchange, while Authorization exposes durable grant
convergence. Consumers still provision their provider, product, frontend, and deployment portions
of the contract.

## Browser Session Configuration

Local HTTP development uses the defaults:

```yaml
cardo:
  identity:
    session:
      mode: local
      access-cookie-name: cardo.session
      refresh-cookie-name: cardo.refresh
      csrf-cookie-name: cardo.csrf
      refresh-cookie-path: ${cardo.api.base-path}/identity/sessions/current
      secure: false
```

Production requires explicit secure policy:

```yaml
cardo:
  identity:
    session:
      mode: production
      access-cookie-name: __Host-cardo.session
      refresh-cookie-name: __Secure-cardo.refresh
      csrf-cookie-name: __Host-cardo.csrf
      refresh-cookie-path: /api/v1/identity/sessions/current
      secure: true
```

Set the refresh path to the browser-visible current-session path when a gateway rewrites the public
prefix. Identity rejects production startup with insecure names or attributes. Cookie `Max-Age`
comes from the corresponding credential expiry rather than configuration. The Identity RPT audience
is the fixed resource catalog name `identity`; `cardo-identity` remains the separate confidential
OAuth client. Browsers bootstrap the readable CSRF cookie with
`GET /api/v1/identity/sessions/csrf` and echo it unchanged in `X-CSRF-TOKEN` for login, refresh, and
logout. Identity alone creates and expires this cookie.

Invited-user credential setup and provisional deletion are durable operations.
Credential setup delegates password/profile entry to Keycloak's action flow;
Cardo never receives the password. Identity persists the operation before the
provider call, performs provider work outside database transactions, and
reconciles with leases and bounded retry. The operation response exposes its
status and action expiry. Provisional deletion follows the same model and is
queryable after the request, including after the local user row is gone.
An `INVITED` user is not operational: password login, refresh, and current-session
establishment fail until credential setup activates the user. Failed password or
refresh establishment revokes the provider session that was just issued or rotated;
the existing `DISABLED` rejection remains distinct.

Ordinary password-user provisioning and global enabled-state changes use a
separate internal `identity_provider_mutations` lifecycle. Cardo persists only
normalized profile intent and a random Keycloak correlation marker before
provisioning; passwords remain request-local and are never stored. An ambiguous
create is recovered by that marker. If recovery cannot prove that Keycloak
created the identity, the mutation terminates with
`CREDENTIAL_RESUBMISSION_REQUIRED` after bounded attempts and a caller must
resubmit credentials through the unchanged create-user API. A definite password
rejection permits the same resubmission immediately. A create conflict is kept
recoverable while the worker waits for a potentially concurrent same-marker
identity to become visible.
Terminal rows retain `terminal_reason` and a bounded `last_error` until a safe
credential resubmission reclaims a `CREDENTIAL_RESUBMISSION_REQUIRED` row and
starts a new attempt on the same correlation marker. Other terminal reasons
require operator repair. Worker logs carry the mutation id, exact type, target
version, and retry/terminal outcome for operator correlation.

Provisional-user creation uses that same durable lifecycle under
`PROVISION_PROVISIONAL_USER`. Identity commits the normalized email and a random
Keycloak correlation marker before attempting provider creation. A synchronous
lost response and the background worker both recover only when exactly one
Keycloak user carries that exact marker; an email match is never ownership
evidence. Local invited-user creation, mutation completion, durable
`BIND_USER_ID` staging, and creation-grant staging then commit together. Provider
creation and user-id binding never run inside that transaction, and rollback
does not delete the marker-owned provider identity because reconciliation may
still need it.

`PROVIDER_REJECTED` identifies a definite provider rejection, including an
unattributed pre-existing Keycloak user that conflicts by email but has no exact
marker. `LOCAL_STATE_CONFLICT` identifies a local user with the same email and a
different provider subject. `RETRY_EXHAUSTED` means either the marker lookup or
provider call remained unavailable, or the local completion transaction kept
failing, after bounded reconciliation. A completion failure may leave
`provider_subject` null even though the exact correlation marker owns a Keycloak
identity. These terminal rows are operator-visible and are not automatically
replaced with a new marker: inspect the mutation id, query Keycloak by the exact
correlation marker, require exactly one marker match, and compare any persisted
`provider_subject` with `users.keycloak_subject` before repairing the provider,
local user, binding, or grant failure. Never adopt or delete a Keycloak user
solely by email.

Migration V3 backfills idempotent user-id binding and desired enabled-state
mutations for every local user. This replaces startup-wide rebinding; mapper
installation still runs at startup, while the mutation worker drains and
repairs the backfill. Provider identities orphaned before V3 have no trustworthy
correlation marker and are deliberately not auto-deleted. Operators must audit
Keycloak users without `identity_user_id` against local `users.keycloak_subject`
and resolve those historical records manually. Migration V4 only extends
mutation constraints and the active-provisioning index for provisional work; it
does not backfill historical provider-only identities because those rows have no
trustworthy Cardo correlation marker.

The Identity integration tests use Testcontainers with PostgreSQL 17.5 to
exercise Flyway migrations, partial indexes, and row-lock behavior. Running
`./mvnw --batch-mode --no-transfer-progress verify` therefore requires a
Docker-compatible container runtime.

Example product configuration:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:${product.keycloak.base-url}/realms/${product.keycloak.realm}}

cardo:
  identity:
    product-auth:
      identity-session-audience: identity
      product-audience: ${product.keycloak.resource-server-client-id}
```

The product also supplies a `ProductRequestPolicy` bean for method-aware public and authenticated
routes. See the [product integration reference](../docs/reference/product-integration.md).

## Documentation

Start with the [Identity documentation index](docs/README.md). Cross-project
architecture and conventions remain in the [Cardo documentation](../docs/README.md).
