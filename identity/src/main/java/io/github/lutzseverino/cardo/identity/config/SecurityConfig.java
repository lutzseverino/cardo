package io.github.lutzseverino.cardo.identity.config;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrantAuthorityReader;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthoritiesConverter;
import io.github.lutzseverino.cardo.authorization.spring.RequiredExpirationValidator;
import io.github.lutzseverino.cardo.authorization.spring.ResourcePermissionEvaluator;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.client.RestOperations;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  KeycloakAuthoritiesConverter keycloakAuthoritiesConverter() {
    return new KeycloakAuthoritiesConverter();
  }

  @Bean
  EffectiveGrantAuthorityReader effectiveGrantAuthorityReader() {
    return new EffectiveGrantAuthorityReader();
  }

  @Bean
  PermissionEvaluator permissionEvaluator() {
    return new ResourcePermissionEvaluator();
  }

  @Bean
  MethodSecurityExpressionHandler methodSecurityExpressionHandler(
      PermissionEvaluator permissionEvaluator) {
    DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
    handler.setPermissionEvaluator(permissionEvaluator);
    return handler;
  }

  @Bean
  BearerTokenResolver bearerTokenResolver(
      SessionProperties properties, @Value("${cardo.api.base-path}") String basePath) {
    return new IdentitySessionBearerTokenResolver(properties.accessCookieName(), basePath);
  }

  @Bean
  CookieCsrfTokenRepository csrfTokenRepository(SessionProperties properties) {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieName(properties.csrfCookieName());
    repository.setHeaderName("X-CSRF-TOKEN");
    repository.setCookiePath("/");
    repository.setCookieCustomizer(
        cookie -> cookie.secure(properties.secure()).httpOnly(false).sameSite("Lax").path("/"));
    return repository;
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Qualifier("identityJwkRestOperations") RestOperations rest) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withIssuerLocation(issuer).restOperations(rest).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            new IdentityAudienceValidator(IdentityPermissions.CLIENT_ID),
            new RequiredExpirationValidator()));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      @Value("${cardo.api.base-path}") String basePath,
      KeycloakAuthoritiesConverter authorities,
      BearerTokenResolver bearerTokens,
      CookieCsrfTokenRepository csrfTokens) {
    JwtAuthenticationConverter jwt = new JwtAuthenticationConverter();
    jwt.setJwtGrantedAuthoritiesConverter(authorities);

    return http.csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokens)
                    .csrfTokenRequestHandler(new HeaderOnlyCsrfTokenRequestHandler())
                    .requireCsrfProtectionMatcher(
                        new IdentitySessionCsrfProtectionMatcher(basePath)))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness",
                        "/actuator/info")
                    .permitAll()
                    .requestMatchers(
                        "/openapi.json", "/docs/**", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers(basePath + "/identity")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, basePath + "/identity/users")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, basePath + "/identity/sessions")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, basePath + "/identity/sessions/csrf")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST, basePath + "/identity/sessions/current/refresh")
                    .permitAll()
                    .requestMatchers(HttpMethod.DELETE, basePath + "/identity/sessions/current")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .bearerTokenResolver(bearerTokens)
                    .jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(jwt)))
        .build();
  }
}
