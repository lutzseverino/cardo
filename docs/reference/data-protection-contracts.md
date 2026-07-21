# Data Protection Contracts

Cardo exposes small shared persistence contracts for audit timestamps and personal-data
classification. Retention periods and deletion behavior remain owned by the module that owns the
data until Cardo implements a real shared retention lifecycle.

## Contracts

- `AuditedEntity` owns creation and update timestamps.
- `PersonalDataEntity` is a method-free marker for entities containing personal data. It enables
  discovery and review but does not enforce persistence, access, retention, or deletion behavior.

Cardo does not currently publish generic retained-data entities, medical-data markers, or retention
policy implementations. Do not describe or depend on those concepts as shared runtime contracts
until their owner, lifecycle, and enforcement exist in code.

## Rules

- Apply `PersonalDataEntity` only as classification; do not infer a retention period or deletion
  policy from the marker.
- The owning module documents and enforces its required deletion, anonymization, restriction, and
  retention behavior, including database cascade decisions.
- Add a shared retention abstraction only when implemented behavior with a stable cross-module owner
  exists. Use neutral contract names for shared mechanics; product names remain with their owner.
