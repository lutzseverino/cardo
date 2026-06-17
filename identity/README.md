# Identity

Identity owns Odonta users, authentication, sessions, and the authenticated
principal exposed to products.

## Product Integration

Products use `identity-client` as the stable Java contract for user lookup and
provisional-user flows. `identity-client-http` provides the HTTP implementation
and Spring Boot auto-configuration for service-to-service calls to Identity.

Products that accept logged-in Odonta users use `identity-product-auth`. It
auto-configures the shared Spring Security pieces for product services:

- JWT authority conversion
- permission evaluation
- authenticated-user reading
- method security
- OAuth2 resource-server setup
- session-cookie bearer token resolution

Products still own product access. Identity must not silently grant clinic,
workforce, or other product-domain permissions as a side effect of user creation
or login.

Example product configuration:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:${odonta.workforce.keycloak.base-url}/realms/${odonta.workforce.keycloak.realm}}

odonta:
  identity:
    product-auth:
      public-paths:
        - ${odonta.api.base-path}/workforce
```
