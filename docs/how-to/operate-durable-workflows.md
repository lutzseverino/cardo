# Operate Durable Workflows

Use this guide to inspect, correlate, recover, and retain Cardo's persisted
background work. Each service owns its queries and repair decision. Cardo does
not expose administrative workflow endpoints, and the default management route
surface remains `health,info`; deployments decide how the internal Micrometer
registry is exported.

## Inventory

| Workflow | Owner and store | Active states | Terminal state | Correlation |
| --- | --- | --- | --- | --- |
| Credential setup and provisional deletion | Identity, `identity_operations` | `REQUESTED`, `AWAITING_USER` | `FAILED` | `id` |
| Password/provisional provisioning, subject binding, enabled state | Identity, `identity_provider_mutations` | `REQUESTED` | `FAILED` plus `terminal_reason` | `id`; provisioning also has `correlation_marker` |
| Invitation completion | Invite, `invitation_completion_operations` | `REQUESTED`, `AWAITING_IDENTITY` | `FAILED`; `REVOKED` is the terminal #22 lifecycle state | `id` / `invitation_id` |
| Invitation delivery | Invite, `invite_events.event_publication` | incomplete `invite.invitation-delivery` publication | none; retried indefinitely | publication `id` and invitation id in the serialized event |
| Grant convergence | Embedded Authorization, `<service>_events.grant_receipt` and `.event_publication` | `PENDING` receipt/incomplete `authorization.grant-plan` publication | receipt `FAILED` | receipt `id` |
| Revocation convergence | Embedded Authorization, `<service>_events.event_publication` | incomplete `authorization.revocation-plan` publication | none; retried indefinitely | publication `id` |
| Customer provisioning | Billing, `billing_customer_provisioning_operations` | `REQUESTED` | `FAILED` | `id` and Stripe `cardo_provisioning_id` |

The `cardo.durable.workflow.work` gauge reports complete persisted counts with
bounded `workflow`, `type`, and `state` tags. States are `active`, `actionable`,
and `terminal` where the owner has a terminal failure. The
`cardo.durable.workflow.oldest.actionable.age` gauge reports seconds since the
oldest actionable row was created or published. The
`cardo.durable.workflow.processing` counter uses only `success`, `retry`,
`stale-ack`, and `terminal` outcomes. No operation id, user id, email, token,
provider customer id, or error text is a metric tag.

## Inspect Identity

```sql
SELECT id, operation_type, status, attempt_count, next_attempt_at,
       lease_token, last_error, expires_at, created_at, updated_at
FROM identity_operations
WHERE status IN ('REQUESTED', 'AWAITING_USER', 'FAILED')
ORDER BY status, next_attempt_at, created_at;

SELECT id, mutation_type, status, attempt_count, next_attempt_at, lease_until,
       provider_subject, correlation_marker, terminal_reason, last_error,
       created_at, updated_at
FROM identity_provider_mutations
WHERE status IN ('REQUESTED', 'FAILED')
ORDER BY status, next_attempt_at, created_at;
```

Identity operation and Invite completion claims write a new opaque
`lease_token` and set or replace `next_attempt_at` with the new lease deadline;
that column is their combined scheduling and lease field. Provider mutations
already use a token plus a separate `lease_until`. Identity and Invite accept
only the current, unexpired token. Provider mutations accept the current token
while the row is `REQUESTED`; `lease_until` controls when another worker may
reclaim it but does not by itself invalidate the current acknowledgement. After
a crash an Identity or Invite row becomes actionable at `next_attempt_at`; a
provider mutation becomes actionable when `next_attempt_at` is due and
`lease_until` is null or elapsed. A later claim changes the token, so the
earlier worker is counted and logged as `stale-ack` and cannot overwrite the
new result.

For credential setup or provisional deletion, repair the provider/local cause,
then repeat the existing idempotent request while its owner still permits it.
Never mark an operation complete before verifying the corresponding Keycloak
and local user state. For provider mutations, follow the exact correlation
marker guidance in the [Identity overview](../../identity/README.md). A password
mutation with `CREDENTIAL_RESUBMISSION_REQUIRED` requires a fresh caller-held
password; Cardo never persists one. Ambiguous marker matches, provider/local
subject disagreement, or any proposed deletion is an Identity-owner escalation.

### Migrate active provisioning ownership to V6

V6 permits only one active password or provisional provisioning owner for a
normalized email. Before rollout, run this count-only preflight; do not select,
log, or attach email values to deployment output:

```sql
SELECT count(*) AS conflicting_active_provisioning_owners
FROM (
  SELECT 1
  FROM identity_provider_mutations
  WHERE mutation_type IN ('PROVISION_PASSWORD_USER', 'PROVISION_PROVISIONAL_USER')
    AND status = 'REQUESTED'
  GROUP BY normalized_email
  HAVING count(*) > 1
) conflicts;
```

If the count is nonzero, V6 stops before replacing the V5 index and preserves
all rows. Keep Identity stopped and inspect the conflicting work only in the
restricted database session. This query emits mutation evidence without email
values:

