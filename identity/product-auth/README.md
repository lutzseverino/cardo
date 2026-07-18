# Identity Product Auth

Spring Security integration for products that accept logged-in Cardo users.

The module provides auto-configuration for the shared product auth mechanics:
JWT authority conversion, resource permission evaluation, authenticated-user
reading, method security, OAuth2 resource-server setup, bearer token lookup from the Identity
session cookie, and optional active-token validation.

This current cookie-authenticated shape is not production-ready. The accepted production
[browser-session contract](../../docs/reference/browser-sessions.md) requires cookie-aware CSRF,
Identity-session validation, server-side product-token exchange, strict product audience
validation, and a method-aware product request-policy seam. Those dependent slices are not yet
implemented.

## Configuration

```yaml
cardo:
  identity:
    product-auth:
      session-cookie-name: cardo.session
      public-paths:
        - ${product.api.base-path}/status
      active-token-validation:
        enabled: true
        introspection-uri: ${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token/introspect
        client-id: ${KEYCLOAK_CLIENT_ID}
        client-secret: ${KEYCLOAK_CLIENT_SECRET}
        cache-ttl: 10s
        cache-max-entries: 2048
        connect-timeout: 2s
        read-timeout: 2s
```

The default public paths are health, info, OpenAPI, and Swagger UI. Product status or onboarding
routes should be listed explicitly in `public-paths`. Active-token validation is fail-closed when
enabled. It caches only positive introspection responses for `cache-ttl`; inactive tokens and
provider failures are denied. Keep the TTL short so disabling a global Identity user takes effect
quickly without making every product request call Keycloak. Keep the introspection timeouts low
because request handling fails closed while waiting for Keycloak.

The supported cookie topology, CSRF behavior, product exchange, and grant-convergence rules are
defined in [Browser Sessions And Product Tokens](../../docs/reference/browser-sessions.md).

This module does not define product resources, product grants, entitlement
rules, invite lifecycles, or tenant lookup. Those remain product-owned.

## Documentation

Start with the [Identity Product Auth documentation index](docs/README.md).
Cross-project architecture and conventions remain in the
[Cardo documentation](../../docs/README.md).
