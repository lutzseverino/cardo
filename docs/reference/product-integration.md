# Product Integration

Platform capabilities should make products easier to build without absorbing
product behavior. Add product integration modules only when real products repeat
the same wiring and the packaged shape can stay product-neutral.

## Inventory

| Capability | Service owner | Stable client contract | HTTP client module | Product integration |
| --- | --- | --- | --- | --- |
| Identity | users, authentication, sessions, authenticated principal | `identity-client` | `identity-client-http` | `identity-product-auth` for accepting logged-in Odonta users |
| Authorization | resource naming, permission evaluation, access profiles, grant staging, provider adapters | embedded Java APIs | none while authorization has no HTTP owner | embedded mechanics and docs |
| Billing | customers, entitlements, checkout, portal, provider webhooks | `billing-client` | `billing-client-http` | none until products repeat billing guard or flow wiring |
| Invite | invitation token lifecycle and completion | service OpenAPI contract only | none | none until multiple products repeat invite lifecycle wiring |
| Common | shared API errors, data markers, value objects, validation, cookie helpers | embedded Java APIs | none | none |
| OpenAPI Support | generated transport mapping helpers and PATCH presence conversion | embedded Java APIs | none | none |

## Rules

- Keep product resource catalogs, domain rules, tenant semantics, and lifecycle
  decisions in the product.
- Do not make Identity, Billing, or Invite silently grant product-domain access.
- Keep Authorization product-neutral. It provides mechanics; products decide
  which resources and actions exist.
- Prefer Maven modules and Spring Boot auto-configuration when they match the
  existing `identity-client-http` and `billing-client-http` mechanics.
- Do not add a generic platform registry, plugin interface, or module framework.
- Do not add a product integration module just for symmetry.

## Current Shape

Use `identity-product-auth` when a product accepts logged-in Odonta users. The
module auto-configures the shared Spring Security pieces: JWT authority
conversion, permission evaluation, authenticated-user reading, method security,
resource-server setup, and session-cookie bearer token resolution.

Products still configure their issuer and public product paths:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:${odonta.workforce.keycloak.base-url}/realms/${odonta.workforce.keycloak.realm}}

odonta:
  api:
    base-path: /api/v1
  identity:
    product-auth:
      public-paths:
        - ${odonta.api.base-path}/workforce
```

When another platform capability starts to leak repeated product ceremony, first
convert one active product and then Clinic. If the shape is still clear, keep it.
If it only makes modules look uniform, leave the ceremony documented instead.
