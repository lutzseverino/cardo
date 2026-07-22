package io.github.lutzseverino.cardo.authorization.keycloak;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    List<ClientRepresentation> exact =
        clients == null
            ? List.of()
            : Arrays.stream(clients)
                .filter(client -> clientId.equals(client.clientId()))
                .filter(client -> client.id() != null && !client.id().isBlank())
                .toList();
    if (exact.size() != 1) {
      throw new KeycloakAuthorizationException("Expected exactly one Keycloak client: " + clientId);
    }
    return exact.getFirst().id();
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

  private String authorization() {
    return "Bearer " + adminToken.get();
  }

  private record ClientRepresentation(String id, String clientId) {}

  private record UserProfile(
      String username,
      String email,
      String firstName,
      String lastName,
      Boolean enabled,
      Boolean emailVerified,
      Map<String, List<String>> attributes) {

    UserProfile withAttribute(String name, String value) {
      Map<String, List<String>> merged =
          new LinkedHashMap<>(attributes == null ? Map.of() : attributes);
      merged.put(name, List.of(value));
      return new UserProfile(username, email, firstName, lastName, enabled, emailVerified, merged);
    }
  }
}
