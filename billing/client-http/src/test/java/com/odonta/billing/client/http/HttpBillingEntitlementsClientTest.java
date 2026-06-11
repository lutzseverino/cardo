package com.odonta.billing.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odonta.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import com.odonta.billing.client.BillingEntitlementsClient;
import com.odonta.billing.client.http.generated.EntitlementResponse;
import com.odonta.billing.client.http.generated.api.EntitlementsApi;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HttpBillingEntitlementsClientTest {

  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(BillingClientAutoConfiguration.class))
          .withBean(ObjectMapper.class)
          .withBean(
              KeycloakClientCredentialsTokenProvider.class,
              () -> mock(KeycloakClientCredentialsTokenProvider.class))
          .withPropertyValues("odonta.billing.client.base-url=http://billing.test/api/v1");

  @Test
  void autoConfiguresTheStableClientContract() {
    context.run(
        application -> assertThat(application).hasSingleBean(BillingEntitlementsClient.class));
  }

  @Test
  void registersAutoConfigurationForDiscovery() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains(BillingClientAutoConfiguration.class.getName());
  }

  @Test
  void returnsTenantLimitAcrossTheGeneratedClientBoundary() {
    EntitlementsApi entitlements = mock(EntitlementsApi.class);
    HttpBillingEntitlementsClient client = new HttpBillingEntitlementsClient(entitlements);
    UUID subjectId = UUID.randomUUID();
    when(entitlements.requireSubjectEntitlement(subjectId, "clinic"))
        .thenReturn(new EntitlementResponse().tenantLimit(3));

    assertThat(client.requireTenantLimit(subjectId, "clinic")).isEqualTo(3);
  }
}
