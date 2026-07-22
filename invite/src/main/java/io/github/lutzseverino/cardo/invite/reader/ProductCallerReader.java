package io.github.lutzseverino.cardo.invite.reader;

import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import io.github.lutzseverino.cardo.invite.config.KeycloakProperties;
import io.github.lutzseverino.cardo.invite.config.ProductCallerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductCallerReader {

  private static final String SERVICE_ROLE = "product-service";

  private final KeycloakProperties keycloak;
  private final ProductCallerProperties properties;

  public String currentProduct() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwt)) {
      throw invalidToken("Missing product service JWT authentication.");
    }
    if (jwt.getToken().hasClaim(CardoJwtClaims.IDENTITY_USER_ID)) {
      throw invalidToken("A product service token is required.");
    }
    if (jwt.getToken().getAudience() == null
        || !jwt.getToken().getAudience().contains(keycloak.clientId())) {
      throw invalidToken("The token is not intended for Invite.");
    }
    String requiredAuthority = keycloak.clientId() + ":" + SERVICE_ROLE;
    if (!jwt.getAuthorities().stream()
        .anyMatch(authority -> requiredAuthority.equals(authority.getAuthority()))) {
      throw invalidToken("Missing Invite product-service authority.");
    }
    String clientId = jwt.getToken().getClaimAsString("azp");
    if (clientId == null || clientId.isBlank()) {
      clientId = jwt.getToken().getClaimAsString("client_id");
    }
    if (clientId == null || clientId.isBlank()) {
      throw invalidToken("Missing product client identifier.");
    }
    if (!properties.allowedClientIdSet().contains(clientId)) {
      throw invalidToken("The product client is not allowed to call Invite.");
    }
    return clientId;
  }

  private OAuth2AuthenticationException invalidToken(String description) {
    return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", description, null));
  }
}
