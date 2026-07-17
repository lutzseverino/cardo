package io.github.lutzseverino.cardo.identity.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
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
import io.github.lutzseverino.cardo.identity.config.KeycloakProperties;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakIdentityProviderTest {

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
}
