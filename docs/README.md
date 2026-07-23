# Documentation

Documentation is organized by reader intent.

## Sections

- `how-to/` solves focused operational or implementation tasks.
- `reference/` captures factual lookup material.
- `explanation/` records concepts, rationale, and architecture.
- `decisions/` stores architecture decision records.
- `_templates/` contains starting points for new documents.

## Writing Rules

- Choose the section by reader intent, not by topic.
- Keep documents durable and reader-oriented.
- Keep examples concrete only when they clarify durable guidance.
- Do not document ephemeral notes or implementation checklists as durable docs.
- Link new documents from the nearest section README.
- Use the templates in `_templates/` when creating a new document.

## Documents

- [How-To Guides](how-to/README.md)
- [Reference](reference/README.md)
- [Explanation](explanation/README.md)
- [Decisions](decisions/README.md)
- [Templates](_templates/README.md)

## Projects

Each separately packaged Maven project owns a left-aligned README and a local
documentation index. Repository-wide architecture and conventions remain here.

| Project | Role | Documentation |
| --- | --- | --- |
| [Common](../common/README.md) | Shared Java contracts | [Docs](../common/docs/README.md) |
| [Common API](../common/api/README.md) | API error contracts and client translation | [Docs](../common/api/docs/README.md) |
| [OpenAPI Contracts](../cardo-openapi-contracts/README.md) | Public versioned service contracts | [Docs](../cardo-openapi-contracts/docs/README.md) |
| [OpenAPI Support](../openapi-support/README.md) | Generated-transport mechanics | [Docs](../openapi-support/docs/README.md) |
| [Authorization](../authorization/README.md) | Embedded authorization mechanics | [Docs](../authorization/docs/README.md) |
| [Authorization Keycloak Client](../authorization/keycloak-client/README.md) | Token acquisition and exchange | [Docs](../authorization/keycloak-client/docs/README.md) |
| [Authorization Security](../authorization/security/README.md) | Spring Security and JWT mechanics | [Docs](../authorization/security/docs/README.md) |
| [Identity](../identity/README.md) | Identity service | [Docs](../identity/docs/README.md) |
| [Identity Client API](../identity/client/README.md) | Stable Identity client contract | [Docs](../identity/client/docs/README.md) |
| [Identity HTTP Client](../identity/client-http/README.md) | Identity HTTP implementation | [Docs](../identity/client-http/docs/README.md) |
| [Identity Product Auth](../identity/product-auth/README.md) | Product authentication integration | [Docs](../identity/product-auth/docs/README.md) |
| [Invite](../invite/README.md) | Invitation service | [Docs](../invite/docs/README.md) |
| [Billing](../billing/README.md) | Billing service | [Docs](../billing/docs/README.md) |
| [Billing Client API](../billing/client/README.md) | Stable Billing client contract | [Docs](../billing/client/docs/README.md) |
| [Billing HTTP Client](../billing/client-http/README.md) | Billing HTTP implementation | [Docs](../billing/client-http/docs/README.md) |
| [Reference Stack](../integration/reference-stack/README.md) | Unpublished executable product fixture | [Docs](../integration/reference-stack/docs/README.md) |
