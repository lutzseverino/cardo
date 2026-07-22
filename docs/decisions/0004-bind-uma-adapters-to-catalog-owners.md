# Bind UMA Adapters To Catalog Owners

## Status

Accepted

## Context

A provider adapter that accepts an arbitrary resource-server client ID while
using one protection token can accidentally mutate a different UMA catalog.
Identity also used one OAuth client for both constrained realm administration
and Identity-catalog protection. Invite captured product access profiles and
grants, making a platform lifecycle service an implicit owner of product policy
and convergence.

## Decision

Every `KeycloakAuthorizationClient` is constructed for one resource-server
client ID. Each operation retains and presents that owner, including revocation;
the adapter rejects a mismatch before acquiring either credential or making a
network request. Protection API operations use the bound catalog PAT. Client
lookup and user role assignment use a separate realm-admin token.

Identity materializes exactly two confidential clients: `cardo-identity` for
runtime user/realm-admin work and `identity` for the Identity authorization
catalog. Their secrets are distinct. The `identity` service account has exactly
its automatic `uma_protection` role and no realm-management authority.

Invite owns only invitation delivery, completion, acceptance, expiry, and
revocation. Products own invitation grant plans and their receipt/convergence
APIs. Invite's pre-release authorization persistence is retained under explicit
legacy names but has no active runtime meaning.

## Consequences

- Cross-catalog calls fail locally before token acquisition or network I/O.
- Deployment provisioning must provide two Identity clients and secrets and
  verify their positive and negative authority boundaries.
- Products stage invitation grants in their own transactions and expose their
  own convergence state.
- Invite's OpenAPI, Java client, runtime dependencies, and active model no longer
  contain product grant or convergence contracts.
- Existing V1–V5 Invite data remains auditable after the V6 rename migration.

## Alternatives Considered

- Keep one broad Identity service account. Rejected because catalog protection
  and realm administration have different owners and authority requirements.
- Select catalog IDs dynamically through a registry. Rejected because it hides
  ownership and introduces a generic product framework without a demonstrated
  need.
- Keep Invite's grant snapshot as a compatibility alias. Rejected because this
  is a pre-release breaking correction and the alias would preserve the wrong
  policy owner.
