package io.github.lutzseverino.cardo.identity.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.identity.keycloak")
public record KeycloakProperties(
    String baseUrl,
    String realm,
    String clientId,
    String clientSecret,
    String authorizationClientSecret,
    String credentialSetupClientId,
    URI credentialSetupRedirectUri,
    List<String> userIdClaimClientIds,
    boolean legacyStartupMutationEnabled) {}
