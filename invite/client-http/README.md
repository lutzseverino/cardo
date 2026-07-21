# Invite HTTP Client

Invite HTTP Client implements the stable `invite-client` contract over HTTP.
It contains generated transport integration and Spring Boot auto-configuration
while keeping those mechanics out of product code.

## Product Integration

Products combine `invite-client` with `invite-client-http`, configure
`cardo.invite.client.base-url` with Invite's `/api/v1` base URL, and set the
required `cardo.invite.client.service-token-scope=cardo-invite` property. The
scope is the optional Keycloak client scope that emits a token whose only
audience is `cardo-invite`; missing or blank values fail auto-configuration.

The auto-configuration requires a `KeycloakClientCredentialsTokenProvider`
bean and requests only the configured scope. The provider reuses the
scope-specific service token until shortly before Keycloak's reported expiry.
The calling service account still needs Invite's `product-service` client role
and an entry in Invite's product-caller allowlist.

Outbound Invite calls default to two-second connection and response timeouts.
Override them with `cardo.invite.client.connect-timeout` and
`cardo.invite.client.read-timeout` only with positive bounded values.

## Development

Build and validate the project from the repository root:

```bash
./mvnw --projects invite/client-http --also-make verify
```

## Documentation

Start with the [Invite HTTP Client documentation index](docs/README.md).
Cross-project architecture and conventions remain in the
[Cardo documentation](../../docs/README.md).
