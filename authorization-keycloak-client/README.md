# Authorization Keycloak Client

The authorization-keycloak-client artifact owns the Keycloak client-credentials provider and
settings, requesting-party-token client, and token request/value contracts. Existing packages,
constructors, and behavior are unchanged.

This leaf is for outbound authentication mechanics only. It contains no provider administration,
access profiles, durable grants, persistence, migrations, or Spring Security resource-server
configuration. It introduces no configuration keys; host applications continue to construct the
provider or consume existing Cardo auto-configuration.

Public surface:

- `KeycloakClientCredentialsTokenProvider`
- `KeycloakClientCredentialsTokenSettings`
- `KeycloakAuthorizationException`
- `KeycloakRequestingPartyTokenClient`
- `RequestingPartyTokenClient` and its request, permission, and token value types
