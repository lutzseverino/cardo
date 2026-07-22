# Migrate Invite Authorization Ownership

This pre-release breaking correction moves invitation grant planning,
application, receipt retention, and convergence from Invite to each product that
owns the corresponding resource catalog.

## Caller changes

1. Remove `accessProfile` and `grants` from Invite create requests and generated
   client models. Keep the product-owned tenant resource type.
2. Remove calls to Invite's
   deleted `/invitations/{invitationId}/grant-convergence` route and the deleted
   `InvitationGrantConvergenceClient`.
3. Define a product-local revocation plan and retain the resource-server owner
   through `Revocations.stage(...)`. A catalog-bound Keycloak adapter rejects a
   different owner before acquiring a token or sending a request.

## Product-owned acceptance sequence

Use this ordering for every invitation-driven product transition:

`product acceptance intent + durable command`
`-> idempotent Invite acceptance`
`-> product transaction stages its plan, stores its receipt, and completes its transition`
`-> product convergence API`
`-> fresh product-audience RPT`
`-> usable access`

The durable command retains the original acceptance timestamp. If the worker
crashes after Invite accepts but before the product transaction commits, retry
the idempotent Invite call and then commit the product transition, plan, and
receipt together. Invite records only its own accepted lifecycle state.

## Maintenance cutover

1. Back up and inventory accepted invitations, Invite receipts, incomplete
   plans, legacy grant snapshots, and settled publications.
2. Pause invitation acceptance while old Invite-owned work is drained.
3. Settle every old Invite grant publication and record any terminal failure.
4. Backfill product-owned plans and receipts from product-owned snapshots; do
   not use an old Invite receipt as proof of product access.
5. Verify each backfill with a fresh matching-audience product RPT containing
   the expected permission.
6. Upgrade callers and Invite together to the breaking OpenAPI and Java
   contracts.
7. Resume acceptance through the product-owned sequence above.
8. Remove obsolete provider resources, permissions, service accounts, or
   credentials only after audit and retention requirements are satisfied.

Do not add a compatibility endpoint or a runtime dual-write during this
cutover.

## Invite datastore

Run Invite migration V6 after V5. It renames the pre-release authorization
evidence without copying or dropping it:

| V5 name | V6 legacy evidence name |
| --- | --- |
| `invitations.grant_receipt_id` | `invitations.legacy_grant_receipt_id` |
| `invitations.access_profile` | `invitations.legacy_access_profile` |
| `invitations.invited_authorization_subject` | `invitations.legacy_invited_authorization_subject` |
| `invitation_grants` | `legacy_invitation_grants` |

Invite no longer reads or writes these fields. Preserve them for audit and a
product-specific migration; do not treat them as an active fallback contract.

## Verification

- Invite's OpenAPI and Java client expose no grant input, access profile, or
  convergence route/type.
- Invite accepts, revokes, delivers, and completes invitations without the full
  Authorization aggregate on its runtime classpath.
- Product grant receipts reach `APPLIED`, and the next uncached product UMA
  exchange returns an RPT whose sole audience is that product's resource server.
- A populated V5 database upgrades to V6 with receipt and grant evidence intact
  under the exact legacy names above.
