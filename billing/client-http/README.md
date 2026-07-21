# Billing HTTP Client

Billing HTTP Client implements the stable `billing-client` contract over HTTP.
It contains generated transport integration and Spring Boot auto-configuration
while keeping those mechanics out of product code.

## Product Integration

Products combine `billing-client` with `billing-client-http`, configure
`cardo.billing.client.base-url` with Billing's `/api/v1` base URL, and inject the
stable client interface. The auto-configuration requires a
`KeycloakClientCredentialsTokenProvider` bean. The shared provider reuses its
service token until shortly before Keycloak's reported expiry.

Outbound Billing calls default to two-second connection and response timeouts.
Override them with `cardo.billing.client.connect-timeout` and
`cardo.billing.client.read-timeout` when the deployment requires different
bounded values. Product-specific entitlement policy remains in the consuming
product.

## Development

Build and validate the project from the repository root:

```bash
./mvnw --projects billing/client-http --also-make verify
```

## Documentation

Start with the [Billing HTTP Client documentation index](docs/README.md).
Cross-project architecture and conventions remain in the
[Cardo documentation](../../docs/README.md).
