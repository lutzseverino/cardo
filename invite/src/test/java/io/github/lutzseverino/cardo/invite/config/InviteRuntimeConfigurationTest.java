package io.github.lutzseverino.cardo.invite.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class InviteRuntimeConfigurationTest {

  @Test
  void acceptsLocalAndIsolatedProductionConfigurations() throws Exception {
    policy(
            new InviteRuntimeProperties(null, null, null),
            new ProductCallerProperties(Set.of()),
            new MockEnvironment())
        .afterPropertiesSet();
    policy(production(), new ProductCallerProperties(Set.of("clinic")), productionEnvironment())
        .afterPropertiesSet();
  }

  @Test
  void preservesPublicSetConstructorAndAccessorContract() {
    ProductCallerProperties properties = new ProductCallerProperties(Set.of("clinic", "polity"));

    assertThat(properties.allowedClientIds()).containsExactlyInAnyOrder("clinic", "polity");
    assertThatThrownBy(() -> properties.allowedClientIds().add("another"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void preservesPublicConfigurationPropertiesContract() {
    ConfigurationProperties annotation =
        ProductCallerProperties.class.getAnnotation(ConfigurationProperties.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.prefix()).isEqualTo("cardo.invite.product-callers");
  }

  @Test
  void publicPropertiesRemainDirectlyEnableableByConsumers() {
    new ApplicationContextRunner()
        .withUserConfiguration(PublicProductCallerPropertiesConfiguration.class)
        .withPropertyValues(
            "cardo.invite.product-callers.allowed-client-ids[0]=clinic",
            "cardo.invite.product-callers.allowed-client-ids[1]=polity")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(ProductCallerProperties.class);
              assertThat(context.getBean(ProductCallerProperties.class).allowedClientIds())
                  .containsExactlyInAnyOrder("clinic", "polity");
            });
  }

  @Test
  void rejectsCanonicalLoopbackIdentitySmtpAndDatasourceEndpoints() {
    MockEnvironment identityClient = productionEnvironment();
    identityClient.setProperty("cardo.identity.client.base-url", "https://localhost./api/v1");
    assertThatThrownBy(
            () ->
                policy(production(), new ProductCallerProperties(Set.of("clinic")), identityClient)
                    .afterPropertiesSet())
        .hasMessageContaining("cardo.identity.client.base-url");

    MockEnvironment smtp = productionEnvironment();
    smtp.setProperty("spring.mail.host", "127.1");
    assertThatThrownBy(
            () ->
                policy(production(), new ProductCallerProperties(Set.of("clinic")), smtp)
                    .afterPropertiesSet())
        .hasMessageContaining("spring.mail.host");

    MockEnvironment datasource = productionEnvironment();
    datasource.setProperty("spring.datasource.url", "jdbc:postgresql://[::1]:5432/cardo_invite");
    assertThatThrownBy(
            () ->
                policy(production(), new ProductCallerProperties(Set.of("clinic")), datasource)
                    .afterPropertiesSet())
        .hasMessageContaining("spring.datasource");
  }

  @ParameterizedTest
  @ValueSource(strings = {".", "..", "[]"})
  void rejectsSmtpHostsThatNormalizeToEmpty(String host) {
    MockEnvironment smtp = productionEnvironment();
    smtp.setProperty("spring.mail.host", host);

    assertThatThrownBy(
            () ->
                policy(production(), new ProductCallerProperties(Set.of("clinic")), smtp)
                    .afterPropertiesSet())
        .hasMessageContaining("spring.mail.host");
  }

  @Test
  void yamlListBindingPreservesDuplicatesForValidation() {
    new ApplicationContextRunner()
        .withUserConfiguration(ProductCallerConfiguration.class)
        .withInitializer(
            context -> {
              try {
                var source =
                    new YamlPropertySourceLoader()
                        .load(
                            "duplicate-product-callers",
                            new ClassPathResource("duplicate-product-callers.yml"))
                        .getFirst();
                context.getEnvironment().getPropertySources().addFirst(source);
              } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
              }
            })
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .rootCause()
                  .hasMessageContaining("allowed-client-ids");
            });
  }

  @Test
  void bindingMapsAValidRawListToThePublicSetContract() {
    new ApplicationContextRunner()
        .withUserConfiguration(ProductCallerConfiguration.class)
        .withPropertyValues(
            "cardo.invite.product-callers.allowed-client-ids[0]=clinic",
            "cardo.invite.product-callers.allowed-client-ids[1]=polity")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(ProductCallerProperties.class);
              assertThat(context.getBean(ProductCallerProperties.class).allowedClientIds())
                  .isInstanceOf(Set.class)
                  .containsExactlyInAnyOrder("clinic", "polity");
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(ProductCallerProperties.class)
  static class PublicProductCallerPropertiesConfiguration {}

  @Test
  void rejectsNonPositiveSmtpAndWorkflowBounds() {
    assertThatThrownBy(
            () ->
                new InviteRuntimeProperties(
                    null, Duration.ofNanos(1), Duration.ofSeconds(Long.MAX_VALUE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1ms");
    assertThatThrownBy(() -> new SmtpTimeoutProperties(Duration.ZERO, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("connect-timeout");
    assertThatThrownBy(() -> new SmtpTimeoutProperties(Duration.ofNanos(1), null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1ms");
    assertThatThrownBy(
            () -> new SmtpTimeoutProperties(Duration.ofSeconds(Long.MAX_VALUE), null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1ms");
    assertThatThrownBy(
            () ->
                new InvitationCompletionProperties(
                    Duration.ofSeconds(1),
                    Duration.ZERO,
                    Duration.ofSeconds(1),
                    Duration.ofMinutes(1),
                    3,
                    10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("poll-delay");
  }

  @Test
  void generatedMetadataContainsRuntimeAndSmtpProperties() throws Exception {
    String metadata =
        new String(
            getClass()
                .getResourceAsStream("/META-INF/spring-configuration-metadata.json")
                .readAllBytes());
    assertThat(metadata)
        .contains("cardo.invite.runtime.connect-timeout")
        .contains("cardo.invite.smtp.write-timeout");
  }

  @Test
  void smtpReadBoundStopsAStalledServerGreeting() throws Exception {
    CountDownLatch connectionAccepted = new CountDownLatch(1);
    CountDownLatch releaseServer = new CountDownLatch(1);
    try (ServerSocket server = new ServerSocket(0);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      executor.submit(
          () -> {
            try (var socket = server.accept()) {
              connectionAccepted.countDown();
              releaseServer.await(5, TimeUnit.SECONDS);
            }
            return null;
          });
      org.springframework.boot.mail.autoconfigure.MailProperties mail =
          new org.springframework.boot.mail.autoconfigure.MailProperties();
      mail.setHost("localhost");
      mail.setPort(server.getLocalPort());
      var sender =
          new InviteRuntimeConfiguration()
              .javaMailSender(
                  mail,
                  new SmtpTimeoutProperties(
                      Duration.ofMillis(100), Duration.ofMillis(100), Duration.ofMillis(100)));
      org.springframework.mail.SimpleMailMessage message =
          new org.springframework.mail.SimpleMailMessage();
      message.setFrom("sender@example.com");
      message.setTo("recipient@example.com");
      message.setSubject("bounded");
      message.setText("bounded");

      try {
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
            Duration.ofSeconds(2),
            () ->
                assertThatThrownBy(() -> sender.send(message))
                    .isInstanceOf(org.springframework.mail.MailException.class));
        assertThat(connectionAccepted.await(1, TimeUnit.SECONDS)).isTrue();
      } finally {
        releaseServer.countDown();
      }
    }
  }

  private org.springframework.beans.factory.InitializingBean policy(
      InviteRuntimeProperties runtime,
      ProductCallerProperties callers,
      MockEnvironment environment) {
    return new InviteRuntimeConfiguration()
        .inviteProductionConfigurationPolicy(
            runtime,
            new KeycloakProperties(
                runtime.mode() == InviteRuntimeProperties.Mode.PRODUCTION
                    ? "https://id.example.com"
                    : "http://localhost:8080",
                "cardo",
                "cardo-invite",
                runtime.mode() == InviteRuntimeProperties.Mode.PRODUCTION ? "invite-secret" : ""),
            callers,
            environment);
  }

  private InviteRuntimeProperties production() {
    return new InviteRuntimeProperties(
        InviteRuntimeProperties.Mode.PRODUCTION, Duration.ofSeconds(1), Duration.ofSeconds(2));
  }

  private MockEnvironment productionEnvironment() {
    return new MockEnvironment()
        .withProperty(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            "https://id.example.com/realms/cardo")
        .withProperty("cardo.invite.product-callers.allowed-client-ids", "clinic")
        .withProperty("cardo.identity.client.base-url", "https://identity.example.com/api/v1")
        .withProperty("cardo.identity.client.service-token-scope", "identity")
        .withProperty("spring.mail.host", "smtp.example.com")
        .withProperty("spring.mail.port", "587")
        .withProperty("spring.mail.username", "mailer")
        .withProperty("spring.mail.password", "smtp-secret")
        .withProperty("spring.mail.properties.mail.smtp.auth", "true")
        .withProperty("spring.mail.properties.mail.smtp.starttls.enable", "true")
        .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.com:5432/cardo_invite")
        .withProperty("spring.datasource.username", "cardo_invite_app")
        .withProperty("spring.datasource.password", "invite-db-secret");
  }
}
