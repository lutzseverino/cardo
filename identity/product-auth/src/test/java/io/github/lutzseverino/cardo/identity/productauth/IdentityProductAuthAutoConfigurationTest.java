package io.github.lutzseverino.cardo.identity.productauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthoritiesConverter;
import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

class IdentityProductAuthAutoConfigurationTest {

  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(IdentityProductAuthAutoConfiguration.class))
          .withBean(RestClient.Builder.class, RestClient::builder)
          .withPropertyValues(commonProperties());

  private final WebApplicationContextRunner webContext =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  SecurityAutoConfiguration.class,
                  ServletWebSecurityAutoConfiguration.class,
                  OAuth2ResourceServerAutoConfiguration.class,
                  IdentityProductAuthAutoConfiguration.class))
          .withBean(RestClient.Builder.class, RestClient::builder)
          .withPropertyValues(commonProperties());

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
          assertThat(application).hasSingleBean(SessionCookieAuthenticationSelector.class);
          assertThat(application).hasSingleBean(CardoProductTokenDecoder.class);
          assertThat(application).hasSingleBean(RequestingPartyTokenClient.class);
          assertThat(application).hasSingleBean(ReadOnlyCsrfTokenRepository.class);
          assertThat(application).doesNotHaveBean(ActiveTokenValidator.class);
          assertThat(application).doesNotHaveBean(ActiveTokenValidationFilter.class);
        });
  }

  @Test
  void autoConfiguresActiveTokenValidationWhenEnabled() {
    context
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
  void ordersCsrfAndActiveValidationAroundBothAuthenticationFilters() {
    webContext
        .withBean(ActiveTokenValidator.class, () -> token -> true)
        .withPropertyValues("cardo.identity.product-auth.active-token-validation.enabled=true")
        .run(
            application -> {
              var filters = application.getBean(SecurityFilterChain.class).getFilters();
              int csrf = indexOf(filters, CsrfFilter.class);
              int browserSession = indexOf(filters, SessionCookieAuthenticationFilter.class);
              int explicitBearer = indexOfExact(filters, BearerTokenAuthenticationFilter.class);
              int activeValidation = indexOf(filters, ActiveTokenValidationFilter.class);

              assertThat(csrf).isLessThan(browserSession);
              assertThat(browserSession).isLessThan(explicitBearer);
              assertThat(explicitBearer).isLessThan(activeValidation);
            });
  }

  @Test
  void rejectsIncompleteActiveTokenValidationConfiguration() {
    context
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
  void rejectsUnboundedTokenExchangeTimeouts() {
    context
        .withPropertyValues("cardo.identity.product-auth.token-exchange.read-timeout=0s")
        .run(
            application -> {
              assertThat(application).hasFailed();
              assertThat(application.getStartupFailure())
                  .hasRootCauseMessage(
                      "Token exchange read timeout must be between 1ms and 2147483647ms.");
            });
  }

  @Test
  void rejectsSubMillisecondAndOverflowingProductAuthTimeouts() {
    context
        .withPropertyValues("cardo.identity.product-auth.token-exchange.read-timeout=1ns")
        .run(
            application -> {
              assertThat(application).hasFailed();
              assertThat(application.getStartupFailure())
                  .hasRootCauseMessage(
                      "Token exchange read timeout must be between 1ms and 2147483647ms.");
            });
    context
        .withPropertyValues(
            "cardo.identity.product-auth.active-token-validation.enabled=true",
            "cardo.identity.product-auth.active-token-validation.introspection-uri=https://id.example/introspect",
            "cardo.identity.product-auth.active-token-validation.client-id=clinic",
            "cardo.identity.product-auth.active-token-validation.client-secret=secret",
            "cardo.identity.product-auth.active-token-validation.connect-timeout=2147483648ms")
        .run(
            application -> {
              assertThat(application).hasFailed();
              assertThat(application.getStartupFailure())
                  .hasRootCauseMessage(
                      "Active token validation connect timeout must be between 1ms and 2147483647ms.");
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
          assertThat(filters.stream().filter(BearerTokenAuthenticationFilter.class::isInstance))
              .hasSize(2);
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
                      .filter(
                          filter -> BearerTokenAuthenticationFilter.class.equals(filter.getClass()))
                      .map(BearerTokenAuthenticationFilter.class::cast)
                      .findFirst()
                      .orElseThrow();
              assertThat(chain.getFilters())
                  .anyMatch(SessionCookieAuthenticationFilter.class::isInstance);
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
                  .isSameAs(application.getBean("cardoProductBearerTokenResolver"));
              assertThat(ReflectionTestUtils.getField(csrfFilter, "tokenRepository"))
                  .isSameAs(application.getBean(ReadOnlyCsrfTokenRepository.class));
            });
  }

  @Test
  void narrowerHostFilterChainPrecedesCardoWithoutCapturingProductRoutes() {
    webContext
        .withUserConfiguration(HostSecurityChain.class, TestProductRoutes.class)
        .run(
            application -> {
              assertThat(application).hasBean("identityProductSecurity");
              assertThat(application.getBeansOfType(SecurityFilterChain.class)).hasSize(2);
              assertThat(application.getBean("identityProductSecurity"))
                  .isNotSameAs(application.getBean("hostSecurityFilterChain"));

              var mvc =
                  MockMvcBuilders.webAppContextSetup((WebApplicationContext) application)
                      .addFilters(application.getBean(FilterChainProxy.class))
                      .build();

              mvc.perform(get("/management/status")).andExpect(status().isOk());
              mvc.perform(get("/product/public")).andExpect(status().isOk());
              mvc.perform(get("/product/unmatched")).andExpect(status().isUnauthorized());
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
  void rejectsMissingOrConfusedAudienceConfiguration() {
    context
        .withPropertyValues("cardo.identity.product-auth.product-audience=")
        .run(
            application -> {
              assertThat(application).hasFailed();
              assertThat(application.getStartupFailure())
                  .hasRootCauseMessage("product audience must not be blank");
            });

    context
        .withPropertyValues("cardo.identity.product-auth.product-audience=identity")
        .run(
            application -> {
              assertThat(application).hasFailed();
              assertThat(application.getStartupFailure())
                  .hasRootCauseMessage("identity session and product audiences must be distinct");
            });
  }

  @Test
  void appliesMethodAwareProductRulesAndDeniesUnmatchedRoutes() {
    RequestingPartyTokenClient exchange = mock(RequestingPartyTokenClient.class);
    webContext
        .withBean(RequestingPartyTokenClient.class, () -> exchange)
        .withUserConfiguration(TestProductRoutes.class)
        .run(
            application -> {
              var mvc =
                  MockMvcBuilders.webAppContextSetup((WebApplicationContext) application)
                      .addFilters(application.getBean(FilterChainProxy.class))
                      .build();

              mvc.perform(get("/product/public")).andExpect(status().isOk());
              mvc.perform(get("/product/authenticated")).andExpect(status().isUnauthorized());
              mvc.perform(get("/product/unmatched")).andExpect(status().isUnauthorized());
              mvc.perform(
                      post("/product/mutation")
                          .cookie(
                              new jakarta.servlet.http.Cookie("cardo.session", "session-token")))
                  .andExpect(status().isForbidden());
              verifyNoInteractions(exchange);
            });
  }

  private String[] commonProperties() {
    return new String[] {
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.test/realms/cardo",
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.test/jwks",
      "cardo.identity.product-auth.identity-session-audience=identity",
      "cardo.identity.product-auth.product-audience=polity"
    };
  }

  private int indexOf(java.util.List<jakarta.servlet.Filter> filters, Class<?> type) {
    for (int index = 0; index < filters.size(); index++) {
      if (type.isInstance(filters.get(index))) {
        return index;
      }
    }
    return -1;
  }

  private int indexOfExact(java.util.List<jakarta.servlet.Filter> filters, Class<?> type) {
    for (int index = 0; index < filters.size(); index++) {
      if (type.equals(filters.get(index).getClass())) {
        return index;
      }
    }
    return -1;
  }

  @Configuration(proxyBeanMethods = false)
  static class TestProductRoutes {

    @Bean
    ProductRequestPolicy productRequestPolicy() {
      return rules ->
          rules
              .permitAll(HttpMethod.GET, "/product/public")
              .authenticated(HttpMethod.GET, "/product/authenticated")
              .authenticated(HttpMethod.POST, "/product/mutation");
    }

    @Bean
    TestProductController testProductController() {
      return new TestProductController();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class HostSecurityChain {

    @Bean
    @Order(-1)
    SecurityFilterChain hostSecurityFilterChain() {
      return new DefaultSecurityFilterChain(
          request -> request.getRequestURI().startsWith("/management/"));
    }

    @Bean
    TestManagementController testManagementController() {
      return new TestManagementController();
    }
  }

  @RestController
  static class TestProductController {

    @GetMapping({"/product/public", "/product/authenticated", "/product/unmatched"})
    String getRoute() {
      return "ok";
    }

    @PostMapping("/product/mutation")
    String mutate() {
      return "ok";
    }
  }

  @RestController
  static class TestManagementController {

    @GetMapping("/management/status")
    String getStatus() {
      return "ok";
    }
  }
}
