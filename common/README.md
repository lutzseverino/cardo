# Common

Common owns small shared Java contracts that are not product behavior: API error
types, data-retention markers, shared value objects, validation helpers, and
cookie helpers.

## Product Integration

Products import `common` directly when they need these low-level contracts.
There is no service owner, HTTP client, or product integration module.

Keep product-specific validation, lifecycle decisions, and domain vocabulary in
the product. Common should stay boring and narrow.

## Documentation

Start with the [Common documentation index](docs/README.md). Cross-project
architecture and conventions remain in the [Cardo documentation](../docs/README.md).
