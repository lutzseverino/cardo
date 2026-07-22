package io.github.lutzseverino.cardo.identity.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class IdentityKeycloakLegacyStartupRepairTest {

  @Test
  void convergesMissingDriftedAndDuplicateProviderDefinitions() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    IdentityKeycloakLegacyStartupRepair repair = repair(rest);
    expectClient(server, "runtime", "runtime-uuid");
    expectMappers(server, "runtime-uuid", "[]");
    expectCanonicalMapperWrite(server, POST, "runtime-uuid", null);
    expectClient(server, "identity", "identity-uuid");
    expectMappers(
        server,
        "identity-uuid",
        """
        [
          {"id":"mapper-b","name":"cardo_user_id","protocol":"openid-connect",
           "protocolMapper":"oidc-hardcoded-claim-mapper","consentRequired":false,"config":{}},
          {"id":"mapper-a","name":"cardo_user_id","protocol":"openid-connect",
           "protocolMapper":"oidc-hardcoded-claim-mapper","consentRequired":false,"config":{}}
        ]
        """);
    expectCanonicalMapperWrite(server, PUT, "identity-uuid", "mapper-a");
    server
        .expect(
            requestTo(Matchers.endsWith("/clients/identity-uuid/protocol-mappers/models/mapper-b")))
        .andExpect(method(DELETE))
        .andRespond(withNoContent());
    expectClient(server, "billing", "billing-uuid");
    expectMappers(server, "billing-uuid", canonicalMapperResponse());
    expectClient(server, "identity", "identity-uuid");
    expectRoleLookup(server, "profile%3Aread", HttpStatus.NOT_FOUND);
    expectRoleCreate(server, "profile:read", withNoContent());
    expectClient(server, "identity", "identity-uuid");
    expectRoleLookup(server, "profile%3Awrite", HttpStatus.OK);
    expectClient(server, "identity", "identity-uuid");
    expectRoleLookup(server, "user%3Aprovision", HttpStatus.NOT_FOUND);
    expectRoleCreate(server, "user:provision", withStatus(HttpStatus.CONFLICT));
    expectRoleLookup(server, "user%3Aprovision", HttpStatus.OK);

    repair.repair();

    server.verify();
  }

  @Test
  void isReadOnlyWhenDefinitionsAreAlreadyCanonical() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    IdentityKeycloakLegacyStartupRepair repair = repair(rest);
    expectClient(server, "runtime", "runtime-uuid");
    expectMappers(server, "runtime-uuid", canonicalMapperResponse());
    expectClient(server, "identity", "identity-uuid");
    expectMappers(server, "identity-uuid", canonicalMapperResponse());
    expectClient(server, "billing", "billing-uuid");
    expectMappers(server, "billing-uuid", canonicalMapperResponse());
    expectClient(server, "identity", "identity-uuid");
    expectRoleLookup(server, "profile%3Aread", HttpStatus.OK);
    expectClient(server, "identity", "identity-uuid");
    expectRoleLookup(server, "profile%3Awrite", HttpStatus.OK);
    expectClient(server, "identity", "identity-uuid");
    expectRoleLookup(server, "user%3Aprovision", HttpStatus.OK);

    repair.repair();

    server.verify();
  }

  private IdentityKeycloakLegacyStartupRepair repair(RestClient.Builder rest) {
    KeycloakClientCredentialsTokenProvider tokens =
        mock(KeycloakClientCredentialsTokenProvider.class);
    when(tokens.clientCredentialsToken()).thenReturn("runtime-token");
    return new IdentityKeycloakLegacyStartupRepair(
        IdentityKeycloakProviderContractValidatorTest.properties(true), tokens, rest);
  }

  private void expectClient(MockRestServiceServer server, String clientId, String clientUuid) {
    server
        .expect(requestTo(Matchers.containsString("clientId=" + clientId)))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                "[{\"id\":\"" + clientUuid + "\",\"clientId\":\"" + clientId + "\"}]",
                MediaType.APPLICATION_JSON));
  }

  private void expectMappers(MockRestServiceServer server, String clientUuid, String responseBody) {
    server
        .expect(requestTo(Matchers.endsWith("/clients/" + clientUuid + "/protocol-mappers/models")))
        .andExpect(method(GET))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
  }

  private void expectCanonicalMapperWrite(
      MockRestServiceServer server,
      org.springframework.http.HttpMethod httpMethod,
      String clientUuid,
      String mapperId) {
    String suffix =
        "/clients/"
            + clientUuid
            + "/protocol-mappers/models"
            + (mapperId == null ? "" : "/" + mapperId);
    server
        .expect(requestTo(Matchers.endsWith(suffix)))
        .andExpect(method(httpMethod))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "name":"cardo_user_id",
                      "protocol":"openid-connect",
                      "protocolMapper":"oidc-usermodel-attribute-mapper",
                      "consentRequired":false,
                      "config":{
                        "user.attribute":"cardo_user_id",
                        "claim.name":"cardo_user_id",
                        "access.token.claim":"true"
                      }
                    }
                    """))
        .andRespond(withNoContent());
  }

  private void expectRoleLookup(
      MockRestServiceServer server, String encodedRole, HttpStatus status) {
    var response =
        status.is2xxSuccessful()
            ? withSuccess("{\"id\":\"role-id\",\"name\":\"role\"}", MediaType.APPLICATION_JSON)
            : withStatus(status);
    server
        .expect(requestTo(Matchers.endsWith("/clients/identity-uuid/roles/" + encodedRole)))
        .andExpect(method(GET))
        .andRespond(response);
  }

  private void expectRoleCreate(
      MockRestServiceServer server,
      String role,
      org.springframework.test.web.client.ResponseCreator response) {
    server
        .expect(requestTo(Matchers.endsWith("/clients/identity-uuid/roles")))
        .andExpect(method(POST))
        .andExpect(content().json("{\"name\":\"" + role + "\"}"))
        .andRespond(response);
  }

  private String canonicalMapperResponse() {
    return """
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
            "multivalued":"false"
          }
        }]
        """;
  }
}
