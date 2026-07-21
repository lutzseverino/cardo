package io.github.lutzseverino.cardo.authorization.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyToken;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenRequest;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

public class KeycloakRequestingPartyTokenClient implements RequestingPartyTokenClient {

  private static final String UMA_TICKET_GRANT = "urn:ietf:params:oauth:grant-type:uma-ticket";

  private final URI tokenEndpoint;
  private final RestClient rest;

  public KeycloakRequestingPartyTokenClient(String baseUrl, String realm, RestClient.Builder rest) {
    this(
        URI.create(
            baseUrl.replaceFirst("/+$", "")
                + "/realms/"
                + realm
                + "/protocol/openid-connect/token"),
        rest);
  }

  public KeycloakRequestingPartyTokenClient(URI tokenEndpoint, RestClient.Builder rest) {
    this.tokenEndpoint = tokenEndpoint;
    this.rest = rest.build();
  }

  @Override
  public RequestingPartyToken authorize(RequestingPartyTokenRequest request) {
    TokenResponse token =
        rest.post()
            .uri(tokenEndpoint)
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
