package io.github.lutzseverino.cardo.identity.integration.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakRealmAdminClient;
import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.config.KeycloakProperties;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KeycloakIdentityProvider implements IdentityProvider {

  private static final String FORM_CLIENT_ID = "client_id";
  private static final String FORM_CLIENT_SECRET = "client_secret";
  private static final String FORM_GRANT_TYPE = "grant_type";
  private static final String PASSWORD_CREDENTIAL_TYPE = "password";
  private static final String FORM_PASSWORD = PASSWORD_CREDENTIAL_TYPE;
  private static final String FORM_REFRESH_TOKEN = "refresh_token";
  private static final String FORM_TOKEN = "token";
  private static final String REFRESH_TOKEN_GRANT = "refresh_token";
  private static final String PROVISIONING_CORRELATION_ATTRIBUTE = "cardo_provisioning_correlation";

  private final KeycloakProperties properties;
  private final KeycloakRealmAdminClient admin;
  private final KeycloakClientCredentialsTokenProvider clientCredentialsTokens;
  private final RestClient rest;

  KeycloakIdentityProvider(
      KeycloakProperties properties,
      KeycloakRealmAdminClient admin,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens,
      RestClient.Builder rest) {
    this.properties = properties;
    this.admin = admin;
    this.clientCredentialsTokens = clientCredentialsTokens;
    this.rest = rest.baseUrl(properties.baseUrl()).build();
  }

  @Override
  public ProvisionedIdentity provisionPasswordIdentity(
      String email, String password, String name, String correlationMarker) {
    return createUser(
        new KeycloakUser(
            email,
            email,
            name,
            true,
            List.of(passwordCredential(password)),
            Map.of(PROVISIONING_CORRELATION_ATTRIBUTE, List.of(correlationMarker))));
  }

  @Override
  public Optional<ProvisionedIdentity> findPasswordIdentityByCorrelationMarker(
      String correlationMarker) {
    try {
      List<KeycloakUserDetails> matches =
          rest.get()
              .uri(
                  builder ->
                      builder
                          .path("/admin/realms/{realm}/users")
                          .queryParam(
                              "q", PROVISIONING_CORRELATION_ATTRIBUTE + ":" + correlationMarker)
                          .queryParam("exact", true)
                          .queryParam("briefRepresentation", false)
                          .build(properties.realm()))
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
              .retrieve()
              .body(new org.springframework.core.ParameterizedTypeReference<>() {});
      if (matches == null || matches.isEmpty()) {
        return Optional.empty();
      }
      List<KeycloakUserDetails> exactMatches =
          matches.stream()
              .filter(
                  user ->
                      Optional.ofNullable(user.attributes())
                          .map(attributes -> attributes.get(PROVISIONING_CORRELATION_ATTRIBUTE))
                          .orElseGet(List::of)
                          .contains(correlationMarker))
              .toList();
      if (exactMatches.size() != 1
          || exactMatches.getFirst().id() == null
          || exactMatches.getFirst().id().isBlank()) {
        throw ApiException.of(
            502,
            "identity_provider_correlation_invalid",
            "Identity provider returned an invalid provisioning correlation result.");
      }
      return Optional.of(new ProvisionedIdentity(exactMatches.getFirst().id()));
    } catch (RestClientResponseException exception) {
      throw providerException(exception);
    } catch (RestClientException exception) {
      throw sessionUnavailable(exception);
    }
  }

  @Override
  public ProvisionedIdentity provisionProvisionalIdentity(String email) {
    return createUser(new KeycloakUser(email, email, null, true, List.of(), Map.of()));
  }

  @Override
  public void requestCredentialSetup(String subject, Duration lifespan) {
    try {
      rest.put()
          .uri(
              builder ->
                  builder
                      .path("/admin/realms/{realm}/users/{subject}/execute-actions-email")
                      .queryParam("client_id", properties.credentialSetupClientId())
                      .queryParam("redirect_uri", properties.credentialSetupRedirectUri())
                      .queryParam("lifespan", lifespan.toSeconds())
                      .build(properties.realm(), subject))
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
          .body(List.of("UPDATE_PASSWORD", "UPDATE_PROFILE"))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      throw providerException(exception);
    }
  }

  @Override
  public Optional<CompletedIdentityProfile> completedIdentityProfile(String subject) {
    try {
      KeycloakUserDetails user =
          rest.get()
              .uri("/admin/realms/{realm}/users/{subject}", properties.realm(), subject)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
              .retrieve()
              .body(KeycloakUserDetails.class);
      List<KeycloakCredentialSummary> credentials =
          rest.get()
              .uri("/admin/realms/{realm}/users/{subject}/credentials", properties.realm(), subject)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
              .retrieve()
              .body(new org.springframework.core.ParameterizedTypeReference<>() {});
      if (user == null
          || user.firstName() == null
          || user.firstName().isBlank()
          || credentials == null
          || credentials.stream()
              .noneMatch(credential -> PASSWORD_CREDENTIAL_TYPE.equals(credential.type()))) {
        return Optional.empty();
      }
      return Optional.of(new CompletedIdentityProfile(user.firstName()));
    } catch (RestClientResponseException exception) {
      throw providerException(exception);
    }
  }

  @Override
  public void deleteIdentity(String subject) {
    try {
      rest.delete()
          .uri("/admin/realms/{realm}/users/{subject}", properties.realm(), subject)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 404) {
        return;
      }
      throw providerException(exception);
    }
  }

  private ProvisionedIdentity createUser(KeycloakUser user) {
    try {
      URI location =
          rest.post()
              .uri("/admin/realms/{realm}/users", properties.realm())
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
              .body(user)
              .retrieve()
              .toBodilessEntity()
              .getHeaders()
              .getLocation();
      if (location == null) {
        throw ApiException.of(
            502, "identity_provider_user_missing", "Identity provider did not return a user id.");
      }
      return new ProvisionedIdentity(
          location.getPath().substring(location.getPath().lastIndexOf('/') + 1));
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 409) {
        throw ApiException.conflict("user_exists", "A user with this email already exists.");
      }
      throw providerException(exception);
    } catch (RestClientException exception) {
      throw sessionUnavailable(exception);
    }
  }

  @Override
  public void bindUserId(String subject, UUID userId) {
    try {
      admin.bindUserAttribute(subject, CardoJwtClaims.IDENTITY_USER_ID, userId.toString());
    } catch (RestClientResponseException exception) {
      throw providerException(exception);
    }
  }

  @Override
  public void setIdentityEnabled(String subject, boolean enabled) {
    try {
      rest.put()
          .uri("/admin/realms/{realm}/users/{subject}", properties.realm(), subject)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
          .body(new KeycloakUserStatus(enabled))
          .retrieve()
          .toBodilessEntity();
      if (!enabled) {
        rest.post()
            .uri("/admin/realms/{realm}/users/{subject}/logout", properties.realm(), subject)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .retrieve()
            .toBodilessEntity();
      }
    } catch (RestClientResponseException exception) {
      throw providerException(exception);
    }
  }

  @Override
  public void ensureUserIdClaimMapped(List<String> clientIds) {
    Optional.ofNullable(clientIds).orElseGet(List::of).stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(clientId -> !clientId.isBlank())
        .distinct()
        .forEach(
            clientId ->
                admin.ensureAccessTokenUserAttributeMapper(
                    clientId, CardoJwtClaims.IDENTITY_USER_ID, CardoJwtClaims.IDENTITY_USER_ID));
  }

  @Override
  public IssuedSession issuePasswordSession(String email, String password) {
    return issueSession(
        passwordGrant(email, password), "invalid_credentials", "Invalid credentials.");
  }

  @Override
  public IssuedSession refreshSession(String refreshToken) {
    return issueSession(
        refreshGrant(refreshToken), "invalid_session", "The session is no longer valid.");
  }

  @Override
  public void revokeSession(String refreshToken) {
    try {
      rest.post()
          .uri("/realms/{realm}/protocol/openid-connect/revoke", properties.realm())
          .body(revocationRequest(refreshToken))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      throw sessionProviderException(exception);
    } catch (RestClientException exception) {
      throw sessionUnavailable(exception);
    }
  }

  private IssuedSession issueSession(
      MultiValueMap<String, String> form, String invalidCode, String invalidMessage) {
    OffsetDateTime requestedAt = OffsetDateTime.now(ZoneOffset.UTC);
    TokenResponse token = token(form, invalidCode, invalidMessage);
    try {
      TokenIntrospection introspection = introspect(token.accessToken());
      return new IssuedSession(
          token.accessToken(),
          requestedAt.plusSeconds(token.expiresIn()),
          token.refreshToken(),
          requestedAt.plusSeconds(token.refreshExpiresIn()),
          introspection.sub(),
          introspection.sid());
    } catch (RuntimeException exception) {
      revokeAfterFailedIssue(token.refreshToken(), exception);
      throw exception;
    }
  }

  private String adminToken() {
    return clientCredentialsTokens.clientCredentialsToken();
  }

  private TokenResponse token(
      MultiValueMap<String, String> form, String invalidCode, String invalidMessage) {
    try {
      TokenResponse token =
          rest.post()
              .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
              .body(form)
              .retrieve()
              .body(TokenResponse.class);
      if (token == null
          || token.accessToken() == null
          || token.accessToken().isBlank()
          || token.refreshToken() == null
          || token.refreshToken().isBlank()
          || token.expiresIn() == null
          || token.expiresIn() <= 0
          || token.refreshExpiresIn() == null
          || token.refreshExpiresIn() <= 0) {
        throw ApiException.of(
            502,
            "identity_provider_session_missing",
            "Identity provider did not return complete session credentials.");
      }
      return token;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 400) {
        throw ApiException.of(
            "invalid_credentials".equals(invalidCode) ? 400 : 401, invalidCode, invalidMessage);
      }
      throw sessionProviderException(exception);
    } catch (RestClientException exception) {
      throw sessionUnavailable(exception);
    }
  }

  private TokenIntrospection introspect(String token) {
    try {
      TokenIntrospection introspection =
          rest.post()
              .uri("/realms/{realm}/protocol/openid-connect/token/introspect", properties.realm())
              .body(tokenRequest(token))
              .retrieve()
              .body(TokenIntrospection.class);
      if (introspection == null) {
        throw ApiException.of(
            502,
            "identity_provider_introspection_missing",
            "Identity provider did not return token introspection.");
      }
      if (!introspection.active() || introspection.sub() == null || introspection.sub().isBlank()) {
        throw ApiException.of(401, "invalid_session", "The session is no longer valid.");
      }
      return introspection;
    } catch (RestClientResponseException exception) {
      throw sessionProviderException(exception);
    } catch (RestClientException exception) {
      throw sessionUnavailable(exception);
    }
  }

  private MultiValueMap<String, String> passwordGrant(String email, String password) {
    MultiValueMap<String, String> form = clientCredentials();
    form.add(FORM_GRANT_TYPE, PASSWORD_CREDENTIAL_TYPE);
    form.add("username", email);
    form.add(FORM_PASSWORD, password);
    return form;
  }

  private MultiValueMap<String, String> refreshGrant(String refreshToken) {
    MultiValueMap<String, String> form = clientCredentials();
    form.add(FORM_GRANT_TYPE, REFRESH_TOKEN_GRANT);
    form.add(FORM_REFRESH_TOKEN, refreshToken);
    return form;
  }

  private MultiValueMap<String, String> revocationRequest(String token) {
    MultiValueMap<String, String> form = tokenRequest(token);
    form.add("token_type_hint", REFRESH_TOKEN_GRANT);
    return form;
  }

  private MultiValueMap<String, String> tokenRequest(String token) {
    MultiValueMap<String, String> form = clientCredentials();
    form.add(FORM_TOKEN, token);
    return form;
  }

  private MultiValueMap<String, String> clientCredentials() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add(FORM_CLIENT_ID, properties.clientId());
    form.add(FORM_CLIENT_SECRET, properties.clientSecret());
    return form;
  }

  private KeycloakCredential passwordCredential(String password) {
    return new KeycloakCredential(PASSWORD_CREDENTIAL_TYPE, password, false);
  }

  private ApiException providerException(RestClientResponseException exception) {
    return ApiException.of(
        exception.getStatusCode().value(),
        "identity_provider_error",
        "Identity provider request failed.");
  }

  private ApiException sessionProviderException(RestClientResponseException exception) {
    int upstreamStatus = exception.getStatusCode().value();
    int status = upstreamStatus == 429 || upstreamStatus >= 500 ? 503 : 502;
    return ApiException.of(status, "identity_provider_error", "Identity provider request failed.");
  }

  private ApiException sessionUnavailable(RestClientException exception) {
    ApiException failure =
        ApiException.of(503, "identity_provider_unavailable", "Identity provider is unavailable.");
    failure.addSuppressed(exception);
    return failure;
  }

  private void revokeAfterFailedIssue(String refreshToken, RuntimeException failure) {
    try {
      revokeSession(refreshToken);
    } catch (RuntimeException exception) {
      failure.addSuppressed(exception);
    }
  }

  private record KeycloakUser(
      String username,
      String email,
      String firstName,
      boolean enabled,
      List<KeycloakCredential> credentials,
      Map<String, List<String>> attributes) {}

  private record KeycloakUserStatus(boolean enabled) {}

  private record KeycloakUserDetails(
      String id, String firstName, Map<String, List<String>> attributes) {}

  private record KeycloakCredentialSummary(String type) {}

  private record KeycloakCredential(String type, String value, boolean temporary) {}

  private record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") Long expiresIn,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("refresh_expires_in") Long refreshExpiresIn) {}

  private record TokenIntrospection(boolean active, String sub, String sid) {}
}
