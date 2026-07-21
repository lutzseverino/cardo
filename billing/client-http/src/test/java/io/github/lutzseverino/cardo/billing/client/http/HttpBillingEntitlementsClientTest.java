package io.github.lutzseverino.cardo.billing.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.billing.client.BillingEntitlement;
import io.github.lutzseverino.cardo.billing.client.BillingEntitlementStatus;
import io.github.lutzseverino.cardo.billing.client.BillingEntitlementsClient;
import io.github.lutzseverino.cardo.billing.client.http.generated.EntitlementResponse;
import io.github.lutzseverino.cardo.billing.client.http.generated.api.EntitlementsApi;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class HttpBillingEntitlementsClientTest {

  private final ApplicationContextRunner baseContext =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(BillingClientAutoConfiguration.class))
          .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
          .withBean(
              KeycloakClientCredentialsTokenProvider.class,
              () -> mock(KeycloakClientCredentialsTokenProvider.class));
  private final ApplicationContextRunner context =
      baseContext.withPropertyValues(
          "cardo.billing.client.base-url=http://billing.test/api/v1",
          "cardo.billing.client.service-token-scope=billing");

  @Test
  void autoConfiguresTheStableClientContract() {
    context.run(
        application -> {
          assertThat(application).hasSingleBean(BillingEntitlementsClient.class);
          BillingClientProperties properties = application.getBean(BillingClientProperties.class);
          assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
          assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(2));
          assertThat(properties.serviceTokenScope()).isEqualTo("billing");
        });
  }

  @Test
  void bindsClientTimeoutOverrides() {
    context
        .withPropertyValues(
            "cardo.billing.client.connect-timeout=500ms", "cardo.billing.client.read-timeout=3s")
        .run(
            application -> {
              BillingClientProperties properties =
                  application.getBean(BillingClientProperties.class);
              assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(500));
              assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(3));
            });
  }

  @Test
  void rejectsAnUnboundedClientTimeout() {
    context
        .withPropertyValues("cardo.billing.client.read-timeout=0s")
        .run(application -> assertThat(application).hasFailed());
  }

  @Test
  void rejectsMissingAndBlankServiceTokenScopesAtStartup() {
    baseContext
        .withPropertyValues("cardo.billing.client.base-url=http://billing.test/api/v1")
        .run(application -> assertThat(application).hasFailed());
    context
        .withPropertyValues("cardo.billing.client.service-token-scope=  ")
        .run(application -> assertThat(application).hasFailed());
  }

  @Test
  void requestsOnlyTheConfiguredBillingServiceTokenScope() {
    context
        .withPropertyValues("cardo.billing.client.base-url=http://localhost:1/api/v1")
        .run(
            application -> {
              KeycloakClientCredentialsTokenProvider tokens =
                  application.getBean(KeycloakClientCredentialsTokenProvider.class);
              when(tokens.clientCredentialsToken("billing")).thenReturn("billing-token");

              assertThatThrownBy(
                      () ->
                          application
                              .getBean(BillingEntitlementsClient.class)
                              .require(UUID.randomUUID(), "clinic"))
                  .isInstanceOf(RuntimeException.class);
              verify(tokens).clientCredentialsToken("billing");
              verify(tokens, org.mockito.Mockito.never()).clientCredentialsToken();
            });
  }

  @Test
  void registersAutoConfigurationForDiscovery() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains(BillingClientAutoConfiguration.class.getName());
  }

  @Test
  void returnsEntitlementAcrossTheGeneratedClientBoundary() {
    EntitlementsApi entitlements = mock(EntitlementsApi.class);
    HttpBillingEntitlementsClient client = new HttpBillingEntitlementsClient(entitlements);
    UUID subjectId = UUID.randomUUID();
    UUID entitlementId = UUID.randomUUID();
    OffsetDateTime createdAt = OffsetDateTime.parse("2026-06-29T05:00:00Z");
    when(entitlements.requireSubjectEntitlement(subjectId, "clinic"))
        .thenReturn(
            new EntitlementResponse()
                .id(entitlementId)
                .subjectId(subjectId)
                .product("clinic")
                .status(EntitlementResponse.StatusEnum.ACTIVE)
                .tenantLimit(3)
                .seatLimit(12)
                .createdAt(createdAt)
                .updatedAt(createdAt.plusHours(1)));

    BillingEntitlement entitlement = client.require(subjectId, "clinic");

    assertThat(entitlement.id()).isEqualTo(entitlementId);
    assertThat(entitlement.subjectId()).isEqualTo(subjectId);
    assertThat(entitlement.product()).isEqualTo("clinic");
    assertThat(entitlement.status()).isEqualTo(BillingEntitlementStatus.ACTIVE);
    assertThat(entitlement.tenantLimit()).isEqualTo(3);
    assertThat(entitlement.seatLimit()).isEqualTo(12);
    assertThat(entitlement.createdAt()).isEqualTo(createdAt);
    assertThat(entitlement.updatedAt()).isEqualTo(createdAt.plusHours(1));
  }
}
