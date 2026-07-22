# Billing

Billing owns commercial access: customers, entitlements, checkout sessions,
portal sessions, and provider webhook ingestion.

## Product Integration

Products use `billing-client` as the stable Java contract for entitlement
checks. It returns billing-owned entitlement state; products decide how product
limits apply to their domain workflows. `billing-client-http` provides the HTTP
implementation and Spring Boot auto-configuration for service-to-service calls
to Billing.

There is no product integration module yet. Add one only if real products repeat
the same entitlement guard, checkout, or portal wiring beyond the existing
client modules.

Products still own product catalog meaning, tenant limits in domain workflows,
and the decision about which product operation requires an entitlement. Billing
must not silently grant product-domain access.

## Documentation

Set `BILLING_RUNTIME_MODE=production` with a remote HTTPS Keycloak issuer, Stripe credentials and
catalogue, and a Billing-owned PostgreSQL database and role. Stripe and issuer/JWK calls have
positive finite connection and response bounds; Stripe automatic network retries are disabled.
See the indexed [runtime property reference](../docs/reference/runtime-properties.md).

Start with the [Billing documentation index](docs/README.md). Cross-project
architecture and conventions remain in the [Cardo documentation](../docs/README.md).
