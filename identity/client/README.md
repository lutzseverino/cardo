# Identity Client API

Identity Client API is the stable, transport-independent Java contract for
services that call Cardo Identity. It owns user lookup and provisional-user
types without exposing HTTP or generated transport details to consumers.

## Product Integration

Products depend on `identity-client` when they need Identity operations. Pair it
with `identity-client-http` when the product should use Cardo's standard HTTP
implementation and Spring Boot auto-configuration.

## Development

Build and validate the project from the repository root:

```bash
./mvnw --projects identity/client --also-make verify
```

## Documentation

Start with the [Identity Client API documentation index](docs/README.md).
Cross-project architecture and conventions remain in the
[Cardo documentation](../../docs/README.md).
