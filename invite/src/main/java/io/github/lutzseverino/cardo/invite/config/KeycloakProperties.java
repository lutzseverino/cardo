package io.github.lutzseverino.cardo.invite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.invite.keycloak")
public record KeycloakProperties(
    String baseUrl, String realm, String clientId, String clientSecret) {}
