package com.odonta.authorization.keycloak;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

public class KeycloakRealmAdminClient {

  private final String realm;
  private final RestClient rest;
  private final Supplier<String> adminToken;

  public KeycloakRealmAdminClient(
      String baseUrl, String realm, RestClient.Builder rest, Supplier<String> adminToken) {
    this.realm = realm;
    this.rest = rest.baseUrl(baseUrl).build();
    this.adminToken = adminToken;
  }

  public String clientUuid(String clientId) {
    ClientRepresentation[] clients =
        rest.get()
            .uri(
                uri ->
                    uri.path("/admin/realms/{realm}/clients")
                        .queryParam("clientId", clientId)
                        .build(realm))
            .header(HttpHeaders.AUTHORIZATION, authorization())
            .retrieve()
            .body(ClientRepresentation[].class);
    if (clients == null || clients.length == 0 || clients[0].id() == null) {
      throw new KeycloakAuthorizationException("Keycloak client not found: " + clientId);
    }
    return clients[0].id();
  }

  public void bindUserAttribute(String subject, String name, String value) {
    UserProfile user =
        rest.get()
            .uri("/admin/realms/{realm}/users/{subject}", realm, subject)
            .header(HttpHeaders.AUTHORIZATION, authorization())
            .retrieve()
            .body(UserProfile.class);
    if (user == null) {
      throw new KeycloakAuthorizationException("Keycloak did not return a user.");
    }
    rest.put()
        .uri("/admin/realms/{realm}/users/{subject}", realm, subject)
        .header(HttpHeaders.AUTHORIZATION, authorization())
        .body(user.withAttribute(name, value))
        .retrieve()
        .toBodilessEntity();
  }

  public void ensureAccessTokenUserAttributeMapper(
      String clientId, String userAttribute, String claimName) {
    String clientUuid = clientUuid(clientId);
    ProtocolMapper mapper = accessTokenUserAttributeMapper(userAttribute, claimName);
    protocolMapper(clientUuid, mapper.name())
        .ifPresentOrElse(
            existing -> updateProtocolMapper(clientUuid, existing.id(), mapper),
            () -> createProtocolMapper(clientUuid, mapper));
  }

  private Optional<ProtocolMapper> protocolMapper(String clientUuid, String mapperName) {
    ProtocolMapper[] mappers =
        rest.get()
            .uri(
                "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models",
                realm,
                clientUuid)
            .header(HttpHeaders.AUTHORIZATION, authorization())
            .retrieve()
            .body(ProtocolMapper[].class);
    if (mappers == null) {
      return Optional.empty();
    }
    return Arrays.stream(mappers).filter(mapper -> mapperName.equals(mapper.name())).findFirst();
  }

  private void createProtocolMapper(String clientUuid, ProtocolMapper mapper) {
    rest.post()
        .uri(
            "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models", realm, clientUuid)
        .header(HttpHeaders.AUTHORIZATION, authorization())
        .body(mapper)
        .retrieve()
        .toBodilessEntity();
  }

  private void updateProtocolMapper(String clientUuid, String mapperId, ProtocolMapper mapper) {
    rest.put()
        .uri(
            "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models/{mapperId}",
            realm,
            clientUuid,
            mapperId)
        .header(HttpHeaders.AUTHORIZATION, authorization())
        .body(mapper.withId(mapperId))
        .retrieve()
        .toBodilessEntity();
  }

  private ProtocolMapper accessTokenUserAttributeMapper(String userAttribute, String claimName) {
    return new ProtocolMapper(
        null,
        claimName,
        "openid-connect",
        "oidc-usermodel-attribute-mapper",
        false,
        Map.of(
            "user.attribute",
            userAttribute,
            "claim.name",
            claimName,
            "jsonType.label",
            "String",
            "access.token.claim",
            "true",
            "id.token.claim",
            "false",
            "userinfo.token.claim",
            "false",
            "multivalued",
            "false"));
  }

  private String authorization() {
    return "Bearer " + adminToken.get();
  }

  private record ClientRepresentation(String id, String clientId) {}

  private record UserProfile(Map<String, List<String>> attributes) {

    UserProfile withAttribute(String name, String value) {
      Map<String, List<String>> merged = new HashMap<>(attributes == null ? Map.of() : attributes);
      merged.put(name, List.of(value));
      return new UserProfile(merged);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record ProtocolMapper(
      String id,
      String name,
      String protocol,
      String protocolMapper,
      boolean consentRequired,
      Map<String, String> config) {

    ProtocolMapper withId(String id) {
      return new ProtocolMapper(id, name, protocol, protocolMapper, consentRequired, config);
    }
  }
}
