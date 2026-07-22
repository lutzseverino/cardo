# Authorization Security

The authorization-security artifact owns Cardo's authenticated-user reader, JWT claim and validator
mechanics, resource permission evaluator, authority names, and Keycloak JWT authority converter.
Existing packages and public types are unchanged.

This leaf exposes only Spring Security and JWT mechanics. It contains no Keycloak HTTP client,
provider administration, access profiles, durable grants, persistence, or migrations, and adds no
configuration keys or beans.

Public surface:

- `AuthenticatedUser` and `AuthenticatedUserReader`
- `AuthorizationAuthorityNames` and `CardoJwtClaims`
- `ExactAudienceValidator` and `RequiredExpirationValidator`
- `ResourcePermissionEvaluator`
- `KeycloakAuthoritiesConverter`
