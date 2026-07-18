# Product Integration

Cardo capabilities should make products easier to build without absorbing
product behavior. Add product integration modules only when real products repeat
the same wiring and the packaged shape can stay product-neutral.

## Inventory

| Capability | Service owner | Stable client contract | HTTP client module | Product integration |
| --- | --- | --- | --- | --- |
| Identity | users, authentication, sessions, authenticated principal | `identity-client` | `identity-client-http` | `identity-product-auth` for accepting logged-in Cardo users |
| Authorization | resource naming, permission evaluation, access profiles, grant staging, provider adapters | embedded Java APIs | none while authorization has no HTTP owner | embedded mechanics and docs |
| Invite | invitation tokens, expiry, delivery, provisional identity completion, lifecycle state, grant-snapshot staging | `invite-client` | `invite-client-http` | none until multiple products repeat durable invite orchestration |
| Billing | customers, entitlements, checkout, portal, provider webhooks | `billing-client` | `billing-client-http` | none until products repeat billing guard or flow wiring |
| Common | shared API errors, data markers, value objects, validation, cookie helpers | embedded Java APIs | none | none |
| OpenAPI Support | generated transport mapping helpers and PATCH presence conversion | embedded Java APIs | none | none |

## Rules

- Keep product resource catalogs, domain rules, tenant semantics, and lifecycle
  decisions in the product.
- Do not make Identity or Billing silently grant product-domain access.
- Keep invitation token lifecycle in Invite. Products own why an invitation is
  created, what resource it targets, and any domain lifecycle surrounding
  acceptance.
- Treat Invite as an external owner from product workflows. Persist the
  product-domain transition and a durable integration command together, then
  invoke Invite after commit. Use the product invitation UUID as Invite's
  `requestId` so create retries are idempotent.
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
resource-server setup, session-cookie bearer token resolution, and optional active-token validation
through the identity provider introspection endpoint. It also enforces double-submit CSRF on unsafe
requests when the session cookie is selected and leaves explicit bearer requests to resource-server
authentication.

The current cookie-authenticated shape is not production-ready. The accepted
[browser-session contract](browser-sessions.md) still requires Identity-session validation,
server-side exchange for a product-audience requesting-party token, strict audience validation, and
a method-aware product request-policy seam. Do not adopt browser cookie authentication in a
production product until those dependent slices are implemented.

Products currently configure their issuer, public product paths, and active-token validation
credentials:

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
      session-cookie-name: cardo.session
      csrf-cookie-name: cardo.csrf
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

When enabled, active-token validation fails closed and caches only positive introspection responses
for the configured TTL. Keep the TTL short so global
Identity disablement is enforced quickly for already-issued JWTs. Keep
introspection timeouts low because product requests wait on the fail-closed
validation path.

The production contract requires products that stage grants to expose the durable receipt through
their own lifecycle contract. They do not promise usable access while authorization is pending.
Once provider application succeeds, the next uncached product exchange observes the new grants;
Identity does not grant product access or decide which permissions a product flow requires.

Use `invite-client` for compile-time product code and `invite-client-http` as a
runtime dependency. Configure `cardo.invite.client.base-url` with the Invite
`/api/v1` base URL. The HTTP client obtains a service token from the shared
client-credentials provider; Invite derives product ownership from that token's
OAuth client identifier.

Invite's service also requires a positive product-caller boundary. Add the
product OAuth client identifier to
`cardo.invite.product-callers.allowed-client-ids`, create the `product-service`
client role on the `cardo-invite` Keycloak client, and grant that role only to
the product client's service account. Invite requires the resulting
`cardo-invite:product-service` authority, the `cardo-invite` audience, and the
allowlist entry; merely lacking a Cardo end-user claim never makes a token a
service token.

Invite create, accept, and revoke calls are integration effects. A product must
not place them inside a local transaction and assume atomicity. Dispatch them
from a durable command or outbox after the product transaction commits, and
retry failures. Invite create is idempotent for `(product, requestId)`; accept
and revoke are idempotent in their matching terminal state. Carry the product's
committed acceptance timestamp in the durable accept command; Invite evaluates
expiry against that business timestamp rather than the eventual retry time.

Invitation credential setup is asynchronous and does not carry a password.
Call the completion request and poll the same invitation-scoped completion
resource until it is `completed` or `failed`. Failure is durable, and an
explicit repeat of the same request restarts exhausted or expired work.
Completion only establishes the Identity user; it never accepts the product
invitation or applies the product's domain transition.

When another Cardo capability starts to leak repeated product ceremony, first
convert one active product and then a second independent consumer. If the shape
is still clear, keep it. If it only makes modules look uniform, leave the
ceremony documented instead.
