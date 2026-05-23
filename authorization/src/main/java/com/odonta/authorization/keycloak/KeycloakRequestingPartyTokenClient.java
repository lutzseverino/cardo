package com.odonta.authorization.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.odonta.authorization.token.RequestingPartyToken;
import com.odonta.authorization.token.RequestingPartyTokenClient;
import com.odonta.authorization.token.RequestingPartyTokenRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

public class KeycloakRequestingPartyTokenClient implements RequestingPartyTokenClient {

  private static final String UMA_TICKET_GRANT = "urn:ietf:params:oauth:grant-type:uma-ticket";

  private final String realm;
  private final RestClient rest;

  public KeycloakRequestingPartyTokenClient(String baseUrl, String realm, RestClient.Builder rest) {
    this.realm = realm;
    this.rest = rest.baseUrl(baseUrl).build();
  }

  @Override
  public RequestingPartyToken authorize(RequestingPartyTokenRequest request) {
    TokenResponse token =
        rest.post()
            .uri("/realms/{realm}/protocol/openid-connect/token", realm)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.accessToken())
            .body(authorizationRequest(request))
            .retrieve()
            .body(TokenResponse.class);
    if (token == null || token.accessToken() == null) {
      throw new KeycloakAuthorizationException("Keycloak did not return a requesting party token.");
    }
    return new RequestingPartyToken(token.accessToken());
  }

  private MultiValueMap<String, String> authorizationRequest(RequestingPartyTokenRequest request) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", UMA_TICKET_GRANT);
    form.add("audience", request.resourceServerClientId());
    request.permissions().stream()
        .flatMap(
            permission ->
                permission.actions().stream()
                    .map(action -> permissionParameter(permission.resourceId(), action)))
        .forEach(permission -> form.add("permission", permission));
    return form;
  }

  private String permissionParameter(String resourceId, String action) {
    return resourceId + "#" + action;
  }

  private record TokenResponse(@JsonProperty("access_token") String accessToken) {}
}
