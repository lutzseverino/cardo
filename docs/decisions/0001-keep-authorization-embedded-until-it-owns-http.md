# Keep Authorization Embedded Until It Owns HTTP

## Status

Accepted

## Context

HTTP-owning modules publish OpenAPI contracts. Their controllers implement generated interfaces, and client types are generated from the same contract.

Authorization currently provides reusable Java primitives for authenticated user reading, permission evaluation, access profiles, authorization planning, and synchronization. It does not own controllers or an HTTP runtime.

## Decision

Keep authorization as an embedded Java library boundary until it owns HTTP endpoints.

Do not add an OpenAPI spec, generated HTTP client, or generated TypeScript API types before that boundary exists.

If authorization becomes a standalone service, introduce the HTTP contract deliberately as part of that extraction.

## Consequences

- Only HTTP-owning modules publish OpenAPI contracts.
- Services keep using authorization through direct Java dependencies.
- A future service extraction must design the HTTP API rather than inheriting one accidentally.

## Alternatives Considered

- Add an OpenAPI spec now. Rejected because there is no HTTP owner for it.
- Extract authorization into a service now. Rejected because the embedded boundary is sufficient for current use.
