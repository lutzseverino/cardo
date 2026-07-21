# Repair Billing Customer Provisioning

Billing records a customer-provisioning operation before calling Stripe. A healthy request still
creates its Stripe customer and checkout or portal session synchronously. If the Stripe response is
ambiguous or Billing stops before saving the local mapping, the background reconciler searches for
the operation UUID stored in Stripe metadata as `cardo_provisioning_id`. It never creates a second
customer during recovery.

## Configuration

The recovery loop uses these settings:

| Property | Environment variable | Default |
| --- | --- | --- |
| `cardo.billing.customer-provisioning.dispatch-delay` | `BILLING_CUSTOMER_PROVISIONING_DISPATCH_DELAY` | `5s` |
| `cardo.billing.customer-provisioning.retry-base-delay` | `BILLING_CUSTOMER_PROVISIONING_RETRY_BASE_DELAY` | `10s` |
| `cardo.billing.customer-provisioning.claim-lease` | `BILLING_CUSTOMER_PROVISIONING_CLAIM_LEASE` | `1m` |
| `cardo.billing.customer-provisioning.max-attempts` | `BILLING_CUSTOMER_PROVISIONING_MAX_ATTEMPTS` | `6` |
| `cardo.billing.customer-provisioning.batch-size` | `BILLING_CUSTOMER_PROVISIONING_BATCH_SIZE` | `50` |

Keep the claim lease longer than the expected Stripe request timeout. Retries use exponential
backoff and stop at `max-attempts`; terminal rows remain in the database for inspection.
Checkout and portal requests remain unavailable with `billing_customer_create_failed` and HTTP
`502` while an operation is leased, waiting to retry, or terminal without a verified local mapping.
This prevents a customer-facing retry from replacing an ambiguous Stripe customer.

## Inspect A Failure

1. Find terminal operations and retain the operation, subject, and provider identifiers:

   ```sql
   SELECT id, subject_id, provider, attempt_count, last_error,
          remote_attempted_at, lease_token, provider_customer_id, created_at, updated_at
   FROM billing_customer_provisioning_operations
   WHERE status = 'FAILED'
   ORDER BY updated_at DESC;
   ```

2. In Stripe test or live mode matching the Billing deployment, search customer metadata for the
   exact `cardo_provisioning_id` value. Also correlate the structured Billing log field
   `operationId`.
3. Inspect the local rows for both uniqueness dimensions:

   ```sql
   SELECT id, subject_id, provider, provider_customer_id
   FROM billing_customers
   WHERE (subject_id = :subject_id AND provider = :provider)
      OR (provider = :provider AND provider_customer_id = :provider_customer_id);
   ```

Do not delete, replace, or merge Stripe customers automatically. More than one Stripe customer for
one marker, or a local mapping to a different customer, requires a billing-owner decision before
state is changed.

Each claim writes a new `lease_token`. Completion and failure acknowledgements must carry that exact
token while the operation is still `REQUESTED`; an expired worker is logged with its `leaseToken`
and ignored. This fencing prevents a late timeout or response from overwriting a newer worker's
completion.

All current terminal causes are unsafe for automatic reprovisioning: an exhausted search can still
follow a lost create response, duplicate marker matches need a canonical-customer decision, and a
local uniqueness mismatch may identify an existing owner. Repeated checkout or portal requests
therefore reuse the terminal operation and return `502`; they cannot create a new marker.

## Repair One Unambiguous Orphan

Only use this repair when the exact marker has one Stripe result and neither uniqueness query maps
the subject or Stripe customer differently. Run the mapping insert and operation completion in one
database transaction, substituting the inspected values:

```sql
BEGIN;

SELECT id, subject_id, provider, status
FROM billing_customer_provisioning_operations
WHERE id = :operation_id
  AND subject_id = :subject_id
  AND provider = :provider
  AND status = 'FAILED'
FOR UPDATE;

INSERT INTO billing_customers (subject_id, provider, provider_customer_id)
SELECT :subject_id, :provider, :provider_customer_id
FROM billing_customer_provisioning_operations
WHERE id = :operation_id
  AND subject_id = :subject_id
  AND provider = :provider
  AND status = 'FAILED'
ON CONFLICT DO NOTHING;

UPDATE billing_customer_provisioning_operations
SET status = 'COMPLETED',
    provider_customer_id = :provider_customer_id,
    completed_at = now(),
    last_error = NULL,
    next_attempt_at = now(),
    updated_at = now(),
    version = version + 1
WHERE id = :operation_id
  AND subject_id = :subject_id
  AND provider = :provider
  AND status = 'FAILED'
  AND EXISTS (
    SELECT 1
    FROM billing_customers
    WHERE subject_id = :subject_id
      AND provider = :provider
      AND provider_customer_id = :provider_customer_id
  );

COMMIT;
```

Verify that exactly one operation row was updated. A zero-row update means the evidence changed or
the supplied values do not match; roll back the investigation rather than forcing the mapping.

## Reset Only A Proven Non-Creation

Use a fresh operation only when repeated exact-marker searches after Stripe's search-propagation
window return no customer, Stripe request logs show no successful create, and no local mapping
exists. Preserve the failed row as evidence and explicitly stage a new UUID:

```sql
BEGIN;

SELECT id, subject_id, provider, status, remote_attempted_at, last_error
FROM billing_customer_provisioning_operations
WHERE id = :failed_operation_id
  AND status = 'FAILED'
FOR UPDATE;

INSERT INTO billing_customer_provisioning_operations
  (id, subject_id, provider, status, attempt_count, next_attempt_at)
SELECT :new_operation_id, failed.subject_id, failed.provider, 'REQUESTED', 0, now()
FROM billing_customer_provisioning_operations failed
WHERE failed.id = :failed_operation_id
  AND failed.status = 'FAILED'
  AND NOT EXISTS (
    SELECT 1
    FROM billing_customers customer
    WHERE customer.subject_id = failed.subject_id
      AND customer.provider = failed.provider
  )
RETURNING id, subject_id, provider;

COMMIT;
```

Require exactly one returned row. The partial unique index also rejects this reset if another active
operation appeared. The scheduler or next user request may then claim the explicitly staged
operation; Billing still will not derive a new marker from the failed row on its own.

## Inspect Customers Created Before This Migration

Older Stripe customers do not have `cardo_provisioning_id`; they only carry `subject_id`. Search
Stripe by the exact subject metadata and compare every result with `billing_customers`, Stripe
request logs, and creation timestamps. If there is exactly one unambiguous orphan and no conflicting
local mapping, insert that mapping in a transaction using the same uniqueness checks above, without
updating a provisioning operation. If there are multiple candidates, record the evidence and have a
billing owner choose the canonical customer. This runbook intentionally provides no automatic
customer deletion or merge procedure.
