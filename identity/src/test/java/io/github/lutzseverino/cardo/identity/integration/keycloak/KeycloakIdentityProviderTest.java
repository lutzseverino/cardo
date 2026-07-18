package io.github.lutzseverino.cardo.identity.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakRealmAdminClient;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.config.KeycloakProperties;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakIdentityProviderTest {

  @Test
  void issuesCompletePasswordSessionCredentials() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo("https://keycloak.example/realms/cardo/protocol/openid-connect/token"))
        .andExpect(method(POST))
        .andExpect(content().string(containsString("grant_type=password")))
        .andExpect(content().string(containsString("username=user%40example.com")))
        .andRespond(
            withSuccess(
                """
                {"access_token":"provider-access","expires_in":300,
                 "refresh_token":"provider-refresh","refresh_expires_in":3600}
                """,
                MediaType.APPLICATION_JSON));
    expectIntrospection(server, "provider-access");
    OffsetDateTime before = OffsetDateTime.now();

    IdentityProvider.IssuedSession session =
        provider.issuePasswordSession("user@example.com", "password-1");

    assertThat(session.accessToken()).isEqualTo("provider-access");
    assertThat(session.refreshToken()).isEqualTo("provider-refresh");
    assertThat(session.subject()).isEqualTo("subject-1");
    assertThat(session.sessionId()).isEqualTo("session-1");
    assertThat(session.accessExpiresAt())
        .isBetween(before.plusSeconds(299), before.plusSeconds(301));
    assertThat(session.refreshExpiresAt())
        .isBetween(before.plusSeconds(3599), before.plusSeconds(3601));
    assertThat(session.toString()).doesNotContain("provider-access", "provider-refresh");
    server.verify();
  }

  @Test
  void refreshesAndReturnsRotatedCredentials() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo("https://keycloak.example/realms/cardo/protocol/openid-connect/token"))
        .andExpect(method(POST))
        .andExpect(content().string(containsString("grant_type=refresh_token")))
        .andExpect(content().string(containsString("refresh_token=old-refresh")))
        .andRespond(
            withSuccess(
                """
                {"access_token":"rotated-access","expires_in":300,
                 "refresh_token":"rotated-refresh","refresh_expires_in":3600}
                """,
                MediaType.APPLICATION_JSON));
    expectIntrospection(server, "rotated-access");

    IdentityProvider.IssuedSession session = provider.refreshSession("old-refresh");

    assertThat(session.accessToken()).isEqualTo("rotated-access");
    assertThat(session.refreshToken()).isEqualTo("rotated-refresh");
    server.verify();
  }

  @Test
  void revokesNewlyIssuedSessionWhenIntrospectionFails() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo("https://keycloak.example/realms/cardo/protocol/openid-connect/token"))
        .andRespond(
            withSuccess(
                """
                {"access_token":"rotated-access","expires_in":300,
                 "refresh_token":"rotated-refresh","refresh_expires_in":3600}
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(
            requestTo(
                "https://keycloak.example/realms/cardo/protocol/openid-connect/token/introspect"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    server
        .expect(requestTo("https://keycloak.example/realms/cardo/protocol/openid-connect/revoke"))
        .andExpect(content().string(containsString("token=rotated-refresh")))
        .andRespond(withNoContent());

    assertThatThrownBy(() -> provider.refreshSession("old-refresh"))
        .isInstanceOfSatisfying(
            ApiException.class, exception -> assertThat(exception.status()).isEqualTo(503));

    server.verify();
  }

  @Test
  void rejectsInvalidRefreshCredentialAsAnInvalidSession() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo("https://keycloak.example/realms/cardo/protocol/openid-connect/token"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> provider.refreshSession("invalid-refresh"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(401);
              assertThat(exception.code()).isEqualTo("invalid_session");
            });
  }

  @Test
  void revokesTheRefreshCredentialAndAcceptsTheProvidersIdempotentResponse() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo("https://keycloak.example/realms/cardo/protocol/openid-connect/revoke"))
        .andExpect(method(POST))
        .andExpect(content().string(containsString("token=provider-refresh")))
        .andExpect(content().string(containsString("token_type_hint=refresh_token")))
        .andRespond(withNoContent());

    provider.revokeSession("provider-refresh");

    server.verify();
  }

  @Test
  void reportsRevocationAuthenticationFailure() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo("https://keycloak.example/realms/cardo/protocol/openid-connect/revoke"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> provider.revokeSession("provider-refresh"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(401);
              assertThat(exception.code()).isEqualTo("identity_provider_error");
            });
  }

  @Test
  void reportsTokenEndpointAuthenticationFailureAsAProviderError() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo("https://keycloak.example/realms/cardo/protocol/openid-connect/token"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> provider.refreshSession("provider-refresh"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(401);
              assertThat(exception.code()).isEqualTo("identity_provider_error");
            });
  }

  @Test
  void reportsTransientRevocationFailure() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo("https://keycloak.example/realms/cardo/protocol/openid-connect/revoke"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> provider.revokeSession("provider-refresh"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(503);
              assertThat(exception.code()).isEqualTo("identity_provider_error");
            });
  }

  @Test
  void delegatesCredentialSetupToKeycloakWithoutReceivingThePassword() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo(containsString("/users/subject-1/execute-actions-email")))
        .andExpect(method(PUT))
        .andExpect(queryParam("client_id", "cardo-web"))
        .andExpect(queryParam("redirect_uri", "https://app.example/invitations/completed"))
        .andExpect(queryParam("lifespan", "86400"))
        .andExpect(
            content().json("[\"UPDATE_PASSWORD\",\"UPDATE_PROFILE\"]", JsonCompareMode.STRICT))
        .andRespond(withNoContent());

    provider.requestCredentialSetup("subject-1", Duration.ofHours(24));

    server.verify();
  }

  @Test
  void reportsCompletionOnlyAfterProfileAndPasswordExistInKeycloak() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo("https://keycloak.example/admin/realms/cardo/users/subject-1"))
        .andExpect(method(GET))
        .andRespond(withSuccess("{\"firstName\":\"Employee\"}", MediaType.APPLICATION_JSON));
    server
        .expect(
            requestTo("https://keycloak.example/admin/realms/cardo/users/subject-1/credentials"))
        .andExpect(method(GET))
        .andRespond(withSuccess("[{\"type\":\"password\"}]", MediaType.APPLICATION_JSON));

    assertThat(provider.completedIdentityProfile("subject-1"))
        .contains(new IdentityProvider.CompletedIdentityProfile("Employee"));

    server.verify();
  }

  @Test
  void treatsAlreadyMissingIdentityAsSuccessfulDeletion() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo("https://keycloak.example/admin/realms/cardo/users/subject-1"))
        .andExpect(method(DELETE))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    provider.deleteIdentity("subject-1");

    server.verify();
  }

  @Test
  void disablesIdentityAndLogsOutSessions() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo("https://keycloak.example/admin/realms/cardo/users/subject-1"))
        .andExpect(method(PUT))
        .andExpect(content().json("{\"enabled\":false}", JsonCompareMode.STRICT))
        .andRespond(withNoContent());
    server
        .expect(requestTo("https://keycloak.example/admin/realms/cardo/users/subject-1/logout"))
        .andExpect(method(POST))
        .andRespond(withNoContent());

    provider.setIdentityEnabled("subject-1", false);

    server.verify();
  }

  @Test
  void enablesIdentityWithoutLoggingOutSessions() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo("https://keycloak.example/admin/realms/cardo/users/subject-1"))
        .andExpect(method(PUT))
        .andExpect(content().json("{\"enabled\":true}", JsonCompareMode.STRICT))
        .andRespond(withNoContent());

    provider.setIdentityEnabled("subject-1", true);

    server.verify();
  }

  private KeycloakIdentityProvider provider(RestClient.Builder rest) {
    KeycloakClientCredentialsTokenProvider tokens =
        mock(KeycloakClientCredentialsTokenProvider.class);
    when(tokens.clientCredentialsToken()).thenReturn("admin-token");
    return new KeycloakIdentityProvider(
        new KeycloakProperties(
            "https://keycloak.example",
            "cardo",
            "identity",
            "secret",
            "cardo-web",
            URI.create("https://app.example/invitations/completed"),
            List.of()),
        mock(KeycloakRealmAdminClient.class),
        tokens,
        rest);
  }

  private void expectIntrospection(MockRestServiceServer server, String accessToken) {
    server
        .expect(
            requestTo(
                "https://keycloak.example/realms/cardo/protocol/openid-connect/token/introspect"))
        .andExpect(method(POST))
        .andExpect(content().string(containsString("token=" + accessToken)))
        .andRespond(
            withSuccess(
                """
                {"active":true,"sub":"subject-1","sid":"session-1"}
                """,
                MediaType.APPLICATION_JSON));
  }
}
