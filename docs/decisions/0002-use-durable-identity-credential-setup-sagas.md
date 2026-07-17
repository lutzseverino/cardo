# Use Durable Identity Credential-Setup Sagas

## Status

Accepted

## Context

Invitation completion currently spans Invite's database, Identity's database, and Keycloak. Those
resources cannot participate in one atomic transaction. Completing or deleting a Keycloak user
inside a local transaction leaves an unrecoverable ambiguity when the remote request succeeds but
the local commit fails.

Persisting a password for background retry would turn a transient credential into durable
application data. Cardo must not make that security tradeoff.

Product invitation acceptance is a separate domain transition. Completing credentials must not
implicitly accept a product invitation or apply product grants.

## Decision

Keycloak owns password and profile setup through its execute-actions flow. Cardo invitation APIs do
not accept, persist, log, or relay a password.

Invite and Identity each persist an idempotent operation before crossing a process boundary. The
operation row is the durable saga state and dispatch work item for that service:

- Invite records a credential-setup request keyed by the invitation id, dispatches it to Identity,
  and reconciles Identity's operation status.
- Identity records the same operation id, dispatches Keycloak's credential-setup action, polls the
  provider for completion, and activates the local user only after provider state is verified.
- Provisional-user cancellation records a durable deletion operation. Provider deletion treats a
  missing user as success, and the local user is removed only after provider deletion succeeds.
- Dispatch and reconciliation run outside local database transactions. Short transactions claim,
  reschedule, complete, or fail work using optimistic concurrency and retry leases.
- Cross-service requests are idempotent. A repeated operation id with different ownership data is
  rejected.
- Invite serializes operation creation on the invitation row, so concurrent equivalent requests
  resolve to the same operation rather than leaking a uniqueness failure.
- The invitation's hard expiry is persisted and propagated to Identity. It caps the Keycloak
  action lifetime and prevents reconciliation from activating a user after the invitation expires.
- Product services continue to accept invitations explicitly after their own domain transition.

Operations retry transient failures with bounded exponential backoff. Non-retryable provider and
cross-service 4xx failures become terminal immediately; request timeout, too-early, and rate-limit
responses remain retryable. Exhausted operations retain their error and terminal state for
inspection and explicit retry; they are not silently discarded.
Credential setup also persists the Keycloak action deadline. An incomplete operation becomes a
terminal, inspectable failure when the action expires, and an explicit idempotent retry issues a
fresh action instead of polling forever.

## Consequences

- Database commits and remote effects no longer pretend to be atomic.
- A lost HTTP response, process crash, or transaction rollback can be reconciled from durable state.
- Cardo no longer handles invitation passwords.
- Credential setup becomes asynchronous and clients poll an operation resource.
- Keycloak may deliver a duplicate action email after an ambiguous crash; the action itself and the
  Cardo operation are idempotent.
- Credential-setup redirect URIs and action lifetimes become Identity configuration.
- Product invitation acceptance and authorization staging remain independently retryable.

## Alternatives Considered

- Store an encrypted password in an outbox. Rejected because Cardo would retain a reusable secret
  and key compromise would expose queued credentials.
- Compensate completed credentials. Rejected because the previous password cannot be recovered.
- Rely only on caller retries. Rejected because abandoned requests would leave divergence without a
  durable owner or reconciliation path.
- Use a distributed transaction across PostgreSQL and Keycloak. Rejected because Keycloak does not
  participate in Cardo's transaction manager and the coupling would remain operationally fragile.
