# Cardo OpenAPI Contracts

This contract-only artifact publishes the authoritative Identity, Invite,
Billing, and shared-error OpenAPI documents under
`META-INF/cardo/openapi/{common,identity,invite,billing}/openapi/`.

The documents retain their repository-relative layout so external references
remain resolvable after extraction. The artifact contains no generated code or
runtime helpers. Import `cardo-bom` and depend on
`cardo-openapi-contracts` without declaring a separate version.
