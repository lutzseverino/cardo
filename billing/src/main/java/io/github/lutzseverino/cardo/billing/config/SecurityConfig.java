package io.github.lutzseverino.cardo.billing.config;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthoritiesConverter;
import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.authorization.spring.ExactAudienceValidator;
import io.github.lutzseverino.cardo.authorization.spring.RequiredExpirationValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestOperations;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  static final String RESOURCE_AUDIENCE = "billing";

  @Bean
  KeycloakAuthoritiesConverter keycloakAuthoritiesConverter() {
    return new KeycloakAuthoritiesConverter();
  }

  @Bean
  AuthenticatedUserReader authenticatedUserReader() {
    return new AuthenticatedUserReader();
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Qualifier("billingJwkRestOperations") RestOperations rest) {
    return new SupplierJwtDecoder(() -> strictJwtDecoder(issuer, rest));
  }

  private NimbusJwtDecoder strictJwtDecoder(String issuer, RestOperations rest) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withIssuerLocation(issuer).restOperations(rest).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            new ExactAudienceValidator(RESOURCE_AUDIENCE),
            new RequiredExpirationValidator()));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      @Value("${cardo.api.base-path}") String basePath,
      KeycloakAuthoritiesConverter authorities) {
    JwtAuthenticationConverter jwt = new JwtAuthenticationConverter();
    jwt.setJwtGrantedAuthoritiesConverter(authorities);

    return http.csrf(AbstractHttpConfigurer::disable)
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
                    .requestMatchers(basePath + "/billing")
                    .permitAll()
                    .requestMatchers(basePath + "/billing/webhooks/stripe")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(jwt)))
        .build();
  }
}
