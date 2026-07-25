# Own Browser-Session Lifetimes In Identity

## Status

Accepted

## Context

The Identity API exposed only `expiresAt`, whose actual meaning is the expiry of the current
Identity authorization token. Browser clients could not distinguish that short credential expiry
from idle and absolute browser-session deadlines, compensate for clock skew, or decide whether an
explicit refresh was still useful.

Keycloak provider settings are deployment policy and do not provide Cardo with stable,
request-time idle and absolute timestamps. Deriving those values independently in controllers,
cookies, or clients would create multiple session authorities. Treating normal product traffic as
idle activity would also require every product request to call Identity or mutate shared session
state.

## Decision

Identity owns a first-class `BrowserSession` aggregate in its existing PostgreSQL datastore. It
records the provider session identifier and subject, a one-way hash of the current refresh
credential, fixed start and absolute timestamps, a sliding idle timestamp, revocation state, and a
short refresh lease. Raw access and refresh credentials are never persisted.

Password authentication creates the aggregate only after provider authentication, Identity
authorization, and local-principal validation succeed. The explicit refresh operation claims the
session under a row lock, performs provider I/O outside the database transaction, then atomically
rotates the refresh hash and idle deadline. The fencing lease is never reclaimed: an elapsed or
ambiguous provider refresh fails closed and requires reauthentication because a rotated credential
may have been lost. Only explicit refresh renews idle lifetime; login fixes
the absolute deadline, and current-session and product requests do not change either deadline.
Concurrent refresh attempts fail with a stable conflict rather than racing refresh-token rotation.

Browser authentication, current-session, and refresh responses expose `serverTime`,
`sessionStartedAt`, `idleExpiresAt`, `absoluteExpiresAt`, and `refreshable`. A valid explicit bearer
that did not originate in Cardo's browser flow has no browser-session metadata. The existing
top-level `expiresAt` remains the authorization credential expiry. Access and refresh cookie
lifetimes are capped by both their credential expiry and the earliest browser-session deadline.

Identity session endpoints consult the aggregate. Product traffic remains token-based and
stateless with respect to Identity's database, preserving the product-boundary design in ADR 0003.
Deploying this decision does not infer records for browser credentials issued by older versions:
those credentials must authenticate again. Cardo has no production sessions during this rollout,
so a speculative and unreliable backfill is intentionally omitted.
Terminal rows are retained for a bounded period and removed by a scheduled workflow.

## Consequences

- Browser clients receive one authoritative, clock-skew-aware session lifetime contract.
- Session policy, refresh serialization, revocation, and error semantics remain inside Identity
  rather than forming a parallel controller or frontend pattern.
- Identity session endpoints add one indexed local datastore operation; product requests add none.
- Refresh availability now depends on Identity's datastore as well as the provider, which is
  consistent with Identity already owning the session operation.
- The provider still authenticates credentials, issues tokens, and revokes its session, while
  Cardo owns the product-level browser-session lifetime it promises through its API.

## Alternatives Considered

- Derive deadlines from access- and refresh-token expiries. Rejected because token expiry does not
  reliably identify both provider idle and absolute session deadlines and keeps `expiresAt`
  semantically overloaded.
- Store signed lifetime metadata in another cookie. Rejected because it would create a second
  state-bearing browser artifact, complicate rotation and revocation, and require key management.
- Query or mutate Identity session state on every product request. Rejected because it would
  introduce cross-service latency and availability coupling merely to model idle activity.
- Let each frontend infer its own refresh window. Rejected because browser clocks and refresh
  policies differ and cannot provide an authoritative contract.
