package io.github.lutzseverino.cardo.invite.config;

import io.github.lutzseverino.cardo.common.runtime.NetworkEndpointSafety;
import java.net.URI;
import java.time.Duration;
import java.util.Properties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
class InviteRuntimeConfiguration {

  @Bean
  InitializingBean inviteProductionConfigurationPolicy(
      InviteRuntimeProperties runtime,
      KeycloakProperties keycloak,
      ProductCallerProperties callers,
      Environment environment) {
    return () -> {
      if (runtime.mode() != InviteRuntimeProperties.Mode.PRODUCTION) {
        return;
      }
      requireRemoteHttps("cardo.invite.keycloak.base-url", keycloak.baseUrl());
      requireText("cardo.invite.keycloak.realm", keycloak.realm());
      requireText("cardo.invite.keycloak.client-id", keycloak.clientId());
      requireSecret("cardo.invite.keycloak.client-secret", keycloak.clientSecret());
      if (callers.allowedClientIds().isEmpty()) {
        throw invalid(
            "cardo.invite.product-callers.allowed-client-ids",
            "must contain at least one product client");
      }
      String expectedIssuer =
          stripSlash(keycloak.baseUrl()) + "/realms/" + keycloak.realm().strip();
      String issuer =
          environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
      requireRemoteHttps("spring.security.oauth2.resourceserver.jwt.issuer-uri", issuer);
      if (!expectedIssuer.equals(stripSlash(issuer))) {
        throw invalid(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            "must match the configured Invite Keycloak realm");
      }
      requireRemoteHttp(
          "cardo.identity.client.base-url",
          environment.getProperty("cardo.identity.client.base-url"));
      requireText(
          "cardo.identity.client.service-token-scope",
          environment.getProperty("cardo.identity.client.service-token-scope"));
      validateSmtp(environment);
      requireIsolatedDatasource(environment);
    };
  }

  @Bean("inviteOutboundRestClientBuilder")
  RestClient.Builder inviteOutboundRestClientBuilder(InviteRuntimeProperties properties) {
    return RestClient.builder()
        .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()));
  }

  @Bean("inviteJwkRestOperations")
  RestOperations inviteJwkRestOperations(InviteRuntimeProperties properties) {
    return new RestTemplate(requestFactory(properties.connectTimeout(), properties.readTimeout()));
  }

  @Bean
  JavaMailSender javaMailSender(MailProperties mail, SmtpTimeoutProperties timeouts) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(mail.getHost());
    sender.setPort(mail.getPort());
    sender.setUsername(mail.getUsername());
    sender.setPassword(mail.getPassword());
    sender.setProtocol(mail.getProtocol());
    sender.setDefaultEncoding(mail.getDefaultEncoding().name());
    Properties properties = new Properties();
    properties.putAll(mail.getProperties());
    properties.setProperty(
        "mail.smtp.connectiontimeout", Long.toString(timeouts.connectTimeout().toMillis()));
    properties.setProperty("mail.smtp.timeout", Long.toString(timeouts.readTimeout().toMillis()));
    properties.setProperty(
        "mail.smtp.writetimeout", Long.toString(timeouts.writeTimeout().toMillis()));
    sender.setJavaMailProperties(properties);
    return sender;
  }

  private SimpleClientHttpRequestFactory requestFactory(Duration connect, Duration read) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connect);
    factory.setReadTimeout(read);
    return factory;
  }

  private static void validateSmtp(Environment environment) {
    String host = environment.getProperty("spring.mail.host");
    requireText("spring.mail.host", host);
    if (NetworkEndpointSafety.isLocalOrUnspecified(host)) {
      throw invalid("spring.mail.host", "must be remote in production");
    }
    Integer port = environment.getProperty("spring.mail.port", Integer.class);
    if (port == null || port < 1 || port > 65535) {
      throw invalid("spring.mail.port", "must be between 1 and 65535");
    }
    if (!environment.getProperty("spring.mail.properties.mail.smtp.auth", Boolean.class, false)) {
      throw invalid("spring.mail.properties.mail.smtp.auth", "must be true in production");
    }
    if (!environment.getProperty(
        "spring.mail.properties.mail.smtp.starttls.enable", Boolean.class, false)) {
      throw invalid(
          "spring.mail.properties.mail.smtp.starttls.enable", "must be true in production");
    }
    if (!environment.getProperty(
        "spring.mail.properties.mail.smtp.starttls.required", Boolean.class, false)) {
      throw invalid(
          "spring.mail.properties.mail.smtp.starttls.required", "must be true in production");
    }
    requireText("spring.mail.username", environment.getProperty("spring.mail.username"));
    requireSecret("spring.mail.password", environment.getProperty("spring.mail.password"));
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
          "spring.datasource", "must use a non-local Invite-owned database and a dedicated role");
    }
  }

  private static void requireRemoteHttps(String property, String value) {
    requireRemoteUri(property, value, true);
  }

  private static void requireRemoteHttp(String property, String value) {
    requireRemoteUri(property, value, false);
  }

  private static void requireRemoteUri(String property, String value, boolean httpsOnly) {
    requireText(property, value);
    try {
      URI uri = URI.create(value);
      String host = uri.getHost();
      boolean validScheme =
          httpsOnly
              ? "https".equalsIgnoreCase(uri.getScheme())
              : "https".equalsIgnoreCase(uri.getScheme())
                  || "http".equalsIgnoreCase(uri.getScheme());
      if (!validScheme || host == null || NetworkEndpointSafety.isLocalOrUnspecified(host)) {
        throw invalid(
            property,
            "must be a remote " + (httpsOnly ? "HTTPS" : "HTTP(S)") + " URI in production");
      }
    } catch (IllegalArgumentException exception) {
      if (exception.getMessage() != null
          && exception.getMessage().startsWith("Invalid production")) {
        throw exception;
      }
      throw invalid(property, "must be a valid remote URI in production");
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
