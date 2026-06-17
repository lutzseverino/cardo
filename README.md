<div align="center">
    <h1 align="center">Platform</h1>
    <p>Backend capabilities that can be shared by multiple products.</p>
    <p>
        <img alt="status" src="https://img.shields.io/badge/status-consolidated-0f172a">
        <img alt="backend" src="https://img.shields.io/badge/backend-spring_boot-111827">
        <img alt="docs" src="https://img.shields.io/badge/docs-diataxis-1f2937">
    </p>
</div>

## Overview

Platform contains backend capabilities that can be shared by multiple products.

It is the place for backend concerns that are reusable across product boundaries: shared service primitives, product-neutral mechanics, integrations, and contracts that more than one product can depend on.

## Boundary

Platform code should make products easier to build without absorbing product behavior.

Product-specific language, behavior, persistence, and policies should stay in the product that owns them. Platform modules should expose capabilities through clear contracts instead of making products reach into platform internals.

## Development

```bash
cd services
mvn validate
```

## Documentation

Durable platform docs live in [docs](docs/README.md).

## Modules

- [Authorization](authorization/README.md)
- [Billing](billing/README.md)
- [Common](common/README.md)
- [Identity](identity/README.md)
- [Invite](invite/README.md)
- [OpenAPI Support](openapi/README.md)