```sql
WITH conflicting_emails AS (
  SELECT normalized_email
  FROM identity_provider_mutations
  WHERE mutation_type IN ('PROVISION_PASSWORD_USER', 'PROVISION_PROVISIONAL_USER')
    AND status = 'REQUESTED'
  GROUP BY normalized_email
  HAVING count(*) > 1
)
SELECT mutation.id, mutation.mutation_type, mutation.status,
       mutation.correlation_marker, mutation.provider_subject,
       mutation.attempt_count, mutation.created_at, mutation.updated_at
FROM identity_provider_mutations mutation
JOIN conflicting_emails conflict USING (normalized_email)
ORDER BY mutation.created_at;
```

For each conflict, correlate both mutation IDs with Keycloak by their exact
markers and determine the valid owner from provider and local evidence. Do not
delete either row or choose a winner by age. After an explicit Identity-owner
decision, terminalize only the invalid request with the appropriate existing
terminal reason and retain it as audit evidence. Back up the database, record
the decision by mutation ID only, repeat the count-only preflight, and apply V6
only when it returns zero.

## Inspect Invite

```sql
SELECT id, invitation_id, status, attempt_count, next_attempt_at, lease_token,
       expires_at, action_expires_at, last_error, created_at, updated_at
FROM invitation_completion_operations
WHERE status IN ('REQUESTED', 'AWAITING_IDENTITY', 'FAILED', 'REVOKED')
ORDER BY status, next_attempt_at, created_at;

SELECT id, listener_id, publication_date, completion_attempts,
       last_resubmission_date
FROM invite_events.event_publication
WHERE listener_id = 'invite.invitation-delivery'
  AND completion_date IS NULL
ORDER BY publication_date;
```

Invitation completion uses the same opaque-token fencing and restart behavior
as Identity operations. Repair Identity reachability or the recorded terminal
cause, then repeat the completion request only while the invitation remains
pending and unexpired. `REVOKED` is final locally. A claim committed before
revocation may already have crossed Identity; do not claim Cardo recalled a
Keycloak action, delete the shared provisional user, or reopen the revoked row.
Delivery is at least once: repair SMTP and let the existing publication retry;
do not clone or complete the publication manually. The sender must tolerate a
duplicate after an ambiguous response.

## Inspect Authorization

Substitute the service-owned schema, such as `identity_events` or
`invite_events`.

```sql
SELECT id, status, failure_code, attempt_count, created_at, updated_at
FROM identity_events.grant_receipt
WHERE status IN ('PENDING', 'FAILED')
ORDER BY status, created_at;

SELECT id, listener_id, publication_date, completion_attempts,
       last_resubmission_date
FROM identity_events.event_publication
WHERE listener_id IN ('authorization.grant-plan', 'authorization.revocation-plan')
  AND completion_date IS NULL
ORDER BY listener_id, publication_date;
```

Repair provider access and let incomplete publications retry. A `FAILED` grant
receipt exhausted its bounded attempts and its publication is complete. Verify
provider state, then have the owning product stage a new idempotent plan and
track the new receipt; do not rewrite the old receipt. Revocations have no
failed receipt and retry indefinitely. Preserve #22 semantics: a pending or
failed invitation grant receipt is not converged access, while legacy
`UNKNOWN` remains distinct from `PENDING`, `APPLIED`, and `FAILED`.

Staged grant transitions log their `receiptId`, bounded outcome, and reason.
Legacy grant plans and revocation plans do not carry a receipt or Modulith
publication id in their listener payload, so their processing logs cannot add
an equivalent identifier without broadening those event contracts. Correlate
them through the incomplete-publication query, listener id, and publication
time. Adding a durable correlation field to those contracts is deliberately
deferred rather than changing compatibility in this operations slice.

## Inspect Billing

Use the focused [Billing customer provisioning runbook](repair-billing-customer-provisioning.md).
It defines exact Stripe-marker inspection, lease-token fencing, unambiguous
mapping repair, and escalation. Do not create a replacement customer merely
because a row is delayed or terminal.

## Correlate Logs

Search structured logs by `operationId`, `receiptId`, or `id` in Identity
provider-mutation messages, then constrain by the bounded workflow type,
outcome, and reason.
Logs intentionally omit emails, invitation secrets, session or service tokens,
lease tokens, provider subjects, provider customer ids, and raw upstream error
bodies. Inspect sensitive provider data only in the owning system with its
normal access controls.

## Retention

- Keep Identity operations and provider mutations indefinitely by default.
  They fence idempotency, carry provider-effect evidence, and support audits.
- Keep invitation completion rows for at least as long as their invitation
  history. Never delete active, failed, or revoked evidence. Product-owned grant
  convergence has its own receipt retention policy.
- Keep grant receipts while an owning lifecycle can reference them. A failed
  receipt and every incomplete publication are repair evidence.
- Keep Billing provisioning rows indefinitely by default; they prevent a
  second Stripe customer after an ambiguous create.
- Completed event publications may be removed only through the owning Spring
  Modulith completion-retention policy after the related receipt/domain history
  is retained elsewhere. Never delete incomplete publications.

Before any cleanup, take a database backup, restrict the selection to completed
rows older than the service's approved audit horizon, prove no foreign key or
domain lifecycle references them, and compare counts before and after. Cardo
does not ship a cross-module cleanup job because each owner has different
idempotency and audit obligations.

## Verification

After repair, confirm active/actionable counts and oldest age fall, the terminal
count changes only through an explicit owner action, and a processing outcome
counter increments. Re-run the inspection query and verify the provider/local
state independently. Business backlog must not be wired into liveness or cause
restart loops.
