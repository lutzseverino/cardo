# Application Boundary Types

This document is authoritative for naming and placement of data types crossing transport, application, and persistence boundaries.

## Vocabulary

| Suffix | Owner | Role |
| --- | --- | --- |
| `...Request` | Transport contract | Carries inbound transport data. |
| `...Input` | Application | Carries structured input to an application operation. |
| `...Projection` | Persistence | Carries repository query output without exposing an entity. |
| `...Result` | Application | Carries output from an application operation. |
| `...Response` | Transport contract | Carries outbound transport data. |

Use ordinary parameters for simple identifiers, scalar values, and contextual data. Use `...Criteria` for a meaningful group of read filters, sorting, or pagination values.

Reserve `...Command` for a value dispatched through an explicit command execution model, such as a command bus and handler. A mutation payload is not a command merely because it changes state.

## Boundary Flow

```text
Request -> Input -> application operation -> Result -> Response
                         ^                 ^
                         |                 |
                   domain behavior   Projection
```

The first line applies to resource operations, queries, and workflow actions. Not every operation requires every type: bodyless operations may use ordinary parameters, and operations with no response body may return no result.

A projection is a persistence-owned source for a result, not a mandatory stage in every operation. Queries commonly map projections to results. Mutations and workflow actions may produce results from projections, domain values, or explicit operation outcomes.

Generated transport types do not cross into application service contracts. Repository projections do not cross out of the application layer. Application inputs and results are independent of transport generation and persistence query mechanics.

## Operation Shapes

- Resource operations create, retrieve, update, or remove a resource representation.
- Queries retrieve lists, summaries, reports, search results, or other read models without implying resource mutation.
- Workflow actions perform named domain transitions such as activate, cancel, restore, approve, reject, or complete.

Use operation-specific application names when generic CRUD names would hide domain meaning. For example, prefer `ActivateAccountInput` and `ActivationResult` over a generic update input for an activation workflow.

An action payload is still an `...Input`; it is not a `...Command` unless it participates in an explicit command execution model.

## Mapper Roles

- `...TransportMapper` maps `Request -> Input` and `Result -> Response`. It is used by the inbound transport adapter, such as a controller.
- `...ApplicationMapper` maps persistence or domain-owned output into an application result. `Projection -> Result` is the common query case. It is used by the application service coordinating the operation.
- A transport mapper must not accept or return projections, entities, or other persistence-owned types.
- An application mapper must not accept or return requests, responses, or other transport-owned types.
- Name a mapper for the boundary it crosses, not merely for the model subject.
- Do not combine transport and persistence mappings in one mapper.
- Do not map unrelated subjects in a mapper because a caller happens to need both.

Mapper interfaces contain declarative mapping methods only. Do not use default methods for orchestration, repository access, authorization, response assembly, or unrelated object construction. Put pure conversions that MapStruct cannot express in a dedicated helper referenced by the mapper.

Use strict MapStruct reporting for unmapped source and target properties. A mapper configuration may be local to each independently compiled module. Share it only through a dependency that all participating modules intentionally own. Mapping configuration belongs with mapping infrastructure; a general runtime `config` package is not required.

## Service Contracts

- Application services accept application-owned inputs and return application-owned results.
- Application services may call application mappers after repository queries. The mapper is the persistence-to-application adapter; a separate adapter class is unnecessary unless it owns additional behavior.
- Controllers accept requests, call transport mappers, invoke services, and map results to responses.
- Neither controller nor service mapping should contain business rules. Business rules remain in the application or domain operation.
- Entities remain internal to persistence and domain behavior unless an explicit architecture establishes them as application contracts.

## Types Outside The Flow

This vocabulary governs operation-boundary data. It does not require every type in a module to use one of these suffixes.

Use responsibility-specific vocabulary for domain value objects, domain events, validation errors, pagination metadata, security principals, audit records, integration DTOs, persistence entities, and infrastructure configuration.

If a type does not fit the operation flow, review its ownership, consumers, and crossed boundaries. Do not rename it to an operation-boundary type solely for uniformity.

## Collections And Wrappers

Return `List<ItemResponse>` when the transport response is only an unpaginated collection.

Introduce a named response wrapper only when it carries contract-level meaning or additional fields, such as pagination, cursors, totals, links, summaries, or multiple collections. Do not create a plural response type whose only field is a list.

## Partial Updates

Choose the transport operation from its semantics before applying partial-update mechanics:

- Use PATCH when the request changes a subset of a resource and omitted fields mean "leave unchanged."
- Use PUT when the request replaces the complete state addressed by the operation, including an
  idempotent value such as one caller's vote.
- Use a workflow POST for named domain transitions such as admit, activate, certify, or complete.

Do not convert PUT or workflow POST operations to PATCH solely to make modules look uniform. The
cross-module invariant is boundary ownership (`Request -> Input`), while `FieldUpdate` is the
additional mechanic used only when an operation has partial-update presence semantics.

Transport-specific presence types stay at the transport boundary. A product-local `...PatchAdapter` converts absent, explicit-null, and concrete values into an application-owned representation that preserves the distinctions required by the operation.

Do not leak generator-specific wrappers into application inputs.

Use `FieldUpdate<T>` for nullable fields in partial-update inputs when the operation must distinguish:

- `FieldUpdate.absent()` to leave the field unchanged.
- `FieldUpdate.present(null)` to clear the field.
- `FieldUpdate.present(value)` to assign a value.

Do not use `FieldUpdate` in create inputs, full replacements, non-nullable updates, results, or domain state.

Type-use validation constraints on `FieldUpdate<T>` apply to present values. The `common` module
registers the Jakarta Bean Validation value extractor for this behavior; product modules do not
register their own extractors.

OpenAPI-generated transports use `io.github.lutzseverino.cardo.openapi.patch.PatchFields` from the
`openapi-support` module to perform this conversion. Product-specific request-to-input adapters
remain in the product module because they own enum conversion and application input construction.

## Enumerated Values

Generated transport enums remain transport-owned. Map them to application or domain enums at the transport boundary, even when their current values are identical.

Do not configure transport generation to substitute an application or domain enum. Independent types allow either contract to evolve without making transport generation part of the application model.
