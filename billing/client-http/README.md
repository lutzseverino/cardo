# Billing HTTP Client

Billing HTTP Client implements the stable `billing-client` contract over HTTP.
It contains generated transport integration and Spring Boot auto-configuration
while keeping those mechanics out of product code.

## Product Integration

Products combine `billing-client` with `billing-client-http`, configure the
Billing base URL, and inject the stable client interface. Product-specific
entitlement policy remains in the consuming product.

## Development

Build and validate the project from the repository root:

```bash
./mvnw --projects billing/client-http --also-make verify
```

## Documentation

Start with the [Billing HTTP Client documentation index](docs/README.md).
Cross-project architecture and conventions remain in the
[Cardo documentation](../../docs/README.md).
