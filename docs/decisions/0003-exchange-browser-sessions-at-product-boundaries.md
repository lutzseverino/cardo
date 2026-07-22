# Exchange Browser Sessions At Product Boundaries

## Status

Accepted

## Context

Identity currently places an Identity requesting-party token in `cardo.session`. Product services
can read that cookie through `identity-product-auth`, but they do not validate a product audience.
Accepting the Identity token directly at every product creates token-confusion risk, and the token
cannot acquire authorities that are applied after it was issued.

Putting one all-permissions product token in the browser cookie would couple the session to one
product, grow with every resource grant, and still leave asynchronous grant convergence
unobservable. A multi-audience token would widen that coupling across all products. Storing a
provider refresh token in the shared cookie would also expose a long-lived credential to every
same-origin product route.

Browser authentication is ambient, so disabling CSRF while resolving authentication from a cookie
is unsafe. Cookie scope and lifetime must also agree with the public deployment topology and the
provider credentials they contain.

## Decision

Support browser sessions only when one public origin serves the product frontend, its product API,
and reverse-proxied Identity session routes. Identity cookies are host-only. Cross-origin browser
APIs and parent-domain cookies are outside this contract.

Identity owns two browser credentials:

- a short-lived Identity access token whose only audience is the configured Identity resource
  server, in the host-wide session cookie;
- a longer-lived provider refresh token in a cookie whose path is limited to Identity session
  routes.

The shared session cookie is an authentication credential, not a product authorization token. A
product service using `identity-product-auth` validates the Identity token's exact configured
session audience and exchanges it server-side for a fresh requesting-party token whose single
audience is that product's configured resource-server client. Spring Security builds the request
authentication from the exchanged product token. Explicit `Authorization: Bearer` clients must
supply an already product-scoped token and do not use the browser exchange.

The provider operation is Keycloak Authorization Services' UMA ticket grant, not RFC 8693 OAuth
Token Exchange. Cookie extraction and exchange run in a dedicated authentication filter after CSRF;
Spring Resource Server's bearer resolver remains header-only so its automatic bearer-request CSRF
exclusion cannot classify the ambient cookie as an explicit bearer credential.

Product exchange is fail-closed and uncached by default. An optimization may cache only positive
exchange results for a bounded interval when it preserves an explicit invalidation path; caching
must never become the correctness mechanism for authority convergence or revocation.

Identity refreshes the short-lived session credential through the path-scoped refresh credential
and rotates both cookies when the provider rotates the refresh token. Refresh cannot extend beyond
the provider session's idle or absolute limits. Logout uses the refresh credential to revoke the
provider session and expires both authentication cookies and the CSRF cookie with the same name,
path, domain, SameSite, and secure attributes used to create them.

The Identity session controller only reads and writes the HTTP cookie contract.
`AuthenticationService` owns session creation, current-session validation, refresh, and revocation
through an Identity provider port; the provider adapter owns the refresh and revocation protocol.

Production cookies use these invariants:

- session: `__Host-cardo.session`, `Secure`, `HttpOnly`, `SameSite=Lax`, `Path=/`, and no `Domain`;
- refresh: `__Secure-cardo.refresh`, `Secure`, `HttpOnly`, `SameSite=Lax`, a configured path that
  exactly scopes the externally visible current-session, refresh, and logout routes, and no
  `Domain`;
- CSRF: `__Host-cardo.csrf`, `Secure`, readable by the browser, `SameSite=Lax`, `Path=/`, and no
  `Domain`.

Local HTTP development uses explicit non-prefixed names and disables `Secure`. Production startup
must reject that local-only policy. Cookie lifetime never exceeds the corresponding token expiry;
the provider session remains the source of truth for idle and absolute lifetime. The CSRF cookie is
a browser-session cookie with no `Max-Age` or `Expires` attribute.

Cardo uses a cookie-to-header CSRF token for browser requests. Public
`GET /identity/sessions/csrf` materializes the CSRF cookie, and clients echo it as
`X-CSRF-TOKEN`. Every unsafe Identity session endpoint, including login, refresh, and logout, must
require an exact cookie/header match. Unsafe product requests require it whenever cookie
authentication is selected. A valid explicit Authorization header takes precedence over cookies
and is not subject to CSRF validation because it is not ambient browser authentication.

Authorization staging remains asynchronous. As refined by ADR 0004, a product first records its
acceptance intent and durable command, calls Invite acceptance idempotently, and then stages the
product-owned plan, stores its receipt, and completes its product transition in one local
transaction. The embedded authorization boundary persists and returns a stable grant-receipt
identifier before publishing a receipt-bearing plan. Non-empty plans begin pending; empty plans are
immediately applied. Provider application moves the durable receipt to applied or, after bounded
attempts, failed. The product exposes that convergence status through its API. Once applied, the
next uncached product exchange includes the new authorities. Identity and Invite do not infer
product lifecycle or silently grant product access.

`identity-product-auth` owns issuer and audience validation, session-cookie recognition, product
token exchange, authority conversion, authenticated-user reading, CSRF enforcement, active-token
validation, and the shared filter order. A product supplies method-aware request authorization
rules through a configuration seam. The Cardo chain applies those rules and denies unmatched
requests; products do not replace the chain or recreate Cardo-owned beans.

## Consequences

- Product services never accept an Identity-audience cookie token as their authorization token.
- Product requesting-party tokens and resource grants do not enter browser cookies.
- Newly applied authorities become visible without rotating the browser session cookie.
- Cookie-authenticated requests incur one fail-closed product-token exchange by default.
- Identity's controller owns refresh-cookie transport, its Authentication service owns the session
  lifecycle, and its provider adapter owns the refresh protocol; product routes never receive that
  credential.
- Products must expose grant convergence before promising newly authorized behavior.
- Explicit bearer clients remain stateless and product-audience-specific.
- Implementation proceeds in dependency order:

  | Order | Owning repository or boundary | Slice |
  | --- | --- | --- |
  | 1 | Cardo Identity | Cookie policy, current-session consumption, refresh rotation, and logout |
  | 2 | Cardo Identity and `identity-product-auth` | Cookie-aware CSRF bootstrap and enforcement |
  | 3 | Cardo `identity-product-auth` | Identity-token validation, product exchange, audience validation, and product route-rule seam |
  | 4 | Cardo Authorization | Durable grant receipt and provider-application status |
  | 5 | Polity | Product-auth adoption plus account and invitation convergence APIs |
  | 6 | Polity browser client | CSRF bootstrap/header handling and pending-access UX |
  | 7 | Polity deployment | Same-origin routing, production cookie mode, and Keycloak audience/client provisioning |
- Invitation grant planning, receipt retention, convergence, and recovery are product-owned;
  Invite owns only its invitation lifecycle and is called idempotently by the product workflow.

## Alternatives Considered

- Accept the Identity token directly in every product. Rejected because it prevents strict product
  audience validation and leaves product permissions stale.
- Store an all-permissions product RPT in the browser cookie. Rejected because the cookie becomes
  product-specific, grows with resource grants, and remains stale after asynchronous changes.
- Issue one multi-audience browser token. Rejected because it couples Identity to the installed
  product set and broadens token use beyond least privilege.
- Store the provider refresh token in the host-wide session cookie. Rejected because every product
  route would receive a long-lived credential that only Identity should handle.
- Use parent-domain cookies for product subdomains. Rejected because sibling applications would
  share ambient credentials and weaken cookie isolation.
- Introduce an opaque Cardo session store immediately. Rejected because provider sessions and
  path-scoped refresh credentials meet the current requirement without adding a new durable secret
  store and synchronous Identity lookup to every product request.
