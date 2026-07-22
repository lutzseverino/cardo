package io.github.lutzseverino.cardo.identity.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.identity.config.KeycloakProperties;
import java.net.URI;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakIdentityRuntimeContractTest {

  @Test
  void acceptsTheExactReadOnlyProviderContract() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityRuntimeContract validator = validator(rest);
    expectValidClients(server);
    expectCanonicalMapper(server, "runtime-uuid");
    expectCanonicalMapper(server, "identity-uuid");
    expectCanonicalMapper(server, "billing-uuid");
    expectRoles(server);
    expectReadCapabilities(server);

    assertThatCode(validator::validate).doesNotThrowAnyException();

    server.verify();
  }

  @Test
  void aggregatesIndependentDriftWithoutDisclosingProviderResponsesOrCredentials() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityRuntimeContract validator = validator(rest);
    expectClient(
        server,
        "runtime",
        """
        [
          {"id":"runtime-uuid-1","clientId":"runtime"},
          {"id":"runtime-uuid-2","clientId":"runtime"}
        ]
        """);
    expectClient(server, "setup", "[{\"id\":\"setup-uuid\",\"clientId\":\"setup\"}]");
    expectClient(server, "identity", "[{\"id\":\"identity-uuid\",\"clientId\":\"identity\"}]");
    expectClient(server, "billing", "[{\"id\":\"billing-uuid\",\"clientId\":\"billing\"}]");
    server
        .expect(requestTo(Matchers.endsWith("/clients/identity-uuid/protocol-mappers/models")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [{"id":"mapper-1","name":"cardo_user_id","protocol":"openid-connect",
                  "protocolMapper":"oidc-hardcoded-claim-mapper","consentRequired":false,
                  "config":{}}]
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(Matchers.endsWith("/clients/billing-uuid/protocol-mappers/models")))
        .andExpect(method(GET))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .body("provider-secret-body")
                .contentType(MediaType.TEXT_PLAIN));
    server
        .expect(requestTo(Matchers.endsWith("/clients/identity-uuid/roles/profile%3Aread")))
        .andExpect(method(GET))
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND)
                .body("missing-role-secret")
                .contentType(MediaType.TEXT_PLAIN));
    expectRole(server, "profile%3Awrite", "profile:write");
    expectRole(server, "user%3Aprovision", "user:provision");
    server
        .expect(requestTo(Matchers.containsString("/admin/realms/cardo/users?")))
        .andExpect(method(GET))
        .andRespond(withStatus(HttpStatus.FORBIDDEN).body("user-read-secret"));
    server
        .expect(requestTo(Matchers.endsWith("/realms/cardo/authz/protection/resource_set")))
        .andExpect(method(GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThatThrownBy(validator::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("client runtime expected one exact match but found 2")
        .hasMessageContaining("mapper cardo_user_id has incompatible semantics")
        .hasMessageContaining("mapper lookup returned HTTP 403")
        .hasMessageContaining("Identity role profile:read lookup returned HTTP 404")
        .hasMessageContaining("user directory read returned HTTP 403")
        .hasMessageContaining("Repair all drift with deployment-owned provisioning")
        .hasMessageNotContaining("legacy-startup-mutation-enabled")
        .hasMessageNotContaining("runtime-secret")
        .hasMessageNotContaining("provider-secret-body")
        .hasMessageNotContaining("missing-role-secret")
        .hasMessageNotContaining("user-read-secret")
        .hasMessageNotContaining("https://keycloak.example");

    server.verify();
  }

  @Test
  void limitsLegacyRepairAdviceToMapperAndFixedRoleDrift() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakIdentityRuntimeContract validator = validator(rest);
    expectValidClients(server);
    expectCanonicalMapper(server, "runtime-uuid");
    expectCanonicalMapper(server, "identity-uuid");
    server
        .expect(requestTo(Matchers.endsWith("/clients/billing-uuid/protocol-mappers/models")))
        .andExpect(method(GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(Matchers.endsWith("/clients/identity-uuid/roles/profile%3Aread")))
        .andExpect(method(GET))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    expectRole(server, "profile%3Awrite", "profile:write");
    expectRole(server, "user%3Aprovision", "user:provision");
    expectReadCapabilities(server);

    assertThatThrownBy(validator::validate)
        .hasMessageContaining("Repair all drift with deployment-owned provisioning")
        .hasMessageContaining("legacy-startup-mutation-enabled");

    server.verify();
  }

  private KeycloakIdentityRuntimeContract validator(RestClient.Builder rest) {
    KeycloakClientCredentialsTokenProvider tokens =
        mock(KeycloakClientCredentialsTokenProvider.class);
    when(tokens.clientCredentialsToken()).thenReturn("runtime-secret-token");
    KeycloakLegacyStartupRepair repair =
        new KeycloakLegacyStartupRepair(properties(false), tokens, rest.clone());
    return new KeycloakIdentityRuntimeContract(properties(false), tokens, repair, rest);
  }

  private void expectValidClients(MockRestServiceServer server) {
    expectClient(server, "runtime", "[{\"id\":\"runtime-uuid\",\"clientId\":\"runtime\"}]");
    expectClient(server, "setup", "[{\"id\":\"setup-uuid\",\"clientId\":\"setup\"}]");
    expectClient(server, "identity", "[{\"id\":\"identity-uuid\",\"clientId\":\"identity\"}]");
    expectClient(server, "billing", "[{\"id\":\"billing-uuid\",\"clientId\":\"billing\"}]");
  }

  private void expectClient(MockRestServiceServer server, String clientId, String response) {
    server
        .expect(requestTo(Matchers.containsString("clientId=" + clientId)))
        .andExpect(method(GET))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
  }

  private void expectCanonicalMapper(MockRestServiceServer server, String clientUuid) {
    server
        .expect(requestTo(Matchers.endsWith("/clients/" + clientUuid + "/protocol-mappers/models")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [{
                  "id":"mapper-id",
                  "name":"cardo_user_id",
                  "protocol":"openid-connect",
                  "protocolMapper":"oidc-usermodel-attribute-mapper",
                  "consentRequired":false,
                  "config":{
                    "user.attribute":"cardo_user_id",
                    "claim.name":"cardo_user_id",
                    "jsonType.label":"String",
                    "access.token.claim":"true",
                    "id.token.claim":"false",
                    "userinfo.token.claim":"false",
                    "multivalued":"false",
                    "provider.added.default":"accepted"
                  }
                }]
                """,
                MediaType.APPLICATION_JSON));
  }

  private void expectRoles(MockRestServiceServer server) {
    expectRole(server, "profile%3Aread", "profile:read");
    expectRole(server, "profile%3Awrite", "profile:write");
    expectRole(server, "user%3Aprovision", "user:provision");
  }

  private void expectRole(MockRestServiceServer server, String encodedRole, String role) {
    server
        .expect(requestTo(Matchers.endsWith("/clients/identity-uuid/roles/" + encodedRole)))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                "{\"id\":\"role-id\",\"name\":\"" + role + "\"}", MediaType.APPLICATION_JSON));
  }

  private void expectReadCapabilities(MockRestServiceServer server) {
    server
        .expect(requestTo(Matchers.containsString("/admin/realms/cardo/users?")))
        .andExpect(method(GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(Matchers.endsWith("/realms/cardo/authz/protection/resource_set")))
        .andExpect(method(GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
  }

  static KeycloakProperties properties(boolean legacyMutation) {
    return new KeycloakProperties(
        "https://keycloak.example",
        "cardo",
        "runtime",
        "runtime-secret",
        "setup",
        URI.create("https://app.example/invitations/completed"),
        List.of("runtime", "identity", "billing"),
        legacyMutation);
  }
}
