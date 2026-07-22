package io.github.lutzseverino.cardo.billing.config;

import io.github.lutzseverino.cardo.common.runtime.NetworkEndpointSafety;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

@Configuration
class BillingRuntimeConfiguration {

  @Bean
  InitializingBean billingProductionConfigurationPolicy(
      BillingRuntimeProperties runtime,
      KeycloakProperties keycloak,
      StripeProperties stripe,
      Environment environment) {
    return () -> {
      if (runtime.mode() != BillingRuntimeProperties.Mode.PRODUCTION) {
        return;
      }
      requireRemoteHttps("cardo.billing.keycloak.base-url", keycloak.baseUrl());
      requireText("cardo.billing.keycloak.realm", keycloak.realm());
      String expectedIssuer =
          stripSlash(keycloak.baseUrl()) + "/realms/" + keycloak.realm().strip();
      String issuer =
          environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
      requireRemoteHttps("spring.security.oauth2.resourceserver.jwt.issuer-uri", issuer);
      if (!expectedIssuer.equals(stripSlash(issuer))) {
        throw invalid(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            "must match the configured Billing Keycloak realm");
      }
      requireSecret("cardo.billing.stripe.secret-key", stripe.secretKey());
      requireSecret("cardo.billing.stripe.webhook-secret", stripe.webhookSecret());
      if (stripe.checkoutPrices().isEmpty()) {
        throw invalid(
            "cardo.billing.stripe.checkout-prices",
            "must contain at least one price-to-product mapping");
      }
      requireIsolatedDatasource(environment);
    };
  }

  @Bean("billingJwkRestOperations")
  RestOperations billingJwkRestOperations(BillingRuntimeProperties properties) {
    SimpleClientHttpRequestFactory factory =
        requestFactory(properties.jwkConnectTimeout(), properties.jwkReadTimeout());
    return new RestTemplate(factory);
  }

  private SimpleClientHttpRequestFactory requestFactory(Duration connect, Duration read) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connect);
    factory.setReadTimeout(read);
    return factory;
  }

  private static void requireIsolatedDatasource(Environment environment) {
    String url = environment.getProperty("spring.datasource.url");
    String username = environment.getProperty("spring.datasource.username");
    requireText("spring.datasource.url", url);
    requireText("spring.datasource.username", username);
    requireSecret(
        "spring.datasource.password", environment.getProperty("spring.datasource.password"));
    String normalizedUrl = url.toLowerCase(java.util.Locale.ROOT);
    if (unsafeDatasourceHost(url)
        || normalizedUrl.matches(".*[/]cardo(?:\\?.*)?$")
        || "cardo".equals(username)) {
      throw invalid(
          "spring.datasource", "must use a non-local Billing-owned database and a dedicated role");
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
    if (value == null || value.isBlank()) {
      throw invalid(property, "is required in production");
    }
  }

  private static void requireSecret(String property, String value) {
    if (value == null || value.isBlank() || "cardo".equals(value)) {
      throw invalid(property, "must be supplied as a non-development secret");
    }
  }

  private static String stripSlash(String value) {
    return value == null ? "" : value.strip().replaceFirst("/+$", "");
  }

  private static IllegalStateException invalid(String property, String requirement) {
    return new IllegalStateException(
        "Invalid production property " + property + ": " + requirement + ".");
  }
}
