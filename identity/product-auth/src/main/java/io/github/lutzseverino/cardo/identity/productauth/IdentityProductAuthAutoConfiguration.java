package io.github.lutzseverino.cardo.identity.productauth;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthoritiesConverter;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakRequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.authorization.spring.RequiredExpirationValidator;
import io.github.lutzseverino.cardo.authorization.spring.ResourcePermissionEvaluator;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
import io.github.lutzseverino.cardo.identity.productauth.IdentityProductAuthProperties.ActiveTokenValidation;
import io.github.lutzseverino.cardo.identity.productauth.IdentityProductAuthProperties.TokenExchange;
import jakarta.servlet.DispatcherType;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

@AutoConfiguration(
    before = {
      ServletWebSecurityAutoConfiguration.class,
      OAuth2ResourceServerAutoConfiguration.class
    })
@EnableMethodSecurity
@EnableConfigurationProperties(IdentityProductAuthProperties.class)
public class IdentityProductAuthAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  KeycloakAuthoritiesConverter keycloakAuthoritiesConverter() {
    return new KeycloakAuthoritiesConverter();
  }

  @Bean
  @ConditionalOnMissingBean
  PermissionEvaluator permissionEvaluator() {
    return new ResourcePermissionEvaluator();
  }

  @Bean
  @ConditionalOnMissingBean
  AuthenticatedUserReader authenticatedUserReader() {
    return new AuthenticatedUserReader();
  }

  @Bean
  @ConditionalOnMissingBean
  MethodSecurityExpressionHandler methodSecurityExpressionHandler(
      PermissionEvaluator permissionEvaluator) {
    DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
    handler.setPermissionEvaluator(permissionEvaluator);
    return handler;
  }

  @Bean
  SessionCookieAuthenticationSelector sessionCookieAuthenticationSelector(
      IdentityProductAuthProperties properties) {
    return new SessionCookieAuthenticationSelector(properties.sessionCookieName());
  }

  @Bean("cardoReadOnlyCsrfTokenRepository")
  ReadOnlyCsrfTokenRepository readOnlyCsrfTokenRepository(
      IdentityProductAuthProperties properties) {
    return new ReadOnlyCsrfTokenRepository(properties.csrfCookieName());
  }

  @Bean
  CardoProductTokenDecoder cardoProductTokenDecoder(
      IdentityProductAuthProperties properties,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri) {
    NimbusJwtDecoder signatureDecoder =
        jwkSetUri == null || jwkSetUri.isBlank()
            ? NimbusJwtDecoder.withIssuerLocation(issuer)
                .restOperations(jwkRestOperations(properties.tokenExchange()))
                .build()
            : NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .restOperations(jwkRestOperations(properties.tokenExchange()))
                .build();
    signatureDecoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), new RequiredExpirationValidator()));
    return new CardoProductTokenDecoder(
        signatureDecoder, properties.identitySessionAudience(), properties.productAudience());
  }

  @Bean("cardoProductJwtDecoder")
  JwtDecoder productJwtDecoder(CardoProductTokenDecoder tokens) {
    return tokens::decodeProduct;
  }

  @Bean
  @ConditionalOnMissingBean
  RequestingPartyTokenClient requestingPartyTokenClient(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      IdentityProductAuthProperties properties,
      RestClient.Builder rest) {
    TokenExchange tokenExchange = properties.tokenExchange();
    tokenExchange.validate();
    return new KeycloakRequestingPartyTokenClient(
        tokenEndpoint(issuer),
        rest.clone()
            .requestFactory(
                requestFactory(tokenExchange.connectTimeout(), tokenExchange.readTimeout())));
  }

  @Bean("cardoProductBearerTokenResolver")
  BearerTokenResolver productBearerTokenResolver() {
    return new DefaultBearerTokenResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "cardo.identity.product-auth.active-token-validation",
      name = "enabled",
      havingValue = "true")
  ActiveTokenValidator activeTokenValidator(
      IdentityProductAuthProperties properties, RestClient.Builder rest) {
    ActiveTokenValidation activeTokenValidation = properties.activeTokenValidation();
    activeTokenValidation.validate();
    return new KeycloakActiveTokenValidator(
        activeTokenValidation.introspectionUri(),
        activeTokenValidation.clientId(),
        activeTokenValidation.clientSecret(),
        activeTokenValidation.cacheTtl(),
        activeTokenValidation.cacheMaxEntries(),
        activeTokenValidation.connectTimeout(),
        activeTokenValidation.readTimeout(),
        rest);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "cardo.identity.product-auth.active-token-validation",
      name = "enabled",
      havingValue = "true")
  ActiveTokenValidationFilter activeTokenValidationFilter(ActiveTokenValidator validator) {
    return new ActiveTokenValidationFilter(validator, bearerTokenAuthenticationEntryPoint());
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "cardo.identity.product-auth.active-token-validation",
      name = "enabled",
      havingValue = "true")
  FilterRegistrationBean<ActiveTokenValidationFilter> activeTokenValidationFilterRegistration(
      ActiveTokenValidationFilter filter) {
    FilterRegistrationBean<ActiveTokenValidationFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  private AuthenticationEntryPoint bearerTokenAuthenticationEntryPoint() {
    return new BearerTokenAuthenticationEntryPoint();
  }

  @Bean
  @Order(0)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  SecurityFilterChain identityProductSecurity(
      HttpSecurity http,
      KeycloakAuthoritiesConverter authorities,
      SessionCookieAuthenticationSelector selector,
      @Qualifier("cardoProductBearerTokenResolver") BearerTokenResolver bearerTokens,
      @Qualifier("cardoProductJwtDecoder") JwtDecoder productJwtDecoder,
      CardoProductTokenDecoder tokens,
      RequestingPartyTokenClient exchange,
      IdentityProductAuthProperties properties,
      @Qualifier("cardoReadOnlyCsrfTokenRepository") ReadOnlyCsrfTokenRepository csrfTokens,
      ObjectProvider<ProductRequestPolicy> productRequestPolicy,
      ObjectProvider<ActiveTokenValidationFilter> activeTokenValidationFilter)
      throws Exception {
    AuthenticationManager productTokens =
        productAuthenticationManager(productJwtDecoder, authorities);
    AuthenticationManager browserSessions =
        new SessionCookieAuthenticationManager(
            tokens, exchange, properties.productAudience(), productTokens);
    SessionCookieAuthenticationFilter browserSessionAuthentication =
        new SessionCookieAuthenticationFilter(
            browserSessions,
            new SessionCookieBearerTokenResolver(selector),
            bearerTokenAuthenticationEntryPoint());

    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokens)
                    .csrfTokenRequestHandler(new HeaderOnlyCsrfTokenRequestHandler())
                    .requireCsrfProtectionMatcher(new SessionCookieCsrfProtectionMatcher(selector)))
        .authorizeHttpRequests(
            requests -> {
              requests
                  .dispatcherTypeMatchers(DispatcherType.ERROR)
                  .permitAll()
                  .requestMatchers("/actuator/health", "/actuator/info")
                  .permitAll()
                  .requestMatchers("/openapi.json", "/docs/**", "/swagger-ui/**", "/v3/api-docs/**")
                  .permitAll();
              ProductRequestPolicy policy = productRequestPolicy.getIfAvailable();
              if (policy != null) {
                policy.authorize(new ProductRequestRules(requests));
              }
              requests.anyRequest().denyAll();
            })
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .bearerTokenResolver(bearerTokens)
                    .authenticationManagerResolver(request -> productTokens));
    http.addFilterBefore(browserSessionAuthentication, BearerTokenAuthenticationFilter.class);
    activeTokenValidationFilter.ifAvailable(
        filter -> http.addFilterAfter(filter, BearerTokenAuthenticationFilter.class));
    return http.build();
  }

  private AuthenticationManager productAuthenticationManager(
      JwtDecoder productJwtDecoder, KeycloakAuthoritiesConverter authorities) {
    JwtAuthenticationConverter jwt = new JwtAuthenticationConverter();
    jwt.setJwtGrantedAuthoritiesConverter(authorities);
    JwtAuthenticationProvider provider = new JwtAuthenticationProvider(productJwtDecoder);
    provider.setJwtAuthenticationConverter(jwt);
    return new ProviderManager(provider);
  }

  private URI tokenEndpoint(String issuer) {
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("resource server issuer URI must not be blank");
    }
    return URI.create(issuer.replaceFirst("/+$", "") + "/protocol/openid-connect/token");
  }

  private SimpleClientHttpRequestFactory requestFactory(
      Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  private RestOperations jwkRestOperations(TokenExchange timeouts) {
    timeouts.validate();
    return new RestTemplate(requestFactory(timeouts.connectTimeout(), timeouts.readTimeout()));
  }
}
