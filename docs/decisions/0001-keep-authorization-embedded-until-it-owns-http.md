# Keep Authorization Embedded Until It Owns HTTP

## Status

Accepted

## Context

HTTP-owning modules publish OpenAPI contracts. Their controllers implement generated interfaces, and client types are generated from the same contract.

Authorization currently provides reusable Java primitives for authenticated user reading, permission evaluation, access profiles, authorization planning, and synchronization. It does not own controllers or an HTTP runtime.

The embedded artifact is intentionally cohesive today, but consumers should not have to pull materially unused persistence, Keycloak administration, or Spring infrastructure indefinitely.

## Decision

Keep authorization as an embedded Java library boundary until it owns HTTP endpoints.

If its embedded dependency footprint becomes too broad for consumers, split it into smaller responsibility-based Java libraries while preserving the embedded boundary.

Do not add an OpenAPI spec, generated HTTP client, or generated TypeScript API types before that boundary exists.

If authorization becomes a standalone service, introduce the HTTP contract deliberately as part of that extraction.

## Consequences

- Only HTTP-owning modules publish OpenAPI contracts.
- Services keep using authorization through direct Java dependencies.
- Excessive dependency weight should be addressed with finer-grained embedded artifacts, not a client facade for a service that does not exist.
- A future service extraction must design the HTTP API rather than inheriting one accidentally.

## Alternatives Considered

- Add an OpenAPI spec now. Rejected because there is no HTTP owner for it.
- Extract authorization into a service now. Rejected because the embedded boundary is sufficient for current use.
