package io.github.lutzseverino.cardo.identity.productauth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthoritiesConverter;
import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class IdentityProductAuthAutoConfigurationTest {

  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(IdentityProductAuthAutoConfiguration.class));

  private final WebApplicationContextRunner webContext =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  SecurityAutoConfiguration.class,
                  ServletWebSecurityAutoConfiguration.class,
                  OAuth2ResourceServerAutoConfiguration.class,
                  IdentityProductAuthAutoConfiguration.class))
          .withPropertyValues(
              "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.test/jwks",
              "cardo.identity.product-auth.public-paths=/api/v1/workforce");

  @Test
  void autoConfiguresSharedProductAuthBeans() {
    context.run(
        application -> {
          assertThat(application).hasSingleBean(KeycloakAuthoritiesConverter.class);
          assertThat(application).hasSingleBean(PermissionEvaluator.class);
          assertThat(application).hasSingleBean(AuthenticatedUserReader.class);
          assertThat(application).hasSingleBean(MethodSecurityExpressionHandler.class);
          assertThat(application).hasSingleBean(BearerTokenResolver.class);
          assertThat(application).hasSingleBean(CsrfTokenRepository.class);
          assertThat(application).hasSingleBean(SessionCookieBearerTokenResolver.class);
          assertThat(application).hasSingleBean(ReadOnlyCsrfTokenRepository.class);
          assertThat(application).doesNotHaveBean(ActiveTokenValidator.class);
          assertThat(application).doesNotHaveBean(ActiveTokenValidationFilter.class);
        });
  }

  @Test
  void autoConfiguresActiveTokenValidationWhenEnabled() {
    context
        .withBean(RestClient.Builder.class, RestClient::builder)
        .withPropertyValues(
            "cardo.identity.product-auth.active-token-validation.enabled=true",
            "cardo.identity.product-auth.active-token-validation.introspection-uri=https://identity.test/introspect",
            "cardo.identity.product-auth.active-token-validation.client-id=clinic",
            "cardo.identity.product-auth.active-token-validation.client-secret=clinic-secret")
        .run(
            application -> {
              assertThat(application).hasSingleBean(ActiveTokenValidator.class);
              assertThat(application).hasSingleBean(ActiveTokenValidationFilter.class);
              assertThat(application)
                  .getBean(FilterRegistrationBean.class)
                  .extracting(FilterRegistrationBean::isEnabled)
                  .isEqualTo(false);
            });
  }

  @Test
  void rejectsIncompleteActiveTokenValidationConfiguration() {
    context
        .withBean(RestClient.Builder.class, RestClient::builder)
        .withPropertyValues(
            "cardo.identity.product-auth.active-token-validation.enabled=true",
            "cardo.identity.product-auth.active-token-validation.client-id=clinic",
            "cardo.identity.product-auth.active-token-validation.client-secret=clinic-secret")
        .run(
            application -> {
              assertThat(application).hasFailed();
              assertThat(application.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .hasRootCauseMessage("Active token validation requires an introspection URI.");
            });
  }

  @Test
  void registersAutoConfigurationForDiscovery() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains(IdentityProductAuthAutoConfiguration.class.getName());
  }

  @Test
  void autoConfiguresProductSecurityFilterChainInWebContexts() {
    webContext.run(
        application -> {
          assertThat(application).hasSingleBean(SecurityFilterChain.class);
          assertThat(application).hasSingleBean(BearerTokenResolver.class);
          var filters = application.getBean(SecurityFilterChain.class).getFilters();
          assertThat(filters.stream().filter(CsrfFilter.class::isInstance)).hasSize(1);
          assertThat(application).doesNotHaveBean(CsrfFilter.class);
          assertThat(indexOf(filters, BearerTokenAuthenticationFilter.class)).isGreaterThan(-1);
          assertThat(indexOf(filters, CsrfFilter.class))
              .isLessThan(indexOf(filters, BearerTokenAuthenticationFilter.class));
        });
  }

  @Test
  void hostSecurityBeansCannotReplaceTheCoordinatedCardoMechanics() {
    BearerTokenResolver hostBearerTokens = request -> "host-token";
    CsrfTokenRepository hostCsrfTokens = new HttpSessionCsrfTokenRepository();

    webContext
        .withBean("hostBearerTokenResolver", BearerTokenResolver.class, () -> hostBearerTokens)
        .withBean("hostCsrfTokenRepository", CsrfTokenRepository.class, () -> hostCsrfTokens)
        .run(
            application -> {
              SecurityFilterChain chain = application.getBean(SecurityFilterChain.class);
              BearerTokenAuthenticationFilter bearerFilter =
                  chain.getFilters().stream()
                      .filter(BearerTokenAuthenticationFilter.class::isInstance)
                      .map(BearerTokenAuthenticationFilter.class::cast)
                      .findFirst()
                      .orElseThrow();
              CsrfFilter csrfFilter =
                  chain.getFilters().stream()
                      .filter(CsrfFilter.class::isInstance)
                      .map(CsrfFilter.class::cast)
                      .findFirst()
                      .orElseThrow();

              Object authenticationConverter =
                  ReflectionTestUtils.getField(bearerFilter, "authenticationConverter");
              assertThat(
                      ReflectionTestUtils.getField(authenticationConverter, "bearerTokenResolver"))
                  .isSameAs(application.getBean(SessionCookieBearerTokenResolver.class));
              assertThat(ReflectionTestUtils.getField(csrfFilter, "tokenRepository"))
                  .isSameAs(application.getBean(ReadOnlyCsrfTokenRepository.class));
            });
  }

  @Test
  void rejectsAReadableCsrfCookieThatShadowsTheSessionCredential() {
    context
        .withPropertyValues(
            "cardo.identity.product-auth.session-cookie-name=cardo.session",
            "cardo.identity.product-auth.csrf-cookie-name=cardo.session")
        .run(
            application -> {
              assertThat(application).hasFailed();
              assertThat(application.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .hasRootCauseMessage("session and CSRF cookie names must be distinct");
            });
  }

  @Test
  void resolvesBearerTokenFromConfiguredSessionCookie() {
    context
        .withPropertyValues("cardo.identity.product-auth.session-cookie-name=product.session")
        .run(
            application -> {
              BearerTokenResolver resolver = application.getBean(BearerTokenResolver.class);
              MockHttpServletRequest request = new MockHttpServletRequest();
              request.setCookies(new Cookie("product.session", "cookie-token"));

              assertThat(resolver.resolve(request)).isEqualTo("cookie-token");
            });
  }

  @Test
  void prefersAuthorizationHeaderOverSessionCookie() {
    SessionCookieBearerTokenResolver resolver =
        new SessionCookieBearerTokenResolver("cardo.session");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer header-token");
    request.setCookies(new Cookie("cardo.session", "cookie-token"));

    assertThat(resolver.resolve(request)).isEqualTo("header-token");
  }

  @Test
  void malformedAuthorizationHeaderNeverFallsBackToSessionCookie() {
    SessionCookieBearerTokenResolver resolver =
        new SessionCookieBearerTokenResolver("cardo.session");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Basic malformed-credential");
    request.setCookies(new Cookie("cardo.session", "cookie-token"));

    assertThat(resolver.resolve(request)).isNull();
    assertThat(resolver.selectsSessionCookie(request)).isFalse();
  }

  private int indexOf(java.util.List<jakarta.servlet.Filter> filters, Class<?> type) {
    for (int index = 0; index < filters.size(); index++) {
      if (type.isInstance(filters.get(index))) {
        return index;
      }
    }
    return -1;
  }
}
