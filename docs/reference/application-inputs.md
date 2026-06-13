# Application Inputs

This document is authoritative for the placement of structured inputs to Spring application services.

## Vocabulary

- `...Request` represents an interaction with an external system or transport boundary, such as a search request, protocol request, or request envelope.
- `...Input` is a structured operation payload. OpenAPI mutation body schemas use this name when the generated type is also consumed by the implementing application service.
- `...Criteria` is a structured application read selection.
- Ordinary parameters carry simple identifiers and scalar inputs.
- `...Projection` and explicit result values carry application read output.

Criteria are plain records owned by the service module that consumes them. They do not imply query handlers, buses, dispatchers, marker interfaces, or CQRS infrastructure.

## Naming Semantics

Name a type for the role it actually plays, not for the fact that an operation changes state.

- Use `...Request` for explicit boundary or integration requests that are not shared operation payloads.
- Use `...Input` for generated OpenAPI mutation bodies and application-owned structured mutation inputs.
- Reserve `...Command` for a command abstraction that participates in an explicit command execution model, such as dispatch to a handler. Do not use it as a synonym for request, input, mutation DTO, or service argument.
- Use `...Criteria` for grouped read selection and `...Projection` or `...Result` for application output.

OpenAPI schema names should describe the role of the represented value. Mutation body schemas shared with application services use `...Input`; boundary-specific searches, envelopes, and protocol interactions may use `...Request`. They must not use `...Command` merely because the operation is a mutation.

## Mutation Inputs

Use the generated OpenAPI input directly when it already represents the application mutation payload. Do not introduce a local record that repeats the same fields, nullability, and structural validation.

Keep identifiers, current-user context, and route context as ordinary service parameters when they select or contextualize the resource being changed. Identifier-only operations such as cancel, archive, restore, accept, reject, and delete do not require input wrappers.

OpenAPI owns request shape and reusable structural validation. Services continue to own authorization, stateful validation, domain rules, persistence behavior, and defenses for schema rules that the Java generator cannot express as Bean Validation.

Introduce a local `...Input` only when it materially differs from the generated API input, combines multiple input sources, hides generated quirks from a reusable non-HTTP service, or represents an application operation not owned by an OpenAPI contract.

## Criteria

Use criteria when a read has a meaningful group of filters, sorting, or pagination values. Keep simple reads as ordinary service parameters instead of wrapping every query in a criteria record.

## Partial Updates

Generated partial-update inputs use `JsonNullable<T>` where a field must distinguish unchanged, explicit null, and a concrete value.

Do not mirror it with a command record or common wrapper unless a distinct application or domain abstraction emerges.

## Responses

Generated response models remain at controller or outbound mapper boundaries. Application services return domain entities, projections, or explicit result values rather than generated response DTOs.

## Domain Types

When an OpenAPI schema represents an existing domain enum, configure the Java generator to reuse that domain type. Do not generate and manually translate a second Java enum for the same values. The OpenAPI schema remains the external contract definition, while the domain enum remains the runtime business and persistence type.
