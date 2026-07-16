package com.odonta.identity.integration.keycloak;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

import com.odonta.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import com.odonta.authorization.keycloak.KeycloakRealmAdminClient;
import com.odonta.identity.config.KeycloakProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakIdentityProviderTest {

  @Test
  void disablesIdentityAndLogsOutSessions() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityProvider provider = provider(rest);
    server
        .expect(requestTo("https://keycloak.example/admin/realms/odonta/users/subject-1"))
        .andExpect(method(PUT))
        .andExpect(content().json("{\"enabled\":false}", JsonCompareMode.STRICT))
        .andRespond(withNoContent());
    server
        .expect(requestTo("https://keycloak.example/admin/realms/odonta/users/subject-1/logout"))
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
        .expect(requestTo("https://keycloak.example/admin/realms/odonta/users/subject-1"))
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
            "https://keycloak.example", "odonta", "identity", "secret", List.of()),
        mock(KeycloakRealmAdminClient.class),
        tokens,
        rest);
  }
}
