# OpenAPI Support

Reusable transport mechanics for product services generated from OpenAPI contracts.

## MapStruct Conversions

Use `OpenApiNullableConversions` for common Java value and `JsonNullable` mappings. Use
`UriResponseConversions` when an application-owned string URI maps to a generated `URI` field.

Both classes are Spring components designed for MapStruct's `uses` configuration. Conversions
that reference product-generated models, product enums, or product application types remain in
the product module.

## PATCH Requests

Apply these mechanics only to true partial updates: omitted fields leave existing resource state
unchanged. Full replacement operations remain PUT, and named domain transitions remain workflow
POSTs. All operation shapes still keep generated requests at the transport boundary and map them
to application-owned inputs.

Use `PatchFields.update(JsonNullable<T>)` to convert generator-owned presence information
into the application-owned `FieldUpdate<T>` representation.

Every product follows the same boundary mechanics:

1. PATCH request schemas use OpenAPI nullable fields where absence and explicit `null` differ.
2. A product-local `*PatchAdapter` converts the generated request into an application input.
3. Nullable fields are converted with `PatchFields.update(...)`.
4. Product enums and collections are converted explicitly in the product adapter.
5. Product tests verify that each PATCH request and application input expose the same field names,
   so schema drift cannot silently leave an adapter incomplete.
6. Generated request types and product-specific adapters never move into this module.

Keep PATCH adapters separate from MapStruct transport mappers, including for currently simple
requests. This keeps the update path predictable if its presence semantics become richer later.

## Product Integration

OpenAPI Support owns lightweight generated-transport mechanics such as nullable
field conversion and common response conversions.

Products own their OpenAPI contracts, generated request/response models,
operation-specific adapters, enum conversions, and application inputs. There is
no HTTP client or product integration module here; products import
`openapi-support` only when the shared conversion helpers remove local mapping
noise.
