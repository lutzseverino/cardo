package com.odonta.authorization.spring;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class AuthenticatedUserReader {

  public AuthenticatedUser currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwt) {
      return currentUser(jwt);
    }
    throw invalidToken("Missing Odonta JWT authentication.");
  }

  public AuthenticatedUser currentUser(JwtAuthenticationToken authentication) {
    return new AuthenticatedUser(
        identityUserId(authentication), authentication.getName(), displayName(authentication));
  }

  public String accessToken(JwtAuthenticationToken authentication) {
    return authentication.getToken().getTokenValue();
  }

  private UUID identityUserId(JwtAuthenticationToken authentication) {
    String value = authentication.getToken().getClaimAsString(OdontaJwtClaims.IDENTITY_USER_ID);
    if (value == null || value.isBlank()) {
      throw invalidToken("Missing Odonta identity user claim.");
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw invalidToken("Invalid Odonta identity user claim.");
    }
  }

  private String displayName(JwtAuthenticationToken authentication) {
    String name = authentication.getToken().getClaimAsString("name");
    if (name != null && !name.isBlank()) {
      return name;
    }
    String preferredUsername = authentication.getToken().getClaimAsString("preferred_username");
    return preferredUsername == null ? authentication.getName() : preferredUsername;
  }

  private OAuth2AuthenticationException invalidToken(String description) {
    return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", description, null));
  }
}
