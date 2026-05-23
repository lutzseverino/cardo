package com.odonta.identity.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odonta.identity.keycloak")
public record KeycloakProperties(
    String baseUrl,
    String realm,
    String clientId,
    String clientSecret,
    List<String> userIdClaimClientIds) {}
