# Application Boundary Data Flow

## Purpose

Explain why transport, application, and persistence data shapes are distinct and how values move between them without accumulating arbitrary mapper exceptions.

## Overview

Each boundary owns the shape that describes its concerns:

```text
transport       application              domain / persistence
Request ------> Input ------> operation <---- domain model
                              |
Response <------ Result <-----+-------------- Projection
```

The application layer is the stable center. Transport contracts may be regenerated, and persistence queries may change for performance or storage reasons, without forcing either concern into the application service contract.

This model intentionally accepts small mapping classes even when two shapes currently look identical. The ceremony makes ownership visible and prevents an accidental structural similarity from becoming a long-lived coupling.

The model is operation-oriented, not CRUD-specific. Resource operations, composite queries, and named workflow transitions use the same transport and application boundaries. They differ in how the operation behaves and where its result comes from.

## Key Concepts

### Types Express Ownership

A request or response belongs to a transport contract. An input or result belongs to an application operation. A projection belongs to a repository query.

These names describe architectural roles, not generic data direction. `Input` does not mean every inbound object, and `Result` does not mean every returned object.

### Mappers Express Boundary Crossings

Two mapper roles are sufficient for the normal flow:

- The transport mapper translates between transport-owned and application-owned types.
- The application mapper translates persistence projections into application results.

The service invokes the application mapper because it coordinates the repository and works in application results. This does not make the service itself the adapter; the mapper performs the adaptation and the service decides when it occurs.

The controller invokes the transport mapper because it owns the inbound transport interaction. Neither mapper needs to know the types on the other side of the application layer.

Concretely, a transport mapper must not accept or return projections, and an application mapper must not accept or return requests or responses. When a mapper needs both sets of types, it has crossed two boundaries and should be split.

### Projections Are Query Shapes

A projection should stay close to the data selected by its repository query. It may resemble the result, but it is allowed to change when the query changes. The result instead represents what the application operation promises to callers.

Mapping directly from projection to response collapses persistence and transport concerns. Returning projections from services exposes repository choices as application contracts. Returning responses from services exposes transport generation as an application dependency.

A projection is not required for every result. A workflow may produce an explicit outcome or map updated domain state into a result. A bodyless action may produce no result at all. Requiring a projection in those cases would turn a common query mechanism into a universal abstraction it cannot honestly represent.

### CRUD Is One Operation Family

CRUD vocabulary is appropriate when the operation genuinely creates, reads, updates, or removes a resource. It becomes misleading when an operation represents a domain transition, such as activation, cancellation, restoration, approval, rejection, or completion.

Named workflows should keep their domain verbs in endpoint operation names, application methods, and operation-specific inputs or results. They still follow `Request -> Input` and `Result -> Response`; they do not need a separate architectural pattern.

Queries also extend beyond CRUD reads. Summaries, reports, searches, and composite views may use projections designed for those query shapes and map them into operation-specific results.

### Types Outside The Flow

The boundary flow governs data entering and leaving application operations. It is not a naming system for every class in a module.

Domain value objects, domain events, validation errors, pagination metadata, security principals, audit records, integration DTOs, persistence entities, and infrastructure configuration have their own responsibilities. They should use vocabulary faithful to those responsibilities rather than being renamed to `Request`, `Input`, `Projection`, `Result`, or `Response` merely to fit the operation flow.

When a type does not fit the flow, review what it owns, who consumes it, and which boundary it crosses. That review may reveal a legitimate supporting concept or an unclear responsibility. It should not begin with automatic renaming.

### Assembly Is Not A Default Layer

A separate assembler is useful only when producing a result or response requires meaningful composition across multiple sources. Renaming a mapper to assembler, or adding an assembler that only wraps a list, adds vocabulary without adding responsibility.

For a plain unpaginated collection, a direct list communicates the shape more clearly than a plural wrapper. A wrapper earns its existence when the contract includes metadata or a distinct collection resource.

### Applying The Flow

For a create operation, a typical path is:

```text
CreateResourceRequest -> CreateResourceInput -> service -> ResourceResult -> ResourceResponse
```

For a repository-backed query, the persistence side participates without becoming part of the
service contract:

```text
repository -> ResourceProjection -> ApplicationMapper -> ResourceResult
```

For a partial update, the transport adapter preserves field presence before invoking the same
application boundary:

```text
UpdateResourceRequest -> ResourcePatchAdapter -> UpdateResourceInput -> service
```

The patch adapter is omitted for create, complete replacement, and workflow operations. Those
operations still use transport mappers to cross between requests or responses and application
inputs or results.

## Implications

- Generated transport types remain replaceable and do not define application service APIs.
- Repository queries can optimize their projections without leaking those choices to controllers.
- Mapping code is split by boundary, so a mapper does not accumulate unrelated types or orchestration.
- Supporting domain and infrastructure concepts retain vocabulary appropriate to their own responsibilities.
- Strict mapping checks make shape changes fail at compile time instead of silently dropping data.
- Small features carry some repetitive mapping ceremony. Consistency is preferred because the same mechanics continue to work when a feature grows.
- A dedicated repository adapter layer may be introduced when it owns meaningful persistence behavior, but it is not required solely to hide an application mapper call.

The authoritative vocabulary and rules are listed in [Application Boundary Types](../reference/application-boundary-types.md).
