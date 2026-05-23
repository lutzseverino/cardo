package com.odonta.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odonta.billing.keycloak")
public record KeycloakProperties(String baseUrl, String realm) {}
