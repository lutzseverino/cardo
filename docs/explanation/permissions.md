# Permissions

## Purpose

Explain how permission ownership should work across platform and product services.

## Overview

Authorization should be permission-based across platform and product services. Products may define product-specific permission names, but they should not define separate permission systems.

Each product owns its own permission catalog. The shared authorization boundary provides mechanics: token conversion, resource naming helpers, grant synchronization, permission evaluation, and provider integration.

## Key Concepts

- Product services own product language: resources, actions, grant templates, and the events that create or revoke access.
- The authorization boundary owns reusable mechanics and provider-facing integration.
- Identity owns identity behavior. It should not silently grant product access as a side effect of user creation.
- Runtime checks should evaluate explicit grants rather than contextual fallbacks such as "self or admin" unless those fallbacks are modeled as grants.

## Grant Shapes

Operational permissions have an implied target. For example, a profile read permission targets the authenticated principal.

Resource permissions have an explicit target. They should follow a stable shape such as:

```text
<product>:<resource>:<resource-id>:<action>
```

Wildcard resource grants should use the same shape with a wildcard resource id:

```text
<product>:<resource>:*:<action>
```

## Rules

- Product-owned domain constraints stay in product services.
- Permission checks answer whether a user may perform an operation type.
- Product data still determines which records or tenants the operation applies to.
- Do not create parallel membership or invitation models when a synchronized grant is the durable access record.
- Keep shared authorization code free of product-specific catalogs.

## Implications

- Products can evolve their permission catalogs without turning platform into a global product-policy registry.
- Platform permission mechanics can be reused without depending on product-specific persistence or behavior.
- Access flows should create or revoke durable grants instead of relying on hidden request-time exceptions.
