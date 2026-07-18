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

Products still own product access. Identity must not silently grant product-domain
permissions as a side effect of user creation
or login. Products issue product grants from their own flows by staging
`GrantPlan` instances with product-owned client authorities and resource
actions.

The accepted production [browser-session contract](../docs/reference/browser-sessions.md) requires
one HTTPS origin for the product frontend, product API, and reverse-proxied Identity session routes.
Its cookie lifecycle, CSRF, refresh credential, server-side product exchange, and grant-convergence
slices are not yet implemented; current browser-session consumers are not production-ready.

Invited-user credential setup and provisional deletion are durable operations.
Credential setup delegates password/profile entry to Keycloak's action flow;
Cardo never receives the password. Identity persists the operation before the
provider call, performs provider work outside database transactions, and
reconciles with leases and bounded retry. The operation response exposes its
status and action expiry. Provisional deletion follows the same model and is
queryable after the request, including after the local user row is gone.

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
