# Identity Product Auth

Spring Security integration for products that accept logged-in Odonta users.

The module provides auto-configuration for the shared product auth mechanics:
JWT authority conversion, resource permission evaluation, authenticated-user
reading, method security, OAuth2 resource-server setup, and bearer token lookup
from the Identity session cookie.

## Configuration

```yaml
odonta:
  identity:
    product-auth:
      session-cookie-name: odonta.session
      public-paths:
        - ${odonta.api.base-path}/workforce
```

The default public paths are health, info, OpenAPI, and Swagger UI. Product
status or onboarding routes should be listed explicitly in `public-paths`.

This module does not define product resources, product grants, entitlement
rules, invite lifecycles, or tenant lookup. Those remain product-owned.
