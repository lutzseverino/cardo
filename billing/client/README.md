# Billing Client API

Billing Client API is the stable, transport-independent Java contract for
services that query Cardo Billing entitlements. It exposes billing-owned state
without deciding how a product applies limits to its domain workflows.

## Product Integration

Products depend on `billing-client` for entitlement checks. Pair it with
`billing-client-http` when the product should use Cardo's standard HTTP
implementation and Spring Boot auto-configuration.

## Development

Build and validate the project from the repository root:

```bash
./mvnw --projects billing/client --also-make verify
```

## Documentation

Start with the [Billing Client API documentation index](docs/README.md).
Cross-project architecture and conventions remain in the
[Cardo documentation](../../docs/README.md).
