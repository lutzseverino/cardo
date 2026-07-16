# Identity

Identity owns Cardo users, authentication, sessions, and the authenticated
principal exposed to products. Identity also owns global user status: disabling
an Identity user disables that subject in the identity provider and ends
provider sessions, without changing product-local memberships or grants.

## Product Integration

Products use `identity-client` as the stable Java contract for user lookup and
provisional-user flows. `identity-client-http` provides the HTTP implementation
and Spring Boot auto-configuration for service-to-service calls to Identity.

Products that accept logged-in Cardo users use `identity-product-auth`. It
auto-configures the shared Spring Security pieces for product services:

- JWT authority conversion
- permission evaluation
- authenticated-user reading
- method security
- OAuth2 resource-server setup
- session-cookie bearer token resolution

Products still own product access. Identity must not silently grant product-domain
permissions as a side effect of user creation
or login. Products issue product grants from their own flows by staging
`GrantPlan` instances with product-owned client authorities and resource
actions.

Example product configuration:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:${product.keycloak.base-url}/realms/${product.keycloak.realm}}

cardo:
  identity:
    product-auth:
      public-paths:
        - ${product.api.base-path}/status
```
