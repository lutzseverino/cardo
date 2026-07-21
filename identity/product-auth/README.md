# Identity Product Auth

Spring Security integration for products that accept logged-in Cardo users.

The module provides auto-configuration for the shared product auth mechanics:
JWT authority conversion, resource permission evaluation, authenticated-user
reading, method security, OAuth2 resource-server setup, Identity-session validation, uncached
product-token exchange, exact audience validation, cookie-selected CSRF enforcement, a
method-aware request-policy seam, and optional active-token validation.

Explicit bearer credentials stay on Spring Security's normal Resource Server path. Browser
session credentials use a separate authentication filter after CSRF and before Resource Server
bearer authentication. The bearer resolvers only extract credentials: the browser authentication
manager validates the Identity credential, obtains the product credential, and delegates that
credential to the same product JWT authentication manager used for explicit bearers. This ordering
is security-sensitive because Spring Resource Server automatically excludes credentials recognized
by its own bearer resolver from CSRF; the Resource Server resolver must remain header-only.

## Configuration

```yaml
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

Declare the product routes in one policy bean. Cardo installs these rules between its shared
documentation/actuator rules and a deny-all fallback:

```java
@Bean
ProductRequestPolicy productRequestPolicy() {
  return rules -> rules
      .permitAll(HttpMethod.GET, "/api/v1/status")
      .authenticated(HttpMethod.GET, "/api/v1/accounts/**")
      .authenticated(HttpMethod.POST, "/api/v1/accounts/**");
}
```

Health, info, OpenAPI, and Swagger UI are public by default. Every other route must match the
product policy; unmatched routes are denied. Method-specific authenticated rules cover
state-changing routes, while Cardo independently enforces cookie-selected CSRF. Active-token
validation is fail-closed when enabled. It caches only positive introspection responses for
`cache-ttl`; inactive tokens and provider failures are denied. Keep the TTL short so disabling a
global Identity user takes effect quickly without making every product request call Keycloak.
Keep the introspection timeouts low because request handling fails closed while waiting for
Keycloak.

A cookie request performs UMA authorization and then, when active-token validation is enabled,
introspection of the fresh product RPT. Positive introspection caching is most effective for reused
explicit bearer tokens; it may provide few hits for cookie requests when Keycloak returns a new RPT
for each authorization request. Keep the check enabled when the required revocation and disablement
window justifies this latency and availability coupling.

Unsafe requests require an exact `cardo.csrf` cookie and `X-CSRF-TOKEN` header match whenever the
session cookie is selected. Any supplied Authorization header takes precedence and bypasses
cookie-selected CSRF without falling back to the cookie. The Resource Server accepts or rejects a
Bearer credential; another authentication scheme leaves the request unauthenticated. Safe and
anonymous requests do not require CSRF. The product integration only reads the Identity-issued CSRF
cookie and never creates, rotates, or expires it. Production products
configure `__Host-cardo.session` and `__Host-cardo.csrf` to match Identity's production policy.
The Cardo filter chain wires its cookie selector, dedicated cookie authentication filter,
header-only bearer resolver, and read-only CSRF repository as one coordinated mechanism.
Product-defined `BearerTokenResolver` or `CsrfTokenRepository` beans are not customization seams and
do not replace those Cardo-owned instances.

The session cookie is validated for issuer, signature, expiration, an exact single `identity`
audience, and a valid `cardo_user_id` before exchange. The exchanged credential and explicit bearer
credentials are validated against the exact single configured product audience and the same user
claim before authorities are built. The exchanged token must identify the same Cardo user as the
session token. The product credential is decoded once by the shared product JWT authentication
manager. Acquisition is uncached, uses bounded network timeouts, and fails closed.

Product-token acquisition uses Keycloak Authorization Services' UMA ticket grant
(`urn:ietf:params:oauth:grant-type:uma-ticket`) with an audience and no permission parameters. This
is Keycloak/UMA-specific behavior, not RFC 8693 OAuth Token Exchange; the `token-exchange`
configuration name describes Cardo's operation, not a portable grant type.

The supported cookie topology, CSRF behavior, product exchange, and grant-convergence rules are
defined in [Browser Sessions And Product Tokens](../../docs/reference/browser-sessions.md).

This module does not define product resources, product grants, entitlement
rules, invite lifecycles, or tenant lookup. Those remain product-owned.

## Documentation

Start with the [Identity Product Auth documentation index](docs/README.md).
Cross-project architecture and conventions remain in the
[Cardo documentation](../../docs/README.md).
