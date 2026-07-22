# Keep Authorization Embedded Until It Owns HTTP

## Status

Accepted

## Context

HTTP-owning modules publish OpenAPI contracts. Their controllers implement generated interfaces, and client types are generated from the same contract.

Authorization currently provides reusable Java primitives for authenticated user reading, permission evaluation, access profiles, authorization planning, and synchronization. It does not own controllers or an HTTP runtime.

The embedded aggregate is intentionally cohesive for Cardo services, but outbound HTTP adapters and
product authentication use only API-error translation, token acquisition, and Spring Security
mechanics. Pulling persistence, Keycloak administration, durable grants, migrations, and Modulith
through those consumers makes the resolved dependency graph dishonest.

## Decision

Keep authorization as an embedded Java library boundary until it owns HTTP endpoints.

Keep the full authorization artifact as the compatibility aggregate for services that embed
Authorization. Publish authorization-keycloak-client for client-credential and requesting-party
token mechanics, and authorization-security for Spring Security and JWT mechanics. Keep the
existing Java packages and public types so consumers change only their Maven dependency.

The HTTP client adapters use common-api for the existing API error contracts without acquiring the
full common aggregate. The full common artifact depends on common-api for source and binary
compatibility.

Do not add an OpenAPI spec, generated HTTP client, or generated TypeScript API types before that boundary exists.

If authorization becomes a standalone service, introduce the HTTP contract deliberately as part of that extraction.

## Consequences

- Only HTTP-owning modules publish OpenAPI contracts.
- Services keep using authorization through direct Java dependencies.
- HTTP client and product-auth consumers select the smallest embedded leaf that owns their runtime
  mechanics; build enforcement prevents the full aggregates and persistence infrastructure from
  returning transitively.
- Excessive dependency weight is addressed with finer-grained embedded artifacts, not an
  Authorization HTTP facade for a service that does not exist.
- A future service extraction must design the HTTP API rather than inheriting one accidentally.

## Alternatives Considered

- Add an OpenAPI spec now. Rejected because there is no HTTP owner for it.
- Extract authorization into a service now. Rejected because the embedded boundary is sufficient for current use.
