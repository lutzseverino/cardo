package io.github.lutzseverino.cardo.identity.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakRealmAdminClient;
import io.github.lutzseverino.cardo.identity.config.KeycloakProperties;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.client.RestClient;

public final class RealKeycloakIdentityProviderExercise {

  private RealKeycloakIdentityProviderExercise() {}

  public static void verify(
      KeycloakProperties properties,
      KeycloakClientCredentialsTokenProvider runtimeTokens,
      RestClient.Builder rest) {
    KeycloakRealmAdminClient admin =
        new KeycloakRealmAdminClient(
            properties.baseUrl(),
            properties.realm(),
            rest.clone(),
            runtimeTokens::clientCredentialsToken);
    IdentityProvider provider =
        new KeycloakIdentityProvider(properties, admin, runtimeTokens, rest.clone());

    IdentityProvider.ProvisionedIdentity passwordIdentity =
        provider.provisionPasswordIdentity(
            "password-user@example.test",
            "S3cure-cardo-password!",
            "Password User",
            "marker-password");
    assertThat(passwordIdentity.subject()).isNotBlank();
    assertThat(provider.findIdentityByCorrelationMarker("marker-password"))
        .contains(passwordIdentity);
    assertThat(provider.completedIdentityProfile(passwordIdentity.subject()))
        .contains(new IdentityProvider.CompletedIdentityProfile("Password User"));
    assertThatCode(
            () ->
                provider.bindUserId(
                    passwordIdentity.subject(),
                    UUID.fromString("11111111-1111-1111-1111-111111111111")))
        .doesNotThrowAnyException();

    Map<String, Object> storedUser =
        rest.clone()
            .baseUrl(properties.baseUrl())
            .build()
            .get()
            .uri(
                "/admin/realms/{realm}/users/{subject}",
                properties.realm(),
                passwordIdentity.subject())
            .header(
                org.springframework.http.HttpHeaders.AUTHORIZATION,
                "Bearer " + runtimeTokens.clientCredentialsToken())
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<>() {});
    assertThat(storedUser.get("requiredActions")).as(storedUser.toString()).isEqualTo(List.of());

    IdentityProvider.IssuedSession session =
        provider.issuePasswordSession("session-user@example.test", "S3cure-cardo-password!");
    assertThat(session.subject()).isNotBlank();
    IdentityProvider.IssuedSession refreshed = provider.refreshSession(session.refreshToken());
    assertThat(refreshed.subject()).isEqualTo(session.subject());
    assertThatCode(() -> provider.revokeSession(refreshed.refreshToken()))
        .doesNotThrowAnyException();

    IdentityProvider.ProvisionedIdentity provisional =
        provider.provisionProvisionalIdentity(
            "provisional-user@example.test", "marker-provisional");
    assertThat(provider.findIdentityByCorrelationMarker("marker-provisional"))
        .contains(provisional);
    assertThatCode(
            () -> provider.requestCredentialSetup(provisional.subject(), Duration.ofHours(1)))
        .doesNotThrowAnyException();

    assertThatCode(() -> provider.setIdentityEnabled(passwordIdentity.subject(), false))
        .doesNotThrowAnyException();
    assertThatCode(() -> provider.setIdentityEnabled(passwordIdentity.subject(), true))
        .doesNotThrowAnyException();
    assertThatCode(() -> provider.deleteIdentity(provisional.subject())).doesNotThrowAnyException();
    assertThatCode(() -> provider.deleteIdentity(provisional.subject())).doesNotThrowAnyException();
    assertThatCode(() -> provider.deleteIdentity(passwordIdentity.subject()))
        .doesNotThrowAnyException();
  }
}
