# Identity HTTP Client

Identity HTTP Client implements the stable `identity-client` contract over
HTTP. It contains generated transport integration and Spring Boot
auto-configuration while keeping those mechanics out of product code.

## Product Integration

Products combine `identity-client` with `identity-client-http`, configure the
Identity base URL, and inject the stable client interface. Product-specific
Identity policy remains in the consuming product.

## Development

Build and validate the project from the repository root:

```bash
./mvnw --projects identity/client-http --also-make verify
```

## Documentation

Start with the [Identity HTTP Client documentation index](docs/README.md).
Cross-project architecture and conventions remain in the
[Cardo documentation](../../docs/README.md).
