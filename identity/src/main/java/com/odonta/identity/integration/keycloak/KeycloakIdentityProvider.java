package com.odonta.identity.integration.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.odonta.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import com.odonta.authorization.keycloak.KeycloakRealmAdminClient;
import com.odonta.authorization.spring.OdontaJwtClaims;
import com.odonta.common.api.ApiException;
import com.odonta.identity.config.KeycloakProperties;
import com.odonta.identity.provider.IdentityProvider;
import java.net.URI;
import java.time.Instant;
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
import org.springframework.web.client.RestClientResponseException;

@Component
public class KeycloakIdentityProvider implements IdentityProvider {

  private static final String FORM_CLIENT_ID = "client_id";
  private static final String FORM_CLIENT_SECRET = "client_secret";
  private static final String FORM_GRANT_TYPE = "grant_type";
  private static final String PASSWORD_CREDENTIAL_TYPE = "password";
  private static final String FORM_PASSWORD = PASSWORD_CREDENTIAL_TYPE;
  private static final String FORM_TOKEN = "token";

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
  public ProvisionedIdentity provisionPasswordIdentity(String email, String password, String name) {
    return createUser(
        new KeycloakUser(email, email, name, true, List.of(passwordCredential(password))));
  }

  @Override
  public ProvisionedIdentity provisionProvisionalIdentity(String email) {
    return createUser(new KeycloakUser(email, email, null, true, List.of()));
  }

  @Override
  public void completePasswordIdentity(String subject, String password, String name) {
    try {
      rest.put()
          .uri("/admin/realms/{realm}/users/{subject}", properties.realm(), subject)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
          .body(new KeycloakUser(null, null, name, true, null))
          .retrieve()
          .toBodilessEntity();
      rest.put()
          .uri("/admin/realms/{realm}/users/{subject}/reset-password", properties.realm(), subject)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
          .body(passwordCredential(password))
          .retrieve()
          .toBodilessEntity();
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
    }
  }

  @Override
  public void bindUserId(String subject, UUID userId) {
    try {
      admin.bindUserAttribute(subject, OdontaJwtClaims.IDENTITY_USER_ID, userId.toString());
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
                    clientId, OdontaJwtClaims.IDENTITY_USER_ID, OdontaJwtClaims.IDENTITY_USER_ID));
  }

  @Override
  public IssuedIdentityToken issuePasswordToken(String email, String password) {
    TokenResponse token = token(passwordGrant(email, password));
    String accessToken = token.accessToken();
    TokenIntrospection introspection = introspect(accessToken);
    return new IssuedIdentityToken(
        accessToken, expiresAt(introspection.exp()), introspection.sub(), introspection.sid());
  }

  @Override
  public void revokeToken(String token) {
    try {
      rest.post()
          .uri("/realms/{realm}/protocol/openid-connect/revoke", properties.realm())
          .body(revocationRequest(token))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      throw providerException(exception);
    }
  }

  private String adminToken() {
    return clientCredentialsTokens.clientCredentialsToken();
  }

  private TokenResponse token(MultiValueMap<String, String> form) {
    try {
      TokenResponse token =
          rest.post()
              .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
              .body(form)
              .retrieve()
              .body(TokenResponse.class);
      if (token == null || token.accessToken() == null) {
        throw ApiException.of(
            502, "identity_provider_token_missing", "Identity provider did not return a token.");
      }
      return token;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 400 || exception.getStatusCode().value() == 401) {
        throw ApiException.badRequest("invalid_credentials", "Invalid credentials.");
      }
      throw providerException(exception);
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
      return introspection;
    } catch (RestClientResponseException exception) {
      throw providerException(exception);
    }
  }

  private MultiValueMap<String, String> passwordGrant(String email, String password) {
    MultiValueMap<String, String> form = clientCredentials();
    form.add(FORM_GRANT_TYPE, PASSWORD_CREDENTIAL_TYPE);
    form.add("username", email);
    form.add(FORM_PASSWORD, password);
    return form;
  }

  private MultiValueMap<String, String> revocationRequest(String token) {
    return tokenRequest(token);
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

  private OffsetDateTime expiresAt(Long epochSeconds) {
    if (epochSeconds == null) {
      return null;
    }
    return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
  }

  private ApiException providerException(RestClientResponseException exception) {
    return ApiException.of(
        exception.getStatusCode().value(),
        "identity_provider_error",
        "Identity provider request failed.");
  }

  private record KeycloakUser(
      String username,
      String email,
      String firstName,
      boolean enabled,
      List<KeycloakCredential> credentials) {}

  private record KeycloakCredential(String type, String value, boolean temporary) {}

  private record TokenResponse(@JsonProperty("access_token") String accessToken) {}

  private record TokenIntrospection(
      boolean active, String sub, String sid, Long exp, Map<String, Object> claims) {}
}
