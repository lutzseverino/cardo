# Application Inputs

This document is authoritative for naming and placement of structured inputs to Spring application services.

## Vocabulary

- `...Request` is a generated HTTP contract input and stays at the controller or HTTP mapper boundary.
- `...Command` is a structured application mutation input.
- `...Criteria` is a structured application read selection.
- Ordinary parameters carry simple identifiers and scalar inputs.
- `...Projection` and explicit result values carry application read output.

Commands and criteria are plain records owned by the service module that consumes them. They do not imply command handlers, buses, dispatchers, marker interfaces, or CQRS infrastructure.

## Commands

Name a command after the application action performed by its receiving service, such as `AuthenticateCommand`, `CreatePatientCommand`, or `UpdateClinicCommand`.

Create a command when a mutation has a meaningful structured payload. Keep identifiers as ordinary service parameters when they select the resource being changed. Identifier-only operations such as cancel, archive, restore, accept, reject, and delete do not require command records.

Commands own reusable structural validation at the service boundary. Services continue to own stateful and domain validation.

## Criteria

Use criteria when a read has a meaningful group of filters, sorting, or pagination values. Keep simple reads as ordinary service parameters instead of wrapping every query in a criteria record.

## Partial Updates

Clinic partial-update commands use `JsonNullable<T>` where a field must distinguish unchanged, explicit null, and a concrete value. This is the established three-state carrier already produced by the generated contract.

Do not mirror it with a common wrapper unless a distinct application or domain abstraction emerges. Generated request classes themselves still remain outside application services.
