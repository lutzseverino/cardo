package io.github.lutzseverino.cardo.authorization.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class KeycloakClientCredentialsTokenProvider {

  private static final String FORM_CLIENT_ID = "client_id";
  private static final String FORM_CLIENT_SECRET = "client_secret";
  private static final String FORM_GRANT_TYPE = "grant_type";
  private static final String FORM_SCOPE = "scope";

  private final String realm;
  private final String clientId;
  private final String clientSecret;
  private final RestClient rest;
  private final Clock clock;
  private final KeycloakClientCredentialsTokenSettings settings;
  private final TokenCache unscopedToken = new TokenCache();
  private final Map<String, TokenCache> scopedTokens = new ConcurrentHashMap<>();

  public KeycloakClientCredentialsTokenProvider(
      String baseUrl, String realm, String clientId, String clientSecret, RestClient.Builder rest) {
    this(
        baseUrl,
        realm,
        clientId,
        clientSecret,
        rest,
        KeycloakClientCredentialsTokenSettings.defaults());
  }

  public KeycloakClientCredentialsTokenProvider(
      String baseUrl,
      String realm,
      String clientId,
      String clientSecret,
      RestClient.Builder rest,
      KeycloakClientCredentialsTokenSettings settings) {
    this(baseUrl, realm, clientId, clientSecret, rest, settings, Clock.systemUTC());
  }

  KeycloakClientCredentialsTokenProvider(
      String baseUrl,
      String realm,
      String clientId,
      String clientSecret,
      RestClient.Builder rest,
      KeycloakClientCredentialsTokenSettings settings,
      Clock clock) {
    this.realm = realm;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.settings = settings;
    this.clock = clock;
    this.rest =
        rest.baseUrl(baseUrl)
            .requestFactory(requestFactory(settings.connectTimeout(), settings.readTimeout()))
            .build();
  }

  public String clientCredentialsToken() {
    return token(unscopedToken, null);
  }

  public String clientCredentialsToken(String scope) {
    String normalizedScope = normalizeScope(scope);
    return token(
        scopedTokens.computeIfAbsent(normalizedScope, ignored -> new TokenCache()),
        normalizedScope);
  }

  private String token(TokenCache cache, String scope) {
    Instant now = clock.instant();
    CachedToken current = cache.token;
    if (current != null && current.isReusableAt(now)) {
      return current.value();
    }
    return refreshToken(cache, scope);
  }

  private String refreshToken(TokenCache cache, String scope) {
    synchronized (cache) {
      Instant now = clock.instant();
      CachedToken current = cache.token;
      if (current != null && current.isReusableAt(now)) {
        return current.value();
      }
      return acquireToken(cache, scope);
    }
  }

  private String acquireToken(TokenCache cache, String scope) {
    try {
      TokenResponse token =
          rest.post()
              .uri("/realms/{realm}/protocol/openid-connect/token", realm)
              .body(clientCredentialsGrant(scope))
              .retrieve()
              .body(TokenResponse.class);
      if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
        throw new KeycloakAuthorizationException(
            "Keycloak did not return a client credentials token.");
      }
      if (token.expiresIn() == null || token.expiresIn() <= 0) {
        throw new KeycloakAuthorizationException(
            "Keycloak did not return a valid client credentials token expiry.");
      }
      Instant refreshAt =
          clock.instant().plusSeconds(token.expiresIn()).minus(settings.refreshSkew());
      cache.token = new CachedToken(token.accessToken(), refreshAt);
      return token.accessToken();
    } catch (RestClientException exception) {
      throw new KeycloakAuthorizationException("Keycloak client credentials request failed.");
    }
  }

  private MultiValueMap<String, String> clientCredentialsGrant(String scope) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add(FORM_CLIENT_ID, clientId);
    form.add(FORM_CLIENT_SECRET, clientSecret);
    form.add(FORM_GRANT_TYPE, "client_credentials");
    if (scope != null) {
      form.add(FORM_SCOPE, scope);
    }
    return form;
  }

  private String normalizeScope(String scope) {
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("scope must not be blank.");
    }
    return Arrays.stream(scope.strip().split("\\s+"))
        .distinct()
        .sorted()
        .collect(Collectors.joining(" "));
  }

  private static SimpleClientHttpRequestFactory requestFactory(
      java.time.Duration connectTimeout, java.time.Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  private record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") Long expiresIn) {}

  private record CachedToken(String value, Instant refreshAt) {

    boolean isReusableAt(Instant now) {
      return now.isBefore(refreshAt);
    }
  }

  private static final class TokenCache {

    private volatile CachedToken token;
  }
}
