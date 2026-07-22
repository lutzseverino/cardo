package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.github.lutzseverino.cardo.identity.productauth.ActiveTokenValidator;
import io.github.lutzseverino.cardo.identity.productauth.IdentityProductAuthAutoConfiguration;
import io.github.lutzseverino.cardo.identity.productauth.ProductRequestRules;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

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
  void startsProductAuthWithTheSingleReferenceBuilder() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(IdentityProductAuthAutoConfiguration.class))
        .withUserConfiguration(ReferenceProductConfiguration.class)
        .withBean(ReferenceGrantGate.class, ReferenceGrantGate::new)
        .withPropertyValues(
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
            "reference.keycloak.catalog-client-secret=catalog-secret")
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              assertThat(application).hasSingleBean(RestClient.Builder.class);
              assertThat(application).hasSingleBean(ActiveTokenValidator.class);
              assertThat(application).hasBean("referenceAuthorization");
            });
  }

  @Test
  void limitsTheReferenceProductToItsDocumentedPublicAndAuthenticatedRoutes() {
    ProductRequestRules rules = mock(ProductRequestRules.class, Answers.RETURNS_SELF);

    new ReferenceProductConfiguration().referenceRequestPolicy().authorize(rules);

    verify(rules).permitAll(HttpMethod.GET, "/");
    verify(rules).permitAll("/invitations/**");
    verify(rules).permitAll("/internal/reference/**");
    verify(rules).authenticated(HttpMethod.POST, "/api/reference/invitations");
    verify(rules)
        .hasAuthority(
            ReferenceContract.TENANT_AUTHORITY, HttpMethod.GET, "/api/reference/tenants/*");
    verify(rules).authenticated("/api/reference/billing/**");
    verifyNoMoreInteractions(rules);
  }
}
