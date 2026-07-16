package io.github.lutzseverino.cardo.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.billing.keycloak")
public record KeycloakProperties(String baseUrl, String realm) {}
