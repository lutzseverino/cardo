package com.odonta.invite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odonta.invite.keycloak")
public record KeycloakProperties(
    String baseUrl, String realm, String clientId, String clientSecret) {}
