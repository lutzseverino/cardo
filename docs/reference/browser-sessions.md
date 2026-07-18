# Browser Sessions And Product Tokens

This reference is authoritative for the required production contract for Cardo browser cookies,
product-token acquisition, CSRF, and post-grant authorization convergence. The contract is an
implementation target; browser-session consumers are not production-ready until the dependent
Identity, product-auth, authorization, product, frontend, and deployment slices are complete.

Identity currently implements access/refresh cookie creation, rotation, expiry, refresh-token
logout, cookie authentication for current-session routes, exact `identity` audience validation,
CSRF bootstrap, Identity session-mutation enforcement, and cookie-selected product CSRF
enforcement. Product exchange, grant receipt, product adoption, browser, and deployment requirements
in this reference remain pending.

## Supported Topology

A browser-enabled product uses one HTTPS public origin for:

- the product frontend;
- the product API;
- reverse-proxied Identity session routes.

Cookies are host-only. Cross-origin browser APIs, `Domain` cookies, and sharing one browser session
across sibling product origins are not supported.

## Cookie Contract

| Purpose | Production name | Path | Browser access | SameSite | Lifetime |
| --- | --- | --- | --- | --- | --- |
| Identity access | `__Host-cardo.session` | `/` | HTTP only | `Lax` | No later than access-token expiry |
| Provider refresh | `__Secure-cardo.refresh` | Configured public current-session path | HTTP only | `Lax` | No later than refresh-token expiry |
| CSRF | `__Host-cardo.csrf` | `/` | Readable | `Lax` | Browser session; no `Max-Age` or `Expires` |

All production cookies are `Secure` and omit `Domain`. Creation, rotation, and expiry use identical
name, path, domain, SameSite, and secure attributes. Production rejects non-secure or non-prefixed
cookie configuration.

Local HTTP development uses explicitly configured non-prefixed cookie names with `Secure=false`.
Local defaults are not valid production configuration.

Identity selects the policy with `cardo.identity.session.mode=local|production`. Access, refresh,
and CSRF cookie names, `refresh-cookie-path`, and `secure` are explicit properties under the same
prefix. Production accepts only the names in the table and `Secure=true`; invalid policy fails
startup.

The provider session owns idle and absolute lifetime. A cookie `Max-Age` must not exceed the expiry
of the credential stored in that cookie.

`cardo.identity.session.refresh-cookie-path` is the exact externally visible path shared by current
session, refresh, and logout. Its direct-service default is
`${cardo.api.base-path}/identity/sessions/current`, currently
`/api/v1/identity/sessions/current`. A gateway that rewrites the public prefix must override the
property with the browser-visible path. The configured path must path-match all three operations
and must not path-match product routes or login.

## Authentication Flows

| Operation | Contract |
| --- | --- |
| `GET /identity/sessions/csrf` | Publicly creates the CSRF cookie when absent and returns `204`. |
| `POST /identity/sessions` | Validates CSRF, authenticates credentials, creates the provider session, and sets access and refresh cookies. |
| `GET /identity/sessions/current` | Authenticates from the Identity access cookie or an explicit Identity bearer token and returns the current principal. |
| `POST /identity/sessions/current/refresh` | Validates CSRF, uses the path-scoped refresh credential, rotates provider credentials, and replaces both cookies. |
| `DELETE /identity/sessions/current` | Validates CSRF, uses the refresh credential to revoke the provider session, and expires the access, refresh, and CSRF cookies. |
| Product request with cookie | Validates the Identity access token, exchanges it server-side for the configured product audience, and authenticates from the product RPT. |
| Product request with Authorization header | Validates the supplied token directly and requires the configured product audience. It does not use cookie exchange. |

An explicit Authorization header takes precedence when both header and cookies are present.
Identity access tokens are never accepted directly as product authorization tokens. Product RPTs
are never written to browser cookies. Identity routes that accept an explicit bearer token require
the same exact Identity-session audience as the cookie token.

## CSRF

Before login, a browser calls public `GET /identity/sessions/csrf`. Identity returns `204` with
`Cache-Control: no-store` and sets a cryptographically random CSRF token when the cookie is absent.
The browser sends the cookie value unchanged in `X-CSRF-TOKEN`; the header name is fixed by this
contract.

CSRF validation applies to every unsafe Identity session endpoint, including login before a session
exists, refresh, and logout. Authorization headers do not bypass those controller-credential
routes. It also applies to every unsafe product request when cookie authentication is selected. Any
supplied Authorization header selects bearer authentication and bypasses CSRF validation; the
resource-server filter then validates it and never falls back to the cookie. SameSite is defense in
depth and does not replace the CSRF token.

