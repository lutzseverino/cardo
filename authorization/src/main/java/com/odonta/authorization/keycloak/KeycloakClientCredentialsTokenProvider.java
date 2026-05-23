package com.odonta.authorization.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class KeycloakClientCredentialsTokenProvider {

  private static final String FORM_CLIENT_ID = "client_id";
  private static final String FORM_CLIENT_SECRET = "client_secret";
  private static final String FORM_GRANT_TYPE = "grant_type";

  private final String realm;
  private final String clientId;
  private final String clientSecret;
  private final RestClient rest;

  public KeycloakClientCredentialsTokenProvider(
      String baseUrl, String realm, String clientId, String clientSecret, RestClient.Builder rest) {
    this.realm = realm;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.rest = rest.baseUrl(baseUrl).build();
  }

  public String clientCredentialsToken() {
    try {
      TokenResponse token =
          rest.post()
              .uri("/realms/{realm}/protocol/openid-connect/token", realm)
              .body(clientCredentialsGrant())
              .retrieve()
              .body(TokenResponse.class);
      if (token == null || token.accessToken() == null) {
        throw new KeycloakAuthorizationException(
            "Keycloak did not return a client credentials token.");
      }
      return token.accessToken();
    } catch (RestClientResponseException exception) {
      throw new KeycloakAuthorizationException("Keycloak client credentials request failed.");
    }
  }

  private MultiValueMap<String, String> clientCredentialsGrant() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add(FORM_CLIENT_ID, clientId);
    form.add(FORM_CLIENT_SECRET, clientSecret);
    form.add(FORM_GRANT_TYPE, "client_credentials");
    return form;
  }

  private record TokenResponse(@JsonProperty("access_token") String accessToken) {}
}
