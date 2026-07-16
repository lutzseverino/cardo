package com.odonta.identity.productauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

final class KeycloakActiveTokenValidator implements ActiveTokenValidator {

  private static final String FORM_CLIENT_ID = "client_id";
  private static final String FORM_CLIENT_SECRET = "client_secret";
  private static final String FORM_TOKEN = "token";

  private final URI introspectionUri;
  private final String clientId;
  private final String clientSecret;
  private final Duration cacheTtl;
  private final int cacheMaxEntries;
  private final RestClient rest;
  private final Clock clock;
  private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

  KeycloakActiveTokenValidator(
      URI introspectionUri,
      String clientId,
      String clientSecret,
      Duration cacheTtl,
      int cacheMaxEntries,
      Duration connectTimeout,
      Duration readTimeout,
      RestClient.Builder rest) {
    this(
        introspectionUri,
        clientId,
        clientSecret,
        cacheTtl,
        cacheMaxEntries,
        rest.requestFactory(requestFactory(connectTimeout, readTimeout)).build(),
        Clock.systemUTC());
  }

  KeycloakActiveTokenValidator(
      URI introspectionUri,
      String clientId,
      String clientSecret,
      Duration cacheTtl,
      int cacheMaxEntries,
      RestClient rest,
      Clock clock) {
    this.introspectionUri = introspectionUri;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.cacheTtl = cacheTtl;
    this.cacheMaxEntries = cacheMaxEntries;
    this.rest = rest;
    this.clock = clock;
  }

  @Override
  public boolean isActive(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    String tokenKey = tokenKey(token);
    CachedToken cached = cache.get(tokenKey);
    if (cached != null && cached.isValid(clock.instant())) {
      return true;
    }
    TokenIntrospection introspection =
        rest.post()
            .uri(introspectionUri)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(introspectionRequest(token))
            .retrieve()
            .body(TokenIntrospection.class);
    boolean active = introspection != null && introspection.active();
    if (active && !cacheTtl.isZero()) {
      cache(tokenKey);
    } else {
      cache.remove(tokenKey);
    }
    return active;
  }

  private void cache(String tokenKey) {
    purgeExpiredTokens();
    if (cache.size() >= cacheMaxEntries) {
      cache.keySet().stream().findFirst().ifPresent(cache::remove);
    }
    cache.put(tokenKey, new CachedToken(clock.instant().plus(cacheTtl)));
  }

  private void purgeExpiredTokens() {
    Instant now = clock.instant();
    cache.entrySet().removeIf(entry -> !entry.getValue().isValid(now));
  }

  private MultiValueMap<String, String> introspectionRequest(String token) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add(FORM_CLIENT_ID, clientId);
    form.add(FORM_CLIENT_SECRET, clientSecret);
    form.add(FORM_TOKEN, token);
    return form;
  }

  private static SimpleClientHttpRequestFactory requestFactory(
      Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  private String tokenKey(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }

  private record CachedToken(Instant expiresAt) {

    boolean isValid(Instant now) {
      return now.isBefore(expiresAt);
    }
  }

  private record TokenIntrospection(@JsonProperty("active") boolean active) {}
}