Identity and `identity-product-auth` reject a missing cookie, missing header, or non-matching value
with `403`. The token remains stable through login and refresh, is recreated only when absent, and
is expired on logout with the same production cookie attributes. The server does not persist the
token; the host-only `__Host-` cookie and exact cookie/header match form the double-submit contract.
Only Identity creates and expires the CSRF cookie. `identity-product-auth` uses a read-only token
repository so product services cannot accidentally become alternate cookie issuers.

## Product Token Validation And Exchange

Every product configures exactly one expected Identity-session audience and one expected product
resource-server audience. Production configuration must supply both; neither is inferred from
untrusted token claims. `identity-product-auth`:

- validates issuer, expiry, signature, the Cardo identity-user claim, and the exact configured
  Identity-session audience on the session token;
- requests a product RPT from the provider with the Identity token and expected audience;
- validates the returned token's exact product audience;
- converts only the returned product token into request authorities;
- fails closed when validation, exchange, introspection, or required claims fail.

For both token types, exact audience means the JWT `aud` claim contains one value and that value
equals the corresponding configured audience. Explicit bearer tokens must satisfy the product
audience rule. Session-cookie tokens must satisfy the Identity-session audience rule before they
can be sent to the provider exchange endpoint.

The target configuration surface is:

```yaml
cardo:
  identity:
    product-auth:
      identity-session-audience: identity
      product-audience: polity
```

`product-audience` is required and non-blank whenever product authentication is enabled;
`identity-session-audience` is additionally required when session-cookie exchange is enabled. The
values name Keycloak resource-server clients; they are not browser origins, API paths, or OAuth
clients used for password and service-account grants. Cardo's current Identity resource audience is
`identity`; the separate confidential OAuth client defaults to `cardo-identity`.

Product exchange is uncached by default. A positive cache is permitted only with a bounded TTL and
an explicit invalidation path that preserves grant-convergence and revocation behavior.

Production deployments enable active-token validation with short timeouts. The Keycloak realm must
map `cardo_user_id` into the Identity and product tokens, issue each token with only its configured
audience, and allow the configured product resource server as an exchange audience.

## Grant Convergence

`GrantReceipt Grants.stage(plan)` remains an in-transaction staging operation. `GrantReceipt`
contains a stable UUID `id`, a `PENDING`, `APPLIED`, or `FAILED` status, and a stable failure code
only when failed. `Optional<GrantReceipt> Grants.find(receiptId)` provides the embedded status
lookup; an empty result means that the identifier is unknown.

Authorization persists the receipt in the caller's transaction. A non-empty plan creates a
`PENDING` receipt and publishes a durable envelope containing the same receipt ID and plan. An empty
plan creates and returns an `APPLIED` receipt without a publication. The provider listener marks the
receipt `APPLIED` only after all idempotent provider work completes. After bounded processing
attempts, it marks the receipt `FAILED` with a stable code. Terminal states do not return to
`PENDING`; an explicit retry contract, if added, must be a separate operation.

The application that owns the product mutation stores the receipt ID with its own lifecycle in the
same transaction and exposes convergence through its API. A product must not claim that new access
is usable while the receipt is pending. It must expose durable failure rather than polling forever.

After the receipt is applied, the next uncached product exchange observes the new provider grants.
Account provisioning and invitation acceptance use the same sequence:

```text
product transition -> durable grant receipt -> provider application -> product exchange -> access
```

Identity does not decide which product grants are required. Invite continues to own invitation
grant-snapshot staging, while the consuming product owns its membership transition and user-facing
convergence state.

## Product-Auth Boundary

Cardo owns the shared Spring Security filter chain and these mechanics:

- cookie-versus-header authentication selection;
- CSRF enforcement;
- Identity-token validation and product-token exchange;
- Identity-session and product audience validation plus active-token validation;
- JWT authority conversion, permission evaluation, and authenticated-user reading;
- shared filter ordering and deny-by-default fallback.

Products contribute method-aware request matchers and authorization decisions through the
product-auth configuration seam. They own public product routes and product authorization policy,
but do not replace the shared filter chain or redeclare Cardo-owned security beans. The chain uses
one coordinated cookie selector, bearer resolver, and read-only CSRF repository; generic product
`BearerTokenResolver` or `CsrfTokenRepository` beans are not supported customization seams.
