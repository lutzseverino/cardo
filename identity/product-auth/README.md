# Identity Product Auth

Spring Security integration for products that accept logged-in Odonta users.

The module provides auto-configuration for the shared product auth mechanics:
JWT authority conversion, resource permission evaluation, authenticated-user
reading, method security, OAuth2 resource-server setup, and bearer token lookup
from the Identity session cookie. It can also validate bearer tokens against the
identity provider's introspection endpoint so global Identity disablement is
enforced for already-issued JWTs after a short cache window.

## Configuration

```yaml
odonta:
  identity:
    product-auth:
      session-cookie-name: odonta.session
      public-paths:
        - ${odonta.api.base-path}/workforce
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

The default public paths are health, info, OpenAPI, and Swagger UI. Product
status or onboarding routes should be listed explicitly in `public-paths`.
Active token validation is fail-closed when enabled. It caches only positive
introspection responses for `cache-ttl`; inactive tokens and provider failures
are denied. Keep the TTL short so disabling a global Identity user takes effect
quickly without making every product request call Keycloak. Keep the
introspection timeouts low because request handling fails closed while waiting
for Keycloak.

This module does not define product resources, product grants, entitlement
rules, invite lifecycles, or tenant lookup. Those remain product-owned.
