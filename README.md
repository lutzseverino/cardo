<div align="center">
  <h1>Cardo</h1>
  <p>Shared application services and integration libraries for independent product repositories.</p>

  [![CI](https://github.com/lutzseverino/cardo/actions/workflows/ci.yml/badge.svg)](https://github.com/lutzseverino/cardo/actions/workflows/ci.yml)
  [![License: MIT](https://img.shields.io/badge/license-MIT-2f3437)](LICENSE)
</div>

Cardo provides the product-neutral service capabilities used by Odonta, Polity,
and future applications. It owns stable identity and billing contracts,
authorization mechanics, HTTP client implementations, and the small shared
libraries that make those boundaries consistent.

Products remain independently deployable and own their domain language,
persistence, APIs, permissions, and lifecycle decisions. Cardo does not absorb
product behavior or provide a generic plugin framework.

> [!IMPORTANT]
> Cardo is currently a `0.x` platform. Its contracts are shared by active
> products, but releases may still include coordinated migrations while the
> repository family settles.

## Capabilities

| Capability | Ownership |
| --- | --- |
| Identity | Application users, profiles, provider integration, sessions, and stable user clients |
| Authorization | Resource vocabulary, permission evaluation, durable grant staging, and provider adapters |
| Invite | Cross-product invitation tokens, expiry, provisional identity completion, and access-profile grant staging |
| Billing | Customers, Stripe checkout and portal integration, entitlements, and stable billing clients |
| Common | Shared API errors, audit and personal-data markers, value objects, and validation |
| OpenAPI Support | Reusable generated-transport and PATCH conversion helpers |

## Product Contract

Products consume the narrow client artifacts they need and keep policy local.
Identity and Billing expose transport-independent client interfaces with
separate HTTP implementations. Authorization is embedded because grant staging
participates in the product transaction that produces the grant.

See the [product integration reference](docs/reference/product-integration.md)
for artifact selection, configuration, and ownership rules.

## Development

Cardo targets Java 21 and Spring Boot 4.

```bash
mvn test
mvn verify
mvn install
```

`mvn install` makes the current snapshot available to sibling product
repositories during local development. CI validates formatting, compilation,
generated OpenAPI boundaries, and the complete Maven test suite.

## Documentation

Start with the [documentation index](docs/README.md). Documentation is organized
by reader intent so durable guidance has one predictable home.

## License

Cardo is available under the [MIT License](LICENSE).
