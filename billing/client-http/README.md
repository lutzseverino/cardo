# Billing HTTP Client

Billing HTTP Client implements the stable `billing-client` contract over HTTP.
It contains generated transport integration and Spring Boot auto-configuration
while keeping those mechanics out of product code.
Its runtime uses only `common-api` and `authorization-keycloak-client`, so it
does not bring Cardo persistence, migrations, JDBC, or Modulith into products.

## Product Integration

Products combine `billing-client` with `billing-client-http`, configure
`cardo.billing.client.base-url` with Billing's `/api/v1` base URL, and inject the
stable client interface. They must also set
`cardo.billing.client.service-token-scope=billing`. The value is the optional
Keycloak client scope that emits a token whose only audience is `billing`;
missing or blank values fail auto-configuration. The auto-configuration requires a
`KeycloakClientCredentialsTokenProvider` bean. The shared provider reuses its
scope-specific service token until shortly before Keycloak's reported expiry.

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
