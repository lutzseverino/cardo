package com.odonta.billing.config;

import com.odonta.authorization.keycloak.KeycloakAuthoritiesConverter;
import com.odonta.authorization.spring.AuthenticatedUserReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  KeycloakAuthoritiesConverter keycloakAuthoritiesConverter() {
    return new KeycloakAuthoritiesConverter();
  }

  @Bean
  AuthenticatedUserReader authenticatedUserReader() {
    return new AuthenticatedUserReader();
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      @Value("${odonta.api.base-path}") String basePath,
      KeycloakAuthoritiesConverter authorities) {
    JwtAuthenticationConverter jwt = new JwtAuthenticationConverter();
    jwt.setJwtGrantedAuthoritiesConverter(authorities);

    return http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health", "/actuator/info")
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
