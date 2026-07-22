# Invite

Invite owns cross-product invitation delivery and lifecycle mechanics: secret
tokens, fixed creation-time expiry, provisional identity completion, acceptance,
and revocation. Invite does not own product access profiles, grant snapshots,
grant application, or authorization convergence.

## Product Integration

Backend product services depend on `invite-client` and add
`invite-client-http` at runtime. The HTTP implementation is auto-configured with
the existing Cardo client-credentials token provider:

```yaml
cardo:
  invite:
    client:
      base-url: ${INVITE_BASE_URL:http://localhost:8083/api/v1}
```

Authenticated Invite operations accept product service tokens only. The OAuth
client identifier is the product identifier and must match the prefix of the
tenant resource type supplied by that product. Token lookup is
public; identity completion, acceptance, and revocation remain product-service
operations.

Invite authorizes product callers positively. Configure
`cardo.invite.product-callers.allowed-client-ids` (or
`INVITE_PRODUCT_CLIENT_IDS`) with the OAuth client identifiers that may call
Invite. Each client's Keycloak service account must also hold the dedicated
Invite resource-server client role `product-service`. With the default
`cardo.invite.keycloak.client-id=cardo-invite`, it appears as the
`cardo-invite:product-service` authority. Configure the Keycloak audience mapper so the token's
only audience is that same configured client ID. If the client ID is customized, both the role
namespace and exact audience change with it. Do not grant that role to end users. Invite rejects
callers that lack the audience, role, or allowlist entry; the absence of an end-user claim is not
treated as service-token proof.

Products still own why an invitation exists and every domain consequence of
acceptance. For a product-owned invitation record, use its UUID as Invite's
`requestId`. Persist that record and a durable integration command in the same
product transaction, then call `InvitationsClient.create(...)` after commit.
Creation is idempotent for `(product, requestId)`, including concurrent retries;
Invite serializes that key with a PostgreSQL transaction-scoped advisory lock
before provisioning Identity or inserting the invitation.

Provisional identities are reusable by normalized email. Invite deliberately
does not delete one when its local creation transaction fails: Identity may
have returned an existing provisional identity already referenced by another
invitation. A later create retry safely reuses it.

Apply the same ordering to acceptance: commit the product's membership or other
domain transition together with a durable command, then call
`InvitationsClient.accept(...)`. This keeps a remote Invite call out of the
product's database transaction and makes failure retryable. The product stages
its own authorization plan and retains/exposes its own receipt alongside that
domain transition. Invite acceptance only atomically marks Invite's lifecycle
record accepted; it has no grant-convergence route or convergence client.

Pass the timestamp committed with the product-domain acceptance to
`InvitationsClient.accept(...)`. Invite validates expiry against that durable
business timestamp rather than the later retry time, so an outage does not
change a transition that was valid when the product committed it. Invite rejects
timestamps outside the invitation creation/current-time window, allowing
`cardo.invite.invitation.acceptance-clock-skew` (configured as `5m`) for service
clock differences.

Invitation delivery is also staged transactionally and processed at least once
after commit. Incomplete publications, including crash-interrupted and failed work, are retried using
`cardo.invite.delivery.retry-delay` (default `PT1M`); sender implementations
must tolerate a duplicate delivery when a failure occurs after the provider has
already accepted a message.

The production adapter sends through SMTP. Configure `SMTP_HOST`, `SMTP_PORT`,
`SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`,
`SMTP_CONNECT_TIMEOUT`, `SMTP_READ_TIMEOUT`, `SMTP_WRITE_TIMEOUT`, and
`INVITE_MAIL_FROM` for the deployment. Local defaults target an SMTP catcher on
`localhost:1025`; delivery failures remain durable and are retried.

Identity completion intentionally does not accept the invitation. A completion
request first persists a saga operation and returns `202`; the product polls the
same completion resource until it is `completed`, `failed`, or `revoked`. Invite dispatches
the stable operation id to Identity, and Identity asks Keycloak to deliver its
own password/profile action. Cardo never accepts, persists, logs, or relays the
invitation password.

Both services claim work in short database transactions and perform remote
calls outside those transactions. Leases, bounded exponential retry, and the
shared operation id cover process crashes and lost responses. The Keycloak
action deadline is persisted and exposed by the completion resource. It is
capped by the invitation's hard expiry, so asynchronous setup cannot activate a
user after the invitation expires. An abandoned setup becomes an inspectable
failed operation instead of polling forever, and repeating the same completion
request explicitly restarts it with a fresh Keycloak action. Product acceptance
remains a separate domain transition after authentication.

Revocation terminates existing queued or polling completion work locally as
`REVOKED`. Invite serializes revocation and worker claims on the invitation row:
when revocation commits before claim, Invite does not call Identity; when claim
commits first, dispatch may proceed and an already-issued Keycloak action link
may still be completed. Invite does not cancel a provider action, cancel or
delete the shared provisional Identity user, or undo global Identity
activation. Later worker callbacks preserve the terminal `REVOKED` completion,
which remains readable after invitation revocation.

Migration V6 preserves pre-release authorization columns and rows as explicitly
named legacy evidence (`legacy_grant_receipt_id`, `legacy_access_profile`,
`legacy_invited_authorization_subject`, and `legacy_invitation_grants`). Invite
does not interpret or update that evidence after the migration.

## Documentation

Set `INVITE_RUNTIME_MODE=production` with the remote Keycloak/issuer and Identity client, an
authenticated STARTTLS SMTP service, at least one product caller, and an Invite-owned PostgreSQL
database with distinct no-login owner and login application roles. Startup verifies the effective
database and roles after Flyway completes. See the indexed
[runtime property reference](../docs/reference/runtime-properties.md) for the full contract.

Start with the [Invite documentation index](docs/README.md). Cross-project
architecture and conventions remain in the [Cardo documentation](../docs/README.md).
