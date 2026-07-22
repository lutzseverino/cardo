<div align="center">
  <h1>Cardo</h1>
  <p>Shared application services and integration libraries.</p>

  [![CI](https://github.com/lutzseverino/cardo/actions/workflows/ci.yml/badge.svg)](https://github.com/lutzseverino/cardo/actions/workflows/ci.yml)
  [![License: MIT](https://img.shields.io/badge/license-MIT-2f3437)](LICENSE)
</div>

Cardo provides reusable identity, authorization, invitation, and billing
capabilities. It owns stable service contracts, HTTP client implementations,
and the shared libraries that keep those boundaries consistent.

Consuming applications own their domain language, persistence, APIs,
permissions, and lifecycle decisions. Cardo does not absorb application
behavior or provide a generic plugin framework.

## Capabilities

| Capability | Ownership |
| --- | --- |
| Identity | Application users, profiles, provider integration, sessions, and stable user clients |
| Authorization | Resource vocabulary, permission evaluation, durable grant staging, and provider adapters |
| Invite | Resource-scoped invitation delivery, expiry, provisional identity completion, acceptance, and revocation lifecycle |
| Billing | Customers, Stripe checkout and portal integration, entitlements, and stable billing clients |
| Common API | API error contracts and outbound client error translation |
| Common | Audit and personal-data markers, value objects, validation, server error handling, and compatibility aggregation |
| OpenAPI Support | Reusable generated-transport and PATCH conversion helpers |

## Integration Contract

Applications consume the narrow client artifacts they need and keep policy
local. Identity, Invite, and Billing expose transport-independent client interfaces
with separate HTTP implementations. Authorization is embedded because grant
staging participates in the application transaction that produces the grant.

See the [integration reference](docs/reference/product-integration.md)
for artifact selection, configuration, and ownership rules.
Deployments consume the executable JAR and OCI image process contract described
in the [runtime artifact reference](docs/reference/runtime-artifacts.md).
Released consumers align supported libraries through the
[`cardo-bom` and immutable release contract](docs/reference/releases.md).
Maintainers can exercise those boundaries together through the unpublished
[portable reference stack](integration/reference-stack/README.md).

## Development

Cardo targets Java 21 and Spring Boot 4.

```bash
./mvnw test
./mvnw verify
./mvnw install
```

`./mvnw install` installs the current development snapshot locally. Products
consume published versions from Maven Central; local installation is not a
release path. CI validates formatting, compilation, generated OpenAPI
boundaries, and the complete Maven test suite.

## Documentation

Start with the [documentation index](docs/README.md). Documentation is organized
by reader intent so durable guidance has one predictable home.
Operators can inspect and repair persisted background work through the
[durable workflow operations guide](docs/how-to/operate-durable-workflows.md).

## License

Cardo is available under the [MIT License](LICENSE).
