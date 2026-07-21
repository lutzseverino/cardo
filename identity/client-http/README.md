# Identity HTTP Client

Identity HTTP Client implements the stable `identity-client` contract over
HTTP. It contains generated transport integration and Spring Boot
auto-configuration while keeping those mechanics out of product code.

## Product Integration

Products combine `identity-client` with `identity-client-http`, configure
`cardo.identity.client.base-url` with Identity's `/api/v1` base URL (for example,
`https://identity.internal/api/v1`), and inject the stable `IdentityUsersClient`
interface. The auto-configuration also requires a
`KeycloakClientCredentialsTokenProvider` bean configured with the Keycloak base
URL, realm, service-account client ID, and client secret. It obtains a bearer
token for Identity requests; the shared provider reuses that token until shortly
before Keycloak's reported expiry. The service account must carry the Identity
audience and authority required by the invoked operation. Product-specific
Identity policy remains in the consuming product.

Outbound Identity calls default to two-second connection and response timeouts.
Override them with `cardo.identity.client.connect-timeout` and
`cardo.identity.client.read-timeout` when the deployment requires different
bounded values.

## Development

Build and validate the project from the repository root:

```bash
./mvnw --projects identity/client-http --also-make verify
```

## Documentation

Start with the [Identity HTTP Client documentation index](docs/README.md).
Cross-project architecture and conventions remain in the
[Cardo documentation](../../docs/README.md).
