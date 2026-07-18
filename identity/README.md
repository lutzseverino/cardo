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
- session-cookie bearer token resolution
- cookie-selected CSRF enforcement

Products still own product access. Identity must not silently grant product-domain
permissions as a side effect of user creation
or login. Products issue product grants from their own flows by staging
`GrantPlan` instances with product-owned client authorities and resource
actions.

The accepted production [browser-session contract](../docs/reference/browser-sessions.md) requires
one HTTPS origin for the product frontend, product API, and reverse-proxied Identity session routes.
Identity implements the access/refresh cookie lifecycle, CSRF bootstrap and session-mutation
enforcement, refresh rotation, refresh-token logout, and exact `identity` audience validation.
Product-token exchange and grant convergence remain dependent slices, so browser-session consumers
are not yet production-ready.

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
      public-paths:
        - ${product.api.base-path}/status
```

## Documentation

Start with the [Identity documentation index](docs/README.md). Cross-project
architecture and conventions remain in the [Cardo documentation](../docs/README.md).
