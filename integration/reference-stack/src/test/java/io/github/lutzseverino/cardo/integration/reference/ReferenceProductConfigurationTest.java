package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.lutzseverino.cardo.identity.productauth.ActiveTokenValidator;
import io.github.lutzseverino.cardo.identity.productauth.IdentityProductAuthAutoConfiguration;
import io.github.lutzseverino.cardo.identity.productauth.ProductRequestRules;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

class ReferenceProductConfigurationTest {

  @Test
  void suppliesTheBoundedBuilderRequiredByProductAuth() {
    RestClient.Builder builder = new ReferenceProductConfiguration().referenceRestClientBuilder();

    Object requestFactory = ReflectionTestUtils.getField(builder, "requestFactory");
    assertThat(requestFactory).isInstanceOf(SimpleClientHttpRequestFactory.class);
    assertThat(ReflectionTestUtils.getField(requestFactory, "connectTimeout")).isEqualTo(2000);
    assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).isEqualTo(2000);
  }

  @Test
  void startsProductAuthAndExposesOnlyTheStandardAnonymousHealthProbes() {
    new WebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                SecurityAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class,
                IdentityProductAuthAutoConfiguration.class))
        .withUserConfiguration(ReferenceProductConfiguration.class, TestActuatorRoutes.class)
        .withBean(ReferenceGrantGate.class, ReferenceGrantGate::new)
        .withPropertyValues(productProperties())
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              assertThat(application).hasSingleBean(RestClient.Builder.class);
              assertThat(application).hasSingleBean(ActiveTokenValidator.class);
              assertThat(application).hasBean("referenceAuthorization");
              var mvc =
                  MockMvcBuilders.webAppContextSetup((WebApplicationContext) application)
                      .addFilters(application.getBean(FilterChainProxy.class))
                      .build();

              mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
              mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
              mvc.perform(get("/actuator/health/environment")).andExpect(status().isUnauthorized());
            });
  }

  @Test
  void limitsTheReferenceProductToItsDocumentedPublicAndAuthenticatedRoutes() {
    ProductRequestRules rules = mock(ProductRequestRules.class, Answers.RETURNS_SELF);

    new ReferenceProductConfiguration().referenceRequestPolicy().authorize(rules);

    verify(rules).permitAll(HttpMethod.GET, "/");
    verify(rules).permitAll("/invitations/**");
    verify(rules).permitAll("/internal/reference/**");
    verify(rules).permitAll(HttpMethod.GET, "/actuator/health/liveness");
    verify(rules).permitAll(HttpMethod.GET, "/actuator/health/readiness");
    verify(rules).authenticated(HttpMethod.POST, "/api/reference/invitations");
    verify(rules)
        .hasAuthority(
            ReferenceContract.TENANT_AUTHORITY, HttpMethod.GET, "/api/reference/tenants/*");
    verify(rules).authenticated("/api/reference/billing/**");
    verifyNoMoreInteractions(rules);
  }

  private String[] productProperties() {
    return new String[] {
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.test/realms/cardo",
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.test/jwks",
      "cardo.identity.product-auth.identity-session-audience=identity",
      "cardo.identity.product-auth.product-audience=reference-product",
      "cardo.identity.product-auth.active-token-validation.enabled=true",
      "cardo.identity.product-auth.active-token-validation.introspection-uri=https://identity.test/introspect",
      "cardo.identity.product-auth.active-token-validation.client-id=reference-product",
      "cardo.identity.product-auth.active-token-validation.client-secret=product-secret",
      "reference.keycloak.base-url=https://identity.test",
      "reference.keycloak.realm=cardo",
      "reference.keycloak.outbound-client-id=reference-product-outbound",
      "reference.keycloak.outbound-client-secret=outbound-secret",
      "reference.keycloak.catalog-client-id=reference-product",
      "reference.keycloak.catalog-client-secret=catalog-secret"
    };
  }

  @Configuration(proxyBeanMethods = false)
  static class TestActuatorRoutes {

    @Bean
    TestActuatorController testActuatorController() {
      return new TestActuatorController();
    }
  }

  @RestController
  static class TestActuatorController {

    @GetMapping({
      "/actuator/health/liveness",
      "/actuator/health/readiness",
      "/actuator/health/environment"
    })
    String health() {
      return "UP";
    }
  }
}
