# Browser Sessions And Product Tokens

This reference is authoritative for the required production contract for Cardo browser cookies,
product-token acquisition, CSRF, and post-grant authorization convergence. The contract is an
implementation target; browser-session consumers are not production-ready until the dependent
Identity, product-auth, authorization, product, frontend, and deployment slices are complete.

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
| Provider refresh | `__Secure-cardo.refresh` | Identity session API path | HTTP only | `Lax` | No later than refresh-token expiry |
| CSRF | `__Host-cardo.csrf` | `/` | Readable | `Lax` | Browser session; no `Max-Age` or `Expires` |

All production cookies are `Secure` and omit `Domain`. Creation, rotation, and expiry use identical
name, path, domain, SameSite, and secure attributes. Production rejects non-secure or non-prefixed
cookie configuration.

Local HTTP development uses explicitly configured non-prefixed cookie names with `Secure=false`.
Local defaults are not valid production configuration.

The provider session owns idle and absolute lifetime. A cookie `Max-Age` must not exceed the expiry
of the credential stored in that cookie.

## Authentication Flows

| Operation | Contract |
| --- | --- |
| `POST /identity/sessions` | Validates CSRF, authenticates credentials, creates the provider session, and sets access and refresh cookies. |
| `GET /identity/sessions/current` | Authenticates from the Identity access cookie or an explicit Identity bearer token and returns the current principal. |
| `POST /identity/sessions/current/refresh` | Validates CSRF, uses the path-scoped refresh credential, rotates provider credentials, and replaces both cookies. |
| `DELETE /identity/sessions/current` | Validates CSRF, revokes the provider session, and expires both cookies. |
| Product request with cookie | Validates the Identity access token, exchanges it server-side for the configured product audience, and authenticates from the product RPT. |
| Product request with Authorization header | Validates the supplied token directly and requires the configured product audience. It does not use cookie exchange. |

An explicit Authorization header takes precedence when both header and cookies are present.
Identity access tokens are never accepted directly as product authorization tokens. Product RPTs
are never written to browser cookies.

## CSRF

A safe Identity or product bootstrap request materializes the readable CSRF cookie. Browser clients
echo its value in Cardo's configured CSRF header.

CSRF validation applies to every unsafe Identity session endpoint, including login before a session
exists, refresh, and logout. It also applies to every unsafe product request when cookie
authentication is selected. Requests authenticated by an explicit valid Authorization header
bypass CSRF validation. SameSite is defense in depth and does not replace the CSRF token.

## Product Token Validation And Exchange

Every product configures exactly one expected resource-server audience. `identity-product-auth`:

- validates issuer, expiry, signature, and the Cardo identity-user claim on the Identity session
  token;
- requests a product RPT from the provider with the Identity token and expected audience;
- validates the returned token's exact product audience;
- converts only the returned product token into request authorities;
- fails closed when validation, exchange, introspection, or required claims fail.

Product exchange is uncached by default. A positive cache is permitted only with a bounded TTL and
an explicit invalidation path that preserves grant-convergence and revocation behavior.

Production deployments enable active-token validation with short timeouts. The Keycloak realm must
map `cardo_user_id` into the Identity and product tokens and must allow the configured product
resource server as an exchange audience.

## Grant Convergence

`Grants.stage(plan)` remains an in-transaction staging operation. The authorization boundary
provides a durable receipt whose states distinguish pending, applied, and failed work.

The application that owns the product mutation persists or associates that receipt with its own
lifecycle and exposes convergence through its API. A product must not claim that new access is
usable while the receipt is pending. It must expose durable failure rather than polling forever.

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
- product audience and active-token validation;
- JWT authority conversion, permission evaluation, and authenticated-user reading;
- shared filter ordering and deny-by-default fallback.

Products contribute method-aware request matchers and authorization decisions through the
product-auth configuration seam. They own public product routes and product authorization policy,
but do not replace the shared filter chain or redeclare Cardo-owned security beans.
