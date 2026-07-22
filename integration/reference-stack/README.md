# Reference Stack

This unpublished, test-only module is Cardo's executable product reference. It
starts the checkout-built Identity, Invite, Billing, and reference-product JARs
with `java -jar`, then exercises their public contracts against disposable,
digest-pinned PostgreSQL, Keycloak, Mailpit, and Caddy dependencies.

The reference product owns a tenant membership transition, persists its own
invitation command and authorization receipt, embeds Authorization, and consumes
only the stable Invite and Billing clients. A single HTTPS browser
origin exposes the product plus Identity session routes. Caddy explicitly hides
the test-control paths before applying its product fallback.

## Provider Clients

The test realm deliberately separates the product's credentials:

- `reference-product` owns the product resource server, UMA catalog, PAT, and
  introspection credential;
- `reference-product-outbound` owns only the optional `cardo-invite` and
  `billing` scopes used by stable outbound clients.

The split prevents Keycloak's automatic `uma_protection` role from contaminating
scoped outbound tokens. It belongs only to this executable fixture and is not a
new public registry or runtime configuration contract.

## Pre-grant acceptance

Invitation acceptance must authenticate the invited user before that user's
first product grant exists. The acceptance POST and convergence GET use the
standard `identity-product-auth` chain with method-aware `authenticated` rules.
It validates and exchanges the Identity session cookie for a fresh,
exact-audience product RPT; an authenticated RPT with no product permission can
therefore reach the product-owned invitation checks. Direct Identity bearers
remain invalid at the product boundary, cookie-authenticated acceptance retains
the standard read-only, header-only CSRF check, and authority-protected routes
still require their product permission.

Before recording acceptance, the product compares its durable `invitedUserId`
with the authenticated Cardo user from the signed Identity session, then checks
that Invite still reports the same immutable user identifier. It binds the
trusted authorization subject to the durable acceptance intent; convergence is
visible only to that bound subject.

## Guarantees Exercised

The journey covers production cookie names and CSRF, exact token audiences,
real invitation mail and Keycloak required-action forms, durable product-owned
acceptance, a controlled remote-success/local-gap retry, a single retained
`PENDING` to `APPLIED` grant receipt, fresh RPT authorization, active
introspection after provider revocation, Billing entitlement boundaries,
session refresh/logout, isolated databases, sanitized diagnostics, and complete
teardown. The provider materializer runs twice and proves unique definitions and
least-privilege service accounts.

Run the fixture through the
[reference-stack how-to](../../docs/how-to/run-reference-stack.md). The artifact
has `maven.deploy.skip=true`, is absent from the BOM and release inventory, and
must never be published.

## Documentation

See the [local documentation index](docs/README.md).
