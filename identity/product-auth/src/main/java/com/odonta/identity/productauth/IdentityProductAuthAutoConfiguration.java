package com.odonta.identity.productauth;

import com.odonta.authorization.keycloak.KeycloakAuthoritiesConverter;
import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.authorization.spring.ResourcePermissionEvaluator;
import com.odonta.identity.productauth.IdentityProductAuthProperties.ActiveTokenValidation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

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
  @ConditionalOnMissingBean
  BearerTokenResolver bearerTokenResolver(IdentityProductAuthProperties properties) {
    return new SessionCookieBearerTokenResolver(properties.sessionCookieName());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "odonta.identity.product-auth.active-token-validation",
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
      prefix = "odonta.identity.product-auth.active-token-validation",
      name = "enabled",
      havingValue = "true")
  ActiveTokenValidationFilter activeTokenValidationFilter(ActiveTokenValidator validator) {
    return new ActiveTokenValidationFilter(validator, bearerTokenAuthenticationEntryPoint());
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "odonta.identity.product-auth.active-token-validation",
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
  @ConditionalOnMissingBean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  SecurityFilterChain identityProductSecurity(
      HttpSecurity http,
      KeycloakAuthoritiesConverter authorities,
      BearerTokenResolver bearerTokens,
      IdentityProductAuthProperties properties,
      ObjectProvider<ActiveTokenValidationFilter> activeTokenValidationFilter)
      throws Exception {
    JwtAuthenticationConverter jwt = new JwtAuthenticationConverter();
    jwt.setJwtGrantedAuthoritiesConverter(authorities);

    http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            requests -> {
              requests
                  .requestMatchers("/actuator/health", "/actuator/info")
                  .permitAll()
                  .requestMatchers("/openapi.json", "/docs/**", "/swagger-ui/**", "/v3/api-docs/**")
                  .permitAll();
              if (!properties.publicPaths().isEmpty()) {
                requests
                    .requestMatchers(properties.publicPaths().toArray(String[]::new))
                    .permitAll();
              }
              requests.anyRequest().authenticated();
            })
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .bearerTokenResolver(bearerTokens)
                    .jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(jwt)));
    activeTokenValidationFilter.ifAvailable(
        filter -> http.addFilterAfter(filter, BearerTokenAuthenticationFilter.class));
    return http.build();
  }
}
