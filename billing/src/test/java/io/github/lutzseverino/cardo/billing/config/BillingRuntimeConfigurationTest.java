package io.github.lutzseverino.cardo.billing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class BillingRuntimeConfigurationTest {

  @Test
  void acceptsLocalAndIsolatedProductionConfigurations() throws Exception {
    policy(
            new BillingRuntimeProperties(null, null, null),
            new StripeProperties("", "", List.of(), null, null),
            new MockEnvironment())
        .afterPropertiesSet();
    policy(production(), productionStripe(), productionEnvironment()).afterPropertiesSet();
  }

  @Test
  void rejectsMissingProductionCatalogWithoutDisclosingSecrets() {
    MockEnvironment environment = productionEnvironment();
    environment.setProperty("spring.datasource.password", "database-secret-value");
    StripeProperties stripe =
        new StripeProperties("stripe-secret", "webhook-secret", List.of(), null, null);

    assertThatThrownBy(() -> policy(production(), stripe, environment).afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("checkout-prices")
        .hasMessageNotContaining("database-secret-value");
  }

  @Test
  void rejectsDuplicateCatalogAndNonPositiveBounds() {
    assertThatThrownBy(
            () ->
                new StripeProperties(
                    "key",
                    "webhook",
                    List.of(
                        new StripeProperties.CheckoutPrice("price_1", "clinic"),
                        new StripeProperties.CheckoutPrice("price_1", "polity")),
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unique ids and products");
    assertThatThrownBy(
            () ->
                new BillingRuntimeProperties(
                    BillingRuntimeProperties.Mode.LOCAL, Duration.ZERO, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("jwk-connect-timeout");
  }

  @Test
  void generatedMetadataContainsRuntimeAndStripeBounds() throws Exception {
    String metadata =
        new String(
            getClass()
                .getResourceAsStream("/META-INF/spring-configuration-metadata.json")
                .readAllBytes());
    assertThat(metadata)
        .contains("cardo.billing.runtime.jwk-read-timeout")
        .contains("cardo.billing.stripe.connect-timeout");
  }

  private org.springframework.beans.factory.InitializingBean policy(
      BillingRuntimeProperties runtime, StripeProperties stripe, MockEnvironment environment) {
    return new BillingRuntimeConfiguration()
        .billingProductionConfigurationPolicy(
            runtime,
            new KeycloakProperties(
                runtime.mode() == BillingRuntimeProperties.Mode.PRODUCTION
                    ? "https://id.example.com"
                    : "http://localhost:8080",
                "cardo"),
            stripe,
            environment);
  }

  private BillingRuntimeProperties production() {
    return new BillingRuntimeProperties(
        BillingRuntimeProperties.Mode.PRODUCTION, Duration.ofSeconds(1), Duration.ofSeconds(2));
  }

  private StripeProperties productionStripe() {
    return new StripeProperties(
        "stripe-secret",
        "webhook-secret",
        List.of(new StripeProperties.CheckoutPrice("price_clinic", "clinic")),
        Duration.ofSeconds(1),
        Duration.ofSeconds(2));
  }

  private MockEnvironment productionEnvironment() {
    return new MockEnvironment()
        .withProperty(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            "https://id.example.com/realms/cardo")
        .withProperty(
            "spring.datasource.url", "jdbc:postgresql://db.example.com:5432/cardo_billing")
        .withProperty("spring.datasource.username", "cardo_billing_app")
        .withProperty("spring.datasource.password", "billing-db-secret");
  }
}
