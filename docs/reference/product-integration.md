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
resource-server setup, session-cookie authentication orchestration, and optional active-token validation
through the identity provider introspection endpoint. It validates the Identity session credential,
exchanges it server-side for an uncached product-audience requesting-party token, validates exact
single audiences and the Cardo user claim, and builds authorities only from the product token. It
also enforces double-submit CSRF on unsafe requests when the session cookie is selected and leaves
explicit product bearer requests to resource-server authentication.

Products configure their issuer, Identity and product audiences, and active-token validation
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
      identity-session-audience: identity
      product-audience: polity
      token-exchange:
        connect-timeout: 2s
        read-timeout: 2s
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

Product-token acquisition is uncached, requires the returned credential to name the same Cardo user
as the session credential, and defaults to two-second connect and read timeouts. It uses Keycloak
Authorization Services' UMA ticket grant and returns a Keycloak RPT; it is not RFC 8693 OAuth Token
Exchange despite the `token-exchange` configuration group. Provide one
`ProductRequestPolicy` bean with method-aware `permitAll`, `authenticated`, or
`hasAuthority` rules. Cardo keeps health and API documentation public, applies the product rules in
declaration order, and denies every unmatched route. Unsafe routes selected by the session cookie
require CSRF. Any supplied `Authorization` header selects non-cookie authentication with no cookie
fallback; a non-Bearer header therefore leaves a public route anonymous. Products do not replace
the filter chain.

When enabled, active-token validation fails closed and caches only positive introspection responses
for the configured TTL. Cookie requests perform UMA authorization and then introspection of the
fresh product RPT; cache reuse on that path depends on Keycloak returning the same RPT, while reused
explicit bearer tokens benefit directly. Keep the TTL short so global
Identity disablement is enforced quickly for already-issued JWTs. Keep
introspection timeouts low because product requests wait on the fail-closed
validation path.

Products that stage grants store the `GrantReceipt.id()` returned by `Grants.stage(plan)` in the
same transaction as their lifecycle transition and expose `Grants.find(id)` through their own API.
They do not promise usable access while authorization is `PENDING`, and surface the stable failure
code if it reaches `FAILED`. Once it is `APPLIED`, the next uncached product exchange observes the
new grants; Identity does not grant product access or decide which permissions a product flow
requires.

Use `invite-client` for compile-time product code and `invite-client-http` as a
runtime dependency. Configure `cardo.invite.client.base-url` with the Invite
`/api/v1` base URL and, for the default deployment,
`cardo.invite.client.service-token-scope=cardo-invite`.
The HTTP client obtains a service token from the shared client-credentials
provider; Invite derives product ownership from that token's OAuth client
identifier.

The Identity, Invite, and Billing HTTP clients default to two-second connection
and response timeouts. Override the relevant
`cardo.<service>.client.connect-timeout` and `read-timeout` properties only with
positive, bounded durations. All three clients reuse the supplied
`KeycloakClientCredentialsTokenProvider`; the provider caches a token only when
Keycloak returns a positive `expires_in` value and refreshes it shortly before
that expiry. Its default acquisition timeouts are two seconds and its default
refresh skew is thirty seconds. Consumers that need different bounds construct
the provider with `KeycloakClientCredentialsTokenSettings`.

Each adapter requires a non-blank target scope and requests only that scope:

| Adapter | Required property | Deployed value and exact token audience |
| --- | --- | --- |
| Identity | `cardo.identity.client.service-token-scope` | `identity` |
| Billing | `cardo.billing.client.service-token-scope` | `billing` |
| Invite | `cardo.invite.client.service-token-scope` | `cardo-invite` by default; its sole audience must equal the deployed Invite client ID |

The provider normalizes scope lists and isolates their caches. Scoped tokens
never share the unscoped cache used for Cardo's Keycloak administration calls.
Follow the [ordered scoped service-token rollout](../how-to/roll-out-scoped-service-tokens.md);
adding these properties without the matching optional Keycloak scopes and
audience mappers fails closed at the first remote call.

Billing and Invite perform issuer discovery lazily when the first bearer token
is decoded, preserving Spring Boot's startup behavior. Public health can
therefore remain available during an issuer outage, while protected bearer
authentication fails closed. Deployment smoke tests must exercise a protected
authenticated route rather than relying on startup or health alone.

This cache removes a Keycloak request from the ordinary service-call path, but
it also means revoking a service credential does not remove an already-issued
token. Keep service-token lifetimes short enough for the deployment's
revocation requirement. Downstream services still validate every presented
token through their resource-server chain; caching does not bypass that
processing. The provider fails closed when acquisition fails or Keycloak omits
a positive expiry.

Invite's service also requires a positive product-caller boundary. Add the
product OAuth client identifier to
`cardo.invite.product-callers.allowed-client-ids`, create the `product-service`
client role on the configured Invite Keycloak client, and grant that role only to
the product client's service account. With the default client ID, Invite requires the resulting
`cardo-invite:product-service` authority and exact `cardo-invite` audience. A customized client ID
changes both values together. Invite also requires the allowlist entry; merely lacking a Cardo
end-user claim never makes a token a service token.

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
