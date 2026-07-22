package io.github.lutzseverino.cardo.identity.config;

import io.github.lutzseverino.cardo.common.runtime.NetworkEndpointSafety;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

@Configuration
class IdentityRuntimeConfiguration {

  @Bean
  InitializingBean identityProductionConfigurationPolicy(
      IdentityRuntimeProperties runtime,
      SessionProperties session,
      KeycloakProperties keycloak,
      Environment environment) {
    return () -> {
      if (runtime.mode() != IdentityRuntimeProperties.Mode.PRODUCTION) {
        return;
      }
      if (session.mode() != SessionProperties.Mode.PRODUCTION) {
        throw invalid("cardo.identity.session.mode", "must be production");
      }
      requireRemoteHttps("cardo.identity.keycloak.base-url", keycloak.baseUrl());
      requireText("cardo.identity.keycloak.realm", keycloak.realm());
      requireText("cardo.identity.keycloak.client-id", keycloak.clientId());
      requireSecret("cardo.identity.keycloak.client-secret", keycloak.clientSecret());
      requireSecret(
          "cardo.identity.keycloak.authorization-client-secret",
          keycloak.authorizationClientSecret());
      if (keycloak.clientSecret().equals(keycloak.authorizationClientSecret())) {
        throw invalid(
            "cardo.identity.keycloak.authorization-client-secret",
            "must be distinct from cardo.identity.keycloak.client-secret");
      }
      requireText(
          "cardo.identity.keycloak.credential-setup-client-id", keycloak.credentialSetupClientId());
      requireRemoteHttps(
          "cardo.identity.keycloak.credential-setup-redirect-uri",
          keycloak.credentialSetupRedirectUri() == null
              ? null
              : keycloak.credentialSetupRedirectUri().toString());
      requireUniqueNonBlank(
          "cardo.identity.keycloak.user-id-claim-client-ids", keycloak.userIdClaimClientIds());
      String expectedIssuer =
          stripSlash(keycloak.baseUrl()) + "/realms/" + keycloak.realm().strip();
      String issuer =
          environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
      requireRemoteHttps("spring.security.oauth2.resourceserver.jwt.issuer-uri", issuer);
      if (!expectedIssuer.equals(stripSlash(issuer))) {
        throw invalid(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            "must match the configured Identity Keycloak realm");
      }
      requireIsolatedDatasource(environment, "identity");
    };
  }

  @Bean("identityOutboundRestClientBuilder")
  RestClient.Builder identityOutboundRestClientBuilder(IdentityRuntimeProperties properties) {
    return RestClient.builder()
        .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()));
  }

  @Bean("identityJwkRestOperations")
  RestOperations identityJwkRestOperations(IdentityRuntimeProperties properties) {
    return new RestTemplate(requestFactory(properties.connectTimeout(), properties.readTimeout()));
  }

  private SimpleClientHttpRequestFactory requestFactory(Duration connect, Duration read) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connect);
    factory.setReadTimeout(read);
    return factory;
  }

  private static void requireIsolatedDatasource(Environment environment, String service) {
    String url = environment.getProperty("spring.datasource.url");
    String username = environment.getProperty("spring.datasource.username");
    String password = environment.getProperty("spring.datasource.password");
    requireText("spring.datasource.url", url);
    requireText("spring.datasource.username", username);
    requireSecret("spring.datasource.password", password);
    String normalizedUrl = url.toLowerCase(java.util.Locale.ROOT);
    if (unsafeDatasourceHost(url)
        || normalizedUrl.matches(".*[/]cardo(?:\\?.*)?$")
        || "cardo".equals(username)) {
      throw invalid(
          "spring.datasource",
          "must use a non-local " + service + "-owned database and a dedicated role");
    }
  }

  private static void requireUniqueNonBlank(String property, List<String> values) {
    if (values == null
        || values.isEmpty()
        || values.stream().anyMatch(IdentityRuntimeConfiguration::blank)
        || new HashSet<>(values).size() != values.size()) {
      throw invalid(property, "must contain distinct non-blank values");
    }
  }

  private static void requireRemoteHttps(String property, String value) {
    requireText(property, value);
    try {
      URI uri = URI.create(value);
      String host = uri.getHost();
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || host == null
          || NetworkEndpointSafety.isLocalOrUnspecified(host)) {
        throw invalid(property, "must be a remote HTTPS URI in production");
      }
    } catch (IllegalArgumentException exception) {
      if (exception.getMessage() != null
          && exception.getMessage().startsWith("Invalid production")) {
        throw exception;
      }
      throw invalid(property, "must be a valid remote HTTPS URI in production");
    }
  }

  private static boolean unsafeDatasourceHost(String value) {
    try {
      URI uri = URI.create(value.substring("jdbc:".length()));
      return NetworkEndpointSafety.isLocalOrUnspecified(uri.getHost());
    } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
      return true;
    }
  }

  private static void requireText(String property, String value) {
    if (blank(value)) {
      throw invalid(property, "is required in production");
    }
  }

  private static void requireSecret(String property, String value) {
    if (blank(value)
        || java.util.Set.of(
                "cardo",
                "change-me",
                "changeme",
                "placeholder",
                "identity-runtime-secret",
                "identity-resource-secret")
            .contains(value.toLowerCase(java.util.Locale.ROOT))) {
      throw invalid(property, "must be supplied as a non-development secret");
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String stripSlash(String value) {
    return value == null ? "" : value.strip().replaceFirst("/+$", "");
  }

  private static IllegalStateException invalid(String property, String requirement) {
    return new IllegalStateException(
        "Invalid production property " + property + ": " + requirement + ".");
  }
}
