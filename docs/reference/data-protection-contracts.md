# Data Protection Contracts

Entities that store personal, medical, financial, or otherwise retained data should expose that responsibility through shared contracts instead of inventing local deletion fields.

## Contracts

- `AuditedEntity` owns creation and update timestamps.
- `PersonalDataEntity` marks an entity as containing personal data.
- `RetainedDataEntity` owns archive, restriction, retention, and purge state.
- `MedicalRecordEntity` marks retained data as medical record data.
- `DataRetentionPolicy` calculates retention deadlines and reasons.
- `MedicalRetentionPolicy` provides medical record retention behavior.

## Rules

- Retained personal, medical, or financial data must not be physically deleted by normal product flows.
- Retained data must not sit behind database cascades that can erase it through parent deletion.
- Use archive when active use stops but retention is still required.
- Use restriction when the record remains present but normal processes should not use it.
- Use purge or anonymization only from a dedicated retention process after retention eligibility is established.
- Use neutral contract names for shared mechanics. Product names belong where the product owns the behavior.
