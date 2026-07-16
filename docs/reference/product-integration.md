# Product Integration

Cardo capabilities should make products easier to build without absorbing
product behavior. Add product integration modules only when real products repeat
the same wiring and the packaged shape can stay product-neutral.

## Inventory

| Capability | Service owner | Stable client contract | HTTP client module | Product integration |
| --- | --- | --- | --- | --- |
| Identity | users, authentication, sessions, authenticated principal | `identity-client` | `identity-client-http` | `identity-product-auth` for accepting logged-in Cardo users |
| Authorization | resource naming, permission evaluation, access profiles, grant staging, provider adapters | embedded Java APIs | none while authorization has no HTTP owner | embedded mechanics and docs |
| Billing | customers, entitlements, checkout, portal, provider webhooks | `billing-client` | `billing-client-http` | none until products repeat billing guard or flow wiring |
| Common | shared API errors, data markers, value objects, validation, cookie helpers | embedded Java APIs | none | none |
| OpenAPI Support | generated transport mapping helpers and PATCH presence conversion | embedded Java APIs | none | none |

## Rules

- Keep product resource catalogs, domain rules, tenant semantics, and lifecycle
  decisions in the product.
- Do not make Identity or Billing silently grant product-domain access.
- Keep invitation lifecycle and acceptance semantics in the product that owns
  the resulting membership or access.
- Keep Authorization product-neutral. It provides mechanics; products decide
  which resources and actions exist.
- Prefer Maven modules and Spring Boot auto-configuration when they match the
  existing `identity-client-http` and `billing-client-http` mechanics.
- Do not add a generic platform registry, plugin interface, or module framework.
- Do not add a product integration module just for symmetry.

## Current Shape

Use `identity-product-auth` when a product accepts logged-in Cardo users. The
module auto-configures the shared Spring Security pieces: JWT authority
conversion, permission evaluation, authenticated-user reading, method security,
resource-server setup, session-cookie bearer token resolution, and optional
active-token validation through the identity provider introspection endpoint.

Products still configure their issuer, public product paths, and active-token
validation credentials:

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

When enabled, active-token validation fails closed and caches only positive
introspection responses for the configured TTL. Keep the TTL short so global
Identity disablement is enforced quickly for already-issued JWTs. Keep
introspection timeouts low because product requests wait on the fail-closed
validation path.

When another Cardo capability starts to leak repeated product ceremony, first
convert one active product and then a second independent consumer. If the shape
is still clear, keep it. If it only makes modules look uniform, leave the
ceremony documented instead.
