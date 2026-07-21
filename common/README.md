# Common

Common owns small shared Java contracts that are not product behavior: API error
types, personal-data markers, shared value objects, and validation helpers.

## Product Integration

Products import `common` directly when they need these low-level contracts.
There is no service owner, HTTP client, or product integration module.

Keep product-specific validation, lifecycle decisions, and domain vocabulary in
the product. Common should stay boring and narrow.

## Partial-Update Values

`FieldUpdate<T>` is the application-owned representation for a nullable partial-update field when
absence, explicit `null`, and a concrete value have different meanings. Do not use it for creates,
complete replacements, non-nullable updates, results, or persisted domain state.

Type-use Jakarta Bean Validation constraints apply to present values. `common` registers the
`FieldUpdateValueExtractor`; consuming modules do not register their own extractors.

For example:

```java
record ProfilePatch(FieldUpdate<@Size(max = 100) String> biography) {}
```

An absent update is not passed to its element constraints. A present value, including an explicit
`null`, follows each constraint's normal null semantics: `@Size` allows null, while constraints such
as `@NotNull` and `@NotBlank` reject it.

Generated transport presence remains outside this module. OpenAPI-based products use
`openapi-support` to convert generated nullable wrappers into `FieldUpdate<T>` inside a
product-owned patch adapter.

## Documentation

Start with the [Common documentation index](docs/README.md). Cross-project
architecture and conventions remain in the [Cardo documentation](../docs/README.md).
