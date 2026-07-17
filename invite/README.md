# Invite

Invite owns cross-product invitation delivery and lifecycle mechanics: secret
tokens, fixed creation-time expiry, provisional identity completion, revocation, and durable staging
of the access grants captured when an invitation is created.

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
client identifier is the product identifier and must match the prefix of every
resource type and access-profile name supplied by that product. Token lookup is
public; identity completion, acceptance, and revocation remain product-service
operations.

Invite authorizes product callers positively. Configure
`cardo.invite.product-callers.allowed-client-ids` (or
`INVITE_PRODUCT_CLIENT_IDS`) with the OAuth client identifiers that may call
Invite. Each client's Keycloak service account must also hold the dedicated
`cardo-invite` client role `product-service`, which appears as the
`cardo-invite:product-service` authority. Configure the Keycloak audience
mapper so those tokens also include `cardo-invite` in `aud`. Do not grant that
role to end users. Invite rejects callers that lack the audience, role, or
allowlist entry; the absence of an end-user claim is not treated as
service-token proof.

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
product's database transaction and makes failure retryable. Invite acceptance
atomically marks its own record accepted and stages the captured grant snapshot
for asynchronous, idempotent authorization application.

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
`SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, and
`INVITE_MAIL_FROM` for the deployment. Local defaults target an SMTP catcher on
`localhost:1025`; delivery failures remain durable and are retried.

Identity completion intentionally does not accept the invitation. A completion
request first persists a saga operation and returns `202`; the product polls the
same completion resource until it is `completed` or `failed`. Invite dispatches
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

## Documentation

Start with the [Invite documentation index](docs/README.md). Cross-project
architecture and conventions remain in the [Cardo documentation](../docs/README.md).
